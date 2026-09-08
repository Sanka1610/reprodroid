package com.sanka1610.reprodroid.data.provider

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CodebergRepositoryDiscoveryClientTest {
    @Test
    fun `discovery follows plural refs and completes every paginated tree page`() = runBlocking {
        val commit = "1".repeat(40)
        val root = "2".repeat(40)
        val blob = "3".repeat(40)
        val requests = mutableListOf<String>()
        val engine = MockEngine { request ->
            requests += request.url.encodedPath +
                request.url.parameters["page"]?.let {
                    "?page=$it&per_page=${request.url.parameters["per_page"]}"
                }.orEmpty()
            val body = when (request.url.encodedPath) {
                "/api/v1/repos/example/project" -> repositoryJson()
                "/api/v1/repos/example/project/git/refs/heads/main" ->
                    """[{"ref":"refs/heads/main","object":{"type":"commit","sha":"$commit"}}]"""
                "/api/v1/repos/example/project/git/commits/$commit" ->
                    """{"sha":"$commit","tree":{"sha":"$root"}}"""
                "/api/v1/repos/example/project/git/trees/$root" -> when (request.url.parameters["page"]) {
                    "1" -> """{"sha":"$root","truncated":true,"page":1,"total_count":2,
                        "tree":[{"path":"build.gradle","mode":"100644","type":"blob","sha":"$blob"}]}"""
                    "2" -> """{"sha":"$root","truncated":true,"page":2,"total_count":2,
                        "tree":[{"path":"settings.gradle.kts","mode":"100644","type":"blob","sha":"$blob"}]}"""
                    else -> error("Unexpected root page ${request.url.parameters["page"]}")
                }
                else -> error("Unexpected request: ${request.url}")
            }
            respond(body, HttpStatusCode.OK, JSON_HEADERS)
        }

        val preview = CodebergRepositoryDiscoveryClient(engine)
            .preview("https://codeberg.org/example/project")

        assertEquals("123456789012345678", preview.identity.providerRepositoryId)
        assertEquals("COMPLETE", preview.discovery.state)
        assertEquals(
            listOf("build.gradle", "settings.gradle.kts"),
            preview.discovery.candidates.map { it.relativePath },
        )
        assertTrue(requests.any { it.contains("git/refs/heads/main") })
        assertTrue(requests.any { it.contains("git/trees/$root") && it.contains("page=1&per_page=50") })
        assertTrue(requests.any { it.contains("git/trees/$root") && it.contains("page=2&per_page=50") })
        assertTrue(requests.all { !it.contains("page=3") })
    }

    @Test
    fun `discovery continues while exact total is not reached even when truncated is false`() = runBlocking {
        val commit = "1".repeat(40)
        val root = "2".repeat(40)
        val firstBlob = "3".repeat(40)
        val secondBlob = "4".repeat(40)
        val requestedPages = mutableListOf<String?>()
        val engine = MockEngine { request ->
            val body = when (request.url.encodedPath) {
                "/api/v1/repos/example/project" -> repositoryJson()
                "/api/v1/repos/example/project/git/refs/heads/main" ->
                    """[{"ref":"refs/heads/main","object":{"type":"commit","sha":"$commit"}}]"""
                "/api/v1/repos/example/project/git/commits/$commit" ->
                    """{"sha":"$commit","tree":{"sha":"$root"}}"""
                "/api/v1/repos/example/project/git/trees/$root" -> {
                    requestedPages += request.url.parameters["page"]
                    when (request.url.parameters["page"]) {
                        "1" -> """{"sha":"$root","truncated":false,"page":1,"total_count":2,
                            "tree":[{"path":"build.gradle","mode":"100644","type":"blob","sha":"$firstBlob"}]}"""
                        "2" -> """{"sha":"$root","truncated":true,"page":2,"total_count":2,
                            "tree":[{"path":"settings.gradle.kts","mode":"100644","type":"blob","sha":"$secondBlob"}]}"""
                        else -> error("Unexpected root page ${request.url.parameters["page"]}")
                    }
                }
                else -> error("Unexpected request: ${request.url}")
            }
            respond(body, HttpStatusCode.OK, JSON_HEADERS)
        }

        val preview = CodebergRepositoryDiscoveryClient(engine)
            .preview("https://codeberg.org/example/project")

        assertEquals("COMPLETE", preview.discovery.state)
        assertEquals(listOf("1", "2"), requestedPages)
        assertEquals(
            listOf("build.gradle", "settings.gradle.kts"),
            preview.discovery.candidates.map { it.relativePath },
        )
    }

    @Test
    fun `discovery rejects an echoed per-page value above the requested bound`() {
        val preview = previewForTree(
            """{"sha":"${"2".repeat(40)}","truncated":false,"page":1,"per_page":51,
                "total_count":0,"tree":[]}""",
        )

        assertEquals("FAILED", preview.discovery.state)
        assertEquals("INVALID_METADATA", preview.discovery.reason)
        assertTrue(preview.discovery.diagnostic.orEmpty().contains("pagination metadata"))
    }

    @Test
    fun `discovery rejects a page above the requested bound when per-page is omitted`() {
        val entries = (0..50).joinToString(",") { index ->
            """{"path":"file-$index","mode":"100644","type":"blob","sha":"${"3".repeat(40)}"}"""
        }
        val preview = previewForTree(
            """{"sha":"${"2".repeat(40)}","truncated":false,"page":1,
                "total_count":51,"tree":[$entries]}""",
        )

        assertEquals("FAILED", preview.discovery.state)
        assertEquals("INVALID_METADATA", preview.discovery.reason)
        assertTrue(preview.discovery.diagnostic.orEmpty().contains("exceeds per_page"))
    }

    @Test
    fun `discovery rejects more entries than the stable total count`() {
        val preview = previewForTree(
            """{"sha":"${"2".repeat(40)}","truncated":false,"page":1,"total_count":1,
                "tree":[
                  {"path":"one","mode":"100644","type":"blob","sha":"${"3".repeat(40)}"},
                  {"path":"two","mode":"100644","type":"blob","sha":"${"4".repeat(40)}"}
                ]}""",
        )

        assertEquals("FAILED", preview.discovery.state)
        assertEquals("INVALID_METADATA", preview.discovery.reason)
        assertTrue(preview.discovery.diagnostic.orEmpty().contains("more entries than total_count"))
    }

    @Test
    fun `discovery accepts an empty tree without an echoed per-page value`() {
        val preview = previewForTree(
            """{"sha":"${"2".repeat(40)}","truncated":false,"page":1,"total_count":0,"tree":[]}""",
        )

        assertEquals("COMPLETE", preview.discovery.state)
        assertTrue(preview.discovery.candidates.isEmpty())
    }

    @Test
    fun `discovery rejects repositories that do not advertise SHA-1`() = runBlocking {
        val engine = MockEngine {
            respond(repositoryJson(objectFormat = "sha256"), HttpStatusCode.OK, JSON_HEADERS)
        }

        val failure = org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                CodebergRepositoryDiscoveryClient(engine).preview("https://codeberg.org/example/project")
            }
        }
        assertTrue(failure.message.orEmpty().contains("SHA-1"))
    }

    private fun repositoryJson(objectFormat: String = "sha1"): String = """
        {
          "id":123456789012345678,
          "name":"project",
          "full_name":"example/project",
          "private":false,
          "html_url":"https://codeberg.org/example/project",
          "default_branch":"main",
          "object_format_name":"$objectFormat",
          "owner":{"login":"example"}
        }
    """.trimIndent()

    private fun previewForTree(treeJson: String): RepositoryRegistrationPreview {
        val commit = "1".repeat(40)
        val root = "2".repeat(40)
        val engine = MockEngine { request ->
            val body = when (request.url.encodedPath) {
                "/api/v1/repos/example/project" -> repositoryJson()
                "/api/v1/repos/example/project/git/refs/heads/main" ->
                    """[{"ref":"refs/heads/main","object":{"type":"commit","sha":"$commit"}}]"""
                "/api/v1/repos/example/project/git/commits/$commit" ->
                    """{"sha":"$commit","tree":{"sha":"$root"}}"""
                "/api/v1/repos/example/project/git/trees/$root" -> treeJson
                else -> error("Unexpected request: ${request.url}")
            }
            respond(body, HttpStatusCode.OK, JSON_HEADERS)
        }
        return runBlocking {
            CodebergRepositoryDiscoveryClient(engine).preview("https://codeberg.org/example/project")
        }
    }

    private companion object {
        val JSON_HEADERS = headersOf(HttpHeaders.ContentType, "application/json")
    }
}
