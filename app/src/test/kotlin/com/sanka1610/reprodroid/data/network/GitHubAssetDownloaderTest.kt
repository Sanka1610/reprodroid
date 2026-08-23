package com.sanka1610.reprodroid.data.artifact

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.security.MessageDigest
import java.nio.file.Files

class GitHubAssetDownloaderTest {
    @Test
    fun `download follows an allowed HTTPS CDN redirect and verifies digest`() = runBlocking {
        val bytes = "signed-apk-placeholder".toByteArray()
        val sha256 = bytes.sha256()
        var requests = 0
        val engine = MockEngine { request ->
            requests++
            when (request.url.host) {
                "github.com" -> respond(
                    content = "",
                    status = HttpStatusCode.Found,
                    headers = headersOf(
                        HttpHeaders.Location,
                        "https://release-assets.githubusercontent.com/object?signature=temporary",
                    ),
                )
                "release-assets.githubusercontent.com" -> respond(
                    content = bytes,
                    status = HttpStatusCode.OK,
                    headers = headersOf(
                        HttpHeaders.ContentType to listOf("application/vnd.android.package-archive"),
                        HttpHeaders.ContentLength to listOf(bytes.size.toString()),
                        HttpHeaders.ETag to listOf("\"opaque-cache-validator\""),
                    ),
                )
                else -> error("Unexpected host: ${request.url.host}")
            }
        }
        val destination = Files.createTempFile("github-asset", ".part.apk").toFile()
        try {
            val result = GitHubAssetDownloader(engine).download(
                stableAssetUrl = "https://github.com/example/app/releases/download/1/app.apk",
                expectedSizeBytes = bytes.size.toLong(),
                expectedProviderSha256 = sha256,
                destinationPart = destination,
            )
            assertEquals(2, requests)
            assertEquals(sha256, result.computedSha256)
            assertEquals("release-assets.githubusercontent.com", result.finalHost)
            assertEquals("\"opaque-cache-validator\"", result.responseEtag)
            assertArrayEquals(bytes, destination.readBytes())
        } finally {
            destination.delete()
        }
    }

    @Test
    fun `download rejects HTTP downgrade redirect`() {
        val engine = MockEngine {
            respond(
                content = "",
                status = HttpStatusCode.Found,
                headers = headersOf(HttpHeaders.Location, "http://release-assets.githubusercontent.com/object"),
            )
        }
        val destination = Files.createTempFile("github-asset", ".part.apk").toFile()
        try {
            assertThrows(ReferenceAssetDownloadException::class.java) {
                runBlocking {
                    GitHubAssetDownloader(engine).download(
                        stableAssetUrl = "https://github.com/example/app/releases/download/1/app.apk",
                        expectedSizeBytes = 10,
                        expectedProviderSha256 = null,
                        destinationPart = destination,
                    )
                }
            }
        } finally {
            destination.delete()
        }
    }

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
