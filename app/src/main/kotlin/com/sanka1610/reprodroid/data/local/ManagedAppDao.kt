package com.sanka1610.reprodroid.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ManagedAppDao {
    @Query("SELECT * FROM global_settings WHERE singletonId = 1")
    fun observeGlobalSettings(): Flow<GlobalSettingsEntity?>

    @Query("SELECT * FROM global_settings WHERE singletonId = 1")
    suspend fun getGlobalSettings(): GlobalSettingsEntity?

    @Transaction
    @Query("SELECT * FROM registered_apps ORDER BY displayName COLLATE NOCASE, createdAt")
    fun observeRegisteredApps(): Flow<List<RegisteredAppRecord>>

    @Query("SELECT * FROM registered_apps")
    suspend fun getRegisteredApps(): List<RegisteredAppEntity>

    @Transaction
    @Query("SELECT * FROM registered_apps WHERE registeredAppId = :registeredAppId")
    fun observeRegisteredApp(registeredAppId: String): Flow<RegisteredAppRecord?>

    @Transaction
    @Query("SELECT * FROM registered_apps WHERE registeredAppId = :registeredAppId")
    suspend fun getRegisteredAppRecord(registeredAppId: String): RegisteredAppRecord?

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

    @Query("SELECT * FROM release_install_attempts WHERE attemptId = :attemptId")
    suspend fun getReleaseInstallAttempt(attemptId: String): ReleaseInstallAttemptEntity?

    @Query(
        "SELECT * FROM release_install_attempts WHERE status IN " +
            "('PREPARING', 'COMMITTED', 'PENDING_USER_ACTION')",
    )
    suspend fun getPendingReleaseInstallAttempts(): List<ReleaseInstallAttemptEntity>

    @Query("SELECT * FROM comparison_runs WHERE comparisonRunId = :comparisonRunId")
    suspend fun getComparisonRun(comparisonRunId: String): ComparisonRunEntity?

    @Query("SELECT * FROM comparison_entries WHERE comparisonRunId = :comparisonRunId ORDER BY entryName")
    suspend fun getComparisonEntries(comparisonRunId: String): List<ComparisonEntryEntity>

    @Query(
        "SELECT * FROM advanced_comparison_entries WHERE comparisonRunId = :comparisonRunId " +
            "ORDER BY axis, entryName",
    )
    suspend fun getAdvancedComparisonEntries(comparisonRunId: String): List<AdvancedComparisonEntryEntity>

    @Upsert
    suspend fun upsertRegisteredApp(app: RegisteredAppEntity)

    @Upsert
    suspend fun upsertReleaseSnapshot(snapshot: ReleaseSnapshotEntity)

    @Upsert
    suspend fun upsertReleaseAsset(asset: ReleaseAssetEntity)

    @Upsert
    suspend fun upsertComparisonRun(comparisonRun: ComparisonRunEntity)

    @Upsert
    suspend fun upsertComparisonEntries(entries: List<ComparisonEntryEntity>)

    @Upsert
    suspend fun upsertAdvancedComparisonEntries(entries: List<AdvancedComparisonEntryEntity>)

    @Upsert
    suspend fun upsertGlobalSettings(settings: GlobalSettingsEntity)

    @Upsert
    suspend fun upsertReleaseInstallAttempt(attempt: ReleaseInstallAttemptEntity)

    @Query("DELETE FROM comparison_entries WHERE comparisonRunId = :comparisonRunId")
    suspend fun deleteComparisonEntries(comparisonRunId: String)

    @Query("DELETE FROM advanced_comparison_entries WHERE comparisonRunId = :comparisonRunId")
    suspend fun deleteAdvancedComparisonEntries(comparisonRunId: String)

    @Query("DELETE FROM registered_apps WHERE registeredAppId = :registeredAppId")
    suspend fun deleteRegisteredApp(registeredAppId: String)
}
