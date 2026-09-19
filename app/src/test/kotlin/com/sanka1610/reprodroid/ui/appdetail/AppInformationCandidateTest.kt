package com.sanka1610.reprodroid.ui.appdetail

import com.sanka1610.reprodroid.data.local.ComparisonRunStatus
import com.sanka1610.reprodroid.data.local.ManagementMode
import com.sanka1610.reprodroid.data.local.ReleaseAssetEntity
import com.sanka1610.reprodroid.data.local.ReleaseCandidateEntity
import com.sanka1610.reprodroid.data.local.ReleaseCandidateState
import com.sanka1610.reprodroid.data.local.ReleaseSnapshotEntity
import com.sanka1610.reprodroid.data.local.ReleaseSnapshotWithAssets
import com.sanka1610.reprodroid.data.local.ResourceAvailabilityEntity
import com.sanka1610.reprodroid.data.local.TrustLevel
import com.sanka1610.reprodroid.data.local.UpdateStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppInformationCandidateTest {
    @Test
    fun latestInstallableCandidateIgnoresReleaseWithoutApk() {
        val installable = candidate(
            id = "installable",
            providerReleaseId = "10",
            publishedAt = "2026-09-16T00:00:00Z",
            state = ReleaseCandidateState.NEW_RELEASE_DISCOVERED,
        )
        val newerWithoutApk = candidate(
            id = "without-apk",
            providerReleaseId = "11",
            publishedAt = "2026-09-17T00:00:00Z",
            state = ReleaseCandidateState.NO_APK_ASSET,
        )

        assertEquals(installable, listOf(installable, newerWithoutApk).latestInstallableCandidate())
    }

    @Test
    fun latestInstallableCandidateUsesProviderIdLengthAsStableTieBreaker() {
        val lower = candidate("lower", "99", null, ReleaseCandidateState.VERIFICATION_REQUIRED)
        val higher = candidate("higher", "100", null, ReleaseCandidateState.VERIFICATION_REQUIRED)

        assertEquals(higher, listOf(lower, higher).latestInstallableCandidate())
    }

    @Test
    fun latestInstallableCandidateReturnsNullWhenNoManualActionIsPossible() {
        assertNull(
            listOf(
                candidate("missing", "1", null, ReleaseCandidateState.NO_APK_ASSET),
                candidate("obsolete", "2", null, ReleaseCandidateState.OBSOLETE),
            ).latestInstallableCandidate(),
        )
    }

    @Test
    fun uninspectedCandidateRequiresAcquisitionEvenWhenPreviousReleaseWasReproducible() {
        val action = appInformationPrimaryAction(
            managementMode = ManagementMode.VERIFICATION.name,
            trustLevel = TrustLevel.REPRODUCIBLE,
            updateStatus = UpdateStatus.UP_TO_DATE.name,
            candidate = candidate(
                "new-release",
                "20",
                "2026-09-17T00:00:00Z",
                ReleaseCandidateState.VERIFICATION_REQUIRED,
            ),
        )

        assertEquals(AppInformationPrimaryAction.ACQUIRE, action)
    }

    @Test
    fun uninspectedCandidateDoesNotPromiseAnInstallation() {
        val action = appInformationPrimaryAction(
            managementMode = ManagementMode.ACQUISITION.name,
            trustLevel = null,
            updateStatus = null,
            candidate = candidate(
                "new-release",
                "20",
                "2026-09-17T00:00:00Z",
                ReleaseCandidateState.NEW_RELEASE_DISCOVERED,
            ),
        )

        assertEquals(AppInformationPrimaryAction.ACQUIRE, action)
    }

    @Test
    fun currentReleaseWithoutUpdateHasNoAction() {
        val action = appInformationPrimaryAction(
            managementMode = ManagementMode.ACQUISITION.name,
            trustLevel = null,
            updateStatus = UpdateStatus.UP_TO_DATE.name,
            candidate = null,
        )

        assertNull(action)
    }

    @Test
    fun inspectedSameOrOlderVersionDoesNotOfferAnUpdateDespiteRetainedCandidate() {
        val observed = candidate("current", "20", null, ReleaseCandidateState.VERIFICATION_REQUIRED)
        for (status in listOf(UpdateStatus.UP_TO_DATE, UpdateStatus.OLDER_THAN_INSTALLED)) {
            val actionable = listOf(observed).latestInstallableCandidate(inspectedRelease(observed, status))
            assertNull(actionable)
            assertNull(appInformationPrimaryAction(
                ManagementMode.ACQUISITION.name, null, status.name, actionable,
            ))
        }
    }

    @Test
    fun inspectedUpdateAndUninstalledApkRemainActionable() {
        val observed = candidate("current", "20", null, ReleaseCandidateState.NEW_RELEASE_DISCOVERED)
        for (status in listOf(UpdateStatus.UPDATE_AVAILABLE, UpdateStatus.NOT_INSTALLED, UpdateStatus.UNKNOWN)) {
            assertEquals(observed, listOf(observed).latestInstallableCandidate(inspectedRelease(observed, status)))
        }
    }

    @Test
    fun changedObservationWithSameTagIsNotHiddenByPreviousVersionInspection() {
        val old = candidate("old", "20", null, ReleaseCandidateState.NEW_RELEASE_DISCOVERED)
        val changed = old.copy(candidateId = "changed", observationSha256 = "c".repeat(64))
        assertEquals(changed, listOf(changed).latestInstallableCandidate(inspectedRelease(old, UpdateStatus.UP_TO_DATE)))
    }

    @Test
    fun completedNewestCandidateDoesNotFallBackToAnOlderCandidate() {
        val older = candidate("old", "19", null, ReleaseCandidateState.NEW_RELEASE_DISCOVERED)
        val current = older.copy(candidateId = "current", providerReleaseId = "20")
        assertNull(listOf(older, current).latestInstallableCandidate(inspectedRelease(current, UpdateStatus.UP_TO_DATE)))
    }

    @Test
    fun missingSelectionOrDifferentAppDoesNotSuppressCandidate() {
        val observed = candidate("current", "20", null, ReleaseCandidateState.NEW_RELEASE_DISCOVERED)
        val inspected = inspectedRelease(observed, UpdateStatus.UP_TO_DATE)
        assertEquals(observed, listOf(observed).latestInstallableCandidate(
            inspected.copy(snapshot = inspected.snapshot.copy(selectedProviderAssetId = null)),
        ))
        assertEquals(observed, listOf(observed).latestInstallableCandidate(
            inspected.copy(snapshot = inspected.snapshot.copy(registeredAppId = "another-app")),
        ))
    }

    @Test
    fun inspectedVersionRelationControlsInstallLabelWithoutDisplayName() {
        assertEquals(AppInformationPrimaryAction.UPDATE, appInformationPrimaryAction(
            ManagementMode.ACQUISITION.name, null, UpdateStatus.UPDATE_AVAILABLE.name, null,
        ))
        assertEquals(AppInformationPrimaryAction.INSTALL, appInformationPrimaryAction(
            ManagementMode.ACQUISITION.name, null, UpdateStatus.NOT_INSTALLED.name, null,
        ))
        assertNull(appInformationPrimaryAction(ManagementMode.ACQUISITION.name, null, UpdateStatus.UNKNOWN.name, null))
    }

    @Test
    fun activeVerificationTakesPriorityOverASeparateNewCandidate() {
        val next = candidate("new", "30", null, ReleaseCandidateState.NEW_RELEASE_DISCOVERED)
        ComparisonRunStatus.entries.filter { it != ComparisonRunStatus.COMPLETED }.forEach { state ->
            assertEquals(AppInformationPrimaryAction.VIEW_VERIFICATION, appInformationPrimaryAction(
                ManagementMode.VERIFICATION.name, null, UpdateStatus.UP_TO_DATE.name, next, state.name,
            ))
        }
        assertEquals(AppInformationPrimaryAction.ACQUIRE, appInformationPrimaryAction(
            ManagementMode.VERIFICATION.name, null, UpdateStatus.UP_TO_DATE.name, next, ComparisonRunStatus.COMPLETED.name,
        ))
    }

    @Test
    fun candidatePreparationRequiresTheSameObservationAndAnInspectedSelection() {
        val current = candidate("current", "20", null, ReleaseCandidateState.NEW_RELEASE_DISCOVERED)
        val uninspected = inspectedRelease(current, UpdateStatus.UPDATE_AVAILABLE)
        assertTrue(candidateNeedsPreparation(current, uninspected))
        val inspected = uninspected.copy(assets = uninspected.assets.map { it.copy(downloadStatus = "VERIFIED") })
        assertFalse(candidateNeedsPreparation(current, inspected))
        assertTrue(candidateNeedsPreparation(current.copy(observationSha256 = "c".repeat(64)), inspected))
        assertTrue(candidateNeedsPreparation(current, inspected.copy(snapshot = inspected.snapshot.copy(selectedProviderAssetId = null))))
    }

    @Test
    fun anInspectedApkNeedsPresentBytesBeforeOfferingVerificationOrInstallation() {
        val current = candidate("current", "20", null, ReleaseCandidateState.NEW_RELEASE_DISCOVERED)
        val asset = inspectedRelease(current, UpdateStatus.UPDATE_AVAILABLE).selectedAsset!!.copy(downloadStatus = "VERIFIED")
        val present = ResourceAvailabilityEntity(
            ownerType = "ANDROID", ownerId = "local", resourceKind = "REFERENCE_APK", resourceId = asset.releaseAssetId,
            state = "PRESENT", observedBytes = 1024, knownSha256 = null, lastUsedAt = null,
            checkedAt = "2026-09-20T00:00:00Z", deletionRunId = null, deletionReason = null,
        )
        assertTrue(referenceApkAvailable(asset, listOf(present)))
        assertFalse(referenceApkAvailable(asset, emptyList()))
        assertFalse(referenceApkAvailable(asset, listOf(present.copy(resourceId = "different-apk"))))
        listOf("MISSING", "CORRUPT", "DELETED", "UNKNOWN").forEach { state ->
            assertFalse(referenceApkAvailable(asset, listOf(present.copy(state = state))))
        }
        assertFalse(referenceApkAvailable(asset.copy(downloadStatus = "FAILED"), listOf(present)))
    }

    private fun inspectedRelease(candidate: ReleaseCandidateEntity, status: UpdateStatus) = ReleaseSnapshotWithAssets(
        snapshot = ReleaseSnapshotEntity(
            releaseSnapshotId = "snapshot", registeredAppId = candidate.registeredAppId,
            providerReleaseId = candidate.providerReleaseId, tagName = candidate.tagName,
            resolvedCommitSha = candidate.resolvedCommitSha, releaseName = candidate.releaseName,
            releaseUrl = candidate.releaseUrl, targetCommitishRaw = candidate.targetCommitishRaw,
            isDraft = false, isPrerelease = false, isImmutable = true,
            releaseCreatedAt = candidate.releaseCreatedAt, publishedAt = candidate.publishedAt,
            fetchedAt = candidate.lastSeenAt, lastObservedAt = candidate.lastSeenAt,
            observationSha256 = "d".repeat(64), metadataObservationSha256 = candidate.observationSha256,
            selectedProviderAssetId = "asset", observationSchemaVersion = 2,
        ),
        assets = listOf(ReleaseAssetEntity(
            releaseAssetId = "local-asset", releaseSnapshotId = "snapshot", providerAssetId = "asset",
            assetName = "app.apk", stableAssetUrl = "https://example.invalid/app.apk",
            selectionReason = "MANUAL_RELEASE_ASSET", contentType = "application/vnd.android.package-archive",
            providerSizeBytes = 1024, providerDigestSha256 = null, versionCode = 10,
            installedVersionCode = if (status == UpdateStatus.OLDER_THAN_INSTALLED) 11 else 10,
            updateStatus = status.name,
        )),
    )

    private fun candidate(
        id: String,
        providerReleaseId: String,
        publishedAt: String?,
        state: ReleaseCandidateState,
    ) = ReleaseCandidateEntity(
        candidateId = id,
        registeredAppId = "00000000-0000-0000-0000-000000000001",
        provider = "GITHUB",
        instance = "github.com",
        providerRepositoryId = "repository",
        providerReleaseId = providerReleaseId,
        tagName = "v$providerReleaseId",
        resolvedCommitSha = "a".repeat(40),
        releaseName = "Release $providerReleaseId",
        releaseUrl = "https://example.invalid/releases/$providerReleaseId",
        targetCommitishRaw = "main",
        isPrerelease = false,
        isImmutable = true,
        releaseCreatedAt = publishedAt ?: "2026-09-15T00:00:00Z",
        publishedAt = publishedAt,
        assetsJson = "[]",
        observationSha256 = "b".repeat(64),
        state = state.name,
        firstSeenAt = "2026-09-17T00:00:00Z",
        lastSeenAt = "2026-09-17T00:00:00Z",
    )
}
