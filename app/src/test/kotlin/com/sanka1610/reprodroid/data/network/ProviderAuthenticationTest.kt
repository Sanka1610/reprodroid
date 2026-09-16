package com.sanka1610.reprodroid.data.provider

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.takeFrom
import com.sanka1610.reprodroid.data.local.PreferredAbi
import com.sanka1610.reprodroid.data.local.ReleaseCheckChannel
import com.sanka1610.reprodroid.data.local.ReleaseVariantPreference
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ProviderAuthenticationTest {
    @Test
    fun `github token is attached as bearer only to exact API origin`() {
        val authenticator = StoredProviderRequestAuthenticator(
            ProviderCredentialSource { provider -> if (provider == ProviderId.GITHUB) GITHUB_TEST_TOKEN else null },
        )
        val request = request("https://api.github.com/repos/example/project")

        authenticator.authenticate(ProviderId.GITHUB, request)

        assertEquals("Bearer $GITHUB_TEST_TOKEN", request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun `codeberg token is attached with token scheme only below API v1`() {
        val authenticator = StoredProviderRequestAuthenticator(
            ProviderCredentialSource { provider -> if (provider == ProviderId.CODEBERG) CODEBERG_TEST_TOKEN else null },
        )
        val request = request("https://codeberg.org/api/v1/repos/example/project")

        authenticator.authenticate(ProviderId.CODEBERG, request)

        assertEquals("token $CODEBERG_TEST_TOKEN", request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun `unconfigured provider preserves unauthenticated request`() {
        val request = request("https://api.github.com/repos/example/project")

        StoredProviderRequestAuthenticator(ProviderCredentialSource { null })
            .authenticate(ProviderId.GITHUB, request)

        assertNull(request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun `authentication refuses browser asset and non API origins`() {
        val authenticator = StoredProviderRequestAuthenticator(ProviderCredentialSource { GITHUB_TEST_TOKEN })

        listOf(
            ProviderId.GITHUB to "https://github.com/example/project",
            ProviderId.GITHUB to "https://release-assets.githubusercontent.com/example/file.apk",
            ProviderId.GITHUB to "http://api.github.com/repos/example/project",
            ProviderId.GITHUB to "https://api.github.com:444/repos/example/project",
            ProviderId.CODEBERG to "https://codeberg.org/example/project",
            ProviderId.CODEBERG to "https://codeberg.org/api/v2/repos/example/project",
        ).forEach { (provider, url) ->
            val request = request(url)
            assertThrows(ProviderCredentialException::class.java) {
                authenticator.authenticate(provider, request)
            }
            assertNull(request.headers[HttpHeaders.Authorization])
        }
    }

    @Test
    fun `token validation accepts opaque printable values and rejects header injection`() {
        validateProviderToken(GITHUB_TEST_TOKEN)
        validateProviderToken(CODEBERG_TEST_TOKEN)

        listOf("", " leading", "trailing ", "line\nbreak", "tab\tvalue", "non-ascii-\u3042").forEach { candidate ->
            assertThrows(ProviderCredentialException::class.java) { validateProviderToken(candidate) }
        }
        assertThrows(ProviderCredentialException::class.java) {
            validateProviderToken("x".repeat(MAX_PROVIDER_TOKEN_BYTES + 1))
        }
    }

    @Test
    fun `every provider API client delegates request authentication`() = runBlocking {
        val recorder = RecordingAuthenticator()

        runCatching {
            GitHubRepositoryDiscoveryClient(failingEngine(), authenticator = recorder)
                .preview("https://github.com/example/project")
        }
        runCatching {
            GitHubReleasesClient(failingEngine(), recorder).resolveLatestRelease(
                repositoryUrl = "https://github.com/example/project",
                previousEtag = null,
                preferredAbi = PreferredAbi.ARM64_V8A,
                preferredVariant = ReleaseVariantPreference.RELEASE,
            )
        }
        runCatching {
            GitHubReleaseMetadataClient(failingEngine(), recorder).check(
                repositoryUrl = "https://github.com/example/project",
                providerRepositoryId = "42",
                channel = ReleaseCheckChannel.STABLE_ONLY,
                cachedRepresentations = emptyList(),
                now = Instant.parse("2026-09-16T00:00:00Z"),
            )
        }
        runCatching {
            CodebergRepositoryDiscoveryClient(failingEngine(), authenticator = recorder)
                .preview("https://codeberg.org/example/project")
        }
        runCatching {
            CodebergReleasesClient(failingEngine(), recorder).resolveLatestRelease(
                repositoryUrl = "https://codeberg.org/example/project",
                previousEtag = null,
                preferredAbi = PreferredAbi.ARM64_V8A,
                preferredVariant = ReleaseVariantPreference.RELEASE,
            )
        }
        runCatching {
            CodebergReleaseMetadataClient(failingEngine(), recorder).check(
                repositoryUrl = "https://codeberg.org/example/project",
                providerRepositoryId = "42",
                channel = ReleaseCheckChannel.STABLE_ONLY,
                cachedRepresentations = emptyList(),
                now = Instant.parse("2026-09-16T00:00:00Z"),
            )
        }

        assertEquals(
            listOf(
                ProviderId.GITHUB,
                ProviderId.GITHUB,
                ProviderId.GITHUB,
                ProviderId.CODEBERG,
                ProviderId.CODEBERG,
                ProviderId.CODEBERG,
            ),
            recorder.providers,
        )
    }

    private fun request(url: String): HttpRequestBuilder = HttpRequestBuilder().apply {
        this.url.takeFrom(url)
    }

    private fun failingEngine() = MockEngine {
        respond(
            content = "{\"message\":\"synthetic rejection\"}",
            status = HttpStatusCode.Unauthorized,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )
    }

    private class RecordingAuthenticator : ProviderRequestAuthenticator {
        val providers = mutableListOf<ProviderId>()

        override fun authenticate(provider: ProviderId, request: HttpRequestBuilder) {
            providers += provider
        }
    }

    private companion object {
        const val GITHUB_TEST_TOKEN = "synthetic-github-test-token-never-send"
        const val CODEBERG_TEST_TOKEN = "synthetic-codeberg-test-token-never-send"
    }
}
