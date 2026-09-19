package com.sanka1610.reprodroid.ui.appdetail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sanka1610.reprodroid.R
import com.sanka1610.reprodroid.data.local.GlobalSettingsEntity
import com.sanka1610.reprodroid.data.local.ManagementMode
import com.sanka1610.reprodroid.data.local.ReferenceDownloadStatus
import com.sanka1610.reprodroid.data.local.RegisteredAppRecord
import com.sanka1610.reprodroid.data.local.ReleaseAssetEntity
import com.sanka1610.reprodroid.data.local.ResourceAvailabilityEntity
import com.sanka1610.reprodroid.data.local.UpdateStatus
import com.sanka1610.reprodroid.ui.*
import com.sanka1610.reprodroid.ui.shared.*

@Composable
internal fun AppAcquisitionScreen(
    record: RegisteredAppRecord,
    globalSettings: GlobalSettingsEntity,
    active: Boolean,
    availability: List<ResourceAvailabilityEntity>,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSelectReleaseAsset: (String, String, Boolean) -> Unit,
    onInstall: (Boolean) -> Unit,
    onVerification: () -> Unit,
    onTechnical: () -> Unit,
) {
    val latest = record.latestRelease
    val asset = latest?.selectedAsset
    val referenceAvailable = referenceApkAvailable(asset, availability)
    var selectedReleaseAssetId by rememberSaveable(record.app.registeredAppId, latest?.snapshot?.releaseSnapshotId, latest?.snapshot?.observationSha256) { mutableStateOf<String?>(null) }
    val effectiveSelectedAssetId = selectedReleaseAssetId?.takeIf { id -> latest?.assets?.any { it.providerAssetId == id } == true }
        ?: latest?.assets?.singleOrNull()?.providerAssetId
    BackScaffoldTitle(stringResource(R.string.app_acquisition_open), onBack) {
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { Text(record.app.resolvedDisplayName, style = MaterialTheme.typography.titleLarge) }
            if (active) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (latest == null || latest.assets.isEmpty()) {
                item {
                    Text(stringResource(R.string.app_flow_no_apk))
                    TextButton(enabled = !active, onClick = onRefresh) { Text(stringResource(R.string.app_flow_check_release)) }
                }
            } else if (asset == null) {
                item {
                    DetailCard(stringResource(R.string.technical_select_apk)) {
                        Text(
                            stringResource(
                                if (latest.assets.size == 1) R.string.technical_single_apk_body
                                else R.string.technical_select_apk_body,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        latest.assets
                            .sortedWith(compareBy<ReleaseAssetEntity> { it.assetName.lowercase() }.thenBy { it.providerAssetId })
                            .forEach { candidate ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !active && latest.assets.size > 1) {
                                            selectedReleaseAssetId = candidate.providerAssetId
                                        }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (latest.assets.size > 1) RadioButton(
                                        selected = effectiveSelectedAssetId == candidate.providerAssetId,
                                        onClick = {
                                            selectedReleaseAssetId = candidate.providerAssetId
                                        },
                                        enabled = !active,
                                    )
                                    Column(modifier = Modifier.padding(start = 8.dp)) {
                                        DetailValue(stringResource(R.string.technical_file), candidate.assetName)
                                        DetailValue(stringResource(R.string.technical_provider_size), formatBytes(candidate.providerSizeBytes))
                                    }
                                }
                            }
                        Button(
                            enabled = !active && effectiveSelectedAssetId != null,
                            onClick = {
                                effectiveSelectedAssetId?.let { providerAssetId ->
                                    onSelectReleaseAsset(
                                        latest.snapshot.releaseSnapshotId,
                                        providerAssetId,
                                        false,
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                stringResource(
                                    if (latest.assets.size == 1) R.string.technical_download_apk
                                    else R.string.technical_select_download,
                                ),
                            )
                        }
                    }
                }
            } else {
                item {
                    DetailCard(stringResource(R.string.technical_official_apk)) {
                        Text(asset.assetName, style = MaterialTheme.typography.titleMedium)
                        Text(formatBytes(asset.providerSizeBytes))
                        Text(if (asset.downloadStatus == ReferenceDownloadStatus.VERIFIED.name && !referenceAvailable) stringResource(R.string.app_flow_apk_missing) else statusLabel(asset.downloadStatus))
                        asset.versionName?.let { Text(it) }
                        asset.downloadErrorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        if (!referenceAvailable) {
                            Button(enabled = !active, onClick = { onSelectReleaseAsset(latest.snapshot.releaseSnapshotId, asset.providerAssetId, false) }) {
                                Text(stringResource(R.string.technical_download_apk))
                            }
                        } else {
                            Text(updateLabel(asset.updateStatus))
                        }
                    }
                }
                if (referenceAvailable) {
                    if (record.app.managementMode == ManagementMode.VERIFICATION.name) item {
                        OutlinedButton(onClick = onVerification, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.app_verification_open)) }
                    }
                    if (asset.updateStatus in setOf(UpdateStatus.NOT_INSTALLED.name, UpdateStatus.UPDATE_AVAILABLE.name)) item { AppInstallationContent(record, globalSettings, active, onInstall) }
                }
            }
            item { TextButton(onClick = onTechnical) { Text(stringResource(R.string.app_flow_details)) } }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}
