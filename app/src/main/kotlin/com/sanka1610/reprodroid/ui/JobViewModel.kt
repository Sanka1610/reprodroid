package com.sanka1610.reprodroid.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sanka1610.reprodroid.ReproDroidApplication
import com.sanka1610.reprodroid.data.network.ExecutionMode
import com.sanka1610.reprodroid.data.network.RevisionType
import com.sanka1610.reprodroid.data.network.SimulationOutcome
import com.sanka1610.reprodroid.work.JobSyncWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class JobViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as ReproDroidApplication).jobRepository
    private var visiblePollingJob: Job? = null

    val jobs = repository.observeJobs().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    val buildManifestWarnings = repository.buildManifestWarnings
    val sourceScanWarnings = repository.sourceScanWarnings
    val sandboxWarnings = repository.sandboxWarnings

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting = _isSubmitting.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    private val _activeArtifactActions = MutableStateFlow<Set<String>>(emptySet())
    val activeArtifactActions = _activeArtifactActions.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                repository.recoverOrphanedInstallAttempts()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                _message.value = failure.userMessage()
            }
        }
    }

    fun startVisibleSync() {
        if (visiblePollingJob?.isActive == true) return
        JobSyncWorker.enqueueImmediate(getApplication())
        visiblePollingJob = viewModelScope.launch {
            while (isActive) {
                try {
                    repository.syncActiveJobs()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (failure: Throwable) {
                    _message.value = failure.userMessage()
                }
                delay(VISIBLE_POLL_INTERVAL_MILLIS)
            }
        }
    }

    fun stopVisibleSync() {
        visiblePollingJob?.cancel()
        visiblePollingJob = null
    }

    fun createJob(
        executionMode: ExecutionMode,
        repositoryUrl: String,
        revisionType: RevisionType,
        revisionValue: String,
        outcome: SimulationOutcome,
    ) {
        if (repositoryUrl.isBlank() || revisionValue.isBlank()) {
            _message.value = "Repository URL and revision are required."
            return
        }
        viewModelScope.launch {
            _isSubmitting.value = true
            try {
                when (executionMode) {
                    ExecutionMode.SIMULATED -> repository.createSimulatedJob(
                        repositoryUrl = repositoryUrl.trim(),
                        revisionType = revisionType,
                        revisionValue = revisionValue.trim(),
                        outcome = outcome,
                    )
                    ExecutionMode.REAL_TRUSTED -> repository.createRealTrustedJob(
                        repositoryUrl = repositoryUrl.trim(),
                        revisionType = revisionType,
                        revisionValue = revisionValue.trim(),
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                _message.value = failure.userMessage()
            } finally {
                _isSubmitting.value = false
            }
        }
    }

    fun cancelJob(jobId: String) = runAction { repository.cancelJob(jobId) }

    fun confirmRealBuild(jobId: String, resolvedCommitSha: String) =
        runAction { repository.confirmRealBuild(jobId, resolvedCommitSha) }

    fun continueSourceScan(jobId: String, scanResultSha256: String) =
        runAction { repository.continueSourceScan(jobId, scanResultSha256) }

    fun retryJob(jobId: String) = runAction { repository.retryJob(jobId) }

    fun refreshJob(jobId: String) = runAction { repository.syncJob(jobId) }

    fun downloadArtifact(jobId: String, artifactId: String) = runArtifactAction(artifactId) {
        repository.downloadArtifact(jobId, artifactId)
    }

    fun installArtifact(jobId: String, artifactId: String) = runArtifactAction(artifactId) {
        repository.installArtifact(jobId, artifactId)
    }

    fun clearMessage() {
        _message.value = null
    }

    private fun runAction(action: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                action()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                _message.value = failure.userMessage()
            }
        }
    }

    private fun runArtifactAction(artifactId: String, action: suspend () -> Unit) {
        if (artifactId in _activeArtifactActions.value) return
        viewModelScope.launch {
            _activeArtifactActions.value += artifactId
            try {
                action()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                _message.value = failure.userMessage()
            } finally {
                _activeArtifactActions.value -= artifactId
            }
        }
    }

    private fun Throwable.userMessage(): String = message ?: "Runner synchronization failed."

    companion object {
        private const val VISIBLE_POLL_INTERVAL_MILLIS = 2_000L
    }
}
