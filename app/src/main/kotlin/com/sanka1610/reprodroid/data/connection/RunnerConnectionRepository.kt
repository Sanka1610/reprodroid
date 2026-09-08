package com.sanka1610.reprodroid.data.connection

import android.content.Context
import android.os.Build
import androidx.room.withTransaction
import com.sanka1610.reprodroid.data.local.ReproDroidDatabase
import com.sanka1610.reprodroid.data.local.RunnerConnectionEntity
import com.sanka1610.reprodroid.data.local.RunnerPairingState
import com.sanka1610.reprodroid.data.local.RunnerRevocationKnowledge
import com.sanka1610.reprodroid.data.local.RunnerTransportMode
import com.sanka1610.reprodroid.data.network.RunnerApiClient
import com.sanka1610.reprodroid.data.network.RunnerApiException
import com.sanka1610.reprodroid.data.network.RunnerResponseIntegrityException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Instant
import java.util.Base64
import java.util.UUID

enum class RunnerConnectionPhase { INITIALIZING, UNCONFIGURED, DEVELOPMENT, PENDING_APPROVAL, CONFIGURED, CONNECTED, ERROR }
enum class RunnerConnectionIssue {
    AMBIGUOUS_RECORDS, CREDENTIAL_UNAVAILABLE, PAIRING_INTERRUPTED, PAIRING_RETRY,
    PAIRING_ENDED, PAIRING_EXPIRED, PAIRING_INVALID, CANCELLED_LOCALLY,
    REVOCATION_PENDING, REVOCATION_CONFIRMED, AUTHENTICATION_UNKNOWN, LOCAL_DELETION,
    NETWORK_UNAVAILABLE, INVALID_PAYLOAD,
}

data class RunnerConnectionStatus(
    val phase: RunnerConnectionPhase = RunnerConnectionPhase.INITIALIZING,
    val active: RunnerConnectionEntity? = null,
    val pending: RunnerConnectionEntity? = null,
    val issue: RunnerConnectionIssue? = null,
    val lastCheckedAt: String? = null,
)

class RunnerConnectionRepository(
    context: Context,
    private val database: ReproDroidDatabase,
    private val registry: RunnerConnectionRegistry,
    private val runnerApi: RunnerApiClient,
    private val applicationScope: CoroutineScope,
    private val developmentEndpoint: String?,
    private val allowDevelopmentHttp: Boolean,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val dao = database.runnerConnectionDao()
    private val credentials: RunnerCredentialStore by lazy { AndroidKeystoreRunnerCredentialStore(context) }
    private val certificates by lazy { RunnerCertificateStore(context) }
    private val mutex = Mutex()
    private var pollingJob: Job? = null
    private val _status = MutableStateFlow(RunnerConnectionStatus())
    val status = _status.asStateFlow()
    val connections = dao.observeConnections()

    fun reportInitializationFailure() {
        registry.clear()
        _status.value = RunnerConnectionStatus(RunnerConnectionPhase.ERROR, issue = RunnerConnectionIssue.CREDENTIAL_UNAVAILABLE)
    }

    suspend fun initialize() = mutex.withLock {
        val active = dao.getActiveConnections()
        if (active.size > 1) {
            registry.clear()
            _status.value = RunnerConnectionStatus(
                phase = RunnerConnectionPhase.ERROR,
                issue = RunnerConnectionIssue.AMBIGUOUS_RECORDS,
            )
            return@withLock
        }
        // A pending attempt cannot survive process death by contract. No raw invitation is recovered.
        val interrupted = dao.getPendingConnections()
        interrupted.forEach { pending ->
            credentials.delete(pending.credentialFileReference)
            dao.upsertConnection(
                pending.copy(
                    pairingState = RunnerPairingState.FAILED.name,
                    active = false,
                    updatedAt = clock.instant().toString(),
                ),
            )
        }
        // Files published before Room commit must not recover an interrupted credential generation.
        val retained = dao.getConnections()
        credentials.pruneUnreferenced(retained.filter { it.pairingState == RunnerPairingState.APPROVED.name }
            .map { it.credentialFileReference }.toSet())
        certificates.pruneUnreferenced(retained.map { it.caCertificateFileReference }.toSet())
        val connection = dao.getActiveConnections().singleOrNull()
        if (connection != null) {
            try {
                activateRegistry(connection)
                _status.value = RunnerConnectionStatus(RunnerConnectionPhase.CONFIGURED, active = connection)
            } catch (failure: Exception) {
                registry.clear()
                _status.value = RunnerConnectionStatus(
                    RunnerConnectionPhase.ERROR,
                    active = connection,
                    issue = RunnerConnectionIssue.CREDENTIAL_UNAVAILABLE,
                )
            }
        } else if (dao.getConnections().isEmpty() && allowDevelopmentHttp && !developmentEndpoint.isNullOrBlank()) {
            val context = RunnerTransportContext(
                mode = RunnerClientTransportMode.DEVELOPMENT_HTTP,
                endpoint = developmentEndpoint.trim().trimEnd('/'),
            )
            registry.use(context)
            _status.value = RunnerConnectionStatus(
                RunnerConnectionPhase.DEVELOPMENT,
            )
        } else {
            registry.clear()
            _status.value = RunnerConnectionStatus(
                RunnerConnectionPhase.UNCONFIGURED,
                active = dao.getConnections().maxByOrNull { it.updatedAt },
                issue = RunnerConnectionIssue.PAIRING_INTERRUPTED.takeIf { interrupted.isNotEmpty() },
            )
        }
    }

    suspend fun pair(encodedPayload: String): RunnerConnectionEntity = mutex.withLock {
        val payload = ManualPairingPayloadParser.parse(encodedPayload, clock)
        check(dao.getActiveConnections().size <= 1) { "Ambiguous active Runner records require local cleanup." }
        check(dao.getPendingConnections().isEmpty()) { "Finish or delete the pending pairing attempt first." }
        val existing = dao.getConnection(payload.runnerId)
        val tokenId = UUID.randomUUID().toString()
        val tokenSecret = randomSecret()
        val bearer = "rdb1.$tokenId.$tokenSecret"
        val continuationId = UUID.randomUUID().toString()
        val continuationSecret = randomSecret()
        val continuation = "$continuationId.$continuationSecret"
        val pendingReference = "$tokenId.pending.bin"
        credentials.write(
            pendingReference,
            RunnerCredentialMaterial(
                kind = AndroidKeystoreRunnerCredentialStore.KIND_PENDING,
                runnerId = payload.runnerId,
                bindingId = payload.invitationId,
                bearerToken = bearer,
                continuationCredential = continuation,
            ),
        )
        val (response, rootDer) = try {
            RunnerPairingClient(payload.endpoint, payload.rootSpkiSha256).use { client ->
                val identity = client.identity()
                if (identity.runnerId != payload.runnerId) {
                    throw RunnerResponseIntegrityException("TLS endpoint Runner identity does not match the manual payload.")
                }
                val created = client.create(
                    CreatePairingRequest(
                        schemaVersion = 1,
                        runnerId = payload.runnerId,
                        invitationId = payload.invitationId,
                        invitationSecret = payload.invitationSecret,
                        deviceDisplayName = deviceDisplayName(),
                        tokenId = tokenId,
                        tokenSha256 = sha256(bearer),
                        continuationId = continuationId,
                        continuationSha256 = sha256(continuationSecret),
                    ),
                )
                created to client.authenticatedRootDer()
            }
        } catch (failure: Throwable) {
            runCatching { credentials.delete(pendingReference) }
            throw failure
        }
        if (response.state != RunnerPairingState.PENDING_APPROVAL.name) {
            credentials.delete(pendingReference)
            throw IllegalStateException("Runner did not create a pending approval request.")
        }
        if (Instant.parse(response.expiresAt) != Instant.parse(payload.expiresAt)) {
            credentials.delete(pendingReference)
            throw IllegalStateException("Runner pairing request changed the invitation expiry.")
        }
        credentials.write(
            pendingReference,
            RunnerCredentialMaterial(
                kind = AndroidKeystoreRunnerCredentialStore.KIND_PENDING,
                runnerId = payload.runnerId,
                bindingId = response.requestId,
                bearerToken = bearer,
                continuationCredential = continuation,
            ),
        )
        val certificateReference = "$tokenId.pending-root-ca.der"
        val certificateDigest = try {
            certificates.write(certificateReference, rootDer)
        } finally {
            rootDer.fill(0)
        }
        val now = clock.instant().toString()
        val pending = RunnerConnectionEntity(
            runnerId = payload.runnerId,
            endpoint = payload.endpoint,
            rootSpkiSha256 = payload.rootSpkiSha256,
            caCertificateFileReference = certificateReference,
            caCertificateSha256 = certificateDigest,
            credentialFileReference = pendingReference,
            principalId = null,
            displayName = deviceDisplayName(),
            transportMode = RunnerTransportMode.PAIRED_HTTPS.name,
            pairingRequestId = response.requestId,
            pairingState = response.state,
            confirmationFingerprint = response.confirmationFingerprint,
            pairingExpiresAt = response.expiresAt,
            revocationKnowledge = RunnerRevocationKnowledge.UNKNOWN.name,
            active = false,
            createdAt = now,
            updatedAt = now,
        )
        dao.upsertConnection(pending)
        // The old usable generation survives all remote validation and the replacement Room commit.
        if (existing != null) {
            if (existing.active) registry.clear()
            credentials.delete(existing.credentialFileReference)
            certificates.delete(existing.caCertificateFileReference)
        }
        _status.value = RunnerConnectionStatus(
            RunnerConnectionPhase.PENDING_APPROVAL,
            active = dao.getActiveConnections().singleOrNull(),
            pending = pending,
        )
        startPolling(pending.runnerId, response.requestId)
        pending
    }

    private fun startPolling(runnerId: String, requestId: String) {
        pollingJob?.cancel()
        pollingJob = applicationScope.launch {
            while (isActive) {
                try {
                    val terminal = pollOnce(runnerId, requestId)
                    if (terminal) return@launch
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (failure: Throwable) {
                    if (handlePollingFailure(runnerId, requestId, failure)) return@launch
                }
                delay(POLL_INTERVAL_MILLIS)
            }
        }
    }

    suspend fun pollOnce(runnerId: String, expectedRequestId: String? = null): Boolean = mutex.withLock {
        val record = dao.getConnection(runnerId) ?: return@withLock true
        if (record.pairingState != RunnerPairingState.PENDING_APPROVAL.name ||
            (expectedRequestId != null && record.pairingRequestId != expectedRequestId)
        ) return@withLock true
        if (pendingExpired(record)) {
            finishPending(record, RunnerPairingState.EXPIRED, RunnerConnectionIssue.PAIRING_EXPIRED)
            return@withLock true
        }
        val requestId = record.pairingRequestId ?: throw CredentialStoreException("Pairing request ID is missing.")
        if (!UUID_PATTERN.matches(record.runnerId) || !UUID_PATTERN.matches(requestId) ||
            !SHA256_PATTERN.matches(record.caCertificateSha256) || record.transportMode != RunnerTransportMode.PAIRED_HTTPS.name
        ) throw CredentialStoreException("Pending Runner metadata is inconsistent.")
        val material = credentials.read(record.credentialFileReference, record.runnerId, requestId)
        if (material.kind != AndroidKeystoreRunnerCredentialStore.KIND_PENDING) {
            throw CredentialStoreException("Pending Runner credential kind is inconsistent.")
        }
        val continuation = requireNotNull(material.continuationCredential)
        val root = certificates.read(record.caCertificateFileReference, record.caCertificateSha256)
        val response = RunnerPairingClient(record.endpoint, record.rootSpkiSha256, root).use {
            it.status(requestId, continuation, record.runnerId)
        }
        if (
            Instant.parse(response.expiresAt) != Instant.parse(requireNotNull(record.pairingExpiresAt)) ||
            response.confirmationFingerprint != record.confirmationFingerprint
        ) {
            throw RunnerResponseIntegrityException("Runner pairing status changed immutable request evidence.")
        }
        when (response.state) {
            RunnerPairingState.PENDING_APPROVAL.name -> {
                val updated = record.copy(
                    confirmationFingerprint = response.confirmationFingerprint,
                    pairingExpiresAt = response.expiresAt,
                    updatedAt = clock.instant().toString(),
                )
                dao.upsertConnection(updated)
                _status.value = RunnerConnectionStatus(RunnerConnectionPhase.PENDING_APPROVAL,
                    active = dao.getActiveConnections().singleOrNull(), pending = updated)
                false
            }
            RunnerPairingState.APPROVED.name -> {
                val principalId = requireNotNull(response.principalId)
                val generationId = UUID.randomUUID().toString()
                val activeReference = "$generationId.credential.bin"
                val activeCertificateReference = "$generationId.root-ca.der"
                val activeCertificateDigest = certificates.write(activeCertificateReference, root)
                credentials.write(
                    activeReference,
                    RunnerCredentialMaterial(
                        kind = AndroidKeystoreRunnerCredentialStore.KIND_ACTIVE,
                        runnerId = record.runnerId,
                        bindingId = principalId,
                        bearerToken = material.bearerToken,
                    ),
                )
                val active = record.copy(
                    credentialFileReference = activeReference,
                    caCertificateFileReference = activeCertificateReference,
                    caCertificateSha256 = activeCertificateDigest,
                    principalId = principalId,
                    pairingState = RunnerPairingState.APPROVED.name,
                    revocationKnowledge = RunnerRevocationKnowledge.ACTIVE.name,
                    active = true,
                    updatedAt = clock.instant().toString(),
                )
                database.withTransaction { dao.activate(active) }
                activateRegistry(active)
                credentials.delete(record.credentialFileReference)
                certificates.delete(record.caCertificateFileReference)
                _status.value = RunnerConnectionStatus(RunnerConnectionPhase.CONFIGURED, active = active)
                true
            }
            else -> {
                val localState = runCatching { RunnerPairingState.valueOf(response.state) }
                    .getOrDefault(RunnerPairingState.FAILED)
                finishPending(record, localState, RunnerConnectionIssue.PAIRING_ENDED)
                true
            }
        }
    }

    suspend fun cancelPending(runnerId: String) = mutex.withLock {
        pollingJob?.cancel()
        val record = requireNotNull(dao.getConnection(runnerId))
        check(record.pairingState == RunnerPairingState.PENDING_APPROVAL.name)
        val requestId = requireNotNull(record.pairingRequestId)
        val material = credentials.read(record.credentialFileReference, runnerId, requestId)
        val root = certificates.read(record.caCertificateFileReference, record.caCertificateSha256)
        RunnerPairingClient(record.endpoint, record.rootSpkiSha256, root).use {
            it.cancel(requestId, requireNotNull(material.continuationCredential))
        }
        credentials.delete(record.credentialFileReference)
        val cancelled = record.copy(
            pairingState = RunnerPairingState.FAILED.name,
            active = false,
            updatedAt = clock.instant().toString(),
        )
        dao.upsertConnection(cancelled)
        _status.value = RunnerConnectionStatus(
            RunnerConnectionPhase.UNCONFIGURED,
            active = dao.getActiveConnections().singleOrNull() ?: cancelled,
            issue = RunnerConnectionIssue.CANCELLED_LOCALLY,
        )
    }

    suspend fun refreshHealth() = mutex.withLock {
        val active = dao.getActiveConnections().singleOrNull()
            ?: throw IllegalStateException("No active paired Runner exists.")
        try {
            // A previous 401 retires the session, but an explicit check may retry the retained credential.
            activateRegistry(active)
            val response = runnerApi.getV2Capabilities()
            check(response.runnerId == active.runnerId) { "Authenticated Runner identity changed." }
            check(response.capabilities.any { it.id == AUTHENTICATION_CAPABILITY && it.contractVersion == 1 }) {
                "Runner did not advertise authenticated transport capability."
            }
            val checkedAt = clock.instant().toString()
            val checked = active.copy(revocationKnowledge = RunnerRevocationKnowledge.ACTIVE.name, updatedAt = checkedAt)
            dao.upsertConnection(checked)
            _status.value = RunnerConnectionStatus(RunnerConnectionPhase.CONNECTED, active = checked, lastCheckedAt = checkedAt)
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            val unknown = active.copy(revocationKnowledge = RunnerRevocationKnowledge.UNKNOWN.name,
                updatedAt = clock.instant().toString())
            dao.upsertConnection(unknown)
            registry.clear()
            _status.value = RunnerConnectionStatus(RunnerConnectionPhase.ERROR, active = unknown,
                issue = issueFor(failure))
            throw failure
        }
    }

    suspend fun selfRevoke() = mutex.withLock {
        val active = dao.getActiveConnections().singleOrNull()
            ?: throw IllegalStateException("No active paired Runner exists.")
        val checking = active.copy(
            revocationKnowledge = RunnerRevocationKnowledge.UNKNOWN.name,
            updatedAt = clock.instant().toString(),
        )
        dao.upsertConnection(checking)
        _status.value = RunnerConnectionStatus(
            RunnerConnectionPhase.CONFIGURED,
            active = checking,
            issue = RunnerConnectionIssue.REVOCATION_PENDING,
        )
        try {
            val response = runnerApi.selfRevoke()
            check(response.runnerId == active.runnerId && response.principalId == active.principalId && response.state == "REVOKED")
            registry.clear()
            val revoked = active.copy(
                pairingState = RunnerPairingState.REVOKED.name,
                revocationKnowledge = RunnerRevocationKnowledge.REVOKED.name,
                active = false,
                updatedAt = response.revokedAt,
            )
            dao.upsertConnection(revoked)
            credentials.delete(active.credentialFileReference)
            _status.value = RunnerConnectionStatus(
                RunnerConnectionPhase.UNCONFIGURED,
                active = revoked,
                issue = RunnerConnectionIssue.REVOCATION_CONFIRMED,
            )
        } catch (failure: Throwable) {
            registry.clear()
            _status.value = RunnerConnectionStatus(
                RunnerConnectionPhase.ERROR,
                active = dao.getConnection(active.runnerId) ?: checking,
                issue = RunnerConnectionIssue.AUTHENTICATION_UNKNOWN,
            )
            throw failure
        }
    }

    suspend fun localDelete(runnerId: String) = mutex.withLock {
        val record = requireNotNull(dao.getConnection(runnerId))
        if (record.pairingState == RunnerPairingState.PENDING_APPROVAL.name) pollingJob?.cancel()
        if (record.active) registry.clear()
        val retained = dao.getConnections().filter { it.runnerId != runnerId }
        // Corrupt references never become deletion paths. Enumerate only our bounded private generations,
        // retaining every file referenced by another record before removing the selected Room reference.
        credentials.pruneUnreferenced(retained.map { it.credentialFileReference }.toSet())
        certificates.pruneUnreferenced(retained.map { it.caCertificateFileReference }.toSet())
        if (retained.isEmpty()) credentials.retireKeyIfUnused()
        dao.deleteConnection(runnerId)
        val remainingActive = dao.getActiveConnections().singleOrNull()
        _status.value = RunnerConnectionStatus(
            if (remainingActive != null) RunnerConnectionPhase.CONFIGURED else RunnerConnectionPhase.UNCONFIGURED,
            active = remainingActive,
            issue = RunnerConnectionIssue.LOCAL_DELETION,
        )
    }

    private fun activateRegistry(connection: RunnerConnectionEntity) {
        check(
            connection.active && connection.pairingState == RunnerPairingState.APPROVED.name &&
                connection.transportMode == RunnerTransportMode.PAIRED_HTTPS.name &&
                UUID_PATTERN.matches(connection.runnerId) && connection.principalId?.matches(UUID_PATTERN) == true &&
                SHA256_PATTERN.matches(connection.caCertificateSha256)
        ) { "Runner connection metadata is not an active approved binding." }
        ManualPairingPayloadParser.validateEndpoint(connection.endpoint)
        val principalId = requireNotNull(connection.principalId)
        val material = credentials.read(connection.credentialFileReference, connection.runnerId, principalId)
        check(material.kind == AndroidKeystoreRunnerCredentialStore.KIND_ACTIVE)
        val root = certificates.read(connection.caCertificateFileReference, connection.caCertificateSha256)
        registry.use(
            RunnerTransportContext(
                mode = RunnerClientTransportMode.PAIRED_HTTPS,
                endpoint = connection.endpoint,
                runnerId = connection.runnerId,
                principalId = principalId,
                bearerToken = material.bearerToken,
                rootSpkiSha256 = connection.rootSpkiSha256,
                rootCertificateDer = root,
            ),
        )
    }

    private fun randomSecret(): String = ByteArray(32).also(SecureRandom()::nextBytes).let {
        Base64.getUrlEncoder().withoutPadding().encodeToString(it).also { _ -> it.fill(0) }
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.US_ASCII)).joinToString("") { "%02x".format(it) }

    private fun deviceDisplayName(): String = "${Build.MANUFACTURER} ${Build.MODEL}"
        .replace(Regex("[\\p{Cntrl}]"), " ").trim().take(128).ifEmpty { "Android device" }

    companion object {
        private const val POLL_INTERVAL_MILLIS = 2_000L
        private const val AUTHENTICATION_CAPABILITY = "runner-authentication"
        private val UUID_PATTERN = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        private val SHA256_PATTERN = Regex("[0-9a-f]{64}")
    }

    private fun pendingExpired(record: RunnerConnectionEntity): Boolean =
        record.pairingExpiresAt?.let { runCatching { Instant.parse(it).isAfter(clock.instant()) }.getOrDefault(false) } != true

    private suspend fun finishPending(record: RunnerConnectionEntity, state: RunnerPairingState, issue: RunnerConnectionIssue) {
        credentials.delete(record.credentialFileReference)
        val terminal = record.copy(
            pairingState = state.name,
            active = false,
            updatedAt = clock.instant().toString(),
        )
        dao.upsertConnection(terminal)
        _status.value = RunnerConnectionStatus(RunnerConnectionPhase.ERROR,
            active = dao.getActiveConnections().singleOrNull() ?: terminal, issue = issue)
    }

    private suspend fun handlePollingFailure(runnerId: String, requestId: String, failure: Throwable): Boolean = mutex.withLock {
        val record = dao.getConnection(runnerId) ?: return@withLock true
        if (record.pairingRequestId != requestId || record.pairingState != RunnerPairingState.PENDING_APPROVAL.name) {
            return@withLock true
        }
        if (pendingExpired(record)) {
            finishPending(record, RunnerPairingState.EXPIRED, RunnerConnectionIssue.PAIRING_EXPIRED)
            return@withLock true
        }
        if (failure is RunnerResponseIntegrityException || failure is CredentialStoreException || failure is PairingPayloadException ||
            (failure as? RunnerApiException)?.statusCode in setOf(401, 403, 404) ||
            generateSequence(failure) { it.cause }.take(16).any { it is java.security.cert.CertificateException }
        ) {
            finishPending(record, RunnerPairingState.FAILED, RunnerConnectionIssue.PAIRING_INVALID)
            return@withLock true
        }
        _status.value = RunnerConnectionStatus(RunnerConnectionPhase.ERROR,
            active = dao.getActiveConnections().singleOrNull(), pending = record,
            issue = RunnerConnectionIssue.PAIRING_RETRY)
        false
    }

    fun reportActionFailure(failure: Throwable) {
        val issue = if (_status.value.issue == RunnerConnectionIssue.AUTHENTICATION_UNKNOWN) {
            RunnerConnectionIssue.AUTHENTICATION_UNKNOWN
        } else issueFor(failure)
        _status.value = _status.value.copy(phase = RunnerConnectionPhase.ERROR, issue = issue)
    }

    private fun issueFor(failure: Throwable): RunnerConnectionIssue = when {
        failure is PairingPayloadException -> RunnerConnectionIssue.INVALID_PAYLOAD
        failure is CredentialStoreException -> RunnerConnectionIssue.CREDENTIAL_UNAVAILABLE
        failure is RunnerResponseIntegrityException -> RunnerConnectionIssue.PAIRING_INVALID
        generateSequence(failure) { it.cause }.take(16).any { it is java.security.cert.CertificateException } -> RunnerConnectionIssue.PAIRING_INVALID
        (failure as? RunnerApiException)?.statusCode == 401 -> RunnerConnectionIssue.AUTHENTICATION_UNKNOWN
        else -> RunnerConnectionIssue.NETWORK_UNAVAILABLE
    }
}
