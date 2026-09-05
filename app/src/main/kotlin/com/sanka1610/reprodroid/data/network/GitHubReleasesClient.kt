package com.sanka1610.reprodroid.data.provider

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import com.sanka1610.reprodroid.data.local.PreferredAbi
import com.sanka1610.reprodroid.data.local.ReleaseVariantPreference

class GitHubProviderException(
    val statusCode: Int?,
    val code: String,
    override val message: String,
) : RuntimeException(message)

class GitHubReleasesClient(engine: HttpClientEngine? = null) {
    private val client = if (engine == null) HttpClient(Android) { configure() } else HttpClient(engine) { configure() }

    suspend fun resolveLatestRelease(
        repositoryUrl: String,
        previousEtag: String? = null,
        preferredAbi: PreferredAbi = PreferredAbi.ARM64_V8A,
        preferredVariant: ReleaseVariantPreference = ReleaseVariantPreference.RELEASE,
    ): ResolvedGitHubRelease {
        val repository = GitHubRepositoryParser.parse(repositoryUrl)
        val response = client.get(apiUrl(repository, "releases", "latest")) {
            githubHeaders()
            previousEtag?.let { header(HttpHeaders.IfNoneMatch, it) }
        }
        if (response.status == HttpStatusCode.NotModified) {
            throw GitHubProviderException(304, "NOT_MODIFIED", "The latest release metadata has not changed.")
        }
        val release = response.successBody<GitHubRelease>()
        if (release.draft || release.prerelease || release.publishedAt == null || release.tagName.isBlank()) {
            throw GitHubProviderException(
                response.status.value,
                "INVALID_LATEST_RELEASE",
                "GitHub returned a latest release that is not a published stable release.",
            )
        }
        val candidates = ReleaseAssetSelector.candidates(release.assets)
        val selectedAsset = try {
            ReleaseAssetSelector.selectValidatedCandidates(candidates, preferredAbi, preferredVariant)
        } catch (failure: ReleaseAssetSelectionException) {
            if (failure.code == "AMBIGUOUS_APK_ASSETS") null else throw failure
        }
        return ResolvedGitHubRelease(
            repository = repository,
            release = release,
            resolvedCommitSha = resolveTagCommit(repository, release.tagName),
            responseEtag = response.headers[HttpHeaders.ETag],
            candidates = candidates,
            selectedAsset = selectedAsset,
        )
    }

    private suspend fun resolveTagCommit(repository: GitHubRepository, tagName: String): String {
        val ref = client.get(apiUrl(repository, "git", "ref", "tags", tagName)) {
            githubHeaders()
        }.successBody<GitHubRefResponse>()
        var gitObject = ref.`object`
        repeat(MAX_TAG_PEEL_DEPTH) {
            when (gitObject.type) {
                "commit" -> return validateFullSha(gitObject.sha)
                "tag" -> {
                    gitObject = client.get(apiUrl(repository, "git", "tags", validateFullSha(gitObject.sha))) {
                        githubHeaders()
                    }.successBody<GitHubTagResponse>().`object`
                }
                else -> throw GitHubProviderException(
                    null,
                    "UNSUPPORTED_TAG_TARGET",
                    "The release tag does not ultimately reference a commit.",
                )
            }
        }
        throw GitHubProviderException(null, "TAG_PEEL_LIMIT", "The release tag nesting exceeds the safety limit.")
    }

    private fun validateFullSha(value: String): String {
        if (!FULL_SHA.matches(value)) {
            throw GitHubProviderException(null, "INVALID_GIT_OBJECT_SHA", "GitHub returned a non-full Git object SHA.")
        }
        return value.lowercase()
    }

    private fun apiUrl(repository: GitHubRepository, vararg segments: String): String =
        URLBuilder(API_ORIGIN).apply {
            appendPathSegments(listOf("repos", repository.owner, repository.name) + segments)
        }.buildString()

    private fun HttpRequestBuilder.githubHeaders() {
        header(HttpHeaders.Accept, GITHUB_JSON_MEDIA_TYPE)
        header(GITHUB_API_VERSION_HEADER, GITHUB_API_VERSION)
        header(HttpHeaders.UserAgent, USER_AGENT)
    }

    private fun io.ktor.client.HttpClientConfig<*>.configure() {
        expectSuccess = false
        followRedirects = false
        install(HttpTimeout) {
            connectTimeoutMillis = 5_000
            requestTimeoutMillis = 15_000
            socketTimeoutMillis = 15_000
        }
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; explicitNulls = false })
        }
    }

    private suspend inline fun <reified T> HttpResponse.successBody(): T {
        if (status.value in 200..299) return body()
        val apiError = runCatching { body<GitHubApiError>() }.getOrNull()
        val code = when (status.value) {
            403, 429 -> "GITHUB_RATE_LIMITED"
            404 -> "GITHUB_RESOURCE_NOT_FOUND"
            else -> "GITHUB_HTTP_${status.value}"
        }
        throw GitHubProviderException(status.value, code, apiError?.message ?: status.description)
    }

    private companion object {
        const val API_ORIGIN = "https://api.github.com"
        const val GITHUB_JSON_MEDIA_TYPE = "application/vnd.github+json"
        const val GITHUB_API_VERSION_HEADER = "X-GitHub-Api-Version"
        const val GITHUB_API_VERSION = "2022-11-28"
        const val USER_AGENT = "ReproDroid-Android/0.1"
        const val MAX_TAG_PEEL_DEPTH = 8
        val FULL_SHA = Regex("[0-9A-Fa-f]{40}")
    }
}
