package com.sanka1610.reprodroid.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class StorageOwnerType { ANDROID, RUNNER }
enum class ResourceAvailabilityState { PRESENT, DELETED, MISSING, CORRUPT, UNKNOWN }
enum class StorageReservationState { ACTIVE, CONSUMED, RELEASED, RECONCILIATION_REQUIRED }
enum class RetentionHoldState { ACTIVE, RELEASED, RECONCILIATION_REQUIRED }
enum class LocalCleanupRunState { PREVIEWED, APPLYING, COMPLETE, PARTIAL, REJECTED, RECONCILIATION_REQUIRED }
enum class LocalCleanupItemResult { DELETED, ALREADY_MISSING, SKIPPED_PROTECTED, FAILED }
enum class AuditExportState { PREPARING, STAGED, COPYING, COMPLETE, FAILED }

@Entity(
    tableName = "resource_availability",
    primaryKeys = ["ownerType", "ownerId", "resourceKind", "resourceId"],
    indices = [Index("state"), Index(value = ["resourceKind", "resourceId"])],
)
data class ResourceAvailabilityEntity(
    val ownerType: String,
    val ownerId: String,
    val resourceKind: String,
    val resourceId: String,
    val state: String,
    val observedBytes: Long?,
    val knownSha256: String?,
    val lastUsedAt: String?,
    val checkedAt: String,
    val deletionRunId: String?,
    val deletionReason: String?,
)

@Entity(
    tableName = "storage_reservations",
    indices = [Index("state"), Index(value = ["area", "resourceKind", "resourceId"])],
)
data class StorageReservationEntity(
    @PrimaryKey val reservationId: String,
    val area: String,
    val purpose: String,
    val resourceKind: String,
    val resourceId: String,
    val requestedBytes: Long,
    val principalId: String,
    val operationId: String,
    val state: String,
    val createdAt: String,
    val updatedAt: String,
)

@Entity(
    tableName = "retention_holds",
    indices = [Index("runnerId"), Index(value = ["resourceKind", "resourceId"]), Index("state")],
)
data class RetentionHoldEntity(
    @PrimaryKey val holdId: String,
    val runnerId: String,
    val principalId: String,
    val resourceKind: String,
    val resourceId: String,
    val reason: String,
    val clientReferenceType: String,
    val clientReferenceId: String,
    val requestSha256: String,
    val state: String,
    val createdOperationId: String,
    val releasedOperationId: String?,
    val createdAt: String,
    val releasedAt: String?,
)

@Entity(tableName = "cleanup_runs", indices = [Index("state"), Index("ownerType")])
data class CleanupRunEntity(
    @PrimaryKey val cleanupRunId: String,
    val previewId: String,
    val ownerType: String,
    val ownerId: String,
    val area: String,
    val state: String,
    val filterSha256: String,
    val truncated: Boolean,
    val expiresAt: String,
    val releasedBytes: Long,
    val startedAt: String?,
    val finishedAt: String?,
    val createdAt: String,
)

@Entity(
    tableName = "cleanup_items",
    foreignKeys = [
        ForeignKey(
            entity = CleanupRunEntity::class,
            parentColumns = ["cleanupRunId"],
            childColumns = ["cleanupRunId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("cleanupRunId"), Index(value = ["resourceKind", "resourceId"])],
)
data class CleanupItemEntity(
    @PrimaryKey val itemId: String,
    val cleanupRunId: String,
    val resourceKind: String,
    val resourceId: String,
    val observedBytes: Long,
    val observedToken: String,
    val eligibleAt: String,
    val protectionReasons: String,
    val selected: Boolean,
    val result: String?,
    val releasedBytes: Long,
    val reasonCode: String?,
    val reasonMessage: String?,
)

@Entity(tableName = "audit_exports", indices = [Index("state"), Index("createdAt")])
data class AuditExportEntity(
    @PrimaryKey val auditExportId: String,
    val scopeType: String,
    val scopeJson: String,
    val filterJson: String,
    val state: String,
    val stagingName: String?,
    val payloadSha256: String?,
    val bundleSha256: String?,
    val sizeBytes: Long?,
    val recordCount: Int?,
    val errorCode: String?,
    val createdAt: String,
    val updatedAt: String,
)
