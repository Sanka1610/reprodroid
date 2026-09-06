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
import kotlin.math.absoluteValue

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
    fun budgetReductionAndOverflowStopNewConsumptionWithoutDeletingBytes() = runBlocking {
        val resourceId = UUID.randomUUID().toString()
        val path = storage.expectedReferenceApkPath(resourceId)
        Files.createDirectories(path.parent)
        Files.write(path, "retained-over-budget".toByteArray())
        createdFiles.add(path)
        database.managedAppDao().upsertGlobalSettings(
            GlobalSettingsEntity(androidStorageBudgetBytes = 1, updatedAt = Instant.now().toString()),
        )

        val summary = storage.summary()
        assertEquals("OVER_BUDGET", summary.state)
        assertTrue(Files.exists(path))
        assertTrue(runCatching { storage.reserveDownload("REFERENCE_APK", UUID.randomUUID().toString(), 1) }.isFailure)
        assertTrue(runCatching {
            storage.reserveDownload("REFERENCE_APK", UUID.randomUUID().toString(), Long.MAX_VALUE)
        }.exceptionOrNull() is ArithmeticException)
        assertEquals(0, database.storageDao().getActiveReservedBytes(AndroidStorageManager.AREA_ANDROID))
        assertTrue(Files.exists(path))
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
    fun stagedAuditExportIsProtectedUntilItLeavesTheActiveState() = runBlocking {
        val exportId = UUID.randomUUID().toString()
        val path = storage.expectedAuditExportPath(exportId)
        Files.createDirectories(path.parent)
        Files.write(path, "audit".toByteArray())
        createdFiles.add(path)
        val old = Instant.now().minus(100, ChronoUnit.DAYS).toString()
        val export = AuditExportEntity(
            auditExportId = exportId,
            scopeType = "ALL",
            scopeJson = "{}",
            filterJson = "{}",
            state = AuditExportState.STAGED.name,
            stagingName = "audit-exports/${path.fileName}",
            payloadSha256 = "a".repeat(64),
            bundleSha256 = "b".repeat(64),
            sizeBytes = Files.size(path),
            recordCount = 0,
            errorCode = null,
            createdAt = old,
            updatedAt = old,
        )
        database.storageDao().upsertAuditExport(export)
        database.storageDao().upsertAvailability(
            ResourceAvailabilityEntity(
                ownerType = AndroidStorageManager.OWNER_ANDROID,
                ownerId = AndroidStorageManager.OWNER_ANDROID_LOCAL,
                resourceKind = "AUDIT_EXPORT",
                resourceId = exportId,
                state = ResourceAvailabilityState.PRESENT.name,
                observedBytes = Files.size(path),
                knownSha256 = null,
                lastUsedAt = old,
                checkedAt = old,
                deletionRunId = null,
                deletionReason = null,
            ),
        )

        val protected = cleanup.createPreview(resourceIds = setOf(exportId))
        assertEquals(listOf("EXPORT_ACTIVE"), protected.items.single().protectionReasons)
        val skipped = cleanup.execute(protected.previewId, setOf(protected.items.single().itemId))
        assertEquals(LocalCleanupItemResult.SKIPPED_PROTECTED.name, skipped.items.single().result)
        assertTrue(Files.exists(path))

        database.storageDao().upsertAuditExport(export.copy(state = AuditExportState.COMPLETE.name))
        val deletable = cleanup.createPreview(resourceIds = setOf(exportId))
        val complete = cleanup.execute(deletable.previewId, setOf(deletable.items.single().itemId))
        assertEquals(LocalCleanupRunState.COMPLETE.name, complete.state)
        assertFalse(Files.exists(path))
    }

    @Test
    fun currentAndActiveReferencesRemainWhilePastEvidenceSurvivesOldByteDeletion() = runBlocking {
        val appId = UUID.randomUUID().toString()
        val jobId = UUID.randomUUID().toString()
        val old = Instant.now().minus(100, ChronoUnit.DAYS).toString()
        val currentTime = Instant.now().toString()
        val dao = database.managedAppDao()
        dao.upsertRegisteredApp(
            RegisteredAppEntity(
                registeredAppId = appId,
                displayName = "retention fixture",
                repositoryUrl = "https://github.com/example/retention",
                canonicalRepositoryUrl = "https://github.com/example/retention",
                provider = "PUBLIC_GITHUB_RELEASES",
                managementMode = ManagementMode.VERIFICATION.name,
                createdAt = old,
                updatedAt = currentTime,
            ),
        )
        database.jobDao().upsertJob(
            JobEntity(
                jobId = jobId,
                executionMode = "SIMULATED",
                repositoryUrl = "https://github.com/example/retention.git",
                revisionType = "COMMIT",
                revisionValue = "1".repeat(40),
                simulationOutcome = "SUCCESS",
                state = "SUCCEEDED",
                progressPercent = 100,
                latestLogSequence = 0,
                errorCode = null,
                errorMessage = null,
                createdAt = old,
                updatedAt = old,
            ),
        )

        data class Fixture(val snapshotId: String, val assetId: String)
        suspend fun fixture(label: String, lastObservedAt: String, downloadStatus: String = "VERIFIED"): Fixture {
            val snapshotId = UUID.randomUUID().toString()
            val assetId = UUID.randomUUID().toString()
            dao.upsertReleaseSnapshot(
                ReleaseSnapshotEntity(
                    releaseSnapshotId = snapshotId,
                    registeredAppId = appId,
                    providerReleaseId = (label.hashCode().toLong().absoluteValue + 1).toString(),
                    tagName = label,
                    resolvedCommitSha = "1".repeat(40),
                    releaseName = label,
                    releaseUrl = "https://github.com/example/retention/releases/tag/$label",
                    targetCommitishRaw = "main",
                    isDraft = false,
                    isPrerelease = false,
                    isImmutable = false,
                    releaseCreatedAt = old,
                    publishedAt = old,
                    fetchedAt = old,
                    observationSha256 = label.padEnd(64, '0').take(64),
                    lastObservedAt = lastObservedAt,
                    selectedProviderAssetId = (label.hashCode().toLong().absoluteValue + 100).toString(),
                ),
            )
            dao.upsertReleaseAsset(
                ReleaseAssetEntity(
                    releaseAssetId = assetId,
                    releaseSnapshotId = snapshotId,
                    providerAssetId = (label.hashCode().toLong().absoluteValue + 100).toString(),
                    assetName = "$label.apk",
                    stableAssetUrl = "https://github.com/example/retention/releases/download/$label/$label.apk",
                    selectionReason = "SINGLE_APK",
                    contentType = "application/vnd.android.package-archive",
                    providerSizeBytes = label.length.toLong(),
                    providerDigestSha256 = null,
                    downloadStatus = downloadStatus,
                    signingCertificateSha256 = "c".repeat(64),
                    comparisonEligibility = "ELIGIBLE",
                ),
            )
            val path = storage.expectedReferenceApkPath(assetId)
            Files.createDirectories(path.parent)
            Files.write(path, label.toByteArray())
            createdFiles.add(path)
            database.storageDao().upsertAvailability(
                ResourceAvailabilityEntity(
                    ownerType = AndroidStorageManager.OWNER_ANDROID,
                    ownerId = AndroidStorageManager.OWNER_ANDROID_LOCAL,
                    resourceKind = "REFERENCE_APK",
                    resourceId = assetId,
                    state = ResourceAvailabilityState.PRESENT.name,
                    observedBytes = Files.size(path),
                    knownSha256 = null,
                    lastUsedAt = old,
                    checkedAt = old,
                    deletionRunId = null,
                    deletionReason = null,
                ),
            )
            return Fixture(snapshotId, assetId)
        }

        val oldEvidence = fixture("old", old)
        val activeComparison = fixture("active", old)
        val activeDownload = fixture("download", old, ReferenceDownloadStatus.DOWNLOADING.name)
        val activeInstall = fixture("install", old)
        val current = fixture("current", currentTime)
        val completedComparisonId = UUID.randomUUID().toString()
        dao.upsertComparisonRun(
            ComparisonRunEntity(
                comparisonRunId = completedComparisonId,
                registeredAppId = appId,
                releaseSnapshotId = oldEvidence.snapshotId,
                referenceAssetId = oldEvidence.assetId,
                runnerJobId = jobId,
                expectedCommitSha = "1".repeat(40),
                expectedRecipeId = "fixture",
                expectedVariantName = "release",
                status = ComparisonRunStatus.COMPLETED.name,
                outcome = ComparisonOutcome.INCOMPARABLE.name,
                incomparableReason = "FIXTURE_UNAVAILABLE",
                protocolVersion = 2,
                repeatOfficialOutcome = ComparisonOutcome.INCOMPARABLE.name,
                repeatabilityOutcome = ComparisonOutcome.INCOMPARABLE.name,
                createdAt = old,
                updatedAt = old,
                completedAt = old,
            ),
        )
        dao.upsertComparisonRun(
            ComparisonRunEntity(
                comparisonRunId = UUID.randomUUID().toString(),
                registeredAppId = appId,
                releaseSnapshotId = activeComparison.snapshotId,
                referenceAssetId = activeComparison.assetId,
                runnerJobId = jobId,
                expectedCommitSha = "1".repeat(40),
                expectedRecipeId = "fixture",
                expectedVariantName = "release",
                status = ComparisonRunStatus.RESOLVING_RUNNER.name,
                createdAt = old,
                updatedAt = old,
            ),
        )
        val completedInstallId = UUID.randomUUID().toString()
        dao.upsertReleaseInstallAttempt(
            ReleaseInstallAttemptEntity(
                attemptId = completedInstallId,
                registeredAppId = appId,
                releaseAssetId = oldEvidence.assetId,
                packageInstallerSessionId = null,
                status = "SUCCEEDED",
                packageInstallerStatus = 0,
                statusMessage = null,
                createdAt = old,
                updatedAt = old,
            ),
        )
        dao.upsertReleaseInstallAttempt(
            ReleaseInstallAttemptEntity(
                attemptId = UUID.randomUUID().toString(),
                registeredAppId = appId,
                releaseAssetId = activeInstall.assetId,
                packageInstallerSessionId = 1,
                status = "PREPARING",
                packageInstallerStatus = null,
                statusMessage = null,
                createdAt = old,
                updatedAt = old,
            ),
        )

        val resourceIds = setOf(
            oldEvidence.assetId,
            activeComparison.assetId,
            activeDownload.assetId,
            activeInstall.assetId,
            current.assetId,
        )
        val preview = cleanup.createPreview(resourceIds = resourceIds)
        val reasons = preview.items.associate { it.resourceId to it.protectionReasons.toSet() }
        assertTrue(reasons.getValue(oldEvidence.assetId).isEmpty())
        assertTrue("COMPARISON_ACTIVE" in reasons.getValue(activeComparison.assetId))
        assertTrue("DOWNLOAD_ACTIVE" in reasons.getValue(activeDownload.assetId))
        assertTrue("INSTALL_ACTIVE" in reasons.getValue(activeInstall.assetId))
        assertTrue("CURRENT_RELEASE" in reasons.getValue(current.assetId))

        val result = cleanup.execute(preview.previewId, preview.items.mapTo(mutableSetOf()) { it.itemId })

        assertEquals(LocalCleanupRunState.PARTIAL.name, result.state)
        assertFalse(Files.exists(storage.expectedReferenceApkPath(oldEvidence.assetId)))
        listOf(activeComparison, activeDownload, activeInstall, current).forEach { protected ->
            assertTrue(Files.exists(storage.expectedReferenceApkPath(protected.assetId)))
        }
        assertEquals(ComparisonOutcome.INCOMPARABLE.name, dao.getComparisonRun(completedComparisonId)?.outcome)
        assertEquals("c".repeat(64), dao.getReleaseAsset(oldEvidence.assetId)?.signingCertificateSha256)
        assertEquals("SUCCEEDED", dao.getReleaseInstallAttempt(completedInstallId)?.status)
        assertEquals(
            ResourceAvailabilityState.DELETED.name,
            database.storageDao().getAvailability(
                AndroidStorageManager.OWNER_ANDROID,
                AndroidStorageManager.OWNER_ANDROID_LOCAL,
                "REFERENCE_APK",
                oldEvidence.assetId,
            )?.state,
        )
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
