package com.sanka1610.reprodroid.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ReleaseObservationHasherTest {
    @Test
    fun `schema one JCS hash matches known vector`() {
        val input = fixture()
        assertEquals(
            "1359beb543cf167702cd15c496e71f4f7be59ce251b280805ac02a94c23b866c",
            ReleaseObservationHasher.sha256(input),
        )
        assertEquals(ReleaseObservationHasher.sha256(input), ReleaseObservationHasher.sha256(input.copy()))
    }

    @Test
    fun `same provider IDs with changed immutable metadata produce a new observation`() {
        val input = fixture()
        assertNotEquals(
            ReleaseObservationHasher.sha256(input),
            ReleaseObservationHasher.sha256(input.copy(resolvedCommitSha = "c".repeat(40))),
        )
        assertNotEquals(
            ReleaseObservationHasher.sha256(input),
            ReleaseObservationHasher.sha256(input.copy(providerSizeBytes = 124)),
        )
    }

    @Test
    fun `manual candidate set is order independent and part of the observation`() {
        val first = ReleaseObservationCandidate(
            providerAssetId = "10",
            assetName = "app-a.apk",
            stableAssetUrl = "https://github.com/example/app/releases/download/v1/app-a.apk",
            contentType = "application/vnd.android.package-archive",
            providerSizeBytes = 100,
            providerDigestSha256 = "a".repeat(64),
        )
        val second = first.copy(
            providerAssetId = "11",
            assetName = "app-b.apk",
            stableAssetUrl = "https://github.com/example/app/releases/download/v1/app-b.apk",
            providerDigestSha256 = "b".repeat(64),
        )
        val input = fixture().copy(
            providerAssetId = null,
            assetName = null,
            stableAssetUrl = null,
            contentType = null,
            providerSizeBytes = null,
            providerDigestSha256 = null,
            selectionReason = null,
            manualCandidates = listOf(first, second),
        )

        assertEquals(
            ReleaseObservationHasher.sha256(input),
            ReleaseObservationHasher.sha256(input.copy(manualCandidates = listOf(second, first))),
        )
        assertNotEquals(
            ReleaseObservationHasher.sha256(input),
            ReleaseObservationHasher.sha256(input.copy(manualCandidates = listOf(first))),
        )
    }

    @Test
    fun `schema two metadata hash is selection independent and keeps nullable provider claims`() {
        val candidate = ReleaseMetadataObservationCandidate(
            providerAssetId = "9",
            assetName = "app.apk",
            stableAssetUrl = "https://codeberg.org/example/app/releases/download/v1/app.apk",
            contentType = null,
            providerSizeBytes = 123,
            providerDigestSha256 = null,
            providerCreatedAt = "2026-09-01T00:00:00Z",
        )
        val input = ReleaseMetadataObservationInput(
            provider = "CODEBERG",
            instance = "codeberg.org",
            providerRepositoryId = "42",
            providerReleaseId = "7",
            tagName = "v1",
            resolvedCommitSha = "a".repeat(40),
            targetCommitishRaw = "main",
            releaseName = "Version 1",
            releaseUrl = "https://codeberg.org/example/app/releases/tag/v1",
            isDraft = false,
            isPrerelease = false,
            isImmutable = false,
            releaseCreatedAt = "2026-09-01T00:00:00Z",
            publishedAt = null,
            candidates = listOf(candidate),
        )
        assertEquals(
            "4cb6ec83160c58e1ac610d41a4535729ba056fb58e2aeb70474190db4da5d27c",
            ReleaseObservationHasher.metadataSha256(input),
        )
        assertNotEquals(
            ReleaseObservationHasher.metadataSha256(input),
            ReleaseObservationHasher.metadataSha256(input.copy(candidates = listOf(candidate.copy(providerCreatedAt = null)))),
        )
    }

    @Test
    fun `downloaded content hash binds metadata asset and raw bytes`() {
        assertEquals(
            "d239ce93d822ca2e5a84b8bf17649d8991d2ba443362587bdc2ce53893b5911a",
            ReleaseObservationHasher.downloadedContentSha256("b".repeat(64), "9", "c".repeat(64)),
        )
        assertNotEquals(
            ReleaseObservationHasher.downloadedContentSha256("b".repeat(64), "9", "c".repeat(64)),
            ReleaseObservationHasher.downloadedContentSha256("b".repeat(64), "10", "c".repeat(64)),
        )
    }

    private fun fixture() = ReleaseObservationInput(
        provider = "GITHUB",
        instance = "github.com",
        providerRepositoryId = "42",
        providerReleaseId = "7",
        tagName = "v1",
        resolvedCommitSha = "a".repeat(40),
        targetCommitishRaw = "main",
        releaseName = "Version 1",
        releaseUrl = "https://github.com/example/app/releases/tag/v1",
        isDraft = false,
        isPrerelease = false,
        isImmutable = true,
        releaseCreatedAt = "2026-08-31T00:00:00Z",
        publishedAt = "2026-09-01T00:00:00Z",
        providerAssetId = "9",
        assetName = "app.apk",
        stableAssetUrl = "https://github.com/example/app/releases/download/v1/app.apk",
        contentType = "application/vnd.android.package-archive",
        providerSizeBytes = 123,
        providerDigestSha256 = "b".repeat(64),
        selectionReason = "SINGLE_APK",
    )
}
