package com.sanka1610.reprodroid.data.provider

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
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.ArrayDeque
import java.time.Instant

class CodebergRepositoryDiscoveryClient(
    engine: HttpClientEngine? = null,
    private val nanoTime: () -> Long = System::nanoTime,
) : ProviderRepositoryDiscoveryClient {
    override val providerName: String = PROVIDER
    override val providerInstance: String = INSTANCE
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val client = if (engine == null) HttpClient(Android) { configure() } else HttpClient(engine) { configure() }

    override suspend fun preview(repositoryUrl: String): RepositoryRegistrationPreview {
        val repository = CodebergRepositoryParser.parse(repositoryUrl)
        val budget = RequestBudget(nanoTime())
        val metadata = request<CodebergDiscoveryRepositoryMetadata>(apiUrl(repository), budget)
        validateMetadata(repository, metadata)
        val identity = CodebergRepositoryIdentity(
            repository = CodebergRepository(metadata.owner.login, metadata.name),
            providerRepositoryId = metadata.id.toString(),
            displayName = metadata.name,
            defaultBranch = validateBranch(metadata.defaultBranch),
        )
        val discovery = discover(identity, budget)
        return RepositoryRegistrationPreview(
            normalizedInputUrl = identity.repository.canonicalUrl,
            identity = identity,
            discovery = discovery,
        )
    }

    private suspend fun discover(
        identity: CodebergRepositoryIdentity,
        budget: RequestBudget,
    ): StaticDiscoveryResult {
        var commitSha: String? = null
        var rootTreeSha: String? = null
        val candidates = linkedMapOf<String, StaticGradleCandidate>()
        val stats = DiscoveryStats()
        var state = "COMPLETE"
        var reason: String? = null
        var diagnostic: String? = null
        try {
            val refs = request<List<CodebergDiscoveryRefResponse>>(
                apiUrl(identity.repository, "git", "refs", "heads", identity.defaultBranch),
                budget,
            )
            val ref = refs.singleOrNull { it.ref == "refs/heads/${identity.defaultBranch}" }
                ?: invalid("Codeberg returned zero or multiple matching default branch refs.")
            if (ref.gitObject.type != "commit") invalid("The Codeberg default branch ref does not target a commit.")
            commitSha = fullSha(ref.gitObject.sha)
            val commit = request<CodebergDiscoveryCommitResponse>(
                apiUrl(identity.repository, "git", "commits", commitSha),
                budget,
            )
            if (fullSha(commit.sha) != commitSha) invalid("The Codeberg commit response SHA does not match the request.")
            rootTreeSha = fullSha(commit.treeSha())
            walkTrees(identity.repository, rootTreeSha, budget, stats, candidates)
        } catch (limit: CodebergDiscoveryLimitException) {
            state = "INCOMPLETE"
            reason = limit.code
        } catch (failure: CodebergProviderException) {
            state = "FAILED"
            reason = failure.code
            diagnostic = failure.message
        } catch (failure: CodebergDiscoveryMetadataException) {
            state = "FAILED"
            reason = "INVALID_METADATA"
            diagnostic = failure.message
        }
        return StaticDiscoveryResult(
            requestedBranch = identity.defaultBranch,
            resolvedCommitSha = commitSha,
            rootTreeSha = rootTreeSha,
            state = state,
            reason = reason,
            diagnostic = diagnostic,
            entryCount = stats.entries,
            requestCount = budget.requests,
            receivedBytes = budget.bytes,
            maxDepth = stats.maxDepth,
            excludedSymlinkCount = stats.symlinks,
            excludedSubmoduleCount = stats.submodules,
            excludedCacheTreeCount = stats.cacheTrees,
            candidates = candidates.values.sortedBy { it.relativePath },
        )
    }

    private suspend fun walkTrees(
        repository: CodebergRepository,
        rootTreeSha: String,
        budget: RequestBudget,
        stats: DiscoveryStats,
        candidates: MutableMap<String, StaticGradleCandidate>,
    ) {
        val queue = ArrayDeque<TreeTask>()
        queue.add(TreeTask(".", rootTreeSha, 0, setOf(rootTreeSha)))
        val cache = mutableMapOf<String, List<CodebergDiscoveryTreeEntry>>()
        while (queue.isNotEmpty()) {
            budget.checkTime()
            val task = queue.removeFirst()
            stats.maxDepth = maxOf(stats.maxDepth, task.depth)
            val names = hashSetOf<String>()
            val entries = cache[task.sha] ?: fetchTreePages(repository, task.sha, budget).also {
                cache[task.sha] = it
            }
            for (entry in entries) {
                if (stats.entries >= MAX_ENTRIES) throw CodebergDiscoveryLimitException("LIMIT_ENTRIES")
                stats.entries++
                val name = validateEntryName(entry.path)
                if (!names.add(name)) invalid("A Codeberg tree contains a duplicate entry name.")
                val entrySha = fullSha(entry.sha)
                val relativePath = if (task.path == ".") name else "${task.path}/$name"
                if (relativePath.toByteArray(StandardCharsets.UTF_8).size > MAX_PATH_BYTES) {
                    invalid("A repository path exceeds the configured limit.")
                }
                when {
                    entry.mode == "120000" -> stats.symlinks++
                    entry.mode == "160000" || entry.type == "commit" -> stats.submodules++
                    entry.type == "tree" -> {
                        if (entry.mode != "040000" && entry.mode != "40000") {
                            invalid("A Codeberg tree entry has an inconsistent mode.")
                        }
                        if (name == ".git" || name == ".gradle") {
                            stats.cacheTrees++
                        } else {
                            val childDepth = task.depth + 1
                            if (childDepth > MAX_DEPTH) throw CodebergDiscoveryLimitException("LIMIT_DEPTH")
                            if (entrySha in task.ancestors) invalid("A Codeberg tree cycle was returned by the provider.")
                            queue.add(TreeTask(relativePath, entrySha, childDepth, task.ancestors + entrySha))
                        }
                    }
                    entry.type == "blob" -> {
                        if (entry.mode !in REGULAR_BLOB_MODES) invalid("A Codeberg blob entry has an inconsistent mode.")
                        candidate(relativePath, name, entrySha, entry.mode)?.let { found ->
                            if (candidates.putIfAbsent(relativePath, found) != null) {
                                invalid("A Codeberg candidate path was returned more than once.")
                            }
                        }
                    }
                    else -> invalid("A Codeberg tree entry has an unsupported type and mode combination.")
                }
            }
        }
    }

    private suspend fun fetchTreePages(
        repository: CodebergRepository,
        treeSha: String,
        budget: RequestBudget,
    ): List<CodebergDiscoveryTreeEntry> {
        val entries = mutableListOf<CodebergDiscoveryTreeEntry>()
        val names = hashSetOf<String>()
        var page = 1
        var totalCount: Int? = null
        while (true) {
            val response = request<CodebergDiscoveryTreeResponse>(
                apiUrl(
                    repository,
                    listOf("git", "trees", treeSha),
                    mapOf("page" to page.toString(), "per_page" to TREE_PAGE_SIZE.toString()),
                ),
                budget,
            )
            if (fullSha(response.sha) != treeSha) invalid("A Codeberg tree response SHA does not match the requested tree.")
            val responsePage = response.page ?: invalid("A Codeberg tree response omitted page.")
            val responsePerPage = response.perPage ?: invalid("A Codeberg tree response omitted per_page.")
            val responseTotal = response.totalCount ?: invalid("A Codeberg tree response omitted total_count.")
            if (response.truncated == null) invalid("A Codeberg tree response omitted truncated.")
            if (responsePage != page || responsePerPage !in 1..TREE_PAGE_SIZE || responseTotal < 0) {
                invalid("A Codeberg tree response has inconsistent pagination metadata.")
            }
            totalCount = totalCount?.also { existing ->
                if (existing != responseTotal) invalid("A Codeberg tree total_count changed between pages.")
            } ?: responseTotal
            if (response.tree.size > responsePerPage) invalid("A Codeberg tree page exceeds per_page.")
            response.tree.forEach { entry ->
                if (!names.add(entry.path)) invalid("A Codeberg tree contains a duplicate entry name.")
                entries += entry
            }
            if (entries.size.toLong() > MAX_ENTRIES || responseTotal.toLong() > MAX_ENTRIES) {
                throw CodebergDiscoveryLimitException("LIMIT_ENTRIES")
            }
            if (entries.size >= responseTotal && !response.truncated) return entries
            if (response.tree.isEmpty()) invalid("A Codeberg tree pagination response made no progress.")
            if (page >= MAX_TREE_PAGES) throw CodebergDiscoveryLimitException("LIMIT_PAGES")
            page++
        }
    }

    private fun candidate(path: String, name: String, sha: String, mode: String): StaticGradleCandidate? {
        if (name !in GRADLE_FILES) return null
        return StaticGradleCandidate(
            relativePath = path,
            buildRoot = path.substringBeforeLast('/', "."),
            fileKind = name,
            blobSha = sha,
            mode = mode,
            dsl = if (name.endsWith(".kts")) "KOTLIN" else "GROOVY",
        )
    }

    private suspend inline fun <reified T> request(url: String, budget: RequestBudget): T {
        budget.beforeRequest()
        val response = try {
            client.get(url) { codebergHeaders() }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            throw CodebergProviderException(null, "NETWORK_ERROR", failure.message ?: "Codeberg request failed.")
        }
        val bytes = response.boundedBytes(budget)
        if (response.status.value !in 200..299) throw response.providerFailure(bytes)
        val source = decodeUtf8(bytes)
        try {
            StrictJsonAuditor(source, maximumDepth = MAX_JSON_DEPTH, maximumStringBytes = MAX_STRING_BYTES).audit()
            return json.decodeFromString(source)
        } catch (failure: CodebergProviderException) {
            throw failure
        } catch (failure: Exception) {
            throw CodebergDiscoveryMetadataException(failure.message ?: "Codeberg returned invalid JSON.")
        }
    }

    private suspend fun HttpResponse.boundedBytes(budget: RequestBudget): ByteArray {
        val encoding = headers[HttpHeaders.ContentEncoding]
        if (encoding != null && !encoding.equals("identity", ignoreCase = true)) {
            throw CodebergDiscoveryMetadataException("Compressed Codeberg metadata is not accepted.")
        }
        headers[HttpHeaders.ContentLength]?.let { rawLength ->
            val length = rawLength.toLongOrNull()
                ?: throw CodebergDiscoveryMetadataException("Codeberg returned an invalid Content-Length.")
            if (length < 0) throw CodebergDiscoveryMetadataException("Codeberg returned an invalid Content-Length.")
            if (length > MAX_RESPONSE_BYTES) throw CodebergDiscoveryLimitException("LIMIT_BYTES")
        }
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        val channel = bodyAsChannel()
        while (true) {
            val read = channel.readAvailable(buffer, 0, buffer.size)
            if (read == -1) break
            if (read == 0) continue
            if (output.size().toLong() + read > MAX_RESPONSE_BYTES) throw CodebergDiscoveryLimitException("LIMIT_BYTES")
            budget.addBytes(read)
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun HttpResponse.providerFailure(bytes: ByteArray): CodebergProviderException {
        val message = runCatching {
            val source = decodeUtf8(bytes)
            StrictJsonAuditor(source, maximumDepth = MAX_JSON_DEPTH, maximumStringBytes = MAX_STRING_BYTES).audit()
            json.decodeFromString<CodebergDiscoveryApiError>(source).message
        }.getOrNull() ?: status.description
        val rateLimit = codebergRateLimitEvidence(headers, Instant.now())
        val code = when {
            status.value == 404 -> "NOT_FOUND_OR_NOT_PUBLIC"
            status.value == 429 || (status.value == 403 && rateLimit.exhausted) -> "RATE_LIMITED"
            status.value == 403 -> "ACCESS_DENIED"
            status.value == 409 -> "EMPTY_REPOSITORY"
            status.value in 500..599 -> "PROVIDER_UNAVAILABLE"
            else -> "CODEBERG_HTTP_${status.value}"
        }
        return CodebergProviderException(status.value, code, message)
    }

    private fun validateMetadata(requested: CodebergRepository, metadata: CodebergDiscoveryRepositoryMetadata) {
        require(metadata.id > 0) { "The Codeberg repository ID must be positive." }
        require(metadata.objectFormatName == "sha1") { "The Codeberg repository must advertise SHA-1 object format." }
        require(metadata.private == false) { "The Codeberg repository is not confirmed public." }
        require(metadata.owner.login.equals(requested.owner, true) && metadata.name.equals(requested.name, true)) {
            "The Codeberg repository locator does not match the requested owner and name."
        }
        require(metadata.fullName.equals("${metadata.owner.login}/${metadata.name}", false)) {
            "The Codeberg repository full_name is inconsistent."
        }
        val returned = CodebergRepositoryParser.parse(metadata.htmlUrl)
        require(returned.owner.equals(metadata.owner.login, true) && returned.name.equals(metadata.name, true)) {
            "The Codeberg repository html_url is inconsistent."
        }
    }

    private fun validateBranch(value: String): String {
        require(value.isNotBlank() && value.length <= 255 && value.none(Char::isISOControl)) {
            "The Codeberg default branch name is invalid."
        }
        return value
    }

    private fun validateEntryName(value: String): String {
        require(
            value.isNotEmpty() && value != "." && value != ".." && !value.contains('/') &&
                !value.contains('\\') && value.none { it == '\u0000' || it.isISOControl() } &&
                value.toByteArray(StandardCharsets.UTF_8).size <= MAX_PATH_BYTES,
        ) { "A Codeberg tree entry name is invalid." }
        return value
    }

    private fun fullSha(value: String): String {
        if (!FULL_SHA.matches(value)) throw CodebergDiscoveryMetadataException("Codeberg returned a non-full SHA-1 Git object ID.")
        return value.lowercase()
    }

    private fun apiUrl(repository: CodebergRepository, vararg segments: String): String =
        URLBuilder(API_ORIGIN).apply {
            appendPathSegments("repos", repository.owner, repository.name)
            segments.forEach { appendPathSegments(it, encodeSlash = true) }
        }.buildString()

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

    private fun decodeUtf8(bytes: ByteArray): String = try {
        StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (_: Exception) {
        throw CodebergDiscoveryMetadataException("Codeberg returned invalid UTF-8.")
    }

    private fun invalid(message: String): Nothing = throw CodebergDiscoveryMetadataException(message)

    private inner class RequestBudget(private val startedNanos: Long) {
        var requests: Int = 0
            private set
        var bytes: Long = 0
            private set

        fun beforeRequest() {
            checkTime()
            if (requests >= MAX_REQUESTS) throw CodebergDiscoveryLimitException("LIMIT_REQUESTS")
            requests++
        }

        fun addBytes(count: Int) {
            bytes += count
            if (bytes > MAX_TOTAL_BYTES) throw CodebergDiscoveryLimitException("LIMIT_BYTES")
            checkTime()
        }

        fun checkTime() {
            if (nanoTime() - startedNanos > MAX_ELAPSED_NANOS) throw CodebergDiscoveryLimitException("LIMIT_TIME")
        }
    }

    private data class TreeTask(val path: String, val sha: String, val depth: Int, val ancestors: Set<String>)
    private data class DiscoveryStats(
        var entries: Long = 0,
        var maxDepth: Int = 0,
        var symlinks: Long = 0,
        var submodules: Long = 0,
        var cacheTrees: Long = 0,
    )

    private companion object {
        const val PROVIDER = "CODEBERG"
        const val INSTANCE = "codeberg.org"
        const val API_ORIGIN = "https://codeberg.org/api/v1"
        const val USER_AGENT = "ReproDroid-Android/0.1"
        const val MAX_RESPONSE_BYTES = 8L * 1024 * 1024
        const val MAX_TOTAL_BYTES = 32L * 1024 * 1024
        const val MAX_REQUESTS = 100
        const val MAX_ENTRIES = 50_000L
        const val MAX_DEPTH = 32
        const val TREE_PAGE_SIZE = 50
        const val MAX_TREE_PAGES = 1_000
        const val MAX_PATH_BYTES = 1024
        const val MAX_JSON_DEPTH = 32
        const val MAX_STRING_BYTES = 64 * 1024
        const val MAX_ELAPSED_NANOS = 120_000_000_000L
        val FULL_SHA = Regex("[0-9A-Fa-f]{40}")
        val REGULAR_BLOB_MODES = setOf("100644", "100755")
        val GRADLE_FILES = setOf("build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts")
    }
}

private class CodebergDiscoveryLimitException(val code: String) : RuntimeException(code)
private class CodebergDiscoveryMetadataException(message: String) : RuntimeException(message)

@Serializable
private data class CodebergDiscoveryRepositoryMetadata(
    val id: Long,
    val name: String,
    @SerialName("full_name") val fullName: String,
    val private: Boolean? = null,
    @SerialName("html_url") val htmlUrl: String,
    @SerialName("default_branch") val defaultBranch: String,
    @SerialName("object_format_name") val objectFormatName: String? = null,
    val owner: CodebergDiscoveryOwner,
)

@Serializable
private data class CodebergDiscoveryOwner(val login: String)

@Serializable
private data class CodebergDiscoveryGitObject(val type: String, val sha: String)

@Serializable
private data class CodebergDiscoveryRefResponse(
    val ref: String,
    @SerialName("object") val gitObject: CodebergDiscoveryGitObject,
)

@Serializable
private data class CodebergDiscoveryTreePointer(val sha: String)

@Serializable
private data class CodebergDiscoveryCommitResponse(
    val sha: String,
    val tree: CodebergDiscoveryTreePointer? = null,
    val commit: CodebergDiscoveryCommitBody? = null,
) {
    fun treeSha(): String = tree?.sha ?: commit?.tree?.sha
        ?: throw CodebergDiscoveryMetadataException("Codeberg commit response omitted its tree SHA.")
}

@Serializable
private data class CodebergDiscoveryCommitBody(val tree: CodebergDiscoveryTreePointer? = null)

@Serializable
private data class CodebergDiscoveryTreeResponse(
    val sha: String,
    val truncated: Boolean? = null,
    val tree: List<CodebergDiscoveryTreeEntry>,
    val page: Int? = null,
    @SerialName("per_page") val perPage: Int? = null,
    @SerialName("total_count") val totalCount: Int? = null,
)

@Serializable
private data class CodebergDiscoveryTreeEntry(
    val path: String,
    val mode: String,
    val type: String,
    val sha: String,
)

@Serializable
private data class CodebergDiscoveryApiError(val message: String? = null)
