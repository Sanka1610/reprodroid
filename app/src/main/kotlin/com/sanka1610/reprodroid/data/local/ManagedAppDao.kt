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
    @Query("SELECT * FROM registered_apps ORDER BY registeredAppId")
    suspend fun getRegisteredAppRecords(): List<RegisteredAppRecord>

    @Transaction
    @Query("SELECT * FROM registered_apps WHERE registeredAppId = :registeredAppId")
    fun observeRegisteredApp(registeredAppId: String): Flow<RegisteredAppRecord?>

    @Transaction
    @Query("SELECT * FROM registered_apps WHERE registeredAppId = :registeredAppId")
    suspend fun getRegisteredAppRecord(registeredAppId: String): RegisteredAppRecord?

    @Query("SELECT * FROM registered_apps WHERE registeredAppId = :registeredAppId")
    suspend fun getRegisteredApp(registeredAppId: String): RegisteredAppEntity?

    @Query("SELECT * FROM registered_apps WHERE canonicalRepositoryUrl = :canonicalRepositoryUrl ORDER BY createdAt")
    suspend fun getRegisteredAppsByCanonicalUrl(canonicalRepositoryUrl: String): List<RegisteredAppEntity>

    @Query("SELECT * FROM app_repository_bindings WHERE registeredAppId = :registeredAppId")
    suspend fun getRepositoryBinding(registeredAppId: String): AppRepositoryBindingEntity?

    @Query(
        "SELECT * FROM app_repository_bindings WHERE provider = :provider AND instance = :instance " +
            "AND providerRepositoryId = :providerRepositoryId AND registrationSlot = :registrationSlot",
    )
    suspend fun getRepositoryBinding(
        provider: String,
        instance: String,
        providerRepositoryId: String,
        registrationSlot: String,
    ): AppRepositoryBindingEntity?

    @Query("SELECT * FROM source_discoveries WHERE discoveryId = :discoveryId")
    suspend fun getSourceDiscovery(discoveryId: String): SourceDiscoveryEntity?

    @Query("SELECT * FROM gradle_candidates WHERE discoveryId = :discoveryId ORDER BY relativePath")
    suspend fun getGradleCandidates(discoveryId: String): List<GradleCandidateEntity>

    @Query("SELECT * FROM app_source_heads WHERE registeredAppId = :registeredAppId")
    suspend fun getAppSourceHead(registeredAppId: String): AppSourceHeadEntity?

    @Query(
        "SELECT * FROM app_build_configurations WHERE registeredAppId = :registeredAppId " +
            "AND revision = :revision",
    )
    suspend fun getBuildConfiguration(registeredAppId: String, revision: Long): AppBuildConfigurationEntity?

    @Query(
        "SELECT * FROM app_build_configurations WHERE registeredAppId = :registeredAppId " +
            "AND contentSha256 = :contentSha256",
    )
    suspend fun getBuildConfigurationByHash(
        registeredAppId: String,
        contentSha256: String,
    ): AppBuildConfigurationEntity?

    @Query("SELECT MAX(revision) FROM app_build_configurations WHERE registeredAppId = :registeredAppId")
    suspend fun getLatestBuildConfigurationRevision(registeredAppId: String): Long?

    @Query("SELECT * FROM release_snapshots WHERE releaseSnapshotId = :releaseSnapshotId")
    suspend fun getReleaseSnapshot(releaseSnapshotId: String): ReleaseSnapshotEntity?

    @Query(
        "SELECT * FROM release_snapshots WHERE registeredAppId = :registeredAppId " +
            "AND providerReleaseId = :providerReleaseId " +
            "ORDER BY lastObservedAt DESC, releaseSnapshotId DESC LIMIT 1",
    )
    suspend fun getReleaseSnapshot(
        registeredAppId: String,
        providerReleaseId: Long,
    ): ReleaseSnapshotEntity?

    @Query(
        "SELECT * FROM release_snapshots WHERE registeredAppId = :registeredAppId " +
            "AND observationSha256 = :observationSha256",
    )
    suspend fun getReleaseSnapshotByObservationHash(
        registeredAppId: String,
        observationSha256: String,
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

    @Query(
        "SELECT * FROM apk_entry_evidence WHERE comparisonRunId = :comparisonRunId " +
            "ORDER BY axis, entryName",
    )
    suspend fun getApkEntryEvidence(comparisonRunId: String): List<ApkEntryEvidenceEntity>

    @Query(
        "SELECT * FROM semantic_difference_evidence WHERE comparisonRunId = :comparisonRunId " +
            "ORDER BY axis, component, stableKey",
    )
    suspend fun getSemanticDifferenceEvidence(comparisonRunId: String): List<SemanticDifferenceEvidenceEntity>

    @Upsert
    suspend fun upsertRegisteredApp(app: RegisteredAppEntity)

    @Upsert
    suspend fun upsertRepositoryBinding(binding: AppRepositoryBindingEntity)

    @Upsert
    suspend fun upsertSourceDiscovery(discovery: SourceDiscoveryEntity)

    @Upsert
    suspend fun upsertGradleCandidates(candidates: List<GradleCandidateEntity>)

    @Upsert
    suspend fun upsertBuildConfiguration(configuration: AppBuildConfigurationEntity)

    @Upsert
    suspend fun upsertAppSourceHead(head: AppSourceHeadEntity)

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
    suspend fun upsertApkEntryEvidence(entries: List<ApkEntryEvidenceEntity>)

    @Upsert
    suspend fun upsertAdvancedComparisonSummary(summary: AdvancedComparisonSummaryEntity)

    @Upsert
    suspend fun upsertSemanticDifferenceEvidence(entries: List<SemanticDifferenceEvidenceEntity>)

    @Upsert
    suspend fun upsertGlobalSettings(settings: GlobalSettingsEntity)

    @Upsert
    suspend fun upsertReleaseInstallAttempt(attempt: ReleaseInstallAttemptEntity)

    @Query("DELETE FROM comparison_entries WHERE comparisonRunId = :comparisonRunId")
    suspend fun deleteComparisonEntries(comparisonRunId: String)

    @Query("DELETE FROM advanced_comparison_entries WHERE comparisonRunId = :comparisonRunId")
    suspend fun deleteAdvancedComparisonEntries(comparisonRunId: String)

    @Query("DELETE FROM apk_entry_evidence WHERE comparisonRunId = :comparisonRunId AND axis = :axis")
    suspend fun deleteApkEntryEvidence(comparisonRunId: String, axis: String)

    @Query("DELETE FROM semantic_difference_evidence WHERE comparisonRunId = :comparisonRunId AND axis = :axis")
    suspend fun deleteSemanticDifferenceEvidence(comparisonRunId: String, axis: String)

    @Query("DELETE FROM registered_apps WHERE registeredAppId = :registeredAppId")
    suspend fun deleteRegisteredApp(registeredAppId: String)

    @Query(
        "UPDATE source_discoveries SET state = 'INTERRUPTED', reason = 'PROCESS_RESTART', " +
            "finishedAt = :finishedAt WHERE state IN ('RESOLVING', 'SCANNING_TREE')",
    )
    suspend fun interruptRunningSourceDiscoveries(finishedAt: String)
}
