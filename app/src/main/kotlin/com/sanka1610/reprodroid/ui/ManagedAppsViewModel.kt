package com.sanka1610.reprodroid.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sanka1610.reprodroid.ReproDroidApplication
import com.sanka1610.reprodroid.data.local.AppSettingsUpdate
import com.sanka1610.reprodroid.data.local.AppGroupEntity
import com.sanka1610.reprodroid.data.local.AppMetadataUpdate
import com.sanka1610.reprodroid.data.local.AppReleaseCheckOverrideEntity
import com.sanka1610.reprodroid.data.local.GlobalSettingsEntity
import com.sanka1610.reprodroid.data.local.InstallationSource
import com.sanka1610.reprodroid.data.local.ManagementMode
import com.sanka1610.reprodroid.data.local.ReleaseCheckSettingsEntity
import com.sanka1610.reprodroid.data.provider.RepositoryRegistrationPreview
import com.sanka1610.reprodroid.data.repository.BuildConfigurationInput
import com.sanka1610.reprodroid.data.repository.AppDeletionPreview
import com.sanka1610.reprodroid.data.repository.AppDeletionResult
import com.sanka1610.reprodroid.data.repository.ExistingPrimaryRegistration
import com.sanka1610.reprodroid.data.log.AppLogExportResult
import com.sanka1610.reprodroid.data.storage.AndroidCleanupPreview
import com.sanka1610.reprodroid.data.storage.AndroidStorageSummary
import com.sanka1610.reprodroid.data.storage.StagedAuditExport
import com.sanka1610.reprodroid.work.ReleaseCheckScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant

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
    private val reprodroidApplication = application as ReproDroidApplication
    private val repository = reprodroidApplication.managedAppRepository
    private val jobRepository = reprodroidApplication.jobRepository
    private val storageManager = reprodroidApplication.storageManager
    private val cleanupManager = reprodroidApplication.cleanupManager
    private val retentionCoordinator = reprodroidApplication.retentionCoordinator
    private val auditExportManager = reprodroidApplication.auditExportManager
    private val appLogStore = reprodroidApplication.appLogStore
    private val appLogExportManager = reprodroidApplication.appLogExportManager
    private val toolchainCoordinator = reprodroidApplication.toolchainCoordinator
    private val releaseCheckRepository = reprodroidApplication.releaseCheckRepository
    private val runnerConnectionRepository = reprodroidApplication.runnerConnectionRepository

    val apps = repository.observeApps().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    val inactiveApps = repository.observeInactiveApps().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    val appCatalogLoaded = combine(repository.observeApps(), repository.observeInactiveApps()) { _, _ -> true }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )

    val groups = repository.observeGroups().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    val settings = repository.observeSettings().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = GlobalSettingsEntity(updatedAt = Instant.EPOCH.toString()),
    )

    val releaseCheckSettings = releaseCheckRepository.observeSettings().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ReleaseCheckSettingsEntity(updatedAt = Instant.EPOCH.toString()),
    )

    val releaseCheckOverrides = releaseCheckRepository.observeOverrides().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    val releaseScheduleStates = releaseCheckRepository.observeScheduleStates().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    val releaseCandidates = releaseCheckRepository.observeCandidates().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    val buildEnvironmentManifests = jobRepository.observeBuildEnvironmentManifests().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    val runnerJobs = jobRepository.observeJobs().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    val buildManifestWarnings = jobRepository.buildManifestWarnings
    val sourceScanWarnings = jobRepository.sourceScanWarnings
    val sandboxWarnings = jobRepository.sandboxWarnings
    val runnerStorageState = retentionCoordinator.state
    val runnerCleanupPreview = retentionCoordinator.cleanupPreview
    val runnerCleanupRun = retentionCoordinator.cleanupRun
    val toolchainState = toolchainCoordinator.state
    val runnerConnectionStatus = runnerConnectionRepository.status
    val runnerConnections = runnerConnectionRepository.connections.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList(),
    )

    val availability = repository.observeAvailability().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    private val _androidStorageSummary = MutableStateFlow<AndroidStorageSummary?>(null)
    val androidStorageSummary = _androidStorageSummary.asStateFlow()
    private val _androidCleanupPreview = MutableStateFlow<AndroidCleanupPreview?>(null)
    val androidCleanupPreview = _androidCleanupPreview.asStateFlow()
    private val _storageBusy = MutableStateFlow(false)
    val storageBusy = _storageBusy.asStateFlow()
    private val _auditExport = MutableStateFlow<StagedAuditExport?>(null)
    val auditExport = _auditExport.asStateFlow()
    private val _appLogExport = MutableStateFlow<AppLogExportResult?>(null)
    val appLogExport = _appLogExport.asStateFlow()

    private val _preview = MutableStateFlow(RepositoryPreviewState())
    val preview = _preview.asStateFlow()
    private var previewJob: Job? = null
    private var registrationJob: Job? = null
    private val previewGeneration = PreviewGenerationGate()

    private val _sourceEditPreview = MutableStateFlow(SourceEditPreviewState())
    val sourceEditPreview = _sourceEditPreview.asStateFlow()
    private var sourceEditJob: Job? = null

    private val _deletionPreview = MutableStateFlow<AppDeletionPreview?>(null)
    val deletionPreview = _deletionPreview.asStateFlow()
    private val _deletionResult = MutableStateFlow<AppDeletionResult?>(null)
    val deletionResult = _deletionResult.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    private val _activeAppIds = MutableStateFlow<Set<String>>(emptySet())
    val activeAppIds = _activeAppIds.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching {
                repository.ensureSettings()
                repository.recoverInterruptedDownloads()
                repository.recoverOrphanedReleaseInstallAttempts()
                auditExportManager.reconcileInterruptedExports()
                toolchainCoordinator.recoverActive()
                _auditExport.value = auditExportManager.latest()
                _androidStorageSummary.value = storageManager.summary()
            }
                .onFailure { _message.value = it.userMessage() }
        }
    }

    fun preview(repositoryUrl: String) {
        previewJob?.cancel()
        val requestedUrl = repositoryUrl.trim()
        val request = previewGeneration.begin(requestedUrl)
        previewJob = viewModelScope.launch {
            _preview.value = RepositoryPreviewState(
                requestedUrl = request.requestedUrl,
                generation = request.generation,
                isLoading = true,
            )
            try {
                val resolved = repository.previewRepository(request.requestedUrl)
                val existing = repository.findPrimaryRegistration(
                    provider = resolved.identity.provider,
                    instance = resolved.identity.instance,
                    providerRepositoryId = resolved.identity.providerRepositoryId,
                )
                if (previewGeneration.isCurrent(request) && _preview.value.requestedUrl == request.requestedUrl) {
                    _preview.value = RepositoryPreviewState(
                        repository = resolved,
                        requestedUrl = request.requestedUrl,
                        generation = request.generation,
                        existingPrimaryRegistration = existing,
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                if (previewGeneration.isCurrent(request)) {
                    _preview.value = RepositoryPreviewState(generation = request.generation)
                    _message.value = failure.userMessage()
                }
            }
        }
    }

    fun pairRunner(payload: String) = runConnectionAction { runnerConnectionRepository.pair(payload) }

    fun refreshRunnerConnection() = runConnectionAction { runnerConnectionRepository.refreshHealth() }

    fun cancelRunnerPairing(runnerId: String) = runConnectionAction {
        runnerConnectionRepository.cancelPending(runnerId)
    }

    fun selfRevokeRunner() = runConnectionAction { runnerConnectionRepository.selfRevoke() }

    fun deleteLocalRunnerConnection(runnerId: String) = runConnectionAction {
        runnerConnectionRepository.localDelete(runnerId)
    }

    private fun runConnectionAction(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                // Connection failures are bounded reason codes, never raw TLS/HTTP exception text.
                runnerConnectionRepository.reportActionFailure(failure)
            }
        }
    }

    fun register(
        mode: ManagementMode,
        installationSource: InstallationSource,
        localBuildRiskConfirmed: Boolean,
        separateManagementTarget: Boolean,
        onRegistered: (String) -> Unit,
    ) {
        if (registrationJob?.isActive == true) return
        val state = _preview.value
        val resolved = state.repository ?: return
        val request = PreviewRequestToken(state.generation, state.requestedUrl ?: return)
        registrationJob = viewModelScope.launch {
            _preview.value = _preview.value.copy(isLoading = true)
            try {
                if (installationSource == InstallationSource.LOCAL_BUILD) {
                    require(localBuildRiskConfirmed) { "Local build risk acknowledgement is required." }
                }
                val appId = repository.registerRepository(
                    preview = resolved,
                    mode = mode,
                    installationSource = installationSource,
                    separateManagementTarget = separateManagementTarget,
                )
                ReleaseCheckScheduler.reconcile(
                    getApplication(),
                    releaseCheckRepository,
                    forceRecalculate = true,
                )
                if (previewGeneration.isCurrent(request)) {
                    _preview.value = RepositoryPreviewState(generation = request.generation)
                    onRegistered(appId)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                _message.value = failure.userMessage()
                if (previewGeneration.isCurrent(request)) {
                    _preview.value = _preview.value.copy(isLoading = false)
                }
            }
        }
    }

    fun refresh(registeredAppId: String) {
        if (registeredAppId in _activeAppIds.value) return
        viewModelScope.launch {
            _activeAppIds.value += registeredAppId
            try {
                repository.refresh(registeredAppId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                _message.value = failure.userMessage()
            } finally {
                _activeAppIds.value -= registeredAppId
            }
        }
    }

    fun selectReleaseAsset(
        registeredAppId: String,
        releaseSnapshotId: String,
        providerAssetId: String,
        saveExactFilenameCondition: Boolean,
    ) = runAppAction(registeredAppId) {
        repository.selectReleaseAsset(
            registeredAppId,
            releaseSnapshotId,
            providerAssetId,
            saveExactFilenameCondition,
        )
        releaseCheckRepository.reevaluateCandidates(registeredAppId)
    }

    fun clearSavedAssetSelection(registeredAppId: String) = runAppAction(registeredAppId) {
        repository.clearSavedAssetSelection(registeredAppId)
        releaseCheckRepository.reevaluateCandidates(registeredAppId)
    }

    fun updatePreferences(
        registeredAppId: String,
        update: AppSettingsUpdate,
        onSaved: () -> Unit,
    ) {
        if (registeredAppId in _activeAppIds.value) return
        viewModelScope.launch {
            _activeAppIds.value += registeredAppId
            try {
                repository.updatePreferences(registeredAppId, update)
                onSaved()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                _message.value = failure.userMessage()
            } finally {
                _activeAppIds.value -= registeredAppId
            }
        }
    }

    fun updateMetadata(
        registeredAppId: String,
        update: AppMetadataUpdate,
        onSaved: () -> Unit,
    ) = runAppAction(registeredAppId) {
        repository.updateMetadata(registeredAppId, update)
        onSaved()
    }

    fun createGroup(displayName: String) = runGroupAction {
        repository.createGroup(displayName)
    }

    fun renameGroup(groupId: String, displayName: String) = runGroupAction {
        repository.renameGroup(groupId, displayName)
    }

    fun reorderGroups(orderedGroupIds: List<String>) = runGroupAction {
        repository.reorderGroups(orderedGroupIds)
    }

    fun deleteGroup(groupId: String) = runGroupAction {
        repository.deleteGroup(groupId)
    }

    fun previewSourceEdit(registeredAppId: String, expectedUpdatedAt: String, repositoryUrl: String) {
        sourceEditJob?.cancel()
        val requestedUrl = repositoryUrl.trim()
        sourceEditJob = viewModelScope.launch {
            _sourceEditPreview.value = SourceEditPreviewState(
                registeredAppId = registeredAppId,
                expectedUpdatedAt = expectedUpdatedAt,
                requestedUrl = requestedUrl,
                isLoading = true,
            )
            try {
                val preview = repository.previewRepository(requestedUrl)
                val current = _sourceEditPreview.value
                if (
                    current.registeredAppId == registeredAppId &&
                    current.expectedUpdatedAt == expectedUpdatedAt &&
                    current.requestedUrl == requestedUrl
                ) {
                    _sourceEditPreview.value = current.copy(repository = preview, isLoading = false)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                _sourceEditPreview.value = SourceEditPreviewState()
                _message.value = failure.userMessage()
            }
        }
    }

    fun applySourceEdit(registeredAppId: String, onSaved: () -> Unit) {
        val state = _sourceEditPreview.value
        if (state.registeredAppId != registeredAppId) return
        val expectedUpdatedAt = state.expectedUpdatedAt ?: return
        val preview = state.repository ?: return
        runAppAction(registeredAppId) {
            repository.updateTrackingSource(registeredAppId, expectedUpdatedAt, preview)
            clearSourceEditPreview()
            onSaved()
        }
    }

    fun clearSourceEditPreview() {
        sourceEditJob?.cancel()
        _sourceEditPreview.value = SourceEditPreviewState()
    }

    fun stopTracking(registeredAppId: String, onStopped: () -> Unit) = runAppAction(registeredAppId) {
        repository.stopTracking(registeredAppId)
        ReleaseCheckScheduler.reconcile(getApplication(), releaseCheckRepository, forceRecalculate = true)
        onStopped()
    }

    fun stopTrackingAfterConfirmedUninstall(registeredAppId: String, onStopped: () -> Unit) =
        runAppAction(registeredAppId) {
            repository.stopTrackingAfterConfirmedUninstall(registeredAppId)
            ReleaseCheckScheduler.reconcile(getApplication(), releaseCheckRepository, forceRecalculate = true)
            onStopped()
        }

    fun confirmUninstall(registeredAppId: String, onConfirmed: () -> Unit) =
        runAppAction(registeredAppId) {
            repository.confirmUninstall(registeredAppId)
            onConfirmed()
        }

    fun resumeTracking(registeredAppId: String, onResumed: () -> Unit = {}) = runAppAction(registeredAppId) {
        repository.resumeTracking(registeredAppId)
        ReleaseCheckScheduler.reconcile(getApplication(), releaseCheckRepository, forceRecalculate = true)
        onResumed()
    }

    fun previewCompleteDeletion(registeredAppId: String) = runAppAction(registeredAppId) {
        _deletionResult.value = null
        _deletionPreview.value = repository.previewCompleteDeletion(registeredAppId)
    }

    fun executeCompleteDeletion(onDeleted: () -> Unit = {}) {
        val preview = _deletionPreview.value ?: return
        runAppAction(preview.registeredAppId) {
            _deletionResult.value = repository.executeCompleteDeletion(preview)
            _deletionPreview.value = null
            ReleaseCheckScheduler.reconcile(getApplication(), releaseCheckRepository, forceRecalculate = true)
            onDeleted()
        }
    }

    fun clearDeletionState() {
        _deletionPreview.value = null
        _deletionResult.value = null
    }

    fun saveBuildConfiguration(
        registeredAppId: String,
        expectedRevision: Long?,
        input: BuildConfigurationInput,
    ) = runAppAction(registeredAppId) {
        repository.saveBuildConfiguration(registeredAppId, expectedRevision, input)
    }

    fun startComparison(registeredAppId: String) = runAppAction(registeredAppId) {
        repository.startComparison(registeredAppId)
    }

    fun refreshComparison(registeredAppId: String, comparisonRunId: String) =
        runAppAction(registeredAppId) { repository.refreshComparison(comparisonRunId) }

    fun confirmComparison(registeredAppId: String, comparisonRunId: String) =
        runAppAction(registeredAppId) { repository.confirmComparison(comparisonRunId) }

    fun continueComparisonSourceScan(registeredAppId: String, comparisonRunId: String) =
        runAppAction(registeredAppId) { repository.continueComparisonSourceScan(comparisonRunId) }

    fun refreshInstalledState(registeredAppId: String) = runAppAction(registeredAppId) {
        repository.refreshInstalledStateForApp(registeredAppId)
    }

    fun install(registeredAppId: String, riskConfirmed: Boolean) = runAppAction(registeredAppId) {
        repository.installManagedApp(registeredAppId, riskConfirmed)
    }

    fun updateGlobalSettings(settings: GlobalSettingsEntity) {
        viewModelScope.launch {
            try {
                repository.updateGlobalSettings(settings)
                _androidStorageSummary.value = storageManager.summary()
                clearPreview()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                _message.value = failure.userMessage()
            }
        }
    }

    fun updateReleaseCheckSettings(settings: ReleaseCheckSettingsEntity) {
        viewModelScope.launch {
            try {
                releaseCheckRepository.updateSettings(settings)
                ReleaseCheckScheduler.scheduleNext(
                    getApplication(),
                    releaseCheckRepository,
                    androidx.work.ExistingWorkPolicy.REPLACE,
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                _message.value = failure.userMessage()
            }
        }
    }

    fun updateReleaseCheckOverride(override: AppReleaseCheckOverrideEntity) =
        runAppAction(override.registeredAppId) {
            releaseCheckRepository.updateOverride(override)
            ReleaseCheckScheduler.scheduleNext(
                getApplication(),
                releaseCheckRepository,
                androidx.work.ExistingWorkPolicy.REPLACE,
            )
        }

    fun checkReleaseMetadataNow(registeredAppId: String) = runAppAction(registeredAppId) {
        releaseCheckRepository.checkNow(registeredAppId)
        ReleaseCheckScheduler.enqueueDelivery(getApplication())
        ReleaseCheckScheduler.scheduleNext(
            getApplication(),
            releaseCheckRepository,
            androidx.work.ExistingWorkPolicy.REPLACE,
        )
    }

    fun markReleaseCandidateSeen(candidateId: String) {
        viewModelScope.launch { releaseCheckRepository.markCandidateSeen(candidateId) }
    }

    fun openReleaseCandidate(
        registeredAppId: String,
        candidateId: String,
        onReady: () -> Unit,
    ) = runAppAction(registeredAppId) {
        releaseCheckRepository.stageCandidateForManualAction(candidateId)
        onReady()
    }

    fun refreshStorage() = runStorageAction {
        storageManager.reconcileAvailability()
        cleanupManager.reconcileInterruptedRuns()
        retentionCoordinator.syncCurrentComparisonHolds()
        _androidStorageSummary.value = storageManager.summary()
    }

    fun refreshAndroidStorage() = runStorageAction {
        storageManager.reconcileAvailability()
        cleanupManager.reconcileInterruptedRuns()
        _androidStorageSummary.value = storageManager.summary()
    }

    fun refreshRunnerStorage() = runStorageAction {
        retentionCoordinator.syncCurrentComparisonHolds()
    }

    fun previewAndroidCleanup() = runStorageAction {
        _androidCleanupPreview.value = cleanupManager.createPreview()
        _androidStorageSummary.value = storageManager.summary()
    }

    fun executeAndroidCleanup(itemIds: Set<String>) = runStorageAction {
        val preview = requireNotNull(_androidCleanupPreview.value) { "Create a cleanup preview first." }
        _androidCleanupPreview.value = cleanupManager.execute(preview.previewId, itemIds)
        _androidStorageSummary.value = storageManager.summary()
    }

    fun previewRunnerCleanup() = runStorageAction {
        retentionCoordinator.createCleanupPreview()
    }

    fun executeRunnerCleanup(itemIds: Set<String>) = runStorageAction {
        retentionCoordinator.executeCleanup(itemIds)
        retentionCoordinator.syncCurrentComparisonHolds()
    }

    fun clearAndroidCleanupPreview() {
        _androidCleanupPreview.value = null
    }

    fun stageAuditExport(registeredAppIds: Set<String> = emptySet()) = runStorageAction {
        _auditExport.value = if (registeredAppIds.isEmpty()) {
            auditExportManager.stageAll()
        } else {
            auditExportManager.stageApps(registeredAppIds)
        }
        _androidStorageSummary.value = storageManager.summary()
    }

    fun copyAuditExport(destination: Uri) = runStorageAction {
        val staged = requireNotNull(_auditExport.value) { "Stage an audit export first." }
        _auditExport.value = auditExportManager.copyTo(staged.auditExportId, destination)
    }

    fun clearAuditExport() {
        _auditExport.value = null
    }

    fun exportAppLogs(destination: Uri) = runStorageAction {
        val result = appLogExportManager.exportTo(destination)
        _appLogExport.value = result
        appLogStore.info(
            "LOG_EXPORT_COMPLETED",
            "records=${result.recordCount} bytes=${result.sizeBytes}",
        )
    }

    fun clearAppLogExport() {
        _appLogExport.value = null
    }

    fun refreshToolchains() = runToolchainAction { toolchainCoordinator.refresh() }

    fun installToolchains(acceptedLicenseIds: Set<String>) = runToolchainAction {
        toolchainCoordinator.install(acceptedLicenseIds)
    }

    fun cancelToolchainInstallation() = runToolchainAction { toolchainCoordinator.cancel() }

    fun previewToolchainRemoval(artifactIds: Set<String>) = runToolchainAction {
        toolchainCoordinator.previewRemoval(artifactIds)
    }

    fun executeToolchainRemoval() = runToolchainAction { toolchainCoordinator.executeRemoval() }

    fun clearPreview() {
        previewJob?.cancel()
        registrationJob?.cancel()
        _preview.value = RepositoryPreviewState(generation = previewGeneration.invalidate())
    }
    fun clearMessage() { _message.value = null }

    private fun runAppAction(registeredAppId: String, action: suspend () -> Unit) {
        if (registeredAppId in _activeAppIds.value) return
        viewModelScope.launch {
            _activeAppIds.value += registeredAppId
            try {
                action()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                appLogStore.error("APP_ACTION_FAILED", failure::class.simpleName.orEmpty())
                _message.value = failure.userMessage()
            } finally {
                _activeAppIds.value -= registeredAppId
            }
        }
    }

    private fun runStorageAction(action: suspend () -> Unit) {
        if (_storageBusy.value) return
        viewModelScope.launch {
            _storageBusy.value = true
            try {
                action()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                appLogStore.error("STORAGE_ACTION_FAILED", failure::class.simpleName.orEmpty())
                _message.value = failure.userMessage()
            } finally {
                _storageBusy.value = false
            }
        }
    }

    private fun runToolchainAction(action: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                action()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                appLogStore.error("TOOLCHAIN_ACTION_FAILED", failure::class.simpleName.orEmpty())
                _message.value = failure.userMessage()
            }
        }
    }

    private fun runGroupAction(action: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                action()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                appLogStore.error("GROUP_ACTION_FAILED", failure::class.simpleName.orEmpty())
                _message.value = failure.userMessage()
            }
        }
    }

    private fun Throwable.userMessage(): String = message ?: "ReproDroid operation failed."
}
