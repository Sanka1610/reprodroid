package com.sanka1610.reprodroid.ui.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.sanka1610.reprodroid.R
import com.sanka1610.reprodroid.data.local.GlobalSettingsEntity
import com.sanka1610.reprodroid.data.local.ReleaseCheckChannel
import com.sanka1610.reprodroid.data.local.ReleaseCheckSettingsEntity
import com.sanka1610.reprodroid.ui.*
import com.sanka1610.reprodroid.ui.shared.*

@Composable
internal fun UpdateSettingsScreen(settings: GlobalSettingsEntity, releaseSettings: ReleaseCheckSettingsEntity, onUpdateReleaseSettings: (ReleaseCheckSettingsEntity) -> Unit) {
    var showBatteryInfo by remember { mutableStateOf(false) }
    SettingsPage {
        GlobalUpdateSettingsContent(
            settings = releaseSettings,
            showDividers = settings.showSettingsDividers,
            onUpdate = onUpdateReleaseSettings,
            onBatteryInfo = { showBatteryInfo = true },
        )
        SettingDivider(settings.showSettingsDividers)
        SwitchSetting(
            label = stringResource(R.string.release_check_include_prerelease_toggle),
            checked = releaseSettings.releaseChannel == ReleaseCheckChannel.INCLUDE_PRERELEASE.name,
            onCheckedChange = {
                onUpdateReleaseSettings(
                    releaseSettings.copy(
                        releaseChannel = if (it) {
                            ReleaseCheckChannel.INCLUDE_PRERELEASE.name
                        } else {
                            ReleaseCheckChannel.STABLE_ONLY.name
                        },
                    ),
                )
            },
        )

    }
    if (showBatteryInfo) {
        AlertDialog(
            onDismissRequest = { showBatteryInfo = false },
            title = { Text(stringResource(R.string.release_check_battery_info_title)) },
            text = { Text(stringResource(R.string.release_check_battery_info_body)) },
            confirmButton = {
                TextButton(onClick = { showBatteryInfo = false }) {
                    Text(stringResource(R.string.action_close))
                }
            },
        )

    }
}

@Composable
internal fun NotificationSettingsScreen(
    settings: GlobalSettingsEntity,
    releaseSettings: ReleaseCheckSettingsEntity,
    notificationsAllowed: Boolean,
    backgroundWorkAllowed: Boolean,
    onUpdateReleaseSettings: (ReleaseCheckSettingsEntity) -> Unit,
    onRequestNotifications: () -> Unit,
    onOpenBackgroundSettings: () -> Unit,
) {
    SettingsPage {
        SwitchSetting(
            label = stringResource(R.string.release_check_notifications),
            checked = releaseSettings.releaseNotificationsEnabled,
            onCheckedChange = {
                onUpdateReleaseSettings(releaseSettings.copy(releaseNotificationsEnabled = it))
            },
        )
        SettingDivider(settings.showSettingsDividers)
        PermissionSetting(
            label = stringResource(R.string.settings_notification_permission),
            allowed = notificationsAllowed,
            onRequest = onRequestNotifications,
        )
        SettingDivider(settings.showSettingsDividers)
        PermissionSetting(
            label = stringResource(R.string.settings_background_work),
            allowed = backgroundWorkAllowed,
            onRequest = onOpenBackgroundSettings,
            supportingText = stringResource(R.string.settings_background_work_body),
        )
    }
}
