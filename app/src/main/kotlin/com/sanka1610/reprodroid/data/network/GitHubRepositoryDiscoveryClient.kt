package com.sanka1610.reprodroid.data.provider

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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.ArrayDeque

data class GitHubRepositoryIdentity(
    val repository: GitHubRepository,
    val providerRepositoryId: String,
    val displayName: String,
    val defaultBranch: String,
)

data class StaticGradleCandidate(
    val relativePath: String,
    val buildRoot: String,
    val fileKind: String,
    val blobSha: String,
    val mode: String,
    val dsl: String,
)

data class StaticDiscoveryResult(
    val requestedBranch: String,
    val resolvedCommitSha: String?,
    val rootTreeSha: String?,
    val state: String,
    val reason: String?,
    val diagnostic: String? = null,
    val entryCount: Long,
    val requestCount: Int,
    val receivedBytes: Long,
    val maxDepth: Int,
    val excludedSymlinkCount: Long,
    val excludedSubmoduleCount: Long,
    val excludedCacheTreeCount: Long,
    val candidates: List<StaticGradleCandidate>,
)

data class RepositoryRegistrationPreview(
    val normalizedInputUrl: String,
    val identity: GitHubRepositoryIdentity,
    val discovery: StaticDiscoveryResult,
)

class GitHubRepositoryDiscoveryClient(
    engine: HttpClientEngine? = null,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val client = if (engine == null) HttpClient(Android) { configure() } else HttpClient(engine) { configure() }

    suspend fun preview(
        repositoryUrl: String,
        onIdentityResolved: suspend (GitHubRepositoryIdentity) -> Unit = {},
    ): RepositoryRegistrationPreview {
        val repository = GitHubRepositoryParser.parse(repositoryUrl)
        val budget = RequestBudget(nanoTime())
        val metadata = request<RegistrationRepositoryMetadata>(
            apiUrl(repository),
            budget,
        )
        validateMetadata(repository, metadata)
        val identity = GitHubRepositoryIdentity(
            repository = GitHubRepository(metadata.owner.login, metadata.name),
            providerRepositoryId = metadata.id.toString(),
            displayName = metadata.name,
            defaultBranch = validateBranch(metadata.defaultBranch),
        )
        onIdentityResolved(identity)
        val discovery = discover(identity, budget)
        return RepositoryRegistrationPreview(
            normalizedInputUrl = identity.repository.canonicalUrl,
            identity = identity,
            discovery = discovery,
        )
    }

    private suspend fun discover(
        identity: GitHubRepositoryIdentity,
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
            val ref = request<RegistrationRefResponse>(
                apiUrl(identity.repository, "git", "ref", "heads", identity.defaultBranch),
                budget,
            )
            if (ref.gitObject.type != "commit") invalid("The default branch ref does not target a commit.")
            commitSha = fullSha(ref.gitObject.sha)
            val commit = request<RegistrationCommitResponse>(
                apiUrl(identity.repository, "git", "commits", commitSha),
                budget,
            )
            if (fullSha(commit.sha) != commitSha) invalid("The commit response SHA does not match the request.")
            rootTreeSha = fullSha(commit.tree.sha)
            walkTrees(identity.repository, rootTreeSha, budget, stats, candidates)
        } catch (limit: DiscoveryLimitException) {
            state = "INCOMPLETE"
            reason = limit.code
        } catch (failure: GitHubProviderException) {
            state = "FAILED"
            reason = failure.code
            diagnostic = failure.message
        } catch (failure: InvalidProviderMetadataException) {
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
        repository: GitHubRepository,
        rootTreeSha: String,
        budget: RequestBudget,
        stats: DiscoveryStats,
        candidates: MutableMap<String, StaticGradleCandidate>,
    ) {
        val queue = ArrayDeque<TreeTask>()
        queue.add(TreeTask(".", rootTreeSha, 0, setOf(rootTreeSha)))
        val cache = mutableMapOf<String, RegistrationTreeResponse>()
        var providerTruncated = false
        while (queue.isNotEmpty()) {
            budget.checkTime()
            val task = queue.removeFirst()
            stats.maxDepth = maxOf(stats.maxDepth, task.depth)
            val tree = cache[task.sha] ?: request<RegistrationTreeResponse>(
                apiUrl(repository, "git", "trees", task.sha),
                budget,
            ).also { cache[task.sha] = it }
            if (fullSha(tree.sha) != task.sha) invalid("A tree response SHA does not match the requested tree.")
            if (tree.truncated == null) invalid("A tree response omitted truncated.")
            providerTruncated = providerTruncated || tree.truncated
            val names = hashSetOf<String>()
            for (entry in tree.tree) {
                if (stats.entries >= MAX_ENTRIES) throw DiscoveryLimitException("LIMIT_ENTRIES")
                stats.entries++
                val name = validateEntryName(entry.path)
                if (!names.add(name)) invalid("A tree contains a duplicate entry name.")
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
                            invalid("A tree entry has an inconsistent mode.")
                        }
                        if (name == ".git" || name == ".gradle") {
                            stats.cacheTrees++
                        } else {
                            val childDepth = task.depth + 1
                            if (childDepth > MAX_DEPTH) throw DiscoveryLimitException("LIMIT_DEPTH")
                            if (entrySha in task.ancestors) invalid("A tree cycle was returned by the provider.")
                            queue.add(TreeTask(relativePath, entrySha, childDepth, task.ancestors + entrySha))
                        }
                    }
                    entry.type == "blob" -> {
                        if (entry.mode !in REGULAR_BLOB_MODES) invalid("A blob entry has an inconsistent mode.")
                        candidate(relativePath, name, entrySha, entry.mode)?.let { found ->
                            if (candidates.putIfAbsent(relativePath, found) != null) {
                                invalid("A candidate path was returned more than once.")
                            }
                        }
                    }
                    else -> invalid("A tree entry has an unsupported type and mode combination.")
                }
            }
        }
        if (providerTruncated) throw DiscoveryLimitException("PROVIDER_TRUNCATED")
    }

    private fun candidate(path: String, name: String, sha: String, mode: String): StaticGradleCandidate? {
        if (name !in GRADLE_FILES) return null
        val parent = path.substringBeforeLast('/', ".")
        return StaticGradleCandidate(
            relativePath = path,
            buildRoot = parent,
            fileKind = name,
            blobSha = sha,
            mode = mode,
            dsl = if (name.endsWith(".kts")) "KOTLIN" else "GROOVY",
        )
    }

    private suspend inline fun <reified T> request(url: String, budget: RequestBudget): T {
        budget.beforeRequest()
        val response = try {
            client.get(url) { githubHeaders() }
        } catch (failure: Exception) {
            throw GitHubProviderException(null, "NETWORK_ERROR", failure.message ?: "GitHub request failed.")
        }
        val bytes = response.boundedBytes(budget)
        if (response.status.value !in 200..299) throw response.providerFailure(bytes)
        val source = decodeUtf8(bytes)
        StrictJsonValidator(source, MAX_JSON_DEPTH).validate()
        return try {
            json.decodeFromString(source)
        } catch (failure: Exception) {
            throw InvalidProviderMetadataException(failure.message ?: "GitHub returned invalid JSON.")
        }
    }

    private suspend fun HttpResponse.boundedBytes(budget: RequestBudget): ByteArray {
        val encoding = headers[HttpHeaders.ContentEncoding]
        if (encoding != null && !encoding.equals("identity", ignoreCase = true)) {
            throw InvalidProviderMetadataException("Compressed provider metadata is not accepted in Phase 4.1.")
        }
        headers[HttpHeaders.ContentLength]?.let { rawLength ->
            val length = rawLength.toLongOrNull()
                ?: throw InvalidProviderMetadataException("GitHub returned an invalid Content-Length.")
            if (length < 0) throw InvalidProviderMetadataException("GitHub returned an invalid Content-Length.")
            if (length > MAX_RESPONSE_BYTES) throw DiscoveryLimitException("LIMIT_BYTES")
        }
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        val channel = bodyAsChannel()
        while (true) {
            val read = channel.readAvailable(buffer, 0, buffer.size)
            if (read == -1) break
            if (read == 0) continue
            if (output.size().toLong() + read > MAX_RESPONSE_BYTES) throw DiscoveryLimitException("LIMIT_BYTES")
            budget.addBytes(read)
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun HttpResponse.providerFailure(bytes: ByteArray): GitHubProviderException {
        val message = runCatching {
            val source = decodeUtf8(bytes)
            StrictJsonValidator(source, MAX_JSON_DEPTH).validate()
            json.decodeFromString<RegistrationApiError>(source).message
        }.getOrNull() ?: status.description
        val rateLimited = headers["X-RateLimit-Remaining"] == "0" || headers[HttpHeaders.RetryAfter] != null
        val code = when {
            status.value == 404 -> "NOT_FOUND_OR_NOT_PUBLIC"
            status.value == 403 && rateLimited -> "RATE_LIMITED"
            status.value == 429 -> "RATE_LIMITED"
            status.value == 403 -> "ACCESS_DENIED"
            status.value == 409 -> "EMPTY_REPOSITORY"
            else -> "GITHUB_HTTP_${status.value}"
        }
        return GitHubProviderException(status.value, code, message)
    }

    private fun validateMetadata(requested: GitHubRepository, metadata: RegistrationRepositoryMetadata) {
        if (metadata.id <= 0) invalid("The repository ID must be positive.")
        if (metadata.private != false) invalid("The repository is not confirmed public.")
        if (!metadata.owner.login.equals(requested.owner, true) || !metadata.name.equals(requested.name, true)) {
            invalid("The repository locator does not match the requested owner and name.")
        }
        if (!metadata.fullName.equals("${metadata.owner.login}/${metadata.name}", false)) {
            invalid("The repository full_name is inconsistent.")
        }
        val returned = GitHubRepositoryParser.parse(metadata.htmlUrl)
        if (!returned.owner.equals(metadata.owner.login, true) || !returned.name.equals(metadata.name, true)) {
            invalid("The repository html_url is inconsistent.")
        }
    }

    private fun validateBranch(value: String): String {
        if (value.isBlank() || value.length > 255 || value.any { it.isISOControl() }) {
            invalid("The default branch name is invalid.")
        }
        return value
    }

    private fun validateEntryName(value: String): String {
        if (
            value.isEmpty() || value == "." || value == ".." || value.contains('/') ||
            value.contains('\\') || value.any { it == '\u0000' || it.isISOControl() } ||
            value.toByteArray(StandardCharsets.UTF_8).size > MAX_PATH_BYTES
        ) invalid("A tree entry name is invalid.")
        return value
    }

    private fun fullSha(value: String): String {
        if (!FULL_SHA.matches(value)) invalid("GitHub returned a non-full Git object SHA.")
        return value.lowercase()
    }

    private fun apiUrl(repository: GitHubRepository, vararg segments: String): String =
        URLBuilder(API_ORIGIN).apply {
            appendPathSegments("repos", repository.owner, repository.name)
            segments.forEach { appendPathSegments(it, encodeSlash = true) }
        }.buildString()

    private fun HttpRequestBuilder.githubHeaders() {
        header(HttpHeaders.Accept, GITHUB_JSON_MEDIA_TYPE)
        header(HttpHeaders.AcceptEncoding, "identity")
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
    }

    private fun decodeUtf8(bytes: ByteArray): String = try {
        StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (failure: Exception) {
        throw InvalidProviderMetadataException("GitHub returned invalid UTF-8.")
    }

    private fun invalid(message: String): Nothing = throw InvalidProviderMetadataException(message)

    private inner class RequestBudget(private val startedNanos: Long) {
        var requests: Int = 0
            private set
        var bytes: Long = 0
            private set

        fun beforeRequest() {
            checkTime()
            if (requests >= MAX_REQUESTS) throw DiscoveryLimitException("LIMIT_REQUESTS")
            requests++
        }

        fun addBytes(count: Int) {
            bytes += count
            if (bytes > MAX_TOTAL_BYTES) throw DiscoveryLimitException("LIMIT_BYTES")
            checkTime()
        }

        fun checkTime() {
            if (nanoTime() - startedNanos > MAX_ELAPSED_NANOS) throw DiscoveryLimitException("LIMIT_TIME")
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
        const val API_ORIGIN = "https://api.github.com"
        const val GITHUB_JSON_MEDIA_TYPE = "application/vnd.github+json"
        const val GITHUB_API_VERSION_HEADER = "X-GitHub-Api-Version"
        const val GITHUB_API_VERSION = "2022-11-28"
        const val USER_AGENT = "ReproDroid-Android/0.1"
        const val MAX_RESPONSE_BYTES = 8L * 1024 * 1024
        const val MAX_TOTAL_BYTES = 32L * 1024 * 1024
        const val MAX_REQUESTS = 100
        const val MAX_ENTRIES = 50_000L
        const val MAX_DEPTH = 32
        const val MAX_PATH_BYTES = 1024
        const val MAX_JSON_DEPTH = 32
        const val MAX_ELAPSED_NANOS = 120_000_000_000L
        val FULL_SHA = Regex("[0-9A-Fa-f]{40}")
        val REGULAR_BLOB_MODES = setOf("100644", "100755")
        val GRADLE_FILES = setOf("build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts")
    }
}

private class DiscoveryLimitException(val code: String) : RuntimeException(code)
private class InvalidProviderMetadataException(message: String) : RuntimeException(message)

@Serializable
private data class RegistrationRepositoryMetadata(
    val id: Long,
    val name: String,
    @SerialName("full_name") val fullName: String,
    val private: Boolean? = null,
    @SerialName("html_url") val htmlUrl: String,
    @SerialName("default_branch") val defaultBranch: String,
    val owner: RegistrationOwner,
)

@Serializable private data class RegistrationOwner(val login: String)
@Serializable private data class RegistrationGitObject(val type: String, val sha: String)
@Serializable private data class RegistrationTreePointer(val sha: String)
@Serializable private data class RegistrationRefResponse(
    val ref: String,
    @SerialName("object") val gitObject: RegistrationGitObject,
)
@Serializable private data class RegistrationCommitResponse(val sha: String, val tree: RegistrationTreePointer)
@Serializable private data class RegistrationTreeResponse(
    val sha: String,
    val truncated: Boolean? = null,
    val tree: List<RegistrationTreeEntry>,
)
@Serializable private data class RegistrationTreeEntry(
    val path: String,
    val mode: String,
    val type: String,
    val sha: String,
)
@Serializable private data class RegistrationApiError(val message: String? = null)

private class StrictJsonValidator(private val source: String, private val maxDepth: Int) {
    private var index = 0

    fun validate() {
        value(0)
        whitespace()
        if (index != source.length) fail()
    }

    private fun value(depth: Int) {
        whitespace()
        if (index >= source.length) fail()
        when (source[index]) {
            '{' -> objectValue(depth + 1)
            '[' -> arrayValue(depth + 1)
            '"' -> stringValue()
            't' -> literal("true")
            'f' -> literal("false")
            'n' -> literal("null")
            '-', in '0'..'9' -> number()
            else -> fail()
        }
    }

    private fun objectValue(depth: Int) {
        if (depth > maxDepth) fail()
        index++
        whitespace()
        if (consume('}')) return
        val keys = hashSetOf<String>()
        while (true) {
            whitespace()
            if (index >= source.length || source[index] != '"') fail()
            val key = stringValue()
            if (!keys.add(key)) fail()
            whitespace()
            requireChar(':')
            value(depth)
            whitespace()
            if (consume('}')) return
            requireChar(',')
        }
    }

    private fun arrayValue(depth: Int) {
        if (depth > maxDepth) fail()
        index++
        whitespace()
        if (consume(']')) return
        while (true) {
            value(depth)
            whitespace()
            if (consume(']')) return
            requireChar(',')
        }
    }

    private fun stringValue(): String {
        requireChar('"')
        val result = StringBuilder()
        while (index < source.length) {
            val current = source[index++]
            when {
                current == '"' -> return result.toString()
                current == '\\' -> {
                    if (index >= source.length) fail()
                    when (val escaped = source[index++]) {
                        '"', '\\', '/' -> result.append(escaped)
                        'b' -> result.append('\b')
                        'f' -> result.append('\u000c')
                        'n' -> result.append('\n')
                        'r' -> result.append('\r')
                        't' -> result.append('\t')
                        'u' -> {
                            if (index + 4 > source.length) fail()
                            val code = source.substring(index, index + 4).toIntOrNull(16) ?: fail()
                            index += 4
                            when {
                                code in HIGH_SURROGATE_RANGE -> {
                                    if (!source.startsWith("\\u", index) || index + 6 > source.length) fail()
                                    val low = source.substring(index + 2, index + 6).toIntOrNull(16) ?: fail()
                                    if (low !in LOW_SURROGATE_RANGE) fail()
                                    result.append(code.toChar()).append(low.toChar())
                                    index += 6
                                }
                                code in LOW_SURROGATE_RANGE -> fail()
                                else -> result.append(code.toChar())
                            }
                        }
                        else -> fail()
                    }
                }
                current.code < 0x20 -> fail()
                else -> result.append(current)
            }
        }
        fail()
    }

    private fun number() {
        if (consume('-') && index >= source.length) fail()
        if (consume('0')) {
            if (index < source.length && source[index].isDigit()) fail()
        } else {
            digits(required = true)
        }
        if (consume('.')) digits(required = true)
        if (index < source.length && source[index] in "eE") {
            index++
            if (index < source.length && source[index] in "+-") index++
            digits(required = true)
        }
    }

    private fun digits(required: Boolean) {
        val start = index
        while (index < source.length && source[index].isDigit()) index++
        if (required && start == index) fail()
    }

    private fun literal(expected: String) {
        if (!source.startsWith(expected, index)) fail()
        index += expected.length
    }

    private fun whitespace() {
        while (index < source.length && source[index] in " \n\r\t") index++
    }

    private fun consume(expected: Char): Boolean {
        if (index < source.length && source[index] == expected) {
            index++
            return true
        }
        return false
    }

    private fun requireChar(expected: Char) {
        if (!consume(expected)) fail()
    }

    private fun fail(): Nothing = throw InvalidProviderMetadataException("GitHub returned invalid or ambiguous JSON.")

    private companion object {
        val HIGH_SURROGATE_RANGE = 0xD800..0xDBFF
        val LOW_SURROGATE_RANGE = 0xDC00..0xDFFF
    }
}
