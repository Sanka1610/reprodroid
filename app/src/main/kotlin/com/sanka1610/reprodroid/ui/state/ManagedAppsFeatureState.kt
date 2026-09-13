package com.sanka1610.reprodroid.ui.state

import com.sanka1610.reprodroid.data.connection.RunnerConnectionStatus
import com.sanka1610.reprodroid.data.local.AppGroupEntity
import com.sanka1610.reprodroid.data.local.BuildEnvironmentManifestWithDependencies
import com.sanka1610.reprodroid.data.local.GlobalSettingsEntity
import com.sanka1610.reprodroid.data.local.JobRecord
import com.sanka1610.reprodroid.data.local.ReleaseCandidateEntity
import com.sanka1610.reprodroid.data.local.ReleaseCheckSettingsEntity
import com.sanka1610.reprodroid.data.local.ReleaseScheduleStateEntity
import com.sanka1610.reprodroid.data.local.RegisteredAppRecord
import com.sanka1610.reprodroid.data.local.ResourceAvailabilityEntity
import com.sanka1610.reprodroid.data.local.RunnerConnectionEntity
import com.sanka1610.reprodroid.data.local.AppReleaseCheckOverrideEntity
import com.sanka1610.reprodroid.data.log.AppLogExportResult
import com.sanka1610.reprodroid.data.network.V2CleanupPreviewResponse
import com.sanka1610.reprodroid.data.network.V2CleanupRunResponse
import com.sanka1610.reprodroid.data.repository.AppDeletionPreview
import com.sanka1610.reprodroid.data.repository.AppDeletionResult
import com.sanka1610.reprodroid.data.repository.BuildManifestWarning
import com.sanka1610.reprodroid.data.repository.SourceScanWarning
import com.sanka1610.reprodroid.data.storage.AndroidCleanupPreview
import com.sanka1610.reprodroid.data.storage.AndroidStorageSummary
import com.sanka1610.reprodroid.data.storage.RunnerStorageConnectionState
import com.sanka1610.reprodroid.data.storage.StagedAuditExport
import com.sanka1610.reprodroid.data.toolchain.ToolchainUiState
import com.sanka1610.reprodroid.ui.RepositoryPreviewState
import com.sanka1610.reprodroid.ui.SourceEditPreviewState

data class AppsUiState(
    val apps: List<RegisteredAppRecord> = emptyList(),
    val inactiveApps: List<RegisteredAppRecord> = emptyList(),
    val catalogLoaded: Boolean = false,
    val groups: List<AppGroupEntity> = emptyList(),
    val settings: GlobalSettingsEntity = GlobalSettingsEntity(updatedAt = java.time.Instant.EPOCH.toString()),
    val activeAppIds: Set<String> = emptySet(),
)

data class RegistrationUiState(
    val preview: RepositoryPreviewState = RepositoryPreviewState(),
    val sourceEditPreview: SourceEditPreviewState = SourceEditPreviewState(),
)

data class AppDetailUiState(
    val buildEnvironmentManifests: List<BuildEnvironmentManifestWithDependencies> = emptyList(),
    val runnerJobs: List<JobRecord> = emptyList(),
    val buildManifestWarnings: Map<String, BuildManifestWarning> = emptyMap(),
    val sourceScanWarnings: Map<String, SourceScanWarning> = emptyMap(),
    val sandboxWarnings: Map<String, String> = emptyMap(),
    val availability: List<ResourceAvailabilityEntity> = emptyList(),
)

data class ReleaseUiState(
    val settings: ReleaseCheckSettingsEntity? = null,
    val overrides: List<AppReleaseCheckOverrideEntity> = emptyList(),
    val schedules: List<ReleaseScheduleStateEntity> = emptyList(),
    val candidates: List<ReleaseCandidateEntity> = emptyList(),
)

data class StorageUiState(
    val androidSummary: AndroidStorageSummary? = null,
    val androidCleanupPreview: AndroidCleanupPreview? = null,
    val runnerState: RunnerStorageConnectionState = RunnerStorageConnectionState(),
    val busy: Boolean = false,
    val auditExport: StagedAuditExport? = null,
    val appLogExport: AppLogExportResult? = null,
    val runnerCleanupPreview: V2CleanupPreviewResponse? = null,
    val runnerCleanupRun: V2CleanupRunResponse? = null,
)

data class RunnerUiState(
    val status: RunnerConnectionStatus = RunnerConnectionStatus(),
    val connections: List<RunnerConnectionEntity> = emptyList(),
)

data class ToolchainFeatureUiState(
    val coordinator: ToolchainUiState = ToolchainUiState(),
)

data class DeletionExportUiState(
    val preview: AppDeletionPreview? = null,
    val result: AppDeletionResult? = null,
)
