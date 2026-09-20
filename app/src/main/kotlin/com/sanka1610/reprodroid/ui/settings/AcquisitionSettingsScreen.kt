package com.sanka1610.reprodroid.ui.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sanka1610.reprodroid.R
import com.sanka1610.reprodroid.data.artifact.ShizukuPackageInstaller
import com.sanka1610.reprodroid.data.artifact.ShizukuPermissionState
import com.sanka1610.reprodroid.data.local.GlobalSettingsEntity
import com.sanka1610.reprodroid.data.local.InstallationSource
import com.sanka1610.reprodroid.data.local.InstallerMode
import com.sanka1610.reprodroid.data.local.ManagementMode
import com.sanka1610.reprodroid.data.local.PreferredAbi
import com.sanka1610.reprodroid.ui.*
import com.sanka1610.reprodroid.ui.shared.*
import kotlinx.coroutines.launch

@Composable
internal fun AcquisitionSettingsScreen(settings: GlobalSettingsEntity, onUpdate: (GlobalSettingsEntity) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val shizukuInstaller = remember(context) { ShizukuPackageInstaller(context.applicationContext) }
    var shizukuPermissionState by remember { mutableStateOf(shizukuInstaller.permissionState()) }
    SettingsPage {
        DropdownSetting(
            stringResource(R.string.settings_abi),
            settings.defaultPreferredAbi,
            PreferredAbi.entries.associate { it.name to abiLabel(it.name) },
            { onUpdate(settings.copy(defaultPreferredAbi = it)) },
            compact = true,
        )
        SettingDivider(settings.showSettingsDividers)
        DropdownSetting(
            stringResource(R.string.settings_management_mode),
            settings.defaultManagementMode,
            linkedMapOf(
                ManagementMode.VERIFICATION.name to stringResource(R.string.mode_verification),
                ManagementMode.ACQUISITION.name to stringResource(R.string.mode_acquisition),
            ),
            {
                onUpdate(
                    settings.copy(
                        defaultManagementMode = it,
                        defaultInstallationSource = if (it == ManagementMode.ACQUISITION.name) {
                            InstallationSource.OFFICIAL_RELEASE.name
                        } else {
                            settings.defaultInstallationSource
                        },
                    ),
                )
            },
            compact = true,
        )
        SettingDivider(settings.showSettingsDividers)
        DropdownSetting(
            stringResource(R.string.settings_installation_source),
            settings.defaultInstallationSource,
            InstallationSource.entries
                .filter {
                    settings.defaultManagementMode == ManagementMode.VERIFICATION.name ||
                        it == InstallationSource.OFFICIAL_RELEASE
                }
                .associate {
                    it.name to stringResource(
                        if (it == InstallationSource.OFFICIAL_RELEASE) {
                            R.string.installation_official
                        } else {
                            R.string.installation_local
                        },
                    )
                },
            { onUpdate(settings.copy(defaultInstallationSource = it)) },
            compact = true,
        )
        SettingDivider(settings.showSettingsDividers)
        DropdownSetting(
            label = stringResource(R.string.settings_install_method),
            value = settings.installerMode,
            options = linkedMapOf(
                InstallerMode.SYSTEM.name to stringResource(R.string.settings_install_method_system),
                InstallerMode.SHIZUKU.name to stringResource(R.string.settings_install_method_shizuku),
            ),
            onSelect = { selected ->
                if (selected == InstallerMode.SYSTEM.name) {
                    onUpdate(
                        settings.copy(
                            installerMode = selected,
                            recordGooglePlayAsInstaller = false,
                        ),
                    )
                } else {
                    scope.launch {
                        shizukuPermissionState = shizukuInstaller.requestPermission()
                        if (shizukuPermissionState == ShizukuPermissionState.GRANTED) {
                            onUpdate(settings.copy(installerMode = InstallerMode.SHIZUKU.name))
                        }
                    }
                }
            },
            compact = true,
        )
        if (settings.installerMode == InstallerMode.SHIZUKU.name || shizukuPermissionState == ShizukuPermissionState.DENIED) {
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_shizuku_permission), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                InformationButton(
                    stringResource(R.string.settings_shizuku_permission),
                    when (shizukuPermissionState) {
                    ShizukuPermissionState.GRANTED -> stringResource(R.string.settings_shizuku_ready)
                    ShizukuPermissionState.SERVICE_UNAVAILABLE -> stringResource(R.string.settings_shizuku_unavailable)
                    ShizukuPermissionState.UNSUPPORTED -> stringResource(R.string.settings_shizuku_unsupported)
                    ShizukuPermissionState.DENIED -> stringResource(R.string.settings_shizuku_permission_required)
                    } + "\n\n" + stringResource(R.string.settings_shizuku_safety_body),
                )
                Text(stringResource(if (shizukuPermissionState == ShizukuPermissionState.GRANTED) R.string.settings_permission_allowed else R.string.settings_permission_not_allowed), style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (settings.installerMode == InstallerMode.SHIZUKU.name) {
            SettingDivider(settings.showSettingsDividers)
            SwitchSetting(
                label = stringResource(R.string.settings_google_play_installer),
                checked = settings.recordGooglePlayAsInstaller,
                onCheckedChange = {
                    onUpdate(settings.copy(recordGooglePlayAsInstaller = it))
                },
                supportingText = stringResource(R.string.settings_google_play_installer_body),
            )
        }

    }
}
