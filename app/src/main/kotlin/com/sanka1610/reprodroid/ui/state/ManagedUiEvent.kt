package com.sanka1610.reprodroid.ui.state

import com.sanka1610.reprodroid.ui.navigation.ReproDroidRoute
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ManagedUiOwner {
    APPS,
    REGISTRATION,
    APP_DETAIL,
    RELEASE,
    STORAGE,
    RUNNER,
    TOOLCHAIN,
    DELETION_EXPORT,
}

data class ManagedUiMessage(
    val id: Long,
    val owner: ManagedUiOwner,
    val code: ManagedUiMessageCode,
    val text: String,
)

enum class ManagedUiMessageCode {
    ALREADY_REGISTERED_PRIMARY,
    DIFFERENT_REPOSITORY,
    RATE_LIMIT,
    NOT_FOUND,
    STALE_STATE,
    OPERATION_FAILED,
}

enum class ManagedUiResultKind {
    REGISTERED,
    REGISTRATION_RESUMED,
    METADATA_SAVED,
    SOURCE_SAVED,
    PREFERENCES_SAVED,
    TRACKING_STOPPED,
    TRACKING_STOPPED_AFTER_UNINSTALL,
    UNINSTALL_CONFIRMED,
    RELEASE_CANDIDATE_READY,
    DELETION_COMPLETED,
}

data class ManagedUiResult(
    val id: Long = 0,
    val owner: ManagedUiOwner,
    val kind: ManagedUiResultKind,
    val originRoute: String,
    val destination: ReproDroidRoute,
    val registeredAppId: String? = null,
    val candidateId: String? = null,
    val requiresRemovalTarget: Boolean = false,
    val resetRegistrationDraft: Boolean = false,
    val clearRemovalTarget: Boolean = false,
)

data class ManagedUiResultDisposition(
    val destination: ReproDroidRoute,
    val resetRegistrationDraft: Boolean,
    val clearRemovalTarget: Boolean,
)

fun ManagedUiResult.relevantDisposition(
    currentRoute: ReproDroidRoute,
    removalTargetId: String?,
    pendingCandidateId: String? = null,
): ManagedUiResultDisposition? {
    if (currentRoute.encode() != originRoute) return null
    if (currentRoute.appId != null && currentRoute.appId != registeredAppId) return null
    if (requiresRemovalTarget && removalTargetId != registeredAppId) return null
    if (candidateId != null && candidateId != pendingCandidateId) return null
    return ManagedUiResultDisposition(destination, resetRegistrationDraft, clearRemovalTarget)
}

internal class ManagedUiEventStore {
    private val nextId = AtomicLong(0)
    private val mutableMessages = MutableStateFlow<ManagedUiMessage?>(null)
    private val mutableResults = MutableStateFlow<List<ManagedUiResult>>(emptyList())

    val message = mutableMessages.asStateFlow()
    val results = mutableResults.asStateFlow()

    fun publishMessage(owner: ManagedUiOwner, text: String) {
        mutableMessages.value = ManagedUiMessage(
            id = nextId.incrementAndGet(),
            owner = owner,
            code = text.toManagedUiMessageCode(),
            text = text,
        )
    }

    fun acknowledgeMessage(id: Long) {
        if (mutableMessages.value?.id == id) mutableMessages.value = null
    }

    fun publishResult(result: ManagedUiResult) {
        mutableResults.value += result.copy(id = nextId.incrementAndGet())
    }

    fun acknowledgeResult(id: Long) {
        mutableResults.value = mutableResults.value.filterNot { it.id == id }
    }
}

private fun String.toManagedUiMessageCode(): ManagedUiMessageCode = when {
    contains("already registered as the primary", ignoreCase = true) ->
        ManagedUiMessageCode.ALREADY_REGISTERED_PRIMARY
    contains("different repository", ignoreCase = true) -> ManagedUiMessageCode.DIFFERENT_REPOSITORY
    contains("rate limit", ignoreCase = true) -> ManagedUiMessageCode.RATE_LIMIT
    contains("not found", ignoreCase = true) -> ManagedUiMessageCode.NOT_FOUND
    contains("reload", ignoreCase = true) -> ManagedUiMessageCode.STALE_STATE
    else -> ManagedUiMessageCode.OPERATION_FAILED
}
