package com.sanka1610.reprodroid.ui.delegate

import com.sanka1610.reprodroid.data.local.*
import com.sanka1610.reprodroid.data.log.AppLogStore
import com.sanka1610.reprodroid.ui.state.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

internal data class FeatureEvents(
    val message: ManagedUiMessage? = null,
    val results: List<ManagedUiResult> = emptyList(),
)

internal fun ManagedUiEventStore.observe(owner: ManagedUiOwner): Flow<FeatureEvents> =
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
    ): Job? {
        if (!gate.tryAcquire(registeredAppId)) return null
        return scope.launch {
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


internal fun Throwable.userMessage(): String = message ?: "ReproDroid operation failed."
