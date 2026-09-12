package com.sanka1610.reprodroid.data.provider

import com.sanka1610.reprodroid.data.local.ProviderRepresentationEntity
import com.sanka1610.reprodroid.data.local.ReleaseCheckChannel
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import java.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class GitHubReleaseMetadataClientTest {
    @Test
    fun `numeric provider IDs are decoded as canonical strings without precision loss`() {
        val release = Json.decodeFromString<GitHubRelease>(RELEASE_JSON)

        assertEquals("9007199254740993", release.id)
        assertEquals("9007199254740995", release.assets.single().id)
        assertEquals(
            "9007199254740993",
            canonicalProviderId("9007199254740993"),
        )
    }

    @Test
    fun `provider IDs reject quoted noncanonical and overlong values`() {
        val invalidBodies = listOf(
            RELEASE_JSON.replace("9007199254740993", "\"9007199254740993\"", ignoreCase = false),
            RELEASE_JSON.replace("9007199254740993", "0", ignoreCase = false),
            RELEASE_JSON.replace("9007199254740993", "09007199254740993", ignoreCase = false),
        )
        invalidBodies.forEach { body ->
            assertThrows(IllegalArgumentException::class.java) {
                Json.decodeFromString<GitHubRelease>(body)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            canonicalProviderId("1".repeat(41))
        }
    }

    @Test
    fun `provider ID serializer emits JSON integers while retaining the String domain`() {
        val release = GitHubRelease(
            id = "12345678901234567890",
            tagName = "v1",
            targetCommitish = "main",
            name = "Version 1",
            htmlUrl = "https://github.com/example/project/releases/tag/v1",
            draft = false,
            prerelease = false,
            immutable = true,
            createdAt = "2026-09-01T00:00:00Z",
            publishedAt = "2026-09-01T00:00:00Z",
            assets = listOf(
                GitHubReleaseAsset(
                    id = "12345678901234567891",
                    name = "project.apk",
                    state = "uploaded",
                    contentType = "application/vnd.android.package-archive",
                    size = 12,
                    digest = null,
                    browserDownloadUrl = "https://github.com/example/project/releases/download/v1/project.apk",
                ),
            ),
        )

        val encoded = Json.encodeToString(release)

        assertTrue(encoded.contains("\"id\":12345678901234567890"))
        assertTrue(encoded.contains("\"id\":12345678901234567891"))
        assertFalse(encoded.contains("\"id\":\"12345678901234567890\""))
    }

    @Test
    fun `stable check resolves commit and persists an audited representation`() = runBlocking {
        val commitSha = "A".repeat(40)
        val engine = MockEngine { request ->
            val body = when (request.url.encodedPath) {
                "/repos/example/project/releases/latest" -> STABLE_RELEASE_JSON
                "/repos/example/project/git/ref/tags/v1" ->
                    """{"ref":"refs/tags/v1","object":{"type":"commit","sha":"$commitSha","url":"unused"}}"""
                else -> error("Unexpected request: ${request.url}")
            }
            respond(body, HttpStatusCode.OK, JSON_HEADERS)
        }

        val result = GitHubReleaseMetadataClient(engine).check(
            repositoryUrl = "https://github.com/example/project",
            providerRepositoryId = "42",
            channel = ReleaseCheckChannel.STABLE_ONLY,
            cachedRepresentations = emptyList(),
            now = NOW,
        )
        val release = requireIsRelease(result)

        assertEquals(2, release.requestCount)
        assertEquals("10", release.resolved.release.id)
        assertEquals(commitSha.lowercase(), release.resolved.resolvedCommitSha)
        assertEquals(listOf("20"), release.resolved.candidates.map { it.asset.id })
        assertFalse(release.representationNotModified)
        assertEquals(1, release.representations.size)
        assertEquals("github.com/example/project/releases/latest", release.representations.single().endpointKey)
        assertNotNull(release.representations.single().responseBody)
    }

    @Test
    fun `include prerelease scans the paged release endpoint and selects a published prerelease`() = runBlocking {
        val commitSha = "b".repeat(40)
        var releasePageRequests = 0
        val engine = MockEngine { request ->
            val body = when (request.url.encodedPath) {
                "/repos/example/project/releases" -> {
                    releasePageRequests++
                    assertEquals("20", request.url.parameters["per_page"])
                    assertEquals("1", request.url.parameters["page"])
                    "[$PRERELEASE_JSON]"
                }
                "/repos/example/project/git/ref/tags/v2-preview" ->
                    """{"ref":"refs/tags/v2-preview","object":{"type":"commit","sha":"$commitSha","url":"unused"}}"""
                else -> error("Unexpected request: ${request.url}")
            }
            respond(body, HttpStatusCode.OK, JSON_HEADERS)
        }

        val result = GitHubReleaseMetadataClient(engine).check(
            repositoryUrl = "https://github.com/example/project",
            providerRepositoryId = "42",
            channel = ReleaseCheckChannel.INCLUDE_PRERELEASE,
            cachedRepresentations = emptyList(),
            now = NOW,
        )
        val release = requireIsRelease(result)

        assertEquals(1, releasePageRequests)
        assertEquals(2, release.requestCount)
        assertEquals("12", release.resolved.release.id)
        assertTrue(release.resolved.release.prerelease)
        assertEquals(listOf("20"), release.resolved.candidates.map { it.asset.id })
    }

    @Test
    fun `not modified response reuses cached body and sends the cached etag`() = runBlocking {
        val commitSha = "c".repeat(40)
        val endpoint = "github.com/example/project/releases/latest"
        var latestRequests = 0
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/repos/example/project/releases/latest" -> {
                    latestRequests++
                    assertEquals("\"release-etag\"", request.headers[HttpHeaders.IfNoneMatch])
                    respond(
                        content = "",
                        status = HttpStatusCode.NotModified,
                        headers = headersOf(HttpHeaders.ETag, "\"release-etag\""),
                    )
                }
                "/repos/example/project/git/ref/tags/v1" -> respond(
                    """{"ref":"refs/tags/v1","object":{"type":"commit","sha":"$commitSha","url":"unused"}}""",
                    HttpStatusCode.OK,
                    JSON_HEADERS,
                )
                else -> error("Unexpected request: ${request.url}")
            }
        }

        val result = GitHubReleaseMetadataClient(engine).check(
            repositoryUrl = "https://github.com/example/project",
            providerRepositoryId = "42",
            channel = ReleaseCheckChannel.STABLE_ONLY,
            cachedRepresentations = listOf(
                ProviderRepresentationEntity(
                    endpointKey = endpoint,
                    provider = "GITHUB",
                    instance = "github.com",
                    providerRepositoryId = "42",
                    etag = "\"release-etag\"",
                    responseBody = STABLE_RELEASE_JSON,
                    receivedAt = NOW.minusSeconds(60).toString(),
                ),
            ),
            now = NOW,
        )
        val release = requireIsRelease(result)

        assertEquals(1, latestRequests)
        assertTrue(release.representationNotModified)
        assertEquals(STABLE_RELEASE_JSON, release.representations.single().responseBody)
        assertEquals("\"release-etag\"", release.representations.single().etag)
    }

    @Test
    fun `not modified response still resolves a changed current tag SHA`() = runBlocking {
        val changedCommitSha = "e".repeat(40)
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/repos/example/project/releases/latest" -> {
                    assertEquals("\"release-etag\"", request.headers[HttpHeaders.IfNoneMatch])
                    respond(
                        content = "",
                        status = HttpStatusCode.NotModified,
                        headers = headersOf(HttpHeaders.ETag, "\"release-etag\""),
                    )
                }
                "/repos/example/project/git/ref/tags/v1" -> respond(
                    """{"ref":"refs/tags/v1","object":{"type":"commit","sha":"$changedCommitSha","url":"unused"}}""",
                    HttpStatusCode.OK,
                    JSON_HEADERS,
                )
                else -> error("Unexpected request: ${request.url}")
            }
        }

        val result = GitHubReleaseMetadataClient(engine).check(
            repositoryUrl = "https://github.com/example/project",
            providerRepositoryId = "42",
            channel = ReleaseCheckChannel.STABLE_ONLY,
            cachedRepresentations = listOf(
                ProviderRepresentationEntity(
                    endpointKey = "github.com/example/project/releases/latest",
                    provider = "GITHUB",
                    instance = "github.com",
                    providerRepositoryId = "42",
                    etag = "\"release-etag\"",
                    responseBody = STABLE_RELEASE_JSON,
                    receivedAt = NOW.minusSeconds(60).toString(),
                ),
            ),
            now = NOW,
        )
        val release = requireIsRelease(result)

        assertTrue(release.representationNotModified)
        assertEquals(changedCommitSha, release.resolved.resolvedCommitSha)
        assertEquals(2, release.requestCount)
    }

    @Test
    fun `not modified response without cached body performs one bounded refetch`() = runBlocking {
        val commitSha = "d".repeat(40)
        var latestRequests = 0
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/repos/example/project/releases/latest" -> {
                    latestRequests++
                    if (latestRequests == 1) {
                        assertEquals("\"missing-body\"", request.headers[HttpHeaders.IfNoneMatch])
                        respond("", HttpStatusCode.NotModified)
                    } else {
                        assertTrue(request.headers[HttpHeaders.IfNoneMatch] == null)
                        respond(STABLE_RELEASE_JSON, HttpStatusCode.OK, JSON_HEADERS)
                    }
                }
                "/repos/example/project/git/ref/tags/v1" -> respond(
                    """{"ref":"refs/tags/v1","object":{"type":"commit","sha":"$commitSha","url":"unused"}}""",
                    HttpStatusCode.OK,
                    JSON_HEADERS,
                )
                else -> error("Unexpected request: ${request.url}")
            }
        }

        val result = GitHubReleaseMetadataClient(engine).check(
            repositoryUrl = "https://github.com/example/project",
            providerRepositoryId = "42",
            channel = ReleaseCheckChannel.STABLE_ONLY,
            cachedRepresentations = listOf(
                ProviderRepresentationEntity(
                    endpointKey = "github.com/example/project/releases/latest",
                    provider = "GITHUB",
                    instance = "github.com",
                    providerRepositoryId = "42",
                    etag = "\"missing-body\"",
                    responseBody = "",
                    receivedAt = NOW.minusSeconds(60).toString(),
                ),
            ),
            now = NOW,
        )
        val release = requireIsRelease(result)

        assertEquals(2, latestRequests)
        assertEquals(3, release.requestCount)
        assertTrue(release.representationNotModified)
        assertEquals(STABLE_RELEASE_JSON, release.representations.single().responseBody)
    }

    @Test
    fun `not found latest release confirms public repository and reports no published release`() = runBlocking {
        var requests = 0
        val engine = MockEngine { request ->
            requests++
            when (request.url.encodedPath) {
                "/repos/example/project/releases/latest" -> respond("", HttpStatusCode.NotFound, JSON_HEADERS)
                "/repos/example/project" -> respond("""{"id":42}""", HttpStatusCode.OK, JSON_HEADERS)
                else -> error("Unexpected request: ${request.url}")
            }
        }

        val result = GitHubReleaseMetadataClient(engine).check(
            repositoryUrl = "https://github.com/example/project",
            providerRepositoryId = "42",
            channel = ReleaseCheckChannel.STABLE_ONLY,
            cachedRepresentations = emptyList(),
            now = NOW,
        )

        assertTrue(result is ProviderMetadataResult.NoPublishedRelease)
        assertEquals(2, result.requestCount)
        assertEquals(2, requests)
        assertTrue(result.representations.isEmpty())
    }

    @Test
    fun `rate limit response exposes retry after evidence`() = runBlocking {
        val engine = MockEngine {
            respond(
                """{"message":"API rate limit exceeded"}""",
                HttpStatusCode.TooManyRequests,
                headersOf(
                    HttpHeaders.ContentType to listOf("application/json"),
                    HttpHeaders.RetryAfter to listOf("60"),
                ),
            )
        }

        val failure = assertThrows(ReleaseMetadataException::class.java) {
            runBlocking {
                GitHubReleaseMetadataClient(engine).check(
                    repositoryUrl = "https://github.com/example/project",
                    providerRepositoryId = "42",
                    channel = ReleaseCheckChannel.STABLE_ONLY,
                    cachedRepresentations = emptyList(),
                    now = NOW,
                )
            }
        }

        assertEquals("PROVIDER_RATE_LIMITED", failure.code)
        assertEquals(429, failure.statusCode)
        assertEquals(NOW.plusSeconds(60), failure.retryNotBefore)
    }

    @Test
    fun `successful response with exhausted quota is converted to shared cooldown evidence`() = runBlocking {
        val reset = NOW.plusSeconds(120)
        val engine = MockEngine {
            respond(
                STABLE_RELEASE_JSON,
                HttpStatusCode.OK,
                headersOf(
                    HttpHeaders.ContentType to listOf("application/json"),
                    "X-RateLimit-Remaining" to listOf("0"),
                    "X-RateLimit-Reset" to listOf(reset.epochSecond.toString()),
                ),
            )
        }

        val failure = assertThrows(ReleaseMetadataException::class.java) {
            runBlocking {
                GitHubReleaseMetadataClient(engine).check(
                    repositoryUrl = "https://github.com/example/project",
                    providerRepositoryId = "42",
                    channel = ReleaseCheckChannel.STABLE_ONLY,
                    cachedRepresentations = emptyList(),
                    now = NOW,
                )
            }
        }

        assertEquals("PROVIDER_RATE_LIMITED", failure.code)
        assertEquals(200, failure.statusCode)
        assertEquals(0L, failure.rateLimitRemaining)
        assertEquals(reset, failure.retryNotBefore)
        assertEquals(reset, failure.rateLimitResetAt)
    }

    @Test
    fun `forbidden response without rate evidence remains access denied`() = runBlocking {
        val engine = MockEngine {
            respond(
                """{"message":"Repository access denied"}""",
                HttpStatusCode.Forbidden,
                JSON_HEADERS,
            )
        }

        val failure = assertThrows(ReleaseMetadataException::class.java) {
            runBlocking {
                GitHubReleaseMetadataClient(engine).check(
                    repositoryUrl = "https://github.com/example/project",
                    providerRepositoryId = "42",
                    channel = ReleaseCheckChannel.STABLE_ONLY,
                    cachedRepresentations = emptyList(),
                    now = NOW,
                )
            }
        }

        assertEquals("ACCESS_DENIED", failure.code)
        assertEquals(403, failure.statusCode)
        assertTrue(failure.retryNotBefore == null)
    }

    @Test
    fun `malformed release JSON fails closed as invalid metadata`() = runBlocking {
        val engine = MockEngine {
            respond("{\"id\":10,\"tag_name\":}", HttpStatusCode.OK, JSON_HEADERS)
        }

        val failure = assertThrows(ReleaseMetadataException::class.java) {
            runBlocking {
                GitHubReleaseMetadataClient(engine).check(
                    repositoryUrl = "https://github.com/example/project",
                    providerRepositoryId = "42",
                    channel = ReleaseCheckChannel.STABLE_ONLY,
                    cachedRepresentations = emptyList(),
                    now = NOW,
                )
            }
        }

        assertEquals("INVALID_METADATA", failure.code)
    }

    @Test
    fun `metadata body is bounded while streaming and exact limit is accepted`() = runBlocking {
        val atLimit = STABLE_RELEASE_JSON.padEnd(2 * 1024 * 1024, ' ').toByteArray()
        assertEquals(2 * 1024 * 1024, atLimit.size)
        val tagBody = """{"ref":"refs/tags/v1","object":{"type":"commit","sha":"${"a".repeat(40)}","url":"unused"}}"""
        val accepted = GitHubReleaseMetadataClient(
            MockEngine {
                when (it.url.encodedPath) {
                    "/repos/example/project/releases/latest" -> respond(
                        ByteReadChannel(atLimit),
                        HttpStatusCode.OK,
                        JSON_HEADERS,
                    )
                    "/repos/example/project/git/ref/tags/v1" -> respond(
                        tagBody,
                        HttpStatusCode.OK,
                        JSON_HEADERS,
                    )
                    else -> error("Unexpected request: ${it.url}")
                }
            },
        ).check(
            repositoryUrl = "https://github.com/example/project",
            providerRepositoryId = "42",
            channel = ReleaseCheckChannel.STABLE_ONLY,
            cachedRepresentations = emptyList(),
            now = NOW,
        )
        assertEquals(atLimit.size.toLong() + tagBody.toByteArray().size, accepted.receivedBytes)

        val overLimit = STABLE_RELEASE_JSON.padEnd(2 * 1024 * 1024 + 1, ' ').toByteArray()
        val failure = assertThrows(ReleaseMetadataException::class.java) {
            runBlocking {
                GitHubReleaseMetadataClient(
                    MockEngine {
                        respond(ByteReadChannel(overLimit), HttpStatusCode.OK, JSON_HEADERS)
                    },
                ).check(
                    repositoryUrl = "https://github.com/example/project",
                    providerRepositoryId = "42",
                    channel = ReleaseCheckChannel.STABLE_ONLY,
                    cachedRepresentations = emptyList(),
                    now = NOW,
                )
            }
        }
        assertEquals("LIMIT_EXCEEDED", failure.code)
    }

    @Test
    fun `invalid UTF-8 metadata fails closed`() {
        val failure = assertThrows(ReleaseMetadataException::class.java) {
            runBlocking {
                GitHubReleaseMetadataClient(
                    MockEngine {
                        respond(
                            ByteReadChannel(byteArrayOf(0xC3.toByte(), 0x28)),
                            HttpStatusCode.OK,
                            JSON_HEADERS,
                        )
                    },
                ).check(
                    repositoryUrl = "https://github.com/example/project",
                    providerRepositoryId = "42",
                    channel = ReleaseCheckChannel.STABLE_ONLY,
                    cachedRepresentations = emptyList(),
                    now = NOW,
                )
            }
        }

        assertEquals("INVALID_METADATA", failure.code)
    }

    private fun requireIsRelease(result: ProviderMetadataResult): ProviderMetadataResult.Release {
        assertTrue("Expected release result, got $result", result is ProviderMetadataResult.Release)
        return result as ProviderMetadataResult.Release
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-09-06T00:00:00Z")
        val JSON_HEADERS = headersOf(HttpHeaders.ContentType, "application/json")

        const val STABLE_RELEASE_JSON = """
            {
              "id":10,
              "tag_name":"v1",
              "target_commitish":"main",
              "name":"Version 1",
              "html_url":"https://github.com/example/project/releases/tag/v1",
              "draft":false,
              "prerelease":false,
              "immutable":true,
              "created_at":"2026-09-01T00:00:00Z",
              "published_at":"2026-09-01T00:00:00Z",
              "assets":[{
                "id":20,
                "name":"project.apk",
                "state":"uploaded",
                "content_type":"application/vnd.android.package-archive",
                "size":1024,
                "digest":"sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "browser_download_url":"https://github.com/example/project/releases/download/v1/project.apk"
              }]
            }
        """

        const val PRERELEASE_JSON = """
            {
              "id":12,
              "tag_name":"v2-preview",
              "target_commitish":"main",
              "name":"Version 2 Preview",
              "html_url":"https://github.com/example/project/releases/tag/v2-preview",
              "draft":false,
              "prerelease":true,
              "immutable":false,
              "created_at":"2026-09-02T00:00:00Z",
              "published_at":"2026-09-02T00:00:00Z",
              "assets":[{
                "id":20,
                "name":"project-preview.apk",
                "state":"uploaded",
                "content_type":"application/vnd.android.package-archive",
                "size":2048,
                "digest":"sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                "browser_download_url":"https://github.com/example/project/releases/download/v2-preview/project-preview.apk"
              }]
            }
        """

        const val RELEASE_JSON = """
            {
              "id":9007199254740993,
              "tag_name":"v1",
              "target_commitish":"main",
              "name":"Version 1",
              "html_url":"https://github.com/example/project/releases/tag/v1",
              "draft":false,
              "prerelease":false,
              "immutable":true,
              "created_at":"2026-09-01T00:00:00Z",
              "published_at":"2026-09-01T00:00:00Z",
              "assets":[{
                "id":9007199254740995,
                "name":"project.apk",
                "state":"uploaded",
                "content_type":"application/vnd.android.package-archive",
                "size":1024,
                "digest":"sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "browser_download_url":"https://github.com/example/project/releases/download/v1/project.apk"
              }]
            }
        """
    }
}
