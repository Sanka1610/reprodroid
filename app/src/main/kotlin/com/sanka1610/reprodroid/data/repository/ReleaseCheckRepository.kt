package com.sanka1610.reprodroid.data.repository

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.room.withTransaction
import com.sanka1610.reprodroid.MainActivity
import com.sanka1610.reprodroid.R
import com.sanka1610.reprodroid.data.local.AppReleaseCheckOverrideEntity
import com.sanka1610.reprodroid.data.local.AppTrackingState
import com.sanka1610.reprodroid.data.local.AssetSelectionReason
import com.sanka1610.reprodroid.data.local.ComparisonOutcome
import com.sanka1610.reprodroid.data.local.NotificationDedupHeaderEntity
import com.sanka1610.reprodroid.data.local.NotificationOutboxEntity
import com.sanka1610.reprodroid.data.local.NotificationOutboxState
import com.sanka1610.reprodroid.data.local.ProviderCooldownEntity
import com.sanka1610.reprodroid.data.local.RegisteredAppEntity
import com.sanka1610.reprodroid.data.local.RepositoryIdentityStatus
import com.sanka1610.reprodroid.data.local.ReleaseAssetEntity
import com.sanka1610.reprodroid.data.local.ReleaseCandidateEntity
import com.sanka1610.reprodroid.data.local.ReleaseCandidateState
import com.sanka1610.reprodroid.data.local.ReleaseCheckChannel
import com.sanka1610.reprodroid.data.local.ReleaseCheckOutcome
import com.sanka1610.reprodroid.data.local.ReleaseCheckRunEntity
import com.sanka1610.reprodroid.data.local.ReleaseCheckScheduleMode
import com.sanka1610.reprodroid.data.local.ReleaseCheckSettingsEntity
import com.sanka1610.reprodroid.data.local.ReleaseCheckTrigger
import com.sanka1610.reprodroid.data.local.ReleaseCheckWaitingReason
import com.sanka1610.reprodroid.data.local.ReleaseDiscoveryStatus
import com.sanka1610.reprodroid.data.local.ReleaseNotificationType
import com.sanka1610.reprodroid.data.local.ReleaseObservationCandidate
import com.sanka1610.reprodroid.data.local.ReleaseObservationHasher
import com.sanka1610.reprodroid.data.local.ReleaseObservationInput
import com.sanka1610.reprodroid.data.local.ReleaseMetadataObservationCandidate
import com.sanka1610.reprodroid.data.local.ReleaseMetadataObservationInput
import com.sanka1610.reprodroid.data.local.ReleaseScheduleStateEntity
import com.sanka1610.reprodroid.data.local.ReleaseSnapshotEntity
import com.sanka1610.reprodroid.data.local.ReproDroidDatabase
import com.sanka1610.reprodroid.data.local.UpdateStatus
import com.sanka1610.reprodroid.data.provider.GitHubReleaseMetadataClient
import com.sanka1610.reprodroid.data.provider.CodebergReleaseMetadataClient
import com.sanka1610.reprodroid.data.provider.ProviderMetadataResult
import com.sanka1610.reprodroid.data.provider.ProviderReleaseMetadataClient
import com.sanka1610.reprodroid.data.provider.ProviderAssetCandidate
import com.sanka1610.reprodroid.data.provider.ReleaseMetadataException
import com.sanka1610.reprodroid.data.provider.canonicalProviderId
import com.sanka1610.reprodroid.data.provider.SavedAssetSelection
import com.sanka1610.reprodroid.ui.navigation.ReproDroidRoute
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

@Serializable
data class ReleaseCandidateAsset(
    val providerAssetId: String,
    val name: String,
    val stableUrl: String,
    val contentType: String?,
    val providerSizeBytes: Long,
    val providerDigestSha256: String?,
    val providerCreatedAt: String? = null,
)

interface ReleaseCheckEnvironment {
    fun currentState(): ReleaseCheckDeviceState
}

class AndroidReleaseCheckEnvironment(private val context: Context) : ReleaseCheckEnvironment {
    override fun currentState(): ReleaseCheckDeviceState {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val activeNetwork = connectivity?.activeNetwork
        val capabilities = activeNetwork?.let(connectivity::getNetworkCapabilities)
        val available = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val battery = context.getSystemService(BatteryManager::class.java)
            ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            ?.takeIf { it in 0..100 }
        return ReleaseCheckDeviceState(
            networkAvailable = available,
            networkMetered = connectivity?.isActiveNetworkMetered ?: true,
            batteryPercent = battery,
        )
    }
}

class ReleaseCheckRepository(
    private val context: Context,
    private val database: ReproDroidDatabase,
    private val provider: ProviderReleaseMetadataClient = GitHubReleaseMetadataClient(),
    private val codebergProvider: ProviderReleaseMetadataClient = CodebergReleaseMetadataClient(),
    private val environment: ReleaseCheckEnvironment = AndroidReleaseCheckEnvironment(context),
    private val clock: Clock = Clock.systemUTC(),
    private val zoneId: () -> ZoneId = { ZoneId.systemDefault() },
) {
    private data class ProviderMetadataContext(
        val provider: String,
        val instance: String,
        val client: ProviderReleaseMetadataClient,
    )

    private fun providerContext(provider: String, instance: String): ProviderMetadataContext? = when {
        provider == this.provider.providerName && instance == this.provider.providerInstance ->
            ProviderMetadataContext(provider, instance, this.provider)
        provider == codebergProvider.providerName && instance == codebergProvider.providerInstance ->
            ProviderMetadataContext(provider, instance, codebergProvider)
        else -> null
    }

    private val releaseDao = database.releaseCheckDao()
    private val appDao = database.managedAppDao()
    private val providerQueue = Mutex()
    private val json = Json { encodeDefaults = true; explicitNulls = true }

    fun observeSettings() = releaseDao.observeSettings()
    fun observeOverrides() = releaseDao.observeOverrides()
    fun observeScheduleStates() = releaseDao.observeScheduleStates()
    fun observeCandidates() = releaseDao.observeCandidates()

    suspend fun ensureInitialized() {
        val now = clock.instant()
        if (releaseDao.getSettings() == null) {
            releaseDao.upsertSettings(ReleaseCheckSettingsEntity(updatedAt = now.toString()))
        }
        reconcileSchedules(now)
        createNotificationChannel()
    }

    suspend fun updateSettings(requested: ReleaseCheckSettingsEntity) {
        val current = releaseDao.getSettings()
        val now = clock.instant()
        val validated = ReleaseCheckPolicy.validate(
            requested.copy(
                singletonId = ReleaseCheckSettingsEntity.SINGLETON_ID,
                revision = (current?.revision ?: 0) + 1,
                updatedAt = now.toString(),
            ),
        )
        database.withTransaction {
            releaseDao.upsertSettings(validated)
            releaseDao.getActiveApps().forEach { app ->
                val effective = ReleaseCheckPolicy.effective(validated, releaseDao.getOverride(app.registeredAppId))
                reevaluateCandidates(app, effective)
            }
        }
        reconcileSchedules(now, forceRecalculate = true)
    }

    suspend fun updateOverride(requested: AppReleaseCheckOverrideEntity) {
        val app = appDao.getRegisteredApp(requested.registeredAppId)
            ?: throw IllegalArgumentException("Registered app was not found.")
        require(app.trackingState == AppTrackingState.ACTIVE.name) { "Resume tracking before changing update settings." }
        val current = releaseDao.getOverride(requested.registeredAppId)
        val now = clock.instant()
        val global = currentSettings()
        val updated = requested.copy(
            revision = (current?.revision ?: 0) + 1,
            updatedAt = now.toString(),
        )
        ReleaseCheckPolicy.effective(global, updated)
        database.withTransaction {
            releaseDao.upsertOverride(updated)
            reevaluateCandidates(app, ReleaseCheckPolicy.effective(global, updated))
        }
        reconcileSchedules(now, forceRecalculate = true)
    }

    suspend fun reevaluateCandidates(registeredAppId: String) {
        val app = appDao.getRegisteredApp(registeredAppId)
            ?: throw IllegalArgumentException("Registered app was not found.")
        val effective = ReleaseCheckPolicy.effective(currentSettings(), releaseDao.getOverride(registeredAppId))
        database.withTransaction {
            reevaluateCandidates(app, effective)
        }
    }

    suspend fun checkNow(registeredAppId: String): ReleaseCheckOutcome =
        checkApp(registeredAppId, ReleaseCheckTrigger.MANUAL)

    suspend fun runDueChecks(): Int = withTimeoutOrNull(WORKER_TIMEOUT_MILLIS) {
        ensureInitialized()
        val now = clock.instant()
        val global = currentSettings()
        if (!global.enabled) return@withTimeoutOrNull 0
        val due = releaseDao.getActiveApps()
            .mapNotNull { app ->
                val state = releaseDao.getScheduleState(app.registeredAppId) ?: return@mapNotNull null
                if (!isValidScheduleState(state)) return@mapNotNull null
                val waiting = ReleaseCheckWaitingReason.valueOf(state.waitingReason)
                if (waiting == ReleaseCheckWaitingReason.INVALID_STATE) return@mapNotNull null
                val next = parseInstant(state.nextEligibleAt) ?: return@mapNotNull null
                if (next.isAfter(now)) null else Triple(app, state, next)
            }
            .sortedWith(
                compareBy<Triple<RegisteredAppEntity, ReleaseScheduleStateEntity, Instant>> { it.third }
                    .thenBy { parseInstant(it.second.lastAttemptAt) ?: Instant.EPOCH }
                    .thenBy { it.first.registeredAppId },
            )
            .take(MAX_APPS_PER_WORKER)
        var completed = 0
        for ((app) in due) {
            val outcome = checkApp(app.registeredAppId, ReleaseCheckTrigger.SCHEDULED)
            completed++
            if (outcome == ReleaseCheckOutcome.PROVIDER_RATE_LIMITED) break
        }
        completed
    } ?: 0

    suspend fun reconcileSchedules(
        now: Instant = clock.instant(),
        forceRecalculate: Boolean = false,
    ) {
        val global = currentSettings()
        val cooldowns = releaseDao.getCooldowns().associateBy { it.provider to it.instance }
        releaseDao.getActiveApps().forEach { app ->
            val binding = appDao.getRepositoryBinding(app.registeredAppId)
            val cooldown = binding?.let { cooldowns[it.provider to it.instance] }
            val cooldownNotBefore = parseInstant(cooldown?.notBefore)
            val cooldownInvalid = cooldown != null && !isValidCooldown(cooldown)
            val override = releaseDao.getOverride(app.registeredAppId)
            val current = releaseDao.getScheduleState(app.registeredAppId)
            val effective = runCatching { ReleaseCheckPolicy.effective(global, override) }.getOrNull()
            val currentWaiting = current?.waitingReason?.let { value ->
                runCatching { ReleaseCheckWaitingReason.valueOf(value) }.getOrNull()
            }
            val currentNext = parseInstant(current?.nextEligibleAt)
            val waiting = when {
                effective == null -> ReleaseCheckWaitingReason.INVALID_STATE
                current != null && !isValidScheduleState(current) -> ReleaseCheckWaitingReason.INVALID_STATE
                !global.enabled -> ReleaseCheckWaitingReason.GLOBAL_DISABLED
                !effective.enabled -> ReleaseCheckWaitingReason.APP_DISABLED
                cooldownInvalid -> ReleaseCheckWaitingReason.INVALID_STATE
                cooldownNotBefore?.isAfter(now) == true -> ReleaseCheckWaitingReason.PROVIDER_COOLDOWN
                !forceRecalculate && current != null && (currentWaiting == null || currentNext == null) ->
                    ReleaseCheckWaitingReason.INVALID_STATE
                !forceRecalculate && currentNext?.isAfter(now) == true && currentWaiting in OPERATIONAL_WAITING_REASONS ->
                    requireNotNull(currentWaiting)
                else -> ReleaseCheckWaitingReason.NONE
            }
            val calculated = if (effective == null) {
                current?.nextEligibleAt ?: now.plus(24, ChronoUnit.HOURS).toString()
            } else {
                ReleaseCheckPolicy.nextTerminalTime(effective, now, zoneId()).toString()
            }
            val baseNext = if (!forceRecalculate && current != null) current.nextEligibleAt else calculated
            val next = if (cooldownNotBefore?.isAfter(now) == true) {
                maxOf(parseInstant(baseNext) ?: cooldownNotBefore, cooldownNotBefore).toString()
            } else {
                baseNext
            }
            releaseDao.upsertScheduleState(
                current?.copy(
                    nextEligibleAt = next,
                    waitingReason = waiting.name,
                    updatedAt = now.toString(),
                ) ?: ReleaseScheduleStateEntity(
                    registeredAppId = app.registeredAppId,
                    nextEligibleAt = next,
                    waitingReason = waiting.name,
                    updatedAt = now.toString(),
                ),
            )
        }
    }

    suspend fun nextDispatchAt(): Instant? {
        val settings = currentSettings()
        if (!settings.enabled) return null
        val nextAppDispatch = releaseDao.getActiveApps().mapNotNull { app ->
            val effective = runCatching { ReleaseCheckPolicy.effective(settings, releaseDao.getOverride(app.registeredAppId)) }
                .getOrNull() ?: return@mapNotNull null
            if (!effective.enabled) return@mapNotNull null
            val state = releaseDao.getScheduleState(app.registeredAppId) ?: return@mapNotNull null
            if (!isValidScheduleState(state)) return@mapNotNull null
            val waiting = ReleaseCheckWaitingReason.valueOf(state.waitingReason)
            if (waiting == ReleaseCheckWaitingReason.INVALID_STATE) return@mapNotNull null
            parseInstant(state.nextEligibleAt)
        }.minOrNull()
        val cooldownNotBefore = releaseDao.getCooldowns()
            .filter(::isValidCooldown)
            .mapNotNull { parseInstant(it.notBefore) }
            .filter { it.isAfter(clock.instant()) }
            .maxOrNull()
        return if (cooldownNotBefore == null || nextAppDispatch == null) {
            nextAppDispatch
        } else {
            maxOf(nextAppDispatch, cooldownNotBefore)
        }
    }

    suspend fun markCandidateSeen(candidateId: String) = releaseDao.markCandidateSeen(candidateId)

    suspend fun stageCandidateForManualAction(candidateId: String) {
        val candidate = releaseDao.getCandidate(candidateId)
            ?: throw IllegalArgumentException("Release candidate was not found.")
        val app = appDao.getRegisteredApp(candidate.registeredAppId)
            ?: throw IllegalArgumentException("Registered app was not found.")
        check(app.trackingState == AppTrackingState.ACTIVE.name) {
            "Resume tracking before opening a release candidate."
        }
        val latestCandidate = releaseDao.getCandidates(candidate.registeredAppId).firstOrNull()
        check(latestCandidate?.candidateId == candidateId) {
            "Only the latest release candidate can be opened for a manual action."
        }
        val binding = appDao.getRepositoryBinding(candidate.registeredAppId)
            ?: throw IllegalStateException("Repository identity is not available.")
        check(
            binding.identityStatus == RepositoryIdentityStatus.VERIFIED.name &&
                binding.provider == candidate.provider &&
                binding.instance == candidate.instance &&
                binding.providerRepositoryId == candidate.providerRepositoryId,
        ) { "The release candidate no longer matches the verified repository identity." }
        val assets = runCatching {
            json.decodeFromString<List<ReleaseCandidateAsset>>(candidate.assetsJson)
        }.getOrElse { throw IllegalStateException("Stored release candidate metadata is invalid.", it) }
        check(assets.isNotEmpty()) { "The release candidate has no APK to select." }

        database.withTransaction {
            val currentCandidate = releaseDao.getCandidate(candidateId)
            val currentLatestCandidate = releaseDao.getCandidates(candidate.registeredAppId).firstOrNull()
            val currentApp = appDao.getRegisteredApp(candidate.registeredAppId)
            val currentBinding = appDao.getRepositoryBinding(candidate.registeredAppId)
            check(
                currentCandidate == candidate &&
                    currentLatestCandidate?.candidateId == candidateId &&
                    currentApp?.trackingState == AppTrackingState.ACTIVE.name &&
                    currentBinding == binding,
            ) { "The release candidate or repository identity changed while it was being opened." }

            val existingSnapshot = appDao.getLatestReleaseSnapshotByMetadataHash(
                candidate.registeredAppId,
                candidate.observationSha256,
            ) ?: appDao.getReleaseSnapshotByObservationHash(
                candidate.registeredAppId,
                candidate.observationSha256,
            )
            val snapshotId = existingSnapshot?.releaseSnapshotId
                ?: stableUuid("${candidate.registeredAppId}/release-observation/${candidate.observationSha256}")
            if (existingSnapshot == null) {
                appDao.upsertReleaseSnapshot(
                    ReleaseSnapshotEntity(
                        releaseSnapshotId = snapshotId,
                        registeredAppId = candidate.registeredAppId,
                        providerReleaseId = candidate.providerReleaseId,
                        tagName = candidate.tagName,
                        resolvedCommitSha = candidate.resolvedCommitSha,
                        releaseName = candidate.releaseName,
                        releaseUrl = candidate.releaseUrl,
                        targetCommitishRaw = candidate.targetCommitishRaw,
                        isDraft = false,
                        isPrerelease = candidate.isPrerelease,
                        isImmutable = candidate.isImmutable,
                        releaseCreatedAt = candidate.releaseCreatedAt,
                        publishedAt = candidate.publishedAt,
                        fetchedAt = candidate.lastSeenAt,
                        observationSha256 = candidate.observationSha256,
                        lastObservedAt = candidate.lastSeenAt,
                        observationSchemaVersion = 2,
                        metadataObservationSha256 = candidate.observationSha256,
                        selectedProviderAssetId = null,
                    ),
                )
            }
            assets.forEach { asset ->
                if (appDao.getReleaseAsset(snapshotId, asset.providerAssetId) == null) {
                    appDao.upsertReleaseAsset(
                        ReleaseAssetEntity(
                            releaseAssetId = stableUuid("$snapshotId/asset/${asset.providerAssetId}"),
                            releaseSnapshotId = snapshotId,
                            providerAssetId = asset.providerAssetId,
                            assetName = asset.name,
                            stableAssetUrl = asset.stableUrl,
                            selectionReason = AssetSelectionReason.MANUAL_SELECTION_REQUIRED.name,
                            contentType = asset.contentType,
                            providerSizeBytes = asset.providerSizeBytes,
                            providerDigestSha256 = asset.providerDigestSha256,
                            providerCreatedAt = asset.providerCreatedAt,
                        ),
                    )
                }
            }
            appDao.upsertRegisteredApp(
                requireNotNull(currentApp).copy(
                    releaseDiscoveryStatus = if (existingSnapshot?.selectedProviderAssetId == null) {
                        ReleaseDiscoveryStatus.AWAITING_ASSET_SELECTION.name
                    } else {
                        ReleaseDiscoveryStatus.AVAILABLE.name
                    },
                    releaseDiscoveryErrorCode = null,
                    releaseDiscoveryErrorMessage = null,
                    updatedAt = clock.instant().toString(),
                ),
            )
            releaseDao.markCandidateSeen(candidateId)
        }
    }

    suspend fun deliverPendingNotifications(limit: Int = 20): Int {
        createNotificationChannel()
        var processed = 0
        releaseDao.getPendingOutbox(limit.coerceIn(1, 100)).forEach { outbox ->
            val now = clock.instant()
            val candidate = releaseDao.getCandidate(outbox.candidateId)
            val app = appDao.getRegisteredApp(outbox.registeredAppId)
            val override = releaseDao.getOverride(outbox.registeredAppId)
            val terminal = when {
                candidate == null || app == null || app.trackingState != AppTrackingState.ACTIVE.name ->
                    NotificationOutboxState.SUPPRESSED_INACTIVE to "INACTIVE_OR_MISSING"
                override?.notificationMuted == true -> NotificationOutboxState.SUPPRESSED_MUTED to "APP_MUTED"
                !notificationsAllowed() -> NotificationOutboxState.SUPPRESSED_PERMISSION to "PERMISSION_NOT_GRANTED"
                else -> null
            }
            val disposition = if (terminal == null) {
                runCatching { postNotification(requireNotNull(app), requireNotNull(candidate), outbox) }
                    .fold(
                        onSuccess = { NotificationOutboxState.DELIVERED to null },
                        onFailure = {
                            if (it is SecurityException) {
                                NotificationOutboxState.SUPPRESSED_PERMISSION to "PERMISSION_REVOKED"
                            } else {
                                NotificationOutboxState.FAILED to (it.message ?: "NOTIFICATION_POST_FAILED")
                            }
                        },
                    )
            } else {
                terminal
            }
            database.withTransaction {
                releaseDao.upsertOutbox(
                    outbox.copy(
                        state = disposition.first.name,
                        attemptedAt = now.toString(),
                        terminalAt = now.toString(),
                        errorCode = disposition.second,
                    ),
                )
                val header = candidate?.let { releaseDao.getDedupHeader(dedupKey(it, outbox.notificationType)) }
                if (header != null) {
                    releaseDao.upsertDedupHeader(
                        header.copy(disposition = disposition.first.name, lastSeenAt = now.toString()),
                    )
                }
            }
            processed++
        }
        return processed
    }

    suspend fun runRetentionMaintenance(): Int {
        val now = clock.instant()
        var remaining = MAX_MAINTENANCE_RECORDS
        var deleted = releaseDao.deleteExpiredCheckRuns(now.minus(90, ChronoUnit.DAYS).toString(), remaining)
        remaining -= deleted
        if (remaining > 0) {
            val count = releaseDao.deleteExpiredOutbox(now.minus(90, ChronoUnit.DAYS).toString(), remaining)
            deleted += count
            remaining -= count
        }
        if (remaining > 0) {
            deleted += releaseDao.deleteExpiredDedupHeaders(now.minus(365, ChronoUnit.DAYS).toString(), remaining)
        }
        return deleted
    }

    private suspend fun checkApp(
        registeredAppId: String,
        trigger: ReleaseCheckTrigger,
    ): ReleaseCheckOutcome {
        val started = clock.instant()
        val app = appDao.getRegisteredApp(registeredAppId)
            ?: throw IllegalArgumentException("Registered app was not found.")
        if (app.trackingState != AppTrackingState.ACTIVE.name) {
            defer(app, ReleaseCheckWaitingReason.INACTIVE, started.plus(1, ChronoUnit.DAYS))
            return ReleaseCheckOutcome.CANCELLED
        }
        val global = currentSettings()
        val effective = try {
            ReleaseCheckPolicy.effective(global, releaseDao.getOverride(registeredAppId))
        } catch (failure: IllegalArgumentException) {
            defer(app, ReleaseCheckWaitingReason.INVALID_STATE, started.plus(1, ChronoUnit.DAYS))
            return ReleaseCheckOutcome.INVALID_METADATA
        }
        if (trigger == ReleaseCheckTrigger.SCHEDULED && !effective.enabled) {
            defer(
                app,
                if (!global.enabled) ReleaseCheckWaitingReason.GLOBAL_DISABLED else ReleaseCheckWaitingReason.APP_DISABLED,
                parseInstant(releaseDao.getScheduleState(registeredAppId)?.nextEligibleAt) ?: started.plus(1, ChronoUnit.DAYS),
            )
            return ReleaseCheckOutcome.CANCELLED
        }
        ReleaseCheckPolicy.deferReason(effective, environment.currentState())?.let { reason ->
            defer(app, ReleaseCheckWaitingReason.valueOf(reason), started.plus(CONSTRAINT_RETRY_MINUTES, ChronoUnit.MINUTES))
            return ReleaseCheckOutcome.CANCELLED
        }
        val binding = appDao.getRepositoryBinding(registeredAppId)
        val providerRepositoryId = binding?.providerRepositoryId?.let { id ->
            runCatching { canonicalProviderId(id) }.getOrNull()
        }
        val providerContext = binding?.let { providerContext(it.provider, it.instance) } ?: run {
            defer(app, ReleaseCheckWaitingReason.INVALID_STATE, started.plus(1, ChronoUnit.DAYS))
            return ReleaseCheckOutcome.INVALID_METADATA
        }
        if (binding.identityStatus != RepositoryIdentityStatus.VERIFIED.name || providerRepositoryId == null) {
            defer(app, ReleaseCheckWaitingReason.INVALID_STATE, started.plus(1, ChronoUnit.DAYS))
            return ReleaseCheckOutcome.INVALID_METADATA
        }
        return providerQueue.withLock {
            val queuedApp = appDao.getRegisteredApp(registeredAppId)
            val queuedBinding = appDao.getRepositoryBinding(registeredAppId)
            if (
                queuedApp == null ||
                queuedApp.trackingState != AppTrackingState.ACTIVE.name ||
                queuedApp.canonicalRepositoryUrl != app.canonicalRepositoryUrl ||
                queuedBinding == null ||
                queuedBinding.provider != providerContext.provider ||
                queuedBinding.instance != providerContext.instance ||
                queuedBinding.identityStatus != RepositoryIdentityStatus.VERIFIED.name ||
                queuedBinding.providerRepositoryId != providerRepositoryId
            ) {
                defer(app, ReleaseCheckWaitingReason.INVALID_STATE, clock.instant().plus(CONSTRAINT_RETRY_MINUTES, ChronoUnit.MINUTES))
                return@withLock ReleaseCheckOutcome.CANCELLED
            }
            releaseDao.getCooldown(providerContext.provider, providerContext.instance)?.let { cooldown ->
                if (!isValidCooldown(cooldown)) {
                    defer(app, ReleaseCheckWaitingReason.INVALID_STATE, started.plus(1, ChronoUnit.DAYS))
                    return@withLock ReleaseCheckOutcome.INVALID_METADATA
                }
                val notBefore = requireNotNull(parseInstant(cooldown.notBefore))
                if (notBefore.isAfter(clock.instant())) {
                    defer(app, ReleaseCheckWaitingReason.PROVIDER_COOLDOWN, notBefore)
                    return@withLock ReleaseCheckOutcome.PROVIDER_RATE_LIMITED
                }
            }
            ReleaseCheckPolicy.deferReason(effective, environment.currentState())?.let { reason ->
                defer(
                    app,
                    ReleaseCheckWaitingReason.valueOf(reason),
                    clock.instant().plus(CONSTRAINT_RETRY_MINUTES, ChronoUnit.MINUTES),
                )
                return@withLock ReleaseCheckOutcome.CANCELLED
            }
            try {
                val result = providerContext.client.check(
                    repositoryUrl = app.canonicalRepositoryUrl,
                    providerRepositoryId = providerRepositoryId,
                    channel = ReleaseCheckChannel.valueOf(effective.releaseChannel),
                    cachedRepresentations = releaseDao.getRepresentations(
                        providerContext.provider,
                        providerContext.instance,
                        providerRepositoryId,
                    ),
                    now = started,
                )
                ReleaseCheckPolicy.deferReason(effective, environment.currentState())?.let { reason ->
                    defer(
                        app,
                        ReleaseCheckWaitingReason.valueOf(reason),
                        clock.instant().plus(CONSTRAINT_RETRY_MINUTES, ChronoUnit.MINUTES),
                    )
                    return@withLock ReleaseCheckOutcome.CANCELLED
                }
                when (result) {
                    is ProviderMetadataResult.NoPublishedRelease -> {
                        val committed = persistTerminal(
                            app = app,
                            provider = providerContext,
                            providerRepositoryId = providerRepositoryId,
                            trigger = trigger,
                            effective = effective,
                            outcome = ReleaseCheckOutcome.NO_PUBLISHED_RELEASE,
                            evidence = evidence(result.requestCount, result.receivedBytes),
                            representations = result.representations,
                            started = started,
                        )
                        if (committed) ReleaseCheckOutcome.NO_PUBLISHED_RELEASE else ReleaseCheckOutcome.CANCELLED
                    }
                    is ProviderMetadataResult.Release -> persistObservation(
                        app,
                        providerContext,
                        providerRepositoryId,
                        trigger,
                        effective,
                        result,
                        started,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: ReleaseMetadataException) {
                persistFailure(app, providerContext, providerRepositoryId, trigger, effective, failure, started)
            }
        }
    }

    private suspend fun persistObservation(
        app: RegisteredAppEntity,
        provider: ProviderMetadataContext,
        providerRepositoryId: String,
        trigger: ReleaseCheckTrigger,
        effective: EffectiveReleaseCheckSettings,
        result: ProviderMetadataResult.Release,
        started: Instant,
    ): ReleaseCheckOutcome {
        val resolved = result.resolved
        val release = resolved.release
        val assets = resolved.candidates.map(::candidateAsset)
        val observation = ReleaseObservationHasher.metadataSha256(
            ReleaseMetadataObservationInput(
                provider = provider.provider,
                instance = provider.instance,
                providerRepositoryId = providerRepositoryId,
                providerReleaseId = release.id,
                tagName = release.tagName,
                resolvedCommitSha = resolved.resolvedCommitSha,
                targetCommitishRaw = release.targetCommitish,
                releaseName = release.name ?: release.tagName,
                releaseUrl = release.htmlUrl,
                isDraft = release.draft,
                isPrerelease = release.prerelease,
                isImmutable = release.immutable,
                releaseCreatedAt = release.createdAt,
                publishedAt = release.publishedAt,
                candidates = assets.map {
                    ReleaseMetadataObservationCandidate(
                        providerAssetId = it.providerAssetId,
                        assetName = it.name,
                        stableAssetUrl = it.stableUrl,
                        contentType = it.contentType,
                        providerSizeBytes = it.providerSizeBytes,
                        providerDigestSha256 = it.providerDigestSha256,
                        providerCreatedAt = it.providerCreatedAt,
                    )
                },
            ),
        )
        val previousCandidate = releaseDao.getCandidateByObservation(app.registeredAppId, observation)
        val exactVerified = isAlreadyVerified(
            registeredAppId = app.registeredAppId,
            providerReleaseId = release.id,
            tagName = release.tagName,
            resolvedCommitSha = resolved.resolvedCommitSha,
            releaseName = release.name ?: release.tagName,
            releaseUrl = release.htmlUrl,
            targetCommitishRaw = release.targetCommitish,
            isPrerelease = release.prerelease,
            isImmutable = release.immutable,
            releaseCreatedAt = release.createdAt,
            publishedAt = release.publishedAt,
            assets = assets,
        )
        val savedSelection = SavedAssetSelection.select(resolved.candidates, app.savedAssetSelectionJson)
        val state = when {
            assets.isEmpty() -> ReleaseCandidateState.NO_APK_ASSET
            assets.size > 1 && savedSelection == null -> ReleaseCandidateState.ASSET_SELECTION_REQUIRED
            exactVerified -> ReleaseCandidateState.VERIFIED_UPDATE_AVAILABLE
            app.managementMode == "VERIFICATION" -> ReleaseCandidateState.VERIFICATION_REQUIRED
            else -> ReleaseCandidateState.NEW_RELEASE_DISCOVERED
        }
        val outcome = if (result.representationNotModified && previousCandidate != null) {
            ReleaseCheckOutcome.NOT_MODIFIED
        } else when (state) {
            ReleaseCandidateState.NO_APK_ASSET -> ReleaseCheckOutcome.NO_APK_ASSET
            ReleaseCandidateState.ASSET_SELECTION_REQUIRED -> ReleaseCheckOutcome.ASSET_SELECTION_REQUIRED
            ReleaseCandidateState.VERIFIED_UPDATE_AVAILABLE -> ReleaseCheckOutcome.VERIFIED_UPDATE_AVAILABLE
            else -> ReleaseCheckOutcome.NEW_RELEASE_DISCOVERED
        }
        val finished = clock.instant()
        val candidateId = stableUuid("${app.registeredAppId}/candidate/$observation")
        var committed = false
        database.withTransaction {
            val currentApp = appDao.getRegisteredApp(app.registeredAppId)
            val currentBinding = appDao.getRepositoryBinding(app.registeredAppId)
            val currentGlobal = currentSettings()
            val currentEffective = currentApp?.let {
                runCatching { ReleaseCheckPolicy.effective(currentGlobal, releaseDao.getOverride(app.registeredAppId)) }.getOrNull()
            }
            if (
                currentApp == null ||
                currentApp.trackingState != AppTrackingState.ACTIVE.name ||
                currentApp.canonicalRepositoryUrl != app.canonicalRepositoryUrl ||
                currentBinding == null ||
                currentBinding.provider != provider.provider ||
                currentBinding.instance != provider.instance ||
                currentBinding.identityStatus != RepositoryIdentityStatus.VERIFIED.name ||
                currentBinding.providerRepositoryId != providerRepositoryId ||
                currentEffective == null ||
                currentEffective.globalRevision != effective.globalRevision ||
                currentEffective.appRevision != effective.appRevision ||
                (trigger == ReleaseCheckTrigger.SCHEDULED && !currentEffective.enabled)
            ) {
                releaseDao.upsertScheduleState(
                    ReleaseScheduleStateEntity(
                        registeredAppId = app.registeredAppId,
                        lastAttemptAt = finished.toString(),
                        nextEligibleAt = finished.plus(1, ChronoUnit.DAYS).toString(),
                        waitingReason = ReleaseCheckWaitingReason.INACTIVE.name,
                        updatedAt = finished.toString(),
                    ),
                )
                return@withTransaction
            }
            val existing = releaseDao.getCandidateByObservation(app.registeredAppId, observation)
            val candidate = ReleaseCandidateEntity(
                candidateId = existing?.candidateId ?: candidateId,
                registeredAppId = app.registeredAppId,
                provider = provider.provider,
                instance = provider.instance,
                providerRepositoryId = providerRepositoryId,
                providerReleaseId = release.id,
                tagName = release.tagName,
                resolvedCommitSha = resolved.resolvedCommitSha,
                releaseName = release.name ?: release.tagName,
                releaseUrl = release.htmlUrl,
                targetCommitishRaw = release.targetCommitish,
                isPrerelease = release.prerelease,
                isImmutable = release.immutable,
                releaseCreatedAt = release.createdAt,
                publishedAt = release.publishedAt,
                assetsJson = json.encodeToString(assets),
                observationSha256 = observation,
                state = state.name,
                unseen = existing?.unseen ?: true,
                firstSeenAt = existing?.firstSeenAt ?: finished.toString(),
                lastSeenAt = finished.toString(),
            )
            releaseDao.upsertCandidate(candidate)
            releaseDao.upsertRepresentations(result.representations)
            val notificationType = notificationType(state)
            if (existing == null && notificationType != null) {
                persistNotificationIntent(candidate, notificationType, finished)
            }
            releaseDao.upsertCheckRun(
                runEntity(app.registeredAppId, trigger, effective, outcome, evidence(result.requestCount, result.receivedBytes, result.representationNotModified), started, finished),
            )
            releaseDao.upsertScheduleState(terminalSchedule(app.registeredAppId, effective, finished))
            committed = true
        }
        return if (committed) outcome else ReleaseCheckOutcome.CANCELLED
    }

    private suspend fun persistTerminal(
        app: RegisteredAppEntity,
        provider: ProviderMetadataContext,
        providerRepositoryId: String,
        trigger: ReleaseCheckTrigger,
        effective: EffectiveReleaseCheckSettings,
        outcome: ReleaseCheckOutcome,
        evidence: String,
        representations: List<com.sanka1610.reprodroid.data.local.ProviderRepresentationEntity>,
        started: Instant,
    ): Boolean {
        val finished = clock.instant()
        var committed = false
        database.withTransaction {
            val currentApp = appDao.getRegisteredApp(app.registeredAppId)
            val currentBinding = appDao.getRepositoryBinding(app.registeredAppId)
            val currentEffective = currentApp?.let {
                runCatching { ReleaseCheckPolicy.effective(currentSettings(), releaseDao.getOverride(app.registeredAppId)) }
                    .getOrNull()
            }
            if (
                currentApp == null ||
                currentApp.trackingState != AppTrackingState.ACTIVE.name ||
                currentApp.canonicalRepositoryUrl != app.canonicalRepositoryUrl ||
                currentBinding == null ||
                currentBinding.provider != provider.provider ||
                currentBinding.instance != provider.instance ||
                currentBinding.identityStatus != RepositoryIdentityStatus.VERIFIED.name ||
                currentBinding.providerRepositoryId != providerRepositoryId ||
                currentEffective == null ||
                currentEffective.globalRevision != effective.globalRevision ||
                currentEffective.appRevision != effective.appRevision ||
                (trigger == ReleaseCheckTrigger.SCHEDULED && !currentEffective.enabled)
            ) {
                releaseDao.upsertScheduleState(
                    ReleaseScheduleStateEntity(
                        registeredAppId = app.registeredAppId,
                        lastAttemptAt = finished.toString(),
                        nextEligibleAt = finished.plus(CONSTRAINT_RETRY_MINUTES, ChronoUnit.MINUTES).toString(),
                        waitingReason = ReleaseCheckWaitingReason.INVALID_STATE.name,
                        updatedAt = finished.toString(),
                    ),
                )
                return@withTransaction
            }
            releaseDao.upsertRepresentations(representations)
            releaseDao.upsertCheckRun(runEntity(app.registeredAppId, trigger, effective, outcome, evidence, started, finished))
            releaseDao.upsertScheduleState(terminalSchedule(app.registeredAppId, effective, finished))
            committed = true
        }
        return committed
    }

    private suspend fun persistFailure(
        app: RegisteredAppEntity,
        provider: ProviderMetadataContext,
        providerRepositoryId: String,
        trigger: ReleaseCheckTrigger,
        effective: EffectiveReleaseCheckSettings,
        failure: ReleaseMetadataException,
        started: Instant,
    ): ReleaseCheckOutcome {
        val finished = clock.instant()
        val outcome = runCatching { ReleaseCheckOutcome.valueOf(failure.code) }
            .getOrDefault(ReleaseCheckOutcome.INVALID_METADATA)
        val current = releaseDao.getScheduleState(app.registeredAppId)
        val rateLimited = outcome == ReleaseCheckOutcome.PROVIDER_RATE_LIMITED
        val transient = outcome == ReleaseCheckOutcome.NETWORK_ERROR || outcome == ReleaseCheckOutcome.PROVIDER_UNAVAILABLE
        val retry = current?.consecutiveRetry ?: 0
        val next = when {
            rateLimited -> failure.retryNotBefore?.takeIf { it.isAfter(finished) }
                ?: finished.plus(1, ChronoUnit.HOURS)
            transient && retry < RETRY_MINUTES.size -> failure.retryNotBefore
                ?.takeIf { it.isAfter(finished) }
                ?: finished.plus(RETRY_MINUTES[retry], ChronoUnit.MINUTES)
            else -> ReleaseCheckPolicy.nextTerminalTime(effective, finished, zoneId())
        }
        val waiting = when {
            rateLimited -> ReleaseCheckWaitingReason.PROVIDER_COOLDOWN
            transient && retry < RETRY_MINUTES.size -> ReleaseCheckWaitingReason.RETRY_BACKOFF
            else -> ReleaseCheckWaitingReason.NONE
        }
        var committed = false
        database.withTransaction {
            if (rateLimited) {
                releaseDao.upsertCooldown(
                    ProviderCooldownEntity(
                    provider = provider.provider,
                    instance = provider.instance,
                        reason = failure.code,
                        notBefore = next.toString(),
                        rateLimitRemaining = failure.rateLimitRemaining,
                        rateLimitResetAt = failure.rateLimitResetAt?.toString(),
                        updatedAt = finished.toString(),
                    ),
                )
            }
            val currentApp = appDao.getRegisteredApp(app.registeredAppId)
            val currentBinding = appDao.getRepositoryBinding(app.registeredAppId)
            val currentEffective = currentApp?.let {
                runCatching { ReleaseCheckPolicy.effective(currentSettings(), releaseDao.getOverride(app.registeredAppId)) }
                    .getOrNull()
            }
            if (
                currentApp == null ||
                currentApp.trackingState != AppTrackingState.ACTIVE.name ||
                currentApp.canonicalRepositoryUrl != app.canonicalRepositoryUrl ||
                currentBinding == null ||
                currentBinding.provider != provider.provider ||
                currentBinding.instance != provider.instance ||
                currentBinding.identityStatus != RepositoryIdentityStatus.VERIFIED.name ||
                currentBinding.providerRepositoryId != providerRepositoryId ||
                currentEffective == null ||
                currentEffective.globalRevision != effective.globalRevision ||
                currentEffective.appRevision != effective.appRevision ||
                (trigger == ReleaseCheckTrigger.SCHEDULED && !currentEffective.enabled)
            ) {
                releaseDao.upsertScheduleState(
                    ReleaseScheduleStateEntity(
                        registeredAppId = app.registeredAppId,
                        lastAttemptAt = finished.toString(),
                        nextEligibleAt = finished.plus(CONSTRAINT_RETRY_MINUTES, ChronoUnit.MINUTES).toString(),
                        waitingReason = ReleaseCheckWaitingReason.INVALID_STATE.name,
                        updatedAt = finished.toString(),
                    ),
                )
                return@withTransaction
            }
            releaseDao.upsertCheckRun(
                runEntity(
                    app.registeredAppId,
                    trigger,
                    effective,
                    outcome,
                    buildJsonObject {
                        put("statusCode", failure.statusCode)
                        put("code", failure.code)
                        put("retryNotBefore", failure.retryNotBefore?.toString())
                    }.toString(),
                    started,
                    finished,
                ),
            )
            releaseDao.upsertScheduleState(
                ReleaseScheduleStateEntity(
                    registeredAppId = app.registeredAppId,
                    lastAttemptAt = finished.toString(),
                    lastTerminalAt = if (waiting == ReleaseCheckWaitingReason.NONE) finished.toString() else current?.lastTerminalAt,
                    nextEligibleAt = next.toString(),
                    waitingReason = waiting.name,
                    consecutiveRetry = if (transient && retry < RETRY_MINUTES.size) retry + 1 else 0,
                    updatedAt = finished.toString(),
                ),
            )
            committed = true
        }
        return if (committed) outcome else ReleaseCheckOutcome.CANCELLED
    }

    private suspend fun persistNotificationIntent(
        candidate: ReleaseCandidateEntity,
        notificationType: ReleaseNotificationType,
        now: Instant,
    ) {
        val key = dedupKey(candidate, notificationType.name)
        val existing = releaseDao.getDedupHeader(key)
        if (existing != null) {
            releaseDao.upsertDedupHeader(existing.copy(lastSeenAt = now.toString()))
            return
        }
        val notificationId = allocateNotificationId(key)
        val outboxId = stableUuid("outbox/$key")
        releaseDao.upsertDedupHeader(
            NotificationDedupHeaderEntity(
                dedupKey = key,
                registeredAppId = candidate.registeredAppId,
                provider = candidate.provider,
                instance = candidate.instance,
                providerRepositoryId = candidate.providerRepositoryId,
                providerReleaseId = candidate.providerReleaseId,
                observationSha256 = candidate.observationSha256,
                notificationType = notificationType.name,
                disposition = NotificationOutboxState.PENDING.name,
                notificationId = notificationId,
                firstSeenAt = now.toString(),
                lastSeenAt = now.toString(),
            ),
        )
        releaseDao.upsertOutbox(
            NotificationOutboxEntity(
                outboxId = outboxId,
                candidateId = candidate.candidateId,
                registeredAppId = candidate.registeredAppId,
                notificationType = notificationType.name,
                notificationId = notificationId,
                createdAt = now.toString(),
            ),
        )
    }

    private suspend fun allocateNotificationId(dedupKey: String): Int {
        val bytes = MessageDigest.getInstance("SHA-256").digest(dedupKey.toByteArray())
        var candidate = ((bytes[0].toInt() and 0x7f) shl 24) or
            ((bytes[1].toInt() and 0xff) shl 16) or
            ((bytes[2].toInt() and 0xff) shl 8) or
            (bytes[3].toInt() and 0xff)
        if (candidate == 0) candidate = 1
        repeat(MAX_NOTIFICATION_ID_PROBES) {
            val occupied = releaseDao.getDedupHeaderByNotificationId(candidate)
            if (occupied == null || occupied.dedupKey == dedupKey) return candidate
            candidate = if (candidate == Int.MAX_VALUE) 1 else candidate + 1
        }
        throw IllegalStateException("Notification ID collision probe limit was reached.")
    }

    private suspend fun isAlreadyVerified(
        registeredAppId: String,
        providerReleaseId: String,
        tagName: String,
        resolvedCommitSha: String,
        releaseName: String,
        releaseUrl: String,
        targetCommitishRaw: String,
        isPrerelease: Boolean,
        isImmutable: Boolean,
        releaseCreatedAt: String,
        publishedAt: String?,
        assets: List<ReleaseCandidateAsset>,
    ): Boolean {
        val snapshot = appDao.getReleaseSnapshot(registeredAppId, providerReleaseId) ?: return false
        if (
            snapshot.providerReleaseId != providerReleaseId ||
            snapshot.tagName != tagName ||
            snapshot.resolvedCommitSha != resolvedCommitSha ||
            snapshot.releaseName != releaseName ||
            snapshot.releaseUrl != releaseUrl ||
            snapshot.targetCommitishRaw != targetCommitishRaw ||
            snapshot.isDraft ||
            snapshot.isPrerelease != isPrerelease ||
            snapshot.isImmutable != isImmutable ||
            snapshot.releaseCreatedAt != releaseCreatedAt ||
            snapshot.publishedAt != publishedAt
        ) {
            return false
        }
        val selectedId = snapshot.selectedProviderAssetId ?: return false
        val candidateAsset = assets.singleOrNull { it.providerAssetId == selectedId } ?: return false
        val persistedAsset = appDao.getReleaseAsset(snapshot.releaseSnapshotId, selectedId) ?: return false
        if (
            persistedAsset.assetName != candidateAsset.name ||
            persistedAsset.stableAssetUrl != candidateAsset.stableUrl ||
            persistedAsset.contentType != candidateAsset.contentType ||
            persistedAsset.providerSizeBytes != candidateAsset.providerSizeBytes ||
            persistedAsset.providerDigestSha256 != candidateAsset.providerDigestSha256 ||
            persistedAsset.downloadStatus != "VERIFIED" ||
            persistedAsset.packageName.isNullOrBlank() ||
            persistedAsset.versionCode == null ||
            persistedAsset.signingCertificateSha256.isNullOrBlank() ||
            persistedAsset.updateStatus != UpdateStatus.UPDATE_AVAILABLE.name
        ) {
            return false
        }
        val record = appDao.getRegisteredAppRecord(registeredAppId) ?: return false
        return record.comparisons.any { comparison ->
            comparison.releaseSnapshotId == snapshot.releaseSnapshotId &&
                comparison.referenceAssetId == persistedAsset.releaseAssetId &&
                comparison.expectedCommitSha == resolvedCommitSha &&
                comparison.status == "COMPLETED" &&
                comparison.outcome == ComparisonOutcome.MATCH.name &&
                snapshot.selectedProviderAssetId == candidateAsset.providerAssetId
        }
    }

    private suspend fun reevaluateCandidates(
        app: RegisteredAppEntity,
        effective: EffectiveReleaseCheckSettings,
    ) {
        val channel = ReleaseCheckChannel.valueOf(effective.releaseChannel)
        releaseDao.getAllCandidates(app.registeredAppId).forEach { candidate ->
            val assets = runCatching { json.decodeFromString<List<ReleaseCandidateAsset>>(candidate.assetsJson) }
                .getOrElse { throw IllegalStateException("Stored release candidate metadata is invalid.", it) }
            val savedSelection = SavedAssetSelection.selectFilename(
                assets.map(ReleaseCandidateAsset::name),
                app.savedAssetSelectionJson,
            )
            val state = when {
                channel == ReleaseCheckChannel.STABLE_ONLY && candidate.isPrerelease ->
                    ReleaseCandidateState.OBSOLETE
                assets.isEmpty() -> ReleaseCandidateState.NO_APK_ASSET
                assets.size > 1 && savedSelection == null -> ReleaseCandidateState.ASSET_SELECTION_REQUIRED
                isAlreadyVerified(
                    registeredAppId = app.registeredAppId,
                    providerReleaseId = candidate.providerReleaseId,
                    tagName = candidate.tagName,
                    resolvedCommitSha = candidate.resolvedCommitSha,
                    releaseName = candidate.releaseName,
                    releaseUrl = candidate.releaseUrl,
                    targetCommitishRaw = candidate.targetCommitishRaw,
                    isPrerelease = candidate.isPrerelease,
                    isImmutable = candidate.isImmutable,
                    releaseCreatedAt = candidate.releaseCreatedAt,
                    publishedAt = candidate.publishedAt,
                    assets = assets,
                ) -> ReleaseCandidateState.VERIFIED_UPDATE_AVAILABLE
                app.managementMode == "VERIFICATION" -> ReleaseCandidateState.VERIFICATION_REQUIRED
                else -> ReleaseCandidateState.NEW_RELEASE_DISCOVERED
            }
            if (candidate.state != state.name) {
                releaseDao.upsertCandidate(candidate.copy(state = state.name))
            }
        }
    }

    private fun candidateAsset(candidate: ProviderAssetCandidate) = ReleaseCandidateAsset(
        providerAssetId = candidate.asset.id,
        name = candidate.asset.name,
        stableUrl = candidate.asset.browserDownloadUrl,
        contentType = candidate.asset.contentType,
        providerSizeBytes = candidate.asset.size,
        providerDigestSha256 = candidate.providerSha256,
        providerCreatedAt = candidate.asset.providerCreatedAt,
    )

    private fun notificationType(state: ReleaseCandidateState): ReleaseNotificationType? = when (state) {
        ReleaseCandidateState.NEW_RELEASE_DISCOVERED -> ReleaseNotificationType.NEW_RELEASE
        ReleaseCandidateState.VERIFICATION_REQUIRED -> ReleaseNotificationType.VERIFICATION_REQUIRED
        ReleaseCandidateState.VERIFIED_UPDATE_AVAILABLE -> ReleaseNotificationType.VERIFIED_UPDATE_AVAILABLE
        ReleaseCandidateState.ASSET_SELECTION_REQUIRED -> ReleaseNotificationType.ASSET_SELECTION_REQUIRED
        ReleaseCandidateState.NO_APK_ASSET,
        ReleaseCandidateState.OBSOLETE,
        -> null
    }

    private fun runEntity(
        appId: String,
        trigger: ReleaseCheckTrigger,
        effective: EffectiveReleaseCheckSettings,
        outcome: ReleaseCheckOutcome,
        evidence: String,
        started: Instant,
        finished: Instant,
    ) = ReleaseCheckRunEntity(
        checkRunId = UUID.randomUUID().toString(),
        registeredAppId = appId,
        trigger = trigger.name,
        effectiveSettingsJson = json.encodeToString(effective),
        outcome = outcome.name,
        providerEvidenceJson = evidence,
        startedAt = started.toString(),
        finishedAt = finished.toString(),
    )

    private fun terminalSchedule(
        appId: String,
        effective: EffectiveReleaseCheckSettings,
        finished: Instant,
    ) = ReleaseScheduleStateEntity(
        registeredAppId = appId,
        lastAttemptAt = finished.toString(),
        lastTerminalAt = finished.toString(),
        nextEligibleAt = ReleaseCheckPolicy.nextTerminalTime(effective, finished, zoneId()).toString(),
        waitingReason = ReleaseCheckWaitingReason.NONE.name,
        consecutiveRetry = 0,
        updatedAt = finished.toString(),
    )

    private suspend fun defer(app: RegisteredAppEntity, reason: ReleaseCheckWaitingReason, next: Instant) {
        val now = clock.instant()
        val current = releaseDao.getScheduleState(app.registeredAppId)
        releaseDao.upsertScheduleState(
            ReleaseScheduleStateEntity(
                registeredAppId = app.registeredAppId,
                lastAttemptAt = current?.lastAttemptAt,
                lastTerminalAt = current?.lastTerminalAt,
                nextEligibleAt = next.toString(),
                waitingReason = reason.name,
                consecutiveRetry = current?.consecutiveRetry ?: 0,
                updatedAt = now.toString(),
            ),
        )
    }

    private suspend fun currentSettings(): ReleaseCheckSettingsEntity =
        releaseDao.getSettings() ?: ReleaseCheckSettingsEntity(updatedAt = clock.instant().toString()).also {
            releaseDao.upsertSettings(it)
        }

    private fun evidence(requestCount: Int, receivedBytes: Long, notModified: Boolean = false): String =
        buildJsonObject {
            put("requestCount", requestCount)
            put("receivedBytes", receivedBytes)
            put("representationNotModified", notModified)
        }.toString()

    private fun dedupKey(candidate: ReleaseCandidateEntity, notificationType: String): String = sha256(
        listOf(
            candidate.provider,
            candidate.instance,
            candidate.providerRepositoryId,
            candidate.providerReleaseId,
            candidate.observationSha256,
            notificationType,
        ).joinToString("\u0000"),
    )

    private fun stableUuid(value: String): String = UUID.nameUUIDFromBytes(value.toByteArray()).toString()

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun parseInstant(value: String?): Instant? = value?.let { runCatching { Instant.parse(it) }.getOrNull() }

    private fun isValidScheduleState(state: ReleaseScheduleStateEntity): Boolean =
        runCatching { ReleaseCheckWaitingReason.valueOf(state.waitingReason) }.isSuccess &&
            state.consecutiveRetry in 0..RETRY_MINUTES.size &&
            parseInstant(state.nextEligibleAt) != null &&
            parseInstant(state.updatedAt) != null &&
            (state.lastAttemptAt == null || parseInstant(state.lastAttemptAt) != null) &&
            (state.lastTerminalAt == null || parseInstant(state.lastTerminalAt) != null)

    private fun isValidCooldown(cooldown: ProviderCooldownEntity): Boolean =
        parseInstant(cooldown.notBefore) != null &&
            parseInstant(cooldown.updatedAt) != null &&
            (cooldown.rateLimitRemaining == null || cooldown.rateLimitRemaining >= 0) &&
            (cooldown.rateLimitResetAt == null || parseInstant(cooldown.rateLimitResetAt) != null)

    private fun notificationsAllowed(): Boolean {
        val runtimePermissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return runtimePermissionGranted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                context.getString(R.string.release_notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.release_notification_channel_description) },
        )
    }

    private fun postNotification(
        app: RegisteredAppEntity,
        candidate: ReleaseCandidateEntity,
        outbox: NotificationOutboxEntity,
    ) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            throw SecurityException("Notification permission is not granted.")
        }
        val route = ReproDroidRoute.AppInformation(app.registeredAppId).encode()
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_ROUTE, route)
            .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(
            context,
            outbox.notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val body = when (ReleaseNotificationType.valueOf(outbox.notificationType)) {
            ReleaseNotificationType.NEW_RELEASE -> context.getString(R.string.release_notification_new, candidate.tagName)
            ReleaseNotificationType.VERIFICATION_REQUIRED -> context.getString(R.string.release_notification_verify, candidate.tagName)
            ReleaseNotificationType.ASSET_SELECTION_REQUIRED -> context.getString(R.string.release_notification_select, candidate.tagName)
            ReleaseNotificationType.VERIFIED_UPDATE_AVAILABLE -> context.getString(R.string.release_notification_verified, candidate.tagName)
        }
        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_release)
            .setContentTitle(app.resolvedDisplayName)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        NotificationManagerCompat.from(context).notify(outbox.notificationId, notification)
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "release_updates"
        private const val PROVIDER = "GITHUB"
        private const val INSTANCE = "github.com"
        private const val MAX_APPS_PER_WORKER = 8
        private const val MAX_MAINTENANCE_RECORDS = 100
        private const val MAX_NOTIFICATION_ID_PROBES = 1024
        private const val CONSTRAINT_RETRY_MINUTES = 30L
        private const val WORKER_TIMEOUT_MILLIS = 8L * 60L * 1000L
        private val RETRY_MINUTES = listOf(30L, 60L, 120L)
        private val OPERATIONAL_WAITING_REASONS = setOf(
            ReleaseCheckWaitingReason.DEFERRED_NETWORK,
            ReleaseCheckWaitingReason.DEFERRED_BATTERY,
            ReleaseCheckWaitingReason.PROVIDER_COOLDOWN,
            ReleaseCheckWaitingReason.RETRY_BACKOFF,
        )
    }
}
