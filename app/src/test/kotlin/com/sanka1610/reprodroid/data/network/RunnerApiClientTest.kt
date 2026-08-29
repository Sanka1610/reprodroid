package com.sanka1610.reprodroid.data.network

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpMethod
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.fail
import org.junit.Test
import java.nio.file.Files

class RunnerApiClientTest {
    @Test
    fun `legacy effective build without dependency pinning defaults to none`() {
        val effectiveBuild = Json.decodeFromString<EffectiveBuild>(
            """{"buildRoot":".","tasks":["assembleRelease"]}""",
        )

        assertEquals(DependencyPinning.NONE, effectiveBuild.dependencyPinning)
        assertEquals(null, effectiveBuild.determinism)
    }

    @Test
    fun `effective build decodes strict determinism and rejects unknown locale`() {
        val effectiveBuild = Json.decodeFromString<EffectiveBuild>(
            """{"buildRoot":".","tasks":["assembleRelease"],"determinism":{"sourceDateEpoch":1777393787,"noBuildCache":true,"fixedLocale":"C.UTF-8"}}""",
        )

        assertEquals(1_777_393_787L, effectiveBuild.determinism?.sourceDateEpoch)
        assertEquals(true, effectiveBuild.determinism?.noBuildCache)
        assertEquals(FixedLocale.C_UTF_8, effectiveBuild.determinism?.fixedLocale)
        assertThrows(SerializationException::class.java) {
            Json.decodeFromString<EffectiveBuild>(
                """{"buildRoot":".","tasks":["assembleRelease"],"determinism":{"noBuildCache":false,"fixedLocale":"FUTURE"}}""",
            )
        }
    }

    @Test
    fun `unknown dependency pinning is rejected`() {
        assertThrows(SerializationException::class.java) {
            Json.decodeFromString<EffectiveBuild>(
                """{"buildRoot":".","tasks":["assembleRelease"],"dependencyPinning":"FUTURE_MODE"}""",
            )
        }
    }

    @Test
    fun `create job parses accepted response`() = runBlocking {
        val engine = MockEngine { request ->
            assertEquals("http://127.0.0.1:8080/v1/jobs", request.url.toString())
            respond(
                content = """{"jobId":"job-1","state":"CREATED"}""",
                status = HttpStatusCode.Accepted,
                headers = jsonHeaders,
            )
        }
        val client = RunnerApiClient("http://127.0.0.1:8080", engine)

        val created = client.createJob(
            CreateJobRequest(
                executionMode = ExecutionMode.SIMULATED,
                repositoryUrl = "https://github.com/example/app.git",
                revision = RequestedRevision(RevisionType.BRANCH, "main"),
                simulationOutcome = SimulationOutcome.SUCCESS,
            ),
        )

        assertEquals("job-1", created.jobId)
        assertEquals(JobState.CREATED, created.state)
    }

    @Test
    fun `error response exposes runner code`() = runBlocking {
        val engine = MockEngine {
            respond(
                content = """{"code":"JOB_NOT_FOUND","message":"missing"}""",
                status = HttpStatusCode.NotFound,
                headers = jsonHeaders,
            )
        }
        val client = RunnerApiClient("http://127.0.0.1:8080", engine)

        try {
            client.getJob("missing")
            fail("RunnerApiException was expected")
        } catch (failure: RunnerApiException) {
            assertEquals(404, failure.statusCode)
            assertEquals("JOB_NOT_FOUND", failure.errorCode)
        }
    }

    @Test
    fun `confirm real build posts to the commit confirmation endpoint`() = runBlocking {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("http://127.0.0.1:8080/v1/jobs/job-1/confirm", request.url.toString())
            respond(content = "", status = HttpStatusCode.NoContent)
        }
        val client = RunnerApiClient("http://127.0.0.1:8080", engine)

        client.confirmJob(
            "job-1",
            ConfirmJobRequest(
                resolvedCommitSha = "1".repeat(40),
                riskAcknowledged = true,
            ),
        )
    }

    @Test
    fun `artifact download streams bytes and exposes verification headers`() = runBlocking {
        val apkBytes = "apk-transfer-content".toByteArray()
        val expectedSha256 = "a".repeat(64)
        val engine = MockEngine { request ->
            assertEquals(
                "http://127.0.0.1:8080/v1/jobs/job-1/artifacts/artifact-1/content",
                request.url.toString(),
            )
            respond(
                content = apkBytes,
                status = HttpStatusCode.OK,
                headers = headersOf(
                    HttpHeaders.ContentType to listOf("application/vnd.android.package-archive"),
                    HttpHeaders.ContentLength to listOf(apkBytes.size.toString()),
                    HttpHeaders.ETag to listOf("\"$expectedSha256\""),
                ),
            )
        }
        val client = RunnerApiClient("http://127.0.0.1:8080", engine)
        val destination = Files.createTempFile("reprodroid-download-test", ".apk").toFile()
        try {
            val downloaded = client.downloadArtifact("job-1", "artifact-1", destination)

            assertEquals(apkBytes.size.toLong(), downloaded.bytesWritten)
            assertEquals(apkBytes.size.toLong(), downloaded.contentLength)
            assertEquals("\"$expectedSha256\"", downloaded.etag)
            assertEquals("application/vnd.android.package-archive", downloaded.contentType)
            assertArrayEquals(apkBytes, destination.readBytes())
        } finally {
            destination.delete()
        }
    }

    @Test
    fun `build manifest endpoint is bounded and parsed with strict public schema`() = runBlocking {
        val engine = MockEngine { request ->
            assertEquals(
                "http://127.0.0.1:8080/v1/jobs/job-1/build-environment-manifest",
                request.url.toString(),
            )
            respond(
                content = """
                    {
                      "schemaVersion":1,
                      "commit":"${"1".repeat(40)}",
                      "java":{"version":"21.0.1","vendor":"Example"},
                      "gradle":"8.14.3",
                      "androidSdk":36,
                      "buildTools":"36.0.0",
                      "dependencies":[{"fileName":"example.jar","sha256":"${"a".repeat(64)}"}],
                      "apkHash":"${"b".repeat(64)}"
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }
        val manifest = RunnerApiClient("http://127.0.0.1:8080", engine)
            .getBuildEnvironmentManifest("job-1")

        assertEquals(1, manifest.schemaVersion)
        assertEquals("example.jar", manifest.dependencies.single().fileName)
    }

    @Test
    fun `build manifest endpoint rejects declared response larger than 8 MiB`() = runBlocking {
        val engine = MockEngine {
            respond(
                content = "{}",
                status = HttpStatusCode.OK,
                headers = headersOf(
                    HttpHeaders.ContentType to listOf("application/json"),
                    HttpHeaders.ContentLength to listOf((8 * 1024 * 1024 + 1).toString()),
                ),
            )
        }
        assertThrows(RunnerResponseIntegrityException::class.java) {
            runBlocking {
                RunnerApiClient("http://127.0.0.1:8080", engine)
                    .getBuildEnvironmentManifest("job-1")
            }
        }
        Unit
    }

    @Test
    fun `source scan endpoint is bounded and parsed with strict public schema`() = runBlocking {
        val engine = MockEngine { request ->
            assertEquals("http://127.0.0.1:8080/v1/jobs/job-1/source-scan", request.url.toString())
            respond(
                content = sourceScanJson,
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }

        val scan = RunnerApiClient("http://127.0.0.1:8080", engine).getSourceScan("job-1")

        assertEquals(SourceScanDetectorId.PROCESS_EXEC_API, scan.findings.single().detectorId)
        assertEquals("a".repeat(64), scan.resultSha256)
    }

    @Test
    fun `source scan endpoint rejects unknown fields and responses larger than 4 MiB`() {
        val unknownFieldEngine = MockEngine {
            respond(
                content = sourceScanJson.dropLast(1) + ",\"futureField\":true}",
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }
        assertThrows(RunnerResponseIntegrityException::class.java) {
            runBlocking {
                RunnerApiClient("http://127.0.0.1:8080", unknownFieldEngine).getSourceScan("job-1")
            }
        }
        val oversizedEngine = MockEngine {
            respond(
                content = "{}",
                status = HttpStatusCode.OK,
                headers = headersOf(
                    HttpHeaders.ContentType to listOf("application/json"),
                    HttpHeaders.ContentLength to listOf((4 * 1024 * 1024 + 1).toString()),
                ),
            )
        }
        assertThrows(RunnerResponseIntegrityException::class.java) {
            runBlocking {
                RunnerApiClient("http://127.0.0.1:8080", oversizedEngine).getSourceScan("job-1")
            }
        }
    }

    @Test
    fun `source scan continuation posts digest bound acknowledgement`() = runBlocking {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals(
                "http://127.0.0.1:8080/v1/jobs/job-1/source-scan/continue",
                request.url.toString(),
            )
            respond(content = "", status = HttpStatusCode.NoContent)
        }

        RunnerApiClient("http://127.0.0.1:8080", engine).continueSourceScan(
            "job-1",
            ContinueSourceScanRequest("a".repeat(64), riskAcknowledged = true),
        )
    }

    @Test
    fun `base URL rejects paths and user information`() {
        assertThrows(RunnerConfigurationException::class.java) {
            RunnerApiClient("http://127.0.0.1:8080/v1")
        }
        assertThrows(RunnerConfigurationException::class.java) {
            RunnerApiClient("http://user@127.0.0.1:8080")
        }
    }

    companion object {
        private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
        private val sourceScanJson =
            """{"schemaVersion":1,"jobId":"job-1","resolvedCommitSha":"${"1".repeat(40)}","scannerVersion":"reprodroid-static-v1","resultSha256":"${"a".repeat(64)}","summary":{"scannedFiles":1,"scannedBytes":10,"skippedBinaryFiles":0,"skippedSymlinks":0,"findingCount":1},"detectorCounts":[{"detectorId":"PROCESS_EXEC_API","count":1}],"findings":[{"detectorId":"PROCESS_EXEC_API","displayPath":"build.gradle.kts","line":1,"column":1}]}"""
    }
}
