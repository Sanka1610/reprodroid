package com.sanka1610.reprodroid.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sanka1610.reprodroid.ReproDroidApplication
import com.sanka1610.reprodroid.data.local.AppSettingsUpdate
import com.sanka1610.reprodroid.data.local.GlobalSettingsEntity
import com.sanka1610.reprodroid.data.local.InstallationSource
import com.sanka1610.reprodroid.data.local.ManagementMode
import com.sanka1610.reprodroid.data.provider.RepositoryRegistrationPreview
import com.sanka1610.reprodroid.data.repository.BuildConfigurationInput
import com.sanka1610.reprodroid.data.storage.AndroidCleanupPreview
import com.sanka1610.reprodroid.data.storage.AndroidStorageSummary
import com.sanka1610.reprodroid.data.storage.StagedAuditExport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant

data class RepositoryPreviewState(
    val repository: RepositoryRegistrationPreview? = null,
    val requestedUrl: String? = null,
    val generation: Long = 0,
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

    val apps = repository.observeApps().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    val settings = repository.observeSettings().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = GlobalSettingsEntity(updatedAt = Instant.EPOCH.toString()),
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

    private val _preview = MutableStateFlow(RepositoryPreviewState())
    val preview = _preview.asStateFlow()
    private var previewJob: Job? = null
    private var registrationJob: Job? = null
    private val previewGeneration = PreviewGenerationGate()

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
                if (previewGeneration.isCurrent(request) && _preview.value.requestedUrl == request.requestedUrl) {
                    _preview.value = RepositoryPreviewState(
                        repository = resolved,
                        requestedUrl = request.requestedUrl,
                        generation = request.generation,
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

    fun refreshStorage() = runStorageAction {
        storageManager.reconcileAvailability()
        cleanupManager.reconcileInterruptedRuns()
        retentionCoordinator.syncCurrentComparisonHolds()
        _androidStorageSummary.value = storageManager.summary()
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
                _message.value = failure.userMessage()
            } finally {
                _storageBusy.value = false
            }
        }
    }

    private fun Throwable.userMessage(): String = message ?: "ReproDroid operation failed."
}
