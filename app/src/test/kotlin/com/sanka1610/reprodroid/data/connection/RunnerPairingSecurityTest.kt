package com.sanka1610.reprodroid.data.connection

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64

/**
 * Contract tests for the Android-side Phase 4.6 trust boundary.
 *
 * These are deliberately JVM tests: parsing and certificate verification must
 * reject hostile input before a network request is attempted.  The certificate
 * fixtures are real ECDSA P-256 X.509 certificates, rather than a fake
 * X509Certificate implementation, so signature, SAN, EKU, key usage, and pin
 * checks exercise the platform certificate parser and verifier.
 */
class RunnerPairingSecurityTest {
    private val now = Instant.parse("2026-09-08T00:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test
    fun createRequestAlwaysSerializesRequiredSchemaVersion() {
        val request = CreatePairingRequest(
            schemaVersion = 1,
            runnerId = RUNNER_ID,
            invitationId = INVITATION_ID,
            invitationSecret = "A".repeat(43),
            deviceDisplayName = "Android device",
            tokenId = "33333333-3333-4333-8333-333333333333",
            tokenSha256 = "a".repeat(64),
            continuationId = "44444444-4444-4444-8444-444444444444",
            continuationSha256 = "b".repeat(64),
        )

        assertTrue(Json.encodeToString(request).contains("\"schemaVersion\":1"))
    }

    @Test
    fun validManualPayloadIsDecodedOnlyWhenCanonicalAndUnexpired() {
        val payload = ManualPairingPayloadParser.parse(validEncodedPayload(), clock)

        assertEquals(1, payload.schemaVersion)
        assertEquals("https://runner.example:8443", payload.endpoint)
        assertEquals(RUNNER_ID, payload.runnerId)
        assertEquals(INVITATION_ID, payload.invitationId)
        assertEquals("A".repeat(43), payload.invitationSecret)
        assertEquals("2026-09-08T00:05:00Z", payload.expiresAt)
    }

    @Test
    fun manualPayloadRejectsUnknownAndDuplicateFieldsBeforeDeserialization() {
        val unknown = jsonPayload(extra = ",\"unexpected\":true")
        assertThrows(PairingPayloadException::class.java) {
            ManualPairingPayloadParser.parse(encode(unknown), clock)
        }

        val duplicate = jsonPayload().replace(
            "\"schemaVersion\":1,",
            "\"schemaVersion\":1,\"schemaVersion\":1,",
        )
        assertThrows(PairingPayloadException::class.java) {
            ManualPairingPayloadParser.parse(encode(duplicate), clock)
        }
    }

    @Test
    fun manualPayloadRejectsNonCanonicalBase64AndInvalidUtf8() {
        val canonical = validEncodedPayload()
        assertThrows(PairingPayloadException::class.java) {
            ManualPairingPayloadParser.parse("$canonical=", clock)
        }
        assertThrows(PairingPayloadException::class.java) {
            ManualPairingPayloadParser.parse(canonical.replaceFirst('A', '+'), clock)
        }

        val malformedUtf8 = Base64.getUrlEncoder().withoutPadding().encodeToString(
            byteArrayOf('{'.code.toByte(), '}'.code.toByte(), 0xC3.toByte(), 0x28),
        )
        assertThrows(PairingPayloadException::class.java) {
            ManualPairingPayloadParser.parse(malformedUtf8, clock)
        }
    }

    @Test
    fun manualPayloadRejectsInvalidIdentitySecretPinEndpointAndExpiry() {
        val invalidCases = listOf(
            jsonPayload(runnerId = RUNNER_ID.replaceFirst("0", "a").uppercase()),
            jsonPayload(invitationId = "not-a-uuid"),
            jsonPayload(invitationSecret = "A".repeat(42)),
            jsonPayload(invitationSecret = "B".repeat(43)),
            jsonPayload(rootPin = "sha256/" + "A".repeat(42) + "="),
            jsonPayload(rootPin = "sha256/" + "B".repeat(43) + "="),
            jsonPayload(endpoint = "http://runner.example:8443"),
            jsonPayload(endpoint = "https://user:password@runner.example:8443"),
            jsonPayload(endpoint = "https://runner.example:8443/pairing/v1"),
            jsonPayload(endpoint = "https://runner.example:8443?redirect=1"),
            jsonPayload(endpoint = "https://runner.example"),
            jsonPayload(expiresAt = "2026-09-08T00:00:00Z"),
            jsonPayload(expiresAt = "2026-09-08T00:10:01Z"),
        )

        invalidCases.forEachIndexed { index, json ->
            assertThrows("invalid case $index", PairingPayloadException::class.java) {
                ManualPairingPayloadParser.parse(encode(json), clock)
            }
        }
    }

    @Test
    fun manualPayloadRejectsOversizedDecodedJson() {
        val oversized = jsonPayload(extra = ",\"padding\":\"${"x".repeat(5_000)}\"")

        assertThrows(PairingPayloadException::class.java) {
            ManualPairingPayloadParser.parse(encode(oversized), clock)
        }
    }

    @Test
    fun manualPayloadRejectsEscapedDuplicateFieldsNestedValuesAndUnsupportedVersions() {
        val escapedDuplicate = jsonPayload().replace(
            "\"schemaVersion\":1,", "\"schemaVersion\":1,\"schema\\u0056ersion\":1,",
        )
        val nestedValue = jsonPayload().replace("\"schemaVersion\":1", "\"schemaVersion\":${"[".repeat(17)}1${"]".repeat(17)}")
        listOf(escapedDuplicate, nestedValue, jsonPayload().replace("\"schemaVersion\":1", "\"schemaVersion\":2"),
            jsonPayload().replace("\"schemaVersion\":1", "\"schemaVersion\":null"),
            jsonPayload().replace("\"invitationSecret\":\"${"A".repeat(43)}\"", "\"invitationSecret\":null"),
        ).forEachIndexed { index, json ->
            assertThrows("invalid shape $index", PairingPayloadException::class.java) {
                ManualPairingPayloadParser.parse(encode(json), clock)
            }
        }
    }

    @Test
    fun connectionContextOwnsImmutableRootBytesAndDoesNotPrintBearer() {
        val bytes = rootCertificate().encoded
        val original = bytes.clone()
        val bearer = "rdb1.$TOKEN_ID.${"A".repeat(43)}"
        val context = RunnerTransportContext(
            RunnerClientTransportMode.PAIRED_HTTPS, "https://runner.example:8443", RUNNER_ID,
            PRINCIPAL_ID, bearer, rootPin(rootCertificate()), bytes,
        )
        bytes.fill(0)
        assertTrue(original.contentEquals(requireNotNull(context.rootCertificateDer)))
        requireNotNull(context.rootCertificateDer).fill(0)
        assertTrue(original.contentEquals(requireNotNull(context.rootCertificateDer)))
        assertTrue(!context.toString().contains(bearer))
        val invitationSecret = "B".repeat(42) + "A"
        assertTrue(!ManualPairingPayloadParser.parse(encode(jsonPayload(invitationSecret = invitationSecret)), clock)
            .toString().contains(invitationSecret))
    }

    @Test
    fun endpointPolicyAllowsOnlyExplicitDevelopmentLoopbackOrPairedHttps() {
        RunnerTransportPolicy.validate(
            RunnerTransportContext(
                mode = RunnerClientTransportMode.DEVELOPMENT_HTTP,
                endpoint = "http://127.0.0.1:8080",
            ),
        )
        RunnerTransportPolicy.validate(
            RunnerTransportContext(
                mode = RunnerClientTransportMode.DEVELOPMENT_HTTP,
                endpoint = "http://[::1]:8080",
            ),
        )

        val paired = RunnerTransportContext(
            mode = RunnerClientTransportMode.PAIRED_HTTPS,
            endpoint = "https://runner.example:8443",
            runnerId = RUNNER_ID,
            principalId = PRINCIPAL_ID,
            bearerToken = "rdb1.$TOKEN_ID.${"B".repeat(42)}A",
            rootSpkiSha256 = rootPin(rootCertificate()),
            rootCertificateDer = rootCertificate().encoded,
        )
        RunnerTransportPolicy.validate(paired)

        listOf(
            RunnerTransportContext(
                mode = RunnerClientTransportMode.DEVELOPMENT_HTTP,
                endpoint = "http://192.0.2.10:8080",
            ),
            RunnerTransportContext(
                mode = RunnerClientTransportMode.DEVELOPMENT_HTTP,
                endpoint = "http://0.0.0.0:8080",
            ),
            paired.copyForTest(endpoint = "http://runner.example:8443"),
            paired.copyForTest(bearerToken = null),
            paired.copyForTest(rootCertificateDer = null),
        ).forEach { context ->
            assertThrows(PairingPayloadException::class.java) {
                RunnerTransportPolicy.validate(context)
            }
        }
    }

    @Test
    fun endpointParserRejectsAuthorityConfusionAndNonRootPaths() {
        listOf(
            "https://runner.example:8443/",
            "https://runner.example:8443/pairing",
            "https://runner.example:8443?x=1",
            "https://runner.example:8443#fragment",
            "https://runner.example:8443@evil.example:443",
            "https://runner.example:8443\\u0000.evil.example:443",
            "https://runner.example:65536",
            "https://runner.example",
        ).forEach { endpoint ->
            assertThrows(PairingPayloadException::class.java) {
                RunnerTransportPolicy.parseEndpoint(endpoint)
            }
        }
    }

    @Test
    fun registryDoesNotReplaceActiveContextWhenNewContextFailsValidation() {
        val registry = RunnerConnectionRegistry("http://127.0.0.1:8080")
        val original = requireNotNull(registry.current())

        assertThrows(PairingPayloadException::class.java) {
            registry.use(
                RunnerTransportContext(
                    mode = RunnerClientTransportMode.PAIRED_HTTPS,
                    endpoint = "http://127.0.0.1:8080",
                ),
            )
        }

        assertSame(original, registry.current())
        registry.clear()
        assertEquals(null, registry.current())
    }

    @Test
    fun validCertificateChainRequiresExactPinSanAndServerEku() {
        val root = rootCertificate()
        val validLeaf = certificate(VALID_LEAF_PEM)

        assertSame(
            root,
            RunnerCertificateVerifier.verify(
                chain = arrayOf(validLeaf, root),
                endpointHost = "runner.example",
                expectedRootPin = rootPin(root),
            ),
        )

        assertThrows(CertificateException::class.java) {
            RunnerCertificateVerifier.verify(
                chain = arrayOf(validLeaf, root),
                endpointHost = "runner.example",
                expectedRootPin = "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            )
        }
        assertThrows(CertificateException::class.java) {
            RunnerCertificateVerifier.verify(
                chain = arrayOf(certificate(WRONG_SAN_LEAF_PEM), root),
                endpointHost = "runner.example",
                expectedRootPin = rootPin(root),
            )
        }
        assertThrows(CertificateException::class.java) {
            RunnerCertificateVerifier.verify(
                chain = arrayOf(certificate(NO_EKU_LEAF_PEM), root),
                endpointHost = "runner.example",
                expectedRootPin = rootPin(root),
            )
        }
        assertThrows(CertificateException::class.java) {
            RunnerCertificateVerifier.verify(
                chain = arrayOf(validLeaf),
                endpointHost = "runner.example",
                expectedRootPin = rootPin(root),
            )
        }
    }

    @Test
    fun verifierRejectsChangedPinnedRootAndTrustManagerRecordsOnlyValidatedRoot() {
        val root = rootCertificate()
        val leaf = certificate(VALID_LEAF_PEM)
        val pin = rootPin(root)
        val changedRootDer = root.encoded.copyOf().also { bytes ->
            bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        }

        assertThrows(CertificateException::class.java) {
            RunnerCertificateVerifier.verify(
                chain = arrayOf(leaf, root),
                endpointHost = "runner.example",
                expectedRootPin = pin,
                expectedRootCertificateDer = changedRootDer,
            )
        }

        val manager = FixedRunnerTrustManager("runner.example", pin, root.encoded)
        manager.checkServerTrusted(arrayOf(leaf, root), "ECDHE_ECDSA")
        assertEquals(root, manager.authenticatedRoot)
    }

    private fun validEncodedPayload(): String = encode(jsonPayload())

    private fun jsonPayload(
        endpoint: String = "https://runner.example:8443",
        runnerId: String = RUNNER_ID,
        invitationId: String = INVITATION_ID,
        invitationSecret: String = "A".repeat(43),
        rootPin: String = "sha256/" + "A".repeat(43) + "=",
        expiresAt: String = "2026-09-08T00:05:00Z",
        extra: String = "",
    ): String =
        "{" +
            "\"schemaVersion\":1," +
            "\"endpoint\":\"$endpoint\"," +
            "\"runnerId\":\"$runnerId\"," +
            "\"rootSpkiSha256\":\"$rootPin\"," +
            "\"invitationId\":\"$invitationId\"," +
            "\"invitationSecret\":\"$invitationSecret\"," +
            "\"expiresAt\":\"$expiresAt\"$extra" +
            "}"

    private fun encode(json: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray(StandardCharsets.UTF_8))

    private fun rootPin(root: X509Certificate): String =
        "sha256/" + Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(root.publicKey.encoded),
        )

    private fun certificate(pem: String): X509Certificate =
        CertificateFactory.getInstance("X.509").generateCertificate(
            ByteArrayInputStream(pem.toByteArray(StandardCharsets.US_ASCII)),
        ) as X509Certificate

    private fun rootCertificate(): X509Certificate = certificate(ROOT_PEM)

    private fun RunnerTransportContext.copyForTest(
        endpoint: String = this.endpoint,
        bearerToken: String? = this.bearerToken,
        rootCertificateDer: ByteArray? = this.rootCertificateDer,
    ) = RunnerTransportContext(
        mode = mode,
        endpoint = endpoint,
        runnerId = runnerId,
        principalId = principalId,
        bearerToken = bearerToken,
        rootSpkiSha256 = rootSpkiSha256,
        rootCertificateDer = rootCertificateDer,
    )

    private companion object {
        const val RUNNER_ID = "00000000-0000-4000-8000-000000000001"
        const val PRINCIPAL_ID = "00000000-0000-4000-8000-000000000002"
        const val INVITATION_ID = "00000000-0000-4000-8000-000000000003"
        const val TOKEN_ID = "00000000-0000-4000-8000-000000000004"

        // Generated with OpenSSL 3 using ECDSA prime256v1 and the extensions
        // required by RunnerCertificateVerifier (CA root and server leaf).
        private const val ROOT_PEM = """
-----BEGIN CERTIFICATE-----
MIIB0DCCAXagAwIBAgIUf1FCcrikliwsCShGawol7rZGX20wCgYIKoZIzj0EAwIw
NDEdMBsGA1UEAwwUUmVwcm9Ecm9pZCBUZXN0IFJvb3QxEzARBgNVBAoMClJlcHJv
RHJvaWQwHhcNMjYwOTA3MjMzNDM3WhcNMzYwOTA0MjMzNDM3WjA0MR0wGwYDVQQD
DBRSZXByb0Ryb2lkIFRlc3QgUm9vdDETMBEGA1UECgwKUmVwcm9Ecm9pZDBZMBMG
ByqGSM49AgEGCCqGSM49AwEHA0IABN6KVnq2WcZHCm80U2ooQmRSvPuPTURuOf0K
FNgLXARkqRKCO0Rg9S+MGHADFBCm/8n50dvz650Oks7ME3ArQl+jZjBkMB8GA1Ud
IwQYMBaAFGf0fX/cv9wOvXnMbvnol3tglaraMBIGA1UdEwEB/wQIMAYBAf8CAQAw
DgYDVR0PAQH/BAQDAgEGMB0GA1UdDgQWBBRn9H1/3L/cDr15zG756Jd7YJWq2jAK
BggqhkjOPQQDAgNIADBFAiBg5tH7qpdMgcLHxyTkttC2HqYV9jZOmMzpUI8ng6rw
dAIhAJZcD9UhVVXVP0Gww+TsQEZNPpc7rXZs2gy25cBiemZT
-----END CERTIFICATE-----
"""

        private const val VALID_LEAF_PEM = """
-----BEGIN CERTIFICATE-----
MIIB+jCCAaCgAwIBAgIUKrbw3//MUDeWcIrxi7FIzaQrWnUwCgYIKoZIzj0EAwIw
NDEdMBsGA1UEAwwUUmVwcm9Ecm9pZCBUZXN0IFJvb3QxEzARBgNVBAoMClJlcHJv
RHJvaWQwHhcNMjYwOTA3MjMzNDM3WhcNMjcwOTA3MjMzNDM3WjAvMRgwFgYDVQQD
DA9pZ25vcmVkLmV4YW1wbGUxEzARBgNVBAoMClJlcHJvRHJvaWQwWTATBgcqhkjO
PQIBBggqhkjOPQMBBwNCAATZ7p5UtNANXW08EMxb0a6/bqZx/1HSb3iXdvzenyc/
18PXBC5nk5Sp3d70dzF+AoPCQEtq1ERrt/mrHnhQfcG3o4GUMIGRMAwGA1UdEwEB
/wQCMAAwDgYDVR0PAQH/BAQDAgWgMBYGA1UdJQEB/wQMMAoGCCsGAQUFBwMBMBkG
A1UdEQQSMBCCDnJ1bm5lci5leGFtcGxlMB0GA1UdDgQWBBS2H8vldLdcFwPcsQkb
Kb4mliG0KDAfBgNVHSMEGDAWgBRn9H1/3L/cDr15zG756Jd7YJWq2jAKBggqhkjO
PQQDAgNIADBFAiEAtromPs43+rfJjIq4shOmtdfkRX5Ur7IPZXTOwBJqVT8CIHXL
doqFLVJbX8k19JvwKke4zKK9bnDxgJr+nMqTsPdK
-----END CERTIFICATE-----
"""

        private const val WRONG_SAN_LEAF_PEM = """
-----BEGIN CERTIFICATE-----
MIIB+TCCAZ+gAwIBAgIUKrbw3//MUDeWcIrxi7FIzaQrWnYwCgYIKoZIzj0EAwIw
NDEdMBsGA1UEAwwUUmVwcm9Ecm9pZCBUZXN0IFJvb3QxEzARBgNVBAoMClJlcHJv
RHJvaWQwHhcNMjYwOTA3MjMzNDQ2WhcNMjcwOTA3MjMzNDQ2WjAvMRgwFgYDVQQD
DA9pZ25vcmVkLmV4YW1wbGUxEzARBgNVBAoMClJlcHJvRHJvaWQwWTATBgcqhkjO
PQIBBggqhkjOPQMBBwNCAAR+PAA/9nigzBEC0UTxjtQZhmaf32NUPWgI1Uqvy5aT
VelKTIbiztrX5/VXyxS+AsN3y20m7SYuLlGzB81CvXwWo4GTMIGQMAwGA1UdEwEB
/wQCMAAwDgYDVR0PAQH/BAQDAgWgMBYGA1UdJQEB/wQMMAoGCCsGAQUFBwMBMBgG
A1UdEQQRMA+CDW90aGVyLmV4YW1wbGUwHQYDVR0OBBYEFBrspKxTp4krvFMF/fN+
1uSe+HZjMB8GA1UdIwQYMBaAFGf0fX/cv9wOvXnMbvnol3tglaraMAoGCCqGSM49
BAMCA0gAMEUCICgRwDEOY4jHl+5xSa7HV9yghza2HDZxHKH4AAk0Vl1pAiEA4yBy
6y4iozmYLyCTepkig1KTayQKV/cZMAR0inLBdfc=
-----END CERTIFICATE-----
"""

        private const val NO_EKU_LEAF_PEM = """
-----BEGIN CERTIFICATE-----
MIIB4DCCAYagAwIBAgIUKrbw3//MUDeWcIrxi7FIzaQrWncwCgYIKoZIzj0EAwIw
NDEdMBsGA1UEAwwUUmVwcm9Ecm9pZCBUZXN0IFJvb3QxEzARBgNVBAoMClJlcHJv
RHJvaWQwHhcNMjYwOTA3MjMzNDQ2WhcNMjcwOTA3MjMzNDQ2WjAvMRgwFgYDVQQD
DA9pZ25vcmVkLmV4YW1wbGUxEzARBgNVBAoMClJlcHJvRHJvaWQwWTATBgcqhkjO
PQIBBggqhkjOPQMBBwNCAARtEVn4Xby8aMmwGYbJZ927yEWDtWLRseIQwFLTmaI+
ThiTblVI+M0TKTMijrawG1QEDnMGMsI4j1s4qLfMJKnEo3sweTAMBgNVHRMBAf8E
AjAAMA4GA1UdDwEB/wQEAwIFoDAZBgNVHREEEjAQgg5ydW5uZXIuZXhhbXBsZTAd
BgNVHQ4EFgQUtdqcVIQgesVQspRfho/8zJ3wyX8wHwYDVR0jBBgwFoAUZ/R9f9y/
3A69ecxu+eiXe2CVqtowCgYIKoZIzj0EAwIDSAAwRQIhAMxaCKU0b3dDR+qje+6E
ztUhvlGL+xV65/bZiplWdsPTAiB7OoVDDje5iEjdTWQAcfbFY/7KenZCFLXqwcXb
k0jwkA==
-----END CERTIFICATE-----
"""

    }
}
