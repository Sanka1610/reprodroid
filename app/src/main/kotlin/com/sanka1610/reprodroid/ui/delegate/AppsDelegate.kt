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
        repository.ensureSettings(initializeSelfRegistration = true)
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
