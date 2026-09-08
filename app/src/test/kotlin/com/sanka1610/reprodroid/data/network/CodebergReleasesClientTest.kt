package com.sanka1610.reprodroid.data.provider

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class CodebergReleasesClientTest {
    @Test
    fun `parser canonicalizes public Codeberg repository and rejects locator escapes`() {
        assertEquals(
            "https://codeberg.org/example/project",
            CodebergRepositoryParser.parse("https://codeberg.org/Example/Project.git").canonicalUrl,
        )
        listOf(
            "http://codeberg.org/example/project",
            "https://user@codeberg.org/example/project",
            "https://codeberg.org/example/project/issues",
            "https://codeberg.org/example/project?ref=main",
            "https://github.com/example/project",
        ).forEach { invalid ->
            assertThrows(InvalidCodebergRepositoryException::class.java) {
                CodebergRepositoryParser.parse(invalid)
            }
        }
    }

    @Test
    fun `release download URL normalizes only repository identity casing`() {
        val repository = CodebergRepository("unifiedpush", "android-example")

        assertTrue(
            isExactCodebergReleaseDownloadUrl(
                repository,
                "Release-V1",
                "Example-Main.apk",
                "https://codeberg.org/UnifiedPush/Android-Example/releases/download/Release-V1/Example-Main.apk",
            ),
        )
        listOf(
            "https://codeberg.org/other/android-example/releases/download/Release-V1/Example-Main.apk",
            "https://codeberg.org/UnifiedPush/other/releases/download/Release-V1/Example-Main.apk",
            "https://codeberg.org/UnifiedPush/Android-Example/Releases/download/Release-V1/Example-Main.apk",
            "https://codeberg.org/UnifiedPush/Android-Example/releases/Download/Release-V1/Example-Main.apk",
            "https://codeberg.org/UnifiedPush/Android-Example/releases/download/release-v1/Example-Main.apk",
            "https://codeberg.org/UnifiedPush/Android-Example/releases/download/Release-V1/example-main.apk",
            "https://codeberg.org/UnifiedPush/Android-Example/releases/download/Release-V1/Example-Main.apk?download=1",
        ).forEach { unsafe ->
            assertFalse(unsafe, isExactCodebergReleaseDownloadUrl(repository, "Release-V1", "Example-Main.apk", unsafe))
        }
    }

    @Test
    fun `latest release uses plural tag refs and requires exactly one matching ref`() = runBlocking {
        val commit = "c".repeat(40)
        val requests = mutableListOf<String>()
        val engine = MockEngine { request ->
            requests += request.url.encodedPath
            val body = when (request.url.encodedPath) {
                "/api/v1/repos/example/project/releases/latest" -> releaseJson()
                "/api/v1/repos/example/project/git/refs/tags/v1" ->
                    """[{"ref":"refs/tags/v1","object":{"type":"commit","sha":"$commit"}}]"""
                else -> error("Unexpected request: ${request.url}")
            }
            respond(body, HttpStatusCode.OK, JSON_HEADERS)
        }

        val resolved = CodebergReleasesClient(engine).resolveLatestRelease("https://codeberg.org/example/project")

        assertEquals(commit, resolved.resolvedCommitSha)
        assertEquals("10", resolved.release.id)
        assertEquals("1", resolved.selectedAsset?.asset?.id)
        assertEquals(listOf("1"), resolved.candidates.map { it.asset.id })
        assertEquals(
            listOf(
                "/api/v1/repos/example/project/releases/latest",
                "/api/v1/repos/example/project/git/refs/tags/v1",
            ),
            requests,
        )
    }

    @Test
    fun `tag ref ambiguity fails closed`() = runBlocking {
        val engine = MockEngine { request ->
            val body = when (request.url.encodedPath) {
                "/api/v1/repos/example/project/releases/latest" -> releaseJson()
                "/api/v1/repos/example/project/git/refs/tags/v1" ->
                    """[{"ref":"refs/tags/v1","object":{"type":"commit","sha":"${"c".repeat(40)}"}},
                        {"ref":"refs/tags/v1","object":{"type":"commit","sha":"${"d".repeat(40)}"}}]"""
                else -> error("Unexpected request: ${request.url}")
            }
            respond(body, HttpStatusCode.OK, JSON_HEADERS)
        }

        val failure = assertThrows(CodebergProviderException::class.java) {
            runBlocking { CodebergReleasesClient(engine).resolveLatestRelease("https://codeberg.org/example/project") }
        }
        assertEquals("INVALID_TAG_REF", failure.code)
    }

    @Test
    fun `bare 403 is access denied while valid exhausted Codeberg quota is rate limited`() = runBlocking {
        val accessDeniedEngine = MockEngine {
            respond("{\"message\":\"forbidden\"}", HttpStatusCode.Forbidden, JSON_HEADERS)
        }
        val accessDenied = assertThrows(CodebergProviderException::class.java) {
            runBlocking {
                CodebergReleasesClient(accessDeniedEngine)
                    .resolveLatestRelease("https://codeberg.org/example/project")
            }
        }
        assertEquals("CODEBERG_ACCESS_DENIED", accessDenied.code)

        val rateLimitedEngine = MockEngine {
            respond(
                "{\"message\":\"quota\"}",
                HttpStatusCode.Forbidden,
                headersOf(
                    HttpHeaders.ContentType to listOf("application/json"),
                    "ratelimit-policy" to listOf("\"api\";q=100;w=60"),
                    "ratelimit" to listOf("\"api\";r=0;t=17"),
                ),
            )
        }
        val rateLimited = assertThrows(CodebergProviderException::class.java) {
            runBlocking {
                CodebergReleasesClient(rateLimitedEngine)
                    .resolveLatestRelease("https://codeberg.org/example/project")
            }
        }
        assertEquals("CODEBERG_RATE_LIMITED", rateLimited.code)
    }

    private fun releaseJson(): String = """
        {
          "id":10,
          "tag_name":"v1",
          "target_commitish":"main",
          "name":"Version 1",
          "html_url":"https://codeberg.org/example/project/releases/tag/v1",
          "draft":false,
          "prerelease":false,
          "created_at":"2026-09-01T00:00:00Z",
          "published_at":null,
          "assets":[{
            "id":1,
            "name":"project.apk",
            "size":1024,
            "created_at":"2026-09-01T00:00:01Z",
            "browser_download_url":"https://codeberg.org/example/project/releases/download/v1/project.apk",
            "content_type":"application/vnd.android.package-archive",
            "digest":"sha256:${"a".repeat(64)}",
            "type":"attachment"
          }]
        }
    """.trimIndent()

    private companion object {
        val JSON_HEADERS = headersOf(HttpHeaders.ContentType, "application/json")
    }
}
