package com.sanka1610.reprodroid.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sanka1610.reprodroid.data.local.AppTrackingState
import com.sanka1610.reprodroid.data.local.AppRepositoryBindingEntity
import com.sanka1610.reprodroid.data.local.ComparisonEligibility
import com.sanka1610.reprodroid.data.local.ComparisonOutcome
import com.sanka1610.reprodroid.data.local.ComparisonRunEntity
import com.sanka1610.reprodroid.data.local.JobEntity
import com.sanka1610.reprodroid.data.local.ManagementMode
import com.sanka1610.reprodroid.data.local.ReferenceDownloadStatus
import com.sanka1610.reprodroid.data.local.RegisteredAppEntity
import com.sanka1610.reprodroid.data.local.ReleaseAssetEntity
import com.sanka1610.reprodroid.data.local.ReleaseCandidateState
import com.sanka1610.reprodroid.data.local.ReleaseCheckOutcome
import com.sanka1610.reprodroid.data.local.ReleaseSnapshotEntity
import com.sanka1610.reprodroid.data.local.ReproDroidDatabase
import com.sanka1610.reprodroid.data.local.RepositoryIdentityStatus
import com.sanka1610.reprodroid.data.provider.GitHubReleaseMetadataClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReleaseCheckRepositoryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val now = Instant.parse("2026-09-06T00:00:00Z")
    private val databases = mutableListOf<ReproDroidDatabase>()

    @After
    fun tearDown() {
        databases.forEach(ReproDroidDatabase::close)
    }

    @Test
    fun oldSnapshotObservationMismatchCannotBecomeVerifiedUpdate() = runBlocking {
        val commitSha = "c".repeat(40)
        val provider = MockEngine { request ->
            val body = when (request.url.encodedPath) {
                "/repos/example/project/releases/latest" -> RELEASE_JSON
                "/repos/example/project/git/ref/tags/v1" ->
                    """{"ref":"refs/tags/v1","object":{"type":"commit","sha":"$commitSha","url":"unused"}}"""
                else -> error("Unexpected request: ${request.url}")
            }
            respond(body, HttpStatusCode.OK, JSON_HEADERS)
        }
        val database = Room.inMemoryDatabaseBuilder(context, ReproDroidDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        databases += database
        val appId = "app"
        val snapshotId = "old-snapshot"
        val assetId = "old-asset"
        database.managedAppDao().upsertRegisteredApp(
            RegisteredAppEntity(
                registeredAppId = appId,
                displayName = "Example",
                repositoryUrl = "https://github.com/example/project",
                canonicalRepositoryUrl = "https://github.com/example/project",
                provider = "PUBLIC_GITHUB_RELEASES",
                managementMode = ManagementMode.ACQUISITION.name,
                trackingState = AppTrackingState.ACTIVE.name,
                createdAt = now.toString(),
                updatedAt = now.toString(),
            ),
        )
        database.managedAppDao().upsertRepositoryBinding(
            AppRepositoryBindingEntity(
                registeredAppId = appId,
                provider = "GITHUB",
                instance = "github.com",
                providerRepositoryId = "42",
                identityStatus = RepositoryIdentityStatus.VERIFIED.name,
                registrationSlot = "PRIMARY",
                verifiedAt = now.toString(),
            ),
        )
        database.jobDao().upsertJob(
            JobEntity(
                jobId = "job",
                executionMode = "SIMULATED",
                repositoryUrl = "https://github.com/example/project",
                revisionType = "TAG",
                revisionValue = "v1",
                simulationOutcome = "SUCCESS",
                state = "SUCCEEDED",
                progressPercent = 100,
                latestLogSequence = 0,
                errorCode = null,
                errorMessage = null,
                createdAt = now.toString(),
                updatedAt = now.toString(),
            ),
        )
        database.managedAppDao().upsertReleaseSnapshot(
            ReleaseSnapshotEntity(
                releaseSnapshotId = snapshotId,
                registeredAppId = appId,
                providerReleaseId = "100",
                tagName = "v1",
                resolvedCommitSha = commitSha,
                releaseName = "Version 1",
                releaseUrl = "https://github.com/example/project/releases/tag/v1",
                targetCommitishRaw = "main",
                isDraft = false,
                isPrerelease = false,
                isImmutable = false,
                releaseCreatedAt = "2026-09-01T00:00:00Z",
                publishedAt = "2026-09-01T00:00:00Z",
                fetchedAt = now.toString(),
                observationSha256 = "old-observation".padEnd(64, '0'),
                lastObservedAt = now.toString(),
                selectedProviderAssetId = "200",
            ),
        )
        database.managedAppDao().upsertReleaseAsset(
            ReleaseAssetEntity(
                releaseAssetId = assetId,
                releaseSnapshotId = snapshotId,
                providerAssetId = "200",
                assetName = "project.apk",
                stableAssetUrl = "https://github.com/example/project/releases/download/v1/project.apk",
                selectionReason = "SINGLE_APK",
                contentType = "application/vnd.android.package-archive",
                providerSizeBytes = 1024,
                providerDigestSha256 = "a".repeat(64),
                downloadStatus = ReferenceDownloadStatus.VERIFIED.name,
                comparisonEligibility = ComparisonEligibility.READY_FOR_COMPARISON.name,
            ),
        )
        database.managedAppDao().upsertComparisonRun(
            ComparisonRunEntity(
                comparisonRunId = "comparison",
                registeredAppId = appId,
                releaseSnapshotId = snapshotId,
                referenceAssetId = assetId,
                runnerJobId = "job",
                expectedCommitSha = commitSha,
                expectedRecipeId = "recipe",
                expectedVariantName = "release",
                status = "COMPLETED",
                outcome = ComparisonOutcome.MATCH.name,
                createdAt = now.toString(),
                updatedAt = now.toString(),
            ),
        )

        val repository = ReleaseCheckRepository(
            context = context,
            database = database,
            provider = GitHubReleaseMetadataClient(provider),
            environment = object : ReleaseCheckEnvironment {
                override fun currentState() = ReleaseCheckDeviceState(
                    networkAvailable = true,
                    networkMetered = false,
                    batteryPercent = 100,
                )
            },
            clock = Clock.fixed(now, ZoneOffset.UTC),
            zoneId = { ZoneOffset.UTC },
        )

        val outcome = repository.checkNow(appId)
        assertEquals(ReleaseCheckOutcome.NEW_RELEASE_DISCOVERED, outcome)
        val candidate = database.releaseCheckDao().getCandidates(appId).single()
        assertEquals(ReleaseCandidateState.NEW_RELEASE_DISCOVERED.name, candidate.state)
        assertNotEquals(ReleaseCandidateState.VERIFIED_UPDATE_AVAILABLE.name, candidate.state)
        assertNotNull(database.managedAppDao().getReleaseSnapshot(snapshotId))
    }

    @Test
    fun openingLatestCandidateStagesMetadataWithoutSelectingOrDownloadingAnApk() = runBlocking {
        val commitSha = "d".repeat(40)
        val provider = MockEngine { request ->
            val body = when (request.url.encodedPath) {
                "/repos/example/project/releases/latest" -> MULTI_ASSET_RELEASE_JSON
                "/repos/example/project/git/ref/tags/v2" ->
                    """{"ref":"refs/tags/v2","object":{"type":"commit","sha":"$commitSha","url":"unused"}}"""
                else -> error("Unexpected request: ${request.url}")
            }
            respond(body, HttpStatusCode.OK, JSON_HEADERS)
        }
        val database = Room.inMemoryDatabaseBuilder(context, ReproDroidDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        databases += database
        val appId = "app-stage"
        database.managedAppDao().upsertRegisteredApp(
            RegisteredAppEntity(
                registeredAppId = appId,
                displayName = "Example",
                repositoryUrl = "https://github.com/example/project",
                canonicalRepositoryUrl = "https://github.com/example/project",
                provider = "PUBLIC_GITHUB_RELEASES",
                managementMode = ManagementMode.ACQUISITION.name,
                trackingState = AppTrackingState.ACTIVE.name,
                createdAt = now.toString(),
                updatedAt = now.toString(),
            ),
        )
        database.managedAppDao().upsertRepositoryBinding(
            AppRepositoryBindingEntity(
                registeredAppId = appId,
                provider = "GITHUB",
                instance = "github.com",
                providerRepositoryId = "42",
                identityStatus = RepositoryIdentityStatus.VERIFIED.name,
                registrationSlot = "PRIMARY",
                verifiedAt = now.toString(),
            ),
        )
        val repository = ReleaseCheckRepository(
            context = context,
            database = database,
            provider = GitHubReleaseMetadataClient(provider),
            environment = object : ReleaseCheckEnvironment {
                override fun currentState() = ReleaseCheckDeviceState(
                    networkAvailable = true,
                    networkMetered = false,
                    batteryPercent = 100,
                )
            },
            clock = Clock.fixed(now, ZoneOffset.UTC),
            zoneId = { ZoneOffset.UTC },
        )

        assertEquals(ReleaseCheckOutcome.ASSET_SELECTION_REQUIRED, repository.checkNow(appId))
        val candidate = database.releaseCheckDao().getCandidates(appId).single()
        repository.stageCandidateForManualAction(candidate.candidateId)

        val record = database.managedAppDao().getRegisteredAppRecord(appId)
        val release = requireNotNull(record?.latestRelease)
        assertEquals(candidate.observationSha256, release.snapshot.metadataObservationSha256)
        assertNull(release.snapshot.selectedProviderAssetId)
        assertEquals(listOf("201", "202"), release.assets.map { it.providerAssetId }.sorted())
        release.assets.forEach { asset ->
            assertEquals(ReferenceDownloadStatus.NOT_DOWNLOADED.name, asset.downloadStatus)
            assertNull(asset.localContentPath)
            assertNull(asset.computedRawSha256)
        }
        assertEquals(false, database.releaseCheckDao().getCandidate(candidate.candidateId)?.unseen)
    }

    private companion object {
        val JSON_HEADERS = headersOf(HttpHeaders.ContentType, "application/json")
        const val RELEASE_JSON = """
            {
              "id":100,
              "tag_name":"v1",
              "target_commitish":"main",
              "name":"Version 1",
              "html_url":"https://github.com/example/project/releases/tag/v1",
              "draft":false,
              "prerelease":false,
              "immutable":false,
              "created_at":"2026-09-01T00:00:00Z",
              "published_at":"2026-09-01T00:00:00Z",
              "assets":[{
                "id":200,
                "name":"project.apk",
                "state":"uploaded",
                "content_type":"application/vnd.android.package-archive",
                "size":1024,
                "digest":"sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "browser_download_url":"https://github.com/example/project/releases/download/v1/project.apk"
              }]
            }
        """
        const val MULTI_ASSET_RELEASE_JSON = """
            {
              "id":101,
              "tag_name":"v2",
              "target_commitish":"main",
              "name":"Version 2",
              "html_url":"https://github.com/example/project/releases/tag/v2",
              "draft":false,
              "prerelease":false,
              "immutable":true,
              "created_at":"2026-09-02T00:00:00Z",
              "published_at":"2026-09-02T00:00:00Z",
              "assets":[{
                "id":201,
                "name":"project-store.apk",
                "state":"uploaded",
                "content_type":"application/vnd.android.package-archive",
                "size":1024,
                "digest":"sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                "browser_download_url":"https://github.com/example/project/releases/download/v2/project-store.apk"
              },{
                "id":202,
                "name":"project-github.apk",
                "state":"uploaded",
                "content_type":"application/vnd.android.package-archive",
                "size":2048,
                "digest":"sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc",
                "browser_download_url":"https://github.com/example/project/releases/download/v2/project-github.apk"
              }]
            }
        """
    }
}
