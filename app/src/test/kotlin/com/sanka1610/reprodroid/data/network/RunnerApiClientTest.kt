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
    @Test fun `additive outer Job fields stay compatible but unknown sandbox fields fail`() = runBlocking {
        var sandbox = """{"mode":"HOST","origin":"NEW_JOB"}"""
        val engine = MockEngine {
            respond("""{"jobId":"job","executionMode":"REAL_TRUSTED","repositoryUrl":"https://github.com/example/app",
                "requestedRevision":{"type":"TAG","value":"1.0"},"state":"CREATED","progressPercent":0,
                "requiresConfirmation":false,"latestLogSequence":0,"artifacts":[],"createdAt":"now","updatedAt":"now",
                "futureAdditiveField":true,"sandbox":$sandbox}""", HttpStatusCode.OK, jsonHeaders)
        }
        val client = RunnerApiClient("http://127.0.0.1:8080", engine)
        assertEquals(BuildSandboxMode.HOST, client.getJob("job").sandbox?.mode)
        sandbox = """{"mode":"HOST","origin":"NEW_JOB","futureSandboxField":true}"""
        org.junit.Assert.assertTrue(runCatching { client.getJob("job") }.exceptionOrNull() is RunnerResponseIntegrityException)
    }

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

    @Test
    fun `v2 capabilities and storage summary are contract checked`() = runBlocking {
        var requestNumber = 0
        val runnerId = "00000000-0000-4000-8000-000000000001"
        val engine = MockEngine { request ->
            requestNumber++
            when (requestNumber) {
                1 -> {
                    assertEquals("/v2/capabilities", request.url.encodedPath)
                    respond(
                        """{"apiVersion":"v2","foundationContractVersion":1,"runnerId":"$runnerId","runnerVersion":"0.1.0-alpha01","capabilities":[{"id":"foundation","contractVersion":1},{"id":"storage-retention","contractVersion":1}]}""",
                        HttpStatusCode.OK,
                        jsonHeaders,
                    )
                }
                else -> {
                    assertEquals("/v2/storage/summary", request.url.encodedPath)
                    respond(
                        """{"schemaVersion":1,"runnerId":"$runnerId","areas":[{"area":"RUNNER_JOB","budgetBytes":"68719476736","usedBytes":"1","reservedBytes":"0","unclassifiedBytes":"0","usableBytes":"100","warningPercent":80,"state":"OK","measurementState":"COMPLETE","measuredAt":"2026-09-02T00:00:00Z"},{"area":"RUNNER_TOOLCHAIN","budgetBytes":"34359738368","usedBytes":"0","reservedBytes":"0","unclassifiedBytes":"0","usableBytes":"100","warningPercent":80,"state":"OK","measurementState":"COMPLETE","measuredAt":"2026-09-02T00:00:00Z"}]}""",
                        HttpStatusCode.OK,
                        jsonHeaders,
                    )
                }
            }
        }
        val client = RunnerApiClient("http://127.0.0.1:8080", engine, allowDevelopmentV2 = true)

        assertEquals(runnerId, client.getV2Capabilities().runnerId)
        assertEquals("1", client.getV2StorageSummary().areas.first().usedBytes)
    }

    @Test
    fun `v2 hold mutation sends contract and stable idempotency headers`() = runBlocking {
        val key = "00000000-0000-4000-8000-000000000010"
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/v2/retention/holds", request.url.encodedPath)
            assertEquals("storage-retention@1", request.headers["X-ReproDroid-Contract"])
            assertEquals(key, request.headers["Idempotency-Key"])
            respond(
                """{"operationId":"00000000-0000-4000-8000-000000000020","state":"COMPLETED","kind":"retention-hold-create","requestSha256":"${"a".repeat(64)}","result":{"type":"RETENTION_HOLD","resourceId":"00000000-0000-4000-8000-000000000030"},"reason":null,"createdAt":"2026-09-02T00:00:00Z","updatedAt":"2026-09-02T00:00:00Z"}""",
                HttpStatusCode.Accepted,
                jsonHeaders,
            )
        }
        val response = RunnerApiClient("http://127.0.0.1:8080", engine, allowDevelopmentV2 = true).createV2RetentionHold(
            V2RetentionHoldRequest(
                V2ResourceRequest("ARTIFACT", "00000000-0000-4000-8000-000000000001"),
                "CURRENT_COMPARISON",
                V2ClientReferenceRequest("COMPARISON", "00000000-0000-4000-8000-000000000002"),
            ),
            key,
        )

        assertEquals("RETENTION_HOLD", response.result?.type)
    }

    @Test
    fun `toolchain cancel sends contract headers without a synthetic JSON body`() = runBlocking {
        val installationId = "00000000-0000-4000-8000-000000000041"
        val operationId = "00000000-0000-4000-8000-000000000042"
        val runnerId = "00000000-0000-4000-8000-000000000043"
        val key = "00000000-0000-4000-8000-000000000044"
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/v2/toolchains/installations/$installationId:cancel", request.url.encodedPath)
            assertEquals("toolchain-install@1", request.headers["X-ReproDroid-Contract"])
            assertEquals(key, request.headers["Idempotency-Key"])
            assertEquals(null, request.headers[HttpHeaders.ContentType])
            assertEquals(0L, request.body.contentLength)
            respond(
                """{"schemaVersion":1,"installationId":"$installationId","operationId":"$operationId","runnerId":"$runnerId","planSha256":"${"a".repeat(64)}","catalogSha256":"${"b".repeat(64)}","state":"CANCEL_REQUESTED","progressPercent":42,"items":[{"artifactId":"gradle-8.14.3","component":"GRADLE","version":"8.14.3","state":"CANCEL_REQUESTED","downloadedBytes":"12"}],"reason":null,"createdAt":"2026-09-05T00:00:00Z","updatedAt":"2026-09-05T00:00:01Z"}""",
                HttpStatusCode.Accepted,
                jsonHeaders,
            )
        }

        val response = RunnerApiClient("http://127.0.0.1:8080", engine, allowDevelopmentV2 = true)
            .cancelToolchainInstallation(installationId, key)

        assertEquals(ToolchainInstallationState.CANCEL_REQUESTED, response.state)
    }

    @Test
    fun `v2 response rejects duplicate and unknown fields`() {
        listOf(
            """{"apiVersion":"v2","apiVersion":"v2","foundationContractVersion":1,"runnerId":"00000000-0000-4000-8000-000000000001","runnerVersion":"x","capabilities":[]}""",
            """{"apiVersion":"v2","foundationContractVersion":1,"runnerId":"00000000-0000-4000-8000-000000000001","runnerVersion":"x","capabilities":[],"future":true}""",
        ).forEach { body ->
            val engine = MockEngine { respond(body, HttpStatusCode.OK, jsonHeaders) }
            assertThrows(RunnerResponseIntegrityException::class.java) {
                runBlocking {
                    RunnerApiClient("http://127.0.0.1:8080", engine, allowDevelopmentV2 = true)
                        .getV2Capabilities()
                }
            }
        }
    }

    @Test
    fun `v2 is disabled without the loopback development gate`() {
        val engine = MockEngine { respond("{}", HttpStatusCode.OK, jsonHeaders) }
        assertThrows(RunnerConfigurationException::class.java) {
            runBlocking { RunnerApiClient("http://127.0.0.1:8080", engine).getV2Capabilities() }
        }
        assertThrows(RunnerConfigurationException::class.java) {
            runBlocking {
                RunnerApiClient("http://192.0.2.1:8080", engine, allowDevelopmentV2 = true)
                    .getV2Capabilities()
            }
        }
    }

    @Test
    fun `v2 does not follow redirects`() {
        var requests = 0
        val engine = MockEngine {
            requests++
            respond(
                "",
                HttpStatusCode.Found,
                headersOf(HttpHeaders.Location, "http://127.0.0.1:8080/v2/capabilities-redirected"),
            )
        }
        assertThrows(RunnerApiException::class.java) {
            runBlocking {
                RunnerApiClient("http://127.0.0.1:8080", engine, allowDevelopmentV2 = true)
                    .getV2Capabilities()
            }
        }
        assertEquals(1, requests)
    }

    companion object {
        private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
        private val sourceScanJson =
            """{"schemaVersion":1,"jobId":"job-1","resolvedCommitSha":"${"1".repeat(40)}","scannerVersion":"reprodroid-static-v1","resultSha256":"${"a".repeat(64)}","summary":{"scannedFiles":1,"scannedBytes":10,"skippedBinaryFiles":0,"skippedSymlinks":0,"findingCount":1},"detectorCounts":[{"detectorId":"PROCESS_EXEC_API","count":1}],"findings":[{"detectorId":"PROCESS_EXEC_API","displayPath":"build.gradle.kts","line":1,"column":1}]}"""
    }
}
