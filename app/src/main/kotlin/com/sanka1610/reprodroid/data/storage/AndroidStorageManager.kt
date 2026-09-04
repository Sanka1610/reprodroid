package com.sanka1610.reprodroid.data.storage

import android.content.Context
import androidx.room.withTransaction
import com.sanka1610.reprodroid.data.local.ArtifactEntity
import com.sanka1610.reprodroid.data.local.GlobalSettingsEntity
import com.sanka1610.reprodroid.data.local.ReferenceDownloadStatus
import com.sanka1610.reprodroid.data.local.ReleaseAssetEntity
import com.sanka1610.reprodroid.data.local.ReproDroidDatabase
import com.sanka1610.reprodroid.data.local.ResourceAvailabilityEntity
import com.sanka1610.reprodroid.data.local.ResourceAvailabilityState
import com.sanka1610.reprodroid.data.local.StorageReservationEntity
import com.sanka1610.reprodroid.data.local.StorageReservationState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.UUID

data class AndroidStorageSummary(
    val budgetBytes: Long,
    val usedBytes: Long,
    val reservedBytes: Long,
    val unclassifiedBytes: Long?,
    val usableBytes: Long?,
    val warningPercent: Int,
    val state: String,
    val measurementState: String,
    val measuredAt: String,
)

class AndroidStorageManager(
    context: Context,
    private val database: ReproDroidDatabase,
) {
    private val filesRoot = context.filesDir.toPath().toAbsolutePath().normalize()
    private val storageDao = database.storageDao()
    private val managedAppDao = database.managedAppDao()
    private val roots = listOf("reference-apks", "reference-icons", "apks", "audit-exports").map(filesRoot::resolve)
    private val mutationMutex = Mutex()

    suspend fun reconcileAvailability() = mutationMutex.withLock {
        withContext(Dispatchers.IO) {
            storageDao.getReleaseAssets().forEach { reconcileReleaseAsset(it) }
            storageDao.getVerifiedArtifacts().forEach { reconcileArtifact(it) }
            storageDao.getRetainedAuditExports().forEach { export ->
                val expectedRelative = "audit-exports/${safeStorageName(export.auditExportId)}.rdaudit.json"
                val observation = if (export.stagingName != expectedRelative) {
                    Observation(ResourceAvailabilityState.CORRUPT, null, export.payloadSha256)
                } else {
                    observeFile(
                        filesRoot.resolve(expectedRelative).normalize(),
                        export.sizeBytes,
                        export.bundleSha256,
                        hashRequired = true,
                    )
                }
                storageDao.upsertAvailability(
                    observation.toEntity("AUDIT_EXPORT", export.auditExportId, export.updatedAt),
                )
            }
        }
        reconcileReservationsAfterRestart()
    }

    suspend fun summary(): AndroidStorageSummary = mutationMutex.withLock {
        val measuredAt = Instant.now().toString()
        val owned = withContext(Dispatchers.IO) { measure(roots) }
        val total = withContext(Dispatchers.IO) { measure(listOf(filesRoot)) }
        val settings = managedAppDao.getGlobalSettings() ?: defaultSettings()
        val reserved = storageDao.getActiveReservedBytes(AREA_ANDROID)
        val usable = withContext(Dispatchers.IO) { readUsableSpace() }
        val state = when {
            owned.state == "FAILED" || usable == null -> "STORAGE_UNAVAILABLE"
            owned.bytes > settings.androidStorageBudgetBytes -> "OVER_BUDGET"
            reachesWarning(owned.bytes, reserved, settings.androidStorageBudgetBytes, settings.storageWarningPercent) -> "WARNING"
            else -> "OK"
        }
        AndroidStorageSummary(
            budgetBytes = settings.androidStorageBudgetBytes,
            usedBytes = owned.bytes,
            reservedBytes = reserved,
            unclassifiedBytes = if (owned.state == "COMPLETE" && total.state == "COMPLETE") {
                (total.bytes - owned.bytes).coerceAtLeast(0)
            } else {
                null
            },
            usableBytes = usable,
            warningPercent = settings.storageWarningPercent,
            state = state,
            measurementState = owned.state,
            measuredAt = measuredAt,
        )
    }

    suspend fun reserveDownload(resourceKind: String, resourceId: String, expectedBytes: Long): StorageReservationEntity =
        mutationMutex.withLock {
            require(expectedBytes > 0) { "Expected download bytes must be positive." }
            reserve(PURPOSE_DOWNLOAD, resourceKind, resourceId, requiredDownloadBytes(expectedBytes))
        }

    suspend fun reserveWrite(resourceKind: String, resourceId: String, requiredBytes: Long): StorageReservationEntity =
        mutationMutex.withLock {
            require(requiredBytes > 0) { "Required write bytes must be positive." }
            reserve(PURPOSE_EXPORT, resourceKind, resourceId, requiredBytes)
        }

    private suspend fun reserve(
        purpose: String,
        resourceKind: String,
        resourceId: String,
        requiredBytes: Long,
    ): StorageReservationEntity {
            check(storageDao.getReservationReconciliationCount() == 0) {
                "A prior Android storage operation requires reconciliation."
            }
            storageDao.getActiveReservation(AREA_ANDROID, purpose, resourceKind, resourceId)?.let { return it }
            val owned = withContext(Dispatchers.IO) { measure(roots) }
            check(owned.state == "COMPLETE") { "Android storage usage is unavailable or incomplete." }
            val settings = managedAppDao.getGlobalSettings() ?: defaultSettings()
            val reserved = storageDao.getActiveReservedBytes(AREA_ANDROID)
            val total = addExact(owned.bytes, reserved, requiredBytes)
            val usable = withContext(Dispatchers.IO) { readUsableSpace() }
            check(usable != null) { "Android usable storage is unavailable." }
            check(total <= settings.androidStorageBudgetBytes && usable >= addExact(requiredBytes, RECOVERY_RESERVE_BYTES)) {
                "Android storage budget or usable space is insufficient."
            }
            val now = Instant.now().toString()
            val reservation = StorageReservationEntity(
                reservationId = UUID.randomUUID().toString(),
                area = AREA_ANDROID,
                purpose = purpose,
                resourceKind = resourceKind,
                resourceId = resourceId,
                requestedBytes = requiredBytes,
                principalId = LOCAL_PRINCIPAL,
                operationId = UUID.randomUUID().toString(),
                state = StorageReservationState.ACTIVE.name,
                createdAt = now,
                updatedAt = now,
            )
            storageDao.upsertReservation(reservation)
            return reservation
    }

    private fun readUsableSpace(): Long? = runCatching {
        filesRoot.toFile().usableSpace
    }.getOrNull()

    suspend fun recordPresentAndConsume(
        reservation: StorageReservationEntity,
        observedBytes: Long,
        knownSha256: String?,
    ) = mutationMutex.withLock {
        val now = Instant.now().toString()
        database.withTransaction {
            storageDao.upsertAvailability(
                ResourceAvailabilityEntity(
                    ownerType = OWNER_ANDROID,
                    ownerId = OWNER_ANDROID_LOCAL,
                    resourceKind = reservation.resourceKind,
                    resourceId = reservation.resourceId,
                    state = ResourceAvailabilityState.PRESENT.name,
                    observedBytes = observedBytes,
                    knownSha256 = knownSha256,
                    lastUsedAt = now,
                    checkedAt = now,
                    deletionRunId = null,
                    deletionReason = null,
                ),
            )
            storageDao.upsertReservation(
                reservation.copy(state = StorageReservationState.CONSUMED.name, updatedAt = now),
            )
        }
    }

    suspend fun releaseReservation(reservation: StorageReservationEntity) = mutationMutex.withLock {
        storageDao.upsertReservation(
            reservation.copy(state = StorageReservationState.RELEASED.name, updatedAt = Instant.now().toString()),
        )
    }

    suspend fun recordDownloadFailure(reservation: StorageReservationEntity, finalBytesMayExist: Boolean) =
        mutationMutex.withLock {
            storageDao.upsertReservation(
                reservation.copy(
                    state = if (finalBytesMayExist) {
                        StorageReservationState.RECONCILIATION_REQUIRED.name
                    } else {
                        StorageReservationState.RELEASED.name
                    },
                    updatedAt = Instant.now().toString(),
                ),
            )
        }

    suspend fun markUsed(resourceKind: String, resourceId: String) {
        mutationMutex.withLock {
            val current = storageDao.getAvailability(
                OWNER_ANDROID,
                OWNER_ANDROID_LOCAL,
                resourceKind,
                resourceId,
            ) ?: return@withLock
            storageDao.upsertAvailability(current.copy(lastUsedAt = Instant.now().toString()))
        }
    }

    suspend fun requirePresent(resourceKind: String, resourceId: String) {
        mutationMutex.withLock {
            check(revalidateInstallable(resourceKind, resourceId)) {
                "The selected APK bytes are not presently available and verified."
            }
        }
    }

    suspend fun isPresent(resourceKind: String, resourceId: String): Boolean = mutationMutex.withLock {
        revalidateInstallable(resourceKind, resourceId)
    }

    fun expectedReferenceApkPath(resourceId: String): Path =
        filesRoot.resolve("reference-apks").resolve("$resourceId.apk").normalize().also(::requireOwnedPath)

    fun expectedReferenceIconPath(resourceId: String): Path =
        filesRoot.resolve("reference-icons").resolve("$resourceId.png").normalize().also(::requireOwnedPath)

    fun expectedAuditExportPath(resourceId: String): Path =
        filesRoot.resolve("audit-exports").resolve("${safeStorageName(resourceId)}.rdaudit.json")
            .normalize().also(::requireOwnedPath)

    private suspend fun reconcileReleaseAsset(asset: ReleaseAssetEntity) {
        val apk = observeFile(
            expectedReferenceApkPath(asset.releaseAssetId),
            asset.downloadedSizeBytes,
            asset.computedRawSha256,
            hashRequired = asset.downloadStatus == ReferenceDownloadStatus.VERIFIED.name,
        )
        storageDao.upsertAvailability(apk.toEntity("REFERENCE_APK", asset.releaseAssetId, asset.downloadedAt))
        val icon = observeFile(expectedReferenceIconPath(asset.releaseAssetId), null, null, hashRequired = false)
        storageDao.upsertAvailability(icon.toEntity("REFERENCE_ICON", asset.releaseAssetId, asset.downloadedAt))
    }

    private suspend fun reconcileArtifact(artifact: ArtifactEntity) {
        val relative = artifact.localContentPath ?: return
        val expectedRelative = "apks/${safeStorageName(artifact.jobId)}/${safeStorageName(artifact.artifactId)}.apk"
        val path = filesRoot.resolve(relative).normalize()
        val observation = if (relative != expectedRelative) {
            Observation(ResourceAvailabilityState.CORRUPT, null, artifact.downloadedSha256)
        } else {
            observeFile(path, artifact.downloadedSizeBytes, artifact.downloadedSha256, hashRequired = true)
        }
        storageDao.upsertAvailability(observation.toEntity("RUNNER_APK", artifact.artifactId, artifact.downloadedAt))
    }

    private suspend fun revalidateInstallable(resourceKind: String, resourceId: String): Boolean {
        val current = storageDao.getAvailability(OWNER_ANDROID, OWNER_ANDROID_LOCAL, resourceKind, resourceId)
            ?: return false
        val observation = when (resourceKind) {
            "REFERENCE_APK" -> observeFile(
                expectedReferenceApkPath(resourceId),
                current.observedBytes,
                current.knownSha256,
                hashRequired = true,
            )
            "RUNNER_APK" -> {
                val artifact = storageDao.getArtifact(resourceId)
                val relative = artifact?.localContentPath
                if (artifact == null || relative == null) {
                    Observation(ResourceAvailabilityState.MISSING, null, current.knownSha256)
                } else {
                    val expectedRelative =
                        "apks/${safeStorageName(artifact.jobId)}/${safeStorageName(resourceId)}.apk"
                    if (relative != expectedRelative) {
                        Observation(ResourceAvailabilityState.CORRUPT, null, current.knownSha256)
                    } else {
                        observeFile(
                            filesRoot.resolve(relative).normalize(),
                            current.observedBytes,
                            current.knownSha256,
                            hashRequired = true,
                        )
                    }
                }
            }
            else -> throw IllegalArgumentException("The resource kind is not installable.")
        }
        val updated = observation.toEntity(resourceKind, resourceId, current.lastUsedAt)
        storageDao.upsertAvailability(updated)
        return updated.state == ResourceAvailabilityState.PRESENT.name
    }

    private suspend fun reconcileReservationsAfterRestart() {
        storageDao.getActiveReservations().forEach { reservation ->
            val availability = storageDao.getAvailability(
                OWNER_ANDROID,
                OWNER_ANDROID_LOCAL,
                reservation.resourceKind,
                reservation.resourceId,
            )
            storageDao.upsertReservation(
                reservation.copy(
                    state = if (availability?.state == ResourceAvailabilityState.PRESENT.name) {
                        StorageReservationState.CONSUMED.name
                    } else {
                        StorageReservationState.RECONCILIATION_REQUIRED.name
                    },
                    updatedAt = Instant.now().toString(),
                ),
            )
        }
    }

    private fun observeFile(path: Path, expectedBytes: Long?, expectedSha256: String?, hashRequired: Boolean): Observation {
        requireOwnedPath(path)
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return Observation(ResourceAvailabilityState.MISSING, null, expectedSha256)
        }
        val attributes = runCatching {
            Files.readAttributes(path, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        }.getOrElse { return Observation(ResourceAvailabilityState.UNKNOWN, null, expectedSha256) }
        if (!attributes.isRegularFile || attributes.isSymbolicLink || expectedBytes != null && attributes.size() != expectedBytes) {
            return Observation(ResourceAvailabilityState.CORRUPT, attributes.size(), expectedSha256)
        }
        val actualSha256 = if (hashRequired) boundedSha256(path) ?: return Observation(
            ResourceAvailabilityState.UNKNOWN,
            attributes.size(),
            expectedSha256,
        ) else expectedSha256
        val state = if (expectedSha256 != null && actualSha256 != expectedSha256) {
            ResourceAvailabilityState.CORRUPT
        } else {
            ResourceAvailabilityState.PRESENT
        }
        return Observation(state, attributes.size(), actualSha256)
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

    private fun measure(targetRoots: List<Path>): Measurement {
        var bytes = 0L
        var entries = 0
        val deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos()
        return try {
            targetRoots.forEach { root ->
                requireOwnedPath(root)
                if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return@forEach
                Files.walkFileTree(root, setOf(), MAX_DEPTH, object : SimpleFileVisitor<Path>() {
                    override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                        checkEntry(attrs)
                        if (attrs.isSymbolicLink) throw UnsafeEntry()
                        return FileVisitResult.CONTINUE
                    }

                    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                        checkEntry(attrs)
                        if (attrs.isSymbolicLink || !attrs.isRegularFile) throw UnsafeEntry()
                        bytes = Math.addExact(bytes, attrs.size())
                        return FileVisitResult.CONTINUE
                    }

                    private fun checkEntry(attrs: BasicFileAttributes) {
                        entries++
                        if (entries > MAX_ENTRIES || System.nanoTime() > deadline) throw IncompleteMeasurement()
                    }
                })
            }
            Measurement(bytes, "COMPLETE")
        } catch (_: IncompleteMeasurement) {
            Measurement(bytes, "INCOMPLETE")
        } catch (_: Throwable) {
            Measurement(bytes, "FAILED")
        }
    }

    private fun Observation.toEntity(kind: String, resourceId: String, lastUsedAt: String?) =
        ResourceAvailabilityEntity(
            ownerType = OWNER_ANDROID,
            ownerId = OWNER_ANDROID_LOCAL,
            resourceKind = kind,
            resourceId = resourceId,
            state = state.name,
            observedBytes = bytes,
            knownSha256 = sha256,
            lastUsedAt = lastUsedAt,
            checkedAt = Instant.now().toString(),
            deletionRunId = null,
            deletionReason = null,
        )

    private fun requireOwnedPath(path: Path) {
        require(path.toAbsolutePath().normalize().startsWith(filesRoot)) { "Storage path escaped app-private files." }
    }

    private fun defaultSettings() = GlobalSettingsEntity(updatedAt = Instant.EPOCH.toString())
    private fun addExact(vararg values: Long): Long = values.fold(0L, Math::addExact)
    private fun requiredDownloadBytes(expectedBytes: Long): Long = addExact(Math.multiplyExact(expectedBytes, 2), DOWNLOAD_OVERHEAD_BYTES)
    private fun reachesWarning(used: Long, reserved: Long, budget: Long, percent: Int): Boolean =
        runCatching { Math.addExact(used, reserved) }.getOrElse { return true } * 100.0 >= budget * percent.toDouble()
    private fun safeStorageName(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private data class Measurement(val bytes: Long, val state: String)
    private data class Observation(val state: ResourceAvailabilityState, val bytes: Long?, val sha256: String?)
    private class UnsafeEntry : RuntimeException()
    private class IncompleteMeasurement : RuntimeException()

    companion object {
        const val OWNER_ANDROID = "ANDROID"
        const val OWNER_ANDROID_LOCAL = "android-local"
        const val AREA_ANDROID = "ANDROID"
        const val PURPOSE_DOWNLOAD = "DOWNLOAD"
        const val PURPOSE_EXPORT = "EXPORT"
        const val LOCAL_PRINCIPAL = "android-local"
        const val RECOVERY_RESERVE_BYTES = 16L * 1024 * 1024
        const val DOWNLOAD_OVERHEAD_BYTES = 16L * 1024 * 1024
        const val MAX_ENTRIES = 100_000
        const val MAX_DEPTH = 16
    }
}
