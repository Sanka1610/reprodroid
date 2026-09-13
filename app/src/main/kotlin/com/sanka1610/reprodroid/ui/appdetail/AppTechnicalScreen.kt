package com.sanka1610.reprodroid.ui.appdetail

import android.graphics.BitmapFactory
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.net.toUri
import androidx.core.graphics.drawable.toBitmap
import com.sanka1610.reprodroid.R
import com.sanka1610.reprodroid.data.local.ManagementMode
import com.sanka1610.reprodroid.data.local.AppSettingsUpdate
import com.sanka1610.reprodroid.data.local.AppReleaseCheckOverrideEntity
import com.sanka1610.reprodroid.data.local.ComparisonEligibility
import com.sanka1610.reprodroid.data.local.ComparisonRunStatus
import com.sanka1610.reprodroid.data.local.GlobalSettingsEntity
import com.sanka1610.reprodroid.data.local.InstallationSource
import com.sanka1610.reprodroid.data.local.PreferredAbi
import com.sanka1610.reprodroid.data.local.ReferenceDownloadStatus
import com.sanka1610.reprodroid.data.local.ReleaseDiscoveryStatus
import com.sanka1610.reprodroid.data.local.ReleaseAssetEntity
import com.sanka1610.reprodroid.data.local.RegisteredAppRecord
import com.sanka1610.reprodroid.data.local.ReleaseVariantPreference
import com.sanka1610.reprodroid.data.local.ReleaseCheckSettingsEntity
import com.sanka1610.reprodroid.data.local.TrustLevel
import com.sanka1610.reprodroid.data.local.UpdateStatus
import com.sanka1610.reprodroid.data.local.ResourceAvailabilityEntity
import com.sanka1610.reprodroid.data.local.BuildEnvironmentManifestWithDependencies
import com.sanka1610.reprodroid.data.local.JobRecord
import com.sanka1610.reprodroid.data.repository.BuildManifestWarning
import com.sanka1610.reprodroid.data.repository.BuildConfigurationInput
import com.sanka1610.reprodroid.data.repository.BuildConfigurationValidator
import com.sanka1610.reprodroid.data.repository.SourceScanWarning
import com.sanka1610.reprodroid.data.repository.DependencyDifferenceKind
import com.sanka1610.reprodroid.data.repository.compareBuildEnvironments
import com.sanka1610.reprodroid.data.repository.sandboxSelectionText
import com.sanka1610.reprodroid.data.repository.sandboxManifestText
import com.sanka1610.reprodroid.data.repository.sandboxAcknowledgementAllowed
import androidx.compose.ui.platform.LocalContext
import java.io.File
import java.util.UUID


import com.sanka1610.reprodroid.ui.*
import com.sanka1610.reprodroid.ui.settings.AccordionSection
import com.sanka1610.reprodroid.ui.settings.AppUpdateSettingsContent
import com.sanka1610.reprodroid.ui.shared.*
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppDetailScreen(
    record: RegisteredAppRecord,
    globalSettings: GlobalSettingsEntity,
    active: Boolean,
    showSettings: Boolean = true,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onRefresh: () -> Unit,
    onSelectReleaseAsset: (String, String, Boolean) -> Unit,
    onClearSavedAssetSelection: () -> Unit,
    onInstall: (Boolean) -> Unit,
    onStartComparison: () -> Unit,
    onRefreshComparison: (String) -> Unit,
    onConfirmComparison: (String) -> Unit,
    onContinueComparisonSourceScan: (String) -> Unit,
    runnerJobs: Map<String, JobRecord>,
    buildEnvironmentManifests: Map<String, BuildEnvironmentManifestWithDependencies>,
    buildManifestWarnings: Map<String, BuildManifestWarning>,
    sourceScanWarnings: Map<String, SourceScanWarning>,
    sandboxWarnings: Map<String, String>,
    availability: List<ResourceAvailabilityEntity>,
) {
    BackHandler(onBack = onBack)
    val backContentDescription = stringResource(R.string.action_back)
    val refreshContentDescription = stringResource(R.string.action_refresh)
    val settingsContentDescription = stringResource(R.string.nav_settings)
    val latest = record.latestRelease
    val asset = latest?.selectedAsset
    var selectedReleaseAssetId by rememberSaveable(
        record.app.registeredAppId,
        latest?.snapshot?.releaseSnapshotId,
    ) { mutableStateOf<String?>(null) }
    var saveExactFilenameCondition by rememberSaveable(
        record.app.registeredAppId,
        latest?.snapshot?.releaseSnapshotId,
    ) { mutableStateOf(false) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var installRiskConfirmed by rememberSaveable(record.app.registeredAppId) { mutableStateOf(false) }
    val currentReviewJob = record.currentComparison?.let { comparison ->
        runnerJobs[comparison.repeatRunnerJobId ?: comparison.runnerJobId]
    }
    var sourceScanRiskConfirmed by rememberSaveable(
        record.currentComparison?.comparisonRunId,
        record.currentComparison?.status,
        currentReviewJob?.job?.jobId,
        currentReviewJob?.sourceScan?.scan?.resultSha256,
    ) { mutableStateOf(false) }
    var canRequestPackageInstalls by remember {
        mutableStateOf(context.packageManager.canRequestPackageInstalls())
    }
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canRequestPackageInstalls = context.packageManager.canRequestPackageInstalls()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val canInstall = asset?.updateStatus in setOf(
        UpdateStatus.NOT_INSTALLED.name,
        UpdateStatus.UPDATE_AVAILABLE.name,
    )
    val warningRequired = (
        record.app.managementMode == ManagementMode.VERIFICATION.name &&
            record.trustLevel != TrustLevel.REPRODUCIBLE
        ) ||
        asset?.existingInstallStatus == "SIGNER_MISMATCH"
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(record.app.resolvedDisplayName) },
            navigationIcon = {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.semantics { contentDescription = backContentDescription },
                ) { NavigationGlyph("‹") }
            },
            actions = {
                IconButton(
                    enabled = !active,
                    onClick = onRefresh,
                    modifier = Modifier.semantics { contentDescription = refreshContentDescription },
                ) {
                    NavigationGlyph("↻")
                }
                if (showSettings) {
                    IconButton(
                        onClick = onSettings,
                        modifier = Modifier.semantics { contentDescription = settingsContentDescription },
                    ) { NavigationGlyph("⚙") }
                }
            },
        )
        if (active) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = {}, label = { Text(modeLabel(record.app.managementMode)) })
                    if (record.app.managementMode == ManagementMode.VERIFICATION.name) {
                        AssistChip(onClick = {}, label = { Text(trustLabel(record)) })
                    }
                    AssistChip(
                        onClick = {},
                        label = { Text(installationSourceLabel(record.app.installationSource)) },
                    )
                }
            }
            item {
                DetailCard(stringResource(R.string.technical_repository)) {
                    DetailValue(stringResource(R.string.label_provider), record.app.provider)
                    DetailValue(stringResource(R.string.label_source_url), record.app.canonicalRepositoryUrl, true)
                    DetailValue(stringResource(R.string.technical_repository_id), record.repositoryBinding?.providerRepositoryId ?: stringResource(R.string.value_unknown), true)
                    DetailValue(stringResource(R.string.technical_identity), record.repositoryBinding?.identityStatus ?: stringResource(R.string.value_not_available))
                    record.latestSourceDiscovery?.let { discovery ->
                        DetailValue(stringResource(R.string.technical_source_discovery), discovery.state)
                        discovery.reason?.let { DetailValue(stringResource(R.string.technical_discovery_reason), it) }
                        DetailValue(stringResource(R.string.technical_source_commit), discovery.resolvedCommitSha ?: stringResource(R.string.value_not_available), true)
                        DetailValue(stringResource(R.string.add_gradle_candidates), discovery.candidateCount.toString())
                    }
                    record.selectedBuildConfiguration?.let { configuration ->
                        DetailValue(
                            stringResource(R.string.technical_build_settings),
                            stringResource(
                                R.string.technical_build_settings_summary,
                                configuration.revision,
                                configuration.validationState,
                            ),
                        )
                        DetailValue(stringResource(R.string.technical_settings_sha256), configuration.contentSha256, true)
                    }
                    DetailValue(stringResource(R.string.technical_release_last_checked), record.app.lastReleaseCheckedAt ?: stringResource(R.string.value_never))
                    DetailValue(stringResource(R.string.technical_release_variant), effectiveVariant(record, globalSettings).displayName())
                    DetailValue(stringResource(R.string.settings_abi), effectiveAbi(record, globalSettings).displayName())
                    DetailValue(stringResource(R.string.technical_apk_limit), "${effectiveLimit(record, globalSettings) / MIB} MiB")
                    if (record.app.savedAssetSelectionJson != null) {
                        DetailValue(
                            stringResource(R.string.technical_saved_selection),
                            stringResource(R.string.technical_saved_selection_active),
                        )
                        TextButton(enabled = !active, onClick = onClearSavedAssetSelection) {
                            Text(stringResource(R.string.technical_clear_saved_selection))
                        }
                    }
                }
            }
            latest?.let { release ->
                item {
                    DetailCard(stringResource(R.string.technical_latest_release)) {
                        DetailValue(stringResource(R.string.technical_release), release.snapshot.releaseName)
                        DetailValue(stringResource(R.string.technical_tag), release.snapshot.tagName)
                        DetailValue(stringResource(R.string.technical_resolved_commit), release.snapshot.resolvedCommitSha, true)
                        DetailValue(stringResource(R.string.technical_target_commitish), release.snapshot.targetCommitishRaw)
                        DetailValue(
                            stringResource(R.string.technical_published),
                            release.snapshot.publishedAt ?: stringResource(R.string.technical_not_supplied),
                        )
                    }
                }
            }
            if (
                latest != null &&
                latest.snapshot.selectedProviderAssetId == null &&
                latest.assets.isNotEmpty()
            ) {
                item {
                    DetailCard(stringResource(R.string.technical_select_apk)) {
                        Text(
                            stringResource(R.string.technical_select_apk_body),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            stringResource(R.string.technical_select_apk_unknown_body),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        latest.assets
                            .sortedWith(compareBy<ReleaseAssetEntity> { it.assetName.lowercase() }.thenBy { it.providerAssetId })
                            .forEach { candidate ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !active) {
                                            selectedReleaseAssetId = candidate.providerAssetId
                                        }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(
                                        selected = selectedReleaseAssetId == candidate.providerAssetId,
                                        onClick = {
                                            selectedReleaseAssetId = candidate.providerAssetId
                                        },
                                        enabled = !active,
                                    )
                                    Column(modifier = Modifier.padding(start = 8.dp)) {
                                        DetailValue(stringResource(R.string.technical_file), candidate.assetName)
                                        DetailValue(stringResource(R.string.technical_provider_size), formatBytes(candidate.providerSizeBytes))
                                        DetailValue(
                                            stringResource(R.string.technical_provider_created),
                                            candidate.providerCreatedAt ?: stringResource(R.string.value_not_available),
                                        )
                                        DetailValue(
                                            stringResource(R.string.technical_content_type),
                                            candidate.contentType ?: stringResource(R.string.technical_not_supplied),
                                        )
                                        DetailValue(stringResource(R.string.technical_filename_hints), releaseCandidateHints(candidate.assetName))
                                        DetailValue(
                                            stringResource(R.string.technical_provider_sha256),
                                            candidate.providerDigestSha256
                                                ?: stringResource(R.string.technical_not_supplied),
                                            monospace = true,
                                        )
                                    }
                                }
                            }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = saveExactFilenameCondition,
                                onCheckedChange = { saveExactFilenameCondition = it },
                                enabled = !active,
                            )
                            Text(
                                stringResource(R.string.technical_remember_exact_filename),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Button(
                            enabled = !active && selectedReleaseAssetId != null,
                            onClick = {
                                selectedReleaseAssetId?.let { providerAssetId ->
                                    onSelectReleaseAsset(
                                        latest.snapshot.releaseSnapshotId,
                                        providerAssetId,
                                        saveExactFilenameCondition,
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.technical_select_download)) }
                    }
                }
            }
            asset?.let { current ->
                item {
                    DetailCard(stringResource(R.string.technical_official_apk)) {
                        DetailValue(stringResource(R.string.technical_asset), current.assetName)
                        DetailValue(stringResource(R.string.technical_selection), current.selectionReason)
                        DetailValue(
                            stringResource(R.string.technical_provider_created),
                            current.providerCreatedAt ?: stringResource(R.string.value_not_available),
                        )
                        DetailValue(
                            stringResource(R.string.technical_download_content_type),
                            current.downloadContentType ?: stringResource(R.string.value_not_available),
                        )
                        DetailValue(stringResource(R.string.technical_provider_sha256), current.providerDigestSha256 ?: stringResource(R.string.value_not_available), true)
                        DetailValue(stringResource(R.string.technical_computed_sha256), current.computedRawSha256 ?: stringResource(R.string.value_not_available), true)
                        DetailValue(stringResource(R.string.label_package), current.packageName ?: stringResource(R.string.value_unknown))
                        DetailValue(stringResource(R.string.technical_version), current.versionName ?: stringResource(R.string.value_not_available))
                        DetailValue(
                            stringResource(R.string.technical_installed),
                            current.installedVersionName?.let { "$it (${current.installedVersionCode})" }
                                ?: stringResource(R.string.state_not_installed),
                        )
                        DetailValue(stringResource(R.string.label_update), updateLabel(current.updateStatus))
                        DetailValue(stringResource(R.string.technical_signer_relation), signerLabel(current.existingInstallStatus))
                        DetailValue(stringResource(R.string.technical_signer), current.currentSignerSha256 ?: stringResource(R.string.value_unknown), true)
                        DetailValue(stringResource(R.string.technical_comparison), current.comparisonEligibility)
                        current.incomparableReason?.let { DetailValue(stringResource(R.string.storage_reason), it) }
                        current.downloadErrorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
            item {
                DetailCard(stringResource(R.string.technical_history)) {
                    Text(
                        stringResource(R.string.technical_history_body),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    record.releases
                        .sortedWith(
                            compareByDescending<com.sanka1610.reprodroid.data.local.ReleaseSnapshotWithAssets> {
                                it.snapshot.lastObservedAt
                            }.thenByDescending { it.snapshot.releaseSnapshotId },
                        )
                        .forEach { observation ->
                            val observedAsset = observation.selectedAsset
                            val availabilityState = observedAsset?.let { selected ->
                                availability.firstOrNull {
                                    it.ownerType == "ANDROID" &&
                                        it.resourceKind == "REFERENCE_APK" &&
                                        it.resourceId == selected.releaseAssetId
                                }?.state
                            } ?: stringResource(R.string.value_unknown)
                            HorizontalDivider()
                            DetailValue(stringResource(R.string.technical_release), "${observation.snapshot.tagName} · ${observation.snapshot.publishedAt}")
                            DetailValue(stringResource(R.string.technical_observation), observation.snapshot.observationSha256, monospace = true)
                            DetailValue(stringResource(R.string.technical_last_observed), observation.snapshot.lastObservedAt)
                            DetailValue(stringResource(R.string.technical_apk_availability), availabilityState)
                        }
                    record.comparisons.sortedByDescending { it.createdAt }.forEach { comparison ->
                        HorizontalDivider()
                        DetailValue(stringResource(R.string.technical_comparison), "${comparison.createdAt} · ${comparison.status}")
                        DetailValue(stringResource(R.string.technical_raw_outcomes), buildString {
                            append(comparison.outcome)
                            if (comparison.protocolVersion >= 2) {
                                append(" / ${comparison.repeatOfficialOutcome} / ${comparison.repeatabilityOutcome}")
                            }
                        })
                    }
                    record.releaseInstallAttempts.sortedByDescending { it.createdAt }.forEach { attempt ->
                        HorizontalDivider()
                        DetailValue(stringResource(R.string.technical_install_attempt), "${attempt.createdAt} · ${attempt.status}")
                    }
                }
            }
            if (record.app.managementMode == ManagementMode.VERIFICATION.name) {
                val comparison = record.currentComparison
                item {
                    DetailCard(stringResource(R.string.technical_comparison)) {
                        DetailValue(stringResource(R.string.technical_trust), trustLabel(record))
                        Text(
                            stringResource(R.string.technical_comparison_body),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (comparison == null) {
                            Text(
                                stringResource(R.string.technical_build_compare_body),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Button(
                                enabled = !active &&
                                    record.app.releaseDiscoveryStatus == ReleaseDiscoveryStatus.AVAILABLE.name &&
                                    asset?.downloadStatus == ReferenceDownloadStatus.VERIFIED.name &&
                                    asset.comparisonEligibility != ComparisonEligibility.INCOMPARABLE.name,
                                onClick = onStartComparison,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(stringResource(R.string.technical_build_compare)) }
                        } else {
                            DetailValue(stringResource(R.string.technical_status), comparison.status)
                            DetailValue(stringResource(R.string.label_official_primary), comparison.outcome)
                            if (comparison.protocolVersion >= 2) {
                                DetailValue(stringResource(R.string.label_official_repeat), comparison.repeatOfficialOutcome)
                                DetailValue(stringResource(R.string.label_local_repeatability), comparison.repeatabilityOutcome)
                            }
                            DetailValue(stringResource(R.string.technical_expected_recipe), comparison.expectedRecipeId)
                            DetailValue(
                                stringResource(R.string.technical_dependency_pinning, "A"),
                                dependencyPinningLabel(comparison.runnerDependencyPinning),
                            )
                            if (comparison.protocolVersion >= 2 && comparison.repeatRunnerJobId != null) {
                                DetailValue(
                                    stringResource(R.string.technical_dependency_pinning, "B"),
                                    dependencyPinningLabel(comparison.repeatRunnerDependencyPinning),
                                )
                                if (comparison.runnerDependencyPinning != comparison.repeatRunnerDependencyPinning) {
                                    Text(
                                        stringResource(R.string.technical_dependency_policy_mismatch),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                            if (
                                comparison.runnerDependencyPinning == "LOCKFILE_OFFLINE" ||
                                comparison.repeatRunnerDependencyPinning == "LOCKFILE_OFFLINE"
                            ) {
                                Text(
                                    stringResource(R.string.technical_offline_warning),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            DetailValue(stringResource(R.string.technical_expected_commit), comparison.expectedCommitSha, true)
                            comparison.runnerResolvedCommitSha?.let { DetailValue(stringResource(R.string.technical_runner_commit), it, true) }
                            comparison.repeatRunnerResolvedCommitSha?.let {
                                DetailValue(stringResource(R.string.technical_repeat_runner_commit), it, true)
                            }
                            if (comparison.protocolVersion >= 2) {
                                val buildARecord = runnerJobs[comparison.runnerJobId]
                                val buildBRecord = comparison.repeatRunnerJobId?.let(runnerJobs::get)
                                val buildAJob = buildARecord?.job
                                val buildBJob = buildBRecord?.job
                                val buildAManifest = buildEnvironmentManifests[comparison.runnerJobId]
                                val buildBManifest = comparison.repeatRunnerJobId?.let(buildEnvironmentManifests::get)
                                val environmentComparison = compareBuildEnvironments(
                                    buildAJob,
                                    buildBJob,
                                    buildAManifest,
                                    buildBManifest,
                                )
                                Text(stringResource(R.string.technical_environment_evidence), style = MaterialTheme.typography.titleSmall)
                                DetailValue(stringResource(R.string.technical_build_sandbox, "A"), sandboxSelectionText(buildAJob))
                                DetailValue(stringResource(R.string.technical_build_sandbox, "B"), sandboxSelectionText(buildBJob))
                                DetailValue(stringResource(R.string.technical_build_execution, "A"), sandboxManifestText(buildAManifest?.manifest?.sandboxJson))
                                DetailValue(stringResource(R.string.technical_build_execution, "B"), sandboxManifestText(buildBManifest?.manifest?.sandboxJson))
                                sandboxWarnings[comparison.runnerJobId]?.let { DetailValue(stringResource(R.string.technical_build_sandbox_warning, "A"), it) }
                                comparison.repeatRunnerJobId?.let(sandboxWarnings::get)?.let { DetailValue(stringResource(R.string.technical_build_sandbox_warning, "B"), it) }
                                Text(
                                    stringResource(R.string.technical_environment_evidence_body),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                SourceScanEvidence(stringResource(R.string.technical_build_source_scan, "A"), buildARecord?.sourceScan)
                                SourceScanEvidence(stringResource(R.string.technical_build_source_scan, "B"), buildBRecord?.sourceScan)
                                sourceScanWarnings[comparison.runnerJobId]?.let { warning ->
                                    DetailValue(stringResource(R.string.technical_build_source_scan_warning, "A"), "${warning.code}: ${warning.message}")
                                }
                                comparison.repeatRunnerJobId?.let(sourceScanWarnings::get)?.let { warning ->
                                    DetailValue(stringResource(R.string.technical_build_source_scan_warning, "B"), "${warning.code}: ${warning.message}")
                                }
                                buildAManifest?.let { evidence ->
                                    DetailValue(
                                        stringResource(R.string.technical_build_environment, "A"),
                                        stringResource(
                                            R.string.technical_build_environment_value,
                                            evidence.manifest.javaVersion,
                                            evidence.manifest.javaVendor,
                                            evidence.manifest.gradleVersion,
                                            evidence.manifest.androidSdkApiLevel,
                                            evidence.manifest.buildToolsVersion,
                                            determinismSummary(
                                                evidence.manifest.sourceDateEpoch,
                                                evidence.manifest.noBuildCache,
                                                evidence.manifest.fixedLocale,
                                            ),
                                        ),
                                    )
                                }
                                buildBManifest?.let { evidence ->
                                    DetailValue(
                                        stringResource(R.string.technical_build_environment, "B"),
                                        stringResource(
                                            R.string.technical_build_environment_value,
                                            evidence.manifest.javaVersion,
                                            evidence.manifest.javaVendor,
                                            evidence.manifest.gradleVersion,
                                            evidence.manifest.androidSdkApiLevel,
                                            evidence.manifest.buildToolsVersion,
                                            determinismSummary(
                                                evidence.manifest.sourceDateEpoch,
                                                evidence.manifest.noBuildCache,
                                                evidence.manifest.fixedLocale,
                                            ),
                                        ),
                                    )
                                }
                                buildManifestWarnings[comparison.runnerJobId]?.let { warning ->
                                    DetailValue(stringResource(R.string.technical_build_manifest_warning, "A"), "${warning.code}: ${warning.message}")
                                }
                                comparison.repeatRunnerJobId?.let(buildManifestWarnings::get)?.let { warning ->
                                    DetailValue(stringResource(R.string.technical_build_manifest_warning, "B"), "${warning.code}: ${warning.message}")
                                }
                                if (environmentComparison.comparable) {
                                    DetailValue(
                                        stringResource(R.string.technical_dependency_multiset),
                                        stringResource(
                                            R.string.technical_dependency_multiset_value,
                                            environmentComparison.sameCount,
                                            environmentComparison.changedCount,
                                            environmentComparison.buildAOnlyCount,
                                            environmentComparison.buildBOnlyCount,
                                        ),
                                    )
                                    environmentComparison.differences.asSequence()
                                        .filter { it.kind != DependencyDifferenceKind.SAME }
                                        .take(MAX_DEPENDENCY_DIFFERENCES_IN_UI)
                                        .forEach { difference ->
                                            DetailValue(
                                                difference.fileName,
                                                when (difference.kind) {
                                                    DependencyDifferenceKind.CHANGED -> stringResource(R.string.technical_difference_changed)
                                                    DependencyDifferenceKind.BUILD_A_ONLY -> stringResource(R.string.technical_difference_build_a_only)
                                                    DependencyDifferenceKind.BUILD_B_ONLY -> stringResource(R.string.technical_difference_build_b_only)
                                                    DependencyDifferenceKind.SAME -> stringResource(R.string.technical_difference_same)
                                                },
                                            )
                                        }
                                } else {
                                    DetailValue(stringResource(R.string.technical_dependency_comparison), environmentComparison.reason ?: stringResource(R.string.value_not_available))
                                }
                                if (
                                    buildAJob?.effectiveRecipeId != buildBJob?.effectiveRecipeId ||
                                    buildAJob?.effectiveVariantName != buildBJob?.effectiveVariantName ||
                                    buildAManifest?.manifest?.javaVersion != buildBManifest?.manifest?.javaVersion ||
                                    buildAManifest?.manifest?.sourceDateEpoch != buildBManifest?.manifest?.sourceDateEpoch ||
                                    buildAManifest?.manifest?.noBuildCache != buildBManifest?.manifest?.noBuildCache ||
                                    buildAManifest?.manifest?.fixedLocale != buildBManifest?.manifest?.fixedLocale
                                ) {
                                    Text(
                                        stringResource(R.string.technical_environment_mismatch),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                            comparison.incomparableReason?.let { DetailValue(stringResource(R.string.storage_reason), it) }
                            comparison.repeatIncomparableReason?.let { DetailValue(stringResource(R.string.technical_repeat_reason), it) }
                            record.currentAdvancedComparisonSummaries.forEach { summary ->
                                Text(
                                    when (summary.axis) {
                                        "OFFICIAL_PRIMARY" -> stringResource(R.string.technical_advanced_official_primary)
                                        "OFFICIAL_REPEAT" -> stringResource(R.string.technical_advanced_official_repeat)
                                        "LOCAL_REPEATABILITY" -> stringResource(R.string.technical_advanced_local_repeatability)
                                        else -> stringResource(R.string.technical_advanced_other, summary.axis)
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                DetailValue(stringResource(R.string.technical_apk_entries), "${summary.inventoryOutcome} (${summary.entryCount})")
                                DetailValue(
                                    stringResource(R.string.technical_entry_changes),
                                    stringResource(
                                        R.string.technical_entry_changes_value,
                                        summary.sameCount,
                                        summary.changedCount,
                                        summary.addedCount,
                                        summary.missingCount,
                                    ),
                                )
                                DetailValue(stringResource(R.string.technical_dex_structure), summary.dexStructuralOutcome)
                                DetailValue(stringResource(R.string.technical_manifest_meaning), summary.manifestSemanticOutcome)
                                DetailValue(stringResource(R.string.technical_resource_table_meaning), summary.resourceTableSemanticOutcome)
                                DetailValue(stringResource(R.string.technical_semantic_differences), summary.semanticDifferenceCount.toString())
                                record.currentSemanticDifferenceEvidence
                                    .asSequence()
                                    .filter { it.axis == summary.axis }
                                    .take(MAX_SEMANTIC_DIFFERENCES_IN_UI)
                                    .forEach { difference ->
                                        DetailValue(
                                            difference.component,
                                            "${difference.result}: ${difference.stableKey}",
                                        )
                                    }
                                summary.reason?.let { DetailValue(stringResource(R.string.technical_advanced_reason), it) }
                            }
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
                                    enabled = !active,
                                    onClick = onStartComparison,
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text(stringResource(R.string.technical_run_again)) }
                                else -> Button(
                                    enabled = !active,
                                    onClick = { onRefreshComparison(comparison.comparisonRunId) },
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text(stringResource(R.string.technical_refresh_comparison)) }
                            }
                        }
                    }
                }
            }
            item {
                DetailCard(stringResource(R.string.technical_installation)) {
                    DetailValue(stringResource(R.string.technical_source), installationSourceLabel(record.app.installationSource))
                    if (record.app.installationSource == InstallationSource.LOCAL_BUILD.name) {
                        Text(
                            stringResource(R.string.technical_local_install_body),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (warningRequired && canInstall) {
                        Row(verticalAlignment = Alignment.Top) {
                            Checkbox(
                                checked = installRiskConfirmed,
                                onCheckedChange = { installRiskConfirmed = it },
                            )
                            Text(
                                stringResource(R.string.technical_install_risk),
                                modifier = Modifier.padding(top = 10.dp),
                            )
                        }
                    }
                    if (!canRequestPackageInstalls) {
                        TextButton(
                            onClick = {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                        "package:${context.packageName}".toUri(),
                                    ),
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.technical_allow_installs)) }
                    }
                    Button(
                        enabled = !active && canInstall &&
                            canRequestPackageInstalls &&
                            (!warningRequired || installRiskConfirmed),
                        onClick = { onInstall(installRiskConfirmed) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(
                                if (asset?.updateStatus == UpdateStatus.UPDATE_AVAILABLE.name) {
                                    R.string.technical_update
                                } else {
                                    R.string.technical_install
                                },
                            ),
                        )
                    }
                    record.latestReleaseInstallAttempt?.let { attempt ->
                        DetailValue(stringResource(R.string.technical_latest_install_attempt), attempt.status)
                        attempt.statusMessage?.let { DetailValue(stringResource(R.string.technical_installer_message), it) }
                    }
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppPreferencesScreen(
    record: RegisteredAppRecord,
    globalSettings: GlobalSettingsEntity,
    releaseSettings: ReleaseCheckSettingsEntity,
    releaseOverride: AppReleaseCheckOverrideEntity,
    saving: Boolean,
    onBack: () -> Unit,
    onSave: (AppSettingsUpdate) -> Unit,
    onSaveBuildConfiguration: (Long?, BuildConfigurationInput) -> Unit,
    onUpdateReleaseOverride: (AppReleaseCheckOverrideEntity) -> Unit,
) {
    BackHandler(onBack = onBack)
    val appSettingsBackDescription = stringResource(R.string.action_back)
    var mode by rememberSaveable(record.app.registeredAppId) {
        mutableStateOf(enumValue(record.app.managementMode, ManagementMode.VERIFICATION))
    }
    var source by rememberSaveable(record.app.registeredAppId) {
        mutableStateOf(enumValue(record.app.installationSource, InstallationSource.OFFICIAL_RELEASE))
    }
    var abiChoice by rememberSaveable(record.app.registeredAppId) {
        mutableStateOf(if (record.app.useGlobalPreferredAbi) "GLOBAL" else record.app.preferredAbi)
    }
    var localRiskConfirmed by rememberSaveable(record.app.registeredAppId) {
        mutableStateOf(source == InstallationSource.LOCAL_BUILD)
    }
    val sourceLocked = record.latestRelease?.selectedAsset?.installedVersionCode != null
    val initialBuildConfiguration = remember(record.app.registeredAppId) {
        record.selectedBuildConfiguration?.let { configuration ->
            runCatching {
                BuildConfigurationValidator.decodeCanonical(
                    configuration.canonicalJson,
                    configuration.contentSha256,
                )
            }.getOrNull()
        } ?: BuildConfigurationInput()
    }
    var buildRoot by rememberSaveable(record.app.registeredAppId) {
        mutableStateOf(initialBuildConfiguration.buildRoot.orEmpty())
    }
    var modulePath by rememberSaveable(record.app.registeredAppId) {
        mutableStateOf(initialBuildConfiguration.modulePath.orEmpty())
    }
    var buildVariant by rememberSaveable(record.app.registeredAppId) {
        mutableStateOf(initialBuildConfiguration.variant.orEmpty())
    }
    var buildTasks by rememberSaveable(record.app.registeredAppId) {
        mutableStateOf(initialBuildConfiguration.tasks.joinToString("\n"))
    }
    var javaMajor by rememberSaveable(record.app.registeredAppId) {
        mutableStateOf(initialBuildConfiguration.javaMajor?.toString().orEmpty())
    }
    var gradleVersion by rememberSaveable(record.app.registeredAppId) {
        mutableStateOf(initialBuildConfiguration.gradleVersion.orEmpty())
    }
    var compileSdk by rememberSaveable(record.app.registeredAppId) {
        mutableStateOf(initialBuildConfiguration.compileSdk?.toString().orEmpty())
    }
    var buildToolsVersion by rememberSaveable(record.app.registeredAppId) {
        mutableStateOf(initialBuildConfiguration.buildToolsVersion.orEmpty())
    }
    var ndkVersion by rememberSaveable(record.app.registeredAppId) {
        mutableStateOf(initialBuildConfiguration.ndkVersion.orEmpty())
    }
    var cmakeVersion by rememberSaveable(record.app.registeredAppId) {
        mutableStateOf(initialBuildConfiguration.cmakeVersion.orEmpty())
    }
    val numericBuildFieldsValid = listOf(javaMajor, compileSdk).all { value ->
        value.isBlank() || value.trim().toIntOrNull() != null
    }
    var registrationExpanded by rememberSaveable(record.app.registeredAppId, "registration") {
        mutableStateOf(false)
    }
    var updatesExpanded by rememberSaveable(record.app.registeredAppId, "updates") {
        mutableStateOf(false)
    }
    var defaultsExpanded by rememberSaveable(record.app.registeredAppId, "defaults") {
        mutableStateOf(false)
    }
    var buildExpanded by rememberSaveable(record.app.registeredAppId, "build") {
        mutableStateOf(false)
    }
    val savePreferences = {
        onSave(
            AppSettingsUpdate(
                managementMode = mode,
                installationSource = source,
                releaseVariantPreference = enumValue(
                    record.app.releaseVariantPreference,
                    ReleaseVariantPreference.RELEASE,
                ),
                useGlobalReleaseVariant = record.app.useGlobalReleaseVariant,
                preferredAbi = enumValue(
                    abiChoice.takeUnless { it == "GLOBAL" }
                        ?: globalSettings.defaultPreferredAbi,
                    PreferredAbi.ARM64_V8A,
                ),
                useGlobalPreferredAbi = abiChoice == "GLOBAL",
                maxApkSizeBytes = record.app.maxApkSizeBytes,
                useGlobalMaxApkSize = record.app.useGlobalMaxApkSize,
                localBuildRiskConfirmed = localRiskConfirmed,
            ),
        )
    }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.app_settings_for, record.app.resolvedDisplayName)) },
            navigationIcon = {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.semantics { contentDescription = appSettingsBackDescription },
                ) { NavigationGlyph("‹") }
            },
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                AccordionSection(
                    title = stringResource(R.string.app_settings_registration),
                    expanded = registrationExpanded,
                    onToggle = { registrationExpanded = !registrationExpanded },
                ) {
                    Text(
                        stringResource(R.string.app_settings_registration_body),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    DropdownSetting(
                        stringResource(R.string.settings_management_mode),
                        mode,
                        ManagementMode.entries
                            .filter {
                                !sourceLocked || source != InstallationSource.LOCAL_BUILD ||
                                    it == ManagementMode.VERIFICATION
                            }
                            .associateWith {
                                stringResource(
                                    if (it == ManagementMode.VERIFICATION) {
                                        R.string.mode_verification
                                    } else {
                                        R.string.mode_acquisition
                                    },
                                )
                            },
                        onSelect = {
                            mode = it
                            if (it == ManagementMode.ACQUISITION && !sourceLocked) {
                                source = InstallationSource.OFFICIAL_RELEASE
                            }
                        },
                    )
                    DropdownSetting(
                        stringResource(R.string.settings_installation_source),
                        source,
                        InstallationSource.entries
                            .filter {
                                mode == ManagementMode.VERIFICATION ||
                                    it == InstallationSource.OFFICIAL_RELEASE
                            }
                            .associateWith {
                                stringResource(
                                    if (it == InstallationSource.OFFICIAL_RELEASE) {
                                        R.string.installation_official
                                    } else {
                                        R.string.installation_local
                                    },
                                )
                            },
                        onSelect = {
                            source = it
                            if (it == InstallationSource.LOCAL_BUILD) localRiskConfirmed = false
                        },
                        enabled = !sourceLocked,
                        supportingText = if (sourceLocked) {
                            stringResource(
                                R.string.app_settings_source_locked,
                                record.latestRelease?.selectedAsset?.packageName
                                    ?: stringResource(R.string.app_settings_target_package),
                            )
                        } else {
                            stringResource(R.string.app_settings_source_help)
                        },
                    )
                    if (source == InstallationSource.LOCAL_BUILD && !sourceLocked) {
                    Row(verticalAlignment = Alignment.Top) {
                        Checkbox(checked = localRiskConfirmed, onCheckedChange = { localRiskConfirmed = it })
                        Text(stringResource(R.string.app_settings_local_risk), Modifier.padding(top = 10.dp))
                    }
                    }
                    Button(
                        enabled = !saving && (source != InstallationSource.LOCAL_BUILD || localRiskConfirmed),
                        onClick = savePreferences,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(
                                if (saving) R.string.app_settings_saving else R.string.app_settings_save,
                            ),
                        )
                    }
                }
            }
            item {
                AccordionSection(
                    title = stringResource(R.string.settings_updates),
                    expanded = updatesExpanded,
                    onToggle = { updatesExpanded = !updatesExpanded },
                ) {
                    AppUpdateSettingsContent(
                        global = releaseSettings,
                        override = releaseOverride,
                        showDividers = globalSettings.showSettingsDividers,
                        onUpdate = onUpdateReleaseOverride,
                    )
                }
            }
            item {
                AccordionSection(
                    title = stringResource(R.string.app_settings_inherited),
                    expanded = defaultsExpanded,
                    onToggle = { defaultsExpanded = !defaultsExpanded },
                ) {
                    Text(
                        stringResource(R.string.app_settings_inherited_body),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    DropdownSetting(
                        stringResource(R.string.settings_abi),
                        abiChoice,
                        linkedMapOf(
                            "GLOBAL" to stringResource(
                                R.string.app_settings_use_global,
                                globalSettings.defaultPreferredAbi.displayEnum(),
                            ),
                        ) + PreferredAbi.entries.associate { it.name to it.displayName() },
                        onSelect = { abiChoice = it },
                    )
                    Text(
                        stringResource(R.string.app_settings_selection_change),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Button(
                        enabled = !saving && (source != InstallationSource.LOCAL_BUILD || localRiskConfirmed),
                        onClick = savePreferences,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(
                                if (saving) R.string.app_settings_saving else R.string.app_settings_save,
                            ),
                        )
                    }
                }
            }
            item {
                AccordionSection(
                    title = stringResource(R.string.app_settings_build_configuration),
                    expanded = buildExpanded,
                    onToggle = { buildExpanded = !buildExpanded },
                ) {
                    Text(
                        stringResource(R.string.app_settings_build_configuration_body),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    BuildSettingField(stringResource(R.string.build_root), buildRoot, { buildRoot = it }, ". or relative path")
                    BuildSettingField(stringResource(R.string.build_module_path), modulePath, { modulePath = it }, ":app")
                    BuildSettingField(stringResource(R.string.build_variant), buildVariant, { buildVariant = it }, "release")
                    OutlinedTextField(
                        value = buildTasks,
                        onValueChange = { buildTasks = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.build_tasks)) },
                        supportingText = { Text(":app:assembleRelease") },
                        minLines = 2,
                    )
                    BuildSettingField(stringResource(R.string.build_java_major), javaMajor, { javaMajor = it }, "21")
                    BuildSettingField(stringResource(R.string.build_gradle_version), gradleVersion, { gradleVersion = it }, "9.1.0")
                    BuildSettingField("compileSdk", compileSdk, { compileSdk = it }, "36")
                    BuildSettingField(stringResource(R.string.build_tools_version), buildToolsVersion, { buildToolsVersion = it }, "36.0.0")
                    BuildSettingField(stringResource(R.string.build_ndk_version), ndkVersion, { ndkVersion = it }, "")
                    BuildSettingField(stringResource(R.string.build_cmake_version), cmakeVersion, { cmakeVersion = it }, "")
                    Button(
                        enabled = !saving && numericBuildFieldsValid,
                        onClick = {
                            fun optional(value: String) = value.trim().takeIf(String::isNotEmpty)
                            onSaveBuildConfiguration(
                                record.selectedBuildConfiguration?.revision,
                                BuildConfigurationInput(
                                    buildRoot = optional(buildRoot),
                                    modulePath = optional(modulePath),
                                    variant = optional(buildVariant),
                                    tasks = buildTasks.lines().map(String::trim).filter(String::isNotEmpty),
                                    javaMajor = optional(javaMajor)?.toIntOrNull(),
                                    gradleVersion = optional(gradleVersion),
                                    compileSdk = optional(compileSdk)?.toIntOrNull(),
                                    buildToolsVersion = optional(buildToolsVersion),
                                    ndkVersion = optional(ndkVersion),
                                    cmakeVersion = optional(cmakeVersion),
                                ),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(
                                if (saving) R.string.app_settings_saving else R.string.build_save_configuration,
                            ),
                        )
                    }
                    if (!numericBuildFieldsValid) {
                        Text(
                            stringResource(R.string.build_numeric_error),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun BuildSettingField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    hint: String,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        supportingText = hint.takeIf(String::isNotEmpty)?.let { text -> { Text(text) } },
        singleLine = true,
    )
}

@Composable
internal fun ManagedAppIcon(record: RegisteredAppRecord, size: Dp = 52.dp) {
    val context = LocalContext.current
    val assetId = record.latestRelease?.selectedAsset
        ?.takeIf { it.downloadStatus == ReferenceDownloadStatus.VERIFIED.name }
        ?.releaseAssetId
    val iconFile = remember(context.filesDir, assetId) {
        val safeId = assetId?.let { runCatching { UUID.fromString(it).toString() }.getOrNull() }
        safeId?.let { File(context.filesDir, "reference-icons/$it.png") }
    }
    val lastModified = iconFile?.takeIf(File::isFile)?.lastModified() ?: 0L
    val storedImage = remember(iconFile?.absolutePath, lastModified) {
        iconFile?.takeIf(File::isFile)?.let { BitmapFactory.decodeFile(it.absolutePath) }?.asImageBitmap()
    }
    val selfImage = remember(context.packageName, record.app.registeredAppId) {
        if (isSelfRegistration(record)) {
            context.packageManager.getApplicationIcon(context.applicationInfo)
                .toBitmap(width = 192, height = 192)
                .asImageBitmap()
        } else {
            null
        }
    }
    val image = storedImage ?: selfImage
    Surface(
        modifier = Modifier.size(size),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.large),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Text(record.app.resolvedDisplayName.take(2).uppercase(), color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

private fun PreferredAbi.displayName(): String = when (this) {
    PreferredAbi.ARM64_V8A -> "arm64-v8a"
    PreferredAbi.ARMEABI_V7A -> "armeabi-v7a"
    PreferredAbi.X86_64 -> "x86_64"
    PreferredAbi.UNIVERSAL -> "universal"
}

@Composable
private fun releaseCandidateHints(assetName: String): String {
    val filename = assetName.lowercase()
    val abi = when {
        "arm64-v8a" in filename || "arm64_v8a" in filename -> "arm64-v8a"
        "armeabi-v7a" in filename || "armeabi_v7a" in filename || "arm-v7a" in filename -> "armeabi-v7a"
        "x86_64" in filename || "x86-64" in filename -> "x86_64"
        "universal" in filename -> "universal"
        else -> stringResource(R.string.technical_not_inferred)
    }
    val variant = when {
        "debug" in filename -> "debug"
        "preview" in filename -> "preview"
        "release" in filename -> "release"
        else -> stringResource(R.string.technical_not_inferred)
    }
    return stringResource(R.string.technical_filename_inference, abi, variant)
}
