package com.sanka1610.reprodroid.data.connection

import com.sanka1610.reprodroid.data.network.StrictJsonAuditor
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64

class PairingPayloadException(message: String) : IllegalArgumentException(message)

@Serializable
class ManualPairingPayload(
    val schemaVersion: Int,
    val endpoint: String,
    val runnerId: String,
    val rootSpkiSha256: String,
    val invitationId: String,
    val invitationSecret: String,
    val expiresAt: String,
) {
    override fun toString(): String = "ManualPairingPayload(schemaVersion=$schemaVersion, endpoint=$endpoint, runnerId=$runnerId, rootSpkiSha256=$rootSpkiSha256, invitationId=$invitationId, invitationSecret=[REDACTED], expiresAt=$expiresAt)"
}

@Serializable
internal data class PairingIdentityResponse(val schemaVersion: Int, val runnerId: String)

@Serializable
internal class CreatePairingRequest(
    val schemaVersion: Int,
    val runnerId: String,
    val invitationId: String,
    val invitationSecret: String,
    val deviceDisplayName: String,
    val tokenId: String,
    val tokenSha256: String,
    val continuationId: String,
    val continuationSha256: String,
) {
    override fun toString(): String = "CreatePairingRequest(schemaVersion=$schemaVersion, runnerId=$runnerId, invitationId=$invitationId, invitationSecret=[REDACTED], deviceDisplayName=$deviceDisplayName, tokenId=$tokenId, tokenSha256=$tokenSha256, continuationId=$continuationId, continuationSha256=$continuationSha256)"
}

@Serializable
internal data class PairingStatusResponse(
    val schemaVersion: Int,
    val runnerId: String,
    val requestId: String,
    val state: String,
    val expiresAt: String,
    val confirmationFingerprint: String,
    val principalId: String? = null,
)

@Serializable
internal class EmptyPairingRequest

@Serializable
internal data class SelfRevokeResponse(
    val schemaVersion: Int,
    val runnerId: String,
    val principalId: String,
    val state: String,
    val revokedAt: String,
)

object ManualPairingPayloadParser {
    private const val MAX_DECODED_BYTES = 4 * 1024
    private val json = Json { ignoreUnknownKeys = false; explicitNulls = false }
    private val uuid = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    private val invitationSecret = Regex("[A-Za-z0-9_-]{43}")
    private val pin = Regex("sha256/[A-Za-z0-9+/]{43}=")

    fun parse(encoded: String, clock: Clock = Clock.systemUTC()): ManualPairingPayload {
        val compact = encoded.trim()
        if (compact.isEmpty() || compact.length > 5_464 || compact.any(Char::isWhitespace)) {
            throw PairingPayloadException("Pairing payload is empty or exceeds the supported size.")
        }
        val decoded = try {
            Base64.getUrlDecoder().decode(compact)
        } catch (_: IllegalArgumentException) {
            throw PairingPayloadException("Pairing payload is not canonical Base64url.")
        }
        if (decoded.size > MAX_DECODED_BYTES || Base64.getUrlEncoder().withoutPadding().encodeToString(decoded) != compact) {
            throw PairingPayloadException("Pairing payload is not canonical Base64url or exceeds 4 KiB.")
        }
        val text = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(decoded))
                .toString()
        } catch (_: Exception) {
            throw PairingPayloadException("Pairing payload is not strict UTF-8.")
        }
        val payload = try {
            StrictJsonAuditor(text, maximumDepth = 16, maximumStringBytes = 4 * 1024).audit()
            json.decodeFromString<ManualPairingPayload>(text)
        } catch (_: Exception) {
            throw PairingPayloadException("Pairing payload does not match schema version 1.")
        }
        if (payload.schemaVersion != 1 || !uuid.matches(payload.runnerId) || !uuid.matches(payload.invitationId)) {
            throw PairingPayloadException("Pairing payload identity fields are invalid.")
        }
        if (!canonicalSecret(payload.invitationSecret) || !canonicalPin(payload.rootSpkiSha256)) {
            throw PairingPayloadException("Pairing payload secret or root pin is invalid.")
        }
        validateEndpoint(payload.endpoint)
        val expiry = runCatching { Instant.parse(payload.expiresAt) }.getOrNull()
            ?: throw PairingPayloadException("Pairing payload expiry is invalid.")
        val now = clock.instant()
        if (!expiry.isAfter(now) || expiry.isAfter(now.plus(Duration.ofMinutes(10)))) {
            throw PairingPayloadException("Pairing invitation is expired or has an invalid lifetime.")
        }
        return payload
    }

    private fun canonicalSecret(value: String): Boolean = invitationSecret.matches(value) && runCatching {
        val decoded = Base64.getUrlDecoder().decode(value)
        decoded.size == 32 && Base64.getUrlEncoder().withoutPadding().encodeToString(decoded) == value
    }.getOrDefault(false)

    private fun canonicalPin(value: String): Boolean = pin.matches(value) && runCatching {
        val encoded = value.removePrefix("sha256/")
        val decoded = Base64.getDecoder().decode(encoded)
        decoded.size == 32 && Base64.getEncoder().encodeToString(decoded) == encoded
    }.getOrDefault(false)

    fun validateEndpoint(endpoint: String): URI {
        val uri = runCatching { URI(endpoint) }.getOrNull()
            ?: throw PairingPayloadException("Runner endpoint is not a valid URI.")
        if (
            uri.scheme?.lowercase() != "https" || uri.host == null || uri.port !in 1..65_535 ||
            uri.userInfo != null || uri.query != null || uri.fragment != null ||
            (uri.path.isNotEmpty() && uri.path != "/") || endpoint != endpoint.trim().trimEnd('/')
        ) {
            throw PairingPayloadException("Runner endpoint must contain only HTTPS, host, and an explicit port.")
        }
        return uri
    }
}
