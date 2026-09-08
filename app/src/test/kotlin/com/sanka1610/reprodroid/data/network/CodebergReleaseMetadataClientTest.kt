package com.sanka1610.reprodroid.data.provider

import com.sanka1610.reprodroid.data.local.ReleaseCheckChannel
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant

class CodebergReleaseMetadataClientTest {
    @Test
    fun `403 requires valid Codeberg quota exhaustion and 503 remains provider unavailable`() {
        val now = Instant.parse("2026-09-08T00:00:00Z")
        val accessDenied = assertThrows(ReleaseMetadataException::class.java) {
            runBlocking {
                CodebergReleaseMetadataClient(failingEngine(HttpStatusCode.Forbidden))
                    .check(
                        repositoryUrl = REPOSITORY,
                        providerRepositoryId = "1",
                        channel = ReleaseCheckChannel.STABLE_ONLY,
                        cachedRepresentations = emptyList(),
                        now = now,
                    )
            }
        }
        assertEquals("ACCESS_DENIED", accessDenied.code)

        val quota = assertThrows(ReleaseMetadataException::class.java) {
            runBlocking {
                CodebergReleaseMetadataClient(
                    failingEngine(
                        HttpStatusCode.Forbidden,
                        headersOf(
                            HttpHeaders.ContentType to listOf("application/json"),
                            "ratelimit-policy" to listOf("\"api\";q=100;w=60"),
                            "ratelimit" to listOf("\"api\";r=0;t=19"),
                        ),
                    ),
                ).check(
                    repositoryUrl = REPOSITORY,
                    providerRepositoryId = "1",
                    channel = ReleaseCheckChannel.STABLE_ONLY,
                    cachedRepresentations = emptyList(),
                    now = now,
                )
            }
        }
        assertEquals("PROVIDER_RATE_LIMITED", quota.code)
        assertNotNull(quota.retryNotBefore)
        assertEquals(Instant.parse("2026-09-08T00:00:19Z"), quota.retryNotBefore)

        val explicitRetry = assertThrows(ReleaseMetadataException::class.java) {
            runBlocking {
                CodebergReleaseMetadataClient(
                    failingEngine(
                        HttpStatusCode.TooManyRequests,
                        headersOf(
                            HttpHeaders.ContentType to listOf("application/json"),
                            HttpHeaders.RetryAfter to listOf("31"),
                            "ratelimit-policy" to listOf("\"api\";q=100;w=60"),
                            "ratelimit" to listOf("\"api\";r=0;t=19"),
                        ),
                    ),
                ).check(REPOSITORY, "1", ReleaseCheckChannel.STABLE_ONLY, emptyList(), now)
            }
        }
        assertEquals("PROVIDER_RATE_LIMITED", explicitRetry.code)
        assertEquals(Instant.parse("2026-09-08T00:00:31Z"), explicitRetry.retryNotBefore)

        val unavailable = assertThrows(ReleaseMetadataException::class.java) {
            runBlocking {
                CodebergReleaseMetadataClient(
                    failingEngine(
                        HttpStatusCode.ServiceUnavailable,
                        headersOf(
                            HttpHeaders.ContentType to listOf("application/json"),
                            HttpHeaders.RetryAfter to listOf("23"),
                        ),
                    ),
                ).check(
                    repositoryUrl = REPOSITORY,
                    providerRepositoryId = "1",
                    channel = ReleaseCheckChannel.STABLE_ONLY,
                    cachedRepresentations = emptyList(),
                    now = now,
                )
            }
        }
        assertEquals("PROVIDER_UNAVAILABLE", unavailable.code)
        assertEquals(Instant.parse("2026-09-08T00:00:23Z"), unavailable.retryNotBefore)
    }

    private fun failingEngine(
        status: HttpStatusCode,
        headers: io.ktor.http.Headers = headersOf(HttpHeaders.ContentType, "application/json"),
    ) = MockEngine {
        respond("{\"message\":\"failure\"}", status, headers)
    }

    private companion object {
        const val REPOSITORY = "https://codeberg.org/example/project"
    }
}
