package com.sanka1610.reprodroid.data.repository

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.room.withTransaction
import com.sanka1610.reprodroid.data.artifact.ApkInspector
import com.sanka1610.reprodroid.data.artifact.ApkComparisonException
import com.sanka1610.reprodroid.data.artifact.ApkContentComparator
import com.sanka1610.reprodroid.data.artifact.AdvancedApkComparator
import com.sanka1610.reprodroid.data.artifact.AdvancedApkComparison
import com.sanka1610.reprodroid.data.artifact.ExpectedApkFile
import com.sanka1610.reprodroid.data.artifact.GitHubAssetDownloader
import com.sanka1610.reprodroid.data.artifact.CodebergAssetDownloader
import com.sanka1610.reprodroid.data.artifact.ReleaseApkInstaller
import com.sanka1610.reprodroid.data.artifact.ReferenceAssetDownloadException
import com.sanka1610.reprodroid.data.local.ArtifactDownloadStatus
import com.sanka1610.reprodroid.data.local.AppBuildConfigurationEntity
import com.sanka1610.reprodroid.data.local.AppGroupEntity
import com.sanka1610.reprodroid.data.local.AppMetadataUpdate
import com.sanka1610.reprodroid.data.local.AppRepositoryBindingEntity
import com.sanka1610.reprodroid.data.local.AppSourceHeadEntity
import com.sanka1610.reprodroid.data.local.AppTrackingState
import com.sanka1610.reprodroid.data.local.ArtifactEntity
import com.sanka1610.reprodroid.data.local.AdvancedComparisonAxis
import com.sanka1610.reprodroid.data.local.AdvancedComparisonEntryEntity
import com.sanka1610.reprodroid.data.local.AdvancedComparisonSummaryEntity
import com.sanka1610.reprodroid.data.local.ApkEntryEvidenceEntity
import com.sanka1610.reprodroid.data.local.AppSettingsUpdate
import com.sanka1610.reprodroid.data.local.AssetSelectionReason
import com.sanka1610.reprodroid.data.local.ComparisonEntryEntity
import com.sanka1610.reprodroid.data.local.ComparisonOutcome
import com.sanka1610.reprodroid.data.local.ComparisonEligibility
import com.sanka1610.reprodroid.data.local.ComparisonRunEntity
import com.sanka1610.reprodroid.data.local.ComparisonRunStatus
import com.sanka1610.reprodroid.data.local.ManagedAppDao
import com.sanka1610.reprodroid.data.local.ManagementMode
import com.sanka1610.reprodroid.data.local.GlobalSettingsEntity
import com.sanka1610.reprodroid.data.local.InstallationSource
import com.sanka1610.reprodroid.data.local.InstallAttemptStatus
import com.sanka1610.reprodroid.data.local.PreferredAbi
import com.sanka1610.reprodroid.data.local.ReferenceDownloadStatus
import com.sanka1610.reprodroid.data.local.RepositoryIdentityStatus
import com.sanka1610.reprodroid.data.local.RegisteredAppEntity
import com.sanka1610.reprodroid.data.local.RegisteredAppRecord
import com.sanka1610.reprodroid.data.local.ReleaseAssetEntity
import com.sanka1610.reprodroid.data.local.ReleaseDiscoveryStatus
import com.sanka1610.reprodroid.data.local.ReleaseSnapshotEntity
import com.sanka1610.reprodroid.data.local.ReleaseVariantPreference
import com.sanka1610.reprodroid.data.local.ResourceAvailabilityEntity
import com.sanka1610.reprodroid.data.local.ReleaseObservationHasher
import com.sanka1610.reprodroid.data.local.ReleaseObservationCandidate
import com.sanka1610.reprodroid.data.local.ReleaseObservationInput
import com.sanka1610.reprodroid.data.local.ReleaseMetadataObservationCandidate
import com.sanka1610.reprodroid.data.local.ReleaseMetadataObservationInput
import com.sanka1610.reprodroid.data.local.ReproDroidDatabase
import com.sanka1610.reprodroid.data.local.SemanticDifferenceEvidenceEntity
import com.sanka1610.reprodroid.data.local.SourceDiscoveryEntity
import com.sanka1610.reprodroid.data.local.GradleCandidateEntity
import com.sanka1610.reprodroid.data.local.ThemeMode
import com.sanka1610.reprodroid.data.local.UpdateStatus
import com.sanka1610.reprodroid.data.provider.GitHubProviderException
import com.sanka1610.reprodroid.data.provider.CodebergProviderException
import com.sanka1610.reprodroid.data.provider.GitHubRepositoryParser
import com.sanka1610.reprodroid.data.provider.CodebergRepositoryParser
import com.sanka1610.reprodroid.data.provider.GitHubRepositoryDiscoveryClient
import com.sanka1610.reprodroid.data.provider.CodebergRepositoryDiscoveryClient
import com.sanka1610.reprodroid.data.provider.GitHubReleasesClient
import com.sanka1610.reprodroid.data.provider.CodebergReleasesClient
import com.sanka1610.reprodroid.data.provider.ReleaseAssetCandidate
import com.sanka1610.reprodroid.data.provider.RepositoryRegistrationPreview
import com.sanka1610.reprodroid.data.provider.ResolvedGitHubRelease
import com.sanka1610.reprodroid.data.provider.ResolvedProviderRelease
import com.sanka1610.reprodroid.data.provider.ProviderAssetCandidate
import com.sanka1610.reprodroid.data.provider.ProviderSelectedAsset
import com.sanka1610.reprodroid.data.provider.ProviderReleaseClient
import com.sanka1610.reprodroid.data.provider.ProviderRepositoryDiscoveryClient
import com.sanka1610.reprodroid.data.provider.ReleaseAssetSelectionException
import com.sanka1610.reprodroid.data.provider.SavedAssetSelection
import com.sanka1610.reprodroid.data.provider.SavedAssetSelectionCondition
import com.sanka1610.reprodroid.data.artifact.CodebergAssetDownloadPolicy
import com.sanka1610.reprodroid.data.provider.CodebergRepository
import com.sanka1610.reprodroid.data.network.JobState
import com.sanka1610.reprodroid.data.storage.AndroidStorageManager
import com.sanka1610.reprodroid.data.storage.AndroidCleanupManager
import com.sanka1610.reprodroid.data.storage.RunnerRetentionCoordinator
import com.sanka1610.reprodroid.data.network.RevisionType
import com.sanka1610.reprodroid.data.network.GenericBuildAttempt
import com.sanka1610.reprodroid.data.network.CreateGenericComparisonRequest
import com.sanka1610.reprodroid.data.network.OfficialApkIdentity
import com.sanka1610.reprodroid.data.network.RawComparisonResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.security.MessageDigest

data class AppDeletionPreview(
    val previewId: String,
    val registeredAppId: String,
    val expectedUpdatedAt: String,
    val displayName: String,
    val releaseCount: Int,
    val comparisonCount: Int,
    val installAttemptCount: Int,
    val sourceDiscoveryCount: Int,
    val buildConfigurationCount: Int,
    val referenceAssetIds: List<String>,
    val localBytes: Long,
    val protectionReasons: List<String>,
    val createdAt: String,
    val expiresAt: String,
)

data class AppDeletionResult(
    val registeredAppId: String,
    val deletedFiles: Int,
    val releasedBytes: Long,
    val failedFileNames: List<String>,
)

data class ExistingPrimaryRegistration(
    val registeredAppId: String,
    val displayName: String,
    val trackingState: String,
)

class ManagedAppRepository(
    private val context: Context,
    private val database: ReproDroidDatabase,
    private val jobRepository: JobRepository,
    private val provider: GitHubReleasesClient = GitHubReleasesClient(),
    private val repositoryDiscoveryClient: GitHubRepositoryDiscoveryClient = GitHubRepositoryDiscoveryClient(),
    private val downloader: GitHubAssetDownloader = GitHubAssetDownloader(),
    private val codebergProvider: ProviderReleaseClient = CodebergReleasesClient(),
    private val codebergRepositoryDiscoveryClient: ProviderRepositoryDiscoveryClient = CodebergRepositoryDiscoveryClient(),
    private val codebergDownloader: CodebergAssetDownloader = CodebergAssetDownloader(),
    private val comparator: ApkContentComparator = ApkContentComparator(),
    private val advancedComparator: AdvancedApkComparator = AdvancedApkComparator(),
    private val storageManager: AndroidStorageManager = AndroidStorageManager(context, database),
    private val cleanupManager: AndroidCleanupManager = AndroidCleanupManager(context, database),
    private val retentionCoordinator: RunnerRetentionCoordinator? = null,
) {
    private val dao: ManagedAppDao = database.managedAppDao()
    private val inspector = ApkInspector(context.packageManager)
    private val releaseInstaller = ReleaseApkInstaller(context, dao)
    private val packageInstaller = context.packageManager.packageInstaller
    private val referenceDirectory = File(context.filesDir, "reference-apks")
    private val iconDirectory = File(context.filesDir, "reference-icons")
    private val referenceDownloadMutex = Mutex()

    fun observeApps(): Flow<List<RegisteredAppRecord>> = dao.observeRegisteredApps()

    fun observeInactiveApps(): Flow<List<RegisteredAppRecord>> = dao.observeInactiveRegisteredApps()

    fun observeGroups(): Flow<List<AppGroupEntity>> = dao.observeAppGroups()

    fun observeSettings(): Flow<GlobalSettingsEntity> = dao.observeGlobalSettings().map { settings ->
        settings ?: defaultSettings()
    }

    fun observeAvailability(): Flow<List<ResourceAvailabilityEntity>> = database.storageDao().observeAvailability()

    suspend fun ensureSettings() {
        if (dao.getGlobalSettings() == null) dao.upsertGlobalSettings(defaultSettings())
        dao.interruptRunningSourceDiscoveries(Instant.now().toString())
        storageManager.reconcileAvailability()
        cleanupManager.reconcileInterruptedRuns()
        retentionCoordinator?.syncCurrentComparisonHolds()
    }

    fun observeApp(registeredAppId: String): Flow<RegisteredAppRecord?> =
        dao.observeRegisteredApp(registeredAppId)

    suspend fun previewLatest(repositoryUrl: String): ResolvedProviderRelease {
        val settings = currentSettings()
        return releaseClient(repositoryUrl).resolveLatestRelease(
            repositoryUrl,
            null,
            enumValueOrDefault(settings.defaultPreferredAbi, PreferredAbi.ARM64_V8A),
            enumValueOrDefault(settings.defaultReleaseVariantPreference, ReleaseVariantPreference.RELEASE),
        )
    }

    suspend fun previewRepository(repositoryUrl: String): RepositoryRegistrationPreview =
        discoveryProvider(repositoryUrl).preview(repositoryUrl)

    suspend fun findPrimaryRegistration(providerRepositoryId: String): ExistingPrimaryRegistration? {
        val binding = dao.getRepositoryBinding(
            provider = PROVIDER_GITHUB,
            instance = GITHUB_INSTANCE,
            providerRepositoryId = providerRepositoryId,
            registrationSlot = PRIMARY_REGISTRATION_SLOT,
        ) ?: return null
        val app = dao.getRegisteredApp(binding.registeredAppId) ?: return null
        return ExistingPrimaryRegistration(
            registeredAppId = app.registeredAppId,
            displayName = app.resolvedDisplayName,
            trackingState = app.trackingState,
        )
    }

    suspend fun findPrimaryRegistration(
        provider: String,
        instance: String,
        providerRepositoryId: String,
    ): ExistingPrimaryRegistration? {
        val binding = dao.getRepositoryBinding(
            provider = provider,
            instance = instance,
            providerRepositoryId = providerRepositoryId,
            registrationSlot = PRIMARY_REGISTRATION_SLOT,
        ) ?: return null
        val app = dao.getRegisteredApp(binding.registeredAppId) ?: return null
        return ExistingPrimaryRegistration(app.registeredAppId, app.resolvedDisplayName, app.trackingState)
    }

    private fun releaseClient(repositoryUrl: String): ProviderReleaseClient {
        val host = runCatching { URI(repositoryUrl.trim()).host?.lowercase() }.getOrNull()
        return when (host) {
            "github.com" -> provider
            "codeberg.org" -> codebergProvider
            else -> throw IllegalArgumentException("Only public GitHub and Codeberg repositories are supported.")
        }
    }

    private fun releaseClient(providerName: String, instance: String): ProviderReleaseClient? = when {
        providerName == provider.providerName && instance == provider.providerInstance -> provider
        providerName == codebergProvider.providerName && instance == codebergProvider.providerInstance -> codebergProvider
        else -> null
    }

    private fun discoveryProvider(repositoryUrl: String): ProviderRepositoryDiscoveryClient {
        val host = runCatching { URI(repositoryUrl.trim()).host?.lowercase() }.getOrNull()
        return when (host) {
            "github.com" -> repositoryDiscoveryClient
            "codeberg.org" -> codebergRepositoryDiscoveryClient
            else -> throw IllegalArgumentException("Only public GitHub and Codeberg repositories are supported.")
        }
    }

    private fun discoveryProvider(providerName: String, instance: String): ProviderRepositoryDiscoveryClient? = when {
        providerName == repositoryDiscoveryClient.providerName && instance == repositoryDiscoveryClient.providerInstance ->
            repositoryDiscoveryClient
        providerName == codebergRepositoryDiscoveryClient.providerName && instance == codebergRepositoryDiscoveryClient.providerInstance ->
            codebergRepositoryDiscoveryClient
        else -> null
    }

    suspend fun createGroup(displayName: String): AppGroupEntity {
        val normalizedName = validatedGroupName(displayName)
        val now = Instant.now().toString()
        return database.withTransaction {
            check(dao.getAppGroupByName(normalizedName) == null) {
                "An app group with this name already exists."
            }
            val currentMaximum = dao.getMaximumAppGroupSortOrder() ?: -GROUP_SORT_SPACING
            check(currentMaximum <= Long.MAX_VALUE - GROUP_SORT_SPACING) {
                "App group ordering overflow."
            }
            AppGroupEntity(
                groupId = UUID.randomUUID().toString(),
                displayName = normalizedName,
                sortOrder = currentMaximum + GROUP_SORT_SPACING,
                createdAt = now,
                updatedAt = now,
            ).also { dao.upsertAppGroup(it) }
        }
    }

    suspend fun renameGroup(groupId: String, displayName: String): AppGroupEntity {
        val normalizedId = canonicalUuid(groupId, "App group ID")
        val normalizedName = validatedGroupName(displayName)
        return database.withTransaction {
            val group = dao.getAppGroup(normalizedId)
                ?: throw IllegalArgumentException("App group was not found.")
            val collision = dao.getAppGroupByName(normalizedName)
            check(collision == null || collision.groupId == group.groupId) {
                "An app group with this name already exists."
            }
            group.copy(displayName = normalizedName, updatedAt = Instant.now().toString())
                .also { dao.upsertAppGroup(it) }
        }
    }

    suspend fun reorderGroups(orderedGroupIds: List<String>) {
        require(orderedGroupIds.size <= MAX_GROUPS) { "Too many app groups were supplied." }
        val normalizedIds = orderedGroupIds.map { canonicalUuid(it, "App group ID") }
        require(normalizedIds.distinct().size == normalizedIds.size) { "App group IDs must be unique." }
        database.withTransaction {
            val groups = dao.getAppGroups()
            check(groups.map(AppGroupEntity::groupId).toSet() == normalizedIds.toSet()) {
                "The app group list changed; reload before reordering."
            }
            val byId = groups.associateBy(AppGroupEntity::groupId)
            val now = Instant.now().toString()
            normalizedIds.forEachIndexed { index, groupId ->
                dao.upsertAppGroup(
                    requireNotNull(byId[groupId]).copy(
                        sortOrder = Long.MIN_VALUE + index,
                        updatedAt = now,
                    ),
                )
            }
            normalizedIds.forEachIndexed { index, groupId ->
                dao.upsertAppGroup(
                    requireNotNull(byId[groupId]).copy(
                        sortOrder = index * GROUP_SORT_SPACING,
                        updatedAt = now,
                    ),
                )
            }
        }
    }

    suspend fun deleteGroup(groupId: String) {
        val normalizedId = canonicalUuid(groupId, "App group ID")
        database.withTransaction {
            check(dao.getAppGroup(normalizedId) != null) { "App group was not found." }
            val now = Instant.now().toString()
            dao.clearGroupAssignments(normalizedId, now)
            dao.deleteAppGroup(normalizedId)
        }
    }

    suspend fun updateMetadata(registeredAppId: String, update: AppMetadataUpdate) {
        val displayNameOverride = normalizedOptionalText(
            update.displayNameOverride,
            MAX_DISPLAY_NAME_LENGTH,
            "Display name",
        )
        val authorDisplayOverride = normalizedOptionalText(
            update.authorDisplayOverride,
            MAX_AUTHOR_LENGTH,
            "Author display name",
        )
        val note = update.note.trimEnd()
        require(note.length <= MAX_NOTE_LENGTH) { "The note is too long." }
        require(note.toByteArray(StandardCharsets.UTF_8).size <= MAX_NOTE_UTF8_BYTES) {
            "The note is too large."
        }
        database.withTransaction {
            val app = dao.getRegisteredApp(registeredAppId)
                ?: throw IllegalArgumentException("Registered app was not found.")
            check(app.trackingState == AppTrackingState.ACTIVE.name) {
                "Resume tracking before editing app metadata."
            }
            check(app.updatedAt == update.expectedUpdatedAt) {
                "The app changed; reload before saving metadata."
            }
            update.groupId?.let { groupId ->
                check(dao.getAppGroup(canonicalUuid(groupId, "App group ID")) != null) {
                    "The selected app group no longer exists."
                }
            }
            dao.upsertRegisteredApp(
                app.copy(
                    displayNameOverride = displayNameOverride,
                    authorDisplayOverride = authorDisplayOverride,
                    note = note,
                    groupId = update.groupId?.let { canonicalUuid(it, "App group ID") },
                    updatedAt = Instant.now().toString(),
                ),
            )
        }
    }

    suspend fun updateTrackingSource(
        registeredAppId: String,
        expectedUpdatedAt: String,
        preview: RepositoryRegistrationPreview,
    ) {
        validateRegistrationPreview(preview)
        val app = dao.getRegisteredApp(registeredAppId)
            ?: throw IllegalArgumentException("Registered app was not found.")
        check(app.trackingState == AppTrackingState.ACTIVE.name) {
            "Resume tracking before changing the tracked source."
        }
        check(app.updatedAt == expectedUpdatedAt) { "The app changed; reload before changing its source." }
        val binding = dao.getRepositoryBinding(registeredAppId)
            ?: throw IllegalStateException("Repository identity is not available.")
        check(binding.provider == preview.identity.provider && binding.instance == preview.identity.instance) {
            "The current repository provider cannot be edited by this source editor."
        }
        check(binding.providerRepositoryId == preview.identity.providerRepositoryId) {
            "The new URL identifies a different repository. Register it as a separate app."
        }
        val now = Instant.now().toString()
        val discoveryId = UUID.randomUUID().toString()
        val discovery = preview.toDiscovery(registeredAppId, discoveryId, now)
        database.withTransaction {
            val currentApp = dao.getRegisteredApp(registeredAppId)
                ?: throw IllegalArgumentException("Registered app was not found.")
            val currentBinding = dao.getRepositoryBinding(registeredAppId)
                ?: throw IllegalStateException("Repository identity is not available.")
            check(currentApp.updatedAt == expectedUpdatedAt) {
                "The app changed; reload before changing its source."
            }
            check(currentBinding.providerRepositoryId == preview.identity.providerRepositoryId) {
                "Repository identity changed while the source edit was being confirmed."
            }
            dao.upsertSourceDiscovery(discovery)
            dao.upsertGradleCandidates(preview.toCandidates(discoveryId))
            val currentHead = dao.getAppSourceHead(registeredAppId)
            dao.upsertAppSourceHead(
                AppSourceHeadEntity(
                    registeredAppId = registeredAppId,
                    latestDiscoveryId = discoveryId,
                    selectedConfigurationRevision = currentHead?.selectedConfigurationRevision,
                    updatedAt = now,
                ),
            )
            dao.upsertRepositoryBinding(currentBinding.copy(verifiedAt = now))
            dao.upsertRegisteredApp(
                currentApp.copy(
                    displayName = preview.identity.displayName,
                    repositoryUrl = preview.normalizedInputUrl,
                    canonicalRepositoryUrl = preview.normalizedInputUrl,
                    updatedAt = now,
                ),
            )
        }
    }

    suspend fun stopTracking(registeredAppId: String) {
        database.withTransaction {
            val app = dao.getRegisteredApp(registeredAppId)
                ?: throw IllegalArgumentException("Registered app was not found.")
            check(app.trackingState == AppTrackingState.ACTIVE.name) { "App tracking is already inactive." }
            val now = Instant.now().toString()
            dao.upsertRegisteredApp(
                app.copy(
                    trackingState = AppTrackingState.INACTIVE.name,
                    trackingStoppedAt = now,
                    updatedAt = now,
                ),
            )
        }
    }

    suspend fun stopTrackingAfterConfirmedUninstall(registeredAppId: String) {
        confirmUninstall(registeredAppId)
        stopTracking(registeredAppId)
    }

    suspend fun confirmUninstall(registeredAppId: String) {
        val record = dao.getRegisteredAppRecord(registeredAppId)
            ?: throw IllegalArgumentException("Registered app was not found.")
        val packageName = record.latestRelease?.selectedAsset?.packageName
            ?: record.releases.asSequence()
                .flatMap { it.assets.asSequence() }
                .mapNotNull { it.packageName }
                .firstOrNull()
            ?: throw IllegalStateException("The app package is not known, so uninstall cannot be confirmed.")
        check(installedPackageVersion(packageName) == null) {
            "Android still reports the package as installed. Tracking was not changed."
        }
        refreshInstalledStateForApp(registeredAppId)
    }

    suspend fun resumeTracking(registeredAppId: String) {
        database.withTransaction {
            val app = dao.getRegisteredApp(registeredAppId)
                ?: throw IllegalArgumentException("Registered app was not found.")
            check(app.trackingState == AppTrackingState.INACTIVE.name) { "App tracking is already active." }
            val now = Instant.now().toString()
            dao.upsertRegisteredApp(
                app.copy(
                    trackingState = AppTrackingState.ACTIVE.name,
                    trackingStoppedAt = null,
                    updatedAt = now,
                ),
            )
        }
    }

    suspend fun previewCompleteDeletion(registeredAppId: String): AppDeletionPreview {
        val record = dao.getRegisteredAppRecord(registeredAppId)
            ?: throw IllegalArgumentException("Registered app was not found.")
        check(record.app.trackingState == AppTrackingState.INACTIVE.name) {
            "Stop tracking this app before deleting its local history."
        }
        val assetIds = record.releases
            .flatMap { it.assets }
            .map { canonicalUuid(it.releaseAssetId, "Release asset ID") }
            .distinct()
            .sorted()
        val storageDao = database.storageDao()
        val activeInstallAssetIds = storageDao.getActiveReleaseInstallAssetIds().toSet()
        val reservations = storageDao.getActiveReservations()
        val activeExports = storageDao.getActiveAuditExports()
        val unsafeLocalPath = withContext(Dispatchers.IO) {
            assetIds.any { assetId ->
                listOf(finalFile(assetId), iconFile(assetId), partFile(assetId), partIconFile(assetId))
                    .any { Files.isSymbolicLink(it.toPath()) }
            }
        }
        val protectionReasons = buildList {
            if (assetIds.any(activeInstallAssetIds::contains)) add("INSTALL_IN_PROGRESS")
            if (record.comparisons.any { it.status != ComparisonRunStatus.COMPLETED.name }) {
                add("COMPARISON_IN_PROGRESS")
            }
            if (
                reservations.any {
                    it.resourceKind in setOf("REFERENCE_APK", "REFERENCE_ICON") && it.resourceId in assetIds
                }
            ) {
                add("ACTIVE_STORAGE_RESERVATION")
            }
            if (activeExports.isNotEmpty()) add("AUDIT_EXPORT_IN_PROGRESS")
            if (unsafeLocalPath) add("UNSAFE_LOCAL_PATH")
        }
        val localBytes = withContext(Dispatchers.IO) {
            assetIds.sumOf { assetId ->
                listOf(finalFile(assetId), iconFile(assetId), partFile(assetId), partIconFile(assetId))
                    .sumOf { file -> runCatching { confinedRegularFileSize(file) }.getOrDefault(0L) }
            }
        }
        val now = Instant.now()
        return AppDeletionPreview(
            previewId = UUID.randomUUID().toString(),
            registeredAppId = record.app.registeredAppId,
            expectedUpdatedAt = record.app.updatedAt,
            displayName = record.app.resolvedDisplayName,
            releaseCount = record.releases.size,
            comparisonCount = record.comparisons.size,
            installAttemptCount = record.releaseInstallAttempts.size,
            sourceDiscoveryCount = record.sourceDiscoveries.size,
            buildConfigurationCount = record.buildConfigurations.size,
            referenceAssetIds = assetIds,
            localBytes = localBytes,
            protectionReasons = protectionReasons.distinct(),
            createdAt = now.toString(),
            expiresAt = now.plus(DELETION_PREVIEW_MINUTES, ChronoUnit.MINUTES).toString(),
        )
    }

    suspend fun executeCompleteDeletion(preview: AppDeletionPreview): AppDeletionResult {
        canonicalUuid(preview.previewId, "Deletion preview ID")
        check(Instant.now().isBefore(Instant.parse(preview.expiresAt))) {
            "The deletion preview expired. Create a new preview."
        }
        val current = previewCompleteDeletion(preview.registeredAppId)
        check(current.expectedUpdatedAt == preview.expectedUpdatedAt) {
            "The app changed after the deletion preview. Create a new preview."
        }
        check(current.referenceAssetIds == preview.referenceAssetIds) {
            "The app resources changed after the deletion preview. Create a new preview."
        }
        check(current.protectionReasons.isEmpty()) {
            "Protected resources prevent complete deletion: ${current.protectionReasons.joinToString()}."
        }
        val ownedFiles = withContext(Dispatchers.IO) {
            current.referenceAssetIds.flatMap { assetId ->
                listOf(finalFile(assetId), iconFile(assetId), partFile(assetId), partIconFile(assetId))
            }.map { file -> file to confinedRegularFileSize(file) }
        }
        database.withTransaction {
            val record = dao.getRegisteredAppRecord(preview.registeredAppId)
                ?: throw IllegalArgumentException("Registered app was not found.")
            check(record.app.trackingState == AppTrackingState.INACTIVE.name) {
                "App tracking was resumed during deletion."
            }
            check(record.app.updatedAt == preview.expectedUpdatedAt) {
                "The app changed during deletion."
            }
            current.referenceAssetIds.forEach { assetId ->
                database.storageDao().deleteAvailability("REFERENCE_APK", assetId)
                database.storageDao().deleteAvailability("REFERENCE_ICON", assetId)
            }
            dao.deleteRegisteredApp(preview.registeredAppId)
        }
        var deletedFiles = 0
        var releasedBytes = 0L
        val failedFileNames = withContext(NonCancellable + Dispatchers.IO) {
            buildList {
                ownedFiles.forEach { (file, size) ->
                    val deleted = runCatching {
                        requireConfinedOwnedFile(file)
                        Files.deleteIfExists(file.toPath())
                    }.getOrDefault(false)
                    if (deleted) {
                        deletedFiles += 1
                        releasedBytes += size
                    } else if (Files.exists(file.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                        add(file.name)
                    }
                }
            }
        }
        return AppDeletionResult(
            registeredAppId = preview.registeredAppId,
            deletedFiles = deletedFiles,
            releasedBytes = releasedBytes,
            failedFileNames = failedFileNames,
        )
    }

    suspend fun registerRepository(
        preview: RepositoryRegistrationPreview,
        mode: ManagementMode,
        installationSource: InstallationSource,
        separateManagementTarget: Boolean = false,
    ): String {
        validateModeAndInstallationSource(mode, installationSource)
        validateRegistrationPreview(preview)
        val identity = preview.identity
        val slot = if (separateManagementTarget) UUID.randomUUID().toString() else PRIMARY_REGISTRATION_SLOT
        val existingBinding = dao.getRepositoryBinding(
            provider = identity.provider,
            instance = identity.instance,
            providerRepositoryId = identity.providerRepositoryId,
            registrationSlot = PRIMARY_REGISTRATION_SLOT,
        )
        if (!separateManagementTarget && existingBinding != null) {
            throw IllegalStateException("This repository is already registered as the primary management target.")
        }
        val settings = currentSettings()
        val now = Instant.now().toString()
        val appId = UUID.randomUUID().toString()
        val discoveryId = UUID.randomUUID().toString()
        val defaultBuildRoot = preview.discovery.candidates.singleOrNull()
            ?.takeIf { preview.discovery.state == "COMPLETE" }
            ?.buildRoot
        val validatedConfiguration = BuildConfigurationValidator.validate(
            BuildConfigurationInput(buildRoot = defaultBuildRoot),
        )
        val app = RegisteredAppEntity(
            registeredAppId = appId,
            displayName = identity.displayName,
            repositoryUrl = identity.repository.canonicalUrl,
            canonicalRepositoryUrl = identity.repository.canonicalUrl,
            provider = releaseProviderName(identity.provider),
            managementMode = mode.name,
            installationSource = installationSource.name,
            releaseVariantPreference = settings.defaultReleaseVariantPreference,
            preferredAbi = settings.defaultPreferredAbi,
            maxApkSizeBytes = settings.defaultMaxApkSizeBytes,
            useGlobalReleaseVariant = true,
            useGlobalPreferredAbi = true,
            useGlobalMaxApkSize = true,
            releaseDiscoveryStatus = ReleaseDiscoveryStatus.NOT_CHECKED.name,
            createdAt = now,
            updatedAt = now,
        )
        val discovery = preview.toDiscovery(appId, discoveryId, now)
        val configuration = AppBuildConfigurationEntity(
            registeredAppId = appId,
            revision = 1,
            schemaVersion = 1,
            canonicalJson = validatedConfiguration.canonicalJson,
            contentSha256 = validatedConfiguration.contentSha256,
            validationState = validatedConfiguration.validationState.name,
            createdAt = now,
        )
        database.withTransaction {
            val collision = dao.getRepositoryBinding(
                identity.provider,
                identity.instance,
                identity.providerRepositoryId,
                slot,
            )
            check(collision == null) { "This repository management slot was registered concurrently." }
            dao.upsertRegisteredApp(app)
            dao.upsertRepositoryBinding(
                AppRepositoryBindingEntity(
                    registeredAppId = appId,
                    provider = identity.provider,
                    instance = identity.instance,
                    providerRepositoryId = identity.providerRepositoryId,
                    identityStatus = RepositoryIdentityStatus.VERIFIED.name,
                    registrationSlot = slot,
                    verifiedAt = now,
                ),
            )
            dao.upsertSourceDiscovery(discovery)
            dao.upsertGradleCandidates(preview.toCandidates(discoveryId))
            dao.upsertBuildConfiguration(configuration)
            dao.upsertAppSourceHead(
                AppSourceHeadEntity(
                    registeredAppId = appId,
                    latestDiscoveryId = discoveryId,
                    selectedConfigurationRevision = configuration.revision,
                    updatedAt = now,
                ),
            )
        }
        return appId
    }

    suspend fun refreshSourceDiscovery(registeredAppId: String) {
        val app = dao.getRegisteredApp(registeredAppId)
            ?: throw IllegalArgumentException("Registered app was not found.")
        val binding = dao.getRepositoryBinding(registeredAppId)
            ?: throw IllegalStateException("Repository identity is not available.")
        val discoveryClient = discoveryProvider(binding.provider, binding.instance)
            ?: throw IllegalStateException("The registered repository provider is not supported.")
        val expectedProviderId = binding.providerRepositoryId
            ?: throw IllegalStateException("Legacy repository identity must be explicitly verified first.")
        val discoveryId = UUID.randomUUID().toString()
        val startedAt = Instant.now().toString()
        val oldHead = dao.getAppSourceHead(registeredAppId)
        val previousDiscovery = oldHead?.latestDiscoveryId?.let { dao.getSourceDiscovery(it) }
        val resolving = SourceDiscoveryEntity(
            discoveryId = discoveryId,
            registeredAppId = registeredAppId,
            repositoryProvider = binding.provider,
            repositoryInstance = binding.instance,
            providerRepositoryId = expectedProviderId,
            requestedBranch = previousDiscovery?.requestedBranch.orEmpty(),
            resolvedCommitSha = null,
            rootTreeSha = null,
            state = "RESOLVING",
            reason = null,
            entryCount = 0,
            requestCount = 0,
            receivedBytes = 0,
            maxDepth = 0,
            candidateCount = 0,
            excludedSymlinkCount = 0,
            excludedSubmoduleCount = 0,
            excludedCacheTreeCount = 0,
            startedAt = startedAt,
            finishedAt = null,
        )
        database.withTransaction {
            val currentBinding = dao.getRepositoryBinding(registeredAppId)
            check(currentBinding?.providerRepositoryId == expectedProviderId) {
                "Repository identity changed while discovery was running."
            }
            dao.upsertSourceDiscovery(resolving)
            dao.upsertAppSourceHead(
                AppSourceHeadEntity(
                    registeredAppId = registeredAppId,
                    latestDiscoveryId = discoveryId,
                    selectedConfigurationRevision = oldHead?.selectedConfigurationRevision,
                    updatedAt = startedAt,
                ),
            )
        }
        try {
            val preview = discoveryClient.preview(app.canonicalRepositoryUrl)
            check(preview.identity.providerRepositoryId == expectedProviderId) {
                "The provider returned a different repository identity; explicit re-binding is required."
            }
            check(preview.identity.provider == binding.provider && preview.identity.instance == binding.instance) {
                "The provider returned a different repository identity; explicit re-binding is required."
            }
            val finishedAt = Instant.now().toString()
            val discovery = preview.toDiscovery(registeredAppId, discoveryId, finishedAt).copy(startedAt = startedAt)
            database.withTransaction {
                val currentBinding = dao.getRepositoryBinding(registeredAppId)
                check(currentBinding?.providerRepositoryId == expectedProviderId) {
                    "Repository identity changed while discovery was running."
                }
                dao.upsertSourceDiscovery(discovery)
                dao.upsertGradleCandidates(preview.toCandidates(discoveryId))
                dao.upsertRegisteredApp(app.copy(updatedAt = finishedAt))
            }
        } catch (cancellation: CancellationException) {
            withContext(NonCancellable) {
                finishSourceDiscoveryAttempt(resolving, "CANCELLED", "CANCELLED")
            }
            throw cancellation
        } catch (failure: Throwable) {
            finishSourceDiscoveryAttempt(resolving, "FAILED", failure.sourceDiscoveryFailureReason())
            throw failure
        }
    }

    private suspend fun finishSourceDiscoveryAttempt(
        resolving: SourceDiscoveryEntity,
        state: String,
        reason: String,
    ) {
        val current = dao.getSourceDiscovery(resolving.discoveryId) ?: resolving
        dao.upsertSourceDiscovery(
            current.copy(
                state = state,
                reason = reason,
                finishedAt = Instant.now().toString(),
            ),
        )
    }

    private suspend fun verifyLegacyRepositoryAndRefresh(registeredAppId: String) {
        val app = dao.getRegisteredApp(registeredAppId)
            ?: throw IllegalArgumentException("Registered app was not found.")
        val preview = repositoryDiscoveryClient.preview(app.canonicalRepositoryUrl)
        val now = Instant.now().toString()
        val discoveryId = UUID.randomUUID().toString()
        val discovery = preview.toDiscovery(registeredAppId, discoveryId, now)
        database.withTransaction {
            val binding = dao.getRepositoryBinding(registeredAppId)
                ?: throw IllegalStateException("Legacy repository binding is missing.")
            check(binding.identityStatus == RepositoryIdentityStatus.LEGACY_UNRESOLVED.name) {
                "Legacy repository identity changed while verification was running."
            }
            val collision = dao.getRepositoryBinding(
                PROVIDER_GITHUB,
                GITHUB_INSTANCE,
                preview.identity.providerRepositoryId,
                PRIMARY_REGISTRATION_SLOT,
            )
            check(collision == null || collision.registeredAppId == registeredAppId) {
                "This repository is already bound to another primary management target."
            }
            dao.upsertRepositoryBinding(
                binding.copy(
                    provider = PROVIDER_GITHUB,
                    instance = GITHUB_INSTANCE,
                    providerRepositoryId = preview.identity.providerRepositoryId,
                    identityStatus = RepositoryIdentityStatus.VERIFIED.name,
                    verifiedAt = now,
                ),
            )
            dao.upsertSourceDiscovery(discovery)
            dao.upsertGradleCandidates(preview.toCandidates(discoveryId))
            val currentHead = dao.getAppSourceHead(registeredAppId)
            var selectedRevision = currentHead?.selectedConfigurationRevision
            if (selectedRevision == null) {
                val defaultBuildRoot = preview.discovery.candidates.singleOrNull()
                    ?.takeIf { preview.discovery.state == "COMPLETE" }
                    ?.buildRoot
                val validated = BuildConfigurationValidator.validate(
                    BuildConfigurationInput(buildRoot = defaultBuildRoot),
                )
                val configuration = AppBuildConfigurationEntity(
                    registeredAppId = registeredAppId,
                    revision = 1,
                    schemaVersion = 1,
                    canonicalJson = validated.canonicalJson,
                    contentSha256 = validated.contentSha256,
                    validationState = validated.validationState.name,
                    createdAt = now,
                )
                dao.upsertBuildConfiguration(configuration)
                selectedRevision = configuration.revision
            }
            dao.upsertAppSourceHead(
                AppSourceHeadEntity(registeredAppId, discoveryId, selectedRevision, now),
            )
            dao.upsertRegisteredApp(
                app.copy(
                    displayName = preview.identity.displayName,
                    repositoryUrl = preview.normalizedInputUrl,
                    canonicalRepositoryUrl = preview.normalizedInputUrl,
                    updatedAt = now,
                ),
            )
        }
    }

    suspend fun saveBuildConfiguration(
        registeredAppId: String,
        expectedRevision: Long?,
        input: BuildConfigurationInput,
    ): AppBuildConfigurationEntity {
        val validated = BuildConfigurationValidator.validate(input)
        val now = Instant.now().toString()
        return database.withTransaction {
            val app = dao.getRegisteredApp(registeredAppId)
                ?: throw IllegalArgumentException("Registered app was not found.")
            check(app.trackingState == AppTrackingState.ACTIVE.name) {
                "Resume tracking before changing the build configuration."
            }
            val head = dao.getAppSourceHead(registeredAppId)
                ?: AppSourceHeadEntity(registeredAppId, null, null, now)
            check(head.selectedConfigurationRevision == expectedRevision) {
                "Build configuration changed; reload before saving."
            }
            dao.getBuildConfigurationByHash(registeredAppId, validated.contentSha256)?.let { existing ->
                dao.upsertAppSourceHead(head.copy(selectedConfigurationRevision = existing.revision, updatedAt = now))
                return@withTransaction existing
            }
            val previousMaximum = dao.getLatestBuildConfigurationRevision(registeredAppId) ?: 0
            check(previousMaximum < Long.MAX_VALUE) { "Build configuration revision overflow." }
            val configuration = AppBuildConfigurationEntity(
                registeredAppId = registeredAppId,
                revision = previousMaximum + 1,
                schemaVersion = 1,
                canonicalJson = validated.canonicalJson,
                contentSha256 = validated.contentSha256,
                validationState = validated.validationState.name,
                createdAt = now,
            )
            dao.upsertBuildConfiguration(configuration)
            dao.upsertAppSourceHead(head.copy(selectedConfigurationRevision = configuration.revision, updatedAt = now))
            configuration
        }
    }

    suspend fun registerAndDownload(
        preview: ResolvedGitHubRelease,
        mode: ManagementMode,
        installationSource: InstallationSource,
        localBuildRiskConfirmed: Boolean,
    ): String {
        if (dao.getRegisteredAppsByCanonicalUrl(preview.repository.canonicalUrl).isNotEmpty()) {
            throw IllegalStateException("This GitHub repository is already registered.")
        }
        validateModeAndInstallationSource(mode, installationSource)
        if (installationSource == InstallationSource.LOCAL_BUILD) {
            require(localBuildRiskConfirmed) {
                "Local build installation requires explicit acknowledgement of signing and update risks."
            }
        }
        val selectedAsset = requireNotNull(preview.selectedAsset) {
            "This release has multiple eligible APKs. Register the repository and explicitly select one APK."
        }
        val registrationPreview = repositoryDiscoveryClient.preview(preview.repository.canonicalUrl)
        validateRegistrationPreview(registrationPreview)
        check(
            registrationPreview.identity.provider == PROVIDER_GITHUB &&
                registrationPreview.identity.instance == GITHUB_INSTANCE &&
                registrationPreview.normalizedInputUrl == preview.repository.canonicalUrl,
        ) { "The verified repository identity does not match the release preview." }
        val settings = currentSettings()
        val now = Instant.now().toString()
        val appId = UUID.randomUUID().toString()
        val observationHash = preview.legacyObservationSha256(preview.repository.canonicalUrl)
        val snapshotId = stableId("$appId/release-observation/$observationHash")
        val assetId = stableId("$snapshotId/asset/${selectedAsset.asset.id}")
        val app = RegisteredAppEntity(
            registeredAppId = appId,
            displayName = preview.repository.name,
            repositoryUrl = preview.repository.canonicalUrl,
            canonicalRepositoryUrl = preview.repository.canonicalUrl,
            provider = PROVIDER_GITHUB_RELEASES,
            managementMode = mode.name,
            installationSource = installationSource.name,
            releaseVariantPreference = settings.defaultReleaseVariantPreference,
            preferredAbi = settings.defaultPreferredAbi,
            maxApkSizeBytes = settings.defaultMaxApkSizeBytes,
            useGlobalReleaseVariant = true,
            useGlobalPreferredAbi = true,
            useGlobalMaxApkSize = true,
            releaseDiscoveryStatus = ReleaseDiscoveryStatus.AVAILABLE.name,
            releaseMetadataEtag = preview.responseEtag,
            lastReleaseCheckedAt = now,
            createdAt = now,
            updatedAt = now,
        )
        val snapshot = preview.toSnapshot(appId, snapshotId, observationHash, now, preview.selectedAsset).copy(
            observationSchemaVersion = 1,
            metadataObservationSha256 = null,
        )
        val asset = preview.toAssets(snapshotId, preview.selectedAsset).single()
        database.withTransaction {
            dao.upsertRegisteredApp(app)
            dao.upsertRepositoryBinding(
                AppRepositoryBindingEntity(
                    registeredAppId = appId,
                    provider = registrationPreview.identity.provider,
                    instance = registrationPreview.identity.instance,
                    providerRepositoryId = registrationPreview.identity.providerRepositoryId,
                    identityStatus = RepositoryIdentityStatus.VERIFIED.name,
                    registrationSlot = PRIMARY_REGISTRATION_SLOT,
                    verifiedAt = now,
                ),
            )
            dao.upsertReleaseSnapshot(snapshot)
            dao.upsertReleaseAsset(asset)
        }
        try {
            downloadReference(assetId)
            if (installationSource == InstallationSource.LOCAL_BUILD) {
                val inspected = dao.getReleaseAsset(assetId)
                    ?: error("The downloaded release asset was not persisted.")
                check(inspected.installedVersionCode == null) {
                    "Local build installation cannot be selected while the target package is installed."
                }
            }
        } catch (failure: Throwable) {
            val partCleanupFailure = runCatching {
                withContext(NonCancellable + Dispatchers.IO) { Files.deleteIfExists(partFile(assetId).toPath()) }
            }.exceptionOrNull()
            val retainedBytes = runCatching {
                withContext(NonCancellable + Dispatchers.IO) {
                    Files.exists(finalFile(assetId).toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS) ||
                        Files.exists(iconFile(assetId).toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS)
                }
            }.getOrDefault(true)
            val rollbackFailure = if (!retainedBytes) {
                runCatching { withContext(NonCancellable) { dao.deleteRegisteredApp(appId) } }.exceptionOrNull()
            } else {
                null
            }
            listOfNotNull(partCleanupFailure, rollbackFailure).forEach(failure::addSuppressed)
            throw failure
        }
        return appId
    }

    suspend fun refresh(registeredAppId: String) {
        var app = dao.getRegisteredApp(registeredAppId)
            ?: throw IllegalArgumentException("Registered app was not found.")
        check(app.trackingState == AppTrackingState.ACTIVE.name) {
            "Resume tracking before checking for new releases."
        }
        var binding = dao.getRepositoryBinding(registeredAppId)
        if (binding?.identityStatus == RepositoryIdentityStatus.VERIFIED.name) {
            refreshSourceDiscovery(registeredAppId)
        }
        if (binding?.identityStatus == RepositoryIdentityStatus.LEGACY_UNRESOLVED.name) {
            verifyLegacyRepositoryAndRefresh(registeredAppId)
        }
        if (binding?.identityStatus == RepositoryIdentityStatus.LEGACY_INVALID.name) {
            throw IllegalStateException("Legacy repository locator is invalid and requires explicit correction.")
        }
        app = dao.getRegisteredApp(registeredAppId)
            ?: throw IllegalArgumentException("Registered app was not found.")
        binding = dao.getRepositoryBinding(registeredAppId)
        val settings = currentSettings()
        dao.upsertRegisteredApp(
            app.copy(
                releaseDiscoveryStatus = ReleaseDiscoveryStatus.CHECKING.name,
                releaseDiscoveryErrorCode = null,
                releaseDiscoveryErrorMessage = null,
                updatedAt = Instant.now().toString(),
            ),
        )
        try {
            val activeBinding = binding
                ?: throw IllegalStateException("Repository identity is not available.")
            check(activeBinding.identityStatus == RepositoryIdentityStatus.VERIFIED.name) {
                "Repository identity must be verified before release refresh."
            }
            val activeReleaseClient = releaseClient(activeBinding.provider, activeBinding.instance)
                ?: throw IllegalStateException("The registered release provider is not supported.")
            val latest = try {
                activeReleaseClient.resolveLatestRelease(
                    repositoryUrl = app.canonicalRepositoryUrl,
                    previousEtag = app.releaseMetadataEtag,
                    preferredAbi = effectivePreferredAbi(app, settings),
                    preferredVariant = enumValueOrDefault(
                        if (app.useGlobalReleaseVariant) {
                            settings.defaultReleaseVariantPreference
                        } else {
                            app.releaseVariantPreference
                        },
                        ReleaseVariantPreference.RELEASE,
                    ),
                )
            } catch (notModified: Throwable) {
                if (!notModified.isProviderNotModified()) throw notModified
                val now = Instant.now().toString()
                val awaitingSelection = dao.getRegisteredAppRecord(registeredAppId)
                    ?.latestRelease
                    ?.let { release ->
                        release.snapshot.selectedProviderAssetId == null && release.assets.isNotEmpty()
                    } == true
                dao.upsertRegisteredApp(
                    app.copy(
                        releaseDiscoveryStatus = if (awaitingSelection) {
                            ReleaseDiscoveryStatus.AWAITING_ASSET_SELECTION.name
                        } else {
                            ReleaseDiscoveryStatus.AVAILABLE.name
                        },
                        releaseDiscoveryErrorCode = null,
                        releaseDiscoveryErrorMessage = null,
                        lastReleaseCheckedAt = now,
                        updatedAt = now,
                    ),
                )
                refreshInstalledStateForApp(registeredAppId)
                return
            }
            val now = Instant.now().toString()
            val providerRepositoryId = activeBinding.providerRepositoryId
                ?: throw IllegalStateException("Repository identity is not available.")
            val observationHash = latest.metadataObservationSha256(
                activeReleaseClient.providerName,
                activeReleaseClient.providerInstance,
                providerRepositoryId,
            )
            val existingSnapshot = dao.getLatestReleaseSnapshotByMetadataHash(registeredAppId, observationHash)
                ?: dao.getReleaseSnapshotByObservationHash(registeredAppId, observationHash)
            val snapshotId = existingSnapshot?.releaseSnapshotId
                ?: stableId("$registeredAppId/release-observation/$observationHash")
            val conditionSelection = SavedAssetSelection.select(latest.candidates, app.savedAssetSelectionJson)
            val discoveredAssets = latest.toAssets(snapshotId, conditionSelection)
            val existingAssets = existingSnapshot?.let {
                discoveredAssets.associate { asset ->
                    asset.providerAssetId to dao.getReleaseAsset(snapshotId, asset.providerAssetId)
                }
            }.orEmpty()
            val selectedProviderAssetId =
                existingSnapshot?.selectedProviderAssetId ?: conditionSelection?.asset?.id
            val selectedAsset = selectedProviderAssetId?.let { providerAssetId ->
                existingAssets[providerAssetId]
                    ?: discoveredAssets.singleOrNull { it.providerAssetId == providerAssetId }
                    ?: error("The immutable release observation lost its selected asset.")
            }
            val downloadSelectedAsset = selectedAsset
            val releaseStatus = if (selectedProviderAssetId == null) {
                ReleaseDiscoveryStatus.AWAITING_ASSET_SELECTION.name
            } else {
                ReleaseDiscoveryStatus.AVAILABLE.name
            }
            database.withTransaction {
                dao.upsertReleaseSnapshot(
                    existingSnapshot?.copy(
                        lastObservedAt = now,
                        selectedProviderAssetId = selectedProviderAssetId,
                    )
                        ?: latest.toSnapshot(
                            registeredAppId,
                            snapshotId,
                            observationHash,
                            now,
                            conditionSelection,
                        ),
                )
                discoveredAssets
                    .filter { existingAssets[it.providerAssetId] == null }
                    .forEach { asset -> dao.upsertReleaseAsset(asset) }
                if (existingSnapshot?.selectedProviderAssetId == null && conditionSelection != null) {
                    existingAssets[conditionSelection.asset.id]?.let { existingAsset ->
                        dao.upsertReleaseAsset(
                            existingAsset.copy(
                                selectionReason = conditionSelection.reason,
                            ),
                        )
                    }
                }
                dao.upsertRegisteredApp(
                    app.copy(
                        releaseDiscoveryStatus = releaseStatus,
                        releaseDiscoveryErrorCode = null,
                        releaseDiscoveryErrorMessage = null,
                        releaseMetadataEtag = latest.responseEtag,
                        lastReleaseCheckedAt = now,
                        updatedAt = now,
                    ),
                )
            }
            downloadSelectedAsset?.let { asset -> downloadReference(asset.releaseAssetId) }
            refreshInstalledStateForApp(registeredAppId)
        } catch (failure: Throwable) {
            val now = Instant.now().toString()
            dao.upsertRegisteredApp(
                app.copy(
                    releaseDiscoveryStatus = ReleaseDiscoveryStatus.FAILED.name,
                    releaseDiscoveryErrorCode = failure.errorCode(),
                    releaseDiscoveryErrorMessage = failure.message,
                    lastReleaseCheckedAt = now,
                    updatedAt = now,
                ),
            )
            throw failure
        }
    }

    suspend fun selectReleaseAsset(
        registeredAppId: String,
        releaseSnapshotId: String,
        providerAssetId: String,
        saveExactFilenameCondition: Boolean = false,
    ) {
        val record = dao.getRegisteredAppRecord(registeredAppId)
            ?: throw IllegalArgumentException("Registered app was not found.")
        check(record.app.trackingState == AppTrackingState.ACTIVE.name) {
            "Resume tracking before selecting a release asset."
        }
        check(record.latestRelease?.snapshot?.releaseSnapshotId == releaseSnapshotId) {
            "Only the latest release can receive an APK selection."
        }
        val asset = database.withTransaction {
            val snapshot = dao.getReleaseSnapshot(releaseSnapshotId)
                ?: throw IllegalArgumentException("Release snapshot was not found.")
            check(snapshot.registeredAppId == registeredAppId) {
                "Release snapshot does not belong to this registered app."
            }
            val previousSelection = snapshot.selectedProviderAssetId
            check(previousSelection == null || previousSelection == providerAssetId) {
                "A different APK was already selected for this immutable release observation."
            }
            val candidate = dao.getReleaseAsset(releaseSnapshotId, providerAssetId)
                ?: throw IllegalArgumentException("APK candidate was not found for this release.")
            check(
                candidate.selectionReason == AssetSelectionReason.MANUAL_SELECTION_REQUIRED.name ||
                    previousSelection == providerAssetId,
            ) {
                "This release asset was not offered for manual selection."
            }
            dao.upsertReleaseSnapshot(snapshot.copy(selectedProviderAssetId = providerAssetId))
            val selected = candidate.copy(
                selectionReason = AssetSelectionReason.MANUAL_RELEASE_ASSET.name,
            )
            dao.upsertReleaseAsset(selected)
            val app = dao.getRegisteredApp(registeredAppId)
                ?: throw IllegalArgumentException("Registered app was not found.")
            val now = Instant.now().toString()
            dao.upsertRegisteredApp(
                app.copy(
                    savedAssetSelectionJson = if (saveExactFilenameCondition) {
                        SavedAssetSelection.encode(SavedAssetSelectionCondition(exactFilename = candidate.assetName))
                    } else {
                        app.savedAssetSelectionJson
                    },
                    releaseDiscoveryStatus = ReleaseDiscoveryStatus.AVAILABLE.name,
                    releaseDiscoveryErrorCode = null,
                    releaseDiscoveryErrorMessage = null,
                    updatedAt = now,
                ),
            )
            selected
        }
        if (asset.downloadStatus != ReferenceDownloadStatus.VERIFIED.name) {
            downloadReference(asset.releaseAssetId)
        }
        refreshInstalledStateForApp(registeredAppId)
    }

    suspend fun clearSavedAssetSelection(registeredAppId: String) {
        val app = dao.getRegisteredApp(registeredAppId)
            ?: throw IllegalArgumentException("Registered app was not found.")
        dao.upsertRegisteredApp(app.copy(savedAssetSelectionJson = null, updatedAt = Instant.now().toString()))
    }

    suspend fun recoverInterruptedDownloads() = referenceDownloadMutex.withLock {
        withContext(Dispatchers.IO) {
            referenceDirectory.listFiles()
                ?.filter { it.isFile && it.name.startsWith(".download-") && it.name.endsWith(".part") }
                ?.forEach { Files.deleteIfExists(it.toPath()) }
        }
        dao.getInterruptedDownloads().forEach { asset ->
            partFile(asset.releaseAssetId).delete()
            dao.upsertReleaseAsset(
                asset.copy(
                    downloadStatus = ReferenceDownloadStatus.FAILED.name,
                    downloadErrorCode = "DOWNLOAD_INTERRUPTED",
                    downloadErrorMessage = "The previous reference APK download was interrupted.",
                ),
            )
        }
        dao.getVerifiedDownloads().forEach { asset ->
            val expectedApk = finalFile(asset.releaseAssetId).canonicalFile
            val storedApk = asset.localContentPath?.let(::File)?.canonicalFile
            if (
                !iconFile(asset.releaseAssetId).isFile &&
                storedApk == expectedApk &&
                expectedApk.isFile
            ) {
                inspector.inspect(expectedApk).iconPng?.let { icon ->
                    saveIcon(asset.releaseAssetId, icon)
                    dao.upsertReleaseAsset(asset)
                }
            }
        }
    }

    suspend fun updatePreferences(
        registeredAppId: String,
        update: AppSettingsUpdate,
    ) {
        val app = dao.getRegisteredApp(registeredAppId)
            ?: throw IllegalArgumentException("Registered app was not found.")
        check(app.trackingState == AppTrackingState.ACTIVE.name) {
            "Resume tracking before changing app settings."
        }
        validateModeAndInstallationSource(update.managementMode, update.installationSource)
        if (update.installationSource == InstallationSource.LOCAL_BUILD) {
            require(update.localBuildRiskConfirmed) {
                "Local build installation requires explicit acknowledgement of signing and update risks."
            }
        }
        if (app.installationSource != update.installationSource.name) {
            refreshInstalledStateForApp(registeredAppId)
            val current = dao.getRegisteredAppRecord(registeredAppId)
                ?: throw IllegalArgumentException("Registered app was not found.")
            check(canChangeInstallationSource(installedVersionCodeForSourceLock(current))) {
                "The installation source cannot be changed while the target package is installed."
            }
        }
        require(update.maxApkSizeBytes in SUPPORTED_APK_LIMITS) {
            "The APK size limit is not supported."
        }
        val settings = currentSettings()
        val previousVariant = effectiveReleaseVariant(app, settings)
        val previousAbi = effectivePreferredAbi(app, settings)
        val nextVariant = if (update.useGlobalReleaseVariant) {
            enumValueOrDefault(settings.defaultReleaseVariantPreference, ReleaseVariantPreference.RELEASE)
        } else {
            update.releaseVariantPreference
        }
        val nextAbi = if (update.useGlobalPreferredAbi) {
            enumValueOrDefault(settings.defaultPreferredAbi, PreferredAbi.ARM64_V8A)
        } else {
            update.preferredAbi
        }
        val selectionChanged = previousVariant != nextVariant || previousAbi != nextAbi
        val updatedApp = app.copy(
            managementMode = update.managementMode.name,
            installationSource = update.installationSource.name,
            releaseVariantPreference = update.releaseVariantPreference.name,
            preferredAbi = update.preferredAbi.name,
            maxApkSizeBytes = update.maxApkSizeBytes,
            useGlobalReleaseVariant = update.useGlobalReleaseVariant,
            useGlobalPreferredAbi = update.useGlobalPreferredAbi,
            useGlobalMaxApkSize = update.useGlobalMaxApkSize,
            releaseDiscoveryStatus = if (selectionChanged) {
                ReleaseDiscoveryStatus.NOT_CHECKED.name
            } else {
                app.releaseDiscoveryStatus
            },
            releaseMetadataEtag = null,
            updatedAt = Instant.now().toString(),
        )
        database.withTransaction {
            dao.upsertRegisteredApp(updatedApp)
            if (selectionChanged) {
                invalidateCurrentComparison(registeredAppId)
            }
        }
    }

    suspend fun updateGlobalSettings(settings: GlobalSettingsEntity) {
        require(settings.singletonId == GlobalSettingsEntity.SINGLETON_ID) {
            "Only the ReproDroid global settings row can be updated."
        }
        require(ThemeMode.entries.any { it.name == settings.themeMode }) { "The theme mode is invalid." }
        require(ManagementMode.entries.any { it.name == settings.defaultManagementMode }) {
            "The default management mode is invalid."
        }
        require(InstallationSource.entries.any { it.name == settings.defaultInstallationSource }) {
            "The default installation source is invalid."
        }
        require(ReleaseVariantPreference.entries.any { it.name == settings.defaultReleaseVariantPreference }) {
            "The default release variant is invalid."
        }
        require(PreferredAbi.entries.any { it.name == settings.defaultPreferredAbi }) {
            "The default ABI is invalid."
        }
        val source = enumValueOrDefault(
            settings.defaultInstallationSource,
            InstallationSource.OFFICIAL_RELEASE,
        )
        val mode = enumValueOrDefault(settings.defaultManagementMode, ManagementMode.VERIFICATION)
        validateModeAndInstallationSource(mode, source)
        require(settings.defaultMaxApkSizeBytes in SUPPORTED_APK_LIMITS) {
            "The default APK size limit is not supported."
        }
        require(settings.androidStorageBudgetBytes in MIN_ANDROID_STORAGE_BUDGET..MAX_ANDROID_STORAGE_BUDGET) {
            "The Android storage budget must be between 1 GiB and 64 GiB."
        }
        require(settings.storageWarningPercent in 50..95) {
            "The storage warning threshold must be between 50 and 95 percent."
        }
        val previous = currentSettings()
        val now = Instant.now().toString()
        val updated = settings.copy(updatedAt = now)
        val variantChanged = previous.defaultReleaseVariantPreference != updated.defaultReleaseVariantPreference
        val abiChanged = previous.defaultPreferredAbi != updated.defaultPreferredAbi
        database.withTransaction {
            dao.upsertGlobalSettings(updated)
            if (variantChanged || abiChanged) {
                dao.getRegisteredApps().forEach { app ->
                    if ((variantChanged && app.useGlobalReleaseVariant) || (abiChanged && app.useGlobalPreferredAbi)) {
                        dao.upsertRegisteredApp(
                            app.copy(
                                releaseMetadataEtag = null,
                                releaseDiscoveryStatus = ReleaseDiscoveryStatus.NOT_CHECKED.name,
                                updatedAt = now,
                            ),
                        )
                        invalidateCurrentComparison(app.registeredAppId)
                    }
                }
            }
        }
    }

    suspend fun refreshInstalledStateForApp(registeredAppId: String) {
        val record = dao.getRegisteredAppRecord(registeredAppId) ?: return
        val asset = record.latestRelease?.selectedAsset ?: return
        if (asset.downloadStatus != ReferenceDownloadStatus.VERIFIED.name) return
        if (!storageManager.isPresent("REFERENCE_APK", asset.releaseAssetId)) return
        val path = asset.localContentPath?.let(::File) ?: return
        val inspection = withContext(Dispatchers.IO) { inspector.inspect(path) }
        check(inspection.packageName == asset.packageName) {
            "The saved official APK package identity changed after verification."
        }
        val evaluatedAt = Instant.now().toString()
        dao.upsertReleaseAsset(
            asset.copy(
                signingCertificateSha256 = inspection.signingCertificateSha256.joinToString(","),
                currentSignerSha256 = inspection.currentSignerSha256.joinToString(","),
                existingInstallStatus = inspection.existingInstallStatus?.name,
                installedVersionName = inspection.installedVersionName,
                installedVersionCode = inspection.installedVersionCode,
                updateStatus = evaluateUpdateStatus(asset.versionCode, inspection.installedVersionCode).name,
                updateEvaluatedAt = evaluatedAt,
            ),
        )
    }

    suspend fun installManagedApp(registeredAppId: String, riskConfirmed: Boolean): String {
        val trackedApp = dao.getRegisteredApp(registeredAppId)
            ?: throw IllegalArgumentException("Registered app was not found.")
        check(trackedApp.trackingState == AppTrackingState.ACTIVE.name) {
            "Resume tracking before installing an app."
        }
        refreshInstalledStateForApp(registeredAppId)
        val record = dao.getRegisteredAppRecord(registeredAppId)
            ?: throw IllegalArgumentException("Registered app was not found.")
        val asset = record.latestRelease?.selectedAsset
            ?: throw IllegalStateException("No selected official APK is available.")
        val updateStatus = enumValueOrDefault(asset.updateStatus, UpdateStatus.UNKNOWN)
        check(updateStatus == UpdateStatus.NOT_INSTALLED || updateStatus == UpdateStatus.UPDATE_AVAILABLE) {
            "Only a new installation or a newer verified APK can start the installer."
        }
        val requiresRiskConfirmation =
            asset.existingInstallStatus == com.sanka1610.reprodroid.data.local.ExistingInstallStatus.SIGNER_MISMATCH.name ||
                (
                    record.app.managementMode == ManagementMode.VERIFICATION.name &&
                        record.trustLevel != com.sanka1610.reprodroid.data.local.TrustLevel.REPRODUCIBLE
                    )
        check(!requiresRiskConfirmation || riskConfirmed) {
            "The signer or reproducibility warning must be acknowledged before installation."
        }
        return when (enumValueOrDefault(record.app.installationSource, InstallationSource.OFFICIAL_RELEASE)) {
            InstallationSource.OFFICIAL_RELEASE -> {
                storageManager.requirePresent("REFERENCE_APK", asset.releaseAssetId)
                storageManager.markUsed("REFERENCE_APK", asset.releaseAssetId)
                releaseInstaller.install(registeredAppId, asset)
            }
            InstallationSource.LOCAL_BUILD -> {
                check(record.app.managementMode == ManagementMode.VERIFICATION.name) {
                    "Only verification mode can install a local build."
                }
                val comparison = record.currentComparison
                    ?: throw IllegalStateException("A current comparison run is required for local installation.")
                val artifactId = comparison.localArtifactId
                    ?: throw IllegalStateException("The comparison did not retain a local artifact.")
                val artifact = jobRepository.getArtifacts(comparison.runnerJobId)
                    .singleOrNull { it.artifactId == artifactId }
                    ?: throw IllegalStateException("The compared local artifact is unavailable.")
                check(
                    !artifact.signingCertificateSha256.isNullOrBlank() &&
                        !artifact.currentSignerSha256.isNullOrBlank(),
                ) {
                    "Local build installation is unavailable because the compared artifact is unsigned. " +
                        "Phase 2C does not generate or manage a ReproDroid signing key."
                }
                jobRepository.installArtifact(comparison.runnerJobId, artifactId)
            }
        }
    }

    suspend fun recordReleaseInstallStatus(
        attemptId: String,
        status: InstallAttemptStatus,
        packageInstallerStatus: Int,
        statusMessage: String?,
    ) {
        val attempt = dao.getReleaseInstallAttempt(attemptId) ?: return
        dao.upsertReleaseInstallAttempt(
            attempt.copy(
                status = status.name,
                packageInstallerStatus = packageInstallerStatus,
                statusMessage = statusMessage,
                updatedAt = Instant.now().toString(),
            ),
        )
        if (status in setOf(InstallAttemptStatus.SUCCEEDED, InstallAttemptStatus.FAILED, InstallAttemptStatus.CANCELLED)) {
            refreshInstalledStateForApp(attempt.registeredAppId)
        }
    }

    suspend fun recoverOrphanedReleaseInstallAttempts() {
        val now = Instant.now()
        val activeSessionIds = packageInstaller.mySessions.mapTo(mutableSetOf()) { it.sessionId }
        dao.getPendingReleaseInstallAttempts()
            .filter { attempt ->
                val stale = runCatching {
                    Instant.parse(attempt.updatedAt).plusSeconds(INSTALL_CALLBACK_GRACE_SECONDS) <= now
                }.getOrDefault(false)
                stale && attempt.packageInstallerSessionId !in activeSessionIds
            }
            .forEach { attempt ->
                dao.upsertReleaseInstallAttempt(
                    attempt.copy(
                        status = InstallAttemptStatus.FAILED.name,
                        packageInstallerStatus = PackageInstaller.STATUS_FAILURE,
                        statusMessage =
                            "The PackageInstaller session is no longer active, but no terminal callback was received.",
                        updatedAt = now.toString(),
                    ),
                )
                refreshInstalledStateForApp(attempt.registeredAppId)
            }
    }

    suspend fun startComparison(registeredAppId: String): String {
        val record = dao.getRegisteredAppRecord(registeredAppId)
            ?: throw IllegalArgumentException("Registered app was not found.")
        check(record.app.trackingState == AppTrackingState.ACTIVE.name) {
            "Resume tracking before starting a comparison."
        }
        check(record.app.managementMode == ManagementMode.VERIFICATION.name) {
            "Only apps in verification mode can start a reproducibility comparison."
        }
        check(record.app.releaseDiscoveryStatus == ReleaseDiscoveryStatus.AVAILABLE.name) {
            "Refresh release metadata after changing variant or ABI settings."
        }
        val release = record.latestRelease ?: error("No resolved release is available.")
        var asset = release.selectedAsset ?: error("No selected release APK is available.")
        check(asset.downloadStatus == ReferenceDownloadStatus.VERIFIED.name) {
            "The official reference APK must be verified before comparison."
        }
        storageManager.requirePresent("REFERENCE_APK", asset.releaseAssetId)
        val sourceHead = dao.getAppSourceHead(registeredAppId)
            ?: error("Select a complete build configuration before starting a generic comparison.")
        val configurationRevision = sourceHead.selectedConfigurationRevision
            ?: error("Select a complete build configuration before starting a generic comparison.")
        val storedConfiguration = dao.getBuildConfiguration(registeredAppId, configurationRevision)
            ?: error("The selected build configuration is missing.")
        check(storedConfiguration.validationState == com.sanka1610.reprodroid.data.local.BuildConfigurationValidationState.CONFIGURED.name) {
            "The selected build configuration is incomplete."
        }
        asset = restoreRetryableComparisonEligibility(asset)
        check(asset.comparisonEligibility != ComparisonEligibility.INCOMPARABLE.name) {
            asset.incomparableReason ?: "The selected APK is not eligible for comparison."
        }
        val configuration = BuildConfigurationValidator.decodeCanonical(
            storedConfiguration.canonicalJson,
            storedConfiguration.contentSha256,
        )
        val expectedVariant = requireNotNull(configuration.variant)
        val comparisonRunId = UUID.randomUUID().toString()
        val jobId = jobRepository.createGenericBuild(
            repositoryUrl = record.app.canonicalRepositoryUrl,
            commitSha = release.snapshot.resolvedCommitSha,
            comparisonId = comparisonRunId,
            attempt = GenericBuildAttempt.A,
            configurationRevision = configurationRevision,
            configurationSha256 = storedConfiguration.contentSha256,
            configurationCanonicalJson = storedConfiguration.canonicalJson,
            expectedArtifactFileName = asset.assetName,
        )
        val now = Instant.now().toString()
        val officialSha = requireNotNull(asset.computedRawSha256) { "The verified official APK SHA-256 is missing." }
        val officialSize = requireNotNull(asset.downloadedSizeBytes) { "The verified official APK size is missing." }
        val officialPackage = requireNotNull(asset.packageName) { "The official APK package identity is missing." }
        val officialVersionName = requireNotNull(asset.versionName) { "The official APK version name is missing." }
        val officialVersionCode = requireNotNull(asset.versionCode) { "The official APK version code is missing." }
        dao.upsertComparisonRun(
            ComparisonRunEntity(
                comparisonRunId = comparisonRunId,
                registeredAppId = registeredAppId,
                releaseSnapshotId = release.snapshot.releaseSnapshotId,
                referenceAssetId = asset.releaseAssetId,
                runnerJobId = jobId,
                expectedCommitSha = release.snapshot.resolvedCommitSha,
                expectedRecipeId = "generic-${storedConfiguration.contentSha256.take(16)}-a",
                expectedVariantName = expectedVariant,
                protocolVersion = REPEATED_BUILD_PROTOCOL_VERSION,
                createdAt = now,
                updatedAt = now,
                runnerContract = "generic-build@1+apk-comparison@1",
                buildConfigurationRevision = configurationRevision,
                buildConfigurationSha256 = storedConfiguration.contentSha256,
                officialIdentitySha256 = officialIdentitySha256(
                    officialSha, officialSize, officialPackage, officialVersionName, officialVersionCode,
                ),
                officialApkSha256 = officialSha,
                officialApkSizeBytes = officialSize,
                officialPackageName = officialPackage,
                officialVersionName = officialVersionName,
                officialVersionCode = officialVersionCode,
                selectedArtifactFileName = asset.assetName,
            ),
        )
        refreshComparison(comparisonRunId)
        return comparisonRunId
    }

    suspend fun confirmComparison(comparisonRunId: String) {
        refreshComparison(comparisonRunId)
        val run = dao.getComparisonRun(comparisonRunId)
            ?: throw IllegalArgumentException("Comparison run was not found.")
        if (run.status == ComparisonRunStatus.COMPLETED.name) return
        val repeatConfirmation = run.status == ComparisonRunStatus.AWAITING_REPEAT_CONFIRMATION.name
        check(repeatConfirmation || run.status == ComparisonRunStatus.AWAITING_CONFIRMATION.name) {
            "The comparison build is not awaiting confirmation."
        }
        val jobId = if (repeatConfirmation) {
            run.repeatRunnerJobId ?: error("The repeat Runner Job is missing from the comparison.")
        } else {
            run.runnerJobId
        }
        val job = jobRepository.getJob(jobId) ?: error("Runner Job is missing locally.")
        val resolvedCommit = job.resolvedCommitSha ?: error("Runner has not resolved the comparison commit.")
        check(resolvedCommit == run.expectedCommitSha) {
            "Runner resolved a different commit; build confirmation is blocked."
        }
        jobRepository.confirmRealBuild(jobId, resolvedCommit)
        dao.upsertComparisonRun(
            run.copy(
                status = if (repeatConfirmation) {
                    ComparisonRunStatus.REPEAT_BUILDING.name
                } else {
                    ComparisonRunStatus.BUILDING.name
                },
                updatedAt = Instant.now().toString(),
            ),
        )
    }

    suspend fun continueComparisonSourceScan(comparisonRunId: String) {
        refreshComparison(comparisonRunId)
        val run = dao.getComparisonRun(comparisonRunId)
            ?: throw IllegalArgumentException("Comparison run was not found.")
        if (run.status == ComparisonRunStatus.COMPLETED.name) return
        val repeatReview = run.status == ComparisonRunStatus.AWAITING_REPEAT_SCAN_REVIEW.name
        check(repeatReview || run.status == ComparisonRunStatus.AWAITING_SCAN_REVIEW.name) {
            "The comparison build is not awaiting source scan review."
        }
        val jobId = if (repeatReview) {
            run.repeatRunnerJobId ?: error("The repeat Runner Job is missing from the comparison.")
        } else {
            run.runnerJobId
        }
        val scan = jobRepository.getSourceScan(jobId)
            ?: error("Validated source scan evidence is not available locally.")
        jobRepository.continueSourceScan(jobId, scan.scan.resultSha256)
        dao.upsertComparisonRun(
            run.copy(
                status = if (repeatReview) {
                    ComparisonRunStatus.REPEAT_BUILDING.name
                } else {
                    ComparisonRunStatus.BUILDING.name
                },
                updatedAt = Instant.now().toString(),
            ),
        )
    }

    suspend fun refreshComparison(comparisonRunId: String) {
        try {
            val run = dao.getComparisonRun(comparisonRunId)
                ?: throw IllegalArgumentException("Comparison run was not found.")
            if (run.status == ComparisonRunStatus.COMPLETED.name) return
            if (run.repeatRunnerJobId != null) {
                refreshRepeatComparison(run)
                return
            }
            refreshPrimaryComparison(run)
        } finally {
            retentionCoordinator?.syncCurrentComparisonHolds()
        }
    }

    private suspend fun refreshPrimaryComparison(run: ComparisonRunEntity) {
        jobRepository.syncJob(run.runnerJobId)
        val job = jobRepository.getJob(run.runnerJobId)
            ?: return markIncomparable(run, "RUNNER_JOB_MISSING")
        val targetMismatch = comparisonTargetMismatch(run, job)
        if (targetMismatch != null && job.resolvedCommitSha != null) {
            markIncomparable(
                run,
                targetMismatch,
                job.resolvedCommitSha,
                job.effectiveRecipeId,
                job.effectiveVariantName,
                dependencyPinning = job.effectiveDependencyPinning,
            )
            return
        }
        when (JobState.valueOf(job.state)) {
            JobState.AWAITING_CONFIRMATION -> dao.upsertComparisonRun(
                run.copy(
                    runnerResolvedCommitSha = job.resolvedCommitSha,
                    runnerRecipeId = job.effectiveRecipeId,
                    runnerVariantName = job.effectiveVariantName,
                    runnerDependencyPinning = job.effectiveDependencyPinning,
                    status = ComparisonRunStatus.AWAITING_CONFIRMATION.name,
                    updatedAt = Instant.now().toString(),
                ),
            )
            JobState.AWAITING_SCAN_REVIEW -> dao.upsertComparisonRun(
                run.copy(
                    runnerResolvedCommitSha = job.resolvedCommitSha,
                    runnerRecipeId = job.effectiveRecipeId,
                    runnerVariantName = job.effectiveVariantName,
                    runnerDependencyPinning = job.effectiveDependencyPinning,
                    status = ComparisonRunStatus.AWAITING_SCAN_REVIEW.name,
                    updatedAt = Instant.now().toString(),
                ),
            )
            JobState.SUCCEEDED -> completeComparison(
                run,
                job.resolvedCommitSha,
                job.effectiveRecipeId,
                job.effectiveVariantName,
                job.effectiveDependencyPinning,
            )
            JobState.FAILED -> {
                if (
                    run.runnerContract.startsWith("generic-build@1") &&
                    job.errorCode == "SANDBOX_MEMORY_LIMIT_EXCEEDED"
                ) {
                    val awaitingRepeat = run.copy(
                        runnerResolvedCommitSha = job.resolvedCommitSha,
                        runnerRecipeId = job.effectiveRecipeId,
                        runnerVariantName = job.effectiveVariantName,
                        runnerDependencyPinning = job.effectiveDependencyPinning,
                        status = ComparisonRunStatus.RESOLVING_REPEAT_RUNNER.name,
                        outcome = ComparisonOutcome.INCOMPARABLE.name,
                        incomparableReason = "RUNNER_JOB_FAILED",
                        updatedAt = Instant.now().toString(),
                        completedAt = null,
                    )
                    dao.upsertComparisonRun(awaitingRepeat)
                    startRepeatComparison(awaitingRepeat)
                } else {
                    markIncomparable(
                        run,
                        "RUNNER_JOB_${job.state}",
                        job.resolvedCommitSha,
                        job.effectiveRecipeId,
                        job.effectiveVariantName,
                        dependencyPinning = job.effectiveDependencyPinning,
                    )
                }
            }
            JobState.CANCELLED, JobState.INTERRUPTED ->
                markIncomparable(
                    run,
                    "RUNNER_JOB_${job.state}",
                    job.resolvedCommitSha,
                    job.effectiveRecipeId,
                    job.effectiveVariantName,
                    dependencyPinning = job.effectiveDependencyPinning,
                )
            else -> dao.upsertComparisonRun(
                run.copy(
                    runnerResolvedCommitSha = job.resolvedCommitSha,
                    runnerRecipeId = job.effectiveRecipeId,
                    runnerVariantName = job.effectiveVariantName,
                    runnerDependencyPinning = job.effectiveDependencyPinning,
                    status = if (job.state == JobState.RESOLVING_SOURCE.name) {
                        ComparisonRunStatus.RESOLVING_RUNNER.name
                    } else {
                        ComparisonRunStatus.BUILDING.name
                    },
                    updatedAt = Instant.now().toString(),
                ),
            )
        }
    }

    private suspend fun refreshRepeatComparison(run: ComparisonRunEntity) {
        val repeatJobId = run.repeatRunnerJobId
            ?: return markRepeatIncomparable(run, "REPEAT_RUNNER_JOB_MISSING")
        jobRepository.syncJob(repeatJobId)
        val job = jobRepository.getJob(repeatJobId)
            ?: return markRepeatIncomparable(run, "REPEAT_RUNNER_JOB_MISSING")
        val targetMismatch = comparisonTargetMismatch(run, job)
        if (targetMismatch != null && job.resolvedCommitSha != null) {
            markRepeatIncomparable(
                run,
                "REPEAT_$targetMismatch",
                job.resolvedCommitSha,
                job.effectiveRecipeId,
                job.effectiveVariantName,
                dependencyPinning = job.effectiveDependencyPinning,
            )
            return
        }
        when (JobState.valueOf(job.state)) {
            JobState.AWAITING_CONFIRMATION -> dao.upsertComparisonRun(
                run.copy(
                    repeatRunnerResolvedCommitSha = job.resolvedCommitSha,
                    repeatRunnerRecipeId = job.effectiveRecipeId,
                    repeatRunnerVariantName = job.effectiveVariantName,
                    repeatRunnerDependencyPinning = job.effectiveDependencyPinning,
                    status = ComparisonRunStatus.AWAITING_REPEAT_CONFIRMATION.name,
                    updatedAt = Instant.now().toString(),
                ),
            )
            JobState.AWAITING_SCAN_REVIEW -> dao.upsertComparisonRun(
                run.copy(
                    repeatRunnerResolvedCommitSha = job.resolvedCommitSha,
                    repeatRunnerRecipeId = job.effectiveRecipeId,
                    repeatRunnerVariantName = job.effectiveVariantName,
                    repeatRunnerDependencyPinning = job.effectiveDependencyPinning,
                    status = ComparisonRunStatus.AWAITING_REPEAT_SCAN_REVIEW.name,
                    updatedAt = Instant.now().toString(),
                ),
            )
            JobState.SUCCEEDED -> completeRepeatComparison(
                run,
                job.resolvedCommitSha,
                job.effectiveRecipeId,
                job.effectiveVariantName,
                job.effectiveDependencyPinning,
            )
            JobState.FAILED, JobState.CANCELLED, JobState.INTERRUPTED -> {
                val runnerComparisonId = if (
                    job.state == JobState.FAILED.name &&
                    run.runnerContract.startsWith("generic-build@1") &&
                    run.runnerComparisonId == null
                ) {
                    recordGenericFailureComparison(run, job.jobId)
                } else {
                    null
                }
                markRepeatIncomparable(
                    run.copy(runnerComparisonId = runnerComparisonId ?: run.runnerComparisonId),
                    "RUNNER_JOB_${job.state}_REPEAT",
                    job.resolvedCommitSha,
                    job.effectiveRecipeId,
                    job.effectiveVariantName,
                    dependencyPinning = job.effectiveDependencyPinning,
                )
            }
            else -> dao.upsertComparisonRun(
                run.copy(
                    repeatRunnerResolvedCommitSha = job.resolvedCommitSha,
                    repeatRunnerRecipeId = job.effectiveRecipeId,
                    repeatRunnerVariantName = job.effectiveVariantName,
                    repeatRunnerDependencyPinning = job.effectiveDependencyPinning,
                    status = if (job.state == JobState.RESOLVING_SOURCE.name) {
                        ComparisonRunStatus.RESOLVING_REPEAT_RUNNER.name
                    } else {
                        ComparisonRunStatus.REPEAT_BUILDING.name
                    },
                    updatedAt = Instant.now().toString(),
                ),
            )
        }
    }

    private suspend fun completeComparison(
        originalRun: ComparisonRunEntity,
        resolvedCommitSha: String?,
        recipeId: String?,
        variantName: String?,
        dependencyPinning: String,
    ) {
        val run = originalRun.copy(runnerDependencyPinning = dependencyPinning)
        val reference = dao.getReleaseAsset(run.referenceAssetId)
            ?: return markIncomparable(run, "REFERENCE_ASSET_MISSING", resolvedCommitSha, recipeId, variantName)
        val artifacts = jobRepository.getArtifacts(run.runnerJobId)
        if (artifacts.size != 1) {
            markIncomparable(run, "LOCAL_ARTIFACT_COUNT_INVALID", resolvedCommitSha, recipeId, variantName)
            return
        }
        var local = artifacts.single()
        if (local.downloadStatus != ArtifactDownloadStatus.VERIFIED.name) {
            jobRepository.downloadArtifactForComparison(run.runnerJobId, local.artifactId)
            local = jobRepository.getArtifacts(run.runnerJobId).singleOrNull()
                ?: return markIncomparable(run, "LOCAL_ARTIFACT_MISSING", resolvedCommitSha, recipeId, variantName)
        }
        val identityMismatch = comparisonIdentityMismatch(reference, local)
        if (identityMismatch != null) {
            markIncomparable(run, identityMismatch, resolvedCommitSha, recipeId, variantName, local.artifactId)
            return
        }
        val referencePath = reference.localContentPath?.let(::File)
        val localPath = local.localContentPath?.let { File(context.filesDir, it) }
        val referenceSize = reference.downloadedSizeBytes
        val referenceSha = reference.computedRawSha256
        val localSize = local.downloadedSizeBytes
        val localSha = local.downloadedSha256
        if (
            referencePath == null || localPath == null || referenceSize == null || referenceSha == null ||
            localSize == null || localSha == null
        ) {
            markIncomparable(run, "VERIFIED_APK_CONTENT_MISSING", resolvedCommitSha, recipeId, variantName, local.artifactId)
            return
        }
        dao.upsertComparisonRun(
            run.copy(
                localArtifactId = local.artifactId,
                runnerResolvedCommitSha = resolvedCommitSha,
                runnerRecipeId = recipeId,
                runnerVariantName = variantName,
                status = ComparisonRunStatus.COMPARING.name,
                updatedAt = Instant.now().toString(),
            ),
        )
        val comparison = try {
            withContext(Dispatchers.IO) {
                comparator.compare(
                    referenceApk = referencePath,
                    referenceRoot = referenceDirectory,
                    expectedReference = ExpectedApkFile(referenceSize, referenceSha),
                    localApk = localPath,
                    localRoot = File(context.filesDir, "apks"),
                    expectedLocal = ExpectedApkFile(localSize, localSha),
                )
            }
        } catch (failure: ApkComparisonException) {
            markIncomparable(run, failure.code, resolvedCommitSha, recipeId, variantName, local.artifactId)
            return
        }
        val advancedEvidence = withContext(Dispatchers.IO) {
            advancedComparator.compare(
                leftApk = referencePath,
                leftRoot = referenceDirectory,
                expectedLeft = ExpectedApkFile(referenceSize, referenceSha),
                rightApk = localPath,
                rightRoot = File(context.filesDir, "apks"),
                expectedRight = ExpectedApkFile(localSize, localSha),
            )
        }
        val now = Instant.now().toString()
        val outcome = if (comparison.isMatch) ComparisonOutcome.MATCH else ComparisonOutcome.DIFFERENT
        val continueWithRepeat = run.protocolVersion >= REPEATED_BUILD_PROTOCOL_VERSION
        val primaryResult = run.copy(
            localArtifactId = local.artifactId,
            runnerResolvedCommitSha = resolvedCommitSha,
            runnerRecipeId = recipeId,
            runnerVariantName = variantName,
            status = if (continueWithRepeat) {
                ComparisonRunStatus.COMPARING.name
            } else {
                ComparisonRunStatus.COMPLETED.name
            },
            outcome = outcome.name,
            incomparableReason = null,
            updatedAt = now,
            completedAt = if (continueWithRepeat) null else now,
        )
        database.withTransaction {
            dao.deleteComparisonEntries(run.comparisonRunId)
            dao.upsertComparisonEntries(
                comparison.entries.map { entry ->
                    ComparisonEntryEntity(
                        comparisonRunId = run.comparisonRunId,
                        entryName = entry.entryName,
                        result = entry.result,
                        referenceSizeBytes = entry.referenceSizeBytes,
                        localSizeBytes = entry.localSizeBytes,
                        referenceSha256 = entry.referenceSha256,
                        localSha256 = entry.localSha256,
                    )
                },
            )
            persistAdvancedEvidence(run, AdvancedComparisonAxis.OFFICIAL_PRIMARY, advancedEvidence)
            dao.upsertComparisonRun(
                primaryResult,
            )
            dao.upsertReleaseAsset(
                reference.copy(
                    comparisonEligibility = ComparisonEligibility.READY_FOR_COMPARISON.name,
                    incomparableReason = null,
                ),
            )
        }
        if (continueWithRepeat) startRepeatComparison(primaryResult)
    }

    private suspend fun startRepeatComparison(run: ComparisonRunEntity) {
        val snapshot = dao.getReleaseSnapshot(run.releaseSnapshotId)
            ?: return markRepeatIncomparable(run, "REPEAT_RELEASE_SNAPSHOT_MISSING")
        val app = dao.getRegisteredApp(run.registeredAppId)
            ?: return markRepeatIncomparable(run, "REPEAT_REGISTERED_APP_MISSING")
        val configurationRevision = run.buildConfigurationRevision
            ?: return markRepeatIncomparable(run, "REPEAT_CONFIGURATION_REVISION_MISSING")
        val configurationHash = run.buildConfigurationSha256
            ?: return markRepeatIncomparable(run, "REPEAT_CONFIGURATION_HASH_MISSING")
        val configuration = dao.getBuildConfiguration(run.registeredAppId, configurationRevision)
            ?: return markRepeatIncomparable(run, "REPEAT_CONFIGURATION_MISSING")
        if (configuration.contentSha256 != configurationHash) {
            return markRepeatIncomparable(run, "REPEAT_CONFIGURATION_CHANGED")
        }
        val repeatJobId = try {
            jobRepository.createGenericBuild(
                repositoryUrl = app.canonicalRepositoryUrl,
                commitSha = snapshot.resolvedCommitSha,
                comparisonId = run.comparisonRunId,
                attempt = GenericBuildAttempt.B,
                configurationRevision = configurationRevision,
                configurationSha256 = configurationHash,
                configurationCanonicalJson = configuration.canonicalJson,
                expectedArtifactFileName = run.selectedArtifactFileName
                    ?: return markRepeatIncomparable(run, "REPEAT_ARTIFACT_SELECTION_MISSING"),
            )
        } catch (failure: Throwable) {
            if (failure is CancellationException) throw failure
            return markRepeatIncomparable(run, "REPEAT_JOB_CREATE_FAILED")
        }
        dao.upsertComparisonRun(
            run.copy(
                repeatRunnerJobId = repeatJobId,
                status = ComparisonRunStatus.RESOLVING_REPEAT_RUNNER.name,
                updatedAt = Instant.now().toString(),
            ),
        )
        refreshComparison(run.comparisonRunId)
    }

    private suspend fun completeRepeatComparison(
        originalRun: ComparisonRunEntity,
        resolvedCommitSha: String?,
        recipeId: String?,
        variantName: String?,
        dependencyPinning: String,
    ) {
        val run = originalRun.copy(repeatRunnerDependencyPinning = dependencyPinning)
        val reference = dao.getReleaseAsset(run.referenceAssetId)
            ?: return markRepeatIncomparable(run, "REPEAT_REFERENCE_ASSET_MISSING", resolvedCommitSha, recipeId, variantName)
        val repeatJobId = run.repeatRunnerJobId
            ?: return markRepeatIncomparable(run, "REPEAT_RUNNER_JOB_MISSING", resolvedCommitSha, recipeId, variantName)
        val primary = if (run.outcome == ComparisonOutcome.INCOMPARABLE.name) {
            null
        } else {
            val primaryArtifactId = run.localArtifactId
                ?: return markRepeatIncomparable(run, "PRIMARY_LOCAL_ARTIFACT_MISSING", resolvedCommitSha, recipeId, variantName)
            jobRepository.getArtifacts(run.runnerJobId).singleOrNull { it.artifactId == primaryArtifactId }
                ?: return markRepeatIncomparable(run, "PRIMARY_LOCAL_ARTIFACT_MISSING", resolvedCommitSha, recipeId, variantName)
        }
        var repeat = jobRepository.getArtifacts(repeatJobId).singleOrNull()
            ?: return markRepeatIncomparable(run, "REPEAT_LOCAL_ARTIFACT_COUNT_INVALID", resolvedCommitSha, recipeId, variantName)
        if (repeat.downloadStatus != ArtifactDownloadStatus.VERIFIED.name) {
            jobRepository.downloadArtifactForComparison(repeatJobId, repeat.artifactId)
            repeat = jobRepository.getArtifacts(repeatJobId).singleOrNull()
                ?: return markRepeatIncomparable(run, "REPEAT_LOCAL_ARTIFACT_MISSING", resolvedCommitSha, recipeId, variantName)
        }
        val identityMismatch = comparisonIdentityMismatch(reference, repeat)
        if (identityMismatch != null) {
            markRepeatIncomparable(
                run,
                "REPEAT_$identityMismatch",
                resolvedCommitSha,
                recipeId,
                variantName,
                repeat.artifactId,
            )
            return
        }
        if (primary == null) {
            completeRepeatAfterPrimaryFailure(
                run = run,
                resolvedCommitSha = resolvedCommitSha,
                recipeId = recipeId,
                variantName = variantName,
                reference = reference,
                repeat = repeat,
            )
            return
        }
        val referenceInput = referenceComparisonInput(reference)
            ?: return markRepeatIncomparable(run, "REPEAT_VERIFIED_REFERENCE_CONTENT_MISSING", resolvedCommitSha, recipeId, variantName, repeat.artifactId)
        val primaryInput = localComparisonInput(primary)
            ?: return markRepeatIncomparable(run, "PRIMARY_VERIFIED_APK_CONTENT_MISSING", resolvedCommitSha, recipeId, variantName, repeat.artifactId)
        val repeatInput = localComparisonInput(repeat)
            ?: return markRepeatIncomparable(run, "REPEAT_VERIFIED_APK_CONTENT_MISSING", resolvedCommitSha, recipeId, variantName, repeat.artifactId)
        dao.upsertComparisonRun(
            run.copy(
                repeatLocalArtifactId = repeat.artifactId,
                repeatRunnerResolvedCommitSha = resolvedCommitSha,
                repeatRunnerRecipeId = recipeId,
                repeatRunnerVariantName = variantName,
                status = ComparisonRunStatus.COMPARING_REPEAT.name,
                updatedAt = Instant.now().toString(),
            ),
        )
        val officialRepeat = try {
            withContext(Dispatchers.IO) {
                comparator.compare(
                    referenceApk = referenceInput.file,
                    referenceRoot = referenceDirectory,
                    expectedReference = referenceInput.expected,
                    localApk = repeatInput.file,
                    localRoot = File(context.filesDir, "apks"),
                    expectedLocal = repeatInput.expected,
                )
            }
        } catch (failure: ApkComparisonException) {
            return markRepeatIncomparable(
                run,
                "OFFICIAL_REPEAT_${failure.code}",
                resolvedCommitSha,
                recipeId,
                variantName,
                repeat.artifactId,
            )
        }
        val localRepeatability = try {
            withContext(Dispatchers.IO) {
                comparator.compare(
                    referenceApk = primaryInput.file,
                    referenceRoot = File(context.filesDir, "apks"),
                    expectedReference = primaryInput.expected,
                    localApk = repeatInput.file,
                    localRoot = File(context.filesDir, "apks"),
                    expectedLocal = repeatInput.expected,
                )
            }
        } catch (failure: ApkComparisonException) {
            return markRepeatIncomparable(
                run,
                "LOCAL_REPEATABILITY_${failure.code}",
                resolvedCommitSha,
                recipeId,
                variantName,
                repeat.artifactId,
            )
        }
        val officialRepeatEvidence = withContext(Dispatchers.IO) {
            advancedComparator.compare(
                leftApk = referenceInput.file,
                leftRoot = referenceDirectory,
                expectedLeft = referenceInput.expected,
                rightApk = repeatInput.file,
                rightRoot = File(context.filesDir, "apks"),
                expectedRight = repeatInput.expected,
            )
        }
        val repeatabilityEvidence = withContext(Dispatchers.IO) {
            advancedComparator.compare(
                leftApk = primaryInput.file,
                leftRoot = File(context.filesDir, "apks"),
                expectedLeft = primaryInput.expected,
                rightApk = repeatInput.file,
                rightRoot = File(context.filesDir, "apks"),
                expectedRight = repeatInput.expected,
            )
        }
        val officialOutcome = comparisonOutcome(officialRepeat.isMatch)
        val repeatabilityOutcome = comparisonOutcome(localRepeatability.isMatch)
        val runnerComparisonId = if (run.runnerContract.startsWith("generic-build@1")) {
            val configurationHash = requireNotNull(run.buildConfigurationSha256)
            val officialIdentity = OfficialApkIdentity(
                sha256 = requireNotNull(run.officialApkSha256),
                sizeBytes = requireNotNull(run.officialApkSizeBytes),
                packageName = requireNotNull(run.officialPackageName),
                versionName = requireNotNull(run.officialVersionName),
                versionCode = requireNotNull(run.officialVersionCode),
            )
            val trustedPair = !reference.signingCertificateSha256.isNullOrBlank() &&
                reference.signingCertificateSha256 == primary.signingCertificateSha256 &&
                reference.signingCertificateSha256 == repeat.signingCertificateSha256
            val installEligible = trustedPair && reference.existingInstallStatus != com.sanka1610.reprodroid.data.local.ExistingInstallStatus.SIGNER_MISMATCH.name
            jobRepository.recordGenericComparison(
                CreateGenericComparisonRequest(
                    comparisonId = run.comparisonRunId,
                    configurationSha256 = configurationHash,
                    officialIdentity = officialIdentity,
                    buildAJobId = run.runnerJobId,
                    buildBJobId = repeatJobId,
                    officialVsA = run.outcome.toRawComparisonResult(),
                    officialVsB = officialOutcome.toRawComparisonResult(),
                    buildAVsB = repeatabilityOutcome.toRawComparisonResult(),
                    trustEligible = trustedPair,
                    installEligible = installEligible,
                ),
            ).comparisonId
        } else null
        val now = Instant.now().toString()
        database.withTransaction {
            dao.deleteAdvancedComparisonEntries(run.comparisonRunId)
            dao.upsertAdvancedComparisonEntries(
                advancedEntries(run.comparisonRunId, AdvancedComparisonAxis.OFFICIAL_REPEAT, officialRepeat) +
                    advancedEntries(
                        run.comparisonRunId,
                        AdvancedComparisonAxis.LOCAL_REPEATABILITY,
                        localRepeatability,
                    ),
            )
            persistAdvancedEvidence(run, AdvancedComparisonAxis.OFFICIAL_REPEAT, officialRepeatEvidence)
            persistAdvancedEvidence(run, AdvancedComparisonAxis.LOCAL_REPEATABILITY, repeatabilityEvidence)
            dao.upsertComparisonRun(
                run.copy(
                    repeatLocalArtifactId = repeat.artifactId,
                    repeatRunnerResolvedCommitSha = resolvedCommitSha,
                    repeatRunnerRecipeId = recipeId,
                    repeatRunnerVariantName = variantName,
                    repeatOfficialOutcome = officialOutcome.name,
                    repeatabilityOutcome = repeatabilityOutcome.name,
                    repeatIncomparableReason = null,
                    status = ComparisonRunStatus.COMPLETED.name,
                    updatedAt = now,
                    completedAt = now,
                    runnerComparisonId = runnerComparisonId,
                ),
            )
        }
    }

    private suspend fun completeRepeatAfterPrimaryFailure(
        run: ComparisonRunEntity,
        resolvedCommitSha: String?,
        recipeId: String?,
        variantName: String?,
        reference: ReleaseAssetEntity,
        repeat: ArtifactEntity,
    ) {
        val referenceInput = referenceComparisonInput(reference)
            ?: return markRepeatIncomparable(
                run,
                "REPEAT_VERIFIED_REFERENCE_CONTENT_MISSING",
                resolvedCommitSha,
                recipeId,
                variantName,
                repeat.artifactId,
            )
        val repeatInput = localComparisonInput(repeat)
            ?: return markRepeatIncomparable(
                run,
                "REPEAT_VERIFIED_APK_CONTENT_MISSING",
                resolvedCommitSha,
                recipeId,
                variantName,
                repeat.artifactId,
            )
        val officialRepeat = try {
            withContext(Dispatchers.IO) {
                comparator.compare(
                    referenceApk = referenceInput.file,
                    referenceRoot = referenceDirectory,
                    expectedReference = referenceInput.expected,
                    localApk = repeatInput.file,
                    localRoot = File(context.filesDir, "apks"),
                    expectedLocal = repeatInput.expected,
                )
            }
        } catch (failure: ApkComparisonException) {
            return markRepeatIncomparable(
                run,
                "OFFICIAL_REPEAT_${failure.code}",
                resolvedCommitSha,
                recipeId,
                variantName,
                repeat.artifactId,
            )
        }
        val officialRepeatEvidence = withContext(Dispatchers.IO) {
            advancedComparator.compare(
                leftApk = referenceInput.file,
                leftRoot = referenceDirectory,
                expectedLeft = referenceInput.expected,
                rightApk = repeatInput.file,
                rightRoot = File(context.filesDir, "apks"),
                expectedRight = repeatInput.expected,
            )
        }
        val officialOutcome = comparisonOutcome(officialRepeat.isMatch)
        val runnerComparisonId = if (run.runnerContract.startsWith("generic-build@1")) {
            val officialIdentity = OfficialApkIdentity(
                sha256 = requireNotNull(run.officialApkSha256),
                sizeBytes = requireNotNull(run.officialApkSizeBytes),
                packageName = requireNotNull(run.officialPackageName),
                versionName = requireNotNull(run.officialVersionName),
                versionCode = requireNotNull(run.officialVersionCode),
            )
            jobRepository.recordGenericComparison(
                CreateGenericComparisonRequest(
                    comparisonId = run.comparisonRunId,
                    configurationSha256 = requireNotNull(run.buildConfigurationSha256),
                    officialIdentity = officialIdentity,
                    buildAJobId = run.runnerJobId,
                    buildBJobId = requireNotNull(run.repeatRunnerJobId),
                    officialVsA = RawComparisonResult.INCOMPARABLE,
                    officialVsB = officialOutcome.toRawComparisonResult(),
                    buildAVsB = RawComparisonResult.INCOMPARABLE,
                    trustEligible = false,
                    installEligible = false,
                ),
            ).comparisonId
        } else {
            null
        }
        val now = Instant.now().toString()
        database.withTransaction {
            persistAdvancedEvidence(run, AdvancedComparisonAxis.OFFICIAL_REPEAT, officialRepeatEvidence)
            dao.upsertComparisonRun(
                run.copy(
                    repeatLocalArtifactId = repeat.artifactId,
                    repeatRunnerResolvedCommitSha = resolvedCommitSha,
                    repeatRunnerRecipeId = recipeId,
                    repeatRunnerVariantName = variantName,
                    repeatOfficialOutcome = officialOutcome.name,
                    repeatabilityOutcome = ComparisonOutcome.INCOMPARABLE.name,
                    repeatIncomparableReason = "PRIMARY_BUILD_FAILED",
                    status = ComparisonRunStatus.COMPLETED.name,
                    updatedAt = now,
                    completedAt = now,
                    runnerComparisonId = runnerComparisonId,
                ),
            )
        }
    }

    private suspend fun recordGenericFailureComparison(
        run: ComparisonRunEntity,
        repeatJobId: String,
    ): String? {
        if (!run.runnerContract.startsWith("generic-build@1") || run.runnerComparisonId != null) return null
        val officialIdentity = OfficialApkIdentity(
            sha256 = requireNotNull(run.officialApkSha256),
            sizeBytes = requireNotNull(run.officialApkSizeBytes),
            packageName = requireNotNull(run.officialPackageName),
            versionName = requireNotNull(run.officialVersionName),
            versionCode = requireNotNull(run.officialVersionCode),
        )
        return jobRepository.recordGenericComparison(
            CreateGenericComparisonRequest(
                comparisonId = run.comparisonRunId,
                configurationSha256 = requireNotNull(run.buildConfigurationSha256),
                officialIdentity = officialIdentity,
                buildAJobId = run.runnerJobId,
                buildBJobId = repeatJobId,
                officialVsA = run.outcome.toRawComparisonResult(),
                officialVsB = RawComparisonResult.INCOMPARABLE,
                buildAVsB = RawComparisonResult.INCOMPARABLE,
                trustEligible = false,
                installEligible = false,
            ),
        ).comparisonId
    }

    private fun comparisonIdentityMismatch(reference: ReleaseAssetEntity, local: ArtifactEntity): String? = when {
        reference.packageName.isNullOrBlank() || local.packageName.isBlank() -> "PACKAGE_METADATA_MISSING"
        reference.packageName != local.packageName -> "PACKAGE_MISMATCH"
        reference.versionName.isNullOrBlank() || local.versionName.isBlank() -> "VERSION_NAME_MISSING"
        reference.versionName != local.versionName -> "VERSION_NAME_MISMATCH"
        reference.versionCode == null || local.versionCode <= 0 -> "VERSION_CODE_MISSING"
        reference.versionCode != local.versionCode -> "VERSION_CODE_MISMATCH"
        else -> null
    }

    private fun referenceComparisonInput(reference: ReleaseAssetEntity): ComparisonInput? {
        val path = reference.localContentPath?.let(::File) ?: return null
        val size = reference.downloadedSizeBytes ?: return null
        val sha = reference.computedRawSha256 ?: return null
        return ComparisonInput(path, ExpectedApkFile(size, sha))
    }

    private fun localComparisonInput(artifact: ArtifactEntity): ComparisonInput? {
        val path = artifact.localContentPath?.let { File(context.filesDir, it) } ?: return null
        val size = artifact.downloadedSizeBytes ?: return null
        val sha = artifact.downloadedSha256 ?: return null
        return ComparisonInput(path, ExpectedApkFile(size, sha))
    }

    private fun advancedEntries(
        comparisonRunId: String,
        axis: AdvancedComparisonAxis,
        comparison: com.sanka1610.reprodroid.data.artifact.ApkContentComparison,
    ): List<AdvancedComparisonEntryEntity> = comparison.entries.map { entry ->
        AdvancedComparisonEntryEntity(
            comparisonRunId = comparisonRunId,
            axis = axis.name,
            entryName = entry.entryName,
            result = entry.result,
            leftSizeBytes = entry.referenceSizeBytes,
            rightSizeBytes = entry.localSizeBytes,
            leftSha256 = entry.referenceSha256,
            rightSha256 = entry.localSha256,
        )
    }

    private fun comparisonOutcome(isMatch: Boolean): ComparisonOutcome =
        if (isMatch) ComparisonOutcome.MATCH else ComparisonOutcome.DIFFERENT

    private fun ComparisonOutcome.toRawComparisonResult(): RawComparisonResult = when (this) {
        ComparisonOutcome.MATCH -> RawComparisonResult.MATCH
        ComparisonOutcome.DIFFERENT -> RawComparisonResult.DIFFERENT
        ComparisonOutcome.INCOMPARABLE, ComparisonOutcome.NOT_EVALUATED -> RawComparisonResult.INCOMPARABLE
    }

    private fun String.toRawComparisonResult(): RawComparisonResult =
        runCatching { ComparisonOutcome.valueOf(this).toRawComparisonResult() }.getOrDefault(RawComparisonResult.INCOMPARABLE)

    private fun officialIdentitySha256(
        apkSha256: String,
        sizeBytes: Long,
        packageName: String,
        versionName: String,
        versionCode: Long,
    ): String {
        val value = listOf(apkSha256, sizeBytes.toString(), packageName, versionName, versionCode.toString()).joinToString("\u0000")
        return MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private suspend fun persistAdvancedEvidence(
        run: ComparisonRunEntity,
        axis: AdvancedComparisonAxis,
        comparison: AdvancedApkComparison,
    ) {
        dao.deleteApkEntryEvidence(run.comparisonRunId, axis.name)
        dao.deleteSemanticDifferenceEvidence(run.comparisonRunId, axis.name)
        if (comparison.entries.isNotEmpty()) {
            dao.upsertApkEntryEvidence(
                comparison.entries.map { entry ->
                    ApkEntryEvidenceEntity(
                        comparisonRunId = run.comparisonRunId,
                        axis = axis.name,
                        entryName = entry.entryName,
                        category = entry.category.name,
                        result = entry.result,
                        leftSizeBytes = entry.left?.sizeBytes,
                        rightSizeBytes = entry.right?.sizeBytes,
                        leftCrc32 = entry.left?.crc32,
                        rightCrc32 = entry.right?.crc32,
                        leftCompressionMethod = entry.left?.compressionMethod,
                        rightCompressionMethod = entry.right?.compressionMethod,
                        leftUncompressedSha256 = entry.left?.uncompressedSha256,
                        rightUncompressedSha256 = entry.right?.uncompressedSha256,
                        archiveMetadataChanged = entry.archiveMetadataChanged,
                    )
                },
            )
        }
        if (comparison.semanticDifferences.isNotEmpty()) {
            dao.upsertSemanticDifferenceEvidence(
                comparison.semanticDifferences.map { difference ->
                    SemanticDifferenceEvidenceEntity(
                        comparisonRunId = run.comparisonRunId,
                        registeredAppId = run.registeredAppId,
                        axis = axis.name,
                        component = difference.component,
                        stableKey = difference.stableKey,
                        result = difference.result,
                        leftSha256 = difference.leftSha256,
                        rightSha256 = difference.rightSha256,
                    )
                },
            )
        }
        dao.upsertAdvancedComparisonSummary(
            AdvancedComparisonSummaryEntity(
                comparisonRunId = run.comparisonRunId,
                registeredAppId = run.registeredAppId,
                axis = axis.name,
                inventoryOutcome = comparison.inventoryOutcome.name,
                dexStructuralOutcome = comparison.dexStructuralOutcome.name,
                manifestSemanticOutcome = comparison.manifestSemanticOutcome.name,
                resourceTableSemanticOutcome = comparison.resourceTableSemanticOutcome.name,
                reason = comparison.reason,
                entryCount = comparison.entries.size,
                sameCount = comparison.entries.count { it.result == "MATCH" },
                changedCount = comparison.entries.count { it.result == "HASH_MISMATCH" },
                addedCount = comparison.entries.count { it.result == "ADDED" },
                missingCount = comparison.entries.count { it.result == "MISSING" },
                semanticDifferenceCount = comparison.semanticDifferences.size,
            ),
        )
    }

    private data class ComparisonInput(val file: File, val expected: ExpectedApkFile)

    private suspend fun comparisonTargetMismatch(
        run: ComparisonRunEntity,
        job: com.sanka1610.reprodroid.data.local.JobEntity,
    ): String? {
        val snapshot = dao.getReleaseSnapshot(run.releaseSnapshotId) ?: return "RELEASE_SNAPSHOT_MISSING"
        val app = dao.getRegisteredApp(run.registeredAppId) ?: return "REGISTERED_APP_MISSING"
        val jobRepositoryUrl = job.repositoryUrl.removeSuffix(".git").trimEnd('/').lowercase()
        val appRepositoryUrl = app.canonicalRepositoryUrl.removeSuffix(".git").trimEnd('/').lowercase()
        val effectiveBuildMustBeKnown = job.state in setOf(
            JobState.AWAITING_CONFIRMATION.name,
            JobState.QUEUED.name,
            JobState.CLONING.name,
            JobState.VERIFYING_WRAPPER.name,
            JobState.DISCOVERING_CONFIGURATION.name,
            JobState.BUILDING.name,
            JobState.DISCOVERING_ARTIFACTS.name,
            JobState.SUCCEEDED.name,
        )
        if (run.runnerContract.startsWith("generic-build@1")) {
            val revision = run.buildConfigurationRevision ?: return "BUILD_CONFIGURATION_REVISION_MISSING"
            val expectedHash = run.buildConfigurationSha256 ?: return "BUILD_CONFIGURATION_HASH_MISSING"
            val stored = dao.getBuildConfiguration(run.registeredAppId, revision) ?: return "BUILD_CONFIGURATION_MISSING"
            if (stored.contentSha256 != expectedHash) return "BUILD_CONFIGURATION_CHANGED"
            val configuration = runCatching { BuildConfigurationValidator.decodeCanonical(stored.canonicalJson, stored.contentSha256) }
                .getOrElse { return "BUILD_CONFIGURATION_INVALID" }
            val attempt = job.genericAttempt?.lowercase() ?: return "GENERIC_ATTEMPT_MISSING"
            val expectedRecipe = "generic-${expectedHash.take(16)}-$attempt"
            return when {
                jobRepositoryUrl != appRepositoryUrl -> "RUNNER_REPOSITORY_MISMATCH"
                job.revisionType != RevisionType.COMMIT.name -> "RUNNER_REVISION_TYPE_MISMATCH"
                job.revisionValue != run.expectedCommitSha -> "RUNNER_COMMIT_REQUEST_MISMATCH"
                job.genericComparisonId != run.comparisonRunId -> "RUNNER_COMPARISON_MISMATCH"
                job.genericConfigurationRevision != revision || job.genericConfigurationSha256 != expectedHash -> "RUNNER_CONFIGURATION_MISMATCH"
                job.resolvedCommitSha != null && job.resolvedCommitSha != run.expectedCommitSha -> "SOURCE_COMMIT_MISMATCH"
                effectiveBuildMustBeKnown && job.effectiveRecipeId != expectedRecipe -> "BUILD_RECIPE_MISMATCH"
                effectiveBuildMustBeKnown && job.effectiveVariantName != configuration.variant -> "BUILD_VARIANT_MISMATCH"
                effectiveBuildMustBeKnown && job.effectiveJavaMajor != configuration.javaMajor -> "BUILD_JAVA_MISMATCH"
                effectiveBuildMustBeKnown && job.effectiveBuildRoot != configuration.buildRoot -> "BUILD_ROOT_MISMATCH"
                effectiveBuildMustBeKnown && job.effectiveBuildTasks != configuration.tasks.joinToString("\n") -> "BUILD_TASK_MISMATCH"
                else -> null
            }
        }
        return when {
            jobRepositoryUrl != appRepositoryUrl -> "RUNNER_REPOSITORY_MISMATCH"
            job.revisionType != RevisionType.TAG.name -> "RUNNER_REVISION_TYPE_MISMATCH"
            job.revisionValue != snapshot.tagName -> "RUNNER_TAG_MISMATCH"
            job.resolvedCommitSha != null && job.resolvedCommitSha != run.expectedCommitSha -> "SOURCE_COMMIT_MISMATCH"
            effectiveBuildMustBeKnown && job.effectiveRecipeId != run.expectedRecipeId -> "BUILD_RECIPE_MISMATCH"
            effectiveBuildMustBeKnown && job.effectiveVariantName != run.expectedVariantName -> "BUILD_VARIANT_MISMATCH"
            effectiveBuildMustBeKnown && job.effectiveJavaMajor != EXPECTED_BUILD_JAVA_MAJOR -> "BUILD_JAVA_MISMATCH"
            effectiveBuildMustBeKnown && job.effectiveBuildRoot != "." -> "BUILD_ROOT_MISMATCH"
            effectiveBuildMustBeKnown && job.effectiveBuildTasks != EXPECTED_RELEASE_TASKS -> "BUILD_TASK_MISMATCH"
            else -> null
        }
    }

    private suspend fun markIncomparable(
        run: ComparisonRunEntity,
        reason: String,
        resolvedCommitSha: String? = run.runnerResolvedCommitSha,
        recipeId: String? = run.runnerRecipeId,
        variantName: String? = run.runnerVariantName,
        artifactId: String? = run.localArtifactId,
        dependencyPinning: String = run.runnerDependencyPinning,
    ) {
        val now = Instant.now().toString()
        database.withTransaction {
            dao.deleteComparisonEntries(run.comparisonRunId)
            dao.upsertComparisonRun(
                run.copy(
                    localArtifactId = artifactId,
                    runnerResolvedCommitSha = resolvedCommitSha,
                    runnerRecipeId = recipeId,
                    runnerVariantName = variantName,
                    runnerDependencyPinning = dependencyPinning,
                    status = ComparisonRunStatus.COMPLETED.name,
                    outcome = ComparisonOutcome.INCOMPARABLE.name,
                    incomparableReason = reason,
                    updatedAt = now,
                    completedAt = now,
                ),
            )
            if (!isRetryableRunnerFailureReason(reason)) {
                dao.getReleaseAsset(run.referenceAssetId)?.let { asset ->
                    dao.upsertReleaseAsset(
                        asset.copy(
                            comparisonEligibility = ComparisonEligibility.INCOMPARABLE.name,
                            incomparableReason = reason,
                        ),
                    )
                }
            }
        }
    }

    private suspend fun restoreRetryableComparisonEligibility(
        asset: ReleaseAssetEntity,
    ): ReleaseAssetEntity {
        if (asset.comparisonEligibility != ComparisonEligibility.INCOMPARABLE.name) return asset
        val reason = asset.incomparableReason ?: return asset
        if (!isRetryableRunnerFailureReason(reason)) return asset
        val rawSha256 = asset.computedRawSha256 ?: return asset
        if (!dao.hasCompletedIncomparableRun(asset.releaseAssetId, reason)) return asset
        dao.restoreRetryableComparisonEligibility(
            releaseAssetId = asset.releaseAssetId,
            expectedReason = reason,
            expectedRawSha256 = rawSha256,
        )
        return dao.getReleaseAsset(asset.releaseAssetId)
            ?: error("The selected release APK disappeared while restoring comparison eligibility.")
    }

    private suspend fun markRepeatIncomparable(
        run: ComparisonRunEntity,
        reason: String,
        resolvedCommitSha: String? = run.repeatRunnerResolvedCommitSha,
        recipeId: String? = run.repeatRunnerRecipeId,
        variantName: String? = run.repeatRunnerVariantName,
        artifactId: String? = run.repeatLocalArtifactId,
        dependencyPinning: String = run.repeatRunnerDependencyPinning,
    ) {
        val now = Instant.now().toString()
        database.withTransaction {
            dao.deleteAdvancedComparisonEntries(run.comparisonRunId)
            dao.upsertComparisonRun(
                run.copy(
                    repeatLocalArtifactId = artifactId,
                    repeatRunnerResolvedCommitSha = resolvedCommitSha,
                    repeatRunnerRecipeId = recipeId,
                    repeatRunnerVariantName = variantName,
                    repeatRunnerDependencyPinning = dependencyPinning,
                    repeatOfficialOutcome = ComparisonOutcome.INCOMPARABLE.name,
                    repeatabilityOutcome = ComparisonOutcome.INCOMPARABLE.name,
                    repeatIncomparableReason = reason,
                    status = ComparisonRunStatus.COMPLETED.name,
                    updatedAt = now,
                    completedAt = now,
                ),
            )
        }
    }

    private fun requireComparisonProfile(
        repositoryUrl: String,
        tagName: String,
        variant: ReleaseVariantPreference,
    ): ComparisonProfile {
        return comparisonProfileOrNull(repositoryUrl, tagName, variant)
            ?: throw IllegalStateException(
                "Phase 2B permits only the fixed MicroG-RE 6.1.4 release comparison profile.",
            )
    }

    private fun comparisonProfileOrNull(
        repositoryUrl: String,
        tagName: String,
        variant: ReleaseVariantPreference,
    ): ComparisonProfile? {
        val canonical = repositoryUrl.removeSuffix(".git").trimEnd('/').lowercase()
        if (
            canonical != MICROG_REPOSITORY || tagName != MICROG_RELEASE_TAG ||
            variant != ReleaseVariantPreference.RELEASE
        ) return null
        return ComparisonProfile(
            recipeId = "morpheapp-microg-re-6.1.4-default-release",
            variantName = "defaultRelease",
        )
    }

    private data class ComparisonProfile(val recipeId: String, val variantName: String)

    private suspend fun downloadReference(assetId: String) = referenceDownloadMutex.withLock {
        downloadReferenceLocked(assetId)
    }

    private suspend fun downloadReferenceLocked(assetId: String) {
        val asset = dao.getReleaseAsset(assetId) ?: error("Release asset was not found.")
        val snapshot = dao.getReleaseSnapshot(asset.releaseSnapshotId)
            ?: error("Release snapshot was not found.")
        val app = dao.getRegisteredApp(snapshot.registeredAppId)
            ?: error("Registered app was not found.")
        val binding = dao.getRepositoryBinding(app.registeredAppId)
            ?: error("Repository identity is not available.")
        requireCurrentDownloadSelection(app, binding, snapshot, asset)
        val settings = currentSettings()
        val configuredLimit = if (app.useGlobalMaxApkSize) {
            settings.defaultMaxApkSizeBytes
        } else {
            app.maxApkSizeBytes
        }
        val comparisonProfile = comparisonProfileOrNull(
            app.canonicalRepositoryUrl,
            snapshot.tagName,
            effectiveReleaseVariant(app, settings),
        )
        val genericComparisonEligible = binding.identityStatus == RepositoryIdentityStatus.VERIFIED.name
        val comparisonEligible = comparisonProfile != null || genericComparisonEligible
        if (asset.providerSizeBytes > configuredLimit) {
            throw ReferenceAssetDownloadException(
                "CONFIGURED_APK_SIZE_LIMIT",
                "The selected APK exceeds the configured ${configuredLimit / (1024L * 1024L)} MiB limit.",
            )
        }
        val immutableExisting = asset.computedRawSha256 != null
        val downloading = asset.copy(
            downloadStatus = ReferenceDownloadStatus.DOWNLOADING.name,
            downloadErrorCode = null,
            downloadErrorMessage = null,
        )
        val attemptId = UUID.randomUUID().toString()
        val reservation = storageManager.reserveDownload("REFERENCE_APK", attemptId, asset.providerSizeBytes)
        val attemptFile = File(referenceDirectory, ".download-$attemptId.part")
        var publishedFile: File? = null
        var persistedAssetId: String? = null
        try {
            if (!immutableExisting) dao.upsertReleaseAsset(downloading)
            Files.deleteIfExists(attemptFile.toPath())
            val downloaded = when (binding.provider) {
                PROVIDER_GITHUB -> downloader.download(
                    stableAssetUrl = asset.stableAssetUrl,
                    expectedSizeBytes = asset.providerSizeBytes,
                    expectedProviderSha256 = asset.providerDigestSha256,
                    destinationPart = attemptFile,
                )
                PROVIDER_CODEBERG -> codebergDownloader.download(
                    stableAssetUrl = asset.stableAssetUrl,
                    expectedSizeBytes = asset.providerSizeBytes,
                    expectedProviderSha256 = asset.providerDigestSha256,
                    destinationPart = attemptFile,
                    policy = CodebergAssetDownloadPolicy(
                        repository = CodebergRepositoryParser.parse(app.canonicalRepositoryUrl),
                        tagName = snapshot.tagName,
                        assetName = asset.assetName,
                    ),
                )
                else -> throw IllegalStateException("The registered release provider is not supported.")
            }
            val inspection = inspector.inspect(attemptFile)

            if (immutableExisting && asset.computedRawSha256 == downloaded.computedSha256) {
                requireCurrentDownloadSelection(app, binding, snapshot, asset)
                val existingFile = finalFile(asset.releaseAssetId)
                if (!existingFile.isFile) {
                    Files.move(attemptFile.toPath(), existingFile.toPath(), StandardCopyOption.ATOMIC_MOVE)
                    publishedFile = existingFile
                } else {
                    Files.deleteIfExists(attemptFile.toPath())
                }
                database.withTransaction {
                    requireCurrentDownloadSelection(app, binding, snapshot, asset)
                }
                persistedAssetId = asset.releaseAssetId
                storageManager.recordPresentAndConsume(
                    reservation.copy(resourceId = asset.releaseAssetId),
                    downloaded.bytesWritten,
                    downloaded.computedSha256,
                )
                return
            }

            val targetSnapshot: ReleaseSnapshotEntity
            val targetAsset: ReleaseAssetEntity
            if (immutableExisting) {
                val metadataHash = snapshot.metadataObservationSha256
                    ?: throw IllegalStateException("Legacy release observations cannot be replaced in place.")
                val contentHash = ReleaseObservationHasher.downloadedContentSha256(
                    metadataHash,
                    asset.providerAssetId,
                    downloaded.computedSha256,
                )
                val existingFork = dao.getReleaseSnapshotByObservationHash(app.registeredAppId, contentHash)
                val existingForkAsset = existingFork?.let {
                    dao.getReleaseAsset(it.releaseSnapshotId, asset.providerAssetId)
                }
                if (
                    existingFork != null &&
                    existingForkAsset?.downloadStatus == ReferenceDownloadStatus.VERIFIED.name &&
                    existingForkAsset.computedRawSha256 == downloaded.computedSha256
                ) {
                    requireCurrentDownloadSelection(app, binding, snapshot, asset)
                    val forkFile = finalFile(existingForkAsset.releaseAssetId)
                    if (!forkFile.isFile) {
                        Files.move(attemptFile.toPath(), forkFile.toPath(), StandardCopyOption.ATOMIC_MOVE)
                        publishedFile = forkFile
                    } else {
                        Files.deleteIfExists(attemptFile.toPath())
                    }
                    database.withTransaction {
                        requireCurrentDownloadSelection(app, binding, snapshot, asset)
                        dao.upsertReleaseSnapshot(existingFork.copy(lastObservedAt = Instant.now().toString()))
                    }
                    persistedAssetId = existingForkAsset.releaseAssetId
                    storageManager.recordPresentAndConsume(
                        reservation.copy(resourceId = existingForkAsset.releaseAssetId),
                        downloaded.bytesWritten,
                        downloaded.computedSha256,
                    )
                    return
                }
                val forkSnapshotId = stableId("${app.registeredAppId}/downloaded-content/$contentHash")
                val forkAssetId = stableId("$forkSnapshotId/asset/${asset.providerAssetId}")
                targetSnapshot = snapshot.copy(
                    releaseSnapshotId = forkSnapshotId,
                    observationSha256 = contentHash,
                    observationSchemaVersion = 2,
                    metadataObservationSha256 = metadataHash,
                    fetchedAt = Instant.now().toString(),
                    lastObservedAt = Instant.now().toString(),
                    selectedProviderAssetId = asset.providerAssetId,
                )
                targetAsset = asset.copy(
                    releaseAssetId = forkAssetId,
                    releaseSnapshotId = forkSnapshotId,
                    downloadStatus = ReferenceDownloadStatus.DOWNLOADING.name,
                    downloadErrorCode = null,
                    downloadErrorMessage = null,
                    localContentPath = null,
                    downloadedSizeBytes = null,
                    computedRawSha256 = null,
                    responseEtag = null,
                    downloadContentType = null,
                    finalDownloadHost = null,
                    packageName = null,
                    versionName = null,
                    versionCode = null,
                    signingCertificateSha256 = null,
                    currentSignerSha256 = null,
                    existingInstallStatus = null,
                    installedVersionName = null,
                    installedVersionCode = null,
                    updateStatus = UpdateStatus.NOT_EVALUATED.name,
                    updateEvaluatedAt = null,
                    comparisonEligibility = ComparisonEligibility.NOT_EVALUATED.name,
                    incomparableReason = null,
                    downloadedAt = null,
                )
            } else {
                targetSnapshot = snapshot
                targetAsset = downloading
            }

            val targetFile = finalFile(targetAsset.releaseAssetId)
            requireCurrentDownloadSelection(app, binding, snapshot, asset)
            Files.move(
                attemptFile.toPath(),
                targetFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
            )
            publishedFile = targetFile
            inspection.iconPng?.let { icon -> saveIcon(targetAsset.releaseAssetId, icon) }
            val verified = targetAsset.copy(
                    downloadStatus = ReferenceDownloadStatus.VERIFIED.name,
                    localContentPath = targetFile.absolutePath,
                    downloadedSizeBytes = downloaded.bytesWritten,
                    computedRawSha256 = downloaded.computedSha256,
                    responseEtag = downloaded.responseEtag,
                    downloadContentType = downloaded.downloadContentType,
                    finalDownloadHost = downloaded.finalHost,
                    packageName = inspection.packageName,
                    versionName = inspection.versionName,
                    versionCode = inspection.versionCode,
                    signingCertificateSha256 = inspection.signingCertificateSha256.joinToString(","),
                    currentSignerSha256 = inspection.currentSignerSha256.joinToString(","),
                    existingInstallStatus = inspection.existingInstallStatus?.name,
                    installedVersionName = inspection.installedVersionName,
                    installedVersionCode = inspection.installedVersionCode,
                    updateStatus = evaluateUpdateStatus(inspection.versionCode, inspection.installedVersionCode).name,
                    updateEvaluatedAt = Instant.now().toString(),
                    comparisonEligibility = if (comparisonEligible) {
                        ComparisonEligibility.READY_FOR_COMPARISON.name
                    } else {
                        ComparisonEligibility.INCOMPARABLE.name
                    },
                    incomparableReason = if (comparisonEligible) {
                        null
                    } else {
                        COMPARISON_PROFILE_NOT_SUPPORTED_REASON
                    },
                    downloadedAt = Instant.now().toString(),
                )
            database.withTransaction {
                requireCurrentDownloadSelection(app, binding, snapshot, asset)
                if (targetSnapshot.releaseSnapshotId != snapshot.releaseSnapshotId) {
                    dao.upsertReleaseSnapshot(targetSnapshot)
                }
                dao.upsertReleaseAsset(verified)
            }
            persistedAssetId = verified.releaseAssetId
            storageManager.recordPresentAndConsume(
                reservation.copy(resourceId = verified.releaseAssetId),
                downloaded.bytesWritten,
                downloaded.computedSha256,
            )
        } catch (failure: Throwable) {
            val partCleanupFailure = runCatching {
                withContext(NonCancellable + Dispatchers.IO) { Files.deleteIfExists(attemptFile.toPath()) }
            }.exceptionOrNull()
            val finalBytesMayExist = runCatching {
                withContext(NonCancellable + Dispatchers.IO) {
                    publishedFile?.let { Files.exists(it.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS) } == true
                }
            }.getOrDefault(true)
            val unpublishedFile = publishedFile?.takeIf { persistedAssetId == null }
            val publishedCleanupFailure = if (unpublishedFile != null) runCatching {
                withContext(NonCancellable + Dispatchers.IO) { Files.deleteIfExists(unpublishedFile.toPath()) }
            }.exceptionOrNull() else null
            val metadataFailure = if (!immutableExisting && persistedAssetId == null) runCatching {
                withContext(NonCancellable) {
                    dao.failUnverifiedReleaseDownload(
                        releaseAssetId = downloading.releaseAssetId,
                        errorCode = failure.errorCode(),
                        errorMessage = failure.message ?: "Reference APK verification failed.",
                    )
                }
            }.exceptionOrNull() else null
            val reservationFailure = runCatching {
                withContext(NonCancellable) {
                    storageManager.recordDownloadFailure(reservation, finalBytesMayExist)
                }
            }.exceptionOrNull()
            listOfNotNull(partCleanupFailure, publishedCleanupFailure, metadataFailure, reservationFailure)
                .forEach(failure::addSuppressed)
            throw failure
        }
    }

    private suspend fun requireCurrentDownloadSelection(
        expectedApp: RegisteredAppEntity,
        expectedBinding: AppRepositoryBindingEntity,
        expectedSnapshot: ReleaseSnapshotEntity,
        expectedAsset: ReleaseAssetEntity,
    ) {
        val currentApp = dao.getRegisteredApp(expectedApp.registeredAppId)
        check(
            currentApp != null &&
                currentApp.trackingState == AppTrackingState.ACTIVE.name &&
                currentApp.canonicalRepositoryUrl == expectedApp.canonicalRepositoryUrl,
        ) { "The registered repository changed or tracking stopped during download." }

        val currentBinding = dao.getRepositoryBinding(expectedApp.registeredAppId)
        check(
            currentBinding != null &&
                currentBinding.identityStatus == RepositoryIdentityStatus.VERIFIED.name &&
                currentBinding.provider == expectedBinding.provider &&
                currentBinding.instance == expectedBinding.instance &&
                currentBinding.providerRepositoryId == expectedBinding.providerRepositoryId,
        ) { "The verified repository identity changed during download." }

        val currentSnapshot = dao.getReleaseSnapshot(expectedSnapshot.releaseSnapshotId)
        val latestSnapshot = dao.getLatestReleaseSnapshot(expectedApp.registeredAppId)
        check(
            currentSnapshot != null &&
                latestSnapshot?.releaseSnapshotId == expectedSnapshot.releaseSnapshotId &&
                currentSnapshot.observationSha256 == expectedSnapshot.observationSha256 &&
                currentSnapshot.metadataObservationSha256 == expectedSnapshot.metadataObservationSha256 &&
                currentSnapshot.selectedProviderAssetId == expectedAsset.providerAssetId,
        ) { "The selected release observation changed during download." }

        val currentAsset = dao.getReleaseAsset(expectedAsset.releaseAssetId)
        check(
            currentAsset != null &&
                currentAsset.releaseSnapshotId == expectedAsset.releaseSnapshotId &&
                currentAsset.providerAssetId == expectedAsset.providerAssetId &&
                currentAsset.assetName == expectedAsset.assetName &&
                currentAsset.stableAssetUrl == expectedAsset.stableAssetUrl &&
                currentAsset.contentType == expectedAsset.contentType &&
                currentAsset.providerSizeBytes == expectedAsset.providerSizeBytes &&
                currentAsset.providerDigestSha256 == expectedAsset.providerDigestSha256 &&
                currentAsset.providerCreatedAt == expectedAsset.providerCreatedAt &&
                currentAsset.computedRawSha256 == expectedAsset.computedRawSha256,
        ) { "The selected release asset changed during download." }
    }

    private fun ResolvedProviderRelease.toSnapshot(
        appId: String,
        snapshotId: String,
        observationSha256: String,
        now: String,
        selection: ProviderSelectedAsset?,
    ) =
        ReleaseSnapshotEntity(
            releaseSnapshotId = snapshotId,
            registeredAppId = appId,
            providerReleaseId = release.id,
            tagName = release.tagName,
            resolvedCommitSha = resolvedCommitSha,
            releaseName = release.name ?: release.tagName,
            releaseUrl = release.htmlUrl,
            targetCommitishRaw = release.targetCommitish,
            isDraft = release.draft,
            isPrerelease = release.prerelease,
            isImmutable = release.immutable,
            releaseCreatedAt = release.createdAt,
            publishedAt = release.publishedAt,
            fetchedAt = now,
            observationSha256 = observationSha256,
            lastObservedAt = now,
            observationSchemaVersion = 2,
            metadataObservationSha256 = observationSha256,
            selectedProviderAssetId = selection?.asset?.id,
        )

    private fun ResolvedProviderRelease.metadataObservationSha256(
        providerName: String,
        providerInstance: String,
        providerRepositoryId: String,
    ): String =
        ReleaseObservationHasher.metadataSha256(
            ReleaseMetadataObservationInput(
                provider = providerName,
                instance = providerInstance,
                providerRepositoryId = providerRepositoryId,
                providerReleaseId = release.id,
                tagName = release.tagName,
                resolvedCommitSha = resolvedCommitSha,
                targetCommitishRaw = release.targetCommitish,
                releaseName = release.name ?: release.tagName,
                releaseUrl = release.htmlUrl,
                isDraft = release.draft,
                isPrerelease = release.prerelease,
                isImmutable = release.immutable,
                releaseCreatedAt = release.createdAt,
                publishedAt = release.publishedAt,
                candidates = candidates.map { candidate ->
                    ReleaseMetadataObservationCandidate(
                        providerAssetId = candidate.asset.id,
                        assetName = candidate.asset.name,
                        stableAssetUrl = candidate.asset.browserDownloadUrl,
                        contentType = candidate.asset.contentType,
                        providerSizeBytes = candidate.asset.size,
                        providerDigestSha256 = candidate.providerSha256,
                        providerCreatedAt = candidate.asset.providerCreatedAt,
                    )
                },
            ),
        )

    private fun ResolvedGitHubRelease.legacyObservationSha256(providerRepositoryId: String): String =
        ReleaseObservationHasher.sha256(
            ReleaseObservationInput(
                provider = PROVIDER_GITHUB,
                instance = GITHUB_INSTANCE,
                providerRepositoryId = providerRepositoryId,
                providerReleaseId = release.id,
                tagName = release.tagName,
                resolvedCommitSha = resolvedCommitSha,
                targetCommitishRaw = release.targetCommitish,
                releaseName = release.name ?: release.tagName,
                releaseUrl = release.htmlUrl,
                isDraft = release.draft,
                isPrerelease = release.prerelease,
                isImmutable = release.immutable,
                releaseCreatedAt = release.createdAt,
                publishedAt = requireNotNull(release.publishedAt),
                providerAssetId = selectedAsset?.asset?.id,
                assetName = selectedAsset?.asset?.name,
                stableAssetUrl = selectedAsset?.asset?.browserDownloadUrl,
                contentType = selectedAsset?.asset?.contentType,
                providerSizeBytes = selectedAsset?.asset?.size,
                providerDigestSha256 = selectedAsset?.providerSha256,
                selectionReason = selectedAsset?.reason,
                manualCandidates = if (selectedAsset == null) {
                    candidates.map { candidate ->
                        ReleaseObservationCandidate(
                            providerAssetId = candidate.asset.id,
                            assetName = candidate.asset.name,
                            stableAssetUrl = candidate.asset.browserDownloadUrl,
                            contentType = candidate.asset.contentType,
                            providerSizeBytes = candidate.asset.size,
                            providerDigestSha256 = candidate.providerSha256,
                        )
                    }
                } else {
                    emptyList()
                },
            ),
        )

    private fun ResolvedProviderRelease.toAssets(
        snapshotId: String,
        automaticSelection: ProviderSelectedAsset?,
    ): List<ReleaseAssetEntity> {
        val assets = automaticSelection?.let { selected ->
            listOf(selected)
        } ?: candidates
        return assets.map { candidate ->
            ReleaseAssetEntity(
                releaseAssetId = stableId("$snapshotId/asset/${candidate.asset.id}"),
                releaseSnapshotId = snapshotId,
                providerAssetId = candidate.asset.id,
                assetName = candidate.asset.name,
                stableAssetUrl = candidate.asset.browserDownloadUrl,
                selectionReason = automaticSelection
                    ?.takeIf { it.asset.id == candidate.asset.id }
                    ?.reason
                    ?: AssetSelectionReason.MANUAL_SELECTION_REQUIRED.name,
                contentType = candidate.asset.contentType,
                providerSizeBytes = candidate.asset.size,
                providerDigestSha256 = candidate.providerSha256,
                providerCreatedAt = candidate.asset.providerCreatedAt,
            )
        }
    }

    private fun Throwable.isProviderNotModified(): Boolean =
        (this is GitHubProviderException && code == "NOT_MODIFIED") ||
            (this is CodebergProviderException && code == "NOT_MODIFIED")

    private fun RepositoryRegistrationPreview.toDiscovery(
        appId: String,
        discoveryId: String,
        now: String,
    ) = SourceDiscoveryEntity(
        discoveryId = discoveryId,
        registeredAppId = appId,
        repositoryProvider = identity.provider,
        repositoryInstance = identity.instance,
        providerRepositoryId = identity.providerRepositoryId,
        requestedBranch = discovery.requestedBranch,
        resolvedCommitSha = discovery.resolvedCommitSha,
        rootTreeSha = discovery.rootTreeSha,
        state = discovery.state,
        reason = discovery.reason,
        entryCount = discovery.entryCount,
        requestCount = discovery.requestCount,
        receivedBytes = discovery.receivedBytes,
        maxDepth = discovery.maxDepth,
        candidateCount = discovery.candidates.size,
        excludedSymlinkCount = discovery.excludedSymlinkCount,
        excludedSubmoduleCount = discovery.excludedSubmoduleCount,
        excludedCacheTreeCount = discovery.excludedCacheTreeCount,
        startedAt = now,
        finishedAt = now,
    )

    private fun RepositoryRegistrationPreview.toCandidates(discoveryId: String) =
        discovery.candidates.map { candidate ->
            GradleCandidateEntity(
                discoveryId = discoveryId,
                relativePath = candidate.relativePath,
                buildRoot = candidate.buildRoot,
                fileKind = candidate.fileKind,
                blobSha = candidate.blobSha,
                mode = candidate.mode,
                dsl = candidate.dsl,
            )
        }

    private fun stableId(value: String): String =
        UUID.nameUUIDFromBytes(value.toByteArray(StandardCharsets.UTF_8)).toString()

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == value } ?: fallback

    private fun partFile(assetId: String) = File(referenceDirectory, "$assetId.part.apk")
    private fun finalFile(assetId: String) = File(referenceDirectory, "$assetId.apk")
    private fun iconFile(assetId: String) = File(iconDirectory, "$assetId.png")
    private fun partIconFile(assetId: String) = File(iconDirectory, "$assetId.part.png")

    private fun confinedRegularFileSize(file: File): Long {
        requireConfinedOwnedFile(file)
        return if (
            Files.isRegularFile(file.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS) &&
            !Files.isSymbolicLink(file.toPath())
        ) {
            Files.size(file.toPath())
        } else {
            0L
        }
    }

    private fun requireConfinedOwnedFile(file: File) {
        val normalized = file.toPath().toAbsolutePath().normalize()
        val referenceRoot = referenceDirectory.toPath().toAbsolutePath().normalize()
        val iconRoot = iconDirectory.toPath().toAbsolutePath().normalize()
        require(normalized.parent == referenceRoot || normalized.parent == iconRoot) {
            "App-private deletion path escaped its owned directory."
        }
        require(!Files.isSymbolicLink(normalized)) { "Symbolic links are not deleted as app resources." }
    }

    private fun saveIcon(assetId: String, png: ByteArray) {
        if ((!iconDirectory.exists() && !iconDirectory.mkdirs()) || !iconDirectory.isDirectory) {
            throw IllegalStateException("Reference icon storage directory could not be created.")
        }
        val partIcon = File(iconDirectory, "$assetId.part.png")
        FileOutputStream(partIcon, false).use { output ->
            output.write(png)
            output.fd.sync()
        }
        Files.move(
            partIcon.toPath(),
            iconFile(assetId).toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    }

    private suspend fun currentSettings(): GlobalSettingsEntity =
        dao.getGlobalSettings() ?: defaultSettings().also { dao.upsertGlobalSettings(it) }

    private fun defaultSettings() = GlobalSettingsEntity(updatedAt = Instant.EPOCH.toString())

    @Suppress("DEPRECATION")
    private fun installedVersionCodeForSourceLock(record: RegisteredAppRecord): Long? {
        val packageName = record.latestRelease?.selectedAsset?.packageName
            ?: record.releases
                .asSequence()
                .sortedByDescending { it.snapshot.publishedAt }
                .flatMap { it.assets.asSequence() }
                .mapNotNull { it.packageName }
                .firstOrNull()
            ?: return null
        return installedPackageVersion(packageName)
    }

    @Suppress("DEPRECATION")
    private fun installedPackageVersion(packageName: String): Long? {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                context.packageManager.getPackageInfo(packageName, 0)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                packageInfo.versionCode.toLong()
            }
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    private suspend fun invalidateCurrentComparison(registeredAppId: String) {
        val asset = dao.getRegisteredAppRecord(registeredAppId)?.latestRelease?.selectedAsset ?: return
        dao.upsertReleaseAsset(
            asset.copy(
                comparisonEligibility = ComparisonEligibility.NOT_EVALUATED.name,
                incomparableReason = "SETTINGS_CHANGED_REFRESH_REQUIRED",
            ),
        )
    }

    private fun effectiveReleaseVariant(
        app: RegisteredAppEntity,
        settings: GlobalSettingsEntity,
    ): ReleaseVariantPreference = enumValueOrDefault(
        if (app.useGlobalReleaseVariant) {
            settings.defaultReleaseVariantPreference
        } else {
            app.releaseVariantPreference
        },
        ReleaseVariantPreference.RELEASE,
    )

    private fun effectivePreferredAbi(
        app: RegisteredAppEntity,
        settings: GlobalSettingsEntity,
    ): PreferredAbi = enumValueOrDefault(
        if (app.useGlobalPreferredAbi) settings.defaultPreferredAbi else app.preferredAbi,
        PreferredAbi.ARM64_V8A,
    )

    private fun Throwable.errorCode(): String = when (this) {
        is GitHubProviderException -> code
        is CodebergProviderException -> code
        is ReleaseAssetSelectionException -> code
        is com.sanka1610.reprodroid.data.artifact.ReferenceAssetDownloadException -> code
        else -> "REFERENCE_APK_FAILED"
    }

    private fun Throwable.sourceDiscoveryFailureReason(): String = when (this) {
        is GitHubProviderException -> code
        is CodebergProviderException -> code
        is IllegalStateException -> "INVALID_METADATA"
        is IllegalArgumentException -> "INVALID_METADATA"
        else -> "INVALID_METADATA"
    }

    private fun releaseProviderName(provider: String): String = when (provider) {
        PROVIDER_GITHUB -> PROVIDER_GITHUB_RELEASES
        PROVIDER_CODEBERG -> PROVIDER_CODEBERG_RELEASES
        else -> provider
    }

    private fun validateRegistrationPreview(preview: RepositoryRegistrationPreview) {
        val normalizedInput = when (preview.identity.provider) {
            PROVIDER_GITHUB -> GitHubRepositoryParser.parse(preview.normalizedInputUrl).canonicalUrl
            PROVIDER_CODEBERG -> CodebergRepositoryParser.parse(preview.normalizedInputUrl).canonicalUrl
            else -> throw IllegalArgumentException("The repository provider is not supported.")
        }
        check(normalizedInput == preview.identity.repository.canonicalUrl) {
            "Repository preview URL and identity do not match."
        }
        val providerId = preview.identity.providerRepositoryId
        check(providerId.toLongOrNull()?.takeIf { it > 0 }?.toString() == providerId) {
            "Repository preview provider ID is invalid."
        }
        check(preview.discovery.requestedBranch == preview.identity.defaultBranch) {
            "Repository preview branch and discovery do not match."
        }
    }

    private fun validatedGroupName(value: String): String {
        val normalized = value.trim().replace(Regex("\\s+"), " ")
        require(normalized.isNotEmpty()) { "App group name is required." }
        require(normalized.length <= MAX_GROUP_NAME_LENGTH) { "App group name is too long." }
        require(normalized.toByteArray(StandardCharsets.UTF_8).size <= MAX_GROUP_NAME_UTF8_BYTES) {
            "App group name is too large."
        }
        require(normalized.none { it.isISOControl() }) { "App group name contains control characters." }
        return normalized
    }

    private fun normalizedOptionalText(value: String?, maximumLength: Int, label: String): String? {
        val normalized = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
        require(normalized.length <= maximumLength) { "$label is too long." }
        require(normalized.none { it.isISOControl() }) { "$label contains control characters." }
        return normalized
    }

    private fun canonicalUuid(value: String, label: String): String {
        val normalized = runCatching { UUID.fromString(value).toString() }
            .getOrElse { throw IllegalArgumentException("$label is invalid.") }
        require(normalized == value) { "$label must be a canonical UUID." }
        return normalized
    }

    private companion object {
        const val PROVIDER_GITHUB_RELEASES = "PUBLIC_GITHUB_RELEASES"
        const val PROVIDER_CODEBERG_RELEASES = "PUBLIC_CODEBERG_RELEASES"
        const val PROVIDER_GITHUB = "GITHUB"
        const val PROVIDER_CODEBERG = "CODEBERG"
        const val GITHUB_INSTANCE = "github.com"
        const val PRIMARY_REGISTRATION_SLOT = "PRIMARY"
        const val COMPARISON_PROFILE_NOT_SUPPORTED_REASON = "COMPARISON_PROFILE_NOT_SUPPORTED"
        const val MICROG_REPOSITORY = "https://github.com/morpheapp/microg-re"
        const val MICROG_RELEASE_TAG = "6.1.4"
        const val EXPECTED_BUILD_JAVA_MAJOR = 18
        const val REPEATED_BUILD_PROTOCOL_VERSION = 2
        const val EXPECTED_RELEASE_TASKS = "clean\n:play-services-core:assembleDefaultRelease"
        const val INSTALL_CALLBACK_GRACE_SECONDS = 30L
        const val MIN_ANDROID_STORAGE_BUDGET = 1L * 1024L * 1024L * 1024L
        const val MAX_ANDROID_STORAGE_BUDGET = 64L * 1024L * 1024L * 1024L
        const val MAX_GROUPS = 100
        const val GROUP_SORT_SPACING = 1_024L
        const val MAX_GROUP_NAME_LENGTH = 80
        const val MAX_GROUP_NAME_UTF8_BYTES = 240
        const val MAX_DISPLAY_NAME_LENGTH = 120
        const val MAX_AUTHOR_LENGTH = 160
        const val MAX_NOTE_LENGTH = 10_000
        const val MAX_NOTE_UTF8_BYTES = 30_000
        const val DELETION_PREVIEW_MINUTES = 15L
        val SUPPORTED_APK_LIMITS = setOf(
            64L * 1024L * 1024L,
            128L * 1024L * 1024L,
            256L * 1024L * 1024L,
            GlobalSettingsEntity.MAX_APK_SIZE_BYTES,
        )
    }
}

internal fun isRetryableRunnerFailureReason(reason: String?): Boolean = reason in setOf(
    "RUNNER_JOB_FAILED",
    "RUNNER_JOB_CANCELLED",
    "RUNNER_JOB_INTERRUPTED",
    "RUNNER_JOB_MISSING",
)
