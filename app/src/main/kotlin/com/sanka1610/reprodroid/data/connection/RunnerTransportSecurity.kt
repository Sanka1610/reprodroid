package com.sanka1610.reprodroid.data.connection

import java.net.InetAddress
import java.net.URI
import java.security.MessageDigest
import java.security.AlgorithmParameters
import java.security.cert.CertPathValidator
import java.security.cert.CertificateFactory
import java.security.cert.CertificateException
import java.security.cert.PKIXParameters
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECFieldFp
import java.util.Base64
import javax.net.ssl.X509TrustManager

enum class RunnerClientTransportMode { DEVELOPMENT_HTTP, PAIRED_HTTPS }

/** Deliberately not a data class: credentials must not appear in generated toString output. */
class RunnerTransportContext(
    val mode: RunnerClientTransportMode,
    val endpoint: String,
    val runnerId: String? = null,
    val principalId: String? = null,
    val bearerToken: String? = null,
    val rootSpkiSha256: String? = null,
    rootCertificateDer: ByteArray? = null,
) {
    private val rootCertificateBytes = rootCertificateDer?.clone()
    val rootCertificateDer: ByteArray? get() = rootCertificateBytes?.clone()
}

class RunnerConnectionRegistry(developmentEndpoint: String?) {
    private val retirementListeners = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()
    @Volatile
    private var currentContext: RunnerTransportContext? = developmentEndpoint
        ?.takeIf(String::isNotBlank)
        ?.let {
            RunnerTransportContext(
                mode = RunnerClientTransportMode.DEVELOPMENT_HTTP,
                endpoint = it.trim().trimEnd('/'),
            )
        }

    fun current(): RunnerTransportContext? = currentContext

    fun use(context: RunnerTransportContext) {
        RunnerTransportPolicy.validate(context)
        currentContext = context
        retirementListeners.forEach { it() }
    }

    fun clear() {
        currentContext = null
        retirementListeners.forEach { it() }
    }

    internal fun onSessionRetired(listener: () -> Unit): () -> Unit {
        retirementListeners.add(listener)
        return { retirementListeners.remove(listener) }
    }
}

object RunnerTransportPolicy {
    private val loopbackNames = setOf("localhost", "127.0.0.1", "::1", "[::1]")

    fun validate(context: RunnerTransportContext) {
        val uri = parseEndpoint(context.endpoint)
        when (context.mode) {
            RunnerClientTransportMode.DEVELOPMENT_HTTP -> {
                if (uri.scheme.lowercase() != "http" || uri.host.lowercase() !in loopbackNames) {
                    throw PairingPayloadException("Development Runner HTTP is limited to an explicit loopback endpoint.")
                }
                if (context.bearerToken != null || context.rootSpkiSha256 != null) {
                    throw PairingPayloadException("Development Runner HTTP cannot carry paired credentials.")
                }
            }
            RunnerClientTransportMode.PAIRED_HTTPS -> {
                if (
                    uri.scheme.lowercase() != "https" || context.runnerId == null || context.principalId == null ||
                    context.bearerToken == null || context.rootSpkiSha256 == null || context.rootCertificateDer == null
                ) {
                    throw PairingPayloadException("Paired Runner transport requires HTTPS, identity, pin, root, and bearer.")
                }
            }
        }
    }

    fun parseEndpoint(endpoint: String): URI {
        val uri = runCatching { URI(endpoint) }.getOrNull()
            ?: throw PairingPayloadException("Runner endpoint is invalid.")
        if (
            uri.scheme?.lowercase() !in setOf("http", "https") || uri.host == null || uri.port !in 1..65_535 ||
            uri.userInfo != null || uri.query != null || uri.fragment != null ||
            (uri.path.isNotEmpty() && uri.path != "/") || endpoint != endpoint.trim().trimEnd('/')
        ) {
            throw PairingPayloadException("Runner endpoint must contain only scheme, host, and explicit port.")
        }
        return uri
    }
}

internal object RunnerCertificateVerifier {
    private const val SERVER_AUTH_EKU = "1.3.6.1.5.5.7.3.1"

    fun verify(
        chain: Array<out X509Certificate>?,
        endpointHost: String,
        expectedRootPin: String,
        expectedRootCertificateDer: ByteArray? = null,
    ): X509Certificate {
        if (chain == null || chain.size != 2) throw CertificateException("Runner must present a leaf and its local root.")
        val leaf = chain[0]
        val root = chain[1]
        try {
            leaf.checkValidity()
            root.checkValidity()
            if (root.subjectX500Principal != root.issuerX500Principal || root.basicConstraints < 0) {
                throw CertificateException("Runner root is not a self-signed CA.")
            }
            root.verify(root.publicKey)
            if (leaf.issuerX500Principal != root.subjectX500Principal || leaf.basicConstraints >= 0) {
                throw CertificateException("Runner leaf is not issued by the presented root.")
            }
            leaf.verify(root.publicKey)
            if (root.publicKey.algorithm != "EC" || leaf.publicKey.algorithm != "EC") {
                throw CertificateException("Runner certificates must use ECDSA P-256 keys.")
            }
            requireEcP256(root)
            requireEcP256(leaf)
            if (root.sigAlgOID != SHA256_ECDSA_OID || leaf.sigAlgOID != SHA256_ECDSA_OID) {
                throw CertificateException("Runner certificates use an unsupported signature algorithm.")
            }
            if (MessageDigest.isEqual(root.publicKey.encoded, leaf.publicKey.encoded)) {
                throw CertificateException("Runner root and leaf must use independent keys.")
            }
            val rootUsage = root.keyUsage
            if (rootUsage == null || rootUsage.size <= 5 || !rootUsage[5]) {
                throw CertificateException("Runner root key usage does not permit certificate signing.")
            }
            val leafUsage = leaf.keyUsage
            if (leafUsage == null || leafUsage.isEmpty() || !leafUsage[0]) {
                throw CertificateException("Runner leaf key usage does not permit TLS signing.")
            }
            if (leaf.extendedKeyUsage?.contains(SERVER_AUTH_EKU) != true) {
                throw CertificateException("Runner leaf is not valid for TLS server authentication.")
            }
            val unsupportedRootCritical = root.criticalExtensionOIDs.orEmpty() - ROOT_CRITICAL_EXTENSIONS
            if (unsupportedRootCritical.isNotEmpty() || root.hasUnsupportedCriticalExtension()) {
                throw CertificateException("Runner root contains an unsupported critical extension.")
            }
            validatePath(leaf, root)
            val actualPin = "sha256/" + Base64.getEncoder().encodeToString(sha256(root.publicKey.encoded))
            if (!MessageDigest.isEqual(actualPin.toByteArray(Charsets.US_ASCII), expectedRootPin.toByteArray(Charsets.US_ASCII))) {
                throw CertificateException("Runner root pin does not match the manual pairing payload.")
            }
            if (expectedRootCertificateDer != null &&
                !MessageDigest.isEqual(root.encoded, expectedRootCertificateDer)
            ) {
                throw CertificateException("Runner root certificate changed; re-pairing is required.")
            }
            verifyExactSan(leaf, endpointHost)
            return root
        } catch (failure: CertificateException) {
            throw failure
        } catch (failure: Exception) {
            throw CertificateException("Runner certificate validation failed.", failure)
        }
    }

    private fun requireEcP256(certificate: X509Certificate) {
        val actual = (certificate.publicKey as? java.security.interfaces.ECPublicKey)?.params
            ?: throw CertificateException("Runner certificate key is not EC.")
        val expected = AlgorithmParameters.getInstance("EC").run {
            init(ECGenParameterSpec("secp256r1"))
            getParameterSpec(ECParameterSpec::class.java)
        }
        val actualPrime = (actual.curve.field as? ECFieldFp)?.p
        val expectedPrime = (expected.curve.field as? ECFieldFp)?.p
        if (
            actual.cofactor != expected.cofactor || actual.order != expected.order ||
            actual.generator != expected.generator || actualPrime == null || actualPrime != expectedPrime ||
            actual.curve.a != expected.curve.a || actual.curve.b != expected.curve.b
        ) throw CertificateException("Runner certificate key is not ECDSA P-256.")
    }

    private fun validatePath(leaf: X509Certificate, root: X509Certificate) {
        val path = CertificateFactory.getInstance("X.509").generateCertPath(listOf(leaf))
        val parameters = PKIXParameters(setOf(TrustAnchor(root, null))).apply { isRevocationEnabled = false }
        CertPathValidator.getInstance("PKIX").validate(path, parameters)
    }

    private fun verifyExactSan(leaf: X509Certificate, endpointHost: String) {
        val sans = leaf.subjectAlternativeNames ?: throw CertificateException("Runner leaf has no SAN.")
        val hostIsIp = endpointHost.contains(':') || IPV4.matches(endpointHost)
        val matched = sans.any { san ->
            if (san.size < 2) return@any false
            when {
                hostIsIp && san[0] == 7 -> ipEquals(endpointHost, san[1])
                !hostIsIp && san[0] == 2 -> (san[1] as? String)?.equals(endpointHost, ignoreCase = true) == true &&
                    '*' !in (san[1] as String)
                else -> false
            }
        }
        if (!matched) throw CertificateException("Runner leaf SAN does not exactly match the configured endpoint.")
    }

    private fun ipEquals(host: String, san: Any?): Boolean = runCatching {
        val expected = InetAddress.getByName(host.removePrefix("[").removeSuffix("]")).address
        val actual = when (san) {
            is ByteArray -> san
            is String -> InetAddress.getByName(san).address
            else -> return false
        }
        expected.contentEquals(actual)
    }.getOrDefault(false)

    private fun sha256(value: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(value)
    private val IPV4 = Regex("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}")
    private val ROOT_CRITICAL_EXTENSIONS = setOf("2.5.29.19", "2.5.29.15")
    private const val SHA256_ECDSA_OID = "1.2.840.10045.4.3.2"
}

internal class FixedRunnerTrustManager(
    private val endpointHost: String,
    private val rootPin: String,
    expectedRootCertificateDer: ByteArray? = null,
) : X509TrustManager {
    private val expectedRootCertificateDer = expectedRootCertificateDer?.clone()
    @Volatile
    var authenticatedRoot: X509Certificate? = null
        private set

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        authenticatedRoot = RunnerCertificateVerifier.verify(chain, endpointHost, rootPin, expectedRootCertificateDer)
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) =
        throw CertificateException("Client certificate trust is not supported.")

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}
