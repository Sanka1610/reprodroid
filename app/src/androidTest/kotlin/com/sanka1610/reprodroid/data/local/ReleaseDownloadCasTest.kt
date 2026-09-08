package com.sanka1610.reprodroid.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReleaseDownloadCasTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var database: ReproDroidDatabase? = null

    @After
    fun tearDown() {
        database?.close()
    }

    @Test
    fun staleFailureCannotOverwriteVerifiedDownload() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, ReproDroidDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            .also { database = it }
        val dao = db.managedAppDao()
        dao.upsertRegisteredApp(app())
        dao.upsertReleaseSnapshot(snapshot())
        dao.upsertReleaseAsset(
            asset().copy(
                downloadStatus = ReferenceDownloadStatus.VERIFIED.name,
                computedRawSha256 = "a".repeat(64),
            ),
        )

        val changed = dao.failUnverifiedReleaseDownload("asset", "FAILED", "stale failure")

        assertEquals(0, changed)
        assertEquals(ReferenceDownloadStatus.VERIFIED.name, dao.getReleaseAsset("asset")?.downloadStatus)
        assertEquals("a".repeat(64), dao.getReleaseAsset("asset")?.computedRawSha256)
    }

    @Test
    fun ownedUnverifiedDownloadCanBeMarkedFailed() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, ReproDroidDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            .also { database = it }
        val dao = db.managedAppDao()
        dao.upsertRegisteredApp(app())
        dao.upsertReleaseSnapshot(snapshot())
        dao.upsertReleaseAsset(asset().copy(downloadStatus = ReferenceDownloadStatus.DOWNLOADING.name))

        val changed = dao.failUnverifiedReleaseDownload("asset", "NETWORK", "download failed")

        assertEquals(1, changed)
        assertEquals(ReferenceDownloadStatus.FAILED.name, dao.getReleaseAsset("asset")?.downloadStatus)
        assertEquals("NETWORK", dao.getReleaseAsset("asset")?.downloadErrorCode)
    }

    private fun app() = RegisteredAppEntity(
        registeredAppId = "app",
        displayName = "Example",
        repositoryUrl = "https://codeberg.org/example/app",
        canonicalRepositoryUrl = "https://codeberg.org/example/app",
        provider = "CODEBERG",
        managementMode = ManagementMode.VERIFICATION.name,
        createdAt = "2026-09-08T00:00:00Z",
        updatedAt = "2026-09-08T00:00:00Z",
    )

    private fun snapshot() = ReleaseSnapshotEntity(
        releaseSnapshotId = "snapshot",
        registeredAppId = "app",
        providerReleaseId = "1",
        tagName = "v1",
        resolvedCommitSha = "b".repeat(40),
        releaseName = "v1",
        releaseUrl = "https://codeberg.org/example/app/releases/tag/v1",
        targetCommitishRaw = "main",
        isDraft = false,
        isPrerelease = false,
        isImmutable = false,
        releaseCreatedAt = "2026-09-08T00:00:00Z",
        publishedAt = null,
        fetchedAt = "2026-09-08T00:00:00Z",
        observationSha256 = "c".repeat(64),
        lastObservedAt = "2026-09-08T00:00:00Z",
        selectedProviderAssetId = "provider-asset",
    )

    private fun asset() = ReleaseAssetEntity(
        releaseAssetId = "asset",
        releaseSnapshotId = "snapshot",
        providerAssetId = "provider-asset",
        assetName = "app.apk",
        stableAssetUrl = "https://codeberg.org/example/app/releases/download/v1/app.apk",
        selectionReason = AssetSelectionReason.SINGLE_APK.name,
        contentType = "application/vnd.android.package-archive",
        providerSizeBytes = 1024,
        providerDigestSha256 = null,
    )
}
