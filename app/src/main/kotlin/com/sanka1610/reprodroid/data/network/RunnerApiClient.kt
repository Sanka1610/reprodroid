package com.sanka1610.reprodroid.data.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.readAvailable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.net.URI

class RunnerApiException(
    val statusCode: Int,
    val errorCode: String,
    override val message: String,
) : RuntimeException(message)

class RunnerConfigurationException(message: String) : RuntimeException(message)

data class ArtifactDownloadResponse(
    val bytesWritten: Long,
    val contentLength: Long?,
    val etag: String?,
    val contentType: String?,
)

class RunnerApiClient(
    baseUrl: String,
    engine: HttpClientEngine? = null,
) {
    private val runnerBaseUrl = baseUrl.trim().trimEnd('/').also(::validateBaseUrl)
    private val client = if (engine == null) HttpClient(CIO) { configure() } else HttpClient(engine) { configure() }

    suspend fun createJob(request: CreateJobRequest): CreateJobResponse =
        client.post(endpoint("/v1/jobs")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.successBody()

    suspend fun getJob(jobId: String): JobResponse =
        client.get(endpoint("/v1/jobs/$jobId")).successBody()

    suspend fun getLogs(jobId: String, afterSequence: Long, limit: Int = 200): LogResponse =
        client.get(endpoint("/v1/jobs/$jobId/logs")) {
            url {
                parameters.append("afterSequence", afterSequence.toString())
                parameters.append("limit", limit.toString())
            }
        }.successBody()

    suspend fun confirmJob(jobId: String, request: ConfirmJobRequest) {
        client.post(endpoint("/v1/jobs/$jobId/confirm")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.ensureSuccess()
    }

    suspend fun cancelJob(jobId: String) {
        client.post(endpoint("/v1/jobs/$jobId/cancel")).ensureSuccess()
    }

    suspend fun retryJob(jobId: String): CreateJobResponse =
        client.post(endpoint("/v1/jobs/$jobId/retry")).successBody()

    suspend fun downloadArtifact(jobId: String, artifactId: String, destination: File): ArtifactDownloadResponse =
        client.prepareGet(endpoint("/v1/jobs/$jobId/artifacts/$artifactId/content")) {
            timeout {
                requestTimeoutMillis = DOWNLOAD_REQUEST_TIMEOUT_MILLIS
                socketTimeoutMillis = DOWNLOAD_SOCKET_TIMEOUT_MILLIS
            }
        }.execute { response ->
            response.ensureSuccess()
            val channel = response.bodyAsChannel()
            var bytesWritten = 0L
            FileOutputStream(destination, false).use { output ->
                val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
                while (!channel.isClosedForRead) {
                    val read = channel.readAvailable(buffer, 0, buffer.size)
                    if (read == -1) break
                    if (read > 0) {
                        output.write(buffer, 0, read)
                        bytesWritten += read
                    }
                }
                output.fd.sync()
            }
            ArtifactDownloadResponse(
                bytesWritten = bytesWritten,
                contentLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull(),
                etag = response.headers[HttpHeaders.ETag],
                contentType = response.headers[HttpHeaders.ContentType],
            )
        }

    private fun io.ktor.client.HttpClientConfig<*>.configure() {
        expectSuccess = false
        install(HttpTimeout) {
            connectTimeoutMillis = CONNECT_TIMEOUT_MILLIS
            requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS
            socketTimeoutMillis = SOCKET_TIMEOUT_MILLIS
        }
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    explicitNulls = false
                },
            )
        }
    }

    private suspend inline fun <reified T> HttpResponse.successBody(): T {
        ensureSuccess()
        return body()
    }

    private suspend fun HttpResponse.ensureSuccess() {
        if (status.value in 200..299) return
        val apiError = runCatching { body<ApiErrorResponse>() }.getOrNull()
        throw RunnerApiException(
            statusCode = status.value,
            errorCode = apiError?.code ?: "HTTP_${status.value}",
            message = apiError?.message ?: status.description,
        )
    }

    private fun endpoint(path: String): String {
        if (runnerBaseUrl.isBlank()) {
            throw RunnerConfigurationException("Runner base URL is not configured for this build.")
        }
        return runnerBaseUrl + path
    }

    private fun validateBaseUrl(baseUrl: String) {
        if (baseUrl.isBlank()) return
        val uri = runCatching { URI(baseUrl) }.getOrNull()
            ?: throw RunnerConfigurationException("Runner base URL is not a valid URI.")
        if (
            uri.scheme?.lowercase() !in setOf("http", "https") ||
            uri.host == null ||
            uri.userInfo != null ||
            uri.query != null ||
            uri.fragment != null ||
            uri.port == 0 ||
            uri.port > 65_535 ||
            (uri.path.isNotEmpty() && uri.path != "/")
        ) {
            throw RunnerConfigurationException(
                "Runner base URL must contain only an HTTP(S) scheme, host, and optional port.",
            )
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 5_000L
        const val REQUEST_TIMEOUT_MILLIS = 10_000L
        const val SOCKET_TIMEOUT_MILLIS = 10_000L
        const val DOWNLOAD_REQUEST_TIMEOUT_MILLIS = 30 * 60 * 1_000L
        const val DOWNLOAD_SOCKET_TIMEOUT_MILLIS = 30_000L
        const val DOWNLOAD_BUFFER_SIZE = 64 * 1_024
    }
}
