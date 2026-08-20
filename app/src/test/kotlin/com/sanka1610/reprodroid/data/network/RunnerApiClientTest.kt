package com.sanka1610.reprodroid.data.network

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpMethod
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.fail
import org.junit.Test

class RunnerApiClientTest {
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
    }
}
