package com.sanka1610.reprodroid.ui.delegate

import androidx.work.ExistingWorkPolicy
import com.sanka1610.reprodroid.ReproDroidApplication
import com.sanka1610.reprodroid.data.local.*
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
                try {
                    releaseRepository.checkNow(appId)
                    ReleaseCheckScheduler.enqueueDelivery(application)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (failure: Throwable) {
                    // Registration is already durable. Keep it and let the scheduled check or the
                    // explicit recovery action retry metadata discovery later.
                    events.publishMessage(ManagedUiOwner.REGISTRATION, failure.userMessage())
                }
                // checkNow persists the exact terminal/backoff/cooldown state used by the
                // completion screen. Only reschedule WorkManager here; recalculating would erase
                // that state before the user sees it.
                try {
                    ReleaseCheckScheduler.scheduleNext(
                        application,
                        releaseRepository,
                        ExistingWorkPolicy.REPLACE,
                    )
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (failure: Throwable) {
                    events.publishMessage(ManagedUiOwner.REGISTRATION, failure.userMessage())
                }
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
