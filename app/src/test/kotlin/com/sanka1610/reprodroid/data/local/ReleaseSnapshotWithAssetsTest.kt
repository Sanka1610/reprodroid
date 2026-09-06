package com.sanka1610.reprodroid.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReleaseSnapshotWithAssetsTest {
    @Test
    fun `multiple candidates have no selected asset until an explicit provider asset id is stored`() {
        val candidates = listOf(asset("10"), asset("11"))
        assertNull(ReleaseSnapshotWithAssets(snapshot(selectedProviderAssetId = null), candidates).selectedAsset)

        assertEquals(
            "11",
            requireNotNull(
                ReleaseSnapshotWithAssets(snapshot(selectedProviderAssetId = "11"), candidates).selectedAsset,
            ).providerAssetId,
        )
    }

    private fun snapshot(selectedProviderAssetId: String?) = ReleaseSnapshotEntity(
        releaseSnapshotId = "release",
        registeredAppId = "app",
        providerReleaseId = "1",
        tagName = "v1",
        resolvedCommitSha = "a".repeat(40),
        releaseName = "Version 1",
        releaseUrl = "https://github.com/example/project/releases/tag/v1",
        targetCommitishRaw = "main",
        isDraft = false,
        isPrerelease = false,
        isImmutable = false,
        releaseCreatedAt = "2026-09-01T00:00:00Z",
        publishedAt = "2026-09-01T00:00:00Z",
        fetchedAt = "2026-09-01T00:00:00Z",
        observationSha256 = "b".repeat(64),
        lastObservedAt = "2026-09-01T00:00:00Z",
        selectedProviderAssetId = selectedProviderAssetId,
    )

    private fun asset(providerAssetId: String) = ReleaseAssetEntity(
        releaseAssetId = "asset-$providerAssetId",
        releaseSnapshotId = "release",
        providerAssetId = providerAssetId,
        assetName = "app-$providerAssetId.apk",
        stableAssetUrl = "https://github.com/example/project/releases/download/v1/app-$providerAssetId.apk",
        selectionReason = AssetSelectionReason.MANUAL_SELECTION_REQUIRED.name,
        contentType = "application/vnd.android.package-archive",
        providerSizeBytes = 1024,
        providerDigestSha256 = "c".repeat(64),
    )
}
