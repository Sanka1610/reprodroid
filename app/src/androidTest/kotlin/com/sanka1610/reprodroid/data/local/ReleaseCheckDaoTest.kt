package com.sanka1610.reprodroid.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReleaseCheckDaoTest {
    private lateinit var database: ReproDroidDatabase
    private lateinit var dao: ReleaseCheckDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            ReproDroidDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.releaseCheckDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun candidateObservationAndOutboxAreStableAcrossReload() = runBlocking {
        database.managedAppDao().upsertRegisteredApp(app())
        val candidate = candidate()
        dao.upsertCandidate(candidate)
        dao.upsertOutbox(
            NotificationOutboxEntity(
                outboxId = "outbox",
                candidateId = candidate.candidateId,
                registeredAppId = candidate.registeredAppId,
                notificationType = ReleaseNotificationType.NEW_RELEASE.name,
                notificationId = 123,
                createdAt = NOW,
            ),
        )
        dao.upsertDedupHeader(
            NotificationDedupHeaderEntity(
                dedupKey = "dedup",
                registeredAppId = candidate.registeredAppId,
                provider = candidate.provider,
                instance = candidate.instance,
                providerRepositoryId = candidate.providerRepositoryId,
                providerReleaseId = candidate.providerReleaseId,
                observationSha256 = candidate.observationSha256,
                notificationType = ReleaseNotificationType.NEW_RELEASE.name,
                disposition = NotificationOutboxState.PENDING.name,
                notificationId = 123,
                firstSeenAt = NOW,
                lastSeenAt = NOW,
            ),
        )
        dao.upsertCooldown(
            ProviderCooldownEntity(
                provider = candidate.provider,
                instance = candidate.instance,
                reason = ReleaseCheckOutcome.PROVIDER_RATE_LIMITED.name,
                notBefore = "2026-09-06T01:00:00Z",
                rateLimitRemaining = 0,
                rateLimitResetAt = "2026-09-06T01:00:00Z",
                updatedAt = NOW,
            ),
        )

        assertEquals(candidate, dao.getCandidateByObservation("app", candidate.observationSha256))
        assertEquals(listOf("outbox"), dao.getPendingOutbox(20).map { it.outboxId })
        assertEquals("dedup", dao.getDedupHeaderByNotificationId(123)?.dedupKey)
        assertEquals("2026-09-06T01:00:00Z", dao.getCooldown("GITHUB", "github.com")?.notBefore)

        dao.markCandidateSeen(candidate.candidateId)
        assertEquals(false, dao.getCandidate(candidate.candidateId)?.unseen)
        dao.deleteCooldown("GITHUB", "github.com")
        assertNull(dao.getCooldown("GITHUB", "github.com"))
    }

    @Test
    fun activeAppQueryExcludesInactiveTrackingRows() = runBlocking {
        val appDao = database.managedAppDao()
        appDao.upsertRegisteredApp(app())
        appDao.upsertRegisteredApp(app("inactive", AppTrackingState.INACTIVE.name))

        val active = dao.getActiveApps()

        assertEquals(listOf("app"), active.map { it.registeredAppId })
        assertNotNull(appDao.getRegisteredApp("inactive"))
        assertTrue(active.all { it.trackingState == AppTrackingState.ACTIVE.name })
    }

    private fun app(
        registeredAppId: String = "app",
        trackingState: String = AppTrackingState.ACTIVE.name,
    ) = RegisteredAppEntity(
        registeredAppId = registeredAppId,
        displayName = registeredAppId,
        repositoryUrl = "https://github.com/example/$registeredAppId",
        canonicalRepositoryUrl = "https://github.com/example/$registeredAppId",
        provider = "PUBLIC_GITHUB_RELEASES",
        managementMode = ManagementMode.VERIFICATION.name,
        trackingState = trackingState,
        createdAt = NOW,
        updatedAt = NOW,
    )

    private fun candidate() = ReleaseCandidateEntity(
        candidateId = "candidate",
        registeredAppId = "app",
        provider = "GITHUB",
        instance = "github.com",
        providerRepositoryId = "42",
        providerReleaseId = "9007199254740993",
        tagName = "v1",
        resolvedCommitSha = "a".repeat(40),
        releaseName = "Version 1",
        releaseUrl = "https://github.com/example/app/releases/tag/v1",
        targetCommitishRaw = "main",
        isPrerelease = false,
        isImmutable = true,
        releaseCreatedAt = NOW,
        publishedAt = NOW,
        assetsJson = "[]",
        observationSha256 = "b".repeat(64),
        state = ReleaseCandidateState.NEW_RELEASE_DISCOVERED.name,
        firstSeenAt = NOW,
        lastSeenAt = NOW,
    )

    private companion object {
        const val NOW = "2026-09-06T00:00:00Z"
    }
}
