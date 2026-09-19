package com.sanka1610.reprodroid.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.sanka1610.reprodroid.data.artifact.AdvancedApkComparator
import com.sanka1610.reprodroid.data.artifact.AdvancedApkComparison
import com.sanka1610.reprodroid.data.artifact.ApkComparisonException
import com.sanka1610.reprodroid.data.artifact.ApkContentComparator
import com.sanka1610.reprodroid.data.artifact.ExpectedApkFile
import com.sanka1610.reprodroid.data.local.AdvancedComparisonAxis
import com.sanka1610.reprodroid.data.local.AdvancedComparisonEntryEntity
import com.sanka1610.reprodroid.data.local.AdvancedComparisonSummaryEntity
import com.sanka1610.reprodroid.data.local.ApkEntryEvidenceEntity
import com.sanka1610.reprodroid.data.local.AppTrackingState
import com.sanka1610.reprodroid.data.local.ArtifactDownloadStatus
import com.sanka1610.reprodroid.data.local.ArtifactEntity
import com.sanka1610.reprodroid.data.local.ComparisonEligibility
import com.sanka1610.reprodroid.data.local.ComparisonEntryEntity
import com.sanka1610.reprodroid.data.local.ComparisonOutcome
import com.sanka1610.reprodroid.data.local.ComparisonRunEntity
import com.sanka1610.reprodroid.data.local.ComparisonRunStatus
import com.sanka1610.reprodroid.data.local.ManagementMode
import com.sanka1610.reprodroid.data.local.ReferenceDownloadStatus
import com.sanka1610.reprodroid.data.local.ReleaseAssetEntity
import com.sanka1610.reprodroid.data.local.ReleaseDiscoveryStatus
import com.sanka1610.reprodroid.data.local.ReproDroidDatabase
import com.sanka1610.reprodroid.data.local.SemanticDifferenceEvidenceEntity
import com.sanka1610.reprodroid.data.network.CreateGenericComparisonRequest
import com.sanka1610.reprodroid.data.network.GenericBuildAttempt
import com.sanka1610.reprodroid.data.network.JobState
import com.sanka1610.reprodroid.data.network.OfficialApkIdentity
import com.sanka1610.reprodroid.data.network.RawComparisonResult
import com.sanka1610.reprodroid.data.network.RevisionType
import com.sanka1610.reprodroid.data.storage.AndroidStorageManager
import com.sanka1610.reprodroid.data.storage.RunnerRetentionCoordinator
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

internal class AppComparisonCoordinator(
    private val context: Context,
    private val database: ReproDroidDatabase,
    private val jobRepository: JobRepository,
    private val storageManager: AndroidStorageManager,
    private val retentionCoordinator: RunnerRetentionCoordinator?,
    private val comparator: ApkContentComparator,
    private val advancedComparator: AdvancedApkComparator,
) {
    private val dao = database.managedAppDao()
    private val referenceDirectory = File(context.filesDir, "reference-apks")
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

    private companion object {
        const val EXPECTED_BUILD_JAVA_MAJOR = 18
        const val REPEATED_BUILD_PROTOCOL_VERSION = 2
        const val EXPECTED_RELEASE_TASKS = "clean\n:play-services-core:assembleDefaultRelease"
    }
}
