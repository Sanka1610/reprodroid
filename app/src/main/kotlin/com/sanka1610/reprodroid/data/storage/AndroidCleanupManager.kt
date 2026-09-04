package com.sanka1610.reprodroid.data.storage

import android.content.Context
import androidx.room.withTransaction
import com.sanka1610.reprodroid.data.local.CleanupItemEntity
import com.sanka1610.reprodroid.data.local.CleanupRunEntity
import com.sanka1610.reprodroid.data.local.LocalCleanupItemResult
import com.sanka1610.reprodroid.data.local.LocalCleanupRunState
import com.sanka1610.reprodroid.data.local.ReferenceDownloadStatus
import com.sanka1610.reprodroid.data.local.ReproDroidDatabase
import com.sanka1610.reprodroid.data.local.ResourceAvailabilityEntity
import com.sanka1610.reprodroid.data.local.ResourceAvailabilityState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.erdtman.jcs.JsonCanonicalizer
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

data class AndroidCleanupItem(
    val itemId: String,
    val resourceKind: String,
    val resourceId: String,
    val observedBytes: Long,
    val observedToken: String,
    val eligibleAt: String,
    val protectionReasons: List<String>,
    val result: String? = null,
    val releasedBytes: Long = 0,
    val reasonCode: String? = null,
)

data class AndroidCleanupPreview(
    val cleanupRunId: String,
    val previewId: String,
    val state: String,
    val expiresAt: String,
    val truncated: Boolean,
    val items: List<AndroidCleanupItem>,
)

class AndroidCleanupManager(
    context: Context,
    private val database: ReproDroidDatabase,
    private val deleteFile: (Path) -> Unit = Files::delete,
) {
    private val filesRoot = context.filesDir.toPath().toAbsolutePath().normalize()
    private val storageDao = database.storageDao()
    private val managedAppDao = database.managedAppDao()
    private val mutex = Mutex()

    suspend fun createPreview(
        eligibleBefore: Instant = Instant.now(),
        resourceIds: Set<String> = emptySet(),
    ): AndroidCleanupPreview = mutex.withLock {
        reconcileInterruptedRunsLocked()
        check(storageDao.getCleanupReconciliationCount() == 0) {
            "A prior Android cleanup requires reconciliation."
        }
        require(resourceIds.size <= 100) { "At most 100 resource IDs can be filtered." }
        require(!eligibleBefore.isAfter(Instant.now())) { "The cleanup cutoff must not be in the future." }
        val protections = protections()
        val candidates = mutableListOf<Candidate>()
        for (availability in storageDao.getAvailability()) {
            if (availability.ownerType != AndroidStorageManager.OWNER_ANDROID) continue
            if (availability.resourceKind !in CLEANUP_KINDS) continue
            if (resourceIds.isNotEmpty() && availability.resourceId !in resourceIds) continue
            candidate(availability, eligibleBefore, protections)?.let(candidates::add)
        }
        candidates.sortWith(compareBy(Candidate::kind, Candidate::resourceId))
        val truncated = candidates.size > MAX_PREVIEW_ITEMS
        val selected = candidates.take(MAX_PREVIEW_ITEMS)
        val cleanupRunId = UUID.randomUUID().toString()
        val previewId = UUID.randomUUID().toString()
        val now = Instant.now()
        val run = CleanupRunEntity(
            cleanupRunId = cleanupRunId,
            previewId = previewId,
            ownerType = AndroidStorageManager.OWNER_ANDROID,
            ownerId = AndroidStorageManager.OWNER_ANDROID_LOCAL,
            area = AndroidStorageManager.AREA_ANDROID,
            state = LocalCleanupRunState.PREVIEWED.name,
            filterSha256 = filterSha256(eligibleBefore, resourceIds),
            truncated = truncated,
            expiresAt = now.plus(Duration.ofMinutes(30)).toString(),
            releasedBytes = 0,
            startedAt = null,
            finishedAt = null,
            createdAt = now.toString(),
        )
        val items = selected.map { candidate ->
            CleanupItemEntity(
                itemId = UUID.randomUUID().toString(),
                cleanupRunId = cleanupRunId,
                resourceKind = candidate.kind,
                resourceId = candidate.resourceId,
                observedBytes = candidate.observation.bytes,
                observedToken = candidate.observation.token,
                eligibleAt = candidate.eligibleAt,
                protectionReasons = candidate.protections.sorted().joinToString(","),
                selected = false,
                result = null,
                releasedBytes = 0,
                reasonCode = null,
                reasonMessage = null,
            )
        }
        database.withTransaction {
            storageDao.upsertCleanupRun(run)
            storageDao.upsertCleanupItems(items)
        }
        run.toPreview(items)
    }

    suspend fun getPreview(previewId: String): AndroidCleanupPreview = mutex.withLock {
        val run = storageDao.getCleanupRunByPreview(previewId)
            ?: throw IllegalArgumentException("Cleanup preview was not found.")
        run.toPreview(storageDao.getCleanupItems(run.cleanupRunId))
    }

    suspend fun execute(previewId: String, itemIds: Set<String>): AndroidCleanupPreview = mutex.withLock {
        reconcileInterruptedRunsLocked()
        require(itemIds.isNotEmpty() && itemIds.size <= 100) { "Select 1 to 100 cleanup items." }
        var run = storageDao.getCleanupRunByPreview(previewId)
            ?: throw IllegalArgumentException("Cleanup preview was not found.")
        check(run.state == LocalCleanupRunState.PREVIEWED.name) { "Cleanup preview was already executed." }
        check(storageDao.getReservationReconciliationCount() == 0) {
            "A prior Android storage operation requires reconciliation."
        }
        check(storageDao.getCleanupReconciliationCount() == 0) {
            "A prior Android cleanup requires reconciliation."
        }
        check(!run.truncated) { "A truncated cleanup preview cannot be executed." }
        check(Instant.parse(run.expiresAt).isAfter(Instant.now())) { "Cleanup preview expired." }
        val items = storageDao.getCleanupItems(run.cleanupRunId)
        check(itemIds.all { id -> items.any { it.itemId == id } }) { "A selected item is outside this preview." }
        run = run.copy(
            state = LocalCleanupRunState.APPLYING.name,
            startedAt = Instant.now().toString(),
        )
        database.withTransaction {
            storageDao.upsertCleanupRun(run)
            items.filter { it.itemId in itemIds }.forEach { storageDao.upsertCleanupItem(it.copy(selected = true)) }
        }

        for (item in items.filter { it.itemId in itemIds }) {
            val result = evaluate(item)
            try {
                database.withTransaction {
                    storageDao.upsertCleanupItem(
                        item.copy(
                            selected = true,
                            result = result.result.name,
                            releasedBytes = result.releasedBytes,
                            reasonCode = result.reasonCode,
                            reasonMessage = result.reasonMessage,
                        ),
                    )
                    if (result.result in setOf(LocalCleanupItemResult.DELETED, LocalCleanupItemResult.ALREADY_MISSING)) {
                        val previous = storageDao.getAvailability(
                            AndroidStorageManager.OWNER_ANDROID,
                            AndroidStorageManager.OWNER_ANDROID_LOCAL,
                            item.resourceKind,
                            item.resourceId,
                        )
                        storageDao.upsertAvailability(
                            requireNotNull(previous).copy(
                                state = if (result.result == LocalCleanupItemResult.DELETED) {
                                    ResourceAvailabilityState.DELETED.name
                                } else {
                                    ResourceAvailabilityState.MISSING.name
                                },
                                observedBytes = 0,
                                checkedAt = Instant.now().toString(),
                                deletionRunId = run.cleanupRunId,
                                deletionReason = "MANUAL_CLEANUP",
                            ),
                        )
                    }
                }
            } catch (failure: Throwable) {
                if (result.result in setOf(LocalCleanupItemResult.DELETED, LocalCleanupItemResult.ALREADY_MISSING)) {
                    val reconciled = run.copy(
                        state = LocalCleanupRunState.RECONCILIATION_REQUIRED.name,
                        finishedAt = Instant.now().toString(),
                    )
                    runCatching { storageDao.upsertCleanupRun(reconciled) }
                    return@withLock reconciled.toPreview(storageDao.getCleanupItems(run.cleanupRunId))
                }
                throw failure
            }
        }
        val persisted = storageDao.getCleanupItems(run.cleanupRunId).filter { it.selected }
        val released = persisted.fold(0L) { total, item -> Math.addExact(total, item.releasedBytes) }
        val completed = run.copy(
            state = if (persisted.all { it.result in setOf("DELETED", "ALREADY_MISSING") }) {
                LocalCleanupRunState.COMPLETE.name
            } else {
                LocalCleanupRunState.PARTIAL.name
            },
            releasedBytes = released,
            finishedAt = Instant.now().toString(),
        )
        storageDao.upsertCleanupRun(completed)
        completed.toPreview(storageDao.getCleanupItems(run.cleanupRunId))
    }

    suspend fun reconcileInterruptedRuns() = mutex.withLock {
        reconcileInterruptedRunsLocked()
    }

    private suspend fun reconcileInterruptedRunsLocked() {
        storageDao.getCleanupRunsToReconcile().forEach { applying ->
            val items = storageDao.getCleanupItems(applying.cleanupRunId)
            for (item in items.filter { it.selected && it.result == null }) {
                val observation = observe(item.resourceKind, item.resourceId)
                val result = when {
                    !observation.exists -> ExecutionResult(LocalCleanupItemResult.ALREADY_MISSING, 0, null, null)
                    observation.unsafe || observation.token != item.observedToken -> ExecutionResult(
                        LocalCleanupItemResult.SKIPPED_PROTECTED,
                        0,
                        "RESOURCE_CHANGED",
                        publicMessage("RESOURCE_CHANGED"),
                    )
                    else -> ExecutionResult(
                        LocalCleanupItemResult.FAILED,
                        0,
                        "PROCESS_RESTARTED",
                        "Cleanup stopped before this resource could be confirmed deleted.",
                    )
                }
                database.withTransaction {
                    storageDao.upsertCleanupItem(
                        item.copy(
                            result = result.result.name,
                            releasedBytes = result.releasedBytes,
                            reasonCode = result.reasonCode,
                            reasonMessage = result.reasonMessage,
                        ),
                    )
                    if (!observation.exists) {
                        storageDao.getAvailability(
                            AndroidStorageManager.OWNER_ANDROID,
                            AndroidStorageManager.OWNER_ANDROID_LOCAL,
                            item.resourceKind,
                            item.resourceId,
                        )?.let { previous ->
                            storageDao.upsertAvailability(
                                previous.copy(
                                    state = ResourceAvailabilityState.MISSING.name,
                                    observedBytes = 0,
                                    checkedAt = Instant.now().toString(),
                                    deletionRunId = applying.cleanupRunId,
                                    deletionReason = "INTERRUPTED_CLEANUP_RECONCILIATION",
                                ),
                            )
                        }
                    }
                }
            }
            val selected = storageDao.getCleanupItems(applying.cleanupRunId).filter { it.selected }
            val released = selected.fold(0L) { total, item -> Math.addExact(total, item.releasedBytes) }
            storageDao.upsertCleanupRun(
                applying.copy(
                    state = if (selected.isNotEmpty() && selected.all {
                        it.result in setOf("DELETED", "ALREADY_MISSING")
                    }) {
                        LocalCleanupRunState.COMPLETE.name
                    } else {
                        LocalCleanupRunState.PARTIAL.name
                    },
                    releasedBytes = released,
                    finishedAt = Instant.now().toString(),
                ),
            )
        }
    }

    private suspend fun candidate(
        availability: ResourceAvailabilityEntity,
        cutoff: Instant,
        protections: Map<Pair<String, String>, Set<String>>,
    ): Candidate? {
        val lastUsed = runCatching { Instant.parse(availability.lastUsedAt ?: availability.checkedAt) }.getOrNull() ?: return null
        val eligibleAt = lastUsed.plus(if (availability.resourceKind == "AUDIT_EXPORT") 90 else 30, ChronoUnit.DAYS)
        if (eligibleAt > cutoff) return null
        val observation = observe(availability.resourceKind, availability.resourceId)
        val effective = protections[availability.resourceKind to availability.resourceId].orEmpty().toMutableSet()
        if (observation.unsafe) effective += "RESOURCE_CHANGED"
        return Candidate(availability.resourceKind, availability.resourceId, eligibleAt.toString(), observation, effective)
    }

    private suspend fun evaluate(item: CleanupItemEntity): ExecutionResult {
        val currentProtections = protections()[item.resourceKind to item.resourceId].orEmpty().toMutableSet()
        val observation = observe(item.resourceKind, item.resourceId)
        if (observation.unsafe) currentProtections += "RESOURCE_CHANGED"
        if (currentProtections.isNotEmpty()) {
            val reason = currentProtections.sorted().first()
            return ExecutionResult(LocalCleanupItemResult.SKIPPED_PROTECTED, 0, reason, publicMessage(reason))
        }
        if (!observation.exists) return ExecutionResult(LocalCleanupItemResult.ALREADY_MISSING, 0, null, null)
        if (observation.token != item.observedToken) {
            return ExecutionResult(
                LocalCleanupItemResult.SKIPPED_PROTECTED,
                0,
                "RESOURCE_CHANGED",
                publicMessage("RESOURCE_CHANGED"),
            )
        }
        return try {
            withContext(Dispatchers.IO) { deleteFile(observation.path) }
            ExecutionResult(LocalCleanupItemResult.DELETED, observation.bytes, null, null)
        } catch (_: Throwable) {
            ExecutionResult(LocalCleanupItemResult.FAILED, 0, "DELETE_FAILED", "The resource could not be deleted safely.")
        }
    }

    private suspend fun protections(): Map<Pair<String, String>, Set<String>> {
        val result = mutableMapOf<Pair<String, String>, MutableSet<String>>()
        fun protect(kind: String, id: String?, reason: String) {
            if (id != null) result.getOrPut(kind to id, ::mutableSetOf) += reason
        }
        val records = managedAppDao.getRegisteredAppRecords()
        records.forEach { record ->
            record.latestRelease?.selectedAsset?.releaseAssetId?.let { assetId ->
                protect("REFERENCE_APK", assetId, "CURRENT_RELEASE")
                protect("REFERENCE_ICON", assetId, "CURRENT_RELEASE")
            }
            record.currentComparison?.let { comparison ->
                protect("REFERENCE_APK", comparison.referenceAssetId, "CURRENT_COMPARISON_REFERENCE")
                protect("REFERENCE_ICON", comparison.referenceAssetId, "CURRENT_COMPARISON_REFERENCE")
                protect("RUNNER_APK", comparison.localArtifactId, "CURRENT_COMPARISON_ARTIFACT")
                protect("RUNNER_APK", comparison.repeatLocalArtifactId, "CURRENT_COMPARISON_ARTIFACT")
            }
            record.comparisons.filter { it.status != "COMPLETED" }.forEach { comparison ->
                protect("REFERENCE_APK", comparison.referenceAssetId, "COMPARISON_ACTIVE")
                protect("RUNNER_APK", comparison.localArtifactId, "COMPARISON_ACTIVE")
                protect("RUNNER_APK", comparison.repeatLocalArtifactId, "COMPARISON_ACTIVE")
            }
        }
        storageDao.getReleaseAssets().filter { it.downloadStatus == ReferenceDownloadStatus.DOWNLOADING.name }.forEach {
            protect("REFERENCE_APK", it.releaseAssetId, "DOWNLOAD_ACTIVE")
            protect("REFERENCE_ICON", it.releaseAssetId, "DOWNLOAD_ACTIVE")
        }
        storageDao.getArtifacts().filter { it.downloadStatus == "DOWNLOADING" }.forEach {
            protect("RUNNER_APK", it.artifactId, "DOWNLOAD_ACTIVE")
        }
        storageDao.getActiveReleaseInstallAssetIds().forEach { protect("REFERENCE_APK", it, "INSTALL_ACTIVE") }
        storageDao.getActiveInstallArtifactIds().forEach { protect("RUNNER_APK", it, "INSTALL_ACTIVE") }
        storageDao.getActiveReservations().forEach { protect(it.resourceKind, it.resourceId, "ACTIVE_RESERVATION") }
        storageDao.getActiveAuditExports().forEach { protect("AUDIT_EXPORT", it.auditExportId, "EXPORT_ACTIVE") }
        return result.mapValues { it.value.toSet() }
    }

    private suspend fun observe(kind: String, id: String): Observation {
        val path = when (kind) {
            "REFERENCE_APK" -> filesRoot.resolve("reference-apks").resolve("$id.apk")
            "REFERENCE_ICON" -> filesRoot.resolve("reference-icons").resolve("$id.png")
            "RUNNER_APK" -> {
                val artifact = storageDao.getArtifact(id) ?: return missingObservation(kind, id, filesRoot)
                val relative = artifact.localContentPath ?: return missingObservation(kind, id, filesRoot)
                val expected = "apks/${safeStorageName(artifact.jobId)}/${safeStorageName(id)}.apk"
                if (relative != expected) return unsafeObservation(kind, id, filesRoot)
                filesRoot.resolve(relative)
            }
            "AUDIT_EXPORT" -> filesRoot.resolve("audit-exports").resolve("${safeStorageName(id)}.rdaudit.json")
            else -> return unsafeObservation(kind, id, filesRoot)
        }.toAbsolutePath().normalize()
        if (!path.startsWith(filesRoot)) return unsafeObservation(kind, id, path)
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return missingObservation(kind, id, path)
        val attributes = runCatching {
            Files.readAttributes(path, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        }.getOrElse { return unsafeObservation(kind, id, path) }
        if (!attributes.isRegularFile || attributes.isSymbolicLink) return unsafeObservation(kind, id, path)
        val contentSha256 = boundedSha256(path) ?: return unsafeObservation(kind, id, path)
        val token = sha256(
            "$kind|$id|${attributes.size()}|${attributes.lastModifiedTime().toMillis()}|${attributes.fileKey()}|$contentSha256"
                .toByteArray(),
        )
        return Observation(path, attributes.size(), token, unsafe = false, exists = true)
    }

    private fun boundedSha256(path: Path): String? {
        val deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos()
        val digest = MessageDigest.getInstance("SHA-256")
        return runCatching {
            Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS).use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    if (System.nanoTime() > deadline) return null
                    val read = input.read(buffer)
                    if (read == -1) break
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        }.getOrNull()
    }

    private fun missingObservation(kind: String, id: String, path: Path) =
        Observation(path, 0, sha256("$kind|$id|MISSING".toByteArray()), unsafe = false, exists = false)

    private fun unsafeObservation(kind: String, id: String, path: Path) =
        Observation(path, 0, sha256("$kind|$id|UNSAFE".toByteArray()), unsafe = true, exists = true)

    private fun CleanupRunEntity.toPreview(items: List<CleanupItemEntity>) = AndroidCleanupPreview(
        cleanupRunId = cleanupRunId,
        previewId = previewId,
        state = state,
        expiresAt = expiresAt,
        truncated = truncated,
        items = items.map {
            AndroidCleanupItem(
                itemId = it.itemId,
                resourceKind = it.resourceKind,
                resourceId = it.resourceId,
                observedBytes = it.observedBytes,
                observedToken = it.observedToken,
                eligibleAt = it.eligibleAt,
                protectionReasons = it.protectionReasons.split(',').filter(String::isNotBlank),
                result = it.result,
                releasedBytes = it.releasedBytes,
                reasonCode = it.reasonCode,
            )
        },
    )

    private fun filterSha256(cutoff: Instant, ids: Set<String>): String {
        val payload = buildJsonObject {
            put("area", AndroidStorageManager.AREA_ANDROID)
            put("eligibleBefore", cutoff.toString())
            put("resourceIds", ids.sorted().joinToString(","))
        }
        return sha256(JsonCanonicalizer(payload.toString()).encodedUTF8)
    }

    private fun publicMessage(code: String): String = when (code) {
        "CURRENT_RELEASE" -> "The resource belongs to the current release."
        "CURRENT_COMPARISON_REFERENCE" -> "The resource is the current comparison reference."
        "CURRENT_COMPARISON_ARTIFACT" -> "The resource is a current comparison artifact."
        "DOWNLOAD_ACTIVE" -> "The resource has an active download."
        "INSTALL_ACTIVE" -> "The resource has an active install."
        "COMPARISON_ACTIVE" -> "The resource has an active comparison."
        "ACTIVE_RESERVATION" -> "The resource has an active storage reservation."
        "EXPORT_ACTIVE" -> "The audit export is staged or being copied."
        else -> "The resource changed after the cleanup preview."
    }

    private fun safeStorageName(value: String): String = sha256(value.toByteArray())
    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private data class Candidate(
        val kind: String,
        val resourceId: String,
        val eligibleAt: String,
        val observation: Observation,
        val protections: Set<String>,
    )

    private data class Observation(
        val path: Path,
        val bytes: Long,
        val token: String,
        val unsafe: Boolean,
        val exists: Boolean,
    )

    private data class ExecutionResult(
        val result: LocalCleanupItemResult,
        val releasedBytes: Long,
        val reasonCode: String?,
        val reasonMessage: String?,
    )

    private companion object {
        val CLEANUP_KINDS = setOf("REFERENCE_APK", "REFERENCE_ICON", "RUNNER_APK", "AUDIT_EXPORT")
        const val MAX_PREVIEW_ITEMS = 1_000
    }
}
