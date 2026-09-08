package com.sanka1610.reprodroid.data.artifact

import com.sanka1610.reprodroid.data.provider.CodebergRepository
import com.sanka1610.reprodroid.data.provider.ReleaseAssetSelector
import com.sanka1610.reprodroid.data.provider.isExactCodebergReleaseDownloadUrl
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

/** The release identity which must be bound to the initial download URL. */
data class CodebergAssetDownloadPolicy(
    val repository: CodebergRepository,
    val tagName: String,
    val assetName: String,
)

class CodebergAssetDownloader(engine: HttpClientEngine? = null) {
    private val client = if (engine == null) HttpClient(Android) { configure() } else HttpClient(engine) { configure() }

    suspend fun download(
        stableAssetUrl: String,
        expectedSizeBytes: Long,
        expectedProviderSha256: String?,
        destinationPart: File,
        policy: CodebergAssetDownloadPolicy,
    ): ReferenceAssetDownloadResult {
        if (expectedSizeBytes !in 1..ReleaseAssetSelector.MAX_ASSET_SIZE_BYTES) {
            fail("INVALID_EXPECTED_SIZE", "Release metadata size is outside the supported range.")
        }
        val currentUri = validateUri(stableAssetUrl, policy)
        val response = client.get(currentUri.toASCIIString())
        if (response.status.value in 300..399) {
            response.bodyAsChannel().cancel()
            fail("REDIRECT_NOT_ALLOWED", "Codeberg asset downloads must not follow redirects.")
        }
        if (response.status.value !in 200..299) {
            response.bodyAsChannel().cancel()
            fail("DOWNLOAD_HTTP_${response.status.value}", "Asset download failed with HTTP ${response.status.value}.")
        }
        return streamResponse(response, currentUri, expectedSizeBytes, expectedProviderSha256, destinationPart)
    }

    private suspend fun streamResponse(
        response: HttpResponse,
        finalUri: URI,
        expectedSizeBytes: Long,
        expectedProviderSha256: String?,
        destinationPart: File,
    ): ReferenceAssetDownloadResult {
        response.headers[HttpHeaders.ContentLength]?.let { rawContentLength ->
            val contentLength = rawContentLength.toLongOrNull()
                ?: fail("INVALID_CONTENT_LENGTH", "Asset response did not provide a valid Content-Length.")
            if (contentLength != expectedSizeBytes) {
                fail("CONTENT_LENGTH_MISMATCH", "Asset Content-Length differs from Codeberg release metadata.")
            }
        }
        val contentType = response.headers[HttpHeaders.ContentType]
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase()
        if (contentType !in ALLOWED_CONTENT_TYPES) {
            fail("UNEXPECTED_CONTENT_TYPE", "Asset response does not have an allowed APK MIME type.")
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
            fail("RECEIVED_SIZE_MISMATCH", "Received asset size differs from Codeberg release metadata.")
        }
        val computedSha256 = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
        if (expectedProviderSha256 != null && !computedSha256.equals(expectedProviderSha256, ignoreCase = true)) {
            fail("PROVIDER_DIGEST_MISMATCH", "Downloaded asset SHA-256 differs from Codeberg's digest.")
        }
        return ReferenceAssetDownloadResult(
            bytesWritten = bytesWritten,
            computedSha256 = computedSha256,
            responseEtag = response.headers[HttpHeaders.ETag],
            finalHost = finalUri.host.lowercase(),
            downloadContentType = contentType,
        )
    }

    private fun validateUri(value: String, policy: CodebergAssetDownloadPolicy): URI {
        val uri = runCatching { URI(value) }.getOrNull()
            ?: fail("INVALID_DOWNLOAD_URL", "Asset URL is invalid.")
        val host = uri.host?.lowercase()
        if (
            !isExactCodebergReleaseDownloadUrl(
                policy.repository,
                policy.tagName,
                policy.assetName,
                uri.toASCIIString(),
            )
        ) {
            fail("UNSAFE_DOWNLOAD_URL", "Asset URL is not bound to the verified Codeberg release asset identity.")
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
        const val BUFFER_SIZE = 64 * 1024
        const val HOST = "codeberg.org"
        val ALLOWED_CONTENT_TYPES = setOf(
            "application/vnd.android.package-archive",
            "application/octet-stream",
        )
    }
}
