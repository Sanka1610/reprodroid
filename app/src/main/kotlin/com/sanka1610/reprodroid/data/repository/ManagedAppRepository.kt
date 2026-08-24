package com.sanka1610.reprodroid.data.repository

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.room.withTransaction
import com.sanka1610.reprodroid.data.artifact.ApkInspector
import com.sanka1610.reprodroid.data.artifact.ApkComparisonException
import com.sanka1610.reprodroid.data.artifact.ApkContentComparator
import com.sanka1610.reprodroid.data.artifact.ExpectedApkFile
import com.sanka1610.reprodroid.data.artifact.GitHubAssetDownloader
import com.sanka1610.reprodroid.data.artifact.ReleaseApkInstaller
import com.sanka1610.reprodroid.data.artifact.ReferenceAssetDownloadException
import com.sanka1610.reprodroid.data.local.ArtifactDownloadStatus
import com.sanka1610.reprodroid.data.local.ArtifactEntity
import com.sanka1610.reprodroid.data.local.AdvancedComparisonAxis
import com.sanka1610.reprodroid.data.local.AdvancedComparisonEntryEntity
import com.sanka1610.reprodroid.data.local.AppSettingsUpdate
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
import com.sanka1610.reprodroid.data.local.RegisteredAppEntity
import com.sanka1610.reprodroid.data.local.RegisteredAppRecord
import com.sanka1610.reprodroid.data.local.ReleaseAssetEntity
import com.sanka1610.reprodroid.data.local.ReleaseDiscoveryStatus
import com.sanka1610.reprodroid.data.local.ReleaseSnapshotEntity
import com.sanka1610.reprodroid.data.local.ReleaseVariantPreference
import com.sanka1610.reprodroid.data.local.ReproDroidDatabase
import com.sanka1610.reprodroid.data.local.ThemeMode
import com.sanka1610.reprodroid.data.local.UpdateStatus
import com.sanka1610.reprodroid.data.provider.GitHubProviderException
import com.sanka1610.reprodroid.data.provider.GitHubReleasesClient
import com.sanka1610.reprodroid.data.provider.ResolvedGitHubRelease
import com.sanka1610.reprodroid.data.provider.ReleaseAssetSelectionException
import com.sanka1610.reprodroid.data.network.JobState
import com.sanka1610.reprodroid.data.network.RevisionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.UUID

class ManagedAppRepository(
    private val context: Context,
    private val database: ReproDroidDatabase,
    private val jobRepository: JobRepository,
    private val provider: GitHubReleasesClient = GitHubReleasesClient(),
    private val downloader: GitHubAssetDownloader = GitHubAssetDownloader(),
    private val comparator: ApkContentComparator = ApkContentComparator(),
) {
    private val dao: ManagedAppDao = database.managedAppDao()
    private val inspector = ApkInspector(context.packageManager)
    private val releaseInstaller = ReleaseApkInstaller(context, dao)
    private val packageInstaller = context.packageManager.packageInstaller
    private val referenceDirectory = File(context.filesDir, "reference-apks")
    private val iconDirectory = File(context.filesDir, "reference-icons")

    fun observeApps(): Flow<List<RegisteredAppRecord>> = dao.observeRegisteredApps()

    fun observeSettings(): Flow<GlobalSettingsEntity> = dao.observeGlobalSettings().map { settings ->
        settings ?: defaultSettings()
    }

    suspend fun ensureSettings() {
        if (dao.getGlobalSettings() == null) dao.upsertGlobalSettings(defaultSettings())
    }

    fun observeApp(registeredAppId: String): Flow<RegisteredAppRecord?> =
        dao.observeRegisteredApp(registeredAppId)

    suspend fun previewLatest(repositoryUrl: String): ResolvedGitHubRelease {
        val settings = currentSettings()
        return provider.resolveLatestRelease(
            repositoryUrl = repositoryUrl,
            preferredAbi = enumValueOrDefault(settings.defaultPreferredAbi, PreferredAbi.ARM64_V8A),
            preferredVariant = enumValueOrDefault(
                settings.defaultReleaseVariantPreference,
                ReleaseVariantPreference.RELEASE,
            ),
        )
    }

    suspend fun registerAndDownload(
        preview: ResolvedGitHubRelease,
        mode: ManagementMode,
        installationSource: InstallationSource,
        localBuildRiskConfirmed: Boolean,
    ): String {
        if (dao.getRegisteredAppByCanonicalUrl(preview.repository.canonicalUrl) != null) {
            throw IllegalStateException("This GitHub repository is already registered.")
        }
        validateModeAndInstallationSource(mode, installationSource)
        if (installationSource == InstallationSource.LOCAL_BUILD) {
            require(localBuildRiskConfirmed) {
                "Local build installation requires explicit acknowledgement of signing and update risks."
            }
        }
        val settings = currentSettings()
        val now = Instant.now().toString()
        val appId = UUID.randomUUID().toString()
        val snapshotId = stableId("$appId/release/${preview.release.id}")
        val assetId = stableId("$snapshotId/asset/${preview.selectedAsset.asset.id}")
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
        val snapshot = preview.toSnapshot(appId, snapshotId, now)
        val asset = preview.toAsset(snapshotId, assetId)
        database.withTransaction {
            dao.upsertRegisteredApp(app)
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
            dao.deleteRegisteredApp(appId)
            partFile(assetId).delete()
            finalFile(assetId).delete()
            iconFile(assetId).delete()
            throw failure
        }
        return appId
    }

    suspend fun refresh(registeredAppId: String) {
        val app = dao.getRegisteredApp(registeredAppId)
            ?: throw IllegalArgumentException("Registered app was not found.")
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
            val latest = try {
                provider.resolveLatestRelease(
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
            } catch (notModified: GitHubProviderException) {
                if (notModified.code != "NOT_MODIFIED") throw notModified
                val now = Instant.now().toString()
                dao.upsertRegisteredApp(
                    app.copy(
                        releaseDiscoveryStatus = ReleaseDiscoveryStatus.AVAILABLE.name,
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
            val existingSnapshot = dao.getReleaseSnapshot(registeredAppId, latest.release.id)
            val snapshotId = existingSnapshot?.releaseSnapshotId
                ?: stableId("$registeredAppId/release/${latest.release.id}")
            val existingAsset = dao.getReleaseAsset(snapshotId, latest.selectedAsset.asset.id)
            val assetId = existingAsset?.releaseAssetId
                ?: stableId("$snapshotId/asset/${latest.selectedAsset.asset.id}")
            database.withTransaction {
                dao.upsertReleaseSnapshot(latest.toSnapshot(registeredAppId, snapshotId, now))
                dao.upsertReleaseAsset(existingAsset ?: latest.toAsset(snapshotId, assetId))
                dao.upsertRegisteredApp(
                    app.copy(
                        releaseDiscoveryStatus = ReleaseDiscoveryStatus.AVAILABLE.name,
                        releaseDiscoveryErrorCode = null,
                        releaseDiscoveryErrorMessage = null,
                        releaseMetadataEtag = latest.responseEtag,
                        lastReleaseCheckedAt = now,
                        updatedAt = now,
                    ),
                )
            }
            if (existingAsset == null) downloadReference(assetId)
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

    suspend fun recoverInterruptedDownloads() {
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
            InstallationSource.OFFICIAL_RELEASE -> releaseInstaller.install(registeredAppId, asset)
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
        check(record.app.managementMode == ManagementMode.VERIFICATION.name) {
            "Only apps in verification mode can start a reproducibility comparison."
        }
        check(record.app.releaseDiscoveryStatus == ReleaseDiscoveryStatus.AVAILABLE.name) {
            "Refresh release metadata after changing variant or ABI settings."
        }
        val release = record.latestRelease ?: error("No resolved release is available.")
        val asset = release.selectedAsset ?: error("No selected release APK is available.")
        check(asset.downloadStatus == ReferenceDownloadStatus.VERIFIED.name) {
            "The official reference APK must be verified before comparison."
        }
        check(asset.comparisonEligibility != ComparisonEligibility.INCOMPARABLE.name) {
            asset.incomparableReason ?: "The selected APK is not eligible for comparison."
        }
        val profile = requireComparisonProfile(
            record.app.canonicalRepositoryUrl,
            release.snapshot.tagName,
            effectiveReleaseVariant(record.app, currentSettings()),
        )
        val jobId = jobRepository.createRealTrustedJob(
            repositoryUrl = record.app.canonicalRepositoryUrl,
            revisionType = RevisionType.TAG,
            revisionValue = release.snapshot.tagName,
        )
        val now = Instant.now().toString()
        val comparisonRunId = UUID.randomUUID().toString()
        dao.upsertComparisonRun(
            ComparisonRunEntity(
                comparisonRunId = comparisonRunId,
                registeredAppId = registeredAppId,
                releaseSnapshotId = release.snapshot.releaseSnapshotId,
                referenceAssetId = asset.releaseAssetId,
                runnerJobId = jobId,
                expectedCommitSha = release.snapshot.resolvedCommitSha,
                expectedRecipeId = profile.recipeId,
                expectedVariantName = profile.variantName,
                protocolVersion = REPEATED_BUILD_PROTOCOL_VERSION,
                createdAt = now,
                updatedAt = now,
            ),
        )
        refreshComparison(comparisonRunId)
        return comparisonRunId
    }

    suspend fun confirmComparison(comparisonRunId: String) {
        refreshComparison(comparisonRunId)
        val run = dao.getComparisonRun(comparisonRunId)
            ?: throw IllegalArgumentException("Comparison run was not found.")
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

    suspend fun refreshComparison(comparisonRunId: String) {
        val run = dao.getComparisonRun(comparisonRunId)
            ?: throw IllegalArgumentException("Comparison run was not found.")
        if (run.status == ComparisonRunStatus.COMPLETED.name) return
        if (run.repeatRunnerJobId != null) {
            refreshRepeatComparison(run)
            return
        }
        refreshPrimaryComparison(run)
    }

    private suspend fun refreshPrimaryComparison(run: ComparisonRunEntity) {
        jobRepository.syncJob(run.runnerJobId)
        val job = jobRepository.getJob(run.runnerJobId)
            ?: return markIncomparable(run, "RUNNER_JOB_MISSING")
        val targetMismatch = comparisonTargetMismatch(run, job)
        if (targetMismatch != null && job.resolvedCommitSha != null) {
            markIncomparable(run, targetMismatch, job.resolvedCommitSha, job.effectiveRecipeId, job.effectiveVariantName)
            return
        }
        when (JobState.valueOf(job.state)) {
            JobState.AWAITING_CONFIRMATION -> dao.upsertComparisonRun(
                run.copy(
                    runnerResolvedCommitSha = job.resolvedCommitSha,
                    runnerRecipeId = job.effectiveRecipeId,
                    runnerVariantName = job.effectiveVariantName,
                    status = ComparisonRunStatus.AWAITING_CONFIRMATION.name,
                    updatedAt = Instant.now().toString(),
                ),
            )
            JobState.SUCCEEDED -> completeComparison(run, job.resolvedCommitSha, job.effectiveRecipeId, job.effectiveVariantName)
            JobState.FAILED, JobState.CANCELLED, JobState.INTERRUPTED ->
                markIncomparable(run, "RUNNER_JOB_${job.state}", job.resolvedCommitSha, job.effectiveRecipeId, job.effectiveVariantName)
            else -> dao.upsertComparisonRun(
                run.copy(
                    runnerResolvedCommitSha = job.resolvedCommitSha,
                    runnerRecipeId = job.effectiveRecipeId,
                    runnerVariantName = job.effectiveVariantName,
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
            )
            return
        }
        when (JobState.valueOf(job.state)) {
            JobState.AWAITING_CONFIRMATION -> dao.upsertComparisonRun(
                run.copy(
                    repeatRunnerResolvedCommitSha = job.resolvedCommitSha,
                    repeatRunnerRecipeId = job.effectiveRecipeId,
                    repeatRunnerVariantName = job.effectiveVariantName,
                    status = ComparisonRunStatus.AWAITING_REPEAT_CONFIRMATION.name,
                    updatedAt = Instant.now().toString(),
                ),
            )
            JobState.SUCCEEDED -> completeRepeatComparison(
                run,
                job.resolvedCommitSha,
                job.effectiveRecipeId,
                job.effectiveVariantName,
            )
            JobState.FAILED, JobState.CANCELLED, JobState.INTERRUPTED ->
                markRepeatIncomparable(
                    run,
                    "RUNNER_JOB_${job.state}_REPEAT",
                    job.resolvedCommitSha,
                    job.effectiveRecipeId,
                    job.effectiveVariantName,
                )
            else -> dao.upsertComparisonRun(
                run.copy(
                    repeatRunnerResolvedCommitSha = job.resolvedCommitSha,
                    repeatRunnerRecipeId = job.effectiveRecipeId,
                    repeatRunnerVariantName = job.effectiveVariantName,
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
        run: ComparisonRunEntity,
        resolvedCommitSha: String?,
        recipeId: String?,
        variantName: String?,
    ) {
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
        val repeatJobId = try {
            jobRepository.createRealTrustedJob(
                repositoryUrl = app.canonicalRepositoryUrl,
                revisionType = RevisionType.TAG,
                revisionValue = snapshot.tagName,
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
        run: ComparisonRunEntity,
        resolvedCommitSha: String?,
        recipeId: String?,
        variantName: String?,
    ) {
        val reference = dao.getReleaseAsset(run.referenceAssetId)
            ?: return markRepeatIncomparable(run, "REPEAT_REFERENCE_ASSET_MISSING", resolvedCommitSha, recipeId, variantName)
        val repeatJobId = run.repeatRunnerJobId
            ?: return markRepeatIncomparable(run, "REPEAT_RUNNER_JOB_MISSING", resolvedCommitSha, recipeId, variantName)
        val primaryArtifactId = run.localArtifactId
            ?: return markRepeatIncomparable(run, "PRIMARY_LOCAL_ARTIFACT_MISSING", resolvedCommitSha, recipeId, variantName)
        val primary = jobRepository.getArtifacts(run.runnerJobId).singleOrNull { it.artifactId == primaryArtifactId }
            ?: return markRepeatIncomparable(run, "PRIMARY_LOCAL_ARTIFACT_MISSING", resolvedCommitSha, recipeId, variantName)
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
        val officialOutcome = comparisonOutcome(officialRepeat.isMatch)
        val repeatabilityOutcome = comparisonOutcome(localRepeatability.isMatch)
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
                ),
            )
        }
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
            JobState.BUILDING.name,
            JobState.DISCOVERING_ARTIFACTS.name,
            JobState.SUCCEEDED.name,
        )
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
                    status = ComparisonRunStatus.COMPLETED.name,
                    outcome = ComparisonOutcome.INCOMPARABLE.name,
                    incomparableReason = reason,
                    updatedAt = now,
                    completedAt = now,
                ),
            )
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

    private suspend fun markRepeatIncomparable(
        run: ComparisonRunEntity,
        reason: String,
        resolvedCommitSha: String? = run.repeatRunnerResolvedCommitSha,
        recipeId: String? = run.repeatRunnerRecipeId,
        variantName: String? = run.repeatRunnerVariantName,
        artifactId: String? = run.repeatLocalArtifactId,
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

    private suspend fun downloadReference(assetId: String) {
        val asset = dao.getReleaseAsset(assetId) ?: error("Release asset was not found.")
        val snapshot = dao.getReleaseSnapshot(asset.releaseSnapshotId)
            ?: error("Release snapshot was not found.")
        val app = dao.getRegisteredApp(snapshot.registeredAppId)
            ?: error("Registered app was not found.")
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
        if (asset.providerSizeBytes > configuredLimit) {
            throw ReferenceAssetDownloadException(
                "CONFIGURED_APK_SIZE_LIMIT",
                "The selected APK exceeds the configured ${configuredLimit / (1024L * 1024L)} MiB limit.",
            )
        }
        val downloading = asset.copy(
            downloadStatus = ReferenceDownloadStatus.DOWNLOADING.name,
            downloadErrorCode = null,
            downloadErrorMessage = null,
        )
        dao.upsertReleaseAsset(downloading)
        val partFile = partFile(assetId)
        val finalFile = finalFile(assetId)
        partFile.delete()
        try {
            val downloaded = downloader.download(
                stableAssetUrl = asset.stableAssetUrl,
                expectedSizeBytes = asset.providerSizeBytes,
                expectedProviderSha256 = asset.providerDigestSha256,
                destinationPart = partFile,
            )
            val inspection = inspector.inspect(partFile)
            inspection.iconPng?.let { icon -> saveIcon(assetId, icon) }
            Files.move(
                partFile.toPath(),
                finalFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
            dao.upsertReleaseAsset(
                downloading.copy(
                    downloadStatus = ReferenceDownloadStatus.VERIFIED.name,
                    localContentPath = finalFile.absolutePath,
                    downloadedSizeBytes = downloaded.bytesWritten,
                    computedRawSha256 = downloaded.computedSha256,
                    responseEtag = downloaded.responseEtag,
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
                    comparisonEligibility = if (comparisonProfile == null) {
                        ComparisonEligibility.INCOMPARABLE.name
                    } else {
                        ComparisonEligibility.READY_FOR_COMPARISON.name
                    },
                    incomparableReason = if (comparisonProfile == null) {
                        COMPARISON_PROFILE_NOT_SUPPORTED_REASON
                    } else {
                        null
                    },
                    downloadedAt = Instant.now().toString(),
                ),
            )
        } catch (failure: Throwable) {
            partFile.delete()
            dao.upsertReleaseAsset(
                downloading.copy(
                    downloadStatus = ReferenceDownloadStatus.FAILED.name,
                    downloadErrorCode = failure.errorCode(),
                    downloadErrorMessage = failure.message ?: "Reference APK verification failed.",
                ),
            )
            throw failure
        }
    }

    private fun ResolvedGitHubRelease.toSnapshot(appId: String, snapshotId: String, now: String) =
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
            publishedAt = requireNotNull(release.publishedAt),
            fetchedAt = now,
            selectedProviderAssetId = selectedAsset.asset.id,
        )

    private fun ResolvedGitHubRelease.toAsset(snapshotId: String, assetId: String) = ReleaseAssetEntity(
        releaseAssetId = assetId,
        releaseSnapshotId = snapshotId,
        providerAssetId = selectedAsset.asset.id,
        assetName = selectedAsset.asset.name,
        stableAssetUrl = selectedAsset.asset.browserDownloadUrl,
        selectionReason = selectedAsset.reason,
        contentType = selectedAsset.asset.contentType,
        providerSizeBytes = selectedAsset.asset.size,
        providerDigestSha256 = selectedAsset.providerSha256,
    )

    private fun stableId(value: String): String =
        UUID.nameUUIDFromBytes(value.toByteArray(StandardCharsets.UTF_8)).toString()

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == value } ?: fallback

    private fun partFile(assetId: String) = File(referenceDirectory, "$assetId.part.apk")
    private fun finalFile(assetId: String) = File(referenceDirectory, "$assetId.apk")
    private fun iconFile(assetId: String) = File(iconDirectory, "$assetId.png")

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
        is ReleaseAssetSelectionException -> code
        is com.sanka1610.reprodroid.data.artifact.ReferenceAssetDownloadException -> code
        else -> "REFERENCE_APK_FAILED"
    }

    private companion object {
        const val PROVIDER_GITHUB_RELEASES = "PUBLIC_GITHUB_RELEASES"
        const val COMPARISON_PROFILE_NOT_SUPPORTED_REASON = "COMPARISON_PROFILE_NOT_SUPPORTED"
        const val MICROG_REPOSITORY = "https://github.com/morpheapp/microg-re"
        const val MICROG_RELEASE_TAG = "6.1.4"
        const val EXPECTED_BUILD_JAVA_MAJOR = 18
        const val REPEATED_BUILD_PROTOCOL_VERSION = 2
        const val EXPECTED_RELEASE_TASKS = "clean\n:play-services-core:assembleDefaultRelease"
        const val INSTALL_CALLBACK_GRACE_SECONDS = 30L
        val SUPPORTED_APK_LIMITS = setOf(
            64L * 1024L * 1024L,
            128L * 1024L * 1024L,
            256L * 1024L * 1024L,
            GlobalSettingsEntity.MAX_APK_SIZE_BYTES,
        )
    }
}
