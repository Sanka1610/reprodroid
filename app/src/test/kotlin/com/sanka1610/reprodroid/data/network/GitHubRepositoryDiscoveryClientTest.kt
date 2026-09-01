package com.sanka1610.reprodroid.data.provider

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

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

    private companion object {
        val JSON_HEADERS = headersOf(HttpHeaders.ContentType, "application/json")
    }
}
