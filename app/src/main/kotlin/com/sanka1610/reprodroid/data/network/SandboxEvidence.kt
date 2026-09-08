package com.sanka1610.reprodroid.data.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

@Serializable enum class BuildSandboxMode { HOST, DOCKER }
@Serializable enum class SandboxOrigin { NEW_JOB, LEGACY_HOST }
@Serializable enum class SandboxCleanupStatus { NOT_CREATED, PENDING, COMPLETE }

@Serializable
data class JobSandbox(
    val mode: BuildSandboxMode, val origin: SandboxOrigin,
    val profileId: String? = null, val cleanupStatus: SandboxCleanupStatus? = null,
)

@Serializable
data class SandboxLimits(
    val cpuCount: Int, val cpuset: String, val memoryBytes: Long, val memorySwapBytes: Long,
    val pids: Int, val tmpfsBytes: Long,
)

@Serializable
data class SandboxIsolation(
    val uid: Int, val gid: Int, val readOnlyRoot: Boolean, val capDropAll: Boolean,
    val noNewPrivileges: Boolean, val seccomp: String, val sdkReadOnly: Boolean,
    val jdkReadOnly: Boolean, val dockerSocketMounted: Boolean, val jobDiskQuotaEnforced: Boolean,
    val gradleReadOnly: Boolean = false,
)

@Serializable
data class SandboxEvidence(
    val mode: BuildSandboxMode,
    val profileId: String? = null, val imageDigest: String? = null, val platform: String? = null,
    val engineVersion: String? = null, val networkMode: String? = null,
    val limits: SandboxLimits? = null, val isolation: SandboxIsolation? = null,
)

internal const val DOCKER_PROFILE_ID = "docker-microg-v1"
internal const val LEGACY_GENERIC_DOCKER_PROFILE_ID = "docker-generic-v1"
internal const val GENERIC_DOCKER_PROFILE_ID = "docker-generic-v2"
internal const val DOCKER_IMAGE_DIGEST = "sha256:1e0a86e57d247923571b75e0aaf48a1449cf8c543d51fb3e07a4a7d7bfa79316"
internal val SANDBOX_JSON = Json { ignoreUnknownKeys = false; explicitNulls = false; encodeDefaults = true }
internal val GENERIC_DOCKER_PROFILE_IDS = setOf(LEGACY_GENERIC_DOCKER_PROFILE_ID, GENERIC_DOCKER_PROFILE_ID)
private val SUPPORTED_DOCKER_PROFILE_IDS = setOf(DOCKER_PROFILE_ID) + GENERIC_DOCKER_PROFILE_IDS

internal fun validateJobSandbox(sandbox: JobSandbox?, executionMode: ExecutionMode, state: JobState) {
    if (executionMode == ExecutionMode.SIMULATED) { require(sandbox == null); return }
    if (sandbox == null) return // Legacy API: unavailable, never observed HOST evidence.
    when (sandbox.mode) {
        BuildSandboxMode.HOST -> require(sandbox.profileId == null && sandbox.cleanupStatus == null)
        BuildSandboxMode.DOCKER -> {
            require(sandbox.origin == SandboxOrigin.NEW_JOB && sandbox.profileId in SUPPORTED_DOCKER_PROFILE_IDS && sandbox.cleanupStatus != null)
            when (state) {
                JobState.CREATED, JobState.RESOLVING_SOURCE, JobState.AWAITING_CONFIRMATION, JobState.QUEUED,
                JobState.CLONING, JobState.SCANNING_SOURCE, JobState.AWAITING_SCAN_REVIEW -> require(sandbox.cleanupStatus == SandboxCleanupStatus.NOT_CREATED)
                JobState.VERIFYING_WRAPPER, JobState.DISCOVERING_CONFIGURATION, JobState.BUILDING -> Unit
                JobState.DISCOVERING_ARTIFACTS, JobState.SUCCEEDED -> require(sandbox.cleanupStatus == SandboxCleanupStatus.COMPLETE)
                else -> Unit
            }
        }
    }
}

internal fun validateSandboxEvidence(evidence: SandboxEvidence) {
    if (evidence.mode == BuildSandboxMode.HOST) { require(evidence == SandboxEvidence(BuildSandboxMode.HOST)); return }
    require(evidence.profileId in SUPPORTED_DOCKER_PROFILE_IDS && evidence.imageDigest == DOCKER_IMAGE_DIGEST)
    require(evidence.platform == "linux/amd64" && evidence.networkMode == "BRIDGE")
    val version = requireNotNull(evidence.engineVersion)
    require(version.isNotBlank() && version.toByteArray(Charsets.UTF_8).size <= 128 && version.all { it.code in 0x21..0x7e && it != '/' && it != '\\' })
    val generic = evidence.profileId in GENERIC_DOCKER_PROFILE_IDS
    val memory = requireNotNull(evidence.limits).memoryBytes
    if (generic) require(memory in setOf(8_589_934_592, 12_884_901_888))
    require(evidence.limits == if (generic) SandboxLimits(4, "0-3", memory, memory, 1024, 1_073_741_824)
        else SandboxLimits(8, "0-7", 8_589_934_592, 8_589_934_592, 1024, 1_073_741_824))
    require(evidence.isolation == SandboxIsolation(1000, 1000, true, true, true, "DEFAULT", true, true, false, false, generic))
}

internal fun sandboxEvidenceJson(evidence: SandboxEvidence): String {
    validateSandboxEvidence(evidence)
    return SANDBOX_JSON.encodeToString(evidence)
}

internal fun decodeSandboxEvidence(json: String): SandboxEvidence {
    require(json.toByteArray(Charsets.UTF_8).size <= 8192)
    val node = SANDBOX_JSON.parseToJsonElement(json).jsonObject
    require(node.values.none { it == JsonNull })
    val expected = if (node["mode"]?.jsonPrimitive?.content == "HOST") setOf("mode") else
        setOf("mode", "profileId", "imageDigest", "platform", "engineVersion", "networkMode", "limits", "isolation")
    require(node.keys == expected)
    return SANDBOX_JSON.decodeFromJsonElement<SandboxEvidence>(node).also(::validateSandboxEvidence)
}

internal fun validateJobSandboxJson(root: JsonObject) {
    if (!root.containsKey("sandbox")) return
    val node = root.getValue("sandbox").jsonObject
    require(node.toString().toByteArray(Charsets.UTF_8).size <= 8192 && node.values.none { it == JsonNull })
    val expected = if (node["mode"]?.jsonPrimitive?.content == "HOST") setOf("mode", "origin") else
        setOf("mode", "origin", "profileId", "cleanupStatus")
    require(node.keys == expected)
    SANDBOX_JSON.decodeFromJsonElement<JobSandbox>(node) // Reject unknown enum and value types even with a permissive outer client.
}
