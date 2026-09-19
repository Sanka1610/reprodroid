package com.sanka1610.reprodroid.ui.appdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sanka1610.reprodroid.R
import com.sanka1610.reprodroid.data.local.BuildEnvironmentManifestWithDependencies
import com.sanka1610.reprodroid.data.local.GlobalSettingsEntity
import com.sanka1610.reprodroid.data.local.JobRecord
import com.sanka1610.reprodroid.data.local.PreferredAbi
import com.sanka1610.reprodroid.data.local.RegisteredAppRecord
import com.sanka1610.reprodroid.data.local.ResourceAvailabilityEntity
import com.sanka1610.reprodroid.data.repository.BuildManifestWarning
import com.sanka1610.reprodroid.data.repository.SourceScanWarning
import com.sanka1610.reprodroid.ui.*
import com.sanka1610.reprodroid.ui.shared.*

@Composable
internal fun AppTechnicalScreen(
    record: RegisteredAppRecord,
    globalSettings: GlobalSettingsEntity,
    active: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onClearSavedAssetSelection: () -> Unit,
    runnerJobs: Map<String, JobRecord>,
    buildEnvironmentManifests: Map<String, BuildEnvironmentManifestWithDependencies>,
    buildManifestWarnings: Map<String, BuildManifestWarning>,
    sourceScanWarnings: Map<String, SourceScanWarning>,
    sandboxWarnings: Map<String, String>,
    availability: List<ResourceAvailabilityEntity>,
) {
    BackScaffoldTitle(stringResource(R.string.app_flow_details), onBack) {
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { TextButton(enabled = !active, onClick = onRefresh) { Text(stringResource(R.string.action_refresh_release_data)) } }
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
            record.latestRelease?.let { release -> item {
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
            } }
            record.latestRelease?.selectedAsset?.let { current -> item {
                    DetailCard(stringResource(R.string.technical_official_apk)) {
                        DetailValue(stringResource(R.string.technical_asset), current.assetName)
                        DetailValue(stringResource(R.string.label_package), current.packageName ?: stringResource(R.string.value_unknown))
                        DetailValue(stringResource(R.string.technical_version), current.versionName ?: stringResource(R.string.value_not_available))
                        DetailValue(
                            stringResource(R.string.technical_installed),
                            current.installedVersionName?.let { "$it (${current.installedVersionCode})" }
                                ?: stringResource(R.string.state_not_installed),
                        )
                        DetailValue(stringResource(R.string.label_update), updateLabel(current.updateStatus))
                        DetailValue(stringResource(R.string.technical_signer_relation), signerLabel(current.existingInstallStatus))
                        DetailValue(stringResource(R.string.technical_comparison), current.comparisonEligibility)
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
                        DetailValue(stringResource(R.string.technical_signer), current.currentSignerSha256 ?: stringResource(R.string.value_unknown), true)
                        current.incomparableReason?.let { DetailValue(stringResource(R.string.storage_reason), it) }
                        current.downloadErrorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
            } }
            item { ComparisonTechnicalContent(record, runnerJobs, buildEnvironmentManifests, buildManifestWarnings, sourceScanWarnings, sandboxWarnings) }
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
        }
    }
}

internal fun PreferredAbi.displayName(): String = when (this) {
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
