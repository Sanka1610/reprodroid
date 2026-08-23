package com.sanka1610.reprodroid.data.artifact

import com.sanka1610.reprodroid.data.provider.ReleaseAssetSelector
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.cancel
import io.ktor.utils.io.readAvailable
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.security.MessageDigest

class ReferenceAssetDownloadException(
    val code: String,
    override val message: String,
) : RuntimeException(message)

data class ReferenceAssetDownloadResult(
    val bytesWritten: Long,
    val computedSha256: String,
    val responseEtag: String?,
    val finalHost: String,
)

class GitHubAssetDownloader(engine: HttpClientEngine? = null) {
    private val client = if (engine == null) HttpClient(Android) { configure() } else HttpClient(engine) { configure() }

    suspend fun download(
        stableAssetUrl: String,
        expectedSizeBytes: Long,
        expectedProviderSha256: String?,
        destinationPart: File,
    ): ReferenceAssetDownloadResult {
        if (expectedSizeBytes !in 1..ReleaseAssetSelector.MAX_ASSET_SIZE_BYTES) {
            fail("INVALID_EXPECTED_SIZE", "Release metadata size is outside the supported range.")
        }
        var currentUri = validateUri(stableAssetUrl, initial = true)
        repeat(MAX_REDIRECTS + 1) { requestIndex ->
            val response = client.get(currentUri.toASCIIString())
            if (response.status in REDIRECT_STATUSES) {
                response.bodyAsChannel().cancel()
                if (requestIndex == MAX_REDIRECTS) fail("TOO_MANY_REDIRECTS", "Asset download exceeded 5 redirects.")
                val location = response.headers[HttpHeaders.Location]
                    ?: fail("INVALID_REDIRECT", "Asset redirect did not provide a Location header.")
                currentUri = validateUri(currentUri.resolve(location).toASCIIString(), initial = false)
                return@repeat
            }
            if (response.status.value !in 200..299) {
                response.bodyAsChannel().cancel()
                fail("DOWNLOAD_HTTP_${response.status.value}", "Asset download failed with HTTP ${response.status.value}.")
            }
            return streamResponse(response, currentUri, expectedSizeBytes, expectedProviderSha256, destinationPart)
        }
        fail("TOO_MANY_REDIRECTS", "Asset download exceeded 5 redirects.")
    }

    private suspend fun streamResponse(
        response: HttpResponse,
        finalUri: URI,
        expectedSizeBytes: Long,
        expectedProviderSha256: String?,
        destinationPart: File,
    ): ReferenceAssetDownloadResult {
        val contentLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
            ?: fail("MISSING_CONTENT_LENGTH", "Asset response did not provide a valid Content-Length.")
        if (contentLength != expectedSizeBytes) {
            fail("CONTENT_LENGTH_MISMATCH", "Asset Content-Length differs from GitHub release metadata.")
        }
        val contentType = response.headers[HttpHeaders.ContentType]?.substringBefore(';')?.trim()
        if (!contentType.equals(APK_CONTENT_TYPE, ignoreCase = true)) {
            fail("UNEXPECTED_CONTENT_TYPE", "Asset response is not an Android package MIME type.")
        }
        destinationPart.parentFile?.let { parent ->
            if ((!parent.exists() && !parent.mkdirs()) || !parent.isDirectory) {
                fail("STORAGE_UNAVAILABLE", "Reference APK storage directory could not be created.")
            }
        }
        val digest = MessageDigest.getInstance("SHA-256")
        var bytesWritten = 0L
        val channel = response.bodyAsChannel()
        FileOutputStream(destinationPart, false).use { output ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (!channel.isClosedForRead) {
                val read = channel.readAvailable(buffer, 0, buffer.size)
                if (read == -1) break
                if (read > 0) {
                    bytesWritten += read
                    if (bytesWritten > expectedSizeBytes || bytesWritten > ReleaseAssetSelector.MAX_ASSET_SIZE_BYTES) {
                        channel.cancel()
                        fail("DOWNLOAD_SIZE_LIMIT", "Asset exceeded the declared or configured size limit.")
                    }
                    output.write(buffer, 0, read)
                    digest.update(buffer, 0, read)
                }
            }
            output.fd.sync()
        }
        if (bytesWritten != expectedSizeBytes) {
            fail("RECEIVED_SIZE_MISMATCH", "Received asset size differs from GitHub release metadata.")
        }
        val computedSha256 = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
        if (expectedProviderSha256 != null && !computedSha256.equals(expectedProviderSha256, ignoreCase = true)) {
            fail("PROVIDER_DIGEST_MISMATCH", "Downloaded asset SHA-256 differs from GitHub's digest.")
        }
        return ReferenceAssetDownloadResult(
            bytesWritten = bytesWritten,
            computedSha256 = computedSha256,
            responseEtag = response.headers[HttpHeaders.ETag],
            finalHost = finalUri.host.lowercase(),
        )
    }

    private fun validateUri(value: String, initial: Boolean): URI {
        val uri = runCatching { URI(value) }.getOrNull()
            ?: fail("INVALID_DOWNLOAD_URL", "Asset URL is invalid.")
        val host = uri.host?.lowercase()
        val allowedHosts = if (initial) INITIAL_HOSTS else REDIRECT_HOSTS
        if (
            uri.scheme?.lowercase() != "https" ||
            host !in allowedHosts ||
            uri.userInfo != null ||
            uri.port != -1 ||
            uri.fragment != null ||
            (initial && uri.query != null)
        ) {
            fail("UNSAFE_DOWNLOAD_URL", "Asset URL or redirect violates the HTTPS host policy.")
        }
        return uri
    }

    private fun io.ktor.client.HttpClientConfig<*>.configure() {
        expectSuccess = false
        followRedirects = false
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 30 * 60 * 1_000L
            socketTimeoutMillis = 30_000
        }
    }

    private fun fail(code: String, message: String): Nothing = throw ReferenceAssetDownloadException(code, message)

    private companion object {
        const val MAX_REDIRECTS = 5
        const val BUFFER_SIZE = 64 * 1024
        const val APK_CONTENT_TYPE = "application/vnd.android.package-archive"
        val INITIAL_HOSTS = setOf("github.com")
        val REDIRECT_HOSTS = setOf("github.com", "release-assets.githubusercontent.com")
        val REDIRECT_STATUSES = setOf(
            HttpStatusCode.MovedPermanently,
            HttpStatusCode.Found,
            HttpStatusCode.SeeOther,
            HttpStatusCode.TemporaryRedirect,
            HttpStatusCode.PermanentRedirect,
        )
    }
}
