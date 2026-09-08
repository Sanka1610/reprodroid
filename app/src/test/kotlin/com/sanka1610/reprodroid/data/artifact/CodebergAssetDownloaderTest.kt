package com.sanka1610.reprodroid.data.artifact

import com.sanka1610.reprodroid.data.provider.CodebergRepository
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
import java.nio.file.Files
import java.security.MessageDigest

class CodebergAssetDownloaderTest {
    @Test
    fun `download rejects redirects and returns the normalized response MIME`() = runBlocking {
        val bytes = "apk-placeholder".toByteArray()
        val sha256 = bytes.sha256()
        var requests = 0
        val engine = MockEngine {
            requests++
            respond(
                content = bytes,
                status = HttpStatusCode.OK,
                headers = headersOf(
                    HttpHeaders.ContentType to listOf("Application/Octet-Stream; charset=binary"),
                    HttpHeaders.ContentLength to listOf(bytes.size.toString()),
                ),
            )
        }
        val destination = Files.createTempFile("codeberg-asset", ".part.apk").toFile()
        try {
            val result = CodebergAssetDownloader(engine).download(
                stableAssetUrl = "https://codeberg.org/example/project/releases/download/v1/project.apk",
                expectedSizeBytes = bytes.size.toLong(),
                expectedProviderSha256 = sha256,
                destinationPart = destination,
                policy = CodebergAssetDownloadPolicy(
                    repository = CodebergRepository("example", "project"),
                    tagName = "v1",
                    assetName = "project.apk",
                ),
            )
            assertEquals(1, requests)
            assertEquals("application/octet-stream", result.downloadContentType)
            assertEquals(sha256, result.computedSha256)
            assertArrayEquals(bytes, destination.readBytes())
        } finally {
            destination.delete()
        }

        val redirectEngine = MockEngine {
            requests++
            respond(
                content = "",
                status = HttpStatusCode.Found,
                headers = headersOf(HttpHeaders.Location, "https://codeberg.org/other/path"),
            )
        }
        val redirectDestination = Files.createTempFile("codeberg-redirect", ".part.apk").toFile()
        try {
            val failure = assertThrows(ReferenceAssetDownloadException::class.java) {
                runBlocking {
                    CodebergAssetDownloader(redirectEngine).download(
                        stableAssetUrl = "https://codeberg.org/example/project/releases/download/v1/project.apk",
                        expectedSizeBytes = bytes.size.toLong(),
                        expectedProviderSha256 = sha256,
                        destinationPart = redirectDestination,
                        policy = CodebergAssetDownloadPolicy(
                            repository = CodebergRepository("example", "project"),
                            tagName = "v1",
                            assetName = "project.apk",
                        ),
                    )
                }
            }
            assertEquals("REDIRECT_NOT_ALLOWED", failure.code)
        } finally {
            redirectDestination.delete()
        }
    }

    @Test
    fun `download accepts provider display casing for the verified repository identity`() = runBlocking {
        val bytes = "apk-placeholder".toByteArray()
        var requests = 0
        val engine = MockEngine {
            requests++
            respond(
                content = bytes,
                status = HttpStatusCode.OK,
                headers = headersOf(
                    HttpHeaders.ContentType to listOf("application/vnd.android.package-archive"),
                    HttpHeaders.ContentLength to listOf(bytes.size.toString()),
                ),
            )
        }
        val destination = Files.createTempFile("codeberg-display-case", ".part.apk").toFile()
        try {
            val result = CodebergAssetDownloader(engine).download(
                stableAssetUrl = "https://codeberg.org/UnifiedPush/Android-Example/releases/download/v1/project.apk",
                expectedSizeBytes = bytes.size.toLong(),
                expectedProviderSha256 = null,
                destinationPart = destination,
                policy = CodebergAssetDownloadPolicy(
                    repository = CodebergRepository("unifiedpush", "android-example"),
                    tagName = "v1",
                    assetName = "project.apk",
                ),
            )

            assertEquals(1, requests)
            assertEquals(bytes.size.toLong(), result.bytesWritten)
        } finally {
            destination.delete()
        }
    }

    @Test
    fun `download binds the exact repository release asset path before making a request`() {
        var requests = 0
        val engine = MockEngine {
            requests++
            respond(
                content = "unused",
                status = HttpStatusCode.OK,
                headers = headersOf(
                    HttpHeaders.ContentType to listOf("application/vnd.android.package-archive"),
                    HttpHeaders.ContentLength to listOf("6"),
                ),
            )
        }
        val destination = Files.createTempFile("codeberg-bound", ".part.apk").toFile()
        try {
            val failure = assertThrows(ReferenceAssetDownloadException::class.java) {
                runBlocking {
                    CodebergAssetDownloader(engine).download(
                        stableAssetUrl = "https://codeberg.org/example/project/releases/download/v1/other.apk",
                        expectedSizeBytes = 6,
                        expectedProviderSha256 = null,
                        destinationPart = destination,
                        policy = CodebergAssetDownloadPolicy(
                            repository = CodebergRepository("example", "project"),
                            tagName = "v1",
                            assetName = "project.apk",
                        ),
                    )
                }
            }
            assertEquals("UNSAFE_DOWNLOAD_URL", failure.code)
            assertEquals(0, requests)
        } finally {
            destination.delete()
        }
    }

    @Test
    fun `download accepts an omitted Content-Length but rejects encoded path separators`() = runBlocking {
        val bytes = "apk-placeholder".toByteArray()
        var requests = 0
        val engine = MockEngine {
            requests++
            respond(
                bytes,
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/vnd.android.package-archive"),
            )
        }
        val destination = Files.createTempFile("codeberg-no-length", ".part.apk").toFile()
        val policy = CodebergAssetDownloadPolicy(CodebergRepository("example", "project"), "v1", "project.apk")
        try {
            val result = CodebergAssetDownloader(engine).download(
                "https://codeberg.org/example/project/releases/download/v1/project.apk",
                bytes.size.toLong(),
                null,
                destination,
                policy,
            )
            assertEquals(bytes.size.toLong(), result.bytesWritten)
            val failure = assertThrows(ReferenceAssetDownloadException::class.java) {
                runBlocking {
                    CodebergAssetDownloader(engine).download(
                        "https://codeberg.org/example/project/releases/download/v1%2Fescape/project.apk",
                        bytes.size.toLong(),
                        null,
                        destination,
                        policy,
                    )
                }
            }
            assertEquals("UNSAFE_DOWNLOAD_URL", failure.code)
            assertEquals(1, requests)
        } finally {
            destination.delete()
        }
    }

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
