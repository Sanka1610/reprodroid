package com.sanka1610.reprodroid.data.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
enum class ExecutionMode {
    SIMULATED,
    REAL_TRUSTED,
}

@Serializable
enum class RevisionType {
    BRANCH,
    TAG,
    COMMIT,
}

@Serializable
enum class SimulationOutcome {
    SUCCESS,
    FAILURE,
}

@Serializable
enum class JobState {
    CREATED,
    RESOLVING_SOURCE,
    AWAITING_CONFIRMATION,
    QUEUED,
    CLONING,
    SCANNING_SOURCE,
    AWAITING_SCAN_REVIEW,
    VERIFYING_WRAPPER,
    BUILDING,
    DISCOVERING_ARTIFACTS,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    INTERRUPTED,
    ;

    val isTerminal: Boolean
        get() = this in setOf(SUCCEEDED, FAILED, CANCELLED, INTERRUPTED)
}

@Serializable
enum class LogLevel {
    INFO,
    WARN,
    ERROR,
}

@Serializable
data class RequestedRevision(
    val type: RevisionType,
    val value: String,
)

@Serializable
data class CreateJobRequest(
    val executionMode: ExecutionMode,
    val repositoryUrl: String,
    val revision: RequestedRevision,
    val simulationOutcome: SimulationOutcome? = null,
)

@Serializable
data class CreateJobResponse(
    val jobId: String,
    val state: JobState,
)

@Serializable
data class ConfirmJobRequest(
    val resolvedCommitSha: String,
    val riskAcknowledged: Boolean,
)

@Serializable
data class ContinueSourceScanRequest(
    val scanResultSha256: String,
    val riskAcknowledged: Boolean,
)

@Serializable
enum class SourceScanStatus {
    SCANNING,
    COMPLETED,
    FAILED,
}

@Serializable
enum class SourceScanDetectorId {
    UNICODE_BIDI_CONTROL,
    UNICODE_INVISIBLE_FORMAT,
    UNICODE_NON_NFC,
    CANONICAL_PATH_COLLISION,
    TEXT_ENCODING_UNSUPPORTED,
    PROCESS_EXEC_API,
    DYNAMIC_NATIVE_LOAD_API,
    NETWORK_DOWNLOAD_COMMAND,
}

@Serializable
data class SourceScanSummaryResponse(
    val status: SourceScanStatus,
    val scannerVersion: String,
    val resultSha256: String? = null,
    val scannedFiles: Int? = null,
    val scannedBytes: Long? = null,
    val findingCount: Int? = null,
    val requiresReview: Boolean? = null,
    val reviewed: Boolean? = null,
)

@Serializable
data class SourceScanStatistics(
    val scannedFiles: Int,
    val scannedBytes: Long,
    val skippedBinaryFiles: Int,
    val skippedSymlinks: Int,
    val findingCount: Int,
)

@Serializable
data class SourceScanDetectorCount(
    val detectorId: SourceScanDetectorId,
    val count: Int,
)

@Serializable
data class SourceScanFinding(
    val detectorId: SourceScanDetectorId,
    val displayPath: String,
    val line: Int? = null,
    val column: Int? = null,
)

@Serializable
data class SourceScanDetailResponse(
    val schemaVersion: Int,
    val jobId: String,
    val resolvedCommitSha: String,
    val scannerVersion: String,
    val resultSha256: String,
    val summary: SourceScanStatistics,
    val detectorCounts: List<SourceScanDetectorCount>,
    val findings: List<SourceScanFinding>,
)

@Serializable
enum class DependencyPinning {
    NONE,
    LOCKFILE,
    LOCKFILE_OFFLINE,
}

@Serializable
enum class FixedLocale(val value: String) {
    @SerialName("C.UTF-8")
    C_UTF_8("C.UTF-8"),
}

@Serializable
data class DeterminismOptions(
    val sourceDateEpoch: Long? = null,
    val noBuildCache: Boolean,
    val fixedLocale: FixedLocale? = null,
)

@Serializable
data class EffectiveBuild(
    val recipeId: String? = null,
    val variantName: String? = null,
    val buildRoot: String,
    val javaMajor: Int? = null,
    val tasks: List<String>,
    val dependencyPinning: DependencyPinning = DependencyPinning.NONE,
    val determinism: DeterminismOptions? = null,
)

@Serializable
data class JobError(
    val code: String,
    val message: String,
)

@Serializable
data class ArtifactMetadata(
    val artifactId: String,
    val fileName: String,
    val sizeBytes: Long,
    val sha256: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
)

@Serializable
data class PublicJavaRuntime(
    val version: String,
    val vendor: String,
)

@Serializable
data class PublicBuildDependency(
    val fileName: String,
    val sha256: String,
)

@Serializable
data class BuildEnvironmentManifestResponse(
    val schemaVersion: Int,
    val commit: String,
    val java: PublicJavaRuntime,
    val gradle: String,
    val androidSdk: Int,
    val buildTools: String,
    val dependencies: List<PublicBuildDependency>,
    val apkHash: String,
    val determinism: DeterminismOptions? = null,
    val sandbox: SandboxEvidence? = null,
)

@Serializable
data class JobResponse(
    val jobId: String,
    val executionMode: ExecutionMode,
    val repositoryUrl: String,
    val requestedRevision: RequestedRevision,
    val resolvedCommitSha: String? = null,
    val state: JobState,
    val progressPercent: Int,
    val requiresConfirmation: Boolean,
    val effectiveBuild: EffectiveBuild? = null,
    val sourceScan: SourceScanSummaryResponse? = null,
    val latestLogSequence: Long,
    val artifacts: List<ArtifactMetadata>,
    val error: JobError? = null,
    val createdAt: String,
    val updatedAt: String,
    val sandbox: JobSandbox? = null,
)

@Serializable
data class LogEntry(
    val sequence: Long,
    val timestamp: String,
    val level: LogLevel,
    val message: String,
)

@Serializable
data class LogResponse(
    val entries: List<LogEntry>,
    val nextAfterSequence: Long,
    val hasMore: Boolean,
)

@Serializable
data class ApiErrorResponse(
    val code: String,
    val message: String,
)

@Serializable
data class V2Capability(val id: String, val contractVersion: Int)

@Serializable
data class V2CapabilitiesResponse(
    val apiVersion: String,
    val foundationContractVersion: Int,
    val runnerId: String,
    val runnerVersion: String,
    val capabilities: List<V2Capability>,
)

@Serializable
enum class V2OperationState { RESERVED, APPLYING, COMPLETED, REJECTED, RECONCILIATION_REQUIRED }

@Serializable
data class V2OperationResult(val type: String, val resourceId: String)

@Serializable
data class V2PublicReason(val code: String, val message: String)

@Serializable
data class V2OperationResponse(
    val operationId: String,
    val state: V2OperationState,
    val kind: String,
    val requestSha256: String,
    val result: V2OperationResult? = null,
    val reason: V2PublicReason? = null,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class V2ResourceRequest(val kind: String, val id: String)

@Serializable
data class V2ClientReferenceRequest(val type: String, val id: String)

@Serializable
data class V2RetentionHoldRequest(
    val resource: V2ResourceRequest,
    val reason: String,
    val clientReference: V2ClientReferenceRequest,
)

@Serializable
data class V2ReasonRequest(val reason: String)

@Serializable
data class V2StorageAreaSummary(
    val area: String,
    val budgetBytes: String,
    val usedBytes: String,
    val reservedBytes: String,
    val unclassifiedBytes: String,
    val usableBytes: String,
    val warningPercent: Int,
    val state: String,
    val measurementState: String,
    val measuredAt: String,
)

@Serializable
data class V2StorageSummaryResponse(
    val schemaVersion: Int,
    val runnerId: String,
    val areas: List<V2StorageAreaSummary>,
)

@Serializable
data class V2CleanupPreviewRequest(
    val area: String,
    val resourceKinds: List<String>,
    val eligibleBefore: String,
    val resourceIds: List<String>,
)

@Serializable
data class V2CleanupExecuteRequest(val itemIds: List<String>)

@Serializable
data class V2CleanupPreviewItem(
    val itemId: String,
    val resourceKind: String,
    val resourceId: String,
    val observedBytes: String,
    val observedToken: String,
    val eligibleAt: String,
    val protectionReasons: List<String>,
)

@Serializable
data class V2CleanupPreviewResponse(
    val schemaVersion: Int,
    val previewId: String,
    val runnerId: String,
    val state: String,
    val expiresAt: String,
    val truncated: Boolean,
    val items: List<V2CleanupPreviewItem>,
)

@Serializable
data class V2CleanupItemResult(
    val itemId: String,
    val result: String,
    val releasedBytes: String,
    val reason: V2PublicReason? = null,
)

@Serializable
data class V2CleanupRunResponse(
    val schemaVersion: Int,
    val cleanupRunId: String,
    val previewId: String,
    val state: String,
    val releasedBytes: String,
    val items: List<V2CleanupItemResult>,
    val startedAt: String? = null,
    val finishedAt: String? = null,
)

@Serializable
enum class ToolchainComponent { JDK, GRADLE, ANDROID_COMMAND_LINE_TOOLS, ANDROID_PLATFORM, ANDROID_BUILD_TOOLS, ANDROID_NDK, CMAKE }

@Serializable
data class ToolchainRequirement(val component: ToolchainComponent, val version: String)

@Serializable data class ResolveToolchainPlanRequest(val requirements: List<ToolchainRequirement>)
@Serializable data class ToolchainLicense(val licenseId: String, val displayName: String, val text: String, val textSha256: String, val sourceUrl: String)
@Serializable data class ToolchainPlanItem(
    val artifactId: String, val component: ToolchainComponent, val version: String, val archiveSha256: String,
    val downloadBytes: String, val reservedBytes: String, val alreadyInstalled: Boolean,
)
@Serializable data class ToolchainPlanResponse(
    val schemaVersion: Int, val runnerId: String, val catalogSha256: String, val planSha256: String,
    val platform: String, val items: List<ToolchainPlanItem>, val requiredLicenses: List<ToolchainLicense>,
    val downloadBytes: String, val reservedBytes: String,
)
@Serializable data class ToolchainLicenseAcceptanceRequest(val licenseId: String, val licenseTextSha256: String, val accepted: Boolean)
@Serializable data class CreateToolchainInstallationRequest(
    val planSha256: String, val catalogSha256: String, val requirements: List<ToolchainRequirement>,
    val licenseAcceptances: List<ToolchainLicenseAcceptanceRequest>,
)
@Serializable enum class ToolchainInstallationState {
    PLANNED, AWAITING_LICENSE, RESERVING, DOWNLOADING, VERIFYING_ARCHIVE, EXTRACTING,
    VERIFYING_CONTENT, PUBLISHING, INSTALLED, CANCEL_REQUESTED, CANCELLED, FAILED, RECONCILIATION_REQUIRED,
}
@Serializable data class ToolchainInstallationItemResponse(
    val artifactId: String, val component: ToolchainComponent, val version: String,
    val state: ToolchainInstallationState, val downloadedBytes: String,
)
@Serializable data class ToolchainInstallationResponse(
    val schemaVersion: Int, val installationId: String, val operationId: String, val runnerId: String, val planSha256: String,
    val catalogSha256: String, val state: ToolchainInstallationState, val progressPercent: Int,
    val items: List<ToolchainInstallationItemResponse>, val reason: V2PublicReason? = null,
    val createdAt: String, val updatedAt: String,
)
@Serializable data class ToolchainInventoryItem(
    val artifactId: String, val component: ToolchainComponent, val version: String, val archiveSha256: String,
    val contentManifestSha256: String, val installedBytes: String, val state: String, val installedAt: String,
)
@Serializable data class ToolchainInventoryResponse(
    val schemaVersion: Int, val runnerId: String, val catalogSha256: String, val items: List<ToolchainInventoryItem>,
)
@Serializable data class ToolchainRemovalRequest(val artifactIds: List<String>)
@Serializable data class ToolchainRemovalPreviewResponse(
    val schemaVersion: Int, val previewId: String, val artifactIds: List<String>,
    val releasableBytes: String, val expiresAt: String,
)
@Serializable data class ExecuteToolchainRemovalRequest(val previewId: String)
