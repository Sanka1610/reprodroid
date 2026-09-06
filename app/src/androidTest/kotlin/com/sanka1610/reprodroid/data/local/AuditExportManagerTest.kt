package com.sanka1610.reprodroid.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sanka1610.reprodroid.data.storage.AndroidStorageManager
import com.sanka1610.reprodroid.data.storage.AuditExportManager
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.file.Files

@RunWith(AndroidJUnit4::class)
class AuditExportManagerTest {
    private lateinit var context: Context
    private lateinit var database: ReproDroidDatabase
    private lateinit var storage: AndroidStorageManager

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, ReproDroidDatabase::class.java).build()
        storage = AndroidStorageManager(context, database)
    }

    @After
    fun tearDown() {
        val exports = context.filesDir.resolve("audit-exports").toPath()
        if (Files.isSymbolicLink(exports)) {
            Files.deleteIfExists(exports)
        } else {
            exports.toFile().listFiles()?.forEach { it.delete() }
        }
        WritableAuditDestinationProvider.destinationFile(
            context,
        ).delete()
        database.close()
    }

    @Test
    fun exportRebuildsAllowlistedRecordsAndRedactsStoredPrivateFields() = runBlocking {
        val appId = "00000000-0000-4000-8000-000000000001"
        val snapshotId = "00000000-0000-4000-8000-000000000002"
        val assetId = "00000000-0000-4000-8000-000000000003"
        val now = "2026-09-02T00:00:00Z"
        database.managedAppDao().upsertRegisteredApp(
            RegisteredAppEntity(
                registeredAppId = appId,
                displayName = "Example",
                repositoryUrl = "https://github.com/example/app?token=CANARY_REPO_INPUT",
                canonicalRepositoryUrl = "https://github.com/example/app",
                provider = "PUBLIC_GITHUB_RELEASES",
                managementMode = ManagementMode.VERIFICATION.name,
                releaseMetadataEtag = "CANARY_ETAG",
                createdAt = now,
                updatedAt = now,
            ),
        )
        database.managedAppDao().upsertReleaseSnapshot(
            ReleaseSnapshotEntity(
                releaseSnapshotId = snapshotId,
                registeredAppId = appId,
                providerReleaseId = "7",
                tagName = "v1",
                resolvedCommitSha = "a".repeat(40),
                releaseName = "Version 1",
                releaseUrl = "https://github.com/example/app/releases/tag/v1",
                targetCommitishRaw = "main",
                isDraft = false,
                isPrerelease = false,
                isImmutable = true,
                releaseCreatedAt = now,
                publishedAt = now,
                fetchedAt = now,
                observationSha256 = "b".repeat(64),
                lastObservedAt = now,
                selectedProviderAssetId = "9",
            ),
        )
        database.managedAppDao().upsertReleaseAsset(
            ReleaseAssetEntity(
                releaseAssetId = assetId,
                releaseSnapshotId = snapshotId,
                providerAssetId = "9",
                assetName = "app.apk",
                stableAssetUrl = "https://downloads.invalid/app.apk?token=CANARY_SIGNED_URL",
                selectionReason = "SINGLE_APK",
                contentType = "application/vnd.android.package-archive",
                providerSizeBytes = 123,
                providerDigestSha256 = "c".repeat(64),
                downloadStatus = ReferenceDownloadStatus.FAILED.name,
                downloadErrorMessage = "CANARY_RAW_ERROR",
                localContentPath = "/absolute/CANARY_PATH.apk",
                responseEtag = "CANARY_RESPONSE_ETAG",
                finalDownloadHost = "CANARY_FINAL_HOST",
                comparisonEligibility = ComparisonEligibility.INCOMPARABLE.name,
                incomparableReason = "COMPARISON_PROFILE_NOT_SUPPORTED",
            ),
        )

        val staged = AuditExportManager(context, database, storage).stageApps(setOf(appId))
        val text = String(Files.readAllBytes(storage.expectedAuditExportPath(staged.auditExportId)), Charsets.UTF_8)
        val root = Json.parseToJsonElement(text).jsonObject

        assertEquals(3, staged.recordCount)
        assertEquals(staged.payloadSha256, root.getValue("payloadSha256").jsonPrimitive.content)
        assertTrue(text.contains("asset-observation"))
        listOf(
            "CANARY_REPO_INPUT",
            "CANARY_ETAG",
            "CANARY_SIGNED_URL",
            "CANARY_RAW_ERROR",
            "CANARY_PATH",
            "CANARY_RESPONSE_ETAG",
            "CANARY_FINAL_HOST",
        ).forEach { forbidden -> assertFalse("Export leaked $forbidden", text.contains(forbidden)) }
    }

    @Test
    fun destinationCopyCompletesOnlyAfterVerifiedContentProviderWrite() = runBlocking {
        val manager = AuditExportManager(context, database, storage)
        val staged = manager.stageAll()

        val completed = manager.copyTo(staged.auditExportId, WritableAuditDestinationProvider.URI)

        assertEquals(AuditExportState.COMPLETE.name, completed.state)
        val source = Files.readAllBytes(storage.expectedAuditExportPath(staged.auditExportId))
        val destination = Files.readAllBytes(
            WritableAuditDestinationProvider.destinationFile(
                context,
            ).toPath(),
        )
        assertTrue(source.contentEquals(destination))
        assertEquals(
            AuditExportState.COMPLETE.name,
            database.storageDao().getAuditExport(staged.auditExportId)?.state,
        )
    }

    @Test
    fun destinationOpenFailurePersistsFailedInsteadOfComplete() = runBlocking {
        val manager = AuditExportManager(context, database, storage)
        val staged = manager.stageAll()

        assertTrue(runCatching {
            manager.copyTo(staged.auditExportId, WritableAuditDestinationProvider.REJECT_URI)
        }.isFailure)
        assertEquals(
            AuditExportState.FAILED.name,
            database.storageDao().getAuditExport(staged.auditExportId)?.state,
        )
    }

    @Test
    fun unsafeStagingDirectoryFailsWithoutWritingOrConsumingReservation() = runBlocking {
        val exports = context.filesDir.resolve("audit-exports").toPath()
        exports.toFile().listFiles()?.forEach { it.delete() }
        Files.deleteIfExists(exports)
        val outside = Files.createTempDirectory(context.cacheDir.toPath(), "audit-staging-outside-")
        val marker = Files.write(outside.resolve("must-remain"), "outside".toByteArray())
        Files.createSymbolicLink(exports, outside)
        try {
            val manager = AuditExportManager(context, database, storage)

            assertTrue(runCatching { manager.stageAll() }.isFailure)

            val failed = requireNotNull(database.storageDao().getLatestAuditExport())
            assertEquals(AuditExportState.FAILED.name, failed.state)
            assertEquals("STAGING_FAILED", failed.errorCode)
            assertTrue(Files.exists(marker))
            assertEquals(0, database.storageDao().getActiveReservedBytes(AndroidStorageManager.AREA_ANDROID))
            assertEquals(0, database.storageDao().getReservationReconciliationCount())
        } finally {
            Files.deleteIfExists(exports)
            Files.deleteIfExists(marker)
            Files.deleteIfExists(outside)
            Files.createDirectories(exports)
        }
    }
}
