package com.sanka1610.reprodroid.data.provider

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.fail
import org.junit.Test

class GitHubReleasesClientTest {
    @Test
    fun `repository parser accepts only canonical public GitHub repository URLs`() {
        assertEquals(
            "https://github.com/morpheapp/microg-re",
            GitHubRepositoryParser.parse("https://github.com/MorpheApp/MicroG-RE.git").canonicalUrl,
        )
        listOf(
            "http://github.com/MorpheApp/MicroG-RE",
            "https://user@github.com/MorpheApp/MicroG-RE",
            "https://github.com/MorpheApp/MicroG-RE/issues",
            "https://example.com/MorpheApp/MicroG-RE",
        ).forEach { invalid ->
            assertThrows(InvalidGitHubRepositoryException::class.java) {
                GitHubRepositoryParser.parse(invalid)
            }
        }
    }

    @Test
    fun `selector chooses the only APK`() {
        val selected = ReleaseAssetSelector.select(listOf(asset(1, "microg-6.1.4.apk")))
        assertEquals("SINGLE_APK", selected.reason)
        assertEquals("a".repeat(64), selected.providerSha256)
    }

    @Test
    fun `selector chooses exactly one arm64-v8a APK among multiple candidates`() {
        val selected = ReleaseAssetSelector.select(
            listOf(asset(1, "app-x86_64.apk"), asset(2, "app-arm64-v8a.apk")),
        )
        assertEquals(2, selected.asset.id)
        assertEquals("ARM64_V8A_FILENAME", selected.reason)
    }

    @Test
    fun `selector fails closed when multiple APKs have no unique arm64-v8a candidate`() {
        assertThrows(ReleaseAssetSelectionException::class.java) {
            ReleaseAssetSelector.select(listOf(asset(1, "app-universal.apk"), asset(2, "app-x86.apk")))
        }
        assertThrows(ReleaseAssetSelectionException::class.java) {
            ReleaseAssetSelector.select(listOf(asset(1, "one-arm64-v8a.apk"), asset(2, "two_arm64_v8a.apk")))
        }
    }

    @Test
    fun `latest release peels annotated tag to a full commit SHA`() = runBlocking {
        val tagObjectSha = "1".repeat(40)
        val commitSha = "D8DF10AB687A1C1CA05221634CFA46BAD262023A"
        val engine = MockEngine { request ->
            val response = when (request.url.encodedPath) {
                "/repos/MorpheApp/MicroG-RE/releases/latest" -> RELEASE_JSON
                "/repos/MorpheApp/MicroG-RE/git/ref/tags/6.1.4" ->
                    """{"ref":"refs/tags/6.1.4","object":{"type":"tag","sha":"$tagObjectSha","url":"unused"}}"""
                "/repos/MorpheApp/MicroG-RE/git/tags/$tagObjectSha" ->
                    """{"sha":"$tagObjectSha","object":{"type":"commit","sha":"$commitSha","url":"unused"}}"""
                else -> error("Unexpected request: ${request.url}")
            }
            respond(response, HttpStatusCode.OK, jsonHeaders)
        }

        val resolved = GitHubReleasesClient(engine).resolveLatestRelease(
            "https://github.com/MorpheApp/MicroG-RE",
        )

        assertEquals(commitSha.lowercase(), resolved.resolvedCommitSha)
        assertEquals(407554800, resolved.selectedAsset.asset.id)
    }

    @Test
    fun `conditional latest request exposes not modified without resolving tag`() = runBlocking {
        var requests = 0
        val engine = MockEngine { request ->
            requests++
            assertEquals("\"release-etag\"", request.headers[HttpHeaders.IfNoneMatch])
            respond(content = "", status = HttpStatusCode.NotModified)
        }

        try {
            GitHubReleasesClient(engine).resolveLatestRelease(
                "https://github.com/MorpheApp/MicroG-RE",
                previousEtag = "\"release-etag\"",
            )
            fail("GitHubProviderException was expected")
        } catch (failure: GitHubProviderException) {
            assertEquals("NOT_MODIFIED", failure.code)
        }
        assertEquals(1, requests)
    }

    private fun asset(id: Long, name: String) = GitHubReleaseAsset(
        id = id,
        name = name,
        state = "uploaded",
        contentType = "application/vnd.android.package-archive",
        size = 1024,
        digest = "sha256:${"a".repeat(64)}",
        browserDownloadUrl = "https://github.com/example/app/releases/download/1/$name",
    )

    private companion object {
        val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
        const val RELEASE_JSON = """
            {
              "id":314728907,
              "tag_name":"6.1.4",
              "target_commitish":"main",
              "name":"6.1.4",
              "html_url":"https://github.com/MorpheApp/MicroG-RE/releases/tag/6.1.4",
              "draft":false,
              "prerelease":false,
              "immutable":false,
              "created_at":"2026-05-20T00:00:00Z",
              "published_at":"2026-05-20T00:00:00Z",
              "assets":[{
                "id":407554800,
                "name":"microg-6.1.4.apk",
                "state":"uploaded",
                "content_type":"application/vnd.android.package-archive",
                "size":13393291,
                "digest":"sha256:907b0f1d64d4bdf2fc15df596129cdf9f140f5360f557d24ff2e987c9f586f15",
                "browser_download_url":"https://github.com/MorpheApp/MicroG-RE/releases/download/6.1.4/microg-6.1.4.apk"
              }]
            }
        """
    }
}
