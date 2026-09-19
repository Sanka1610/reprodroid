package com.sanka1610.reprodroid.ui.delegate

import android.net.Uri
import com.sanka1610.reprodroid.ReproDroidApplication
import com.sanka1610.reprodroid.data.local.*
import com.sanka1610.reprodroid.ui.state.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
