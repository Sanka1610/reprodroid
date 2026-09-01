package com.sanka1610.reprodroid.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.sanka1610.reprodroid.data.artifact.ApkInspector
import com.sanka1610.reprodroid.data.artifact.ApkInstaller
import com.sanka1610.reprodroid.data.local.ArtifactDownloadStatus
import com.sanka1610.reprodroid.data.local.ArtifactEntity
import com.sanka1610.reprodroid.data.local.BuildEnvironmentManifestWithDependencies
import com.sanka1610.reprodroid.data.local.InstallAttemptStatus
import com.sanka1610.reprodroid.data.local.JobEntity
import com.sanka1610.reprodroid.data.local.JobRecord
import com.sanka1610.reprodroid.data.local.LogEntity
import com.sanka1610.reprodroid.data.local.ReproDroidDatabase
import com.sanka1610.reprodroid.data.local.SourceScanWithDetails
import com.sanka1610.reprodroid.data.network.CreateJobRequest
import com.sanka1610.reprodroid.data.network.ContinueSourceScanRequest
import com.sanka1610.reprodroid.data.network.ConfirmJobRequest
import com.sanka1610.reprodroid.data.network.ExecutionMode
import com.sanka1610.reprodroid.data.network.JobResponse
import com.sanka1610.reprodroid.data.network.JobState
import com.sanka1610.reprodroid.data.network.LogResponse
import com.sanka1610.reprodroid.data.network.RequestedRevision
import com.sanka1610.reprodroid.data.network.RevisionType
import com.sanka1610.reprodroid.data.network.RunnerApiClient
import com.sanka1610.reprodroid.data.network.RunnerApiException
import com.sanka1610.reprodroid.data.network.RunnerResponseIntegrityException
import com.sanka1610.reprodroid.data.network.SimulationOutcome
import com.sanka1610.reprodroid.data.network.SourceScanStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Instant

class JobRepository(
    applicationContext: Context,
    private val database: ReproDroidDatabase,
    private val runnerApi: RunnerApiClient,
) {
    private val jobDao = database.jobDao()
    private val syncMutex = Mutex()
    private val filesDirectory = applicationContext.filesDir.toPath().toAbsolutePath().normalize()
    private val packageInstaller = applicationContext.packageManager.packageInstaller
    private val apkInspector = ApkInspector(applicationContext.packageManager)
    private val apkInstaller = ApkInstaller(applicationContext, jobDao)
    private val _buildManifestWarnings = MutableStateFlow<Map<String, BuildManifestWarning>>(emptyMap())
    private val _sourceScanWarnings = MutableStateFlow<Map<String, SourceScanWarning>>(emptyMap())

    val buildManifestWarnings = _buildManifestWarnings.asStateFlow()
    private val _sandboxWarnings = MutableStateFlow<Map<String, String>>(emptyMap())
    val sandboxWarnings = _sandboxWarnings.asStateFlow()
    val sourceScanWarnings = _sourceScanWarnings.asStateFlow()

    fun observeJobs(): Flow<List<JobRecord>> = jobDao.observeJobs()

    fun observeBuildEnvironmentManifests(): Flow<List<BuildEnvironmentManifestWithDependencies>> =
        jobDao.observeBuildEnvironmentManifests()

    fun observeSourceScans(): Flow<List<SourceScanWithDetails>> = jobDao.observeSourceScans()

    suspend fun getJob(jobId: String): JobEntity? = jobDao.getJob(jobId)

    suspend fun getArtifacts(jobId: String): List<ArtifactEntity> = jobDao.getArtifacts(jobId)

    suspend fun getBuildEnvironmentManifest(jobId: String): BuildEnvironmentManifestWithDependencies? =
        jobDao.getBuildEnvironmentManifest(jobId)

    suspend fun getSourceScan(jobId: String): SourceScanWithDetails? = jobDao.getSourceScan(jobId)

    suspend fun createSimulatedJob(
        repositoryUrl: String,
        revisionType: RevisionType,
        revisionValue: String,
        outcome: SimulationOutcome,
    ): String {
        rejectLegacyExecutionMutation()
        val request = CreateJobRequest(
            executionMode = ExecutionMode.SIMULATED,
            repositoryUrl = repositoryUrl,
            revision = RequestedRevision(revisionType, revisionValue),
            simulationOutcome = outcome,
        )
        return createJob(request, outcome)
    }

    suspend fun createRealTrustedJob(
        repositoryUrl: String,
        revisionType: RevisionType,
        revisionValue: String,
    ): String {
        rejectLegacyExecutionMutation()
        return createJob(
            request = CreateJobRequest(
                executionMode = ExecutionMode.REAL_TRUSTED,
                repositoryUrl = repositoryUrl,
                revision = RequestedRevision(revisionType, revisionValue),
            ),
            outcome = null,
        )
    }

    private suspend fun createJob(
        request: CreateJobRequest,
        outcome: SimulationOutcome?,
    ): String {
        val created = runnerApi.createJob(request)
        val now = Instant.now().toString()
        syncMutex.withLock {
            jobDao.upsertJob(
                JobEntity(
                    jobId = created.jobId,
                    executionMode = request.executionMode.name,
                    repositoryUrl = request.repositoryUrl,
                    revisionType = request.revision.type.name,
                    revisionValue = request.revision.value,
                    simulationOutcome = outcome?.name,
                    state = created.state.name,
                    progressPercent = 0,
                    latestLogSequence = 0,
                    errorCode = null,
                    errorMessage = null,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            syncJobLocked(created.jobId)
        }
        return created.jobId
    }

    suspend fun syncActiveJobs() {
        var firstFailure: Throwable? = null
        jobDao.getActiveJobIds().forEach { jobId ->
            try {
                syncJob(jobId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: RunnerApiException) {
                if (failure.statusCode == 404 && failure.errorCode == "JOB_NOT_FOUND") {
                    markRemoteJobMissing(jobId, failure)
                } else if (firstFailure == null) {
                    firstFailure = failure
                }
            } catch (failure: Throwable) {
                if (firstFailure == null) firstFailure = failure
            }
        }
        firstFailure?.let { throw it }
    }

    private suspend fun markRemoteJobMissing(jobId: String, failure: RunnerApiException) {
        syncMutex.withLock {
            val existing = jobDao.getJob(jobId) ?: return@withLock
            val state = runCatching { JobState.valueOf(existing.state) }.getOrNull()
            if (state?.isTerminal == true) return@withLock
            jobDao.upsertJob(
                existing.copy(
                    state = JobState.INTERRUPTED.name,
                    errorCode = failure.errorCode,
                    errorMessage =
                        "Runner no longer has this job. Local polling stopped; retry it as a new job if needed.",
                    updatedAt = Instant.now().toString(),
                ),
            )
        }
    }

    suspend fun syncJob(jobId: String) = syncMutex.withLock {
        syncJobLocked(jobId)
    }

    private suspend fun syncJobLocked(jobId: String) {
        val existing = jobDao.getJob(jobId)
        val existingArtifacts = jobDao.getArtifacts(jobId).associateBy(ArtifactEntity::artifactId)
        val remote = verifiedRemoteJob(jobId, existing)
        validateSourceScanSummary(remote)
        var afterSequence = existing?.latestLogSequence ?: 0L
        val newLogs = mutableListOf<LogEntity>()
        var hasMore: Boolean
        do {
            val requestedAfterSequence = afterSequence
            val logPage = runnerApi.getLogs(jobId, afterSequence)
            validateLogPage(requestedAfterSequence, logPage)
            newLogs += logPage.entries.map { entry ->
                LogEntity(
                    jobId = jobId,
                    sequence = entry.sequence,
                    timestamp = entry.timestamp,
                    level = entry.level.name,
                    message = entry.message,
                )
            }
            afterSequence = logPage.nextAfterSequence
            hasMore = logPage.hasMore
        } while (hasMore)

        try {
            database.withTransaction {
                jobDao.upsertJob(remote.toJobEntity(existing, afterSequence))
                jobDao.deleteArtifacts(jobId)
                if (remote.artifacts.isNotEmpty()) {
                    jobDao.upsertArtifacts(
                        remote.artifacts.map { artifact -> artifact.toEntity(jobId, existingArtifacts[artifact.artifactId]) },
                    )
                }
                if (newLogs.isNotEmpty()) jobDao.upsertLogs(newLogs)
            }
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Exception) {
            _sandboxWarnings.value += jobId to "Sandbox state could not be stored; the previous transaction is retained. Refresh before acknowledging."
            throw failure
        }
        _sandboxWarnings.value -= jobId
        if (remote.executionMode == ExecutionMode.REAL_TRUSTED && remote.state == JobState.SUCCEEDED) {
            fetchAndStoreBuildEnvironmentManifest(remote)
        }
        if (remote.sourceScan?.status == SourceScanStatus.COMPLETED) {
            fetchAndStoreSourceScan(remote)
        }
    }

    private suspend fun fetchAndStoreSourceScan(remote: JobResponse) {
        try {
            val response = runnerApi.getSourceScan(remote.jobId)
            val validated = validateSourceScanDetail(
                jobId = remote.jobId,
                remoteJob = remote,
                response = response,
                retrievedAt = Instant.now().toString(),
            )
            database.withTransaction {
                jobDao.replaceSourceScan(validated.scan, validated.detectorCounts, validated.findings)
            }
            _sourceScanWarnings.value -= remote.jobId
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: RunnerApiException) {
            setSourceScanWarning(remote.jobId, failure.errorCode)
        } catch (_: RunnerResponseIntegrityException) {
            setSourceScanWarning(remote.jobId, "SOURCE_SCAN_RESPONSE_INVALID")
        } catch (_: IllegalStateException) {
            setSourceScanWarning(remote.jobId, "SOURCE_SCAN_RESPONSE_INVALID")
        } catch (_: IllegalArgumentException) {
            setSourceScanWarning(remote.jobId, "SOURCE_SCAN_RESPONSE_INVALID")
        } catch (_: Exception) {
            setSourceScanWarning(remote.jobId, "SOURCE_SCAN_STORAGE_FAILED")
        }
    }

    private fun setSourceScanWarning(jobId: String, code: String) {
        _sourceScanWarnings.value += jobId to SourceScanWarning(
            code = code,
            message = "Source scan evidence is unavailable ($code). Comparison and trust are unchanged.",
        )
    }

    private suspend fun fetchAndStoreBuildEnvironmentManifest(remote: JobResponse) {
        try {
            val response = runnerApi.getBuildEnvironmentManifest(remote.jobId)
            val validated = validateBuildEnvironmentManifest(
                jobId = remote.jobId,
                remoteJob = remote,
                response = response,
                retrievedAt = Instant.now().toString(),
            )
            database.withTransaction {
                val previous = jobDao.getBuildEnvironmentManifest(remote.jobId)
                check(previous?.manifest?.schemaVersion != 3 || response.schemaVersion == 3) { "Sandbox Manifest schema downgrade rejected." }
                jobDao.replaceBuildEnvironmentManifest(validated.manifest, validated.dependencies)
            }
            _buildManifestWarnings.value -= remote.jobId
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: RunnerApiException) {
            setBuildManifestWarning(remote.jobId, failure.errorCode)
        } catch (_: RunnerResponseIntegrityException) {
            setBuildManifestWarning(remote.jobId, "BUILD_MANIFEST_RESPONSE_INVALID")
        } catch (_: IllegalStateException) {
            setBuildManifestWarning(remote.jobId, "BUILD_MANIFEST_RESPONSE_INVALID")
        } catch (_: Exception) {
            setBuildManifestWarning(remote.jobId, "BUILD_MANIFEST_STORAGE_FAILED")
        }
    }

    private fun setBuildManifestWarning(jobId: String, code: String) {
        _buildManifestWarnings.value += jobId to BuildManifestWarning(
            code = code,
            message = "Build environment manifest is unavailable ($code). Comparison and trust are unchanged.",
        )
    }

    suspend fun cancelJob(jobId: String) {
        syncMutex.withLock {
            runnerApi.cancelJob(jobId)
            syncJobLocked(jobId)
        }
    }

    suspend fun confirmRealBuild(jobId: String, resolvedCommitSha: String) {
        rejectLegacyExecutionMutation()
        syncMutex.withLock {
            verifiedRemoteJob(jobId, jobDao.getJob(jobId))
            runnerApi.confirmJob(
                jobId,
                ConfirmJobRequest(
                    resolvedCommitSha = resolvedCommitSha,
                    riskAcknowledged = true,
                ),
            )
            syncJobLocked(jobId)
        }
    }

    suspend fun continueSourceScan(jobId: String, scanResultSha256: String) {
        syncMutex.withLock {
            verifiedRemoteJob(jobId, jobDao.getJob(jobId))
            val scan = requireNotNull(jobDao.getSourceScan(jobId)) { "Source scan evidence is not available locally." }
            check(scan.scan.resultSha256 == scanResultSha256 && scan.scan.requiresReview && !scan.scan.reviewed) {
                "The stored source scan is not awaiting review for this digest."
            }
            runnerApi.continueSourceScan(
                jobId,
                ContinueSourceScanRequest(
                    scanResultSha256 = scanResultSha256,
                    riskAcknowledged = true,
                ),
            )
            syncJobLocked(jobId)
        }
    }

    suspend fun retryJob(jobId: String): String {
        rejectLegacyExecutionMutation()
        return syncMutex.withLock {
            val original = requireNotNull(jobDao.getJob(jobId)) { "The local job does not exist." }
            val created = runnerApi.retryJob(jobId)
            val now = Instant.now().toString()
            jobDao.upsertJob(
                original.copy(
                    jobId = created.jobId,
                    state = created.state.name,
                    progressPercent = 0,
                    latestLogSequence = 0,
                    errorCode = null,
                    errorMessage = null,
                    resolvedCommitSha = null,
                    requiresConfirmation = false,
                    effectiveBuildRoot = null,
                    effectiveBuildTasks = null,
                    effectiveRecipeId = null,
                    effectiveVariantName = null,
                    effectiveJavaMajor = null,
                    createdAt = now,
                    updatedAt = now,
                    downloadResult = null,
                    installResult = null,
                ),
            )
            syncJobLocked(created.jobId)
            created.jobId
        }
    }

    private fun rejectLegacyExecutionMutation(): Nothing = throw IllegalStateException(
        "Phase 4 execution requires Runner API v2. New jobs, confirmation, and retry do not fall back to API v1.",
    )

    private suspend fun verifiedRemoteJob(jobId: String, existing: JobEntity?): JobResponse {
        try {
            return runnerApi.getJob(jobId).also { validateSandboxRefresh(existing, it) }
        } catch (failure: CancellationException) { throw failure } catch (failure: RunnerApiException) { throw failure }
        catch (_: Exception) {
            _sandboxWarnings.value += jobId to "Sandbox response unavailable or invalid; the last valid state is retained. Refresh before acknowledging."
            throw RunnerResponseIntegrityException("Sandbox response could not be validated.")
        }
    }

    suspend fun downloadArtifact(jobId: String, artifactId: String) =
        downloadArtifact(jobId, artifactId, requireSigningCertificate = true)

    suspend fun downloadArtifactForComparison(jobId: String, artifactId: String) =
        downloadArtifact(jobId, artifactId, requireSigningCertificate = false)

    private suspend fun downloadArtifact(
        jobId: String,
        artifactId: String,
        requireSigningCertificate: Boolean,
    ) = syncMutex.withLock {
        val job = requireNotNull(jobDao.getJob(jobId)) { "The local job does not exist." }
        val artifact = requireNotNull(jobDao.getArtifact(jobId, artifactId)) { "The APK artifact does not exist." }
        check(job.executionMode == ExecutionMode.REAL_TRUSTED.name && job.state == "SUCCEEDED") {
            "Only a succeeded trusted real build can transfer APK content."
        }
        check(artifact.sizeBytes > 0 && SHA256.matches(artifact.sha256)) {
            "Runner returned invalid APK size or SHA-256 metadata."
        }

        val relativeDirectory = "apks/${safeStorageName(jobId)}"
        val artifactStorageName = safeStorageName(artifactId)
        val finalRelativePath = "$relativeDirectory/$artifactStorageName.apk"
        val finalPath = filesDirectory.resolve(finalRelativePath).normalize()
        val temporaryPath = filesDirectory.resolve("$relativeDirectory/$artifactStorageName.part.apk").normalize()
        check(finalPath.startsWith(filesDirectory) && temporaryPath.startsWith(filesDirectory)) {
            "The APK storage path escaped app-private storage."
        }
        withContext(Dispatchers.IO) {
            Files.createDirectories(finalPath.parent)
            Files.deleteIfExists(temporaryPath)
        }
        jobDao.upsertArtifacts(
            listOf(
                artifact.copy(
                    downloadStatus = ArtifactDownloadStatus.DOWNLOADING.name,
                    downloadError = null,
                ),
            ),
        )

        try {
            val response = withContext(Dispatchers.IO) {
                runnerApi.downloadArtifact(jobId, artifactId, temporaryPath.toFile())
            }
            check(response.contentType?.substringBefore(';') == APK_CONTENT_TYPE) {
                "Runner returned an unexpected artifact content type."
            }
            check(response.contentLength == artifact.sizeBytes && response.bytesWritten == artifact.sizeBytes) {
                "Downloaded APK size does not match Runner metadata."
            }
            check(response.etag?.removePrefix("W/")?.trim()?.trim('"')?.lowercase() == artifact.sha256) {
                "Runner artifact ETag does not match its registered SHA-256."
            }
            val downloadedSha256 = withContext(Dispatchers.IO) { sha256(temporaryPath.toFile()) }
            check(downloadedSha256 == artifact.sha256) {
                "Downloaded APK SHA-256 does not match Runner metadata."
            }
            val inspection = withContext(Dispatchers.IO) {
                apkInspector.inspect(
                    apkFile = temporaryPath.toFile(),
                    requireSigningCertificate = requireSigningCertificate,
                )
            }
            withContext(Dispatchers.IO) { moveVerifiedArtifact(temporaryPath.toFile(), finalPath.toFile()) }
            jobDao.upsertArtifacts(
                listOf(
                    artifact.copy(
                        packageName = inspection.packageName,
                        versionName = inspection.versionName,
                        versionCode = inspection.versionCode,
                        downloadStatus = ArtifactDownloadStatus.VERIFIED.name,
                        downloadError = null,
                        localContentPath = finalRelativePath,
                        downloadedSizeBytes = response.bytesWritten,
                        downloadedSha256 = downloadedSha256,
                        signingCertificateSha256 = inspection.signingCertificateSha256.joinToString("\n"),
                        currentSignerSha256 = inspection.currentSignerSha256.joinToString("\n"),
                        existingInstallStatus = inspection.existingInstallStatus?.name,
                        installedVersionName = inspection.installedVersionName,
                        installedVersionCode = inspection.installedVersionCode,
                        downloadedAt = Instant.now().toString(),
                    ),
                ),
            )
        } catch (failure: Throwable) {
            withContext(Dispatchers.IO) {
                Files.deleteIfExists(temporaryPath)
                Files.deleteIfExists(finalPath)
            }
            jobDao.upsertArtifacts(
                listOf(
                    artifact.copy(
                        downloadStatus = ArtifactDownloadStatus.FAILED.name,
                        downloadError = failure.message ?: "APK download or verification failed.",
                        localContentPath = null,
                        downloadedSizeBytes = null,
                        downloadedSha256 = null,
                        signingCertificateSha256 = null,
                        currentSignerSha256 = null,
                        existingInstallStatus = null,
                        installedVersionName = null,
                        installedVersionCode = null,
                        downloadedAt = null,
                    ),
                ),
            )
            if (failure is CancellationException) throw failure
            throw failure
        }
    }

    suspend fun installArtifact(jobId: String, artifactId: String): String {
        val artifact = requireNotNull(jobDao.getArtifact(jobId, artifactId)) { "The APK artifact does not exist." }
        check(
            !artifact.signingCertificateSha256.isNullOrBlank() &&
                !artifact.currentSignerSha256.isNullOrBlank(),
        ) {
            "Only an APK with verified signing certificate information can be installed."
        }
        return apkInstaller.install(jobId, artifact)
    }

    suspend fun recordInstallStatus(
        attemptId: String,
        status: InstallAttemptStatus,
        packageInstallerStatus: Int,
        statusMessage: String?,
    ) {
        val attempt = jobDao.getInstallAttempt(attemptId) ?: return
        jobDao.upsertInstallAttempt(
            attempt.copy(
                status = status.name,
                packageInstallerStatus = packageInstallerStatus,
                statusMessage = statusMessage,
                updatedAt = Instant.now().toString(),
            ),
        )
    }

    suspend fun recoverOrphanedInstallAttempts() {
        val now = Instant.now()
        val activeSessionIds = packageInstaller.mySessions.mapTo(mutableSetOf()) { it.sessionId }
        jobDao.getPendingInstallAttempts()
            .filter { attempt ->
                val stale = runCatching {
                    Instant.parse(attempt.updatedAt).plusSeconds(INSTALL_CALLBACK_GRACE_SECONDS) <= now
                }.getOrDefault(false)
                stale && attempt.packageInstallerSessionId !in activeSessionIds
            }
            .forEach { attempt ->
                jobDao.upsertInstallAttempt(
                    attempt.copy(
                        status = InstallAttemptStatus.FAILED.name,
                        statusMessage =
                            "The PackageInstaller session is no longer active, but no terminal callback was received.",
                        updatedAt = now.toString(),
                    ),
                )
            }
    }

    private fun com.sanka1610.reprodroid.data.network.ArtifactMetadata.toEntity(
        jobId: String,
        existing: ArtifactEntity?,
    ): ArtifactEntity {
        val contentIsUnchanged = existing != null &&
            existing.fileName == fileName && existing.sizeBytes == sizeBytes && existing.sha256 == sha256
        return if (contentIsUnchanged) {
            existing.copy(
                jobId = jobId,
                fileName = fileName,
                sizeBytes = sizeBytes,
                sha256 = sha256,
            )
        } else {
            ArtifactEntity(
                artifactId = artifactId,
                jobId = jobId,
                fileName = fileName,
                sizeBytes = sizeBytes,
                sha256 = sha256,
                packageName = packageName,
                versionName = versionName,
                versionCode = versionCode,
            )
        }
    }

    private fun validateLogPage(
        requestedAfterSequence: Long,
        logPage: LogResponse,
    ) {
        var previousSequence = requestedAfterSequence
        logPage.entries.forEach { entry ->
            check(entry.sequence > previousSequence) { "Runner returned unordered or duplicate log sequences." }
            previousSequence = entry.sequence
        }
        val expectedCursor = logPage.entries.lastOrNull()?.sequence ?: requestedAfterSequence
        check(logPage.nextAfterSequence == expectedCursor) { "Runner returned an inconsistent log cursor." }
        check(!logPage.hasMore || expectedCursor > requestedAfterSequence) {
            "Runner returned a non-advancing log cursor."
        }
    }

    private fun safeStorageName(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1_024)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private fun moveVerifiedArtifact(temporaryFile: File, finalFile: File) {
        try {
            Files.move(
                temporaryFile.toPath(),
                finalFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporaryFile.toPath(), finalFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private companion object {
        val SHA256 = Regex("[0-9a-f]{64}")
        const val APK_CONTENT_TYPE = "application/vnd.android.package-archive"
        const val INSTALL_CALLBACK_GRACE_SECONDS = 30L
    }
}

internal fun JobResponse.toJobEntity(existing: JobEntity?, logCursor: Long): JobEntity = JobEntity(
    jobId = jobId,
    executionMode = executionMode.name,
    repositoryUrl = repositoryUrl,
    revisionType = requestedRevision.type.name,
    revisionValue = requestedRevision.value,
    simulationOutcome = existing?.simulationOutcome,
    resolvedCommitSha = resolvedCommitSha,
    requiresConfirmation = requiresConfirmation,
    effectiveRecipeId = effectiveBuild?.recipeId,
    effectiveVariantName = effectiveBuild?.variantName,
    effectiveBuildRoot = effectiveBuild?.buildRoot,
    effectiveJavaMajor = effectiveBuild?.javaMajor,
    effectiveBuildTasks = effectiveBuild?.tasks?.joinToString("\n"),
    effectiveDependencyPinning = effectiveBuild?.dependencyPinning?.name ?: "NONE",
    effectiveSourceDateEpoch = effectiveBuild?.determinism?.sourceDateEpoch,
    effectiveNoBuildCache = effectiveBuild?.determinism?.noBuildCache ?: false,
    effectiveFixedLocale = effectiveBuild?.determinism?.fixedLocale?.value,
    state = state.name,
    progressPercent = progressPercent,
    latestLogSequence = maxOf(logCursor, existing?.latestLogSequence ?: 0L),
    errorCode = error?.code,
    errorMessage = error?.message,
    createdAt = createdAt,
    updatedAt = updatedAt,
    downloadResult = existing?.downloadResult,
    installResult = existing?.installResult,
    sandboxMode = sandbox?.mode?.name,
    sandboxOrigin = sandbox?.origin?.name,
    sandboxProfileId = sandbox?.profileId,
    sandboxCleanupStatus = sandbox?.cleanupStatus?.name,
    sandboxResponseSeen = true,
)
