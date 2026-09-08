package com.sanka1610.reprodroid.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ReleaseCheckDao {
    @Query("SELECT * FROM release_check_settings WHERE singletonId = 1")
    fun observeSettings(): Flow<ReleaseCheckSettingsEntity?>

    @Query("SELECT * FROM release_check_settings WHERE singletonId = 1")
    suspend fun getSettings(): ReleaseCheckSettingsEntity?

    @Query("SELECT * FROM app_release_check_overrides WHERE registeredAppId = :registeredAppId")
    suspend fun getOverride(registeredAppId: String): AppReleaseCheckOverrideEntity?

    @Query("SELECT * FROM app_release_check_overrides ORDER BY registeredAppId")
    fun observeOverrides(): Flow<List<AppReleaseCheckOverrideEntity>>

    @Query("SELECT * FROM release_schedule_states WHERE registeredAppId = :registeredAppId")
    suspend fun getScheduleState(registeredAppId: String): ReleaseScheduleStateEntity?

    @Query("SELECT * FROM release_schedule_states ORDER BY nextEligibleAt, registeredAppId")
    fun observeScheduleStates(): Flow<List<ReleaseScheduleStateEntity>>

    @Query(
        "SELECT * FROM registered_apps WHERE trackingState = 'ACTIVE' " +
            "ORDER BY registeredAppId",
    )
    suspend fun getActiveApps(): List<RegisteredAppEntity>

    @Query(
        "SELECT * FROM release_candidates WHERE state != 'OBSOLETE' " +
            "ORDER BY unseen DESC, publishedAt DESC, LENGTH(providerReleaseId) DESC, providerReleaseId DESC",
    )
    fun observeCandidates(): Flow<List<ReleaseCandidateEntity>>

    @Query(
        "SELECT * FROM release_candidates WHERE registeredAppId = :registeredAppId " +
            "AND state != 'OBSOLETE' ORDER BY publishedAt DESC, " +
            "LENGTH(providerReleaseId) DESC, providerReleaseId DESC",
    )
    suspend fun getCandidates(registeredAppId: String): List<ReleaseCandidateEntity>

    @Query(
        "SELECT * FROM release_candidates WHERE registeredAppId = :registeredAppId " +
            "ORDER BY publishedAt DESC, LENGTH(providerReleaseId) DESC, providerReleaseId DESC",
    )
    suspend fun getAllCandidates(registeredAppId: String): List<ReleaseCandidateEntity>

    @Query(
        "SELECT * FROM release_candidates WHERE registeredAppId = :registeredAppId " +
            "AND observationSha256 = :observationSha256",
    )
    suspend fun getCandidateByObservation(
        registeredAppId: String,
        observationSha256: String,
    ): ReleaseCandidateEntity?

    @Query("SELECT * FROM release_candidates WHERE candidateId = :candidateId")
    suspend fun getCandidate(candidateId: String): ReleaseCandidateEntity?

    @Query("UPDATE release_candidates SET unseen = 0 WHERE candidateId = :candidateId")
    suspend fun markCandidateSeen(candidateId: String)

    @Query("SELECT * FROM provider_cooldowns WHERE provider = :provider AND instance = :instance")
    suspend fun getCooldown(provider: String, instance: String): ProviderCooldownEntity?

    @Query("SELECT * FROM provider_cooldowns ORDER BY provider, instance")
    suspend fun getCooldowns(): List<ProviderCooldownEntity>

    @Query(
        "SELECT * FROM provider_representations WHERE provider = :provider AND instance = :instance " +
            "AND providerRepositoryId = :providerRepositoryId ORDER BY endpointKey",
    )
    suspend fun getRepresentations(
        provider: String,
        instance: String,
        providerRepositoryId: String,
    ): List<ProviderRepresentationEntity>

    @Query(
        "SELECT * FROM notification_outbox WHERE state = 'PENDING' " +
            "ORDER BY createdAt, outboxId LIMIT :limit",
    )
    suspend fun getPendingOutbox(limit: Int): List<NotificationOutboxEntity>

    @Query("SELECT * FROM notification_dedup_headers WHERE dedupKey = :dedupKey")
    suspend fun getDedupHeader(dedupKey: String): NotificationDedupHeaderEntity?

    @Query("SELECT * FROM notification_dedup_headers WHERE notificationId = :notificationId")
    suspend fun getDedupHeaderByNotificationId(notificationId: Int): NotificationDedupHeaderEntity?

    @Upsert
    suspend fun upsertSettings(settings: ReleaseCheckSettingsEntity)

    @Upsert
    suspend fun upsertOverride(override: AppReleaseCheckOverrideEntity)

    @Upsert
    suspend fun upsertScheduleState(state: ReleaseScheduleStateEntity)

    @Upsert
    suspend fun upsertCheckRun(run: ReleaseCheckRunEntity)

    @Upsert
    suspend fun upsertCandidate(candidate: ReleaseCandidateEntity)

    @Upsert
    suspend fun upsertOutbox(outbox: NotificationOutboxEntity)

    @Upsert
    suspend fun upsertCooldown(cooldown: ProviderCooldownEntity)

    @Upsert
    suspend fun upsertDedupHeader(header: NotificationDedupHeaderEntity)

    @Upsert
    suspend fun upsertRepresentations(representations: List<ProviderRepresentationEntity>)

    @Query("DELETE FROM provider_cooldowns WHERE provider = :provider AND instance = :instance")
    suspend fun deleteCooldown(provider: String, instance: String)

    @Query(
        "DELETE FROM release_check_runs WHERE checkRunId IN (" +
            "SELECT old.checkRunId FROM release_check_runs old WHERE julianday(old.finishedAt) < julianday(:cutoff) " +
            "AND ((old.outcome IN ('PROVIDER_RATE_LIMITED', 'NETWORK_ERROR', 'PROVIDER_UNAVAILABLE') " +
            "AND EXISTS (SELECT 1 FROM release_check_runs newer WHERE newer.registeredAppId = old.registeredAppId " +
            "AND (julianday(newer.finishedAt) > julianday(old.finishedAt) OR " +
            "(julianday(newer.finishedAt) = julianday(old.finishedAt) AND newer.checkRunId > old.checkRunId)))) " +
            "OR (old.outcome NOT IN ('PROVIDER_RATE_LIMITED', 'NETWORK_ERROR', 'PROVIDER_UNAVAILABLE') " +
            "AND EXISTS (SELECT 1 FROM release_check_runs newer WHERE newer.registeredAppId = old.registeredAppId " +
            "AND newer.outcome NOT IN ('PROVIDER_RATE_LIMITED', 'NETWORK_ERROR', 'PROVIDER_UNAVAILABLE') " +
            "AND (julianday(newer.finishedAt) > julianday(old.finishedAt) OR " +
            "(julianday(newer.finishedAt) = julianday(old.finishedAt) AND newer.checkRunId > old.checkRunId))))) " +
            "ORDER BY julianday(old.finishedAt), old.checkRunId LIMIT :limit)",
    )
    suspend fun deleteExpiredCheckRuns(cutoff: String, limit: Int): Int

    @Query(
        "DELETE FROM notification_outbox WHERE outboxId IN (" +
            "SELECT outboxId FROM notification_outbox WHERE terminalAt IS NOT NULL " +
            "AND julianday(terminalAt) < julianday(:cutoff) AND state != 'PENDING' " +
            "ORDER BY julianday(terminalAt), outboxId LIMIT :limit)",
    )
    suspend fun deleteExpiredOutbox(cutoff: String, limit: Int): Int

    @Query(
        "DELETE FROM notification_dedup_headers WHERE dedupKey IN (" +
            "SELECT d.dedupKey FROM notification_dedup_headers d " +
            "LEFT JOIN release_candidates c ON c.registeredAppId = d.registeredAppId " +
            "AND c.observationSha256 = d.observationSha256 " +
            "WHERE julianday(d.lastSeenAt) < julianday(:cutoff) AND c.candidateId IS NULL " +
            "ORDER BY julianday(d.lastSeenAt), d.dedupKey LIMIT :limit)",
    )
    suspend fun deleteExpiredDedupHeaders(cutoff: String, limit: Int): Int
}
