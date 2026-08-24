package com.sanka1610.reprodroid.data.repository

import com.sanka1610.reprodroid.data.local.ComparisonOutcome
import com.sanka1610.reprodroid.data.local.ComparisonEligibility
import com.sanka1610.reprodroid.data.local.ComparisonRunEntity
import com.sanka1610.reprodroid.data.local.InstallationSource
import com.sanka1610.reprodroid.data.local.ManagementMode
import com.sanka1610.reprodroid.data.local.RegisteredAppEntity
import com.sanka1610.reprodroid.data.local.RegisteredAppRecord
import com.sanka1610.reprodroid.data.local.ReleaseAssetEntity
import com.sanka1610.reprodroid.data.local.ReleaseSnapshotEntity
import com.sanka1610.reprodroid.data.local.ReleaseSnapshotWithAssets
import com.sanka1610.reprodroid.data.local.TrustLevel
import com.sanka1610.reprodroid.data.local.UpdateStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ManagedAppPolicyTest {
    @Test
    fun `update relation keeps version ordering separate from signing`() {
        assertEquals(UpdateStatus.UNKNOWN, evaluateUpdateStatus(null, 10))
        assertEquals(UpdateStatus.NOT_INSTALLED, evaluateUpdateStatus(10, null))
        assertEquals(UpdateStatus.UPDATE_AVAILABLE, evaluateUpdateStatus(11, 10))
        assertEquals(UpdateStatus.UP_TO_DATE, evaluateUpdateStatus(10, 10))
        assertEquals(UpdateStatus.OLDER_THAN_INSTALLED, evaluateUpdateStatus(9, 10))
    }

    @Test
    fun `local installation source is restricted to verification mode`() {
        validateModeAndInstallationSource(ManagementMode.VERIFICATION, InstallationSource.LOCAL_BUILD)
        validateModeAndInstallationSource(ManagementMode.ACQUISITION, InstallationSource.OFFICIAL_RELEASE)
        assertThrows(IllegalArgumentException::class.java) {
            validateModeAndInstallationSource(ManagementMode.ACQUISITION, InstallationSource.LOCAL_BUILD)
        }
    }

    @Test
    fun `installation source can change only while package is not installed`() {
        assertEquals(true, canChangeInstallationSource(null))
        assertEquals(false, canChangeInstallationSource(1))
    }

    @Test
    fun `trust result is scoped to the current selected release asset`() {
        val stale = record(listOf(comparison("old-release", "old-asset", "old-sha", ComparisonOutcome.MATCH)))
        assertNull(stale.currentComparison)
        assertNull(stale.trustLevel)

        val current = record(
            listOf(
                comparison("old-release", "old-asset", "old-sha", ComparisonOutcome.MATCH),
                comparison("release", "asset", "sha", ComparisonOutcome.MATCH),
            ),
        )
        assertEquals("asset", current.currentComparison?.referenceAssetId)
        assertEquals(TrustLevel.REPRODUCIBLE, current.trustLevel)
    }

    @Test
    fun `downloaded local artifact is buildable before comparison reaches a terminal outcome`() {
        val buildable = record(
            listOf(
                comparison("release", "asset", "sha", ComparisonOutcome.NOT_EVALUATED)
                    .copy(localArtifactId = "artifact"),
            ),
        )

        assertEquals(TrustLevel.BUILDABLE, buildable.trustLevel)
    }

    private fun record(comparisons: List<ComparisonRunEntity>): RegisteredAppRecord {
        val app = RegisteredAppEntity(
            registeredAppId = "app",
            displayName = "Example",
            repositoryUrl = "https://github.com/example/app",
            canonicalRepositoryUrl = "https://github.com/example/app",
            provider = "PUBLIC_GITHUB_RELEASES",
            managementMode = ManagementMode.VERIFICATION.name,
            createdAt = "2026-08-24T00:00:00Z",
            updatedAt = "2026-08-24T00:00:00Z",
        )
        val snapshot = ReleaseSnapshotEntity(
            releaseSnapshotId = "release",
            registeredAppId = "app",
            providerReleaseId = 1,
            tagName = "1.0",
            resolvedCommitSha = "sha",
            releaseName = "1.0",
            releaseUrl = "https://github.com/example/app/releases/tag/1.0",
            targetCommitishRaw = "main",
            isDraft = false,
            isPrerelease = false,
            isImmutable = false,
            releaseCreatedAt = "2026-08-24T00:00:00Z",
            publishedAt = "2026-08-24T00:00:00Z",
            fetchedAt = "2026-08-24T00:00:00Z",
            selectedProviderAssetId = 1,
        )
        val asset = ReleaseAssetEntity(
            releaseAssetId = "asset",
            releaseSnapshotId = "release",
            providerAssetId = 1,
            assetName = "app.apk",
            stableAssetUrl = "https://github.com/example/app/releases/download/1.0/app.apk",
            selectionReason = "SINGLE_APK",
            contentType = "application/vnd.android.package-archive",
            providerSizeBytes = 1,
            providerDigestSha256 = null,
            comparisonEligibility = ComparisonEligibility.READY_FOR_COMPARISON.name,
        )
        return RegisteredAppRecord(
            app = app,
            releases = listOf(ReleaseSnapshotWithAssets(snapshot, listOf(asset))),
            comparisons = comparisons,
            releaseInstallAttempts = emptyList(),
        )
    }

    private fun comparison(
        releaseId: String,
        assetId: String,
        sha: String,
        outcome: ComparisonOutcome,
    ) = ComparisonRunEntity(
        comparisonRunId = "$releaseId-$assetId",
        registeredAppId = "app",
        releaseSnapshotId = releaseId,
        referenceAssetId = assetId,
        runnerJobId = "job",
        expectedCommitSha = sha,
        expectedRecipeId = "recipe",
        expectedVariantName = "release",
        outcome = outcome.name,
        createdAt = "2026-08-24T00:00:00Z",
        updatedAt = "2026-08-24T00:00:00Z",
    )
}
