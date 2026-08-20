package com.sanka1610.reprodroid.data.repository

import androidx.room.withTransaction
import com.sanka1610.reprodroid.data.local.ArtifactEntity
import com.sanka1610.reprodroid.data.local.JobEntity
import com.sanka1610.reprodroid.data.local.JobRecord
import com.sanka1610.reprodroid.data.local.LogEntity
import com.sanka1610.reprodroid.data.local.ReproDroidDatabase
import com.sanka1610.reprodroid.data.network.CreateJobRequest
import com.sanka1610.reprodroid.data.network.ConfirmJobRequest
import com.sanka1610.reprodroid.data.network.ExecutionMode
import com.sanka1610.reprodroid.data.network.JobResponse
import com.sanka1610.reprodroid.data.network.LogResponse
import com.sanka1610.reprodroid.data.network.RequestedRevision
import com.sanka1610.reprodroid.data.network.RevisionType
import com.sanka1610.reprodroid.data.network.RunnerApiClient
import com.sanka1610.reprodroid.data.network.SimulationOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

class JobRepository(
    private val database: ReproDroidDatabase,
    private val runnerApi: RunnerApiClient,
) {
    private val jobDao = database.jobDao()
    private val syncMutex = Mutex()

    fun observeJobs(): Flow<List<JobRecord>> = jobDao.observeJobs()

    suspend fun createSimulatedJob(
        repositoryUrl: String,
        revisionType: RevisionType,
        revisionValue: String,
        outcome: SimulationOutcome,
    ): String {
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
    ): String = createJob(
        request = CreateJobRequest(
            executionMode = ExecutionMode.REAL_TRUSTED,
            repositoryUrl = repositoryUrl,
            revision = RequestedRevision(revisionType, revisionValue),
        ),
        outcome = null,
    )

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
            } catch (failure: Throwable) {
                if (firstFailure == null) firstFailure = failure
            }
        }
        firstFailure?.let { throw it }
    }

    suspend fun syncJob(jobId: String) = syncMutex.withLock {
        syncJobLocked(jobId)
    }

    private suspend fun syncJobLocked(jobId: String) {
        val existing = jobDao.getJob(jobId)
        val remote = runnerApi.getJob(jobId)
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

        database.withTransaction {
            jobDao.upsertJob(remote.toEntity(existing, afterSequence))
            jobDao.deleteArtifacts(jobId)
            if (remote.artifacts.isNotEmpty()) {
                jobDao.upsertArtifacts(
                    remote.artifacts.map { artifact ->
                        ArtifactEntity(
                            artifactId = artifact.artifactId,
                            jobId = jobId,
                            fileName = artifact.fileName,
                            sizeBytes = artifact.sizeBytes,
                            sha256 = artifact.sha256,
                            packageName = artifact.packageName,
                            versionName = artifact.versionName,
                            versionCode = artifact.versionCode,
                        )
                    },
                )
            }
            if (newLogs.isNotEmpty()) jobDao.upsertLogs(newLogs)
        }
    }

    suspend fun cancelJob(jobId: String) {
        syncMutex.withLock {
            runnerApi.cancelJob(jobId)
            syncJobLocked(jobId)
        }
    }

    suspend fun confirmRealBuild(jobId: String, resolvedCommitSha: String) {
        syncMutex.withLock {
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

    suspend fun retryJob(jobId: String): String {
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

    private fun JobResponse.toEntity(existing: JobEntity?, logCursor: Long): JobEntity = JobEntity(
        jobId = jobId,
        executionMode = executionMode.name,
        repositoryUrl = repositoryUrl,
        revisionType = requestedRevision.type.name,
        revisionValue = requestedRevision.value,
        simulationOutcome = existing?.simulationOutcome,
        resolvedCommitSha = resolvedCommitSha,
        requiresConfirmation = requiresConfirmation,
        effectiveBuildRoot = effectiveBuild?.buildRoot,
        effectiveBuildTasks = effectiveBuild?.tasks?.joinToString("\n"),
        state = state.name,
        progressPercent = progressPercent,
        latestLogSequence = maxOf(logCursor, existing?.latestLogSequence ?: 0L),
        errorCode = error?.code,
        errorMessage = error?.message,
        createdAt = createdAt,
        updatedAt = updatedAt,
        downloadResult = existing?.downloadResult,
        installResult = existing?.installResult,
    )

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
}
