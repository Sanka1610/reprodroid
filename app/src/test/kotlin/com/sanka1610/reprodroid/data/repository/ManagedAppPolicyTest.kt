package com.sanka1610.reprodroid.data.repository

import com.sanka1610.reprodroid.data.local.ComparisonOutcome
import com.sanka1610.reprodroid.data.local.ComparisonEligibility
import com.sanka1610.reprodroid.data.local.ComparisonRunEntity
import com.sanka1610.reprodroid.data.local.AdvancedComparisonSummaryEntity
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

    @Test
    fun `repeated build protocol requires all three exact comparisons to match`() {
        val exact = record(
            listOf(
                repeatedComparison(
                    officialPrimary = ComparisonOutcome.MATCH,
                    officialRepeat = ComparisonOutcome.MATCH,
                    localRepeatability = ComparisonOutcome.MATCH,
                ),
            ),
        )
        assertEquals(TrustLevel.REPRODUCIBLE, exact.trustLevel)

        val nondeterministic = record(
            listOf(
                repeatedComparison(
                    officialPrimary = ComparisonOutcome.MATCH,
                    officialRepeat = ComparisonOutcome.DIFFERENT,
                    localRepeatability = ComparisonOutcome.DIFFERENT,
                ),
            ),
        )
        assertEquals(TrustLevel.DIFFERENT, nondeterministic.trustLevel)
    }

    @Test
    fun `pinning mismatch does not change repeated build trust`() {
        val exactWithDifferentPinning = repeatedComparison(
            officialPrimary = ComparisonOutcome.MATCH,
            officialRepeat = ComparisonOutcome.MATCH,
            localRepeatability = ComparisonOutcome.MATCH,
        ).copy(
            runnerDependencyPinning = "LOCKFILE",
            repeatRunnerDependencyPinning = "LOCKFILE_OFFLINE",
        )

        assertEquals(TrustLevel.REPRODUCIBLE, record(listOf(exactWithDifferentPinning)).trustLevel)
    }

    @Test
    fun `repeated build remains buildable until repeat evidence is complete`() {
        val pending = record(
            listOf(
                repeatedComparison(
                    officialPrimary = ComparisonOutcome.MATCH,
                    officialRepeat = ComparisonOutcome.NOT_EVALUATED,
                    localRepeatability = ComparisonOutcome.NOT_EVALUATED,
                ),
            ),
        )

        assertEquals(TrustLevel.BUILDABLE, pending.trustLevel)
    }

    @Test
    fun `repeat comparison failure is incomparable even when the first build matched`() {
        val incomparable = record(
            listOf(
                repeatedComparison(
                    officialPrimary = ComparisonOutcome.MATCH,
                    officialRepeat = ComparisonOutcome.INCOMPARABLE,
                    localRepeatability = ComparisonOutcome.INCOMPARABLE,
                ).copy(repeatIncomparableReason = "REPEAT_LOCAL_ARTIFACT_MISSING"),
            ),
        )

        assertEquals(TrustLevel.INCOMPARABLE, incomparable.trustLevel)
    }

    @Test
    fun `established content difference is retained when repeat evidence is unavailable`() {
        val different = record(
            listOf(
                repeatedComparison(
                    officialPrimary = ComparisonOutcome.DIFFERENT,
                    officialRepeat = ComparisonOutcome.INCOMPARABLE,
                    localRepeatability = ComparisonOutcome.INCOMPARABLE,
                ).copy(repeatIncomparableReason = "RUNNER_JOB_FAILED_REPEAT"),
            ),
        )

        assertEquals(TrustLevel.DIFFERENT, different.trustLevel)
    }

    @Test
    fun `advanced semantic matches never promote an exact raw difference`() {
        val rawDifferent = repeatedComparison(
            officialPrimary = ComparisonOutcome.DIFFERENT,
            officialRepeat = ComparisonOutcome.MATCH,
            localRepeatability = ComparisonOutcome.MATCH,
        )
        val explanatoryMatch = AdvancedComparisonSummaryEntity(
            comparisonRunId = rawDifferent.comparisonRunId,
            registeredAppId = "app",
            axis = "OFFICIAL_PRIMARY",
            inventoryOutcome = "MATCH",
            dexStructuralOutcome = "MATCH",
            manifestSemanticOutcome = "MATCH",
            resourceTableSemanticOutcome = "MATCH",
        )

        val record = record(
            comparisons = listOf(rawDifferent),
            advancedComparisonSummaries = listOf(explanatoryMatch),
        )

        assertEquals(TrustLevel.DIFFERENT, record.trustLevel)
    }

    private fun record(
        comparisons: List<ComparisonRunEntity>,
        advancedComparisonSummaries: List<AdvancedComparisonSummaryEntity> = emptyList(),
    ): RegisteredAppRecord {
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
            providerReleaseId = "1",
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
            observationSha256 = "0".repeat(64),
            lastObservedAt = "2026-08-24T00:00:00Z",
            selectedProviderAssetId = "1",
        )
        val asset = ReleaseAssetEntity(
            releaseAssetId = "asset",
            releaseSnapshotId = "release",
            providerAssetId = "1",
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
            advancedComparisonSummaries = advancedComparisonSummaries,
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

    private fun repeatedComparison(
        officialPrimary: ComparisonOutcome,
        officialRepeat: ComparisonOutcome,
        localRepeatability: ComparisonOutcome,
    ) = comparison("release", "asset", "sha", officialPrimary).copy(
        protocolVersion = 2,
        localArtifactId = "artifact-a",
        repeatRunnerJobId = "job-b",
        repeatLocalArtifactId = if (officialRepeat == ComparisonOutcome.NOT_EVALUATED) null else "artifact-b",
        repeatOfficialOutcome = officialRepeat.name,
        repeatabilityOutcome = localRepeatability.name,
    )
}
