package com.sanka1610.reprodroid.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ManagedAppDao {
    @Transaction
    @Query("SELECT * FROM registered_apps ORDER BY displayName COLLATE NOCASE, createdAt")
    fun observeRegisteredApps(): Flow<List<RegisteredAppRecord>>

    @Transaction
    @Query("SELECT * FROM registered_apps WHERE registeredAppId = :registeredAppId")
    fun observeRegisteredApp(registeredAppId: String): Flow<RegisteredAppRecord?>

    @Query("SELECT * FROM registered_apps WHERE registeredAppId = :registeredAppId")
    suspend fun getRegisteredApp(registeredAppId: String): RegisteredAppEntity?

    @Query("SELECT * FROM registered_apps WHERE canonicalRepositoryUrl = :canonicalRepositoryUrl")
    suspend fun getRegisteredAppByCanonicalUrl(canonicalRepositoryUrl: String): RegisteredAppEntity?

    @Query("SELECT * FROM release_snapshots WHERE releaseSnapshotId = :releaseSnapshotId")
    suspend fun getReleaseSnapshot(releaseSnapshotId: String): ReleaseSnapshotEntity?

    @Query(
        "SELECT * FROM release_snapshots WHERE registeredAppId = :registeredAppId " +
            "AND providerReleaseId = :providerReleaseId",
    )
    suspend fun getReleaseSnapshot(
        registeredAppId: String,
        providerReleaseId: Long,
    ): ReleaseSnapshotEntity?

    @Query("SELECT * FROM release_assets WHERE releaseAssetId = :releaseAssetId")
    suspend fun getReleaseAsset(releaseAssetId: String): ReleaseAssetEntity?

    @Query(
        "SELECT * FROM release_assets WHERE releaseSnapshotId = :releaseSnapshotId " +
            "AND providerAssetId = :providerAssetId",
    )
    suspend fun getReleaseAsset(releaseSnapshotId: String, providerAssetId: Long): ReleaseAssetEntity?

    @Query("SELECT * FROM release_assets WHERE downloadStatus = 'DOWNLOADING'")
    suspend fun getInterruptedDownloads(): List<ReleaseAssetEntity>

    @Query("SELECT * FROM release_assets WHERE downloadStatus = 'VERIFIED'")
    suspend fun getVerifiedDownloads(): List<ReleaseAssetEntity>

    @Upsert
    suspend fun upsertRegisteredApp(app: RegisteredAppEntity)

    @Upsert
    suspend fun upsertReleaseSnapshot(snapshot: ReleaseSnapshotEntity)

    @Upsert
    suspend fun upsertReleaseAsset(asset: ReleaseAssetEntity)
}
