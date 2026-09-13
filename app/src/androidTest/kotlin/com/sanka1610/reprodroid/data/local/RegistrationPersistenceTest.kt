package com.sanka1610.reprodroid.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sanka1610.reprodroid.data.local.InstallationSource
import com.sanka1610.reprodroid.data.local.AppRepositoryBindingEntity
import com.sanka1610.reprodroid.data.local.ManagementMode
import com.sanka1610.reprodroid.data.local.AssetSelectionReason
import com.sanka1610.reprodroid.data.local.ReferenceDownloadStatus
import com.sanka1610.reprodroid.data.local.ReleaseDiscoveryStatus
import com.sanka1610.reprodroid.data.local.RegisteredAppEntity
import com.sanka1610.reprodroid.data.local.ReleaseAssetEntity
import com.sanka1610.reprodroid.data.local.ReleaseObservationHasher
import com.sanka1610.reprodroid.data.local.ReleaseObservationInput
import com.sanka1610.reprodroid.data.local.ReleaseSnapshotEntity
import com.sanka1610.reprodroid.data.local.ReproDroidDatabase
import com.sanka1610.reprodroid.data.local.RepositoryIdentityStatus
import com.sanka1610.reprodroid.data.network.RunnerApiClient
import com.sanka1610.reprodroid.data.network.SimulationOutcome
import com.sanka1610.reprodroid.data.network.RevisionType
import com.sanka1610.reprodroid.data.provider.GitHubProviderException
import com.sanka1610.reprodroid.data.provider.GitHubReleasesClient
import com.sanka1610.reprodroid.data.provider.GitHubRepository
import com.sanka1610.reprodroid.data.provider.GitHubRepositoryDiscoveryClient
import com.sanka1610.reprodroid.data.provider.GitHubRepositoryIdentity
import com.sanka1610.reprodroid.data.provider.RepositoryRegistrationPreview
import com.sanka1610.reprodroid.data.provider.StaticDiscoveryResult
import com.sanka1610.reprodroid.data.provider.StaticGradleCandidate
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RegistrationPersistenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun freshApplicationStateRegistersReproDroidExactlyOnceWithoutNetworkWork() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, ReproDroidDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val runnerApi = RunnerApiClient("", MockEngine { error("Unexpected Runner request") })
            val repository = ManagedAppRepository(context, database, JobRepository(context, database, runnerApi))

            repository.ensureSettings(initializeSelfRegistration = true)
            repository.ensureSettings(initializeSelfRegistration = true)

            val apps = database.managedAppDao().getRegisteredApps()
            assertEquals(1, apps.size)
            val app = apps.single()
            assertEquals("ReproDroid", app.displayName)
            assertEquals("https://github.com/Sanka1610/reprodroid", app.canonicalRepositoryUrl)
            assertEquals("RELEASE", app.releaseVariantPreference)
            assertFalse(app.useGlobalReleaseVariant)
            val binding = requireNotNull(database.managedAppDao().getRepositoryBinding(app.registeredAppId))
            assertEquals("GITHUB", binding.provider)
            assertEquals("github.com", binding.instance)
            assertEquals("1340628011", binding.providerRepositoryId)
            assertEquals("VERIFIED", binding.identityStatus)
            assertTrue(requireNotNull(database.managedAppDao().getGlobalSettings()).selfRegistrationInitialized)
            assertEquals(0, rowCount(database, "jobs"))
            assertEquals(0, rowCount(database, "release_snapshots"))
        } finally {
            database.close()
        }
    }

    @Test
    fun sourceOnlyRegistrationAndConfigurationDoNotCreateReleaseApkOrJobState() = runBlocking {
        var runnerRequests = 0
        val runnerEngine = MockEngine {
            runnerRequests++
            respond("unexpected", HttpStatusCode.InternalServerError)
        }
        val database = Room.inMemoryDatabaseBuilder(context, ReproDroidDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
                val jobRepository = JobRepository(context, database, RunnerApiClient("", runnerEngine))
                val repository = ManagedAppRepository(context, database, jobRepository)
                repository.ensureSettings()

                val appId = repository.registerRepository(
                    preview(),
                    ManagementMode.VERIFICATION,
                    InstallationSource.OFFICIAL_RELEASE,
                )
                val record = requireNotNull(database.managedAppDao().getRegisteredAppRecord(appId))
                assertEquals("RELEASE", record.app.releaseVariantPreference)
                assertFalse(record.app.useGlobalReleaseVariant)
                assertEquals("VERIFIED", record.repositoryBinding?.identityStatus)
                assertEquals("COMPLETE", record.latestSourceDiscovery?.state)
                assertEquals(1, record.latestSourceDiscovery?.candidateCount)
                assertTrue(record.releases.isEmpty())
                assertEquals("DRAFT", record.selectedBuildConfiguration?.validationState)
                assertEquals(0, rowCount(database, "release_snapshots"))
                assertEquals(0, rowCount(database, "release_assets"))
                assertEquals(0, rowCount(database, "jobs"))
                assertEquals(0, rowCount(database, "artifacts"))

                val configuredInput = BuildConfigurationInput(
                    buildRoot = ".",
                    modulePath = ":app",
                    variant = "release",
                    tasks = listOf(":app:assembleRelease"),
                    javaMajor = 21,
                    gradleVersion = "9.1.0",
                    compileSdk = 36,
                    buildToolsVersion = "36.0.0",
                )
                val configured = repository.saveBuildConfiguration(appId, 1, configuredInput)
                assertEquals(2, configured.revision)
                assertEquals("CONFIGURED", configured.validationState)
                val idempotent = repository.saveBuildConfiguration(appId, 2, configuredInput)
                assertEquals(configured.revision, idempotent.revision)
                assertEquals(configured.contentSha256, idempotent.contentSha256)
                assertThrows(IllegalStateException::class.java) {
                    runBlocking {
                        repository.saveBuildConfiguration(
                            appId,
                            1,
                            configuredInput.copy(variant = "debug"),
                        )
                    }
                }
                assertEquals(2, rowCount(database, "app_build_configurations"))
                assertEquals(0, rowCount(database, "release_snapshots"))
                assertEquals(0, rowCount(database, "jobs"))
                assertEquals(0, runnerRequests)

                assertThrows(IllegalStateException::class.java) {
                    runBlocking {
                        repository.registerRepository(
                            preview(),
                            ManagementMode.VERIFICATION,
                            InstallationSource.OFFICIAL_RELEASE,
                        )
                    }
                }
                assertEquals(1, rowCount(database, "registered_apps"))

                assertThrows(IllegalStateException::class.java) {
                    runBlocking {
                        repository.registerRepository(
                            preview().copy(normalizedInputUrl = "https://github.com/example/different"),
                            ManagementMode.VERIFICATION,
                            InstallationSource.OFFICIAL_RELEASE,
                            separateManagementTarget = true,
                        )
                    }
                }
                assertEquals(1, rowCount(database, "registered_apps"))
        } finally {
            database.close()
        }
    }

    @Test
    fun phaseFourExecutionMutationsDoNotFallBackToRunnerApiV1() = runBlocking {
        var runnerRequests = 0
        val runnerEngine = MockEngine {
            runnerRequests++
            respond("unexpected", HttpStatusCode.InternalServerError)
        }
        val database = Room.inMemoryDatabaseBuilder(context, ReproDroidDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
                val jobs = JobRepository(context, database, RunnerApiClient("", runnerEngine))
                listOf<suspend () -> Unit>(
                    { jobs.createSimulatedJob("https://github.com/example/project", RevisionType.TAG, "1", SimulationOutcome.SUCCESS) },
                    { jobs.confirmRealBuild("job", "1".repeat(40)) },
                    { jobs.retryJob("job") },
                ).forEach { operation ->
                    val failure = assertThrows(IllegalStateException::class.java) {
                        runBlocking { operation() }
                    }
                    assertTrue(failure.message.orEmpty().contains("API v2"))
                }
                assertEquals(0, runnerRequests)
                assertEquals(0, rowCount(database, "jobs"))
        } finally {
            database.close()
        }
    }

    @Test
    fun ambiguousReleaseRequiresExplicitAssetSelectionBeforeDownload() = runBlocking {
        val commitSha = "c".repeat(40)
        val providerEngine = MockEngine { request ->
            val response = when (request.url.encodedPath) {
                "/repos/example/project/releases/latest" -> AMBIGUOUS_RELEASE_JSON
                "/repos/example/project/git/ref/tags/v1" ->
                    """{"ref":"refs/tags/v1","object":{"type":"commit","sha":"$commitSha","url":"unused"}}"""
                else -> error("Unexpected request: ${request.url}")
            }
            respond(response, HttpStatusCode.OK, JSON_HEADERS)
        }
        val database = Room.inMemoryDatabaseBuilder(context, ReproDroidDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repository = ManagedAppRepository(
                context = context,
                database = database,
                jobRepository = JobRepository(context, database, RunnerApiClient("")),
                provider = GitHubReleasesClient(providerEngine),
                repositoryDiscoveryClient = GitHubRepositoryDiscoveryClient(successfulRefreshDiscoveryEngine()),
            )
            repository.ensureSettings()
            val appId = "ambiguous-app"
            val now = "2026-09-05T00:00:00Z"
            database.managedAppDao().upsertRegisteredApp(
                RegisteredAppEntity(
                    registeredAppId = appId,
                    displayName = "project",
                    repositoryUrl = "https://github.com/example/project",
                    canonicalRepositoryUrl = "https://github.com/example/project",
                    provider = "PUBLIC_GITHUB_RELEASES",
                    managementMode = ManagementMode.VERIFICATION.name,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            database.managedAppDao().upsertRepositoryBinding(
                AppRepositoryBindingEntity(
                    registeredAppId = appId,
                    provider = "GITHUB",
                    instance = "github.com",
                    providerRepositoryId = TEST_PROVIDER_REPOSITORY_ID,
                    identityStatus = RepositoryIdentityStatus.VERIFIED.name,
                    registrationSlot = "PRIMARY",
                    verifiedAt = now,
                ),
            )

            repository.refresh(appId)

            val afterRefresh = requireNotNull(database.managedAppDao().getRegisteredAppRecord(appId))
            val snapshot = requireNotNull(afterRefresh.latestRelease)
            assertEquals(ReleaseDiscoveryStatus.AWAITING_ASSET_SELECTION.name, afterRefresh.app.releaseDiscoveryStatus)
            assertEquals(2, snapshot.assets.size)
            assertNull(snapshot.selectedAsset)
            assertTrue(snapshot.assets.all {
                it.selectionReason == AssetSelectionReason.MANUAL_SELECTION_REQUIRED.name &&
                    it.downloadStatus == ReferenceDownloadStatus.NOT_DOWNLOADED.name
            })

            val selected = snapshot.assets.single { it.providerAssetId == "201" }
            database.managedAppDao().upsertReleaseAsset(
                selected.copy(downloadStatus = ReferenceDownloadStatus.VERIFIED.name),
            )
            repository.selectReleaseAsset(appId, snapshot.snapshot.releaseSnapshotId, "201")

            val afterSelection = requireNotNull(database.managedAppDao().getRegisteredAppRecord(appId))
            val selectedRelease = requireNotNull(afterSelection.latestRelease)
            assertEquals(ReleaseDiscoveryStatus.AVAILABLE.name, afterSelection.app.releaseDiscoveryStatus)
            assertEquals("201", selectedRelease.snapshot.selectedProviderAssetId)
            assertEquals(
                AssetSelectionReason.MANUAL_RELEASE_ASSET.name,
                requireNotNull(selectedRelease.selectedAsset).selectionReason,
            )
            assertEquals(2, selectedRelease.assets.size)
        } finally {
            database.close()
        }
    }

    @Test
    fun explicitSeparateManagementTargetUsesAnIndependentAppAndConfigurationSlot() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, ReproDroidDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val jobs = JobRepository(context, database, RunnerApiClient(""))
            val repository = ManagedAppRepository(context, database, jobs)
            repository.ensureSettings()

            val primary = repository.registerRepository(
                preview(),
                ManagementMode.VERIFICATION,
                InstallationSource.OFFICIAL_RELEASE,
            )
            val separate = repository.registerRepository(
                preview(),
                ManagementMode.ACQUISITION,
                InstallationSource.OFFICIAL_RELEASE,
                separateManagementTarget = true,
            )

            assertNotEquals(primary, separate)
            assertEquals(2, rowCount(database, "registered_apps"))
            assertEquals(2, rowCount(database, "app_build_configurations"))
            database.openHelper.readableDatabase.query(
                "SELECT registrationSlot FROM app_repository_bindings ORDER BY registrationSlot",
            ).use { cursor ->
                val slots = buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
                assertEquals(2, slots.distinct().size)
                assertTrue("PRIMARY" in slots)
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun settingsOnlySourceRegistrationSurvivesColdDatabaseOpenWithoutReleaseOrRunner() = runBlocking {
        val databaseName = "phase4-settings-only-cold.sqlite3"
        context.deleteDatabase(databaseName)
        var runnerRequests = 0
        val runnerEngine = MockEngine {
            runnerRequests++
            respond("unexpected", HttpStatusCode.InternalServerError)
        }
        val first = Room.databaseBuilder(context, ReproDroidDatabase::class.java, databaseName).build()
        lateinit var appId: String
        try {
            val jobs = JobRepository(context, first, RunnerApiClient("", runnerEngine))
            val repository = ManagedAppRepository(context, first, jobs)
            repository.ensureSettings()
            appId = repository.registerRepository(
                preview().copy(
                    discovery = preview().discovery.copy(
                        candidates = listOf(
                            preview().discovery.candidates.single().copy(
                                relativePath = "settings.gradle.kts",
                                fileKind = "settings.gradle.kts",
                            ),
                        ),
                    ),
                ),
                ManagementMode.VERIFICATION,
                InstallationSource.OFFICIAL_RELEASE,
            )
        } finally {
            first.close()
        }

        val reopened = Room.databaseBuilder(context, ReproDroidDatabase::class.java, databaseName).build()
        try {
            val record = requireNotNull(reopened.managedAppDao().getRegisteredAppRecord(appId))
            assertEquals("COMPLETE", record.latestSourceDiscovery?.state)
            val discoveryId = requireNotNull(record.latestSourceDiscovery?.discoveryId)
            assertEquals(
                "settings.gradle.kts",
                reopened.managedAppDao().getGradleCandidates(discoveryId).single().fileKind,
            )
            assertTrue(record.releases.isEmpty())
            assertEquals(0, rowCount(reopened, "jobs"))
            assertEquals(0, rowCount(reopened, "release_assets"))
            assertEquals(0, runnerRequests)
        } finally {
            reopened.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun concurrentPrimaryRegistrationsConvergeToOneStoredApp() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, ReproDroidDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val jobs = JobRepository(context, database, RunnerApiClient(""))
            val repository = ManagedAppRepository(context, database, jobs)
            repository.ensureSettings()

            val outcomes = coroutineScope {
                (0 until 2).map {
                    async(Dispatchers.Default) {
                        runCatching {
                            repository.registerRepository(
                                preview(),
                                ManagementMode.VERIFICATION,
                                InstallationSource.OFFICIAL_RELEASE,
                            )
                        }
                    }
                }.awaitAll()
            }

            assertEquals(1, outcomes.count { it.isSuccess })
            assertEquals(1, outcomes.count { it.isFailure })
            assertEquals(1, rowCount(database, "registered_apps"))
            assertEquals(1, rowCount(database, "app_repository_bindings"))
        } finally {
            database.close()
        }
    }

    @Test
    fun failedRegisteredRepositoryRefreshKeepsPreviousSuccessAndStoresLatestAttempt() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, ReproDroidDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val jobs = JobRepository(context, database, RunnerApiClient(""))
            val appId = ManagedAppRepository(context, database, jobs).run {
                ensureSettings()
                registerRepository(
                    preview(),
                    ManagementMode.VERIFICATION,
                    InstallationSource.OFFICIAL_RELEASE,
                )
            }
            val unavailableGitHub = MockEngine {
                respond(
                    """{"message":"Not Found"}""",
                    HttpStatusCode.NotFound,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
            val repository = ManagedAppRepository(
                context = context,
                database = database,
                jobRepository = jobs,
                repositoryDiscoveryClient = GitHubRepositoryDiscoveryClient(unavailableGitHub),
            )

            assertThrows(GitHubProviderException::class.java) {
                runBlocking { repository.refreshSourceDiscovery(appId) }
            }

            val record = requireNotNull(database.managedAppDao().getRegisteredAppRecord(appId))
            assertEquals("FAILED", record.latestSourceDiscovery?.state)
            assertEquals("NOT_FOUND_OR_NOT_PUBLIC", record.latestSourceDiscovery?.reason)
            assertEquals(2, rowCount(database, "source_discoveries"))
            assertEquals(
                1,
                database.openHelper.readableDatabase.query(
                    "SELECT COUNT(*) FROM source_discoveries WHERE state = 'COMPLETE' AND candidateCount = 1",
                ).use { cursor ->
                    check(cursor.moveToFirst())
                    cursor.getInt(0)
                },
            )
            assertEquals(1, rowCount(database, "gradle_candidates"))
        } finally {
            database.close()
        }
    }

    @Test
    fun changedProviderIdentityDoesNotRebindAndStoresFailedAttempt() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, ReproDroidDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val jobs = JobRepository(context, database, RunnerApiClient(""))
            val appId = ManagedAppRepository(context, database, jobs).run {
                ensureSettings()
                registerRepository(
                    preview(),
                    ManagementMode.VERIFICATION,
                    InstallationSource.OFFICIAL_RELEASE,
                )
            }
            val changedIdentity = MockEngine {
                respond(
                    """{
                        "id":987654321,"name":"project","full_name":"example/project","private":false,
                        "html_url":"https://github.com/example/project","default_branch":"main",
                        "owner":{"login":"example"}
                    }""".trimIndent(),
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
            val repository = ManagedAppRepository(
                context,
                database,
                jobs,
                repositoryDiscoveryClient = GitHubRepositoryDiscoveryClient(changedIdentity),
            )

            assertThrows(IllegalStateException::class.java) {
                runBlocking { repository.refreshSourceDiscovery(appId) }
            }

            val binding = requireNotNull(database.managedAppDao().getRepositoryBinding(appId))
            assertEquals("123456789012345678", binding.providerRepositoryId)
            val record = requireNotNull(database.managedAppDao().getRegisteredAppRecord(appId))
            assertEquals("FAILED", record.latestSourceDiscovery?.state)
            assertEquals("INVALID_METADATA", record.latestSourceDiscovery?.reason)
            assertEquals(2, rowCount(database, "source_discoveries"))
        } finally {
            database.close()
        }
    }

    @Test
    fun cancelledRefreshStoresCancelledAttemptAndKeepsPreviousSuccess() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, ReproDroidDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val jobs = JobRepository(context, database, RunnerApiClient(""))
            val appId = ManagedAppRepository(context, database, jobs).run {
                ensureSettings()
                registerRepository(
                    preview(),
                    ManagementMode.VERIFICATION,
                    InstallationSource.OFFICIAL_RELEASE,
                )
            }
            val requestEntered = CompletableDeferred<Unit>()
            val waitingGitHub = MockEngine {
                requestEntered.complete(Unit)
                awaitCancellation()
            }
            val repository = ManagedAppRepository(
                context,
                database,
                jobs,
                repositoryDiscoveryClient = GitHubRepositoryDiscoveryClient(waitingGitHub),
            )

            val refresh = launch(Dispatchers.Default) { repository.refreshSourceDiscovery(appId) }
            requestEntered.await()
            refresh.cancelAndJoin()

            val record = requireNotNull(database.managedAppDao().getRegisteredAppRecord(appId))
            assertEquals("CANCELLED", record.latestSourceDiscovery?.state)
            assertEquals(2, rowCount(database, "source_discoveries"))
            assertEquals(
                1,
                database.openHelper.readableDatabase.query(
                    "SELECT COUNT(*) FROM source_discoveries WHERE state = 'COMPLETE'",
                ).use { cursor ->
                    check(cursor.moveToFirst())
                    cursor.getInt(0)
                },
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun notModifiedReleaseRefreshConvergesWithoutDuplicateOrDownload() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, ReproDroidDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val commitSha = "1".repeat(40)
        val appId = "00000000-0000-4000-8000-000000000101"
        val snapshotId = "00000000-0000-4000-8000-000000000102"
        val assetId = "00000000-0000-4000-8000-000000000103"
        val observedAt = "2026-09-01T00:00:00Z"
        val provider = MockEngine { request ->
            when (request.url.encodedPath) {
                "/repos/example/project/releases/latest" -> respond("", HttpStatusCode.NotModified)
                else -> error("Unexpected request: ${request.url}")
            }
        }
        try {
            val observationSha = ReleaseObservationHasher.sha256(
                ReleaseObservationInput(
                    provider = "GITHUB",
                    instance = "github.com",
                    providerRepositoryId = TEST_PROVIDER_REPOSITORY_ID,
                    providerReleaseId = "100",
                    tagName = "1.0",
                    resolvedCommitSha = commitSha,
                    targetCommitishRaw = "main",
                    releaseName = "1.0",
                    releaseUrl = "https://github.com/example/project/releases/tag/1.0",
                    isDraft = false,
                    isPrerelease = false,
                    isImmutable = false,
                    releaseCreatedAt = "2026-09-01T00:00:00Z",
                    publishedAt = "2026-09-01T00:00:00Z",
                    providerAssetId = "200",
                    assetName = "project.apk",
                    stableAssetUrl = "https://github.com/example/project/releases/download/1.0/project.apk",
                    contentType = "application/vnd.android.package-archive",
                    providerSizeBytes = 1024,
                    providerDigestSha256 = "a".repeat(64),
                    selectionReason = "SINGLE_APK",
                ),
            )
            val dao = database.managedAppDao()
            dao.upsertRegisteredApp(
                RegisteredAppEntity(
                    registeredAppId = appId,
                    displayName = "project",
                    repositoryUrl = "https://github.com/example/project",
                    canonicalRepositoryUrl = "https://github.com/example/project",
                    provider = "PUBLIC_GITHUB_RELEASES",
                    managementMode = ManagementMode.VERIFICATION.name,
                    releaseMetadataEtag = "\"release-etag\"",
                    createdAt = observedAt,
                    updatedAt = observedAt,
                ),
            )
            dao.upsertRepositoryBinding(
                AppRepositoryBindingEntity(
                    registeredAppId = appId,
                    provider = "GITHUB",
                    instance = "github.com",
                    providerRepositoryId = TEST_PROVIDER_REPOSITORY_ID,
                    identityStatus = RepositoryIdentityStatus.VERIFIED.name,
                    registrationSlot = "PRIMARY",
                    verifiedAt = observedAt,
                ),
            )
            dao.upsertReleaseSnapshot(
                ReleaseSnapshotEntity(
                    releaseSnapshotId = snapshotId,
                    registeredAppId = appId,
                    providerReleaseId = "100",
                    tagName = "1.0",
                    resolvedCommitSha = commitSha,
                    releaseName = "1.0",
                    releaseUrl = "https://github.com/example/project/releases/tag/1.0",
                    targetCommitishRaw = "main",
                    isDraft = false,
                    isPrerelease = false,
                    isImmutable = false,
                    releaseCreatedAt = observedAt,
                    publishedAt = observedAt,
                    fetchedAt = observedAt,
                    observationSha256 = observationSha,
                    lastObservedAt = observedAt,
                    selectedProviderAssetId = "200",
                ),
            )
            dao.upsertReleaseAsset(
                ReleaseAssetEntity(
                    releaseAssetId = assetId,
                    releaseSnapshotId = snapshotId,
                    providerAssetId = "200",
                    assetName = "project.apk",
                    stableAssetUrl = "https://github.com/example/project/releases/download/1.0/project.apk",
                    selectionReason = "SINGLE_APK",
                    contentType = "application/vnd.android.package-archive",
                    providerSizeBytes = 1024,
                    providerDigestSha256 = "a".repeat(64),
                    downloadStatus = ReferenceDownloadStatus.NOT_DOWNLOADED.name,
                ),
            )
            val repository = ManagedAppRepository(
                context,
                database,
                JobRepository(context, database, RunnerApiClient("")),
                provider = GitHubReleasesClient(provider),
                repositoryDiscoveryClient = GitHubRepositoryDiscoveryClient(successfulRefreshDiscoveryEngine()),
            )

            repository.refresh(appId)

            assertEquals(1, rowCount(database, "release_snapshots"))
            assertEquals(1, rowCount(database, "release_assets"))
            assertEquals(snapshotId, dao.getReleaseSnapshotByObservationHash(appId, observationSha)?.releaseSnapshotId)
            assertTrue(requireNotNull(dao.getRegisteredApp(appId)).lastReleaseCheckedAt.orEmpty() > observedAt)
            assertEquals(ReferenceDownloadStatus.NOT_DOWNLOADED.name, dao.getReleaseAsset(assetId)?.downloadStatus)
        } finally {
            database.close()
        }
    }

    private fun preview() = RepositoryRegistrationPreview(
        normalizedInputUrl = "https://github.com/example/project",
        identity = GitHubRepositoryIdentity(
            repository = GitHubRepository("example", "project"),
            providerRepositoryId = "123456789012345678",
            displayName = "project",
            defaultBranch = "main",
        ),
        discovery = StaticDiscoveryResult(
            requestedBranch = "main",
            resolvedCommitSha = "1".repeat(40),
            rootTreeSha = "2".repeat(40),
            state = "COMPLETE",
            reason = null,
            entryCount = 1,
            requestCount = 4,
            receivedBytes = 1024,
            maxDepth = 0,
            excludedSymlinkCount = 0,
            excludedSubmoduleCount = 0,
            excludedCacheTreeCount = 0,
            candidates = listOf(
                StaticGradleCandidate(
                    relativePath = "build.gradle.kts",
                    buildRoot = ".",
                    fileKind = "build.gradle.kts",
                    blobSha = "3".repeat(40),
                    mode = "100644",
                    dsl = "KOTLIN",
                ),
            ),
        ),
    )

    private fun successfulRefreshDiscoveryEngine(): MockEngine {
        val commit = "d".repeat(40)
        val root = "e".repeat(40)
        val blob = "f".repeat(40)
        return MockEngine { request ->
            val body = when (request.url.encodedPath) {
                "/repos/example/project" -> """{
                    "id":$TEST_PROVIDER_REPOSITORY_ID,
                    "name":"project",
                    "full_name":"example/project",
                    "private":false,
                    "html_url":"https://github.com/example/project",
                    "default_branch":"main",
                    "owner":{"login":"example"}
                }""".trimIndent()
                "/repos/example/project/git/ref/heads/main" ->
                    """{"ref":"refs/heads/main","object":{"type":"commit","sha":"$commit"}}"""
                "/repos/example/project/git/commits/$commit" ->
                    """{"sha":"$commit","tree":{"type":"tree","sha":"$root"}}"""
                "/repos/example/project/git/trees/$root" ->
                    """{"sha":"$root","truncated":false,"tree":[
                        {"path":"build.gradle.kts","mode":"100644","type":"blob","sha":"$blob"}
                    ]}"""
                else -> error("Unexpected discovery request: ${request.url}")
            }
            respond(body, HttpStatusCode.OK, JSON_HEADERS)
        }
    }

    private companion object {
        const val TEST_PROVIDER_REPOSITORY_ID = "123456789012345678"
        val JSON_HEADERS = headersOf(HttpHeaders.ContentType, "application/json")
        const val AMBIGUOUS_RELEASE_JSON = """
            {
              "id":101,
              "tag_name":"v1",
              "target_commitish":"main",
              "name":"Version 1",
              "html_url":"https://github.com/example/project/releases/tag/v1",
              "draft":false,
              "prerelease":false,
              "immutable":false,
              "created_at":"2026-09-05T00:00:00Z",
              "published_at":"2026-09-05T00:00:00Z",
              "assets":[
                {
                  "id":200,
                  "name":"project-release.apk",
                  "state":"uploaded",
                  "content_type":"application/vnd.android.package-archive",
                  "size":1024,
                  "digest":"sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                  "browser_download_url":"https://github.com/example/project/releases/download/v1/project-release.apk"
                },
                {
                  "id":201,
                  "name":"project-alt.apk",
                  "state":"uploaded",
                  "content_type":"application/vnd.android.package-archive",
                  "size":1024,
                  "digest":"sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                  "browser_download_url":"https://github.com/example/project/releases/download/v1/project-alt.apk"
                }
              ]
            }
        """
    }

    private fun rowCount(database: ReproDroidDatabase, table: String): Int =
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { cursor ->
            check(cursor.moveToFirst())
            cursor.getInt(0)
        }
}
