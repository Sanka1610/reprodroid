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

    private fun fixture() = ReleaseObservationInput(
        provider = "GITHUB",
        instance = "github.com",
        providerRepositoryId = "42",
        providerReleaseId = 7,
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
        providerAssetId = 9,
        assetName = "app.apk",
        stableAssetUrl = "https://github.com/example/app/releases/download/v1/app.apk",
        contentType = "application/vnd.android.package-archive",
        providerSizeBytes = 123,
        providerDigestSha256 = "b".repeat(64),
        selectionReason = "SINGLE_APK",
    )
}
