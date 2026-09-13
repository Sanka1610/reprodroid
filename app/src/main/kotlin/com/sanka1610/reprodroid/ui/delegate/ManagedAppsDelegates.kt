package com.sanka1610.reprodroid.ui.delegate

import android.net.Uri
import androidx.work.ExistingWorkPolicy
import com.sanka1610.reprodroid.ReproDroidApplication
import com.sanka1610.reprodroid.data.local.*
import com.sanka1610.reprodroid.data.log.AppLogStore
import com.sanka1610.reprodroid.data.repository.BuildConfigurationInput
import com.sanka1610.reprodroid.ui.PreviewGenerationGate
import com.sanka1610.reprodroid.ui.PreviewRequestToken
import com.sanka1610.reprodroid.ui.RepositoryPreviewState
import com.sanka1610.reprodroid.ui.SourceEditPreviewState
import com.sanka1610.reprodroid.ui.navigation.ReproDroidRoute
import com.sanka1610.reprodroid.ui.state.*
import com.sanka1610.reprodroid.work.ReleaseCheckScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal class ManagedAppsDelegates(
    private val application: ReproDroidApplication,
    private val scope: CoroutineScope,
) {
    val events = ManagedUiEventStore()
    private val appActions = AppActionDelegate(scope, application.appLogStore, events)

    val apps = AppsDelegate(application, scope, appActions, events)
    val registration = RegistrationDelegate(application, scope, appActions, events)
    val appDetail = AppDetailDelegate(application, scope, appActions, events)
    val release = ReleaseDelegate(application, scope, appActions, events)
    val storage = StorageDelegate(application, scope, events)
    val runner = RunnerDelegate(application, scope, events)
    val toolchain = ToolchainDelegate(application, scope, events)
    val deletionExport = DeletionExportDelegate(application, scope, appActions, storage, events)

    fun initialize() {
        scope.launch {
            try {
                apps.initialize()
                storage.initialize()
                toolchain.initialize()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                events.publishMessage(ManagedUiOwner.APPS, failure.userMessage())
            }
        }
    }

    fun updateGlobalSettings(settings: GlobalSettingsEntity) {
        apps.updateGlobalSettings(settings) {
            storage.refreshLocalSummary()
            registration.clearPreview()
        }
    }
}

internal data class FeatureEvents(
    val message: ManagedUiMessage? = null,
    val results: List<ManagedUiResult> = emptyList(),
)

private fun ManagedUiEventStore.observe(owner: ManagedUiOwner): Flow<FeatureEvents> =
    combine(message, results) { currentMessage, currentResults ->
        FeatureEvents(
            message = currentMessage?.takeIf { it.owner == owner },
            results = currentResults.filter { it.owner == owner },
        )
    }

internal class IdentityActionGate {
    private val mutableActiveIds = MutableStateFlow<Set<String>>(emptySet())
    val activeIds = mutableActiveIds.asStateFlow()

    @Synchronized
    fun tryAcquire(identity: String): Boolean {
        if (identity in mutableActiveIds.value) return false
        mutableActiveIds.value += identity
        return true
    }

    @Synchronized
    fun release(identity: String) {
        mutableActiveIds.value -= identity
    }
}

internal class SingleActionGate {
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()

    @Synchronized
    fun tryAcquire(): Boolean {
        if (mutableBusy.value) return false
        mutableBusy.value = true
        return true
    }

    @Synchronized
    fun release() {
        mutableBusy.value = false
    }
}

internal class AppActionDelegate(
    private val scope: CoroutineScope,
    private val appLogStore: AppLogStore,
    private val events: ManagedUiEventStore,
) {
    private val gate = IdentityActionGate()
    val activeIds = gate.activeIds

    fun run(
        owner: ManagedUiOwner,
        registeredAppId: String,
        action: suspend () -> Unit,
        onSuccess: suspend () -> Unit = {},
    ) {
        if (!gate.tryAcquire(registeredAppId)) return
        scope.launch {
            try {
                action()
                onSuccess()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                appLogStore.error("APP_ACTION_FAILED", failure::class.simpleName.orEmpty())
                events.publishMessage(owner, failure.userMessage())
            } finally {
                gate.release(registeredAppId)
            }
        }
    }
}

internal class AppsDelegate(
    private val application: ReproDroidApplication,
    private val scope: CoroutineScope,
    private val actions: AppActionDelegate,
    private val events: ManagedUiEventStore,
) {
    private val repository = application.managedAppRepository
    private val releaseRepository = application.releaseCheckRepository
    private val coreState = combine(
        repository.observeApps(),
        repository.observeInactiveApps(),
        combine(repository.observeApps(), repository.observeInactiveApps()) { _, _ -> true },
        repository.observeGroups(),
        repository.observeSettings(),
    ) { active, inactive, loaded, groups, settings ->
        AppsUiState(active, inactive, loaded, groups, settings)
    }
    val state = combine(coreState, actions.activeIds, events.observe(ManagedUiOwner.APPS)) { base, activeIds, event ->
        base.copy(activeAppIds = activeIds, message = event.message, results = event.results)
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), AppsUiState())

    suspend fun initialize() {
        repository.ensureSettings()
        repository.recoverInterruptedDownloads()
        repository.recoverOrphanedReleaseInstallAttempts()
    }

    fun refresh(registeredAppId: String) = actions.run(ManagedUiOwner.APPS, registeredAppId, {
        repository.refresh(registeredAppId)
    })

    fun selectReleaseAsset(
        registeredAppId: String,
        releaseSnapshotId: String,
        providerAssetId: String,
        saveExactFilenameCondition: Boolean,
    ) = actions.run(ManagedUiOwner.APP_DETAIL, registeredAppId, {
        repository.selectReleaseAsset(
            registeredAppId,
            releaseSnapshotId,
            providerAssetId,
            saveExactFilenameCondition,
        )
        releaseRepository.reevaluateCandidates(registeredAppId)
    })

    fun clearSavedAssetSelection(registeredAppId: String) =
        actions.run(ManagedUiOwner.APP_DETAIL, registeredAppId, {
            repository.clearSavedAssetSelection(registeredAppId)
            releaseRepository.reevaluateCandidates(registeredAppId)
        })

    fun createGroup(displayName: String) = runGroupAction { repository.createGroup(displayName) }
    fun renameGroup(groupId: String, displayName: String) = runGroupAction { repository.renameGroup(groupId, displayName) }
    fun reorderGroups(orderedGroupIds: List<String>) = runGroupAction { repository.reorderGroups(orderedGroupIds) }
    fun deleteGroup(groupId: String) = runGroupAction { repository.deleteGroup(groupId) }

    fun updateGlobalSettings(settings: GlobalSettingsEntity, onSuccess: suspend () -> Unit) {
        scope.launch {
            try {
                repository.updateGlobalSettings(settings)
                onSuccess()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                events.publishMessage(ManagedUiOwner.APPS, failure.userMessage())
            }
        }
    }

    private fun runGroupAction(action: suspend () -> Unit) {
        scope.launch {
            try {
                action()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                application.appLogStore.error("GROUP_ACTION_FAILED", failure::class.simpleName.orEmpty())
                events.publishMessage(ManagedUiOwner.APPS, failure.userMessage())
            }
        }
    }
}

internal class RegistrationDelegate(
    private val application: ReproDroidApplication,
    private val scope: CoroutineScope,
    private val actions: AppActionDelegate,
    private val events: ManagedUiEventStore,
) {
    private val repository = application.managedAppRepository
    private val releaseRepository = application.releaseCheckRepository
    private val mutablePreview = MutableStateFlow(RepositoryPreviewState())
    private val mutableSourceEditPreview = MutableStateFlow(SourceEditPreviewState())
    private var previewJob: Job? = null
    private var registrationJob: Job? = null
    private var sourceEditJob: Job? = null
    private val previewGeneration = PreviewGenerationGate()

    val state = combine(
        mutablePreview,
        mutableSourceEditPreview,
        events.observe(ManagedUiOwner.REGISTRATION),
    ) { preview, sourceEdit, event ->
        RegistrationUiState(preview, sourceEdit, event.message, event.results)
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), RegistrationUiState())

    fun preview(repositoryUrl: String) {
        previewJob?.cancel()
        val request = previewGeneration.begin(repositoryUrl.trim())
        previewJob = scope.launch {
            mutablePreview.value = RepositoryPreviewState(
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
                if (previewGeneration.isCurrent(request) && mutablePreview.value.requestedUrl == request.requestedUrl) {
                    mutablePreview.value = RepositoryPreviewState(
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
                    mutablePreview.value = RepositoryPreviewState(generation = request.generation)
                    events.publishMessage(ManagedUiOwner.REGISTRATION, failure.userMessage())
                }
            }
        }
    }

    fun register(
        mode: ManagementMode,
        installationSource: InstallationSource,
        localBuildRiskConfirmed: Boolean,
        separateManagementTarget: Boolean,
        originRoute: String,
    ) {
        if (registrationJob?.isActive == true) return
        val previewState = mutablePreview.value
        val resolved = previewState.repository ?: return
        val request = PreviewRequestToken(previewState.generation, previewState.requestedUrl ?: return)
        registrationJob = scope.launch {
            mutablePreview.value = mutablePreview.value.copy(isLoading = true)
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
                ReleaseCheckScheduler.reconcile(application, releaseRepository, forceRecalculate = true)
                if (previewGeneration.isCurrent(request)) {
                    mutablePreview.value = RepositoryPreviewState(generation = request.generation)
                    events.publishResult(
                        ManagedUiResult(
                            owner = ManagedUiOwner.REGISTRATION,
                            kind = ManagedUiResultKind.REGISTERED,
                            originRoute = originRoute,
                            destination = ReproDroidRoute.AppInformation(appId),
                            registeredAppId = appId,
                            resetRegistrationDraft = true,
                        ),
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                events.publishMessage(ManagedUiOwner.REGISTRATION, failure.userMessage())
                if (previewGeneration.isCurrent(request)) {
                    mutablePreview.value = mutablePreview.value.copy(isLoading = false)
                }
            }
        }
    }

    fun resumeTracking(registeredAppId: String, originRoute: String) =
        actions.run(ManagedUiOwner.REGISTRATION, registeredAppId, {
            repository.resumeTracking(registeredAppId)
            ReleaseCheckScheduler.reconcile(application, releaseRepository, forceRecalculate = true)
        }, {
            clearPreview()
            publishResult(
                ManagedUiResultKind.REGISTRATION_RESUMED,
                registeredAppId,
                originRoute,
                ReproDroidRoute.AppInformation(registeredAppId),
                resetRegistrationDraft = true,
            )
        })

    fun previewSourceEdit(registeredAppId: String, expectedUpdatedAt: String, repositoryUrl: String) {
        sourceEditJob?.cancel()
        val requestedUrl = repositoryUrl.trim()
        sourceEditJob = scope.launch {
            mutableSourceEditPreview.value = SourceEditPreviewState(
                registeredAppId = registeredAppId,
                expectedUpdatedAt = expectedUpdatedAt,
                requestedUrl = requestedUrl,
                isLoading = true,
            )
            try {
                val preview = repository.previewRepository(requestedUrl)
                val current = mutableSourceEditPreview.value
                if (
                    current.registeredAppId == registeredAppId &&
                    current.expectedUpdatedAt == expectedUpdatedAt &&
                    current.requestedUrl == requestedUrl
                ) {
                    mutableSourceEditPreview.value = current.copy(repository = preview, isLoading = false)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                mutableSourceEditPreview.value = SourceEditPreviewState()
                events.publishMessage(ManagedUiOwner.APP_DETAIL, failure.userMessage())
            }
        }
    }

    fun applySourceEdit(registeredAppId: String, originRoute: String) {
        val current = mutableSourceEditPreview.value
        if (current.registeredAppId != registeredAppId) return
        val expectedUpdatedAt = current.expectedUpdatedAt ?: return
        val preview = current.repository ?: return
        actions.run(ManagedUiOwner.APP_DETAIL, registeredAppId, {
            repository.updateTrackingSource(registeredAppId, expectedUpdatedAt, preview)
        }, {
            clearSourceEditPreview()
            events.publishResult(
                ManagedUiResult(
                    owner = ManagedUiOwner.APP_DETAIL,
                    kind = ManagedUiResultKind.SOURCE_SAVED,
                    originRoute = originRoute,
                    destination = ReproDroidRoute.AppInformation(registeredAppId),
                    registeredAppId = registeredAppId,
                ),
            )
        })
    }

    fun clearSourceEditPreview() {
        sourceEditJob?.cancel()
        mutableSourceEditPreview.value = SourceEditPreviewState()
    }

    fun clearPreview() {
        previewJob?.cancel()
        registrationJob?.cancel()
        mutablePreview.value = RepositoryPreviewState(generation = previewGeneration.invalidate())
    }

    private fun publishResult(
        kind: ManagedUiResultKind,
        registeredAppId: String,
        originRoute: String,
        destination: ReproDroidRoute,
        resetRegistrationDraft: Boolean = false,
    ) {
        events.publishResult(
            ManagedUiResult(
                owner = ManagedUiOwner.REGISTRATION,
                kind = kind,
                originRoute = originRoute,
                destination = destination,
                registeredAppId = registeredAppId,
                resetRegistrationDraft = resetRegistrationDraft,
            ),
        )
    }
}

internal class AppDetailDelegate(
    private val application: ReproDroidApplication,
    private val scope: CoroutineScope,
    private val actions: AppActionDelegate,
    private val events: ManagedUiEventStore,
) {
    private val repository = application.managedAppRepository
    private val jobRepository = application.jobRepository
    private val releaseRepository = application.releaseCheckRepository
    private val coreState = combine(
        jobRepository.observeBuildEnvironmentManifests(),
        jobRepository.observeJobs(),
        jobRepository.buildManifestWarnings,
        jobRepository.sourceScanWarnings,
        jobRepository.sandboxWarnings,
    ) { manifests, jobs, buildWarnings, scanWarnings, sandboxWarnings ->
        AppDetailUiState(manifests, jobs, buildWarnings, scanWarnings, sandboxWarnings)
    }
    val state = combine(
        coreState,
        repository.observeAvailability(),
        events.observe(ManagedUiOwner.APP_DETAIL),
    ) { base, availability, event ->
        base.copy(availability = availability, message = event.message, results = event.results)
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), AppDetailUiState())

    fun updatePreferences(registeredAppId: String, update: AppSettingsUpdate, originRoute: String) =
        actions.run(ManagedUiOwner.APP_DETAIL, registeredAppId, {
            repository.updatePreferences(registeredAppId, update)
        }, { publishSaved(ManagedUiResultKind.PREFERENCES_SAVED, registeredAppId, originRoute) })

    fun updateMetadata(registeredAppId: String, update: AppMetadataUpdate, originRoute: String) =
        actions.run(ManagedUiOwner.APP_DETAIL, registeredAppId, {
            repository.updateMetadata(registeredAppId, update)
        }, { publishSaved(ManagedUiResultKind.METADATA_SAVED, registeredAppId, originRoute) })

    fun stopTracking(registeredAppId: String, originRoute: String) =
        trackingAction(ManagedUiResultKind.TRACKING_STOPPED, registeredAppId, originRoute) {
            repository.stopTracking(registeredAppId)
        }

    fun stopTrackingAfterConfirmedUninstall(registeredAppId: String, originRoute: String) =
        trackingAction(ManagedUiResultKind.TRACKING_STOPPED_AFTER_UNINSTALL, registeredAppId, originRoute) {
            repository.stopTrackingAfterConfirmedUninstall(registeredAppId)
        }

    fun confirmUninstall(registeredAppId: String, originRoute: String) =
        actions.run(ManagedUiOwner.APP_DETAIL, registeredAppId, {
            repository.confirmUninstall(registeredAppId)
        }, {
            publishRemovalResult(
                ManagedUiResultKind.UNINSTALL_CONFIRMED,
                registeredAppId,
                originRoute,
                ReproDroidRoute.AppInformation(registeredAppId),
            )
        })

    fun resumeTracking(registeredAppId: String) = actions.run(ManagedUiOwner.APP_DETAIL, registeredAppId, {
        repository.resumeTracking(registeredAppId)
        ReleaseCheckScheduler.reconcile(application, releaseRepository, forceRecalculate = true)
    })

    fun saveBuildConfiguration(registeredAppId: String, expectedRevision: Long?, input: BuildConfigurationInput) =
        actions.run(ManagedUiOwner.APP_DETAIL, registeredAppId, {
            repository.saveBuildConfiguration(registeredAppId, expectedRevision, input)
        })

    fun startComparison(registeredAppId: String) = actions.run(ManagedUiOwner.APP_DETAIL, registeredAppId, {
        repository.startComparison(registeredAppId)
    })

    fun refreshComparison(registeredAppId: String, comparisonRunId: String) =
        actions.run(ManagedUiOwner.APP_DETAIL, registeredAppId, { repository.refreshComparison(comparisonRunId) })

    fun confirmComparison(registeredAppId: String, comparisonRunId: String) =
        actions.run(ManagedUiOwner.APP_DETAIL, registeredAppId, { repository.confirmComparison(comparisonRunId) })

    fun continueComparisonSourceScan(registeredAppId: String, comparisonRunId: String) =
        actions.run(ManagedUiOwner.APP_DETAIL, registeredAppId, { repository.continueComparisonSourceScan(comparisonRunId) })

    fun refreshInstalledState(registeredAppId: String) =
        actions.run(ManagedUiOwner.APP_DETAIL, registeredAppId, { repository.refreshInstalledStateForApp(registeredAppId) })

    fun install(registeredAppId: String, riskConfirmed: Boolean) =
        actions.run(ManagedUiOwner.APP_DETAIL, registeredAppId, { repository.installManagedApp(registeredAppId, riskConfirmed) })

    private fun trackingAction(
        kind: ManagedUiResultKind,
        registeredAppId: String,
        originRoute: String,
        action: suspend () -> Unit,
    ) = actions.run(ManagedUiOwner.APP_DETAIL, registeredAppId, {
        action()
        ReleaseCheckScheduler.reconcile(application, releaseRepository, forceRecalculate = true)
    }, { publishRemovalResult(kind, registeredAppId, originRoute, ReproDroidRoute.Apps) })

    private fun publishSaved(kind: ManagedUiResultKind, registeredAppId: String, originRoute: String) {
        events.publishResult(
            ManagedUiResult(
                owner = ManagedUiOwner.APP_DETAIL,
                kind = kind,
                originRoute = originRoute,
                destination = ReproDroidRoute.AppInformation(registeredAppId),
                registeredAppId = registeredAppId,
            ),
        )
    }

    private fun publishRemovalResult(
        kind: ManagedUiResultKind,
        registeredAppId: String,
        originRoute: String,
        destination: ReproDroidRoute,
    ) {
        events.publishResult(
            ManagedUiResult(
                owner = ManagedUiOwner.APP_DETAIL,
                kind = kind,
                originRoute = originRoute,
                destination = destination,
                registeredAppId = registeredAppId,
                requiresRemovalTarget = true,
                clearRemovalTarget = true,
            ),
        )
    }
}

internal class ReleaseDelegate(
    private val application: ReproDroidApplication,
    private val scope: CoroutineScope,
    private val actions: AppActionDelegate,
    private val events: ManagedUiEventStore,
) {
    private val repository = application.releaseCheckRepository
    private val stateCore = combine(
        repository.observeSettings(),
        repository.observeOverrides(),
        repository.observeScheduleStates(),
        repository.observeCandidates(),
    ) { settings, overrides, schedules, candidates -> ReleaseUiState(settings, overrides, schedules, candidates) }
    val state = combine(stateCore, events.observe(ManagedUiOwner.RELEASE)) { base, event ->
        base.copy(message = event.message, results = event.results)
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), ReleaseUiState())

    fun updateSettings(settings: ReleaseCheckSettingsEntity) {
        scope.launch {
            try {
                repository.updateSettings(settings)
                ReleaseCheckScheduler.scheduleNext(application, repository, ExistingWorkPolicy.REPLACE)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                events.publishMessage(ManagedUiOwner.RELEASE, failure.userMessage())
            }
        }
    }

    fun updateOverride(override: AppReleaseCheckOverrideEntity) =
        actions.run(ManagedUiOwner.RELEASE, override.registeredAppId, {
            repository.updateOverride(override)
            ReleaseCheckScheduler.scheduleNext(application, repository, ExistingWorkPolicy.REPLACE)
        })

    fun checkNow(registeredAppId: String) = actions.run(ManagedUiOwner.RELEASE, registeredAppId, {
        repository.checkNow(registeredAppId)
        ReleaseCheckScheduler.enqueueDelivery(application)
        ReleaseCheckScheduler.scheduleNext(application, repository, ExistingWorkPolicy.REPLACE)
    })

    fun markCandidateSeen(candidateId: String) {
        scope.launch {
            try {
                repository.markCandidateSeen(candidateId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                events.publishMessage(ManagedUiOwner.RELEASE, failure.userMessage())
            }
        }
    }

    fun openCandidate(registeredAppId: String, candidateId: String, originRoute: String) =
        actions.run(ManagedUiOwner.RELEASE, registeredAppId, {
            repository.stageCandidateForManualAction(candidateId)
        }, {
            events.publishResult(
                ManagedUiResult(
                    owner = ManagedUiOwner.RELEASE,
                    kind = ManagedUiResultKind.RELEASE_CANDIDATE_READY,
                    originRoute = originRoute,
                    destination = ReproDroidRoute.AppTechnical(registeredAppId),
                    registeredAppId = registeredAppId,
                    candidateId = candidateId,
                ),
            )
        })
}

internal class StorageDelegate(
    private val application: ReproDroidApplication,
    private val scope: CoroutineScope,
    private val events: ManagedUiEventStore,
) {
    private val storageManager = application.storageManager
    private val cleanupManager = application.cleanupManager
    private val retentionCoordinator = application.retentionCoordinator
    private val auditExportManager = application.auditExportManager
    private val appLogStore = application.appLogStore
    private val appLogExportManager = application.appLogExportManager
    private val mutableAndroidSummary = MutableStateFlow<com.sanka1610.reprodroid.data.storage.AndroidStorageSummary?>(null)
    private val mutableAndroidCleanup = MutableStateFlow<com.sanka1610.reprodroid.data.storage.AndroidCleanupPreview?>(null)
    private val actionGate = SingleActionGate()
    private val mutableAuditExport = MutableStateFlow<com.sanka1610.reprodroid.data.storage.StagedAuditExport?>(null)
    private val mutableAppLogExport = MutableStateFlow<com.sanka1610.reprodroid.data.log.AppLogExportResult?>(null)

    private val coreState = combine(
        mutableAndroidSummary,
        mutableAndroidCleanup,
        retentionCoordinator.state,
        actionGate.busy,
        mutableAuditExport,
    ) { summary, cleanup, runnerState, busy, audit -> StorageUiState(summary, cleanup, runnerState, busy, audit) }
    private val extendedState = combine(
        coreState,
        mutableAppLogExport,
        retentionCoordinator.cleanupPreview,
        retentionCoordinator.cleanupRun,
    ) { base, logExport, runnerPreview, runnerRun ->
        base.copy(appLogExport = logExport, runnerCleanupPreview = runnerPreview, runnerCleanupRun = runnerRun)
    }
    val state = combine(extendedState, events.observe(ManagedUiOwner.STORAGE)) { base, event ->
        base.copy(message = event.message, results = event.results)
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), StorageUiState())

    suspend fun initialize() {
        auditExportManager.reconcileInterruptedExports()
        mutableAuditExport.value = auditExportManager.latest()
        refreshLocalSummary()
    }

    suspend fun refreshLocalSummary() {
        mutableAndroidSummary.value = storageManager.summary()
    }

    fun refresh() = runAction {
        storageManager.reconcileAvailability()
        cleanupManager.reconcileInterruptedRuns()
        retentionCoordinator.syncCurrentComparisonHolds()
        refreshLocalSummary()
    }

    fun refreshAndroid() = runAction {
        storageManager.reconcileAvailability()
        cleanupManager.reconcileInterruptedRuns()
        refreshLocalSummary()
    }

    fun refreshRunner() = runAction { retentionCoordinator.syncCurrentComparisonHolds() }

    fun previewAndroidCleanup() = runAction {
        mutableAndroidCleanup.value = cleanupManager.createPreview()
        refreshLocalSummary()
    }

    fun executeAndroidCleanup(itemIds: Set<String>) = runAction {
        val preview = requireNotNull(mutableAndroidCleanup.value) { "Create a cleanup preview first." }
        mutableAndroidCleanup.value = cleanupManager.execute(preview.previewId, itemIds)
        refreshLocalSummary()
    }

    fun previewRunnerCleanup() = runAction { retentionCoordinator.createCleanupPreview() }

    fun executeRunnerCleanup(itemIds: Set<String>) = runAction {
        retentionCoordinator.executeCleanup(itemIds)
        retentionCoordinator.syncCurrentComparisonHolds()
    }

    fun clearAndroidCleanupPreview() {
        mutableAndroidCleanup.value = null
    }

    fun stageAuditExport(registeredAppIds: Set<String>) = runAction {
        mutableAuditExport.value = if (registeredAppIds.isEmpty()) {
            auditExportManager.stageAll()
        } else {
            auditExportManager.stageApps(registeredAppIds)
        }
        refreshLocalSummary()
    }

    fun copyAuditExport(destination: Uri) = runAction {
        val staged = requireNotNull(mutableAuditExport.value) { "Stage an audit export first." }
        mutableAuditExport.value = auditExportManager.copyTo(staged.auditExportId, destination)
    }

    fun clearAuditExport() {
        mutableAuditExport.value = null
    }

    fun exportAppLogs(destination: Uri) = runAction {
        val result = appLogExportManager.exportTo(destination)
        mutableAppLogExport.value = result
        appLogStore.info("LOG_EXPORT_COMPLETED", "records=${result.recordCount} bytes=${result.sizeBytes}")
    }

    fun clearAppLogExport() {
        mutableAppLogExport.value = null
    }

    private fun runAction(action: suspend () -> Unit) {
        if (!actionGate.tryAcquire()) return
        scope.launch {
            try {
                action()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                appLogStore.error("STORAGE_ACTION_FAILED", failure::class.simpleName.orEmpty())
                events.publishMessage(ManagedUiOwner.STORAGE, failure.userMessage())
            } finally {
                actionGate.release()
            }
        }
    }
}

internal class RunnerDelegate(
    application: ReproDroidApplication,
    private val scope: CoroutineScope,
    events: ManagedUiEventStore,
) {
    private val repository = application.runnerConnectionRepository
    private val coreState = combine(
        repository.status,
        repository.connections,
    ) { status, connections -> RunnerUiState(status, connections) }
    val state = combine(coreState, events.observe(ManagedUiOwner.RUNNER)) { base, event ->
        base.copy(message = event.message, results = event.results)
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), RunnerUiState())

    fun pair(payload: String) = runAction { repository.pair(payload) }
    fun refresh() = runAction { repository.refreshHealth() }
    fun cancelPending(runnerId: String) = runAction { repository.cancelPending(runnerId) }
    fun selfRevoke() = runAction { repository.selfRevoke() }
    fun localDelete(runnerId: String) = runAction { repository.localDelete(runnerId) }

    private fun runAction(action: suspend () -> Unit) {
        scope.launch {
            try {
                action()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                repository.reportActionFailure(failure)
            }
        }
    }
}

internal class ToolchainDelegate(
    private val application: ReproDroidApplication,
    private val scope: CoroutineScope,
    private val events: ManagedUiEventStore,
) {
    private val coordinator = application.toolchainCoordinator
    val state = combine(
        coordinator.state.map(::ToolchainFeatureUiState),
        events.observe(ManagedUiOwner.TOOLCHAIN),
    ) { base, event -> base.copy(message = event.message, results = event.results) }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), ToolchainFeatureUiState())

    suspend fun initialize() = coordinator.recoverActive()
    fun refresh() = runAction { coordinator.refresh() }
    fun install(acceptedLicenseIds: Set<String>) = runAction { coordinator.install(acceptedLicenseIds) }
    fun cancel() = runAction { coordinator.cancel() }
    fun previewRemoval(artifactIds: Set<String>) = runAction { coordinator.previewRemoval(artifactIds) }
    fun executeRemoval() = runAction { coordinator.executeRemoval() }

    private fun runAction(action: suspend () -> Unit) {
        scope.launch {
            try {
                action()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                application.appLogStore.error("TOOLCHAIN_ACTION_FAILED", failure::class.simpleName.orEmpty())
                events.publishMessage(ManagedUiOwner.TOOLCHAIN, failure.userMessage())
            }
        }
    }
}

internal class DeletionExportDelegate(
    private val application: ReproDroidApplication,
    private val scope: CoroutineScope,
    private val actions: AppActionDelegate,
    private val storage: StorageDelegate,
    private val events: ManagedUiEventStore,
) {
    private val repository = application.managedAppRepository
    private val mutablePreview = MutableStateFlow<com.sanka1610.reprodroid.data.repository.AppDeletionPreview?>(null)
    private val mutableResult = MutableStateFlow<com.sanka1610.reprodroid.data.repository.AppDeletionResult?>(null)
    val state = combine(
        mutablePreview,
        mutableResult,
        events.observe(ManagedUiOwner.DELETION_EXPORT),
    ) { preview, result, event -> DeletionExportUiState(preview, result, event.message, event.results) }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), DeletionExportUiState())

    fun previewCompleteDeletion(registeredAppId: String) =
        actions.run(ManagedUiOwner.DELETION_EXPORT, registeredAppId, {
            mutableResult.value = null
            mutablePreview.value = repository.previewCompleteDeletion(registeredAppId)
        })

    fun executeCompleteDeletion(originRoute: String) {
        val preview = mutablePreview.value ?: return
        actions.run(ManagedUiOwner.DELETION_EXPORT, preview.registeredAppId, {
            mutableResult.value = repository.executeCompleteDeletion(preview)
            mutablePreview.value = null
            ReleaseCheckScheduler.reconcile(
                application,
                application.releaseCheckRepository,
                forceRecalculate = true,
            )
        }, {
            events.publishResult(
                ManagedUiResult(
                    owner = ManagedUiOwner.DELETION_EXPORT,
                    kind = ManagedUiResultKind.DELETION_COMPLETED,
                    originRoute = originRoute,
                    destination = ReproDroidRoute.parse(originRoute),
                    registeredAppId = preview.registeredAppId,
                ),
            )
        })
    }

    fun clearDeletionState() {
        mutablePreview.value = null
        mutableResult.value = null
    }

    fun stageAuditExport(registeredAppIds: Set<String>) = storage.stageAuditExport(registeredAppIds)
    fun copyAuditExport(destination: Uri) = storage.copyAuditExport(destination)
    fun clearAuditExport() = storage.clearAuditExport()
    fun exportAppLogs(destination: Uri) = storage.exportAppLogs(destination)
    fun clearAppLogExport() = storage.clearAppLogExport()
}

internal fun Throwable.userMessage(): String = message ?: "ReproDroid operation failed."
