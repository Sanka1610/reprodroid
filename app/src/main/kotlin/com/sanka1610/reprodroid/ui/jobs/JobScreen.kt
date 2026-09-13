package com.sanka1610.reprodroid.ui.jobs

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanka1610.reprodroid.R
import com.sanka1610.reprodroid.ui.JobViewModel
import com.sanka1610.reprodroid.ui.dependencyPinningLabel
import com.sanka1610.reprodroid.ui.determinismSummary
import com.sanka1610.reprodroid.ui.shared.statusLabelResource
import com.sanka1610.reprodroid.data.local.JobRecord
import com.sanka1610.reprodroid.data.local.ArtifactDownloadStatus
import com.sanka1610.reprodroid.data.local.ArtifactEntity
import com.sanka1610.reprodroid.data.local.ExistingInstallStatus
import com.sanka1610.reprodroid.data.local.InstallAttemptEntity
import com.sanka1610.reprodroid.data.local.InstallAttemptStatus
import com.sanka1610.reprodroid.data.network.ExecutionMode
import com.sanka1610.reprodroid.data.network.JobState
import com.sanka1610.reprodroid.data.network.RevisionType
import com.sanka1610.reprodroid.data.network.SimulationOutcome
import com.sanka1610.reprodroid.data.repository.sandboxSelectionText
import com.sanka1610.reprodroid.data.repository.sandboxManifestText
import com.sanka1610.reprodroid.data.repository.sandboxAcknowledgementAllowed

@Composable
fun JobScreen(viewModel: JobViewModel) {
    val jobs by viewModel.jobs.collectAsStateWithLifecycle()
    val isSubmitting by viewModel.isSubmitting.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val activeArtifactActions by viewModel.activeArtifactActions.collectAsStateWithLifecycle()
    val buildManifestWarnings by viewModel.buildManifestWarnings.collectAsStateWithLifecycle()
    val sourceScanWarnings by viewModel.sourceScanWarnings.collectAsStateWithLifecycle()
    val sandboxWarnings by viewModel.sandboxWarnings.collectAsStateWithLifecycle()
    var repositoryUrl by rememberSaveable {
        mutableStateOf("https://github.com/MorpheApp/MicroG-RE.git")
    }
    var executionMode by rememberSaveable { mutableStateOf(ExecutionMode.SIMULATED) }
    var revisionType by rememberSaveable { mutableStateOf(RevisionType.BRANCH) }
    var revision by rememberSaveable { mutableStateOf("main") }
    var outcome by rememberSaveable { mutableStateOf(SimulationOutcome.SUCCESS) }

    LifecycleStartEffect(viewModel) {
        viewModel.startVisibleSync()
        onStopOrDispose { viewModel.stopVisibleSync() }
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.jobs_subtitle), style = MaterialTheme.typography.bodyMedium)
                        Column(Modifier.selectableGroup()) {
                            ExecutionMode.entries.forEach { candidate ->
                                JobRadioChoice(
                                    selected = executionMode == candidate,
                                    label = executionModeLabel(candidate),
                                    onClick = { executionMode = candidate },
                                )
                            }
                        }
                        OutlinedTextField(
                            value = repositoryUrl,
                            onValueChange = { repositoryUrl = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = {
                                Text(
                                    if (executionMode == ExecutionMode.SIMULATED) {
                                        stringResource(R.string.jobs_repository_simulated)
                                    } else {
                                        stringResource(R.string.jobs_repository_trusted)
                                    },
                                )
                            },
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = revision,
                            onValueChange = { revision = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.jobs_revision)) },
                            singleLine = true,
                        )
                        Column(Modifier.selectableGroup()) {
                            RevisionType.entries.forEach { candidate ->
                                JobRadioChoice(
                                    selected = revisionType == candidate,
                                    label = revisionTypeLabel(candidate),
                                    onClick = { revisionType = candidate },
                                )
                            }
                        }
                        if (executionMode == ExecutionMode.SIMULATED) {
                            Column(Modifier.selectableGroup()) {
                                SimulationOutcome.entries.forEach { candidate ->
                                    JobRadioChoice(
                                        selected = outcome == candidate,
                                        label = simulationOutcomeLabel(candidate),
                                        onClick = { outcome = candidate },
                                    )
                                }
                            }
                        } else {
                            Text(
                                stringResource(R.string.jobs_creation_boundary),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Button(
                            enabled = !isSubmitting,
                            onClick = {
                                viewModel.createJob(executionMode, repositoryUrl, revisionType, revision, outcome)
                            },
                        ) {
                            Text(stringResource(if (isSubmitting) R.string.jobs_creating else R.string.jobs_create))
                        }
                    }
                }
                message?.let { currentMessage ->
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(currentMessage, modifier = Modifier.weight(1f))
                                TextButton(onClick = viewModel::clearMessage) {
                                    Text(stringResource(R.string.action_dismiss))
                                }
                            }
                        }
                    }
                }
                item { HorizontalDivider() }
                if (jobs.isEmpty()) {
                    item { Text(stringResource(R.string.jobs_empty)) }
                } else {
                    items(jobs, key = { it.job.jobId }) { record ->
                        JobCard(
                            record = record,
                            onCancel = { viewModel.cancelJob(record.job.jobId) },
                            onRetry = { viewModel.retryJob(record.job.jobId) },
                            onConfirm = { commit -> viewModel.confirmRealBuild(record.job.jobId, commit) },
                            activeArtifactActions = activeArtifactActions,
                            onDownload = { artifactId -> viewModel.downloadArtifact(record.job.jobId, artifactId) },
                            onInstall = { artifactId -> viewModel.installArtifact(record.job.jobId, artifactId) },
                            manifestWarning = buildManifestWarnings[record.job.jobId]?.message,
                            onRefreshManifest = { viewModel.refreshJob(record.job.jobId) },
                            sourceScanWarning = sourceScanWarnings[record.job.jobId]?.message,
                            sandboxWarning = sandboxWarnings[record.job.jobId],
                            onContinueSourceScan = { digest ->
                                viewModel.continueSourceScan(record.job.jobId, digest)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun JobRadioChoice(selected: Boolean, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, modifier = Modifier.padding(end = 8.dp))
    }
}

@Composable
internal fun JobCard(
    record: JobRecord,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onConfirm: (String) -> Unit,
    activeArtifactActions: Set<String>,
    onDownload: (String) -> Unit,
    onInstall: (String) -> Unit,
    manifestWarning: String?,
    onRefreshManifest: () -> Unit,
    sourceScanWarning: String?,
    sandboxWarning: String?,
    onContinueSourceScan: (String) -> Unit,
) {
    val job = record.job
    val sandboxValid = sandboxWarning == null && sandboxAcknowledgementAllowed(job)
    val state = remember(job.state) { JobState.valueOf(job.state) }
    var riskAcknowledged by rememberSaveable(job.jobId, job.resolvedCommitSha, job.sandboxMode, job.sandboxOrigin, job.sandboxProfileId) { mutableStateOf(false) }
    var sourceScanAcknowledged by rememberSaveable(job.jobId, record.sourceScan?.scan?.resultSha256) { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(jobStateLabel(state), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.jobs_progress, job.progressPercent))
            }
            LinearProgressIndicator(
                progress = { job.progressPercent / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(R.string.jobs_job_id, job.jobId),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
            Text(job.repositoryUrl, style = MaterialTheme.typography.bodySmall)
            Text(
                stringResource(
                    R.string.jobs_identity_summary,
                    executionModeNameLabel(job.executionMode),
                    revisionTypeNameLabel(job.revisionType),
                    job.revisionValue,
                    job.simulationOutcome?.let { " · ${simulationOutcomeNameLabel(it)}" }.orEmpty(),
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            job.resolvedCommitSha?.let { commit ->
                Text(stringResource(R.string.jobs_resolved_commit, commit), style = MaterialTheme.typography.bodySmall)
            }
            job.effectiveBuildRoot?.let { buildRoot ->
                Text(
                    stringResource(
                        R.string.jobs_fixed_build,
                        buildRoot,
                        job.effectiveBuildTasks.orEmpty().replace('\n', ' '),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    stringResource(
                        R.string.jobs_dependency_pinning,
                        dependencyPinningLabel(job.effectiveDependencyPinning),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    stringResource(
                        R.string.jobs_determinism_controls,
                        determinismSummary(
                            job.effectiveSourceDateEpoch,
                            job.effectiveNoBuildCache,
                            job.effectiveFixedLocale,
                        ),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (job.effectiveDependencyPinning == "LOCKFILE_OFFLINE") {
                    Text(
                        stringResource(R.string.jobs_offline_not_isolation),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            job.errorMessage?.let { error ->
                Text("${job.errorCode}: $error", color = MaterialTheme.colorScheme.error)
            }

            record.sourceScan?.let { evidence ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(stringResource(R.string.jobs_source_scan_title), style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (evidence.scan.findingCount == 0) {
                                stringResource(R.string.jobs_source_scan_clean)
                            } else {
                                pluralStringResource(
                                    R.plurals.jobs_source_scan_findings,
                                    evidence.scan.findingCount,
                                    evidence.scan.findingCount,
                                )
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            stringResource(
                                R.string.jobs_source_scan_summary,
                                evidence.scan.scannedFiles,
                                evidence.scan.scannedBytes,
                                evidence.scan.skippedBinaryFiles,
                                evidence.scan.skippedSymlinks,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            stringResource(
                                R.string.jobs_scanner_result,
                                evidence.scan.scannerVersion,
                                evidence.scan.resultSha256,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                        evidence.detectorCounts.sortedBy { it.detectorId }.forEach { count ->
                            Text("${count.detectorId}: ${count.count}", style = MaterialTheme.typography.bodySmall)
                        }
                        evidence.findings
                            .sortedBy { it.ordinal }
                            .take(MAX_SOURCE_SCAN_FINDINGS_IN_JOB_UI)
                            .forEach { finding ->
                            val position = finding.line?.let { line -> ":$line:${finding.column}" }.orEmpty()
                            Text(
                                "${finding.detectorId} · ${finding.displayPath}$position",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                        if (evidence.findings.size > MAX_SOURCE_SCAN_FINDINGS_IN_JOB_UI) {
                            Text(
                                pluralStringResource(
                                    R.plurals.jobs_additional_findings,
                                    evidence.findings.size - MAX_SOURCE_SCAN_FINDINGS_IN_JOB_UI,
                                    evidence.findings.size - MAX_SOURCE_SCAN_FINDINGS_IN_JOB_UI,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Text(
                            stringResource(R.string.jobs_scan_not_verdict),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            sourceScanWarning?.let { warning ->
                Text(warning, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            record.buildEnvironmentManifest?.let { evidence ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(stringResource(R.string.jobs_build_environment), style = MaterialTheme.typography.titleSmall)
                        Text(sandboxManifestText(evidence.manifest.sandboxJson), style = MaterialTheme.typography.bodySmall)
                        Text(
                            stringResource(
                                R.string.jobs_java_environment,
                                evidence.manifest.javaVersion,
                                evidence.manifest.javaVendor,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            stringResource(
                                R.string.jobs_gradle_environment,
                                evidence.manifest.gradleVersion,
                                evidence.manifest.androidSdkApiLevel,
                                evidence.manifest.buildToolsVersion,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            stringResource(R.string.jobs_apk_sha256, evidence.manifest.apkSha256),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                        Text(
                            stringResource(
                                R.string.jobs_dependency_records,
                                evidence.dependencies.size,
                                evidence.manifest.retrievedAt,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            stringResource(
                                R.string.jobs_determinism_controls,
                                determinismSummary(
                                    evidence.manifest.sourceDateEpoch,
                                    evidence.manifest.noBuildCache,
                                    evidence.manifest.fixedLocale,
                                ),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            manifestWarning?.let { warning ->
                Text(warning, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Text(sandboxSelectionText(job), style = MaterialTheme.typography.bodySmall)
            sandboxWarning?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            if (sandboxWarning != null) {
                TextButton(onClick = onRefreshManifest) { Text(stringResource(R.string.jobs_refresh_sandbox)) }
            }
            if (state == JobState.SUCCEEDED && job.executionMode == ExecutionMode.REAL_TRUSTED.name) {
                TextButton(onClick = onRefreshManifest) { Text(stringResource(R.string.jobs_refresh_manifest)) }
            }

            record.artifacts.forEach { artifact ->
                ArtifactCard(
                    artifact = artifact,
                    executionMode = job.executionMode,
                    jobState = state,
                    attempts = record.installAttempts.filter { it.artifactId == artifact.artifactId },
                    actionInProgress = artifact.artifactId in activeArtifactActions,
                    onDownload = { onDownload(artifact.artifactId) },
                    onInstall = { onInstall(artifact.artifactId) },
                )
            }

            if (state == JobState.AWAITING_CONFIRMATION && job.requiresConfirmation) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(stringResource(R.string.jobs_rce_title), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(
                                if (job.sandboxMode == "DOCKER") {
                                    R.string.jobs_rce_docker_body
                                } else {
                                    R.string.jobs_rce_host_body
                                },
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = riskAcknowledged,
                                onCheckedChange = { riskAcknowledged = it },
                            )
                            Text(stringResource(R.string.jobs_rce_acknowledgement))
                        }
                        Button(
                            enabled = sandboxValid && riskAcknowledged && job.resolvedCommitSha != null,
                            onClick = { job.resolvedCommitSha?.let(onConfirm) },
                        ) {
                            Text(stringResource(R.string.jobs_rce_confirm))
                        }
                    }
                }
            }

            if (state == JobState.AWAITING_SCAN_REVIEW) {
                val sourceScan = record.sourceScan
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(stringResource(R.string.jobs_scan_review_title), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(R.string.jobs_scan_review_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = sourceScanAcknowledged,
                                onCheckedChange = { sourceScanAcknowledged = it },
                            )
                            Text(stringResource(R.string.jobs_scan_acknowledgement))
                        }
                        Button(
                            enabled = sandboxValid && sourceScanAcknowledged &&
                                (sourceScan?.scan?.let { it.requiresReview && !it.reviewed } == true),
                            onClick = { sourceScan?.scan?.resultSha256?.let(onContinueSourceScan) },
                        ) {
                            Text(stringResource(R.string.jobs_scan_continue))
                        }
                    }
                }
            }

            val recentLogs = record.logs.sortedBy { it.sequence }.takeLast(6)
            if (recentLogs.isNotEmpty()) {
                Text(stringResource(R.string.jobs_logs), style = MaterialTheme.typography.labelLarge)
                recentLogs.forEach { log ->
                    Text(
                        "${log.sequence.toString().padStart(3, '0')} ${log.level} ${log.message}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!state.isTerminal) {
                    TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
                } else {
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.jobs_retry)) }
                }
            }
        }
    }
}

private const val MAX_SOURCE_SCAN_FINDINGS_IN_JOB_UI = 40

@Composable
private fun ArtifactCard(
    artifact: ArtifactEntity,
    executionMode: String,
    jobState: JobState,
    attempts: List<InstallAttemptEntity>,
    actionInProgress: Boolean,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
) {
    val context = LocalContext.current
    val downloadStatus = runCatching { ArtifactDownloadStatus.valueOf(artifact.downloadStatus) }
        .getOrDefault(ArtifactDownloadStatus.NOT_DOWNLOADED)
    val latestAttempt = attempts.maxByOrNull { it.updatedAt }
    val installPending = latestAttempt?.status in setOf(
        InstallAttemptStatus.PREPARING.name,
        InstallAttemptStatus.COMMITTED.name,
        InstallAttemptStatus.PENDING_USER_ACTION.name,
    )
    var installRiskAcknowledged by rememberSaveable(artifact.jobId, artifact.artifactId) {
        mutableStateOf(false)
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(stringResource(R.string.jobs_artifact_apk, artifact.fileName), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.jobs_runner_artifact, artifact.sizeBytes, artifact.sha256),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
            if (executionMode != ExecutionMode.REAL_TRUSTED.name) {
                Text(stringResource(R.string.jobs_simulated_no_apk), style = MaterialTheme.typography.bodySmall)
                return@Column
            }
            if (jobState != JobState.SUCCEEDED) return@Column

            if (downloadStatus != ArtifactDownloadStatus.VERIFIED) {
                artifact.downloadError?.let { error ->
                    Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                Button(
                    enabled = !actionInProgress,
                    onClick = onDownload,
                ) {
                    Text(
                        when {
                            actionInProgress -> stringResource(R.string.jobs_downloading_verifying)
                            downloadStatus == ArtifactDownloadStatus.DOWNLOADING -> stringResource(R.string.jobs_retry_interrupted_download)
                            downloadStatus == ArtifactDownloadStatus.FAILED -> stringResource(R.string.jobs_retry_download)
                            else -> stringResource(R.string.jobs_download_verify)
                        },
                    )
                }
                return@Column
            }

            val hasVerifiedSigner = !artifact.signingCertificateSha256.isNullOrBlank() &&
                !artifact.currentSignerSha256.isNullOrBlank()
            Text(
                if (hasVerifiedSigner) {
                    stringResource(R.string.jobs_buildable_not_compared)
                } else {
                    stringResource(R.string.jobs_comparison_only_unsigned)
                },
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                stringResource(R.string.jobs_android_sha256, artifact.downloadedSha256.orEmpty()),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                stringResource(
                    R.string.jobs_package_candidate,
                    artifact.packageName.orEmpty(),
                    artifact.versionName.ifBlank { stringResource(R.string.value_none) },
                    artifact.versionCode,
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(stringResource(R.string.jobs_signing_certificate), style = MaterialTheme.typography.labelLarge)
            artifact.signingCertificateSha256.orEmpty().lineSequence().filter(String::isNotBlank).forEach { fingerprint ->
                Text(fingerprint, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
            if (!hasVerifiedSigner) {
                Text(
                    stringResource(R.string.jobs_unsigned_artifact_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                return@Column
            }
            when (artifact.existingInstallStatus) {
                ExistingInstallStatus.NOT_INSTALLED_OR_NOT_VISIBLE.name -> Text(
                    stringResource(R.string.jobs_no_installed_package),
                    style = MaterialTheme.typography.bodySmall,
                )
                ExistingInstallStatus.SIGNER_MATCH.name -> Text(
                    stringResource(
                        R.string.jobs_installed_signer_match,
                        artifact.installedVersionName?.ifBlank { stringResource(R.string.value_none) }
                            ?: stringResource(R.string.value_none),
                        artifact.installedVersionCode?.toString() ?: stringResource(R.string.value_unknown),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
                ExistingInstallStatus.SIGNER_MISMATCH.name -> Text(
                    stringResource(
                        R.string.jobs_installed_signer_mismatch,
                        artifact.installedVersionName?.ifBlank { stringResource(R.string.value_none) }
                            ?: stringResource(R.string.value_none),
                        artifact.installedVersionCode?.toString() ?: stringResource(R.string.value_unknown),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            TextButton(
                enabled = !actionInProgress && !installPending,
                onClick = onDownload,
            ) {
                Text(stringResource(R.string.jobs_download_again))
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        stringResource(R.string.jobs_install_risk_body),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = installRiskAcknowledged,
                            onCheckedChange = { installRiskAcknowledged = it },
                        )
                        Text(stringResource(R.string.jobs_install_acknowledgement))
                    }
                    Button(
                        enabled = installRiskAcknowledged && !actionInProgress && !installPending,
                        onClick = {
                            if (context.packageManager.canRequestPackageInstalls()) {
                                onInstall()
                            } else {
                                openUnknownAppSources(context)
                            }
                        },
                    ) {
                        Text(
                            when {
                                actionInProgress -> stringResource(R.string.jobs_preparing_installer)
                                installPending -> stringResource(R.string.jobs_waiting_installer)
                                else -> stringResource(R.string.jobs_install_system)
                            },
                        )
                    }
                    if (!context.packageManager.canRequestPackageInstalls()) {
                        Text(
                            stringResource(R.string.jobs_unknown_sources_help),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            latestAttempt?.let { attempt ->
                Text(
                    stringResource(R.string.jobs_latest_install_result, installAttemptStatusLabel(attempt.status)),
                    style = MaterialTheme.typography.labelLarge,
                )
                attempt.packageInstallerStatus?.let { status ->
                    Text(stringResource(R.string.jobs_package_installer_status, status), style = MaterialTheme.typography.bodySmall)
                }
                attempt.statusMessage?.let { statusMessage ->
                    Text(statusMessage, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun openUnknownAppSources(context: Context) {
    val packageSettings = Intent(
        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        "package:${context.packageName}".toUri(),
    )
    try {
        context.startActivity(packageSettings)
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
    }
}

@Composable
private fun executionModeLabel(value: ExecutionMode): String = executionModeNameLabel(value.name)

@Composable
private fun executionModeNameLabel(value: String): String = stringResource(
    when (value) {
        ExecutionMode.SIMULATED.name -> R.string.jobs_execution_simulated
        ExecutionMode.REAL_TRUSTED.name -> R.string.jobs_execution_trusted
        else -> R.string.value_unknown
    },
)

@Composable
private fun revisionTypeLabel(value: RevisionType): String = revisionTypeNameLabel(value.name)

@Composable
private fun revisionTypeNameLabel(value: String): String = stringResource(
    when (value) {
        RevisionType.BRANCH.name -> R.string.jobs_revision_branch
        RevisionType.TAG.name -> R.string.jobs_revision_tag
        RevisionType.COMMIT.name -> R.string.jobs_revision_commit
        else -> R.string.value_unknown
    },
)

@Composable
private fun simulationOutcomeLabel(value: SimulationOutcome): String = simulationOutcomeNameLabel(value.name)

@Composable
private fun simulationOutcomeNameLabel(value: String): String = stringResource(
    when (value) {
        SimulationOutcome.SUCCESS.name -> R.string.state_success
        SimulationOutcome.FAILURE.name -> R.string.state_error
        else -> R.string.value_unknown
    },
)

@Composable
private fun jobStateLabel(value: JobState): String =
    statusLabelResource(value.name)?.let { stringResource(it) } ?: value.name.lowercase().replace('_', ' ')

@Composable
private fun installAttemptStatusLabel(value: String): String =
    statusLabelResource(value)?.let { stringResource(it) } ?: value.lowercase().replace('_', ' ')
