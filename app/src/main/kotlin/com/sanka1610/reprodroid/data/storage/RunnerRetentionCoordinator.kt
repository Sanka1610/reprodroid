package com.sanka1610.reprodroid.data.storage

import com.sanka1610.reprodroid.data.local.ReproDroidDatabase
import com.sanka1610.reprodroid.data.local.RetentionHoldEntity
import com.sanka1610.reprodroid.data.local.RetentionHoldState
import com.sanka1610.reprodroid.data.network.RunnerApiClient
import com.sanka1610.reprodroid.data.network.V2ClientReferenceRequest
import com.sanka1610.reprodroid.data.network.V2OperationState
import com.sanka1610.reprodroid.data.network.V2ResourceRequest
import com.sanka1610.reprodroid.data.network.V2RetentionHoldRequest
import com.sanka1610.reprodroid.data.network.V2StorageSummaryResponse
import com.sanka1610.reprodroid.data.network.V2CleanupPreviewRequest
import com.sanka1610.reprodroid.data.network.V2CleanupPreviewResponse
import com.sanka1610.reprodroid.data.network.V2CleanupRunResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID

enum class RunnerStorageConnectionStatus { NOT_CHECKED, AVAILABLE, UNAVAILABLE, INCOMPATIBLE, RUNNER_CHANGED }

data class RunnerStorageConnectionState(
    val status: RunnerStorageConnectionStatus = RunnerStorageConnectionStatus.NOT_CHECKED,
    val runnerId: String? = null,
    val summary: V2StorageSummaryResponse? = null,
    val message: String? = null,
)

class RunnerRetentionCoordinator(
    private val database: ReproDroidDatabase,
    private val runnerApi: RunnerApiClient,
) {
    private val storageDao = database.storageDao()
    private val managedAppDao = database.managedAppDao()
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(RunnerStorageConnectionState())
    val state = mutableState.asStateFlow()
    private val mutableCleanupPreview = MutableStateFlow<V2CleanupPreviewResponse?>(null)
    val cleanupPreview = mutableCleanupPreview.asStateFlow()
    private val mutableCleanupRun = MutableStateFlow<V2CleanupRunResponse?>(null)
    val cleanupRun = mutableCleanupRun.asStateFlow()
    private var pendingPreviewKey: String? = null
    private var pendingExecute: Pair<String, String>? = null

    suspend fun syncCurrentComparisonHolds() = mutex.withLock {
        try {
            val capabilities = runnerApi.getV2Capabilities()
            val contracts = capabilities.capabilities.associate { it.id to it.contractVersion }
            if (contracts["foundation"] != 1 || contracts["storage-retention"] != 1) {
                mutableState.value = RunnerStorageConnectionState(
                    status = RunnerStorageConnectionStatus.INCOMPATIBLE,
                    runnerId = capabilities.runnerId,
                    message = "Runner does not advertise the required storage-retention@1 contract.",
                )
                return@withLock
            }
            val active = storageDao.getActiveRetentionHolds()
            if (active.any { it.runnerId != capabilities.runnerId }) {
                mutableState.value = RunnerStorageConnectionState(
                    status = RunnerStorageConnectionStatus.RUNNER_CHANGED,
                    runnerId = capabilities.runnerId,
                    message = "Runner identity changed while retention holds remain active.",
                )
                return@withLock
            }
            val desired = desiredHolds()
            for (target in desired) {
                if (active.any { it.matches(target) }) continue
                createHold(capabilities.runnerId, target)
            }
            val desiredKeys = desired.mapTo(mutableSetOf(), HoldTarget::key)
            for (hold in storageDao.getActiveRetentionHolds()) {
                if (hold.runnerId == capabilities.runnerId && hold.key() !in desiredKeys) releaseHold(hold)
            }
            val summary = runnerApi.getV2StorageSummary()
            if (summary.runnerId != capabilities.runnerId) {
                mutableState.value = RunnerStorageConnectionState(
                    status = RunnerStorageConnectionStatus.RUNNER_CHANGED,
                    runnerId = summary.runnerId,
                    message = "Runner identity changed during storage synchronization.",
                )
                return@withLock
            }
            mutableState.value = RunnerStorageConnectionState(
                status = RunnerStorageConnectionStatus.AVAILABLE,
                runnerId = capabilities.runnerId,
                summary = summary,
            )
        } catch (failure: Throwable) {
            if (failure is CancellationException) throw failure
            mutableState.value = RunnerStorageConnectionState(
                status = RunnerStorageConnectionStatus.UNAVAILABLE,
                runnerId = mutableState.value.runnerId,
                summary = null,
                message = failure.message ?: "Runner storage state is unavailable.",
            )
        }
    }

    suspend fun createCleanupPreview(): V2CleanupPreviewResponse = mutex.withLock {
        val runnerId = requireCompatibleRunner()
        val operation = runnerApi.createV2CleanupPreview(
            request = V2CleanupPreviewRequest(
                area = "RUNNER_JOB",
                resourceKinds = listOf("JOB_WORKSPACE", "JOB_ARTIFACT", "JOB_MANIFEST", "JOB_LOG", "SANDBOX_IMPORT"),
                // The Runner is authoritative for cleanup time. Keep the default cutoff behind
                // the Android clock so ordinary device/host skew cannot turn a safe preview into
                // a future-cutoff request.
                eligibleBefore = Instant.now().minusSeconds(CLEANUP_CLOCK_SKEW_SECONDS).toString(),
                resourceIds = emptyList(),
            ),
            idempotencyKey = pendingPreviewKey ?: UUID.randomUUID().toString().also { pendingPreviewKey = it },
        )
        check(
            operation.state == V2OperationState.COMPLETED && operation.kind == "cleanup-preview-create" &&
                operation.result?.type == "CLEANUP_PREVIEW",
        ) { "Runner did not complete the cleanup preview operation." }
        val preview = runnerApi.getV2CleanupPreview(operation.result.resourceId)
        check(preview.runnerId == runnerId) { "Runner identity changed while reading the cleanup preview." }
        mutableCleanupPreview.value = preview
        mutableCleanupRun.value = null
        pendingPreviewKey = null
        preview
    }

    suspend fun executeCleanup(itemIds: Set<String>): V2CleanupRunResponse = mutex.withLock {
        val preview = requireNotNull(mutableCleanupPreview.value) { "Create a Runner cleanup preview first." }
        val runnerId = requireCompatibleRunner()
        check(preview.runnerId == runnerId) { "Runner identity changed after the cleanup preview." }
        val selectionKey = itemIds.sorted().joinToString(",")
        val idempotencyKey = pendingExecute?.takeIf { it.first == selectionKey }?.second
            ?: UUID.randomUUID().toString().also { pendingExecute = selectionKey to it }
        val operation = runnerApi.executeV2Cleanup(
            previewId = preview.previewId,
            itemIds = itemIds.sorted(),
            idempotencyKey = idempotencyKey,
        )
        check(
            operation.state == V2OperationState.COMPLETED && operation.kind == "cleanup-execute" &&
                operation.result?.type == "CLEANUP_RUN",
        ) { "Runner did not complete the cleanup operation." }
        val run = runnerApi.getV2CleanupRun(operation.result.resourceId)
        check(run.previewId == preview.previewId) { "Runner cleanup result does not match the selected preview." }
        mutableCleanupRun.value = run
        pendingExecute = null
        run
    }

    private suspend fun requireCompatibleRunner(): String {
        val capabilities = runnerApi.getV2Capabilities()
        val contracts = capabilities.capabilities.associate { it.id to it.contractVersion }
        check(contracts["foundation"] == 1 && contracts["storage-retention"] == 1) {
            "Runner does not advertise storage-retention@1."
        }
        check(storageDao.getActiveRetentionHolds().all { it.runnerId == capabilities.runnerId }) {
            "Runner identity changed while retention holds remain active."
        }
        return capabilities.runnerId
    }

    private suspend fun desiredHolds(): Set<HoldTarget> = buildSet {
        managedAppDao.getRegisteredAppRecords().forEach { record ->
            val comparison = record.currentComparison ?: return@forEach
            add(HoldTarget("JOB", comparison.runnerJobId, comparison.comparisonRunId))
            comparison.localArtifactId?.let { add(HoldTarget("ARTIFACT", it, comparison.comparisonRunId)) }
            comparison.repeatRunnerJobId?.let { add(HoldTarget("JOB", it, comparison.comparisonRunId)) }
            comparison.repeatLocalArtifactId?.let { add(HoldTarget("ARTIFACT", it, comparison.comparisonRunId)) }
        }
    }

    private suspend fun createHold(runnerId: String, target: HoldTarget) {
        val response = runnerApi.createV2RetentionHold(
            request = V2RetentionHoldRequest(
                resource = V2ResourceRequest(target.resourceKind, target.resourceId),
                reason = HOLD_REASON,
                clientReference = V2ClientReferenceRequest("COMPARISON", target.comparisonRunId),
            ),
            idempotencyKey = stableKey("create", runnerId, target.key()),
        )
        if (
            response.state != V2OperationState.COMPLETED ||
            response.kind != "retention-hold-create" ||
            response.result?.type != "RETENTION_HOLD"
        ) {
            throw IllegalStateException("Runner did not complete the retention hold operation.")
        }
        storageDao.upsertRetentionHold(
            RetentionHoldEntity(
                holdId = response.result.resourceId,
                runnerId = runnerId,
                principalId = PRINCIPAL,
                resourceKind = target.resourceKind,
                resourceId = target.resourceId,
                reason = HOLD_REASON,
                clientReferenceType = "COMPARISON",
                clientReferenceId = target.comparisonRunId,
                requestSha256 = response.requestSha256,
                state = RetentionHoldState.ACTIVE.name,
                createdOperationId = response.operationId,
                releasedOperationId = null,
                createdAt = response.createdAt,
                releasedAt = null,
            ),
        )
    }

    private suspend fun releaseHold(hold: RetentionHoldEntity) {
        val reason = if (managedAppDao.getComparisonRun(hold.clientReferenceId) == null) {
            "APP_REMOVED"
        } else {
            "COMPARISON_REPLACED"
        }
        val response = runnerApi.releaseV2RetentionHold(
            holdId = hold.holdId,
            reason = reason,
            idempotencyKey = stableKey("release", hold.runnerId, hold.holdId),
        )
        if (
            response.state != V2OperationState.COMPLETED ||
            response.kind != "retention-hold-release" ||
            response.result?.type != "RETENTION_HOLD" ||
            response.result.resourceId != hold.holdId
        ) {
            throw IllegalStateException("Runner did not complete the retention hold release.")
        }
        storageDao.upsertRetentionHold(
            hold.copy(
                state = RetentionHoldState.RELEASED.name,
                releasedOperationId = response.operationId,
                releasedAt = response.updatedAt,
            ),
        )
    }

    private fun RetentionHoldEntity.matches(target: HoldTarget): Boolean =
        resourceKind == target.resourceKind && resourceId == target.resourceId &&
            reason == HOLD_REASON && clientReferenceType == "COMPARISON" &&
            clientReferenceId == target.comparisonRunId

    private fun RetentionHoldEntity.key() = HoldTarget(resourceKind, resourceId, clientReferenceId).key()
    private fun stableKey(vararg parts: String): String = UUID.nameUUIDFromBytes(
        parts.joinToString("|").toByteArray(StandardCharsets.UTF_8),
    ).toString()

    private data class HoldTarget(val resourceKind: String, val resourceId: String, val comparisonRunId: String) {
        fun key() = "$resourceKind|$resourceId|$comparisonRunId"
    }

    private companion object {
        const val PRINCIPAL = "local-development"
        const val HOLD_REASON = "CURRENT_COMPARISON"
        const val CLEANUP_CLOCK_SKEW_SECONDS = 60L
    }
}
