package com.sanka1610.reprodroid.data.provider

import com.sanka1610.reprodroid.data.local.PreferredAbi
import com.sanka1610.reprodroid.data.local.ReleaseVariantPreference
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI
import java.time.Instant

class CodebergReleasesClient(engine: HttpClientEngine? = null) : ProviderReleaseClient {
    override val providerName: String = PROVIDER
    override val providerInstance: String = INSTANCE
    private val client = if (engine == null) HttpClient(Android) { configure() } else HttpClient(engine) { configure() }

    override suspend fun resolveLatestRelease(
        repositoryUrl: String,
        previousEtag: String?,
        preferredAbi: PreferredAbi,
        preferredVariant: ReleaseVariantPreference,
    ): ResolvedCodebergRelease {
        val repository = CodebergRepositoryParser.parse(repositoryUrl)
        val response = client.get(apiUrl(repository, "releases", "latest")) {
            codebergHeaders()
            previousEtag?.let { header(HttpHeaders.IfNoneMatch, it) }
        }
        if (response.status == HttpStatusCode.NotModified) {
            throw CodebergProviderException(304, "NOT_MODIFIED", "The latest Codeberg release metadata has not changed.")
        }
        val release = response.successBody<CodebergRelease>()
        validateLatestRelease(release)
        val candidates = ReleaseAssetSelector.providerCandidates(
            assets = release.assets,
            stableHosts = setOf(INSTANCE),
            isSupportedAttachment = { it.providerAssetType == "attachment" },
            stableUrlPredicate = { asset -> isStableDownloadUrl(repository, release, asset) },
        )
        val selectedAsset = candidates.singleOrNull()?.let {
            ProviderSelectedReleaseAsset(it.asset, com.sanka1610.reprodroid.data.local.AssetSelectionReason.SINGLE_APK.name, it.providerSha256)
        }
        return ResolvedCodebergRelease(
            repository = repository,
            release = release,
            resolvedCommitSha = resolveTagCommit(repository, release.tagName),
            responseEtag = response.headers[HttpHeaders.ETag],
            candidates = candidates,
            selectedAsset = selectedAsset,
        )
    }

    /** Convenience entry point for callers that use the default release policy. */
    suspend fun resolveLatestRelease(repositoryUrl: String): ResolvedCodebergRelease =
        resolveLatestRelease(
            repositoryUrl = repositoryUrl,
            previousEtag = null,
            preferredAbi = PreferredAbi.ARM64_V8A,
            preferredVariant = ReleaseVariantPreference.RELEASE,
        )

    private suspend fun resolveTagCommit(repository: CodebergRepository, tagName: String): String {
        val refs = client.get(apiUrl(repository, "git", "refs", "tags", tagName)) {
            codebergHeaders()
        }.successBody<List<CodebergReleaseClientRefResponse>>()
        val ref = refs.singleOrNull { it.ref == "refs/tags/$tagName" }
            ?: throw CodebergProviderException(null, "INVALID_TAG_REF", "Codeberg returned zero or multiple matching tag refs.")
        var gitObject = ref.gitObject
        repeat(MAX_TAG_PEEL_DEPTH) {
            when (gitObject.type) {
                "commit" -> return validateFullSha(gitObject.sha)
                "tag" -> {
                    gitObject = client.get(apiUrl(repository, "git", "tags", validateFullSha(gitObject.sha))) {
                        codebergHeaders()
                    }.successBody<CodebergReleaseClientTagResponse>().gitObject
                }
                else -> throw CodebergProviderException(
                    null,
                    "UNSUPPORTED_TAG_TARGET",
                    "The Codeberg release tag does not ultimately reference a commit.",
                )
            }
        }
        throw CodebergProviderException(null, "TAG_PEEL_LIMIT", "The Codeberg release tag nesting exceeds the safety limit.")
    }

    private fun validateLatestRelease(release: CodebergRelease) {
        canonicalProviderId(release.id)
        if (release.draft || release.prerelease || release.tagName.isBlank()) {
            throw CodebergProviderException(
                null,
                "INVALID_LATEST_RELEASE",
                "Codeberg returned a latest release that is not a stable release.",
            )
        }
        validateRelease(release)
    }

    internal fun validateRelease(release: CodebergRelease) {
        canonicalProviderId(release.id)
        require(release.tagName.isNotBlank() && release.tagName.length <= MAX_TAG_LENGTH) { "Release tag is invalid." }
        require(release.assets.size <= MAX_ASSETS) { "Release contains more than 256 assets." }
        java.time.Instant.parse(release.createdAt)
        release.publishedAt?.let(java.time.Instant::parse)
        release.assets.forEach { asset ->
            canonicalProviderId(asset.id)
            require(asset.name.length <= MAX_ASSET_NAME_LENGTH) { "Asset name is too long." }
            require(asset.size >= 0) { "Asset size is negative." }
            require(asset.providerCreatedAt == null || runCatching { java.time.Instant.parse(asset.providerCreatedAt) }.isSuccess) {
                "Asset created_at is invalid."
            }
        }
    }

    private fun isStableDownloadUrl(
        repository: CodebergRepository,
        release: CodebergRelease,
        asset: ProviderReleaseAsset,
    ): Boolean {
        return isExactCodebergReleaseDownloadUrl(repository, release.tagName, asset.name, asset.browserDownloadUrl)
    }

    private fun validateFullSha(value: String): String {
        if (!FULL_SHA.matches(value)) {
            throw CodebergProviderException(null, "INVALID_GIT_OBJECT_SHA", "Codeberg returned a non-full SHA-1 Git object ID.")
        }
        return value.lowercase()
    }

    private fun apiUrl(repository: CodebergRepository, vararg segments: String): String =
        URLBuilder(API_ORIGIN).apply {
            appendPathSegments("repos", repository.owner, repository.name)
            segments.forEach { appendPathSegments(it, encodeSlash = true) }
        }.buildString()

    private fun HttpRequestBuilder.codebergHeaders() {
        header(HttpHeaders.Accept, "application/json")
        header(HttpHeaders.AcceptEncoding, "identity")
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
        val rateLimit = codebergRateLimitEvidence(headers, Instant.now())
        if (status.value in 200..299) {
            if (rateLimit.exhausted) {
                throw CodebergProviderException(
                    status.value,
                    "CODEBERG_RATE_LIMITED",
                    "Codeberg reported that the provider request quota is exhausted.",
                )
            }
            return body()
        }
        val apiError = runCatching { body<CodebergReleaseClientApiError>() }.getOrNull()
        val code = when {
            status.value == 404 -> "CODEBERG_RESOURCE_NOT_FOUND"
            status.value == 429 || (status.value == 403 && rateLimit.exhausted) ->
                "CODEBERG_RATE_LIMITED"
            status.value == 403 -> "CODEBERG_ACCESS_DENIED"
            status.value in 500..599 -> "CODEBERG_PROVIDER_UNAVAILABLE"
            else -> "CODEBERG_HTTP_${status.value}"
        }
        throw CodebergProviderException(status.value, code, apiError?.message ?: status.description)
    }

    private companion object {
        const val PROVIDER = "CODEBERG"
        const val INSTANCE = "codeberg.org"
        const val API_ORIGIN = "https://codeberg.org/api/v1"
        const val USER_AGENT = "ReproDroid-Android/0.1"
        const val MAX_TAG_PEEL_DEPTH = 8
        const val MAX_TAG_LENGTH = 512
        const val MAX_ASSET_NAME_LENGTH = 1024
        const val MAX_ASSETS = 256
        val FULL_SHA = Regex("[0-9A-Fa-f]{40}")
    }
}

@Serializable
private data class CodebergReleaseClientApiError(val message: String? = null)

@Serializable
private data class CodebergReleaseClientRefResponse(
    val ref: String,
    @kotlinx.serialization.SerialName("object") val gitObject: CodebergReleaseClientGitObject,
)

@Serializable
private data class CodebergReleaseClientTagResponse(
    @kotlinx.serialization.SerialName("object") val gitObject: CodebergReleaseClientGitObject,
)

@Serializable
private data class CodebergReleaseClientGitObject(val type: String, val sha: String)
