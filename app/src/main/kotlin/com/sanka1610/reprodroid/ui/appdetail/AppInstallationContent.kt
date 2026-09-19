package com.sanka1610.reprodroid.ui.appdetail

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sanka1610.reprodroid.R
import com.sanka1610.reprodroid.data.local.GlobalSettingsEntity
import com.sanka1610.reprodroid.data.local.InstallationSource
import com.sanka1610.reprodroid.data.local.InstallerMode
import com.sanka1610.reprodroid.data.local.ManagementMode
import com.sanka1610.reprodroid.data.local.ReferenceDownloadStatus
import com.sanka1610.reprodroid.data.local.RegisteredAppRecord
import com.sanka1610.reprodroid.data.local.TrustLevel
import com.sanka1610.reprodroid.data.local.UpdateStatus
import com.sanka1610.reprodroid.data.repository.PrivilegedInstallPolicy
import com.sanka1610.reprodroid.ui.*
import com.sanka1610.reprodroid.ui.shared.*

@Composable
internal fun AppInstallationContent(record: RegisteredAppRecord, globalSettings: GlobalSettingsEntity, active: Boolean, onInstall: (Boolean) -> Unit) {
    val asset = record.latestRelease?.selectedAsset
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var installRiskConfirmed by remember(record.app.registeredAppId, record.latestRelease?.snapshot?.observationSha256, asset?.releaseAssetId, asset?.computedRawSha256, asset?.existingInstallStatus, record.currentComparison?.comparisonRunId, record.trustLevel, record.app.installationSource) { mutableStateOf(false) }
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
    val canInstall = asset?.downloadStatus == ReferenceDownloadStatus.VERIFIED.name && asset.updateStatus in setOf(
        UpdateStatus.NOT_INSTALLED.name,
        UpdateStatus.UPDATE_AVAILABLE.name,
    )
    val warningRequired = (
        record.app.managementMode == ManagementMode.VERIFICATION.name &&
            record.trustLevel != TrustLevel.REPRODUCIBLE
        ) ||
        asset?.existingInstallStatus == "SIGNER_MISMATCH"
    val privilegedEligible = PrivilegedInstallPolicy.isEligible(
        requiresRiskConfirmation = warningRequired,
        existingInstallStatus = asset?.existingInstallStatus,
        trustLevel = record.trustLevel,
    )
    val usePrivilegedInstaller =
        globalSettings.installerMode == InstallerMode.SHIZUKU.name && privilegedEligible
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
        if (globalSettings.installerMode == InstallerMode.SHIZUKU.name && !privilegedEligible && canInstall) {
            Text(
                stringResource(R.string.technical_shizuku_fallback),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (canInstall && !usePrivilegedInstaller && !canRequestPackageInstalls) {
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
                (usePrivilegedInstaller || canRequestPackageInstalls) &&
                (!warningRequired || installRiskConfirmed),
            onClick = { onInstall(installRiskConfirmed) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                stringResource(
                    if (usePrivilegedInstaller) {
                        if (asset?.updateStatus == UpdateStatus.UPDATE_AVAILABLE.name) {
                            R.string.technical_update_shizuku
                        } else {
                            R.string.technical_install_shizuku
                        }
                    } else if (asset?.updateStatus == UpdateStatus.UPDATE_AVAILABLE.name) {
                        R.string.technical_update
                    } else {
                        R.string.technical_install
                    },
                ),
            )
        }
        record.latestReleaseInstallAttempt?.let { attempt ->
            DetailValue(stringResource(R.string.technical_latest_install_attempt), statusLabel(attempt.status))
            attempt.statusMessage?.let { DetailValue(stringResource(R.string.technical_installer_message), it) }
        }
    }
}
