package com.sanka1610.reprodroid.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sanka1610.reprodroid.data.storage.AndroidCleanupManager
import com.sanka1610.reprodroid.data.storage.AndroidStorageManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.file.Files
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class AndroidStorageManagerTest {
    private lateinit var context: Context
    private lateinit var database: ReproDroidDatabase
    private lateinit var storage: AndroidStorageManager
    private lateinit var cleanup: AndroidCleanupManager
    private val createdFiles = mutableListOf<java.nio.file.Path>()

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, ReproDroidDatabase::class.java).build()
        storage = AndroidStorageManager(context, database)
        cleanup = AndroidCleanupManager(context, database)
    }

    @After
    fun tearDown() {
        createdFiles.asReversed().forEach { Files.deleteIfExists(it) }
        database.close()
    }

    @Test
    fun exactBudgetReservationSucceedsAndAdditionalReservationFails() = runBlocking {
        val initial = storage.summary()
        val expectedBytes = 1L
        val required = expectedBytes * 2 + AndroidStorageManager.DOWNLOAD_OVERHEAD_BYTES
        database.managedAppDao().upsertGlobalSettings(
            GlobalSettingsEntity(
                androidStorageBudgetBytes = initial.usedBytes + required,
                updatedAt = Instant.now().toString(),
            ),
        )

        val accepted = storage.reserveDownload("REFERENCE_APK", UUID.randomUUID().toString(), expectedBytes)
        assertEquals(required, accepted.requestedBytes)

        val failure = runCatching {
            storage.reserveDownload("REFERENCE_APK", UUID.randomUUID().toString(), 1)
        }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertEquals(required, database.storageDao().getActiveReservedBytes(AndroidStorageManager.AREA_ANDROID))
    }

    @Test
    fun manualCleanupPreservesReservedFileThenDeletesOnlyAfterRelease() = runBlocking {
        val resourceId = UUID.randomUUID().toString()
        val path = storage.expectedReferenceApkPath(resourceId)
        Files.createDirectories(path.parent)
        Files.write(path, "retained".toByteArray())
        createdFiles.add(path)
        val old = Instant.now().minus(40, ChronoUnit.DAYS).toString()
        database.storageDao().upsertAvailability(
            ResourceAvailabilityEntity(
                ownerType = AndroidStorageManager.OWNER_ANDROID,
                ownerId = AndroidStorageManager.OWNER_ANDROID_LOCAL,
                resourceKind = "REFERENCE_APK",
                resourceId = resourceId,
                state = ResourceAvailabilityState.PRESENT.name,
                observedBytes = Files.size(path),
                knownSha256 = null,
                lastUsedAt = old,
                checkedAt = old,
                deletionRunId = null,
                deletionReason = null,
            ),
        )
        val reservation = StorageReservationEntity(
            reservationId = UUID.randomUUID().toString(),
            area = AndroidStorageManager.AREA_ANDROID,
            purpose = AndroidStorageManager.PURPOSE_DOWNLOAD,
            resourceKind = "REFERENCE_APK",
            resourceId = resourceId,
            requestedBytes = 1,
            principalId = AndroidStorageManager.LOCAL_PRINCIPAL,
            operationId = UUID.randomUUID().toString(),
            state = StorageReservationState.ACTIVE.name,
            createdAt = old,
            updatedAt = old,
        )
        database.storageDao().upsertReservation(reservation)

        val protectedPreview = cleanup.createPreview(resourceIds = setOf(resourceId))
        assertEquals(listOf("ACTIVE_RESERVATION"), protectedPreview.items.single().protectionReasons)
        val protectedRun = cleanup.execute(protectedPreview.previewId, setOf(protectedPreview.items.single().itemId))
        assertEquals(LocalCleanupRunState.PARTIAL.name, protectedRun.state)
        assertEquals(LocalCleanupItemResult.SKIPPED_PROTECTED.name, protectedRun.items.single().result)
        assertTrue(Files.exists(path))

        database.storageDao().upsertReservation(
            reservation.copy(state = StorageReservationState.RELEASED.name, updatedAt = Instant.now().toString()),
        )
        val deletablePreview = cleanup.createPreview(resourceIds = setOf(resourceId))
        assertTrue(deletablePreview.items.single().protectionReasons.isEmpty())
        val completed = cleanup.execute(deletablePreview.previewId, setOf(deletablePreview.items.single().itemId))
        assertEquals(LocalCleanupRunState.COMPLETE.name, completed.state)
        assertEquals(LocalCleanupItemResult.DELETED.name, completed.items.single().result)
        assertFalse(Files.exists(path))
        assertEquals(
            ResourceAvailabilityState.DELETED.name,
            database.storageDao().getAvailability(
                AndroidStorageManager.OWNER_ANDROID,
                AndroidStorageManager.OWNER_ANDROID_LOCAL,
                "REFERENCE_APK",
                resourceId,
            )?.state,
        )
    }

    @Test
    fun interruptedCleanupReconcilesMissingBytesWithoutRepeatingDeletion() = runBlocking {
        val resourceId = UUID.randomUUID().toString()
        val path = storage.expectedReferenceApkPath(resourceId)
        Files.createDirectories(path.parent)
        Files.write(path, "delete-before-restart".toByteArray())
        createdFiles.add(path)
        val old = Instant.now().minus(40, ChronoUnit.DAYS).toString()
        database.storageDao().upsertAvailability(
            ResourceAvailabilityEntity(
                ownerType = AndroidStorageManager.OWNER_ANDROID,
                ownerId = AndroidStorageManager.OWNER_ANDROID_LOCAL,
                resourceKind = "REFERENCE_APK",
                resourceId = resourceId,
                state = ResourceAvailabilityState.PRESENT.name,
                observedBytes = Files.size(path),
                knownSha256 = null,
                lastUsedAt = old,
                checkedAt = old,
                deletionRunId = null,
                deletionReason = null,
            ),
        )
        val preview = cleanup.createPreview(resourceIds = setOf(resourceId))
        val item = preview.items.single()
        val applying = requireNotNull(database.storageDao().getCleanupRun(preview.cleanupRunId)).copy(
            state = LocalCleanupRunState.APPLYING.name,
            startedAt = Instant.now().toString(),
        )
        database.storageDao().upsertCleanupRun(applying)
        val storedItem = database.storageDao().getCleanupItems(preview.cleanupRunId).single()
        database.storageDao().upsertCleanupItem(storedItem.copy(selected = true))
        Files.delete(path)

        cleanup.reconcileInterruptedRuns()

        val reconciled = requireNotNull(database.storageDao().getCleanupRun(preview.cleanupRunId))
        val reconciledItem = database.storageDao().getCleanupItems(preview.cleanupRunId).single()
        assertEquals(LocalCleanupRunState.COMPLETE.name, reconciled.state)
        assertEquals(LocalCleanupItemResult.ALREADY_MISSING.name, reconciledItem.result)
        assertEquals(
            ResourceAvailabilityState.MISSING.name,
            database.storageDao().getAvailability(
                AndroidStorageManager.OWNER_ANDROID,
                AndroidStorageManager.OWNER_ANDROID_LOCAL,
                "REFERENCE_APK",
                resourceId,
            )?.state,
        )
        assertEquals(item.observedToken, reconciledItem.observedToken)
    }

    @Test
    fun cleanupPersistsPartialResultWhenOneOfTwoDeletesFails() = runBlocking {
        val firstId = UUID.randomUUID().toString()
        val secondId = UUID.randomUUID().toString()
        val old = Instant.now().minus(40, ChronoUnit.DAYS).toString()
        listOf(firstId, secondId).forEach { resourceId ->
            val path = storage.expectedReferenceApkPath(resourceId)
            Files.createDirectories(path.parent)
            Files.write(path, resourceId.toByteArray())
            createdFiles.add(path)
            database.storageDao().upsertAvailability(
                ResourceAvailabilityEntity(
                    ownerType = AndroidStorageManager.OWNER_ANDROID,
                    ownerId = AndroidStorageManager.OWNER_ANDROID_LOCAL,
                    resourceKind = "REFERENCE_APK",
                    resourceId = resourceId,
                    state = ResourceAvailabilityState.PRESENT.name,
                    observedBytes = Files.size(path),
                    knownSha256 = null,
                    lastUsedAt = old,
                    checkedAt = old,
                    deletionRunId = null,
                    deletionReason = null,
                ),
            )
        }
        val failingCleanup = AndroidCleanupManager(context, database) { path ->
            if (path.fileName.toString() == "$secondId.apk") throw IOException("injected delete failure")
            Files.delete(path)
        }
        val preview = failingCleanup.createPreview(resourceIds = setOf(firstId, secondId))

        val result = failingCleanup.execute(preview.previewId, preview.items.mapTo(mutableSetOf()) { it.itemId })

        assertEquals(LocalCleanupRunState.PARTIAL.name, result.state)
        assertEquals(
            setOf(LocalCleanupItemResult.DELETED.name, LocalCleanupItemResult.FAILED.name),
            result.items.mapTo(mutableSetOf()) { it.result },
        )
        assertFalse(Files.exists(storage.expectedReferenceApkPath(firstId)))
        assertTrue(Files.exists(storage.expectedReferenceApkPath(secondId)))
    }

    @Test
    fun cleanupRejectsSymlinkWithoutDeletingOutsideTarget() = runBlocking {
        val resourceId = UUID.randomUUID().toString()
        val expectedPath = storage.expectedReferenceApkPath(resourceId)
        val outsideDirectory = Files.createTempDirectory(context.cacheDir.toPath(), "cleanup-outside-")
        val outsideFile = Files.write(outsideDirectory.resolve("must-remain.apk"), "outside".toByteArray())
        Files.createDirectories(expectedPath.parent)
        Files.createSymbolicLink(expectedPath, outsideFile)
        createdFiles.add(expectedPath)
        val old = Instant.now().minus(40, ChronoUnit.DAYS).toString()
        database.storageDao().upsertAvailability(
            ResourceAvailabilityEntity(
                ownerType = AndroidStorageManager.OWNER_ANDROID,
                ownerId = AndroidStorageManager.OWNER_ANDROID_LOCAL,
                resourceKind = "REFERENCE_APK",
                resourceId = resourceId,
                state = ResourceAvailabilityState.PRESENT.name,
                observedBytes = Files.size(outsideFile),
                knownSha256 = null,
                lastUsedAt = old,
                checkedAt = old,
                deletionRunId = null,
                deletionReason = null,
            ),
        )

        try {
            val preview = cleanup.createPreview(resourceIds = setOf(resourceId))
            assertEquals(listOf("RESOURCE_CHANGED"), preview.items.single().protectionReasons)
            val result = cleanup.execute(preview.previewId, setOf(preview.items.single().itemId))
            assertEquals(LocalCleanupItemResult.SKIPPED_PROTECTED.name, result.items.single().result)
            assertTrue(Files.isSymbolicLink(expectedPath))
            assertTrue(Files.exists(outsideFile))
        } finally {
            Files.deleteIfExists(expectedPath)
            Files.deleteIfExists(outsideFile)
            Files.deleteIfExists(outsideDirectory)
        }
    }
}
