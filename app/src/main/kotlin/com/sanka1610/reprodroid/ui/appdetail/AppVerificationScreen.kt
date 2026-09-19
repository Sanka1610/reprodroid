package com.sanka1610.reprodroid.ui.appdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sanka1610.reprodroid.R
import com.sanka1610.reprodroid.data.local.BuildEnvironmentManifestWithDependencies
import com.sanka1610.reprodroid.data.local.ComparisonEligibility
import com.sanka1610.reprodroid.data.local.ComparisonRunStatus
import com.sanka1610.reprodroid.data.local.JobRecord
import com.sanka1610.reprodroid.data.local.ManagementMode
import com.sanka1610.reprodroid.data.local.RegisteredAppRecord
import com.sanka1610.reprodroid.data.local.ReleaseDiscoveryStatus
import com.sanka1610.reprodroid.data.local.ResourceAvailabilityEntity
import com.sanka1610.reprodroid.data.repository.BuildManifestWarning
import com.sanka1610.reprodroid.data.repository.SourceScanWarning
import com.sanka1610.reprodroid.data.repository.sandboxAcknowledgementAllowed
import com.sanka1610.reprodroid.ui.*
import com.sanka1610.reprodroid.ui.settings.AccordionSection
import com.sanka1610.reprodroid.ui.shared.*

@Composable
internal fun AppVerificationScreen(
    record: RegisteredAppRecord,
    active: Boolean,
    availability: List<ResourceAvailabilityEntity>,
    runnerJobs: Map<String, JobRecord>,
    buildEnvironmentManifests: Map<String, BuildEnvironmentManifestWithDependencies>,
    buildManifestWarnings: Map<String, BuildManifestWarning>,
    sourceScanWarnings: Map<String, SourceScanWarning>,
    sandboxWarnings: Map<String, String>,
    onBack: () -> Unit,
    onAcquire: () -> Unit,
    onSettings: () -> Unit,
    onRunnerSettings: () -> Unit,
    onStartComparison: () -> Unit,
    onConfirmComparison: (String) -> Unit,
    onContinueComparisonSourceScan: (String) -> Unit,
    onRefreshComparison: (String) -> Unit,
    onComparison: (String) -> Unit,
) {
    val asset = record.latestRelease?.selectedAsset
    val comparison = record.currentComparison
    val currentReviewJob = comparison?.let { runnerJobs[it.repeatRunnerJobId ?: it.runnerJobId] }
    var sourceScanRiskConfirmed by remember(comparison?.comparisonRunId, comparison?.status, currentReviewJob?.job?.jobId, currentReviewJob?.sourceScan?.scan?.resultSha256) { mutableStateOf(false) }
    var showEvidence by rememberSaveable(comparison?.comparisonRunId) { mutableStateOf(false) }
    val prepared = referenceApkAvailable(asset, availability) &&
        record.app.releaseDiscoveryStatus == ReleaseDiscoveryStatus.AVAILABLE.name &&
        asset?.comparisonEligibility != ComparisonEligibility.INCOMPARABLE.name &&
        record.selectedBuildConfiguration?.validationState == "CONFIGURED"
    BackScaffoldTitle(stringResource(R.string.install_flow_verify_title), onBack) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(record.app.resolvedDisplayName, style = MaterialTheme.typography.titleLarge)
            if (active) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(trustLabel(record), style = MaterialTheme.typography.titleMedium)
            if (record.app.managementMode != ManagementMode.VERIFICATION.name) {
                TextButton(onClick = onSettings) { Text(stringResource(R.string.settings_management_mode)) }
            } else if (comparison == null) {
                if (!referenceApkAvailable(asset, availability)) {
                    Text(stringResource(R.string.app_flow_apk_missing))
                    Button(enabled = !active, onClick = onAcquire) { Text(stringResource(R.string.app_action_acquire)) }
                }
                Button(enabled = !active && prepared, onClick = onStartComparison, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.technical_build_compare)) }
            } else {
                Text(statusLabel(comparison.status), style = MaterialTheme.typography.titleMedium)
                currentReviewJob?.job?.let { job ->
                    Text(statusLabel(job.state) + " · " + job.progressPercent + "%")
                }
                comparison.incomparableReason?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                comparison.repeatIncomparableReason?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                when (comparison.status) {
                    ComparisonRunStatus.AWAITING_CONFIRMATION.name,
                    ComparisonRunStatus.AWAITING_REPEAT_CONFIRMATION.name -> {
                        val confirmationJobId = if (comparison.status == ComparisonRunStatus.AWAITING_REPEAT_CONFIRMATION.name)
                            comparison.repeatRunnerJobId else comparison.runnerJobId
                        val confirmationJob = confirmationJobId?.let(runnerJobs::get)?.job
                        val confirmationAllowed = confirmationJob != null && sandboxAcknowledgementAllowed(confirmationJob) &&
                            sandboxWarnings[confirmationJobId] == null
                        Text(
                            if (confirmationJob?.sandboxMode == "DOCKER") {
                            stringResource(
                                R.string.technical_repeat_docker_warning,
                                confirmationJob.sandboxProfileId ?: stringResource(R.string.value_unknown),
                            )
                            } else if (comparison.status == ComparisonRunStatus.AWAITING_REPEAT_CONFIRMATION.name) {
                                stringResource(R.string.technical_repeat_build_warning)
                            } else {
                                stringResource(R.string.technical_primary_build_warning)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Button(
                            enabled = !active && confirmationAllowed,
                            onClick = { onConfirmComparison(comparison.comparisonRunId) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                if (comparison.status == ComparisonRunStatus.AWAITING_REPEAT_CONFIRMATION.name) {
                                    stringResource(R.string.technical_confirm_repeat)
                                } else {
                                    stringResource(R.string.technical_confirm_primary)
                                },
                            )
                        }
                    }
                    ComparisonRunStatus.AWAITING_SCAN_REVIEW.name,
                    ComparisonRunStatus.AWAITING_REPEAT_SCAN_REVIEW.name -> {
                        val repeatReview = comparison.status ==
                            ComparisonRunStatus.AWAITING_REPEAT_SCAN_REVIEW.name
                        val reviewJobId = if (repeatReview) {
                            comparison.repeatRunnerJobId
                        } else {
                            comparison.runnerJobId
                        }
                        val scan = reviewJobId?.let(runnerJobs::get)?.sourceScan
                        val reviewJob = reviewJobId?.let(runnerJobs::get)?.job
                        SourceScanEvidence(stringResource(R.string.technical_build_source_scan, if (repeatReview) "B" else "A"), scan)
                        val sandboxReviewAllowed = reviewJob != null && sandboxAcknowledgementAllowed(reviewJob) && sandboxWarnings[reviewJobId] == null
                        Text(
                            if (repeatReview) {
                                stringResource(R.string.technical_review_repeat_scan)
                            } else {
                                stringResource(R.string.technical_review_primary_scan)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = sourceScanRiskConfirmed,
                                onCheckedChange = { sourceScanRiskConfirmed = it },
                            )
                            Text(stringResource(R.string.technical_reviewed_findings))
                        }
                        Button(
                            enabled = !active && sandboxReviewAllowed && sourceScanRiskConfirmed &&
                                (scan?.scan?.let { it.requiresReview && !it.reviewed } == true),
                            onClick = {
                                onContinueComparisonSourceScan(comparison.comparisonRunId)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                if (repeatReview) {
                                    stringResource(R.string.technical_ack_repeat)
                                } else {
                                    stringResource(R.string.technical_ack_primary)
                                },
                            )
                        }
                    }
                    ComparisonRunStatus.COMPLETED.name -> Button(
                        enabled = !active && prepared,
                        onClick = onStartComparison,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.technical_run_again)) }
                    else -> Button(
                        enabled = !active,
                        onClick = { onRefreshComparison(comparison.comparisonRunId) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.technical_refresh_comparison)) }
                }
                if (comparison.status == ComparisonRunStatus.COMPLETED.name) {
                    OutlinedButton(onClick = { onComparison(comparison.comparisonRunId) }) { Text(stringResource(R.string.app_flow_completed)) }
                    TextButton(onClick = onAcquire) { Text(stringResource(R.string.app_acquisition_open)) }
                }
                AccordionSection(stringResource(R.string.app_flow_details), showEvidence, { showEvidence = !showEvidence }) {
                    ComparisonTechnicalContent(record, runnerJobs, buildEnvironmentManifests, buildManifestWarnings, sourceScanWarnings, sandboxWarnings)
                }
            }
            TextButton(onClick = onSettings) { Text(stringResource(R.string.app_flow_build_settings)) }
            TextButton(onClick = onRunnerSettings) { Text(stringResource(R.string.app_flow_runner_settings)) }
            Spacer(Modifier.height(16.dp))
        }
    }
}
