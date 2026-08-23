package com.sanka1610.reprodroid.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.sanka1610.reprodroid.data.artifact.ApkInspector
import com.sanka1610.reprodroid.data.artifact.GitHubAssetDownloader
import com.sanka1610.reprodroid.data.local.ComparisonEligibility
import com.sanka1610.reprodroid.data.local.ManagedAppDao
import com.sanka1610.reprodroid.data.local.ManagementMode
import com.sanka1610.reprodroid.data.local.ReferenceDownloadStatus
import com.sanka1610.reprodroid.data.local.RegisteredAppEntity
import com.sanka1610.reprodroid.data.local.RegisteredAppRecord
import com.sanka1610.reprodroid.data.local.ReleaseAssetEntity
import com.sanka1610.reprodroid.data.local.ReleaseDiscoveryStatus
import com.sanka1610.reprodroid.data.local.ReleaseSnapshotEntity
import com.sanka1610.reprodroid.data.local.ReproDroidDatabase
import com.sanka1610.reprodroid.data.provider.GitHubProviderException
import com.sanka1610.reprodroid.data.provider.GitHubReleasesClient
import com.sanka1610.reprodroid.data.provider.ResolvedGitHubRelease
import com.sanka1610.reprodroid.data.provider.ReleaseAssetSelectionException
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.UUID

class ManagedAppRepository(
    private val context: Context,
    private val database: ReproDroidDatabase,
    private val provider: GitHubReleasesClient = GitHubReleasesClient(),
    private val downloader: GitHubAssetDownloader = GitHubAssetDownloader(),
) {
    private val dao: ManagedAppDao = database.managedAppDao()
    private val inspector = ApkInspector(context.packageManager)
    private val referenceDirectory = File(context.filesDir, "reference-apks")

    fun observeApps(): Flow<List<RegisteredAppRecord>> = dao.observeRegisteredApps()

    fun observeApp(registeredAppId: String): Flow<RegisteredAppRecord?> =
        dao.observeRegisteredApp(registeredAppId)

    suspend fun previewLatest(repositoryUrl: String): ResolvedGitHubRelease =
        provider.resolveLatestRelease(repositoryUrl)

    suspend fun registerAndDownload(
        preview: ResolvedGitHubRelease,
        mode: ManagementMode,
    ): String {
        if (dao.getRegisteredAppByCanonicalUrl(preview.repository.canonicalUrl) != null) {
            throw IllegalStateException("This GitHub repository is already registered.")
        }
        val now = Instant.now().toString()
        val appId = UUID.randomUUID().toString()
        val snapshotId = stableId("$appId/release/${preview.release.id}")
        val assetId = stableId("$snapshotId/asset/${preview.selectedAsset.asset.id}")
        val app = RegisteredAppEntity(
            registeredAppId = appId,
            displayName = preview.repository.name,
            repositoryUrl = preview.repository.canonicalUrl,
            canonicalRepositoryUrl = preview.repository.canonicalUrl,
            provider = PROVIDER_GITHUB_RELEASES,
            managementMode = mode.name,
            releaseDiscoveryStatus = ReleaseDiscoveryStatus.AVAILABLE.name,
            releaseMetadataEtag = preview.responseEtag,
            lastReleaseCheckedAt = now,
            createdAt = now,
            updatedAt = now,
        )
        val snapshot = preview.toSnapshot(appId, snapshotId, now)
        val asset = preview.toAsset(snapshotId, assetId)
        database.withTransaction {
            dao.upsertRegisteredApp(app)
            dao.upsertReleaseSnapshot(snapshot)
            dao.upsertReleaseAsset(asset)
        }
        downloadReference(assetId)
        return appId
    }

    suspend fun refresh(registeredAppId: String) {
        val app = dao.getRegisteredApp(registeredAppId)
            ?: throw IllegalArgumentException("Registered app was not found.")
        dao.upsertRegisteredApp(
            app.copy(
                releaseDiscoveryStatus = ReleaseDiscoveryStatus.CHECKING.name,
                releaseDiscoveryErrorCode = null,
                releaseDiscoveryErrorMessage = null,
                updatedAt = Instant.now().toString(),
            ),
        )
        try {
            val latest = try {
                provider.resolveLatestRelease(app.canonicalRepositoryUrl, app.releaseMetadataEtag)
            } catch (notModified: GitHubProviderException) {
                if (notModified.code != "NOT_MODIFIED") throw notModified
                val now = Instant.now().toString()
                dao.upsertRegisteredApp(
                    app.copy(
                        releaseDiscoveryStatus = ReleaseDiscoveryStatus.AVAILABLE.name,
                        releaseDiscoveryErrorCode = null,
                        releaseDiscoveryErrorMessage = null,
                        lastReleaseCheckedAt = now,
                        updatedAt = now,
                    ),
                )
                return
            }
            val now = Instant.now().toString()
            val existingSnapshot = dao.getReleaseSnapshot(registeredAppId, latest.release.id)
            val snapshotId = existingSnapshot?.releaseSnapshotId
                ?: stableId("$registeredAppId/release/${latest.release.id}")
            val existingAsset = dao.getReleaseAsset(snapshotId, latest.selectedAsset.asset.id)
            val assetId = existingAsset?.releaseAssetId
                ?: stableId("$snapshotId/asset/${latest.selectedAsset.asset.id}")
            database.withTransaction {
                dao.upsertReleaseSnapshot(latest.toSnapshot(registeredAppId, snapshotId, now))
                dao.upsertReleaseAsset(existingAsset ?: latest.toAsset(snapshotId, assetId))
                dao.upsertRegisteredApp(
                    app.copy(
                        releaseDiscoveryStatus = ReleaseDiscoveryStatus.AVAILABLE.name,
                        releaseDiscoveryErrorCode = null,
                        releaseDiscoveryErrorMessage = null,
                        releaseMetadataEtag = latest.responseEtag,
                        lastReleaseCheckedAt = now,
                        updatedAt = now,
                    ),
                )
            }
            if (existingAsset == null) downloadReference(assetId)
        } catch (failure: Throwable) {
            val now = Instant.now().toString()
            dao.upsertRegisteredApp(
                app.copy(
                    releaseDiscoveryStatus = ReleaseDiscoveryStatus.FAILED.name,
                    releaseDiscoveryErrorCode = failure.errorCode(),
                    releaseDiscoveryErrorMessage = failure.message,
                    lastReleaseCheckedAt = now,
                    updatedAt = now,
                ),
            )
            throw failure
        }
    }

    suspend fun recoverInterruptedDownloads() {
        dao.getInterruptedDownloads().forEach { asset ->
            partFile(asset.releaseAssetId).delete()
            dao.upsertReleaseAsset(
                asset.copy(
                    downloadStatus = ReferenceDownloadStatus.FAILED.name,
                    downloadErrorCode = "DOWNLOAD_INTERRUPTED",
                    downloadErrorMessage = "The previous reference APK download was interrupted.",
                ),
            )
        }
    }

    private suspend fun downloadReference(assetId: String) {
        val asset = dao.getReleaseAsset(assetId) ?: error("Release asset was not found.")
        val downloading = asset.copy(
            downloadStatus = ReferenceDownloadStatus.DOWNLOADING.name,
            downloadErrorCode = null,
            downloadErrorMessage = null,
        )
        dao.upsertReleaseAsset(downloading)
        val partFile = partFile(assetId)
        val finalFile = finalFile(assetId)
        partFile.delete()
        try {
            val downloaded = downloader.download(
                stableAssetUrl = asset.stableAssetUrl,
                expectedSizeBytes = asset.providerSizeBytes,
                expectedProviderSha256 = asset.providerDigestSha256,
                destinationPart = partFile,
            )
            val inspection = inspector.inspect(partFile)
            Files.move(
                partFile.toPath(),
                finalFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
            dao.upsertReleaseAsset(
                downloading.copy(
                    downloadStatus = ReferenceDownloadStatus.VERIFIED.name,
                    localContentPath = finalFile.absolutePath,
                    downloadedSizeBytes = downloaded.bytesWritten,
                    computedRawSha256 = downloaded.computedSha256,
                    responseEtag = downloaded.responseEtag,
                    finalDownloadHost = downloaded.finalHost,
                    packageName = inspection.packageName,
                    versionName = inspection.versionName,
                    versionCode = inspection.versionCode,
                    signingCertificateSha256 = inspection.signingCertificateSha256.joinToString(","),
                    currentSignerSha256 = inspection.currentSignerSha256.joinToString(","),
                    existingInstallStatus = inspection.existingInstallStatus.name,
                    installedVersionName = inspection.installedVersionName,
                    installedVersionCode = inspection.installedVersionCode,
                    comparisonEligibility = ComparisonEligibility.INCOMPARABLE.name,
                    incomparableReason = PHASE_2B_REQUIRED_REASON,
                    downloadedAt = Instant.now().toString(),
                ),
            )
        } catch (failure: Throwable) {
            partFile.delete()
            dao.upsertReleaseAsset(
                downloading.copy(
                    downloadStatus = ReferenceDownloadStatus.FAILED.name,
                    downloadErrorCode = failure.errorCode(),
                    downloadErrorMessage = failure.message ?: "Reference APK verification failed.",
                ),
            )
            throw failure
        }
    }

    private fun ResolvedGitHubRelease.toSnapshot(appId: String, snapshotId: String, now: String) =
        ReleaseSnapshotEntity(
            releaseSnapshotId = snapshotId,
            registeredAppId = appId,
            providerReleaseId = release.id,
            tagName = release.tagName,
            resolvedCommitSha = resolvedCommitSha,
            releaseName = release.name ?: release.tagName,
            releaseUrl = release.htmlUrl,
            targetCommitishRaw = release.targetCommitish,
            isDraft = release.draft,
            isPrerelease = release.prerelease,
            isImmutable = release.immutable,
            releaseCreatedAt = release.createdAt,
            publishedAt = requireNotNull(release.publishedAt),
            fetchedAt = now,
        )

    private fun ResolvedGitHubRelease.toAsset(snapshotId: String, assetId: String) = ReleaseAssetEntity(
        releaseAssetId = assetId,
        releaseSnapshotId = snapshotId,
        providerAssetId = selectedAsset.asset.id,
        assetName = selectedAsset.asset.name,
        stableAssetUrl = selectedAsset.asset.browserDownloadUrl,
        selectionReason = selectedAsset.reason,
        contentType = selectedAsset.asset.contentType,
        providerSizeBytes = selectedAsset.asset.size,
        providerDigestSha256 = selectedAsset.providerSha256,
    )

    private fun stableId(value: String): String =
        UUID.nameUUIDFromBytes(value.toByteArray(StandardCharsets.UTF_8)).toString()

    private fun partFile(assetId: String) = File(referenceDirectory, "$assetId.part.apk")
    private fun finalFile(assetId: String) = File(referenceDirectory, "$assetId.apk")

    private fun Throwable.errorCode(): String = when (this) {
        is GitHubProviderException -> code
        is ReleaseAssetSelectionException -> code
        is com.sanka1610.reprodroid.data.artifact.ReferenceAssetDownloadException -> code
        else -> "REFERENCE_APK_FAILED"
    }

    private companion object {
        const val PROVIDER_GITHUB_RELEASES = "PUBLIC_GITHUB_RELEASES"
        const val PHASE_2B_REQUIRED_REASON = "RELEASE_BUILD_RECIPE_DEFERRED_TO_PHASE_2B"
    }
}
