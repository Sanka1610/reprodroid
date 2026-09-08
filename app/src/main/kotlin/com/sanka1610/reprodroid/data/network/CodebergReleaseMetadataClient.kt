package com.sanka1610.reprodroid.data.provider

import com.sanka1610.reprodroid.data.local.ProviderRepresentationEntity
import com.sanka1610.reprodroid.data.local.ReleaseCheckChannel
import com.sanka1610.reprodroid.data.network.StrictJsonAuditor
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.time.DateTimeException
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class CodebergReleaseMetadataClient(engine: HttpClientEngine? = null) : ProviderReleaseMetadataClient {
    override val providerName: String = PROVIDER
    override val providerInstance: String = INSTANCE
    private val client = if (engine == null) HttpClient(Android) { configure() } else HttpClient(engine) { configure() }
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    override suspend fun check(
        repositoryUrl: String,
        providerRepositoryId: String,
        channel: ReleaseCheckChannel,
        cachedRepresentations: List<ProviderRepresentationEntity>,
        now: Instant,
    ): ProviderMetadataResult = try {
        withTimeout(APP_TIMEOUT_MILLIS) {
            val repository = CodebergRepositoryParser.parse(repositoryUrl)
            canonicalProviderId(providerRepositoryId)
            val budget = RequestBudget()
            val cache = cachedRepresentations.associateBy { it.endpointKey }
            val updated = linkedMapOf<String, ProviderRepresentationEntity>()
            var representationNotModified = false
            val releases = when (channel) {
                ReleaseCheckChannel.STABLE_ONLY -> {
                    val endpoint = endpointKey(repository, "releases/latest")
                    val fetched = fetchRepresentation(
                        repository,
                        listOf("releases", "latest"),
                        endpoint,
                        cache[endpoint],
                        budget,
                        now,
                    )
                    if (fetched.status == HttpStatusCode.NotFound) {
                        confirmPublicRepository(repository, providerRepositoryId, budget, now)
                        return@withTimeout ProviderMetadataResult.NoPublishedRelease(
                            requestCount = budget.requestCount,
                            receivedBytes = budget.receivedBytes,
                            representations = emptyList(),
                        )
                    }
                    val body = fetched.requireRepresentationBody()
                    representationNotModified = fetched.notModified
                    updated[endpoint] = representation(endpoint, providerRepositoryId, fetched, body, now)
                    listOf(decode<CodebergRelease>(body))
                }
                ReleaseCheckChannel.INCLUDE_PRERELEASE -> {
                    buildList {
                        var complete = false
                        for (page in 1..MAX_RELEASE_PAGES) {
                            val query = "limit=$RELEASES_PER_PAGE&page=$page"
                            val endpoint = endpointKey(repository, "releases?$query")
                            val fetched = fetchRepresentation(
                                repository = repository,
                                segments = listOf("releases"),
                                endpoint = endpoint,
                                cached = cache[endpoint],
                                budget = budget,
                                now = now,
                                query = mapOf(
                                    "limit" to RELEASES_PER_PAGE.toString(),
                                    "page" to page.toString(),
                                ),
                            )
                            val body = fetched.requireRepresentationBody()
                            representationNotModified = representationNotModified || fetched.notModified
                            updated[endpoint] = representation(endpoint, providerRepositoryId, fetched, body, now)
                            val pageReleases = decode<List<CodebergRelease>>(body)
                            check(pageReleases.size <= RELEASES_PER_PAGE) {
                                "Codeberg returned too many releases in one page."
                            }
                            addAll(pageReleases)
                            if (pageReleases.size < RELEASES_PER_PAGE) {
                                complete = true
                                break
                            }
                        }
                        check(complete) { "Codeberg release pagination exceeded the configured page bound." }
                    }
                }
            }
            val selected = releases
                .asSequence()
                .filter { release ->
                    !release.draft && (channel == ReleaseCheckChannel.INCLUDE_PRERELEASE || !release.prerelease)
                }
                .onEach(::validateRelease)
                .maxWithOrNull { left, right -> compareReleases(left, right) }
                ?: return@withTimeout ProviderMetadataResult.NoPublishedRelease(
                    requestCount = budget.requestCount,
                    receivedBytes = budget.receivedBytes,
                    representations = updated.values.toList(),
                )
            val resolvedSha = resolveTagCommit(repository, selected.tagName, budget, now)
            val candidates = try {
                ReleaseAssetSelector.providerCandidates(
                    assets = selected.assets,
                    stableHosts = setOf(INSTANCE),
                    isSupportedAttachment = { it.providerAssetType == "attachment" },
                    stableUrlPredicate = { asset -> isStableDownloadUrl(repository, selected, asset) },
                )
            } catch (failure: ReleaseAssetSelectionException) {
                if (failure.code == "NO_APK_ASSET") emptyList() else throw failure
            }
            ProviderMetadataResult.Release(
                resolved = ResolvedCodebergRelease(
                    repository = repository,
                    release = selected,
                    resolvedCommitSha = resolvedSha,
                    responseEtag = updated.values.lastOrNull()?.etag,
                    candidates = candidates,
                    selectedAsset = null,
                ),
                representationNotModified = representationNotModified,
                requestCount = budget.requestCount,
                receivedBytes = budget.receivedBytes,
                representations = updated.values.toList(),
            )
        }
    } catch (timeout: TimeoutCancellationException) {
        throw ReleaseMetadataException("NETWORK_ERROR", message = "Codeberg metadata check exceeded 45 seconds.", cause = timeout)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: ReleaseMetadataException) {
        throw failure
    } catch (failure: CodebergProviderException) {
        throw ReleaseMetadataException(
            code = normalizeFailureCode(failure.code),
            statusCode = failure.statusCode,
            message = failure.message,
            cause = failure,
        )
    } catch (failure: ReleaseAssetSelectionException) {
        throw ReleaseMetadataException("INVALID_METADATA", message = failure.message, cause = failure)
    } catch (failure: IllegalArgumentException) {
        throw ReleaseMetadataException("INVALID_METADATA", message = failure.message ?: "Codeberg metadata is invalid.", cause = failure)
    } catch (failure: IllegalStateException) {
        throw ReleaseMetadataException("LIMIT_EXCEEDED", message = failure.message ?: "Codeberg metadata exceeded a safety bound.", cause = failure)
    } catch (failure: Throwable) {
        throw ReleaseMetadataException("NETWORK_ERROR", message = failure.message ?: "Codeberg metadata request failed.", cause = failure)
    }

    private suspend fun fetchRepresentation(
        repository: CodebergRepository,
        segments: List<String>,
        endpoint: String,
        cached: ProviderRepresentationEntity?,
        budget: RequestBudget,
        now: Instant,
        query: Map<String, String> = emptyMap(),
    ): FetchResult {
        val conditional = request(repository, segments, query, cached?.etag, budget, now)
        if (conditional.status != HttpStatusCode.NotModified) return conditional
        if (cached != null && cached.responseBody.isNotEmpty()) {
            return conditional.copy(body = cached.responseBody, etag = conditional.etag ?: cached.etag, notModified = true)
        }
        val unconditional = request(repository, segments, query, null, budget, now)
        return unconditional.copy(notModified = true)
    }

    private suspend fun request(
        repository: CodebergRepository,
        segments: List<String>,
        query: Map<String, String>,
        etag: String?,
        budget: RequestBudget,
        now: Instant,
    ): FetchResult {
        budget.beforeRequest()
        val response = client.get(apiUrl(repository, segments, query)) {
            codebergHeaders()
            etag?.let { header(HttpHeaders.IfNoneMatch, it) }
        }
        val body = if (response.status == HttpStatusCode.NotModified) "" else response.boundedUtf8Body(budget)
        if (response.status.value !in 200..299 && response.status != HttpStatusCode.NotModified && response.status != HttpStatusCode.NotFound) {
            throw response.toFailure(body, now)
        }
        val rateLimit = codebergRateLimitEvidence(response.headers, now)
        if (response.status.value in 200..299 && rateLimit.exhausted) {
            throw ReleaseMetadataException(
                code = "PROVIDER_RATE_LIMITED",
                statusCode = response.status.value,
                retryNotBefore = rateLimit.retryNotBefore,
                rateLimitRemaining = rateLimit.remaining,
                rateLimitResetAt = rateLimit.retryNotBefore,
                message = "Codeberg reported that the provider request quota is exhausted.",
            )
        }
        return FetchResult(
            status = response.status,
            body = body,
            etag = response.headers[HttpHeaders.ETag],
            notModified = false,
        )
    }

    private suspend fun confirmPublicRepository(
        repository: CodebergRepository,
        expectedProviderRepositoryId: String,
        budget: RequestBudget,
        now: Instant,
    ) {
        budget.beforeRequest()
        val response = client.get(apiUrl(repository, emptyList(), emptyMap())) { codebergHeaders() }
        val body = response.boundedUtf8Body(budget)
        if (response.status.value in 200..299) {
            val identity = decode<CodebergApiRepositoryIdentity>(body)
            require(identity.id == expectedProviderRepositoryId) {
                "The public Codeberg repository identity no longer matches the registered repository."
            }
            return
        }
        throw response.toFailure(body, now)
    }

    private suspend fun resolveTagCommit(
        repository: CodebergRepository,
        tagName: String,
        budget: RequestBudget,
        now: Instant,
    ): String {
            val refs = getJson<List<CodebergMetadataClientRefResponse>>(repository, listOf("git", "refs", "tags", tagName), budget, now)
        val ref = refs.singleOrNull { it.ref == "refs/tags/$tagName" }
            ?: throw ReleaseMetadataException("INVALID_METADATA", message = "Codeberg returned zero or multiple matching tag refs.")
        var gitObject = ref.gitObject
        repeat(MAX_TAG_PEEL_DEPTH) {
            when (gitObject.type) {
                "commit" -> return validateFullSha(gitObject.sha)
                "tag" -> {
                    gitObject = getJson<CodebergMetadataClientTagResponse>(
                        repository,
                        listOf("git", "tags", validateFullSha(gitObject.sha)),
                        budget,
                        now,
                    ).gitObject
                }
                else -> throw ReleaseMetadataException(
                    "INVALID_METADATA",
                    message = "The Codeberg release tag does not ultimately reference a commit.",
                )
            }
        }
        throw ReleaseMetadataException("LIMIT_EXCEEDED", message = "The Codeberg release tag nesting exceeds 8 levels.")
    }

    private suspend inline fun <reified T> getJson(
        repository: CodebergRepository,
        segments: List<String>,
        budget: RequestBudget,
        now: Instant,
    ): T {
        val fetched = request(repository, segments, emptyMap(), null, budget, now)
        return decode(fetched.requireRepresentationBody())
    }

    private inline fun <reified T> decode(body: String): T {
        StrictJsonAuditor(body, maximumDepth = MAX_JSON_DEPTH, maximumStringBytes = MAX_STRING_BYTES).audit()
        return json.decodeFromString(body)
    }

    private fun validateRelease(release: CodebergRelease) {
        canonicalProviderId(release.id)
        require(release.tagName.isNotBlank() && release.tagName.length <= MAX_TAG_LENGTH) { "Release tag is invalid." }
        require(release.assets.size <= MAX_ASSETS) { "Release contains more than 256 assets." }
        Instant.parse(release.createdAt)
        release.publishedAt?.let(Instant::parse)
        release.assets.forEach { asset ->
            canonicalProviderId(asset.id)
            require(asset.name.length <= MAX_ASSET_NAME_LENGTH) { "Asset name is too long." }
            require(asset.size >= 0) { "Asset size is negative." }
            asset.providerCreatedAt?.let(Instant::parse)
        }
    }

    private fun isStableDownloadUrl(
        repository: CodebergRepository,
        release: CodebergRelease,
        asset: ProviderReleaseAsset,
    ): Boolean {
        return isExactCodebergReleaseDownloadUrl(repository, release.tagName, asset.name, asset.browserDownloadUrl)
    }

    private fun compareReleases(left: CodebergRelease, right: CodebergRelease): Int {
        val created = Instant.parse(left.createdAt).compareTo(Instant.parse(right.createdAt))
        if (created != 0) return created
        val length = left.id.length.compareTo(right.id.length)
        return if (length != 0) length else left.id.compareTo(right.id)
    }

    private fun representation(
        endpoint: String,
        providerRepositoryId: String,
        fetched: FetchResult,
        body: String,
        now: Instant,
    ) = ProviderRepresentationEntity(
        endpointKey = endpoint,
        provider = PROVIDER,
        instance = INSTANCE,
        providerRepositoryId = providerRepositoryId,
        etag = fetched.etag,
        responseBody = body,
        receivedAt = now.toString(),
    )

    private fun HttpResponse.toFailure(body: String, now: Instant): ReleaseMetadataException {
        val rateLimit = codebergRateLimitEvidence(headers, now)
        val retry = if (status.value == 429 || status.value == 503) {
            parseRetryAfter(headers[HttpHeaders.RetryAfter], now)
        } else {
            null
        }
        val rateLimited = status == HttpStatusCode.TooManyRequests ||
            (status == HttpStatusCode.Forbidden && rateLimit.exhausted)
        val code = when {
            rateLimited -> "PROVIDER_RATE_LIMITED"
            status == HttpStatusCode.Forbidden -> "ACCESS_DENIED"
            status == HttpStatusCode.NotFound -> "NOT_FOUND_OR_NOT_PUBLIC"
            status.value in 500..599 -> "PROVIDER_UNAVAILABLE"
            else -> "INVALID_METADATA"
        }
            val message = runCatching { decode<CodebergMetadataClientApiError>(body).message }.getOrNull() ?: status.description
        return ReleaseMetadataException(
            code = code,
            statusCode = status.value,
            retryNotBefore = if (rateLimited) retry ?: rateLimit.retryNotBefore else retry,
            rateLimitRemaining = rateLimit.remaining,
            rateLimitResetAt = rateLimit.retryNotBefore,
            message = message,
        )
    }

    private fun parseRetryAfter(value: String?, now: Instant): Instant? {
        if (value == null) return null
        value.toLongOrNull()?.let { seconds ->
            if (seconds >= 0) return runCatching { now.plusSeconds(seconds) }.getOrNull()
        }
        return try {
            ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
        } catch (_: DateTimeException) {
            null
        }
    }

    private suspend fun HttpResponse.boundedUtf8Body(budget: RequestBudget): String {
        headers[HttpHeaders.ContentEncoding]?.let { encoding ->
            require(encoding.equals("identity", ignoreCase = true)) { "Compressed Codeberg metadata is not accepted." }
        }
        headers[HttpHeaders.ContentLength]?.let { rawLength ->
            val length = rawLength.toLongOrNull()
                ?: throw IllegalArgumentException("Codeberg returned an invalid Content-Length.")
            require(length >= 0) { "Codeberg returned an invalid Content-Length." }
            check(length <= MAX_RESPONSE_BYTES) { "A Codeberg response exceeds 2 MiB." }
        }
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(RESPONSE_BUFFER_BYTES)
        val channel = bodyAsChannel()
        while (!channel.isClosedForRead) {
            val read = channel.readAvailable(buffer, 0, buffer.size)
            if (read == -1) break
            if (read == 0) continue
            check(output.size().toLong() + read <= MAX_RESPONSE_BYTES) { "A Codeberg response exceeds 2 MiB." }
            budget.recordBytes(read)
            output.write(buffer, 0, read)
        }
        return try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(output.toByteArray()))
                .toString()
        } catch (failure: Exception) {
            throw IllegalArgumentException("Codeberg returned invalid UTF-8 metadata.", failure)
        }
    }

    private fun validateFullSha(value: String): String {
        if (!FULL_SHA.matches(value)) {
            throw ReleaseMetadataException("INVALID_METADATA", message = "Codeberg returned a non-full SHA-1 Git object ID.")
        }
        return value.lowercase()
    }

    private fun normalizeFailureCode(code: String): String = when (code) {
        "CODEBERG_RATE_LIMITED", "RATE_LIMITED" -> "PROVIDER_RATE_LIMITED"
        "CODEBERG_RESOURCE_NOT_FOUND" -> "NOT_FOUND_OR_NOT_PUBLIC"
        "NETWORK_ERROR" -> "NETWORK_ERROR"
        else -> code
    }

    private fun endpointKey(repository: CodebergRepository, suffix: String): String =
        "$INSTANCE/${repository.owner.lowercase()}/${repository.name.lowercase()}/$suffix"

    private fun apiUrl(
        repository: CodebergRepository,
        segments: List<String>,
        query: Map<String, String>,
    ): String = URLBuilder(API_ORIGIN).apply {
        appendPathSegments("repos", repository.owner, repository.name)
        segments.forEach { appendPathSegments(it, encodeSlash = true) }
        query.toSortedMap().forEach { (key, value) -> parameters.append(key, value) }
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
    }

    private data class FetchResult(
        val status: HttpStatusCode,
        val body: String,
        val etag: String?,
        val notModified: Boolean,
    ) {
        fun requireRepresentationBody(): String {
            if (status.value !in 200..299 && status != HttpStatusCode.NotModified) {
                throw ReleaseMetadataException(
                    code = if (status == HttpStatusCode.NotFound) "NOT_FOUND_OR_NOT_PUBLIC" else "INVALID_METADATA",
                    statusCode = status.value,
                    message = status.description,
                )
            }
            require(body.isNotEmpty()) { "Codeberg returned an empty representation." }
            return body
        }
    }

    private class RequestBudget {
        var requestCount: Int = 0
            private set
        var receivedBytes: Long = 0
            private set

        fun beforeRequest() {
            check(requestCount < MAX_REQUESTS) { "Codeberg request count exceeds 12 per app." }
            requestCount++
        }

        fun recordBytes(bytes: Int) {
            receivedBytes += bytes.toLong()
            check(receivedBytes <= MAX_APP_BYTES) { "Codeberg responses exceed 4 MiB per app." }
        }
    }

    private companion object {
        const val PROVIDER = "CODEBERG"
        const val INSTANCE = "codeberg.org"
        const val API_ORIGIN = "https://codeberg.org/api/v1"
        const val USER_AGENT = "ReproDroid-Android/0.1"
        const val RELEASES_PER_PAGE = 20
        const val MAX_RELEASE_PAGES = 2
        const val MAX_ASSETS = 256
        const val MAX_REQUESTS = 12
        const val MAX_TAG_PEEL_DEPTH = 8
        const val MAX_TAG_LENGTH = 512
        const val MAX_ASSET_NAME_LENGTH = 1024
        const val MAX_JSON_DEPTH = 32
        const val MAX_STRING_BYTES = 64 * 1024
        const val RESPONSE_BUFFER_BYTES = 16 * 1024
        const val MAX_RESPONSE_BYTES = 2L * 1024L * 1024L
        const val MAX_APP_BYTES = 4L * 1024L * 1024L
        const val APP_TIMEOUT_MILLIS = 45_000L
        val FULL_SHA = Regex("[0-9A-Fa-f]{40}")
    }
}

@kotlinx.serialization.Serializable
private data class CodebergMetadataGitObject(val type: String, val sha: String)

@kotlinx.serialization.Serializable
private data class CodebergMetadataClientRefResponse(
    val ref: String,
    @kotlinx.serialization.SerialName("object") val gitObject: CodebergMetadataGitObject,
)

@kotlinx.serialization.Serializable
private data class CodebergMetadataClientTagResponse(
    @kotlinx.serialization.SerialName("object") val gitObject: CodebergMetadataGitObject,
)

@kotlinx.serialization.Serializable
private data class CodebergMetadataClientApiError(val message: String? = null)
