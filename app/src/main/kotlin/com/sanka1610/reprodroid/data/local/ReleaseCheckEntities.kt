package com.sanka1610.reprodroid.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

enum class ReleaseCheckScheduleMode {
    INTERVAL,
    DAILY_LOCAL_TIME,
}

enum class ReleaseCheckChannel {
    STABLE_ONLY,
    INCLUDE_PRERELEASE,
}

enum class ReleaseCheckNetworkPolicy {
    ANY_AVAILABLE,
    UNMETERED_ONLY,
}

enum class ReleaseCheckBatteryPolicy {
    ANY,
    ABOVE_20_PERCENT,
}

enum class ReleaseCheckTrigger {
    SCHEDULED,
    MANUAL,
}

enum class ReleaseCheckWaitingReason {
    NONE,
    GLOBAL_DISABLED,
    APP_DISABLED,
    INACTIVE,
    DEFERRED_NETWORK,
    DEFERRED_BATTERY,
    PROVIDER_COOLDOWN,
    RETRY_BACKOFF,
    INVALID_STATE,
}

enum class ReleaseCheckOutcome {
    NOT_MODIFIED,
    NEW_RELEASE_DISCOVERED,
    VERIFIED_UPDATE_AVAILABLE,
    ASSET_SELECTION_REQUIRED,
    NO_PUBLISHED_RELEASE,
    NO_APK_ASSET,
    PROVIDER_RATE_LIMITED,
    ACCESS_DENIED,
    NOT_FOUND_OR_NOT_PUBLIC,
    NETWORK_ERROR,
    PROVIDER_UNAVAILABLE,
    INVALID_METADATA,
    LIMIT_EXCEEDED,
    CANCELLED,
}

enum class ReleaseCandidateState {
    NEW_RELEASE_DISCOVERED,
    VERIFICATION_REQUIRED,
    VERIFIED_UPDATE_AVAILABLE,
    ASSET_SELECTION_REQUIRED,
    NO_APK_ASSET,
    OBSOLETE,
}

enum class ReleaseNotificationType {
    NEW_RELEASE,
    VERIFICATION_REQUIRED,
    ASSET_SELECTION_REQUIRED,
    VERIFIED_UPDATE_AVAILABLE,
}

enum class NotificationOutboxState {
    PENDING,
    DELIVERED,
    SUPPRESSED_PERMISSION,
    SUPPRESSED_MUTED,
    SUPPRESSED_INACTIVE,
    FAILED,
}

@Entity(tableName = "release_check_settings", primaryKeys = ["singletonId"])
data class ReleaseCheckSettingsEntity(
    val singletonId: Int = SINGLETON_ID,
    val enabled: Boolean = true,
    val scheduleMode: String = ReleaseCheckScheduleMode.INTERVAL.name,
    val intervalHours: Int = DEFAULT_INTERVAL_HOURS,
    val dailyLocalMinute: Int = DEFAULT_DAILY_LOCAL_MINUTE,
    val releaseChannel: String = ReleaseCheckChannel.STABLE_ONLY.name,
    val networkPolicy: String = ReleaseCheckNetworkPolicy.UNMETERED_ONLY.name,
    val batteryPolicy: String = ReleaseCheckBatteryPolicy.ANY.name,
    val revision: Long = 1,
    val updatedAt: String,
) {
    companion object {
        const val SINGLETON_ID = 1
        const val DEFAULT_INTERVAL_HOURS = 6
        const val DEFAULT_DAILY_LOCAL_MINUTE = 7 * 60
    }
}

@Entity(
    tableName = "app_release_check_overrides",
    primaryKeys = ["registeredAppId"],
    foreignKeys = [
        ForeignKey(
            entity = RegisteredAppEntity::class,
            parentColumns = ["registeredAppId"],
            childColumns = ["registeredAppId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class AppReleaseCheckOverrideEntity(
    val registeredAppId: String,
    val enabled: Boolean? = null,
    val scheduleMode: String? = null,
    val intervalHours: Int? = null,
    val dailyLocalMinute: Int? = null,
    val releaseChannel: String? = null,
    val networkPolicy: String? = null,
    val batteryPolicy: String? = null,
    val notificationMuted: Boolean = false,
    val revision: Long = 1,
    val updatedAt: String,
)

@Entity(
    tableName = "release_schedule_states",
    primaryKeys = ["registeredAppId"],
    foreignKeys = [
        ForeignKey(
            entity = RegisteredAppEntity::class,
            parentColumns = ["registeredAppId"],
            childColumns = ["registeredAppId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("nextEligibleAt"), Index("waitingReason")],
)
data class ReleaseScheduleStateEntity(
    val registeredAppId: String,
    val lastAttemptAt: String? = null,
    val lastTerminalAt: String? = null,
    val nextEligibleAt: String,
    val waitingReason: String = ReleaseCheckWaitingReason.NONE.name,
    val consecutiveRetry: Int = 0,
    val updatedAt: String,
)

@Entity(
    tableName = "release_check_runs",
    foreignKeys = [
        ForeignKey(
            entity = RegisteredAppEntity::class,
            parentColumns = ["registeredAppId"],
            childColumns = ["registeredAppId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("registeredAppId"), Index("finishedAt"), Index("outcome")],
    primaryKeys = ["checkRunId"],
)
data class ReleaseCheckRunEntity(
    val checkRunId: String,
    val registeredAppId: String,
    val trigger: String,
    val effectiveSettingsJson: String,
    val outcome: String,
    val providerEvidenceJson: String,
    val startedAt: String,
    val finishedAt: String,
)

@Entity(
    tableName = "release_candidates",
    foreignKeys = [
        ForeignKey(
            entity = RegisteredAppEntity::class,
            parentColumns = ["registeredAppId"],
            childColumns = ["registeredAppId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("registeredAppId"),
        Index("state"),
        Index("lastSeenAt"),
        Index(value = ["registeredAppId", "observationSha256"], unique = true),
    ],
    primaryKeys = ["candidateId"],
)
data class ReleaseCandidateEntity(
    val candidateId: String,
    val registeredAppId: String,
    val provider: String,
    val instance: String,
    val providerRepositoryId: String,
    val providerReleaseId: String,
    val tagName: String,
    val resolvedCommitSha: String,
    val releaseName: String,
    val releaseUrl: String,
    val targetCommitishRaw: String,
    val isPrerelease: Boolean,
    val isImmutable: Boolean,
    val releaseCreatedAt: String,
    val publishedAt: String,
    val assetsJson: String,
    val observationSha256: String,
    val state: String,
    val unseen: Boolean = true,
    val firstSeenAt: String,
    val lastSeenAt: String,
)

@Entity(
    tableName = "notification_outbox",
    foreignKeys = [
        ForeignKey(
            entity = ReleaseCandidateEntity::class,
            parentColumns = ["candidateId"],
            childColumns = ["candidateId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("candidateId"), Index("state"), Index("notificationId")],
    primaryKeys = ["outboxId"],
)
data class NotificationOutboxEntity(
    val outboxId: String,
    val candidateId: String,
    val registeredAppId: String,
    val notificationType: String,
    val notificationId: Int,
    val state: String = NotificationOutboxState.PENDING.name,
    val createdAt: String,
    val attemptedAt: String? = null,
    val terminalAt: String? = null,
    val errorCode: String? = null,
)

@Entity(
    tableName = "provider_cooldowns",
    primaryKeys = ["provider", "instance"],
    indices = [Index("notBefore")],
)
data class ProviderCooldownEntity(
    val provider: String,
    val instance: String,
    val reason: String,
    val notBefore: String,
    val rateLimitRemaining: Long?,
    val rateLimitResetAt: String?,
    val updatedAt: String,
)

@Entity(
    tableName = "notification_dedup_headers",
    primaryKeys = ["dedupKey"],
    indices = [
        Index("registeredAppId"),
        Index(value = ["notificationId"], unique = true),
        Index("lastSeenAt"),
    ],
)
data class NotificationDedupHeaderEntity(
    val dedupKey: String,
    val registeredAppId: String,
    val provider: String,
    val instance: String,
    val providerRepositoryId: String,
    val providerReleaseId: String,
    val observationSha256: String,
    val notificationType: String,
    val disposition: String,
    val notificationId: Int,
    val firstSeenAt: String,
    val lastSeenAt: String,
)

@Entity(
    tableName = "provider_representations",
    primaryKeys = ["endpointKey"],
    indices = [Index(value = ["provider", "instance", "providerRepositoryId"])],
)
data class ProviderRepresentationEntity(
    val endpointKey: String,
    val provider: String,
    val instance: String,
    val providerRepositoryId: String,
    val etag: String?,
    val responseBody: String,
    val receivedAt: String,
)

data class ReleaseCandidateSummary(
    val candidate: ReleaseCandidateEntity,
    val appDisplayName: String,
    val notificationMuted: Boolean?,
)
