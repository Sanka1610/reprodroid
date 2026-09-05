package com.sanka1610.reprodroid.data.repository

import com.sanka1610.reprodroid.data.local.JobEntity
import com.sanka1610.reprodroid.data.local.BuildEnvironmentManifestEntity
import com.sanka1610.reprodroid.data.network.*

internal fun storedJobSandbox(job: JobEntity): JobSandbox? {
    val sandbox = if (job.sandboxMode == null) {
        require(job.sandboxOrigin == null && job.sandboxProfileId == null && job.sandboxCleanupStatus == null)
        null
    } else JobSandbox(
        BuildSandboxMode.valueOf(job.sandboxMode), SandboxOrigin.valueOf(requireNotNull(job.sandboxOrigin)),
        job.sandboxProfileId, job.sandboxCleanupStatus?.let(SandboxCleanupStatus::valueOf),
    )
    validateJobSandbox(sandbox, ExecutionMode.valueOf(job.executionMode), JobState.valueOf(job.state))
    return sandbox
}

internal fun validateSandboxRefresh(existing: JobEntity?, remote: JobResponse) {
    validateJobSandbox(remote.sandbox, remote.executionMode, remote.state)
    if (existing == null) return
    require(existing.jobId == remote.jobId && existing.executionMode == remote.executionMode.name)
    val previous = storedJobSandbox(existing)
    if (previous != null) {
        val current = requireNotNull(remote.sandbox)
        require(previous.mode == current.mode && previous.origin == current.origin && previous.profileId == current.profileId)
    } else if (existing.sandboxResponseSeen && remote.sandbox != null) {
        require(remote.sandbox.mode == BuildSandboxMode.HOST && remote.sandbox.origin == SandboxOrigin.LEGACY_HOST)
    }
}

internal fun storedSandboxManifestValid(job: JobEntity, manifest: BuildEnvironmentManifestEntity): Boolean = runCatching {
    val selection = storedJobSandbox(job)
    when (manifest.schemaVersion) {
        1, 2 -> require(manifest.sandboxJson == null && (selection == null || selection.origin == SandboxOrigin.LEGACY_HOST))
        3, 4 -> {
            val evidence = decodeSandboxEvidence(requireNotNull(manifest.sandboxJson))
            require(selection != null && evidence.mode == selection.mode && evidence.profileId == selection.profileId)
        }
        else -> error("Unsupported Manifest schema.")
    }
}.isSuccess

fun sandboxSelectionText(job: JobEntity?): String {
    if (job == null) return "Sandbox: unavailable"
    return try {
        when (val sandbox = storedJobSandbox(job)) {
            null -> if (job.executionMode == ExecutionMode.SIMULATED.name) "Sandbox: not applicable (SIMULATED)" else "Sandbox: unavailable (legacy evidence)"
            else -> when (sandbox.mode) {
                BuildSandboxMode.HOST -> "HOST — no build container" + if (sandbox.origin == SandboxOrigin.LEGACY_HOST) " (legacy Job)" else ""
                BuildSandboxMode.DOCKER -> "DOCKER / ${sandbox.profileId} — cleanup ${sandbox.cleanupStatus}"
            }
        }
    } catch (_: Exception) { "Sandbox: invalid stored evidence; acknowledgement is disabled" }
}

fun sandboxAcknowledgementAllowed(job: JobEntity): Boolean = runCatching { storedJobSandbox(job) }.isSuccess

fun sandboxManifestText(json: String?): String = try {
    if (json == null) "Sandbox execution evidence: unavailable" else {
        val evidence = decodeSandboxEvidence(json)
        if (evidence.mode == BuildSandboxMode.HOST) "Execution: HOST (Runner-observed)" else {
            val limits = requireNotNull(evidence.limits)
            val toolchains = if (evidence.profileId == GENERIC_DOCKER_PROFILE_ID) "JDK / SDK / Gradle" else "JDK / SDK"
            "Execution: DOCKER ${evidence.profileId}, ${evidence.platform}, engine ${evidence.engineVersion}\n" +
                "Image: ${evidence.imageDigest}\n" +
                "${limits.cpuCount} CPUs (${limits.cpuset}), ${limits.memoryBytes / (1024L * 1024 * 1024)} GiB RAM / no extra swap, ${limits.pids} PIDs, /tmp ${limits.tmpfsBytes / (1024L * 1024 * 1024)} GiB\n" +
                "UID/GID 1000; read-only root / $toolchains; cap-drop ALL; no-new-privileges; default seccomp; no Docker socket\n" +
                "Bridge networking: host/LAN isolation is not established. No hard Job disk quota.\n" +
                "Runner-observed evidence, not third-party attestation; raw APK comparison and trust are unchanged."
        }
    }
} catch (_: Exception) { "Sandbox execution evidence: invalid stored JSON" }
