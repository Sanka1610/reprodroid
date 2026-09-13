package com.sanka1610.reprodroid.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sanka1610.reprodroid.ReproDroidApplication
import com.sanka1610.reprodroid.data.local.AppMetadataUpdate
import com.sanka1610.reprodroid.data.local.AppReleaseCheckOverrideEntity
import com.sanka1610.reprodroid.data.local.AppSettingsUpdate
import com.sanka1610.reprodroid.data.local.GlobalSettingsEntity
import com.sanka1610.reprodroid.data.local.InstallationSource
import com.sanka1610.reprodroid.data.local.ManagementMode
import com.sanka1610.reprodroid.data.local.ReleaseCheckSettingsEntity
import com.sanka1610.reprodroid.data.provider.RepositoryRegistrationPreview
import com.sanka1610.reprodroid.data.repository.BuildConfigurationInput
import com.sanka1610.reprodroid.data.repository.ExistingPrimaryRegistration
import com.sanka1610.reprodroid.ui.delegate.ManagedAppsDelegates

data class RepositoryPreviewState(
    val repository: RepositoryRegistrationPreview? = null,
    val requestedUrl: String? = null,
    val generation: Long = 0,
    val isLoading: Boolean = false,
    val existingPrimaryRegistration: ExistingPrimaryRegistration? = null,
)

data class SourceEditPreviewState(
    val registeredAppId: String? = null,
    val expectedUpdatedAt: String? = null,
    val requestedUrl: String? = null,
    val repository: RepositoryRegistrationPreview? = null,
    val isLoading: Boolean = false,
)

class ManagedAppsViewModel(application: Application) : AndroidViewModel(application) {
    private val delegates = ManagedAppsDelegates(application as ReproDroidApplication, viewModelScope)

    val appsUiState = delegates.apps.state
    val registrationUiState = delegates.registration.state
    val appDetailUiState = delegates.appDetail.state
    val releaseUiState = delegates.release.state
    val storageUiState = delegates.storage.state
    val runnerUiState = delegates.runner.state
    val toolchainUiState = delegates.toolchain.state
    val deletionExportUiState = delegates.deletionExport.state

    init {
        delegates.initialize()
    }

    fun acknowledgeMessage(id: Long) = delegates.events.acknowledgeMessage(id)
    fun acknowledgeResult(id: Long) = delegates.events.acknowledgeResult(id)

    fun preview(repositoryUrl: String) = delegates.registration.preview(repositoryUrl)

    fun register(
        mode: ManagementMode,
        installationSource: InstallationSource,
        localBuildRiskConfirmed: Boolean,
        separateManagementTarget: Boolean,
        originRoute: String,
    ) = delegates.registration.register(
        mode,
        installationSource,
        localBuildRiskConfirmed,
        separateManagementTarget,
        originRoute,
    )

    fun resumeRegistration(registeredAppId: String, originRoute: String) =
        delegates.registration.resumeTracking(registeredAppId, originRoute)

    fun clearPreview() = delegates.registration.clearPreview()
    fun previewSourceEdit(registeredAppId: String, expectedUpdatedAt: String, repositoryUrl: String) =
        delegates.registration.previewSourceEdit(registeredAppId, expectedUpdatedAt, repositoryUrl)

    fun applySourceEdit(registeredAppId: String, originRoute: String) =
        delegates.registration.applySourceEdit(registeredAppId, originRoute)

    fun clearSourceEditPreview() = delegates.registration.clearSourceEditPreview()
    fun refresh(registeredAppId: String) = delegates.apps.refresh(registeredAppId)

    fun selectReleaseAsset(
        registeredAppId: String,
        releaseSnapshotId: String,
        providerAssetId: String,
        saveExactFilenameCondition: Boolean,
    ) = delegates.apps.selectReleaseAsset(
        registeredAppId,
        releaseSnapshotId,
        providerAssetId,
        saveExactFilenameCondition,
    )

    fun clearSavedAssetSelection(registeredAppId: String) = delegates.apps.clearSavedAssetSelection(registeredAppId)
    fun createGroup(displayName: String) = delegates.apps.createGroup(displayName)
    fun renameGroup(groupId: String, displayName: String) = delegates.apps.renameGroup(groupId, displayName)
    fun reorderGroups(orderedGroupIds: List<String>) = delegates.apps.reorderGroups(orderedGroupIds)
    fun deleteGroup(groupId: String) = delegates.apps.deleteGroup(groupId)
    fun updateGlobalSettings(settings: GlobalSettingsEntity) = delegates.updateGlobalSettings(settings)

    fun updatePreferences(registeredAppId: String, update: AppSettingsUpdate, originRoute: String) =
        delegates.appDetail.updatePreferences(registeredAppId, update, originRoute)

    fun updateMetadata(registeredAppId: String, update: AppMetadataUpdate, originRoute: String) =
        delegates.appDetail.updateMetadata(registeredAppId, update, originRoute)

    fun stopTracking(registeredAppId: String, originRoute: String) =
        delegates.appDetail.stopTracking(registeredAppId, originRoute)

    fun stopTrackingAfterConfirmedUninstall(registeredAppId: String, originRoute: String) =
        delegates.appDetail.stopTrackingAfterConfirmedUninstall(registeredAppId, originRoute)

    fun confirmUninstall(registeredAppId: String, originRoute: String) =
        delegates.appDetail.confirmUninstall(registeredAppId, originRoute)

    fun resumeTracking(registeredAppId: String) = delegates.appDetail.resumeTracking(registeredAppId)

    fun saveBuildConfiguration(
        registeredAppId: String,
        expectedRevision: Long?,
        input: BuildConfigurationInput,
    ) = delegates.appDetail.saveBuildConfiguration(registeredAppId, expectedRevision, input)

    fun startComparison(registeredAppId: String) = delegates.appDetail.startComparison(registeredAppId)
    fun refreshComparison(registeredAppId: String, comparisonRunId: String) =
        delegates.appDetail.refreshComparison(registeredAppId, comparisonRunId)

    fun confirmComparison(registeredAppId: String, comparisonRunId: String) =
        delegates.appDetail.confirmComparison(registeredAppId, comparisonRunId)

    fun continueComparisonSourceScan(registeredAppId: String, comparisonRunId: String) =
        delegates.appDetail.continueComparisonSourceScan(registeredAppId, comparisonRunId)

    fun refreshInstalledState(registeredAppId: String) = delegates.appDetail.refreshInstalledState(registeredAppId)
    fun install(registeredAppId: String, riskConfirmed: Boolean) =
        delegates.appDetail.install(registeredAppId, riskConfirmed)

    fun updateReleaseCheckSettings(settings: ReleaseCheckSettingsEntity) = delegates.release.updateSettings(settings)
    fun updateReleaseCheckOverride(override: AppReleaseCheckOverrideEntity) = delegates.release.updateOverride(override)
    fun checkReleaseMetadataNow(registeredAppId: String) = delegates.release.checkNow(registeredAppId)
    fun markReleaseCandidateSeen(candidateId: String) = delegates.release.markCandidateSeen(candidateId)
    fun openReleaseCandidate(registeredAppId: String, candidateId: String, originRoute: String) =
        delegates.release.openCandidate(registeredAppId, candidateId, originRoute)

    fun refreshStorage() = delegates.storage.refresh()
    fun refreshAndroidStorage() = delegates.storage.refreshAndroid()
    fun refreshRunnerStorage() = delegates.storage.refreshRunner()
    fun previewAndroidCleanup() = delegates.storage.previewAndroidCleanup()
    fun executeAndroidCleanup(itemIds: Set<String>) = delegates.storage.executeAndroidCleanup(itemIds)
    fun previewRunnerCleanup() = delegates.storage.previewRunnerCleanup()
    fun executeRunnerCleanup(itemIds: Set<String>) = delegates.storage.executeRunnerCleanup(itemIds)
    fun clearAndroidCleanupPreview() = delegates.storage.clearAndroidCleanupPreview()
    fun stageAuditExport(registeredAppIds: Set<String> = emptySet()) =
        delegates.deletionExport.stageAuditExport(registeredAppIds)

    fun copyAuditExport(destination: Uri) = delegates.deletionExport.copyAuditExport(destination)
    fun clearAuditExport() = delegates.deletionExport.clearAuditExport()
    fun exportAppLogs(destination: Uri) = delegates.deletionExport.exportAppLogs(destination)
    fun clearAppLogExport() = delegates.deletionExport.clearAppLogExport()
    fun previewCompleteDeletion(registeredAppId: String) =
        delegates.deletionExport.previewCompleteDeletion(registeredAppId)

    fun executeCompleteDeletion(originRoute: String) = delegates.deletionExport.executeCompleteDeletion(originRoute)
    fun clearDeletionState() = delegates.deletionExport.clearDeletionState()

    fun pairRunner(payload: String) = delegates.runner.pair(payload)
    fun refreshRunnerConnection() = delegates.runner.refresh()
    fun cancelRunnerPairing(runnerId: String) = delegates.runner.cancelPending(runnerId)
    fun selfRevokeRunner() = delegates.runner.selfRevoke()
    fun deleteLocalRunnerConnection(runnerId: String) = delegates.runner.localDelete(runnerId)

    fun refreshToolchains() = delegates.toolchain.refresh()
    fun installToolchains(acceptedLicenseIds: Set<String>) = delegates.toolchain.install(acceptedLicenseIds)
    fun cancelToolchainInstallation() = delegates.toolchain.cancel()
    fun previewToolchainRemoval(artifactIds: Set<String>) = delegates.toolchain.previewRemoval(artifactIds)
    fun executeToolchainRemoval() = delegates.toolchain.executeRemoval()
}
