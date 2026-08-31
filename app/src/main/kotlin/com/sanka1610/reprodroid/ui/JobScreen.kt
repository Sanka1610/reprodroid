package com.sanka1610.reprodroid.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("ReproDroid", style = MaterialTheme.typography.headlineMedium)
                Text("Phase 3E · build sandbox evidence", style = MaterialTheme.typography.bodyMedium)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    ExecutionMode.entries.forEach { candidate ->
                        RadioButton(
                            selected = executionMode == candidate,
                            onClick = { executionMode = candidate },
                        )
                        Text(candidate.name)
                    }
                }
                OutlinedTextField(
                    value = repositoryUrl,
                    onValueChange = { repositoryUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text(
                            if (executionMode == ExecutionMode.SIMULATED) {
                                "Repository URL (not accessed)"
                            } else {
                                "Allowlisted GitHub HTTPS URL"
                            },
                        )
                    },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = revision,
                    onValueChange = { revision = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Revision") },
                    singleLine = true,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    RevisionType.entries.forEach { candidate ->
                        RadioButton(
                            selected = revisionType == candidate,
                            onClick = { revisionType = candidate },
                        )
                        Text(candidate.name)
                    }
                }
                if (executionMode == ExecutionMode.SIMULATED) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SimulationOutcome.entries.forEach { candidate ->
                            RadioButton(
                                selected = outcome == candidate,
                                onClick = { outcome = candidate },
                            )
                            Text(candidate.name)
                        }
                    }
                } else {
                    Text(
                        "Creation resolves the allowlisted ref only. Build execution requires a separate commit and RCE confirmation for the Runner-selected sandbox mode.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Button(
                    enabled = !isSubmitting,
                    onClick = {
                        viewModel.createJob(executionMode, repositoryUrl, revisionType, revision, outcome)
                    },
                ) {
                    Text(if (isSubmitting) "Creating…" else "Create job")
                }

                message?.let { currentMessage ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(currentMessage, modifier = Modifier.weight(1f))
                            TextButton(onClick = viewModel::clearMessage) { Text("Dismiss") }
                        }
                    }
                }

                HorizontalDivider()
                if (jobs.isEmpty()) {
                    Text("No persisted jobs.")
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(jobs, key = { it.job.jobId }) { record ->
                            JobCard(
                                record = record,
                                onCancel = { viewModel.cancelJob(record.job.jobId) },
                                onRetry = { viewModel.retryJob(record.job.jobId) },
                                onConfirm = { commit ->
                                    viewModel.confirmRealBuild(record.job.jobId, commit)
                                },
                                activeArtifactActions = activeArtifactActions,
                                onDownload = { artifactId ->
                                    viewModel.downloadArtifact(record.job.jobId, artifactId)
                                },
                                onInstall = { artifactId ->
                                    viewModel.installArtifact(record.job.jobId, artifactId)
                                },
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
                Text(state.name, style = MaterialTheme.typography.titleMedium)
                Text("${job.progressPercent}%")
            }
            LinearProgressIndicator(
                progress = { job.progressPercent / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Job ID: ${job.jobId}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
            Text(job.repositoryUrl, style = MaterialTheme.typography.bodySmall)
            Text(
                "${job.executionMode} · ${job.revisionType.lowercase()} ${job.revisionValue}" +
                    (job.simulationOutcome?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
            )
            job.resolvedCommitSha?.let { commit ->
                Text("Resolved commit: $commit", style = MaterialTheme.typography.bodySmall)
            }
            job.effectiveBuildRoot?.let { buildRoot ->
                Text(
                    "Fixed build: $buildRoot · ${job.effectiveBuildTasks.orEmpty().replace('\n', ' ')}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "Dependency pinning: ${dependencyPinningLabel(job.effectiveDependencyPinning)}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "Determinism controls: ${determinismSummary(job.effectiveSourceDateEpoch, job.effectiveNoBuildCache, job.effectiveFixedLocale)}",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (job.effectiveDependencyPinning == "LOCKFILE_OFFLINE") {
                    Text(
                        "Gradle offline resolution is not network isolation.",
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
                        Text("Pre-build source scan", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (evidence.scan.findingCount == 0) {
                                "No configured detector findings"
                            } else {
                                "${evidence.scan.findingCount} configured detector findings"
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            "${evidence.scan.scannedFiles} files · ${evidence.scan.scannedBytes} bytes · " +
                                "${evidence.scan.skippedBinaryFiles} binary skipped · " +
                                "${evidence.scan.skippedSymlinks} symlinks skipped",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            "Scanner ${evidence.scan.scannerVersion} · result ${evidence.scan.resultSha256}",
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
                                "Additional findings: ${evidence.findings.size - MAX_SOURCE_SCAN_FINDINGS_IN_JOB_UI}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Text(
                            "Static indicators only; this is not a safe/malicious verdict.",
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
                        Text("Build environment", style = MaterialTheme.typography.titleSmall)
                        Text(sandboxManifestText(evidence.manifest.sandboxJson), style = MaterialTheme.typography.bodySmall)
                        Text(
                            "Java ${evidence.manifest.javaVersion} (${evidence.manifest.javaVendor})",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            "Gradle ${evidence.manifest.gradleVersion} · validated SDK API " +
                                "${evidence.manifest.androidSdkApiLevel} · Build Tools ${evidence.manifest.buildToolsVersion}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            "APK SHA-256 ${evidence.manifest.apkSha256}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                        Text(
                            "${evidence.dependencies.size} dependency records · retrieved ${evidence.manifest.retrievedAt}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            "Determinism controls: ${determinismSummary(evidence.manifest.sourceDateEpoch, evidence.manifest.noBuildCache, evidence.manifest.fixedLocale)}",
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
            if (sandboxWarning != null) TextButton(onClick = onRefreshManifest) { Text("Refresh sandbox state") }
            if (state == JobState.SUCCEEDED && job.executionMode == ExecutionMode.REAL_TRUSTED.name) {
                TextButton(onClick = onRefreshManifest) { Text("Refresh build manifest") }
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
                        Text("Arbitrary-code-execution warning", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (job.sandboxMode == "DOCKER")
                                "Gradle plugins and build scripts execute arbitrary code inside an opt-in Docker build container. " +
                                    "Bridge networking does not establish host/LAN isolation. There is no hard Job disk quota. " +
                                    "The host Runner controls Docker; this is not third-party attestation or proof of safe source."
                            else "Gradle plugins and build scripts at the resolved commit can execute arbitrary code on the Runner host. " +
                                "The allowlist and Wrapper checksum checks do not provide a sandbox.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = riskAcknowledged,
                                onCheckedChange = { riskAcknowledged = it },
                            )
                            Text("I accept this risk for the displayed commit.")
                        }
                        Button(
                            enabled = sandboxValid && riskAcknowledged && job.resolvedCommitSha != null,
                            onClick = { job.resolvedCommitSha?.let(onConfirm) },
                        ) {
                            Text("Confirm and run fixed build")
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
                        Text("Source scan review required", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Review every displayed indicator before continuing. This acknowledgement is separate from the earlier build RCE confirmation.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = sourceScanAcknowledged,
                                onCheckedChange = { sourceScanAcknowledged = it },
                            )
                            Text("I reviewed these findings for this result digest.")
                        }
                        Button(
                            enabled = sandboxValid && sourceScanAcknowledged &&
                                (sourceScan?.scan?.let { it.requiresReview && !it.reviewed } == true),
                            onClick = { sourceScan?.scan?.resultSha256?.let(onContinueSourceScan) },
                        ) {
                            Text("Acknowledge findings and continue")
                        }
                    }
                }
            }

            val recentLogs = record.logs.sortedBy { it.sequence }.takeLast(6)
            if (recentLogs.isNotEmpty()) {
                Text("Logs", style = MaterialTheme.typography.labelLarge)
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
                    TextButton(onClick = onCancel) { Text("Cancel") }
                } else {
                    TextButton(onClick = onRetry) { Text("Retry as new job") }
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
            Text("APK: ${artifact.fileName}", style = MaterialTheme.typography.titleSmall)
            Text(
                "Runner: ${artifact.sizeBytes} bytes · SHA-256 ${artifact.sha256}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
            if (executionMode != ExecutionMode.REAL_TRUSTED.name) {
                Text("Simulated metadata has no downloadable APK content.", style = MaterialTheme.typography.bodySmall)
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
                            actionInProgress -> "Downloading and verifying…"
                            downloadStatus == ArtifactDownloadStatus.DOWNLOADING -> "Retry interrupted download"
                            downloadStatus == ArtifactDownloadStatus.FAILED -> "Retry download"
                            else -> "Download and verify APK"
                        },
                    )
                }
                return@Column
            }

            val hasVerifiedSigner = !artifact.signingCertificateSha256.isNullOrBlank() &&
                !artifact.currentSignerSha256.isNullOrBlank()
            Text(
                if (hasVerifiedSigner) {
                    "🟡 Buildable · official APK comparison not performed"
                } else {
                    "Comparison-only artifact · unsigned"
                },
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                "Android SHA-256: ${artifact.downloadedSha256}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                "Package: ${artifact.packageName}\n" +
                    "Candidate: ${artifact.versionName.ifBlank { "(none)" }} (${artifact.versionCode})",
                style = MaterialTheme.typography.bodySmall,
            )
            Text("Signing certificate SHA-256", style = MaterialTheme.typography.labelLarge)
            artifact.signingCertificateSha256.orEmpty().lineSequence().filter(String::isNotBlank).forEach { fingerprint ->
                Text(fingerprint, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
            if (!hasVerifiedSigner) {
                Text(
                    "This unsigned release artifact is retained only for reproducibility comparison. " +
                        "It cannot be passed to the installer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                return@Column
            }
            when (artifact.existingInstallStatus) {
                ExistingInstallStatus.NOT_INSTALLED_OR_NOT_VISIBLE.name -> Text(
                    "No installed package with this package name was found in the current Android profile.",
                    style = MaterialTheme.typography.bodySmall,
                )
                ExistingInstallStatus.SIGNER_MATCH.name -> Text(
                    "Installed: ${artifact.installedVersionName?.ifBlank { "(none)" } ?: "(none)"} " +
                        "(${artifact.installedVersionCode ?: "unknown"})\n" +
                        "The installed package has the same current signer fingerprint.",
                    style = MaterialTheme.typography.bodySmall,
                )
                ExistingInstallStatus.SIGNER_MISMATCH.name -> Text(
                    "Installed: ${artifact.installedVersionName?.ifBlank { "(none)" } ?: "(none)"} " +
                        "(${artifact.installedVersionCode ?: "unknown"})\n" +
                        "Installed package signer mismatch: Android will normally reject an update. " +
                        "ReproDroid will not uninstall the existing app or bypass signature checks.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            TextButton(
                enabled = !actionInProgress && !installPending,
                onClick = onDownload,
            ) {
                Text("Download and verify again")
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        "This APK is only Buildable. Its contents have not been compared with an official APK. Installation may also be blocked by Android Developer Verification.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = installRiskAcknowledged,
                            onCheckedChange = { installRiskAcknowledged = it },
                        )
                        Text("I understand and want to use Android's standard installer.")
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
                                actionInProgress -> "Preparing installer…"
                                installPending -> "Waiting for installer result…"
                                else -> "Install with system installer"
                            },
                        )
                    }
                    if (!context.packageManager.canRequestPackageInstalls()) {
                        Text(
                            "The first tap opens this app's ‘Install unknown apps’ setting. Return here and tap Install again after allowing it.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            latestAttempt?.let { attempt ->
                Text("Latest install result: ${attempt.status}", style = MaterialTheme.typography.labelLarge)
                attempt.packageInstallerStatus?.let { status ->
                    Text("PackageInstaller status: $status", style = MaterialTheme.typography.bodySmall)
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
