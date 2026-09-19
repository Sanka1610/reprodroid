package com.sanka1610.reprodroid.ui.delegate

import com.sanka1610.reprodroid.ReproDroidApplication
import com.sanka1610.reprodroid.data.local.*
import com.sanka1610.reprodroid.ui.state.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
