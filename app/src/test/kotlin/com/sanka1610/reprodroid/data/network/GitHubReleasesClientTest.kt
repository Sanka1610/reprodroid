package com.sanka1610.reprodroid.data.provider

import com.sanka1610.reprodroid.data.local.PreferredAbi
import com.sanka1610.reprodroid.data.local.ReleaseVariantPreference
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        assertEquals("PREFERRED_ABI_FILENAME", selected.reason)
    }

    @Test
    fun `selector uses per-app ABI and variant preferences when ABI alone is ambiguous`() {
        val selected = ReleaseAssetSelector.select(
            assets = listOf(
                asset(1, "app-release-arm64-v8a.apk"),
                asset(2, "app-preview-arm64-v8a.apk"),
                asset(3, "app-release-armeabi-v7a.apk"),
            ),
            preferredAbi = PreferredAbi.ARM64_V8A,
            preferredVariant = ReleaseVariantPreference.PREVIEW,
        )
        assertEquals(2, selected.asset.id)
        assertEquals("PREFERRED_ABI_AND_VARIANT_FILENAME", selected.reason)
    }

    @Test
    fun `selector supports armeabi-v7a preference`() {
        val selected = ReleaseAssetSelector.select(
            assets = listOf(
                asset(1, "app-release-arm64-v8a.apk"),
                asset(2, "app-release-armeabi-v7a.apk"),
            ),
            preferredAbi = PreferredAbi.ARMEABI_V7A,
        )
        assertEquals(2, selected.asset.id)
    }

    @Test
    fun `selector does not silently use a conflicting explicit variant`() {
        val failure = assertThrows(ReleaseAssetSelectionException::class.java) {
            ReleaseAssetSelector.select(
                assets = listOf(
                    asset(1, "app-preview-arm64-v8a.apk"),
                    asset(2, "app-release-x86_64.apk"),
                ),
                preferredAbi = PreferredAbi.ARM64_V8A,
                preferredVariant = ReleaseVariantPreference.RELEASE,
            )
        }
        assertEquals("AMBIGUOUS_APK_ASSETS", failure.code)
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
        assertEquals(407554800, requireNotNull(resolved.selectedAsset).asset.id)
    }

    @Test
    fun `latest release preserves ambiguous APK candidates for explicit selection`() = runBlocking {
        val commitSha = "c".repeat(40)
        val engine = MockEngine { request ->
            val response = when (request.url.encodedPath) {
                "/repos/example/project/releases/latest" -> AMBIGUOUS_RELEASE_JSON
                "/repos/example/project/git/ref/tags/v1" ->
                    """{"ref":"refs/tags/v1","object":{"type":"commit","sha":"$commitSha","url":"unused"}}"""
                else -> error("Unexpected request: ${request.url}")
            }
            respond(response, HttpStatusCode.OK, jsonHeaders)
        }

        val resolved = GitHubReleasesClient(engine).resolveLatestRelease("https://github.com/example/project")

        assertNull(resolved.selectedAsset)
        assertEquals(listOf(10L, 11L), resolved.candidates.map { it.asset.id })
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
        const val AMBIGUOUS_RELEASE_JSON = """
            {
              "id":10,
              "tag_name":"v1",
              "target_commitish":"main",
              "name":"Version 1",
              "html_url":"https://github.com/example/project/releases/tag/v1",
              "draft":false,
              "prerelease":false,
              "immutable":false,
              "created_at":"2026-09-01T00:00:00Z",
              "published_at":"2026-09-01T00:00:00Z",
              "assets":[
                {
                  "id":10,
                  "name":"project-release.apk",
                  "state":"uploaded",
                  "content_type":"application/vnd.android.package-archive",
                  "size":1024,
                  "digest":"sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                  "browser_download_url":"https://github.com/example/project/releases/download/v1/project-release.apk"
                },
                {
                  "id":11,
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
}
