package com.sanka1610.reprodroid.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface StorageDao {
    @Query("SELECT * FROM resource_availability ORDER BY ownerType, ownerId, resourceKind, resourceId")
    fun observeAvailability(): Flow<List<ResourceAvailabilityEntity>>

    @Query("SELECT * FROM resource_availability ORDER BY ownerType, ownerId, resourceKind, resourceId")
    suspend fun getAvailability(): List<ResourceAvailabilityEntity>

    @Query(
        "SELECT * FROM resource_availability WHERE ownerType = :ownerType AND ownerId = :ownerId " +
            "AND resourceKind = :resourceKind AND resourceId = :resourceId",
    )
    suspend fun getAvailability(
        ownerType: String,
        ownerId: String,
        resourceKind: String,
        resourceId: String,
    ): ResourceAvailabilityEntity?

    @Upsert
    suspend fun upsertAvailability(availability: ResourceAvailabilityEntity)

    @Query("DELETE FROM resource_availability WHERE resourceKind = :resourceKind AND resourceId = :resourceId")
    suspend fun deleteAvailability(resourceKind: String, resourceId: String)

    @Query("SELECT * FROM storage_reservations WHERE state = 'ACTIVE' ORDER BY createdAt, reservationId")
    suspend fun getActiveReservations(): List<StorageReservationEntity>

    @Query(
        "SELECT * FROM storage_reservations WHERE state = 'ACTIVE' AND area = :area " +
            "AND purpose = :purpose AND resourceKind = :resourceKind AND resourceId = :resourceId LIMIT 1",
    )
    suspend fun getActiveReservation(
        area: String,
        purpose: String,
        resourceKind: String,
        resourceId: String,
    ): StorageReservationEntity?

    @Query("SELECT COALESCE(SUM(requestedBytes), 0) FROM storage_reservations WHERE area = :area AND state = 'ACTIVE'")
    suspend fun getActiveReservedBytes(area: String): Long

    @Query("SELECT COUNT(*) FROM storage_reservations WHERE state = 'RECONCILIATION_REQUIRED'")
    suspend fun getReservationReconciliationCount(): Int

    @Query("SELECT * FROM storage_reservations WHERE state = 'ACTIVE' AND resourceKind = :resourceKind AND resourceId = :resourceId")
    suspend fun getActiveReservations(resourceKind: String, resourceId: String): List<StorageReservationEntity>

    @Upsert
    suspend fun upsertReservation(reservation: StorageReservationEntity)

    @Query("SELECT * FROM release_assets ORDER BY releaseAssetId")
    suspend fun getReleaseAssets(): List<ReleaseAssetEntity>

    @Query("SELECT * FROM artifacts WHERE downloadStatus = 'VERIFIED' ORDER BY artifactId")
    suspend fun getVerifiedArtifacts(): List<ArtifactEntity>

    @Query("SELECT * FROM artifacts ORDER BY artifactId")
    suspend fun getArtifacts(): List<ArtifactEntity>

    @Query("SELECT releaseAssetId FROM release_install_attempts WHERE status IN ('PREPARING', 'COMMITTED', 'PENDING_USER_ACTION')")
    suspend fun getActiveReleaseInstallAssetIds(): List<String>

    @Query("SELECT artifactId FROM install_attempts WHERE status IN ('PREPARING', 'COMMITTED', 'PENDING_USER_ACTION')")
    suspend fun getActiveInstallArtifactIds(): List<String>

    @Query("SELECT * FROM artifacts WHERE artifactId = :artifactId ORDER BY jobId LIMIT 1")
    suspend fun getArtifact(artifactId: String): ArtifactEntity?

    @Upsert
    suspend fun upsertCleanupRun(run: CleanupRunEntity)

    @Upsert
    suspend fun upsertCleanupItems(items: List<CleanupItemEntity>)

    @Query("SELECT * FROM cleanup_runs WHERE previewId = :previewId")
    suspend fun getCleanupRunByPreview(previewId: String): CleanupRunEntity?

    @Query("SELECT * FROM cleanup_runs WHERE cleanupRunId = :cleanupRunId")
    suspend fun getCleanupRun(cleanupRunId: String): CleanupRunEntity?

    @Query("SELECT * FROM cleanup_runs ORDER BY createdAt, cleanupRunId")
    suspend fun getCleanupRuns(): List<CleanupRunEntity>

    @Query("SELECT * FROM cleanup_items ORDER BY cleanupRunId, resourceKind, resourceId, itemId")
    suspend fun getCleanupItems(): List<CleanupItemEntity>

    @Query("SELECT * FROM cleanup_runs WHERE state IN ('APPLYING', 'RECONCILIATION_REQUIRED') ORDER BY createdAt, cleanupRunId")
    suspend fun getCleanupRunsToReconcile(): List<CleanupRunEntity>

    @Query("SELECT COUNT(*) FROM cleanup_runs WHERE state = 'RECONCILIATION_REQUIRED'")
    suspend fun getCleanupReconciliationCount(): Int

    @Query("SELECT * FROM cleanup_items WHERE cleanupRunId = :cleanupRunId ORDER BY resourceKind, resourceId, itemId")
    suspend fun getCleanupItems(cleanupRunId: String): List<CleanupItemEntity>

    @Upsert
    suspend fun upsertCleanupItem(item: CleanupItemEntity)

    @Upsert
    suspend fun upsertRetentionHold(hold: RetentionHoldEntity)

    @Query("SELECT * FROM retention_holds WHERE state = 'ACTIVE' ORDER BY createdAt, holdId")
    suspend fun getActiveRetentionHolds(): List<RetentionHoldEntity>

    @Upsert
    suspend fun upsertAuditExport(export: AuditExportEntity)

    @Query("SELECT * FROM audit_exports WHERE auditExportId = :auditExportId")
    suspend fun getAuditExport(auditExportId: String): AuditExportEntity?

    @Query("SELECT * FROM audit_exports WHERE state IN ('PREPARING', 'STAGED', 'COPYING') ORDER BY createdAt")
    suspend fun getActiveAuditExports(): List<AuditExportEntity>

    @Query("SELECT * FROM audit_exports WHERE stagingName IS NOT NULL ORDER BY createdAt")
    suspend fun getRetainedAuditExports(): List<AuditExportEntity>

    @Query("SELECT * FROM audit_exports ORDER BY createdAt DESC, auditExportId DESC LIMIT 1")
    suspend fun getLatestAuditExport(): AuditExportEntity?
}
