package com.sanka1610.reprodroid.data.repository

import com.sanka1610.reprodroid.data.local.BuildEnvironmentDependencyEntity
import com.sanka1610.reprodroid.data.local.BuildEnvironmentManifestEntity
import com.sanka1610.reprodroid.data.local.BuildEnvironmentManifestWithDependencies
import com.sanka1610.reprodroid.data.local.JobEntity
import com.sanka1610.reprodroid.data.network.BuildEnvironmentManifestResponse
import com.sanka1610.reprodroid.data.network.DeterminismOptions
import com.sanka1610.reprodroid.data.network.ExecutionMode
import com.sanka1610.reprodroid.data.network.JobResponse
import com.sanka1610.reprodroid.data.network.JobState
import java.nio.charset.StandardCharsets

data class BuildManifestWarning(
    val code: String,
    val message: String,
)

enum class DependencyDifferenceKind {
    SAME,
    CHANGED,
    BUILD_A_ONLY,
    BUILD_B_ONLY,
}

data class DependencyDifference(
    val fileName: String,
    val kind: DependencyDifferenceKind,
    val buildASha256: String?,
    val buildBSha256: String?,
)

data class BuildEnvironmentComparison(
    val comparable: Boolean,
    val reason: String? = null,
    val differences: List<DependencyDifference> = emptyList(),
) {
    val sameCount: Int get() = differences.count { it.kind == DependencyDifferenceKind.SAME }
    val changedCount: Int get() = differences.count { it.kind == DependencyDifferenceKind.CHANGED }
    val buildAOnlyCount: Int get() = differences.count { it.kind == DependencyDifferenceKind.BUILD_A_ONLY }
    val buildBOnlyCount: Int get() = differences.count { it.kind == DependencyDifferenceKind.BUILD_B_ONLY }
}

internal data class ValidatedBuildEnvironmentManifest(
    val manifest: BuildEnvironmentManifestEntity,
    val dependencies: List<BuildEnvironmentDependencyEntity>,
)

internal fun validateBuildEnvironmentManifest(
    jobId: String,
    remoteJob: JobResponse,
    response: BuildEnvironmentManifestResponse,
    retrievedAt: String,
): ValidatedBuildEnvironmentManifest {
    check(remoteJob.jobId == jobId)
    check(remoteJob.executionMode == ExecutionMode.REAL_TRUSTED && remoteJob.state == JobState.SUCCEEDED)
    check(response.schemaVersion in SUPPORTED_PUBLIC_MANIFEST_SCHEMA_VERSIONS)
    val unconfiguredDeterminism = DeterminismOptions(noBuildCache = false)
    val determinism = when (response.schemaVersion) {
        1 -> {
            check(response.determinism == null)
            check((remoteJob.effectiveBuild?.determinism ?: unconfiguredDeterminism) == unconfiguredDeterminism)
            null
        }
        2 -> requireNotNull(response.determinism)
        else -> error("Unsupported public Manifest schema.")
    }
    determinism?.let { effective ->
        check(effective.sourceDateEpoch?.let { it >= 0 } != false)
        check(remoteJob.effectiveBuild?.determinism == effective)
    }
    check(LOWERCASE_COMMIT_SHA.matches(response.commit) && response.commit == remoteJob.resolvedCommitSha)
    check(response.androidSdk in 1..999)
    check(VERSION_VALUE.matches(response.gradle) && VERSION_VALUE.matches(response.buildTools))
    check(isSafePublicText(response.java.version) && isSafePublicText(response.java.vendor))
    check(LOWERCASE_SHA256.matches(response.apkHash))
    check(remoteJob.artifacts.singleOrNull()?.sha256 == response.apkHash)
    check(response.dependencies.size <= MAX_PUBLIC_DEPENDENCIES)
    val sortedDependencies = response.dependencies.sortedWith(compareBy({ it.fileName }, { it.sha256 }))
    check(response.dependencies == sortedDependencies) { "Runner returned a non-deterministic dependency order." }
    val dependencies = sortedDependencies.mapIndexed { ordinal, dependency ->
        check(dependency.fileName.toByteArray(StandardCharsets.UTF_8).size <= MAX_DEPENDENCY_FILE_NAME_BYTES)
        check(isSafeDependencyFileName(dependency.fileName))
        check(LOWERCASE_SHA256.matches(dependency.sha256))
        BuildEnvironmentDependencyEntity(
            jobId = jobId,
            ordinal = ordinal,
            fileName = dependency.fileName,
            sha256 = dependency.sha256,
        )
    }
    return ValidatedBuildEnvironmentManifest(
        manifest = BuildEnvironmentManifestEntity(
            jobId = jobId,
            schemaVersion = response.schemaVersion,
            commitSha = response.commit,
            javaVersion = response.java.version,
            javaVendor = response.java.vendor,
            gradleVersion = response.gradle,
            androidSdkApiLevel = response.androidSdk,
            buildToolsVersion = response.buildTools,
            apkSha256 = response.apkHash,
            sourceDateEpoch = determinism?.sourceDateEpoch,
            noBuildCache = determinism?.noBuildCache ?: false,
            fixedLocale = determinism?.fixedLocale?.value,
            retrievedAt = retrievedAt,
        ),
        dependencies = dependencies,
    )
}

fun compareBuildEnvironments(
    buildAJob: JobEntity?,
    buildBJob: JobEntity?,
    buildAManifest: BuildEnvironmentManifestWithDependencies?,
    buildBManifest: BuildEnvironmentManifestWithDependencies?,
): BuildEnvironmentComparison {
    if (buildAJob == null || buildBJob == null) return BuildEnvironmentComparison(false, "BUILD_JOB_NOT_AVAILABLE")
    if (buildAManifest == null || buildBManifest == null) {
        return BuildEnvironmentComparison(false, "BUILD_MANIFEST_NOT_AVAILABLE")
    }
    if (
        canonicalRepositoryUrl(buildAJob.repositoryUrl) != canonicalRepositoryUrl(buildBJob.repositoryUrl) ||
        buildAJob.resolvedCommitSha == null || buildAJob.resolvedCommitSha != buildBJob.resolvedCommitSha ||
        buildAManifest.manifest.commitSha != buildAJob.resolvedCommitSha ||
        buildBManifest.manifest.commitSha != buildBJob.resolvedCommitSha
    ) {
        return BuildEnvironmentComparison(false, "BUILD_SOURCE_IDENTITY_MISMATCH")
    }
    val buildAByName = buildAManifest.dependencies.groupBy(BuildEnvironmentDependencyEntity::fileName)
    val buildBByName = buildBManifest.dependencies.groupBy(BuildEnvironmentDependencyEntity::fileName)
    val differences = buildList {
        (buildAByName.keys + buildBByName.keys).sorted().forEach { fileName ->
            val originalA = buildAByName[fileName].orEmpty().map(BuildEnvironmentDependencyEntity::sha256).sorted()
            val originalB = buildBByName[fileName].orEmpty().map(BuildEnvironmentDependencyEntity::sha256).sorted()
            val remainingA = originalA.toMutableList()
            val remainingB = originalB.toMutableList()
            originalA.distinct().forEach { sha256 ->
                repeat(minOf(remainingA.count { it == sha256 }, remainingB.count { it == sha256 })) {
                    remainingA.remove(sha256)
                    remainingB.remove(sha256)
                    add(DependencyDifference(fileName, DependencyDifferenceKind.SAME, sha256, sha256))
                }
            }
            if (originalA.size == 1 && originalB.size == 1 && remainingA.size == 1 && remainingB.size == 1) {
                add(
                    DependencyDifference(
                        fileName,
                        DependencyDifferenceKind.CHANGED,
                        remainingA.removeAt(0),
                        remainingB.removeAt(0),
                    ),
                )
            }
            remainingA.forEach { sha256 ->
                add(DependencyDifference(fileName, DependencyDifferenceKind.BUILD_A_ONLY, sha256, null))
            }
            remainingB.forEach { sha256 ->
                add(DependencyDifference(fileName, DependencyDifferenceKind.BUILD_B_ONLY, null, sha256))
            }
        }
    }
    return BuildEnvironmentComparison(true, differences = differences)
}

private fun canonicalRepositoryUrl(value: String): String =
    value.trim().trimEnd('/').lowercase().removeSuffix(".git")

private fun isSafeDependencyFileName(fileName: String): Boolean {
    if (fileName.isBlank() || fileName.contains('/') || fileName.contains('\\')) return false
    if (fileName.any(::isUnsafePublicCharacter)) return false
    if (SENSITIVE_FILE_NAME_MARKER.containsMatchIn(fileName)) return false
    return !KNOWN_TOKEN_PREFIX.containsMatchIn(fileName)
}

private fun isSafePublicText(value: String): Boolean =
    value.isNotBlank() && value.toByteArray(StandardCharsets.UTF_8).size <= MAX_PUBLIC_TEXT_BYTES &&
        value.none(::isUnsafePublicCharacter)

private fun isUnsafePublicCharacter(character: Char): Boolean =
    character == '\u0000' || Character.isISOControl(character) ||
        Character.getType(character) == Character.FORMAT.toInt() ||
        Character.getType(character) == Character.SURROGATE.toInt()

private val SUPPORTED_PUBLIC_MANIFEST_SCHEMA_VERSIONS = setOf(1, 2)
private const val MAX_PUBLIC_DEPENDENCIES = 20_000
private const val MAX_DEPENDENCY_FILE_NAME_BYTES = 255
private const val MAX_PUBLIC_TEXT_BYTES = 255
private val LOWERCASE_COMMIT_SHA = Regex("[0-9a-f]{40}")
private val LOWERCASE_SHA256 = Regex("[0-9a-f]{64}")
private val VERSION_VALUE = Regex("[0-9]+(?:\\.[0-9]+){1,3}(?:[-+][A-Za-z0-9._-]+)?")
private val SENSITIVE_FILE_NAME_MARKER = Regex(
    "(?:^|[._-])(?:password|passwd|secret|credential|api[-_]?key)(?:[._-]|$)",
    RegexOption.IGNORE_CASE,
)
private val KNOWN_TOKEN_PREFIX = Regex(
    "(?:gh[pousr]_|github_pat_|glpat-|AKIA|ASIA|xox[baprs]-|sk-(?:live|test|proj)-|ya29\\.)",
    RegexOption.IGNORE_CASE,
)
