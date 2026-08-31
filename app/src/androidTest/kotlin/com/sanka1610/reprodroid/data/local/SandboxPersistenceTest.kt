package com.sanka1610.reprodroid.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sanka1610.reprodroid.data.network.*
import com.sanka1610.reprodroid.data.repository.JobRepository
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SandboxPersistenceTest {
    private val json = Json { encodeDefaults = true; explicitNulls = false }
    private val remote = JobResponse("sandbox-fixture", ExecutionMode.REAL_TRUSTED, "https://github.com/example/app",
        RequestedRevision(RevisionType.TAG, "1.0"), "a".repeat(40), JobState.AWAITING_CONFIRMATION, 10, true,
        latestLogSequence = 0, artifacts = emptyList(), createdAt = "before", updatedAt = "before",
        sandbox = JobSandbox(BuildSandboxMode.DOCKER, SandboxOrigin.NEW_JOB, "docker-microg-v1", SandboxCleanupStatus.NOT_CREATED))

    @Test fun invalidRefreshPreservesSelectionAndCannotSendAcknowledgement() = runBlocking(Dispatchers.IO) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, ReproDroidDatabase::class.java).build()
        try {
            var response = json.encodeToString(remote)
            var posts = 0
            val engine = MockEngine { request ->
                if (request.method == HttpMethod.Post) posts++
                respond(if (request.url.encodedPath.endsWith("/logs")) json.encodeToString(LogResponse(emptyList(), 0, false)) else response,
                    HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
            val repository = JobRepository(context, database, RunnerApiClient("http://127.0.0.1:8080", engine))
            repository.syncJob(remote.jobId)
            val original = repository.getJob(remote.jobId)
            for (invalid in listOf(remote.copy(sandbox = null), remote.copy(sandbox = JobSandbox(BuildSandboxMode.HOST, SandboxOrigin.NEW_JOB)))) {
                response = json.encodeToString(invalid)
                assertTrue(runCatching { repository.syncJob(remote.jobId) }.isFailure)
                assertEquals(original, repository.getJob(remote.jobId))
                assertTrue(repository.sandboxWarnings.value.containsKey(remote.jobId))
                assertTrue(runCatching { repository.confirmRealBuild(remote.jobId, "a".repeat(40)) }.isFailure)
                assertEquals(0, posts)
            }
        } finally { database.close() }
    }

    @Test fun failedLogWriteRollsBackJobAndSandboxTogether() = runBlocking(Dispatchers.IO) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, ReproDroidDatabase::class.java).build()
        try {
            var response = remote
            var logs = LogResponse(emptyList(), 0, false)
            val engine = MockEngine { request ->
                respond(if (request.url.encodedPath.endsWith("/logs")) json.encodeToString(logs) else json.encodeToString(response),
                    HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
            val repository = JobRepository(context, database, RunnerApiClient("http://127.0.0.1:8080", engine))
            repository.syncJob(remote.jobId)
            val original = repository.getJob(remote.jobId)
            response = remote.copy(state = JobState.VERIFYING_WRAPPER, progressPercent = 45,
                latestLogSequence = 1, sandbox = remote.sandbox!!.copy(cleanupStatus = SandboxCleanupStatus.PENDING))
            logs = LogResponse(listOf(LogEntry(1, "now", LogLevel.INFO, "fixture")), 1, false)
            database.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_fixture_log BEFORE INSERT ON logs BEGIN SELECT RAISE(ABORT, 'fixture'); END")
            assertTrue(runCatching { repository.syncJob(remote.jobId) }.isFailure)
            assertEquals(original, repository.getJob(remote.jobId))
            assertTrue(repository.sandboxWarnings.value.containsKey(remote.jobId))
            database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_fixture_log")
            repository.syncJob(remote.jobId)
            assertEquals("PENDING", repository.getJob(remote.jobId)?.sandboxCleanupStatus)
            assertFalse(repository.sandboxWarnings.value.containsKey(remote.jobId))
        } finally { database.close() }
    }

    @Test fun manifestDependencyWriteFailureRetainsPreviousHeaderAndDependencies() = runBlocking(Dispatchers.IO) {
        manifestFixture { database, repository, job, setResponse ->
            val previous = requireNotNull(repository.getBuildEnvironmentManifest(job.jobId))
            val artifacts = repository.getArtifacts(job.jobId)
            val replacement = hostManifest().copy(java = PublicJavaRuntime("18.0.2.1", "Replacement vendor"),
                dependencies = listOf(PublicBuildDependency("replacement.jar", "e".repeat(64))))
            database.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER reject_manifest_dependency BEFORE INSERT ON build_environment_dependencies BEGIN SELECT RAISE(ABORT, 'fixture'); END")
            setResponse(replacement)
            repository.syncJob(job.jobId)
            assertEquals(previous, repository.getBuildEnvironmentManifest(job.jobId))
            assertEquals(artifacts, repository.getArtifacts(job.jobId))
            assertEquals("BUILD_MANIFEST_STORAGE_FAILED", repository.buildManifestWarnings.value[job.jobId]?.code)
            assertEquals("HOST", repository.getJob(job.jobId)?.sandboxMode)
            assertEquals(JobState.SUCCEEDED.name, repository.getJob(job.jobId)?.state)
            database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_manifest_dependency")
            repository.syncJob(job.jobId)
            val refreshed = requireNotNull(repository.getBuildEnvironmentManifest(job.jobId))
            assertEquals("Replacement vendor", refreshed.manifest.javaVendor)
            assertEquals(listOf("replacement.jar"), refreshed.dependencies.map { it.fileName })
            assertFalse(repository.buildManifestWarnings.value.containsKey(job.jobId))
        }
    }

    @Test fun legacyHostUpgradeRejectsLaterSchemaDowngradeEvenAfterRepositoryRestart() = runBlocking(Dispatchers.IO) {
        manifestFixture { _, repository, job, setResponse ->
            val previous = requireNotNull(repository.getBuildEnvironmentManifest(job.jobId))
            assertEquals(3, previous.manifest.schemaVersion)
            assertEquals("LEGACY_HOST", repository.getJob(job.jobId)?.sandboxOrigin)
            // Both older representations are otherwise valid for LEGACY_HOST. The stored v3 is the barrier.
            for (schema in listOf(1, 2)) {
                setResponse(hostManifest().copy(schemaVersion = schema, sandbox = null,
                    determinism = if (schema == 1) null else DeterminismOptions(noBuildCache = false)))
                repository.syncJob(job.jobId)
                assertEquals(previous, repository.getBuildEnvironmentManifest(job.jobId))
                assertEquals("BUILD_MANIFEST_RESPONSE_INVALID", repository.buildManifestWarnings.value[job.jobId]?.code)
            }
            setResponse(hostManifest())
            repository.syncJob(job.jobId)
            assertFalse(repository.buildManifestWarnings.value.containsKey(job.jobId))
        }
    }

    private suspend fun manifestFixture(
        verify: suspend (ReproDroidDatabase, JobRepository, JobResponse, (BuildEnvironmentManifestResponse) -> Unit) -> Unit,
    ) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, ReproDroidDatabase::class.java).build()
        try {
            val job = remote.copy(state = JobState.SUCCEEDED, progressPercent = 100, requiresConfirmation = false,
                effectiveBuild = EffectiveBuild("fixture", "release", ".", 18, listOf("assemble"),
                    determinism = DeterminismOptions(noBuildCache = false)),
                artifacts = listOf(ArtifactMetadata("fixture-apk", "fixture.apk", 1, "b".repeat(64), "com.example", "1", 1)),
                sandbox = JobSandbox(BuildSandboxMode.HOST, SandboxOrigin.LEGACY_HOST))
            var response = hostManifest()
            val engine = MockEngine { request ->
                val body = when {
                    request.url.encodedPath.endsWith("/build-environment-manifest") -> json.encodeToString(response)
                    request.url.encodedPath.endsWith("/logs") -> json.encodeToString(LogResponse(emptyList(), 0, false))
                    else -> json.encodeToString(job)
                }
                respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
            val client = RunnerApiClient("http://127.0.0.1:8080", engine)
            JobRepository(context, database, client).syncJob(job.jobId)
            // Recreate the repository: acceptance must use the persisted schema, not an in-memory seen flag.
            verify(database, JobRepository(context, database, client), job) { response = it }
        } finally { database.close() }
    }

    private fun hostManifest() = BuildEnvironmentManifestResponse(
        3, "a".repeat(40), PublicJavaRuntime("18.0.2.1", "Eclipse Adoptium"), "8.14.3", 36, "36.0.0",
        listOf(PublicBuildDependency("original.jar", "c".repeat(64))), "b".repeat(64),
        DeterminismOptions(noBuildCache = false), SandboxEvidence(BuildSandboxMode.HOST),
    )
}
