package com.sanka1610.reprodroid.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sanka1610.reprodroid.R
import com.sanka1610.reprodroid.data.local.AppReleaseCheckOverrideEntity
import com.sanka1610.reprodroid.data.local.ReleaseCheckBatteryPolicy
import com.sanka1610.reprodroid.data.local.ReleaseCheckChannel
import com.sanka1610.reprodroid.data.local.ReleaseCheckNetworkPolicy
import com.sanka1610.reprodroid.data.local.ReleaseCheckScheduleMode
import com.sanka1610.reprodroid.data.local.ReleaseCheckSettingsEntity
import com.sanka1610.reprodroid.data.provider.ProviderCredentialAvailability
import com.sanka1610.reprodroid.data.provider.ProviderCredentialStatus
import com.sanka1610.reprodroid.ui.*
import com.sanka1610.reprodroid.ui.shared.*

private const val UPDATE_CHECK_DISABLED = "UPDATE_CHECK_DISABLED"
private val INTERVAL_HOUR_OPTIONS = listOf(1, 2, 3, 4, 5, 6, 12, 24, 72, 120, 168)

@Composable
internal fun ProviderCredentialSetting(
    label: String,
    status: ProviderCredentialStatus,
    busy: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val statusText = when (status.availability) {
        ProviderCredentialAvailability.NOT_CONFIGURED -> stringResource(R.string.provider_auth_not_configured)
        ProviderCredentialAvailability.CONFIGURED -> stringResource(R.string.provider_auth_configured_unverified)
        ProviderCredentialAvailability.UNAVAILABLE -> stringResource(R.string.provider_auth_unavailable)
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Text(statusText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onEdit,
                enabled = !busy && status.availability != ProviderCredentialAvailability.UNAVAILABLE,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    stringResource(
                        if (status.availability == ProviderCredentialAvailability.CONFIGURED) {
                            R.string.provider_auth_replace
                        } else {
                            R.string.provider_auth_configure
                        },
                    ),
                )
            }
            OutlinedButton(
                onClick = onDelete,
                enabled = !busy && status.availability != ProviderCredentialAvailability.NOT_CONFIGURED,
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.action_delete)) }
        }
    }
}

@Composable
internal fun SwitchSetting(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    supportingText: String? = null,
    infoAction: (() -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            )
            supportingText?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                )
            }
        }
        infoAction?.let { action ->
            IconButton(enabled = enabled, onClick = action) {
                Icon(Icons.Default.Info, contentDescription = stringResource(R.string.action_more_information))
            }
        }
        Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
    }
}

@Composable
internal fun SettingDivider(visible: Boolean) {
    HorizontalDivider(Modifier.alpha(if (visible) 1f else 0f))
}

@Composable
internal fun ExternalSettingsLink(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth()) {
            Text(label, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Text("↗", style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.CenterEnd))
        }
    }
}

@Composable
internal fun PermissionSetting(
    label: String,
    allowed: Boolean,
    onRequest: () -> Unit,
    supportingText: String? = null,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            supportingText?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
        }
        OutlinedButton(enabled = !allowed, onClick = onRequest) {
            Text(
                stringResource(
                    if (allowed) R.string.settings_permission_allowed else R.string.settings_allow_permission,
                ),
            )
        }
    }
}

@Composable
private fun ScheduleSetting(
    enabled: Boolean,
    scheduleMode: String,
    onChange: (enabled: Boolean, scheduleMode: String) -> Unit,
    supportingText: String? = null,
) {
    val selected = if (enabled) scheduleMode else UPDATE_CHECK_DISABLED
    DropdownSetting(
        label = stringResource(R.string.release_check_update_check),
        value = selected,
        options = linkedMapOf(
            ReleaseCheckScheduleMode.INTERVAL.name to stringResource(R.string.release_check_interval_mode),
            ReleaseCheckScheduleMode.DAILY_LOCAL_TIME.name to stringResource(R.string.release_check_daily_mode),
            UPDATE_CHECK_DISABLED to stringResource(R.string.release_check_disabled_mode),
        ),
        onSelect = { value ->
            if (value == UPDATE_CHECK_DISABLED) {
                onChange(false, scheduleMode)
            } else {
                onChange(true, value)
            }
        },
        supportingText = supportingText,
    )
}

@Composable
private fun IntervalHoursSetting(
    hours: Int,
    enabled: Boolean,
    onChange: (Int) -> Unit,
) {
    val options = linkedMapOf<Int, String>().apply {
        INTERVAL_HOUR_OPTIONS.forEach { candidate -> put(candidate, intervalLabel(candidate)) }
        if (hours !in this) put(hours, intervalLabel(hours))
    }
    DropdownSetting(
        label = stringResource(R.string.future_update_interval),
        value = hours,
        options = options,
        onSelect = onChange,
        enabled = enabled,
    )
}

@Composable
private fun intervalLabel(hours: Int): String =
    if (hours >= 24 && hours % 24 == 0) {
        val days = hours / 24
        pluralStringResource(R.plurals.release_check_days, days, days)
    } else {
        pluralStringResource(R.plurals.release_check_hours, hours, hours)
    }

@Composable
internal fun GlobalUpdateSettingsContent(
    settings: ReleaseCheckSettingsEntity,
    showDividers: Boolean,
    onUpdate: (ReleaseCheckSettingsEntity) -> Unit,
    onBatteryInfo: () -> Unit,
) {
    ScheduleSetting(
        enabled = settings.enabled,
        scheduleMode = settings.scheduleMode,
        onChange = { enabled, mode -> onUpdate(settings.copy(enabled = enabled, scheduleMode = mode)) },
        supportingText = stringResource(R.string.release_check_scope_body),
    )
    SettingDivider(showDividers)
    IntervalHoursSetting(
        hours = settings.intervalHours,
        enabled = settings.enabled && settings.scheduleMode == ReleaseCheckScheduleMode.INTERVAL.name,
        onChange = { onUpdate(settings.copy(intervalHours = it)) },
    )
    SettingDivider(showDividers)
    DailyMinuteSetting(
        minute = settings.dailyLocalMinute,
        onSave = { onUpdate(settings.copy(dailyLocalMinute = it)) },
        enabled = settings.enabled && settings.scheduleMode == ReleaseCheckScheduleMode.DAILY_LOCAL_TIME.name,
    )
    SettingDivider(showDividers)
    SwitchSetting(
        label = stringResource(R.string.release_check_include_metered),
        checked = settings.networkPolicy == ReleaseCheckNetworkPolicy.ANY_AVAILABLE.name,
        onCheckedChange = {
            onUpdate(
                settings.copy(
                    networkPolicy = if (it) ReleaseCheckNetworkPolicy.ANY_AVAILABLE.name
                    else ReleaseCheckNetworkPolicy.UNMETERED_ONLY.name,
                ),
            )
        },
    )
    SettingDivider(showDividers)
    SwitchSetting(
        label = stringResource(R.string.release_check_include_low_battery),
        checked = settings.batteryPolicy == ReleaseCheckBatteryPolicy.ANY.name,
        onCheckedChange = {
            onUpdate(
                settings.copy(
                    batteryPolicy = if (it) ReleaseCheckBatteryPolicy.ANY.name
                    else ReleaseCheckBatteryPolicy.ABOVE_20_PERCENT.name,
                ),
            )
        },
        infoAction = onBatteryInfo,
    )
    SettingDivider(showDividers)
    SwitchSetting(
        label = stringResource(R.string.release_check_charging_only),
        checked = settings.requiresCharging,
        onCheckedChange = { onUpdate(settings.copy(requiresCharging = it)) },
    )
}

@Composable
internal fun AppUpdateSettingsContent(
    global: ReleaseCheckSettingsEntity,
    override: AppReleaseCheckOverrideEntity,
    showDividers: Boolean,
    onUpdate: (AppReleaseCheckOverrideEntity) -> Unit,
) {
    var showBatteryInfo by rememberSaveable { mutableStateOf(false) }
    val scheduleMode = override.scheduleMode ?: global.scheduleMode
    SwitchSetting(
        label = stringResource(R.string.release_check_mute),
        checked = override.notificationMuted,
        onCheckedChange = { onUpdate(override.copy(notificationMuted = it)) },
    )
    SettingDivider(showDividers)
    ScheduleSetting(
        enabled = global.enabled && (override.enabled ?: true),
        scheduleMode = scheduleMode,
        onChange = { enabled, mode -> onUpdate(override.copy(enabled = enabled, scheduleMode = mode)) },
    )
    SettingDivider(showDividers)
    IntervalHoursSetting(
        hours = override.intervalHours ?: global.intervalHours,
        enabled = global.enabled && (override.enabled ?: true) &&
            scheduleMode == ReleaseCheckScheduleMode.INTERVAL.name,
        onChange = { onUpdate(override.copy(intervalHours = it)) },
    )
    SettingDivider(showDividers)
    DailyMinuteSetting(
        minute = override.dailyLocalMinute ?: global.dailyLocalMinute,
        onSave = { onUpdate(override.copy(dailyLocalMinute = it)) },
        enabled = global.enabled && (override.enabled ?: true) &&
            scheduleMode == ReleaseCheckScheduleMode.DAILY_LOCAL_TIME.name,
    )
    SettingDivider(showDividers)
    SwitchSetting(
        label = stringResource(R.string.release_check_include_prerelease_toggle),
        checked = (override.releaseChannel ?: global.releaseChannel) ==
            ReleaseCheckChannel.INCLUDE_PRERELEASE.name,
        onCheckedChange = {
            onUpdate(
                override.copy(
                    releaseChannel = if (it) ReleaseCheckChannel.INCLUDE_PRERELEASE.name
                    else ReleaseCheckChannel.STABLE_ONLY.name,
                ),
            )
        },
    )
    SettingDivider(showDividers)
    SwitchSetting(
        label = stringResource(R.string.release_check_include_metered),
        checked = (override.networkPolicy ?: global.networkPolicy) ==
            ReleaseCheckNetworkPolicy.ANY_AVAILABLE.name,
        onCheckedChange = {
            onUpdate(
                override.copy(
                    networkPolicy = if (it) ReleaseCheckNetworkPolicy.ANY_AVAILABLE.name
                    else ReleaseCheckNetworkPolicy.UNMETERED_ONLY.name,
                ),
            )
        },
    )
    SettingDivider(showDividers)
    SwitchSetting(
        label = stringResource(R.string.release_check_include_low_battery),
        checked = (override.batteryPolicy ?: global.batteryPolicy) == ReleaseCheckBatteryPolicy.ANY.name,
        onCheckedChange = {
            onUpdate(
                override.copy(
                    batteryPolicy = if (it) ReleaseCheckBatteryPolicy.ANY.name
                    else ReleaseCheckBatteryPolicy.ABOVE_20_PERCENT.name,
                ),
            )
        },
        infoAction = { showBatteryInfo = true },
    )
    SettingDivider(showDividers)
    SwitchSetting(
        label = stringResource(R.string.release_check_charging_only),
        checked = override.requiresCharging ?: global.requiresCharging,
        onCheckedChange = { onUpdate(override.copy(requiresCharging = it)) },
    )
    SettingDivider(showDividers)
    OutlinedButton(
        onClick = {
            onUpdate(
                override.copy(
                    enabled = null,
                    scheduleMode = null,
                    intervalHours = null,
                    dailyLocalMinute = null,
                    releaseChannel = null,
                    networkPolicy = null,
                    batteryPolicy = null,
                    requiresCharging = null,
                ),
            )
        },
        modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(R.string.release_check_use_global)) }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DailyMinuteSetting(minute: Int, onSave: (Int) -> Unit, enabled: Boolean = true) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.38f)) {
        Text(stringResource(R.string.release_check_daily_time), style = MaterialTheme.typography.titleSmall)
        OutlinedButton(
            enabled = enabled,
            onClick = { showPicker = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("%02d:%02d".format(minute / 60, minute % 60))
        }
        Text(
            stringResource(R.string.release_check_daily_time_body),
            style = MaterialTheme.typography.bodySmall,
        )
    }
    if (showPicker) {
        val pickerState = rememberTimePickerState(
            initialHour = minute / 60,
            initialMinute = minute % 60,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text(stringResource(R.string.release_check_select_time)) },
            text = { TimePicker(state = pickerState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSave(pickerState.hour * 60 + pickerState.minute)
                        showPicker = false
                    },
                ) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
internal fun AccordionSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(
                        if (expanded) R.string.action_collapse else R.string.action_expand,
                    ),
                )
            }
            AnimatedVisibility(expanded) {
                Column(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = content,
                )
            }
        }
    }
}

@Composable
internal fun SettingsLink(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, Modifier.fillMaxWidth()) { Text(label) }
}
