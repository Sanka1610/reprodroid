package com.sanka1610.reprodroid.data.provider

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class GitHubRepositoryDiscoveryClientTest {
    @Test
    fun `preview resolves a fixed commit and scans non-recursive trees without reading blobs`() = runBlocking {
        val commit = "1".repeat(40)
        val root = "2".repeat(40)
        val child = "3".repeat(40)
        val blob = "4".repeat(40)
        val requests = mutableListOf<String>()
        val engine = MockEngine { request ->
            requests += request.url.encodedPath
            val body = when (request.url.encodedPath) {
                "/repos/example/project" -> metadata(defaultBranch = "main")
                "/repos/example/project/git/ref/heads/main" ->
                    """{"ref":"refs/heads/main","object":{"type":"commit","sha":"$commit"}}"""
                "/repos/example/project/git/commits/$commit" ->
                    """{"sha":"$commit","tree":{"type":"tree","sha":"$root"}}"""
                "/repos/example/project/git/trees/$root" ->
                    """{"sha":"$root","truncated":false,"tree":[
                        {"path":"build.gradle","mode":"100644","type":"blob","sha":"$blob"},
                        {"path":"module","mode":"040000","type":"tree","sha":"$child"},
                        {"path":"link","mode":"120000","type":"blob","sha":"$blob"},
                        {"path":"vendor","mode":"160000","type":"commit","sha":"$commit"},
                        {"path":".gradle","mode":"040000","type":"tree","sha":"${"5".repeat(40)}"}
                    ]}"""
                "/repos/example/project/git/trees/$child" ->
                    """{"sha":"$child","truncated":false,"tree":[
                        {"path":"settings.gradle.kts","mode":"100755","type":"blob","sha":"$blob"}
                    ]}"""
                else -> error("Unexpected request ${request.url}")
            }
            respond(body, HttpStatusCode.OK, JSON_HEADERS)
        }

        val preview = GitHubRepositoryDiscoveryClient(engine).preview("https://github.com/example/project")

        assertEquals("123456789012345678", preview.identity.providerRepositoryId)
        assertEquals(commit, preview.discovery.resolvedCommitSha)
        assertEquals("COMPLETE", preview.discovery.state)
        assertEquals(listOf("build.gradle", "module/settings.gradle.kts"), preview.discovery.candidates.map { it.relativePath })
        assertEquals(1, preview.discovery.excludedSymlinkCount)
        assertEquals(1, preview.discovery.excludedSubmoduleCount)
        assertEquals(1, preview.discovery.excludedCacheTreeCount)
        assertTrue(requests.none { "blob" in it })
    }

    @Test
    fun `truncated tree retains candidates but reports incomplete`() = runBlocking {
        val commit = "1".repeat(40)
        val root = "2".repeat(40)
        val blob = "3".repeat(40)
        val engine = MockEngine { request ->
            val body = when (request.url.encodedPath) {
                "/repos/example/project" -> metadata(defaultBranch = "main")
                "/repos/example/project/git/ref/heads/main" ->
                    """{"ref":"refs/heads/main","object":{"type":"commit","sha":"$commit"}}"""
                "/repos/example/project/git/commits/$commit" ->
                    """{"sha":"$commit","tree":{"type":"tree","sha":"$root"}}"""
                else -> """{"sha":"$root","truncated":true,"tree":[
                    {"path":"settings.gradle","mode":"100644","type":"blob","sha":"$blob"}
                ]}"""
            }
            respond(body, HttpStatusCode.OK, JSON_HEADERS)
        }

        val result = GitHubRepositoryDiscoveryClient(engine).preview("https://github.com/example/project").discovery

        assertEquals("INCOMPLETE", result.state)
        assertEquals("PROVIDER_TRUNCATED", result.reason)
        assertEquals(1, result.candidates.size)
    }

    @Test
    fun `duplicate JSON keys are rejected before DTO decoding`() {
        val engine = MockEngine {
            respond(
                metadata(defaultBranch = "main").replace(
                    "\"id\":123456789012345678",
                    "\"id\":1,\"id\":123456789012345678",
                ),
                HttpStatusCode.OK,
                JSON_HEADERS,
            )
        }

        val failure = assertThrows(RuntimeException::class.java) {
            runBlocking { GitHubRepositoryDiscoveryClient(engine).preview("https://github.com/example/project") }
        }
        assertTrue(failure.message.orEmpty().contains("ambiguous JSON"))
    }

    @Test
    fun `unpaired JSON surrogate escape is rejected before DTO decoding`() {
        val engine = MockEngine {
            respond(
                metadata(defaultBranch = "main").replace("\"name\":\"project\"", "\"name\":\"\\uD800\""),
                HttpStatusCode.OK,
                JSON_HEADERS,
            )
        }

        val failure = assertThrows(RuntimeException::class.java) {
            runBlocking { GitHubRepositoryDiscoveryClient(engine).preview("https://github.com/example/project") }
        }
        assertTrue(failure.message.orEmpty().contains("ambiguous JSON"))
    }

    @Test
    fun `compressed metadata response is rejected before body parsing`() {
        val engine = MockEngine {
            respond(
                metadata(defaultBranch = "main"),
                HttpStatusCode.OK,
                headersOf(
                    HttpHeaders.ContentType to listOf("application/json"),
                    HttpHeaders.ContentEncoding to listOf("gzip"),
                ),
            )
        }

        val failure = assertThrows(RuntimeException::class.java) {
            runBlocking { GitHubRepositoryDiscoveryClient(engine).preview("https://github.com/example/project") }
        }
        assertTrue(failure.message.orEmpty().contains("Compressed provider metadata"))
    }

    @Test
    fun `mismatched tree SHA fails without accepting partial candidates`() = runBlocking {
        val commit = "1".repeat(40)
        val root = "2".repeat(40)
        val wrong = "3".repeat(40)
        val engine = MockEngine { request ->
            val body = when (request.url.encodedPath) {
                "/repos/example/project" -> metadata(defaultBranch = "main")
                "/repos/example/project/git/ref/heads/main" ->
                    """{"ref":"refs/heads/main","object":{"type":"commit","sha":"$commit"}}"""
                "/repos/example/project/git/commits/$commit" ->
                    """{"sha":"$commit","tree":{"sha":"$root"}}"""
                else -> """{"sha":"$wrong","truncated":false,"tree":[]}"""
            }
            respond(body, HttpStatusCode.OK, JSON_HEADERS)
        }

        val result = GitHubRepositoryDiscoveryClient(engine)
            .preview("https://github.com/example/project")
            .discovery

        assertEquals("FAILED", result.state)
        assertEquals("INVALID_METADATA", result.reason)
        assertTrue(result.candidates.isEmpty())
    }

    @Test
    fun `branch slash remains one encoded API path segment`() = runBlocking {
        val observedPaths = mutableListOf<String>()
        val engine = MockEngine { request ->
            observedPaths += request.url.encodedPath
            if (observedPaths.size == 1) {
                respond(metadata(defaultBranch = "feature/test"), HttpStatusCode.OK, JSON_HEADERS)
            } else {
                respond("{\"message\":\"empty\"}", HttpStatusCode.Conflict, JSON_HEADERS)
            }
        }

        val result = GitHubRepositoryDiscoveryClient(engine)
            .preview("https://github.com/example/project")

        assertEquals("FAILED", result.discovery.state)
        assertEquals("EMPTY_REPOSITORY", result.discovery.reason)
        assertEquals("/repos/example/project/git/ref/heads/feature%2Ftest", observedPaths[1])
    }

    @Test
    fun `request budget returns partial incomplete result before request 101`() = runBlocking {
        val commit = "1".repeat(40)
        val root = "2".repeat(40)
        val childShas = (0 until 98).map { index -> "%040x".format(index + 16) }
        val engine = MockEngine { request ->
            val body = when (request.url.encodedPath) {
                "/repos/example/project" -> metadata(defaultBranch = "main")
                "/repos/example/project/git/ref/heads/main" ->
                    """{"ref":"refs/heads/main","object":{"type":"commit","sha":"$commit"}}"""
                "/repos/example/project/git/commits/$commit" ->
                    """{"sha":"$commit","tree":{"sha":"$root"}}"""
                "/repos/example/project/git/trees/$root" -> {
                    val entries = childShas.mapIndexed { index, sha ->
                        """{"path":"d$index","mode":"040000","type":"tree","sha":"$sha"}"""
                    }.joinToString(",")
                    """{"sha":"$root","truncated":false,"tree":[$entries]}"""
                }
                else -> {
                    val sha = request.url.encodedPath.substringAfterLast('/')
                    """{"sha":"$sha","truncated":false,"tree":[]}"""
                }
            }
            respond(body, HttpStatusCode.OK, JSON_HEADERS)
        }

        val result = GitHubRepositoryDiscoveryClient(engine)
            .preview("https://github.com/example/project")
            .discovery

        assertEquals("INCOMPLETE", result.state)
        assertEquals("LIMIT_REQUESTS", result.reason)
        assertEquals(100, result.requestCount)
        assertEquals(98, result.entryCount)
    }

    @Test
    fun `entry limit is complete at 50000 and incomplete when entry 50001 is present`() = runBlocking {
        val complete = previewWithRootEntries(50_000)
        assertEquals("COMPLETE", complete.state)
        assertEquals(50_000, complete.entryCount)

        val incomplete = previewWithRootEntries(50_001)
        assertEquals("INCOMPLETE", incomplete.state)
        assertEquals("LIMIT_ENTRIES", incomplete.reason)
        assertEquals(50_000, incomplete.entryCount)
    }

    @Test
    fun `depth 32 is complete and a child at depth 33 is incomplete`() = runBlocking {
        val complete = previewWithTreeDepth(32)
        assertEquals("COMPLETE", complete.state)
        assertEquals(32, complete.maxDepth)

        val incomplete = previewWithTreeDepth(33)
        assertEquals("INCOMPLETE", incomplete.state)
        assertEquals("LIMIT_DEPTH", incomplete.reason)
        assertEquals(32, incomplete.maxDepth)
    }

    @Test
    fun `overall deadline stops before the next discovery request`() = runBlocking {
        var clockReads = 0
        val engine = MockEngine { respond(metadata(defaultBranch = "main"), HttpStatusCode.OK, JSON_HEADERS) }
        val result = GitHubRepositoryDiscoveryClient(engine) {
            clockReads++
            if (clockReads >= 4) 120_000_000_001L else 0L
        }.preview("https://github.com/example/project").discovery

        assertEquals("INCOMPLETE", result.state)
        assertEquals("LIMIT_TIME", result.reason)
        assertEquals(1, result.requestCount)
    }

    @Test
    fun `total byte limit is complete at 32 MiB and incomplete one byte above`() = runBlocking {
        val complete = previewWithTotalBytes(32 * 1024 * 1024)
        assertEquals("COMPLETE", complete.state)
        assertEquals(32L * 1024 * 1024, complete.receivedBytes)

        val incomplete = previewWithTotalBytes(32 * 1024 * 1024 + 1)
        assertEquals("INCOMPLETE", incomplete.state)
        assertEquals("LIMIT_BYTES", incomplete.reason)
        assertTrue(incomplete.receivedBytes > 32L * 1024 * 1024)
    }

    @Test
    fun `an exact 8 MiB unknown-length metadata body is accepted`() = runBlocking {
        var requests = 0
        val body = paddedMetadata(8 * 1024 * 1024)
        assertEquals(8 * 1024 * 1024, body.toByteArray().size)
        val engine = MockEngine {
            requests++
            if (requests == 1) {
                respond(body, HttpStatusCode.OK, JSON_HEADERS)
            } else {
                respond("""{"message":"empty"}""", HttpStatusCode.Conflict, JSON_HEADERS)
            }
        }

        val result = GitHubRepositoryDiscoveryClient(engine)
            .preview("https://github.com/example/project")
            .discovery

        assertEquals("FAILED", result.state)
        assertEquals("EMPTY_REPOSITORY", result.reason)
    }

    @Test
    fun `a metadata body above 8 MiB is rejected while streaming`() {
        val engine = MockEngine {
            respond(paddedMetadata(8 * 1024 * 1024 + 1), HttpStatusCode.OK, JSON_HEADERS)
        }

        val failure = assertThrows(RuntimeException::class.java) {
            runBlocking { GitHubRepositoryDiscoveryClient(engine).preview("https://github.com/example/project") }
        }
        assertEquals("LIMIT_BYTES", failure.message)
    }

    @Test
    fun `invalid UTF-8 numeric overflow and malformed content length are rejected`() {
        val invalidUtf8 = MockEngine {
            respond(ByteReadChannel(byteArrayOf(0xC3.toByte(), 0x28)), HttpStatusCode.OK, JSON_HEADERS)
        }
        val overflow = MockEngine {
            respond(
                metadata(defaultBranch = "main").replace(
                    "123456789012345678",
                    "123456789012345678901234567890",
                ),
                HttpStatusCode.OK,
                JSON_HEADERS,
            )
        }
        val invalidLength = MockEngine {
            respond(
                metadata(defaultBranch = "main"),
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentLength, "invalid"),
            )
        }

        listOf(invalidUtf8, overflow, invalidLength).forEach { engine ->
            assertThrows(RuntimeException::class.java) {
                runBlocking { GitHubRepositoryDiscoveryClient(engine).preview("https://github.com/example/project") }
            }
        }
    }

    @Test
    fun `tree cycle duplicate entry and inconsistent mode are rejected`() = runBlocking {
        val commit = "1".repeat(40)
        val root = "2".repeat(40)
        val child = "3".repeat(40)
        val blob = "4".repeat(40)
        val invalidTrees = listOf(
            mapOf(
                root to """{"sha":"$root","truncated":false,"tree":[{"path":"child","mode":"040000","type":"tree","sha":"$child"}]}""",
                child to """{"sha":"$child","truncated":false,"tree":[{"path":"root","mode":"040000","type":"tree","sha":"$root"}]}""",
            ),
            mapOf(
                root to """{"sha":"$root","truncated":false,"tree":[
                    {"path":"same","mode":"100644","type":"blob","sha":"$blob"},
                    {"path":"same","mode":"100644","type":"blob","sha":"$blob"}
                ]}""",
            ),
            mapOf(
                root to """{"sha":"$root","truncated":false,"tree":[
                    {"path":"bad","mode":"100644","type":"tree","sha":"$child"}
                ]}""",
            ),
        )
        invalidTrees.forEach { trees ->
            val engine = standardTreeEngine(commit, root, trees)
            val result = GitHubRepositoryDiscoveryClient(engine)
                .preview("https://github.com/example/project")
                .discovery
            assertEquals("FAILED", result.state)
            assertEquals("INVALID_METADATA", result.reason)
        }
    }

    @Test
    fun `complete cache symlink and submodule only tree reports zero candidates`() = runBlocking {
        val commit = "1".repeat(40)
        val root = "2".repeat(40)
        val blob = "3".repeat(40)
        val tree = """{"sha":"$root","truncated":false,"tree":[
            {"path":".gradle","mode":"040000","type":"tree","sha":"${"4".repeat(40)}"},
            {"path":"link","mode":"120000","type":"blob","sha":"$blob"},
            {"path":"vendor","mode":"160000","type":"commit","sha":"$commit"}
        ]}"""

        val result = GitHubRepositoryDiscoveryClient(standardTreeEngine(commit, root, mapOf(root to tree)))
            .preview("https://github.com/example/project")
            .discovery

        assertEquals("COMPLETE", result.state)
        assertTrue(result.candidates.isEmpty())
        assertEquals(1, result.excludedCacheTreeCount)
        assertEquals(1, result.excludedSymlinkCount)
        assertEquals(1, result.excludedSubmoduleCount)
    }

    @Test
    fun `private access denied rate limit and offline failures remain distinct`() {
        val cases = listOf(
            MockEngine {
                respond(metadata(defaultBranch = "main").replace("\"private\":false", "\"private\":true"))
            } to "invalid",
            MockEngine {
                respond("""{"message":"forbidden"}""", HttpStatusCode.Forbidden, JSON_HEADERS)
            } to "ACCESS_DENIED",
            MockEngine {
                respond(
                    """{"message":"rate"}""",
                    HttpStatusCode.Forbidden,
                    headersOf("X-RateLimit-Remaining", "0"),
                )
            } to "RATE_LIMITED",
            MockEngine { throw IOException("offline") } to "NETWORK_ERROR",
        )
        cases.forEach { (engine, expected) ->
            val failure = assertThrows(RuntimeException::class.java) {
                runBlocking { GitHubRepositoryDiscoveryClient(engine).preview("https://github.com/example/project") }
            }
            if (expected != "invalid") assertTrue(failure.message.orEmpty().contains(expected) || failure is GitHubProviderException && failure.code == expected)
        }
    }

    @Test
    fun `encoded separators and ambiguous paths are rejected`() {
        listOf(
            "https://github.com/example%2fadmin/project",
            "https://github.com/example//project",
            "https://github.com/example/../project",
            "https://github.com/./project",
        ).forEach { url ->
            assertThrows(InvalidGitHubRepositoryException::class.java) {
                GitHubRepositoryParser.parse(url)
            }
        }
    }

    private fun metadata(defaultBranch: String) = """
        {
          "id":123456789012345678,
          "name":"project",
          "full_name":"example/project",
          "private":false,
          "html_url":"https://github.com/example/project",
          "default_branch":"$defaultBranch",
          "owner":{"login":"example"}
        }
    """.trimIndent()

    private suspend fun previewWithRootEntries(count: Int): StaticDiscoveryResult {
        val commit = "1".repeat(40)
        val root = "2".repeat(40)
        val body = buildString(count * 105) {
            append("{\"sha\":\"").append(root).append("\",\"truncated\":false,\"tree\":[")
            repeat(count) { index ->
                if (index > 0) append(',')
                append("{\"path\":\"file").append(index)
                    .append("\",\"mode\":\"100644\",\"type\":\"blob\",\"sha\":\"")
                    .append("%040x".format(index + 16)).append("\"}")
            }
            append("]}")
        }
        val engine = standardTreeEngine(commit, root, mapOf(root to body))
        return GitHubRepositoryDiscoveryClient(engine)
            .preview("https://github.com/example/project")
            .discovery
    }

    private suspend fun previewWithTreeDepth(depth: Int): StaticDiscoveryResult {
        val commit = "1".repeat(40)
        val shas = (0..depth).map { "%040x".format(it + 32) }
        val trees = shas.mapIndexed { index, sha ->
            sha to if (index == depth) {
                """{"sha":"$sha","truncated":false,"tree":[]}"""
            } else {
                """{"sha":"$sha","truncated":false,"tree":[
                    {"path":"d$index","mode":"040000","type":"tree","sha":"${shas[index + 1]}"}
                ]}"""
            }
        }.toMap()
        val engine = standardTreeEngine(commit, shas.first(), trees)
        return GitHubRepositoryDiscoveryClient(engine)
            .preview("https://github.com/example/project")
            .discovery
    }

    private suspend fun previewWithTotalBytes(totalBytes: Int): StaticDiscoveryResult {
        val commit = "1".repeat(40)
        val root = "2".repeat(40)
        val children = (0 until 4).map { "%040x".format(it + 64) }
        val ref = """{"ref":"refs/heads/main","object":{"type":"commit","sha":"$commit"}}"""
        val commitBody = """{"sha":"$commit","tree":{"sha":"$root"}}"""
        val rootBody = buildString {
            append("{\"sha\":\"").append(root).append("\",\"truncated\":false,\"tree\":[")
            children.forEachIndexed { index, sha ->
                if (index > 0) append(',')
                append("{\"path\":\"d").append(index)
                    .append("\",\"mode\":\"040000\",\"type\":\"tree\",\"sha\":\"")
                    .append(sha).append("\"}")
            }
            append("]}")
        }
        val metadataBody = metadata(defaultBranch = "main")
        val fixedBytes = listOf(metadataBody, ref, commitBody, rootBody).sumOf { it.toByteArray().size }
        val childTotal = totalBytes - fixedBytes
        require(childTotal > 0)
        val childSizes = MutableList(children.size) { childTotal / children.size }
        childSizes[childSizes.lastIndex] += childTotal % children.size
        val trees = buildMap {
            put(root, rootBody)
            children.forEachIndexed { index, sha -> put(sha, paddedTree(sha, childSizes[index])) }
        }
        val engine = MockEngine { request ->
            val body = when (request.url.encodedPath) {
                "/repos/example/project" -> metadataBody
                "/repos/example/project/git/ref/heads/main" -> ref
                "/repos/example/project/git/commits/$commit" -> commitBody
                else -> trees.getValue(request.url.encodedPath.substringAfterLast('/'))
            }
            respond(body, HttpStatusCode.OK, JSON_HEADERS)
        }
        return GitHubRepositoryDiscoveryClient(engine)
            .preview("https://github.com/example/project")
            .discovery
    }

    private fun standardTreeEngine(
        commit: String,
        root: String,
        trees: Map<String, String>,
    ) = MockEngine { request ->
        val body = when (request.url.encodedPath) {
            "/repos/example/project" -> metadata(defaultBranch = "main")
            "/repos/example/project/git/ref/heads/main" ->
                """{"ref":"refs/heads/main","object":{"type":"commit","sha":"$commit"}}"""
            "/repos/example/project/git/commits/$commit" ->
                """{"sha":"$commit","tree":{"sha":"$root"}}"""
            else -> trees.getValue(request.url.encodedPath.substringAfterLast('/'))
        }
        respond(body, HttpStatusCode.OK, JSON_HEADERS)
    }

    private fun paddedMetadata(totalBytes: Int): String {
        val prefix = """{"id":123456789012345678,"name":"project","full_name":"example/project","private":false,"html_url":"https://github.com/example/project","default_branch":"main","owner":{"login":"example"},"padding":""""
        val suffix = "\"}"
        val padding = totalBytes - prefix.toByteArray().size - suffix.toByteArray().size
        require(padding >= 0)
        return prefix + "x".repeat(padding) + suffix
    }

    private fun paddedTree(sha: String, totalBytes: Int): String {
        val prefix = """{"sha":"$sha","truncated":false,"tree":[],"padding":""""
        val suffix = "\"}"
        val padding = totalBytes - prefix.toByteArray().size - suffix.toByteArray().size
        require(padding >= 0)
        return prefix + "x".repeat(padding) + suffix
    }

    private companion object {
        val JSON_HEADERS = headersOf(HttpHeaders.ContentType, "application/json")
    }
}
