package com.sanka1610.reprodroid.ui.delegate

import android.net.Uri
import com.sanka1610.reprodroid.ReproDroidApplication
import com.sanka1610.reprodroid.data.local.*
import com.sanka1610.reprodroid.ui.navigation.ReproDroidRoute
import com.sanka1610.reprodroid.ui.state.*
import com.sanka1610.reprodroid.work.ReleaseCheckScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

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
