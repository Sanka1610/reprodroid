package com.sanka1610.reprodroid.ui.appdetail

import com.sanka1610.reprodroid.data.local.ReleaseCandidateEntity
import com.sanka1610.reprodroid.data.local.ReleaseCandidateState
import com.sanka1610.reprodroid.data.local.ManagementMode
import com.sanka1610.reprodroid.data.local.TrustLevel
import com.sanka1610.reprodroid.data.local.UpdateStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun verificationCandidateUsesVerificationActionEvenWhenPreviousReleaseWasReproducible() {
        val action = appInformationPrimaryAction(
            managementMode = ManagementMode.VERIFICATION.name,
            trustLevel = TrustLevel.REPRODUCIBLE,
            installedVersionName = "1.0.0",
            updateStatus = UpdateStatus.UP_TO_DATE.name,
            candidate = candidate(
                "new-release",
                "20",
                "2026-09-17T00:00:00Z",
                ReleaseCandidateState.VERIFICATION_REQUIRED,
            ),
        )

        assertEquals(AppInformationPrimaryAction.VERIFY_UPDATE, action)
    }

    @Test
    fun acquisitionCandidateUsesInstallActionForFreshInstall() {
        val action = appInformationPrimaryAction(
            managementMode = ManagementMode.ACQUISITION.name,
            trustLevel = null,
            installedVersionName = null,
            updateStatus = null,
            candidate = candidate(
                "new-release",
                "20",
                "2026-09-17T00:00:00Z",
                ReleaseCandidateState.NEW_RELEASE_DISCOVERED,
            ),
        )

        assertEquals(AppInformationPrimaryAction.INSTALL, action)
    }

    @Test
    fun currentReleaseWithoutUpdateHasNoAction() {
        val action = appInformationPrimaryAction(
            managementMode = ManagementMode.ACQUISITION.name,
            trustLevel = null,
            installedVersionName = "1.0.0",
            updateStatus = UpdateStatus.UP_TO_DATE.name,
            candidate = null,
        )

        assertNull(action)
    }

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
