package com.sanka1610.reprodroid.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class RunnerTransportMode { DEVELOPMENT_HTTP, PAIRED_HTTPS }
enum class RunnerPairingState { PENDING_APPROVAL, APPROVED, REJECTED, EXPIRED, FAILED, REVOKED }
enum class RunnerRevocationKnowledge { UNKNOWN, ACTIVE, REVOKED }

/** Non-secret connection metadata. Raw pairing and bearer credentials never enter Room. */
@Entity(
    tableName = "runner_connections",
    indices = [Index("active"), Index("pairingState"), Index("updatedAt")],
)
data class RunnerConnectionEntity(
    @PrimaryKey val runnerId: String,
    val endpoint: String,
    val rootSpkiSha256: String,
    val caCertificateFileReference: String,
    val caCertificateSha256: String,
    val credentialFileReference: String,
    val principalId: String?,
    val displayName: String,
    val transportMode: String,
    val pairingRequestId: String?,
    val pairingState: String,
    val confirmationFingerprint: String?,
    val pairingExpiresAt: String?,
    val revocationKnowledge: String,
    val active: Boolean,
    val createdAt: String,
    val updatedAt: String,
)
