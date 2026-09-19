package com.sanka1610.reprodroid.ui.delegate

import com.sanka1610.reprodroid.ReproDroidApplication
import com.sanka1610.reprodroid.data.local.*
import com.sanka1610.reprodroid.ui.state.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
