package com.sanka1610.reprodroid.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.sanka1610.reprodroid.data.artifact.ApkInspector
import com.sanka1610.reprodroid.data.artifact.ApkComparisonException
import com.sanka1610.reprodroid.data.artifact.ApkContentComparator
import com.sanka1610.reprodroid.data.artifact.ExpectedApkFile
import com.sanka1610.reprodroid.data.artifact.GitHubAssetDownloader
import com.sanka1610.reprodroid.data.local.ArtifactDownloadStatus
import com.sanka1610.reprodroid.data.local.ComparisonEntryEntity
import com.sanka1610.reprodroid.data.local.ComparisonOutcome
import com.sanka1610.reprodroid.data.local.ComparisonEligibility
import com.sanka1610.reprodroid.data.local.ComparisonRunEntity
import com.sanka1610.reprodroid.data.local.ComparisonRunStatus
import com.sanka1610.reprodroid.data.local.ManagedAppDao
import com.sanka1610.reprodroid.data.local.ManagementMode
import com.sanka1610.reprodroid.data.local.PreferredAbi
import com.sanka1610.reprodroid.data.local.ReferenceDownloadStatus
import com.sanka1610.reprodroid.data.local.RegisteredAppEntity
import com.sanka1610.reprodroid.data.local.RegisteredAppRecord
import com.sanka1610.reprodroid.data.local.ReleaseAssetEntity
import com.sanka1610.reprodroid.data.local.ReleaseDiscoveryStatus
import com.sanka1610.reprodroid.data.local.ReleaseSnapshotEntity
import com.sanka1610.reprodroid.data.local.ReleaseVariantPreference
import com.sanka1610.reprodroid.data.local.ReproDroidDatabase
import com.sanka1610.reprodroid.data.provider.GitHubProviderException
import com.sanka1610.reprodroid.data.provider.GitHubReleasesClient
import com.sanka1610.reprodroid.data.provider.ResolvedGitHubRelease
import com.sanka1610.reprodroid.data.provider.ReleaseAssetSelectionException
import com.sanka1610.reprodroid.data.network.JobState
import com.sanka1610.reprodroid.data.network.RevisionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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
    private val referenceDirectory = File(context.filesDir, "reference-apks")
    private val iconDirectory = File(context.filesDir, "reference-icons")

    fun observeApps(): Flow<List<RegisteredAppRecord>> = dao.observeRegisteredApps()

    fun observeApp(registeredAppId: String): Flow<RegisteredAppRecord?> =
        dao.observeRegisteredApp(registeredAppId)

    suspend fun previewLatest(repositoryUrl: String): ResolvedGitHubRelease =
        provider.resolveLatestRelease(repositoryUrl)

    suspend fun registerAndDownload(
        preview: ResolvedGitHubRelease,
        mode: ManagementMode,
    ): String {
        if (dao.getRegisteredAppByCanonicalUrl(preview.repository.canonicalUrl) != null) {
            throw IllegalStateException("This GitHub repository is already registered.")
        }
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
        downloadReference(assetId)
        return appId
    }

    suspend fun refresh(registeredAppId: String) {
        val app = dao.getRegisteredApp(registeredAppId)
            ?: throw IllegalArgumentException("Registered app was not found.")
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
                    preferredAbi = enumValueOrDefault(app.preferredAbi, PreferredAbi.ARM64_V8A),
                    preferredVariant = enumValueOrDefault(
                        app.releaseVariantPreference,
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
        releaseVariant: ReleaseVariantPreference,
        preferredAbi: PreferredAbi,
    ) {
        val app = dao.getRegisteredApp(registeredAppId)
            ?: throw IllegalArgumentException("Registered app was not found.")
        dao.upsertRegisteredApp(
            app.copy(
                releaseVariantPreference = releaseVariant.name,
                preferredAbi = preferredAbi.name,
                releaseMetadataEtag = null,
                updatedAt = Instant.now().toString(),
            ),
        )
    }

    suspend fun startComparison(registeredAppId: String): String {
        val record = dao.getRegisteredAppRecord(registeredAppId)
            ?: throw IllegalArgumentException("Registered app was not found.")
        check(record.app.managementMode == ManagementMode.VERIFICATION.name) {
            "Only apps in verification mode can start a reproducibility comparison."
        }
        val release = record.latestRelease ?: error("No resolved release is available.")
        val asset = release.selectedAsset ?: error("No selected release APK is available.")
        check(asset.downloadStatus == ReferenceDownloadStatus.VERIFIED.name) {
            "The official reference APK must be verified before comparison."
        }
        val profile = requireComparisonProfile(record.app.canonicalRepositoryUrl, release.snapshot.tagName)
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
        check(run.status == ComparisonRunStatus.AWAITING_CONFIRMATION.name) {
            "The comparison build is not awaiting confirmation."
        }
        val job = jobRepository.getJob(run.runnerJobId) ?: error("Runner Job is missing locally.")
        val resolvedCommit = job.resolvedCommitSha ?: error("Runner has not resolved the comparison commit.")
        check(resolvedCommit == run.expectedCommitSha) {
            "Runner resolved a different commit; build confirmation is blocked."
        }
        jobRepository.confirmRealBuild(run.runnerJobId, resolvedCommit)
        dao.upsertComparisonRun(
            run.copy(
                status = ComparisonRunStatus.BUILDING.name,
                updatedAt = Instant.now().toString(),
            ),
        )
    }

    suspend fun refreshComparison(comparisonRunId: String) {
        val run = dao.getComparisonRun(comparisonRunId)
            ?: throw IllegalArgumentException("Comparison run was not found.")
        if (run.status == ComparisonRunStatus.COMPLETED.name) return
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
        val identityMismatch = when {
            reference.packageName.isNullOrBlank() || local.packageName.isBlank() -> "PACKAGE_METADATA_MISSING"
            reference.packageName != local.packageName -> "PACKAGE_MISMATCH"
            reference.versionName.isNullOrBlank() || local.versionName.isBlank() -> "VERSION_NAME_MISSING"
            reference.versionName != local.versionName -> "VERSION_NAME_MISMATCH"
            reference.versionCode == null || local.versionCode <= 0 -> "VERSION_CODE_MISSING"
            reference.versionCode != local.versionCode -> "VERSION_CODE_MISMATCH"
            else -> null
        }
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
                run.copy(
                    localArtifactId = local.artifactId,
                    runnerResolvedCommitSha = resolvedCommitSha,
                    runnerRecipeId = recipeId,
                    runnerVariantName = variantName,
                    status = ComparisonRunStatus.COMPLETED.name,
                    outcome = outcome.name,
                    incomparableReason = null,
                    updatedAt = now,
                    completedAt = now,
                ),
            )
            dao.upsertReleaseAsset(
                reference.copy(
                    comparisonEligibility = ComparisonEligibility.READY_FOR_COMPARISON.name,
                    incomparableReason = null,
                ),
            )
        }
    }

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

    private fun requireComparisonProfile(repositoryUrl: String, tagName: String): ComparisonProfile {
        val canonical = repositoryUrl.removeSuffix(".git").trimEnd('/').lowercase()
        if (canonical != MICROG_REPOSITORY || tagName != MICROG_RELEASE_TAG) {
            throw IllegalStateException("Phase 2B permits only the fixed MicroG-RE 6.1.4 comparison profile.")
        }
        return ComparisonProfile(
            recipeId = "morpheapp-microg-re-6.1.4-default-release",
            variantName = "defaultRelease",
        )
    }

    private data class ComparisonProfile(val recipeId: String, val variantName: String)

    private suspend fun downloadReference(assetId: String) {
        val asset = dao.getReleaseAsset(assetId) ?: error("Release asset was not found.")
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
                    comparisonEligibility = ComparisonEligibility.INCOMPARABLE.name,
                    incomparableReason = PHASE_2B_REQUIRED_REASON,
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

    private fun Throwable.errorCode(): String = when (this) {
        is GitHubProviderException -> code
        is ReleaseAssetSelectionException -> code
        is com.sanka1610.reprodroid.data.artifact.ReferenceAssetDownloadException -> code
        else -> "REFERENCE_APK_FAILED"
    }

    private companion object {
        const val PROVIDER_GITHUB_RELEASES = "PUBLIC_GITHUB_RELEASES"
        const val PHASE_2B_REQUIRED_REASON = "RELEASE_BUILD_RECIPE_DEFERRED_TO_PHASE_2B"
        const val MICROG_REPOSITORY = "https://github.com/morpheapp/microg-re"
        const val MICROG_RELEASE_TAG = "6.1.4"
        const val EXPECTED_BUILD_JAVA_MAJOR = 18
        const val EXPECTED_RELEASE_TASKS = "clean\n:play-services-core:assembleDefaultRelease"
    }
}
