package com.sanka1610.reprodroid.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sanka1610.reprodroid.data.local.InstallationSource
import com.sanka1610.reprodroid.data.local.ManagementMode
import com.sanka1610.reprodroid.data.local.ReproDroidDatabase
import com.sanka1610.reprodroid.data.network.RunnerApiClient
import com.sanka1610.reprodroid.data.network.SimulationOutcome
import com.sanka1610.reprodroid.data.network.RevisionType
import com.sanka1610.reprodroid.data.provider.GitHubProviderException
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
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RegistrationPersistenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

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

    private fun rowCount(database: ReproDroidDatabase, table: String): Int =
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { cursor ->
            check(cursor.moveToFirst())
            cursor.getInt(0)
        }
}
