package com.sanka1610.reprodroid.data.connection

import com.sanka1610.reprodroid.data.network.RunnerApiException
import com.sanka1610.reprodroid.data.network.RunnerResponseIntegrityException
import com.sanka1610.reprodroid.data.network.StrictJsonAuditor
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.readAvailable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

internal class RunnerPairingClient(
    private val endpoint: String,
    rootPin: String,
    expectedRootCertificateDer: ByteArray? = null,
) : AutoCloseable {
    private val trustManager = FixedRunnerTrustManager(
        endpointHost = ManualPairingPayloadParser.validateEndpoint(endpoint).host,
        rootPin = rootPin,
        expectedRootCertificateDer = expectedRootCertificateDer,
    )
    private val strictJson = Json { ignoreUnknownKeys = false; explicitNulls = false }
    private val client = HttpClient(CIO) {
        expectSuccess = false
        followRedirects = false
        engine {
            https { trustManager = this@RunnerPairingClient.trustManager }
        }
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 20_000
            socketTimeoutMillis = 20_000
        }
        install(ContentNegotiation) { json(strictJson) }
    }

    suspend fun identity(): PairingIdentityResponse {
        val response = client.get(url("/pairing/v1/identity"))
        if (response.status.value != 200) response.throwBoundedError()
        return response.strictBody<PairingIdentityResponse>().also {
            if (it.schemaVersion != 1 || !UUID.matches(it.runnerId)) invalidResponse()
        }
    }

    suspend fun create(request: CreatePairingRequest): PairingStatusResponse {
        val response = client.post(url("/pairing/v1/requests")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (response.status.value != 202) response.throwBoundedError()
        return response.strictBody<PairingStatusResponse>().validated(expectedRunnerId = request.runnerId)
    }

    suspend fun status(requestId: String, continuationCredential: String, expectedRunnerId: String): PairingStatusResponse {
        require(UUID.matches(requestId))
        val response = client.get(url("/pairing/v1/requests/$requestId")) {
            header(HttpHeaders.Authorization, "ReproDroid-Continuation $continuationCredential")
        }
        if (response.status.value != 200) response.throwBoundedError()
        return response.strictBody<PairingStatusResponse>().validated(expectedRunnerId, requestId)
    }

    suspend fun cancel(requestId: String, continuationCredential: String) {
        require(UUID.matches(requestId))
        val response = client.post(url("/pairing/v1/requests/$requestId/cancel")) {
            header(HttpHeaders.Authorization, "ReproDroid-Continuation $continuationCredential")
            contentType(ContentType.Application.Json)
            setBody(EmptyPairingRequest())
        }
        if (response.status.value != 204) response.throwBoundedError()
    }

    fun authenticatedRootDer(): ByteArray = trustManager.authenticatedRoot?.encoded?.clone()
        ?: throw RunnerResponseIntegrityException("Runner did not present an authenticated root certificate.")

    override fun close() = client.close()

    private fun url(path: String): String = endpoint.trimEnd('/') + path

    private suspend inline fun <reified T> HttpResponse.strictBody(): T {
        val text = boundedText()
        return try {
            StrictJsonAuditor(text, maximumDepth = 16, maximumStringBytes = 4 * 1024).audit()
            strictJson.decodeFromString(text)
        } catch (_: Exception) {
            invalidResponse()
        }
    }

    private suspend fun HttpResponse.throwBoundedError(): Nothing {
        // Consume a bounded body without reflecting it; pairing errors may contain no secrets.
        runCatching { boundedText() }
        throw RunnerApiException(status.value, "PAIRING_HTTP_${status.value}", "Runner pairing request was rejected (${status.value}).")
    }

    private suspend fun HttpResponse.boundedText(): String {
        headers[HttpHeaders.ContentLength]?.toLongOrNull()?.let {
            if (it > MAX_RESPONSE_BYTES) invalidResponse()
        }
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        val channel = bodyAsChannel()
        var total = 0
        while (!channel.isClosedForRead) {
            val count = channel.readAvailable(buffer, 0, buffer.size)
            if (count < 0) break
            total += count
            if (total > MAX_RESPONSE_BYTES) invalidResponse()
            if (count > 0) output.write(buffer, 0, count)
        }
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(output.toByteArray())).toString()
        } catch (_: Exception) {
            invalidResponse()
        }
    }

    private fun PairingStatusResponse.validated(expectedRunnerId: String, expectedRequestId: String? = null): PairingStatusResponse {
        if (
            schemaVersion != 1 || runnerId != expectedRunnerId || !UUID.matches(requestId) ||
            (expectedRequestId != null && requestId != expectedRequestId) ||
            state !in STATES || confirmationFingerprint.toByteArray().size !in 1..128 ||
            confirmationFingerprint.any(Char::isISOControl) ||
            !expiresAt.endsWith('Z') || runCatching { java.time.Instant.parse(expiresAt) }.isFailure ||
            (state == "APPROVED") != (principalId != null) ||
            (principalId != null && !UUID.matches(principalId))
        ) invalidResponse()
        return this
    }

    private fun invalidResponse(): Nothing = throw RunnerResponseIntegrityException("Runner pairing response is invalid.")

    companion object {
        private const val MAX_RESPONSE_BYTES = 64 * 1024
        private val UUID = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        private val STATES = setOf("PENDING_APPROVAL", "APPROVED", "REJECTED", "EXPIRED", "FAILED")
    }
}
