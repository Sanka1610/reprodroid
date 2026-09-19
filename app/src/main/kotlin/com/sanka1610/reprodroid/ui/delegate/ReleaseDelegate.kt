package com.sanka1610.reprodroid.ui.delegate

import androidx.work.ExistingWorkPolicy
import com.sanka1610.reprodroid.ReproDroidApplication
import com.sanka1610.reprodroid.data.local.*
import com.sanka1610.reprodroid.ui.navigation.ReproDroidRoute
import com.sanka1610.reprodroid.ui.state.*
import com.sanka1610.reprodroid.work.ReleaseCheckScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
                    destination = ReproDroidRoute.AppAcquisition(registeredAppId),
                    registeredAppId = registeredAppId,
                    candidateId = candidateId,
                ),
            )
        })
}
