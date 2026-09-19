package com.sanka1610.reprodroid.ui.delegate

import com.sanka1610.reprodroid.ReproDroidApplication
import com.sanka1610.reprodroid.data.local.*
import com.sanka1610.reprodroid.data.provider.ProviderId
import com.sanka1610.reprodroid.ui.state.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal class ProviderAuthDelegate(
    application: ReproDroidApplication,
    private val scope: CoroutineScope,
    private val events: ManagedUiEventStore,
) {
    private val repository = application.providerCredentialRepository
    private val gate = IdentityActionGate()
    val state = combine(
        repository.statuses,
        gate.activeIds,
        events.observe(ManagedUiOwner.PROVIDER_AUTH),
    ) { statuses, activeIds, event ->
        ProviderAuthUiState(
            statuses = statuses,
            activeProviders = activeIds.mapNotNullTo(linkedSetOf()) { name ->
                ProviderId.entries.firstOrNull { it.name == name }
            },
            message = event.message,
        )
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), ProviderAuthUiState(repository.statuses.value))

    fun save(provider: ProviderId, token: String) = runAction(provider) {
        repository.save(provider, token)
    }

    fun delete(provider: ProviderId) = runAction(provider) {
        repository.delete(provider)
    }

    private fun runAction(provider: ProviderId, action: () -> Unit) {
        if (!gate.tryAcquire(provider.name)) return
        scope.launch {
            try {
                action()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                events.publishMessage(ManagedUiOwner.PROVIDER_AUTH, failure.userMessage())
            } finally {
                gate.release(provider.name)
            }
        }
    }
}
