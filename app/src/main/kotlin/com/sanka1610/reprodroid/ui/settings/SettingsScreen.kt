package com.sanka1610.reprodroid.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sanka1610.reprodroid.R
import com.sanka1610.reprodroid.data.local.AppReleaseCheckOverrideEntity
import com.sanka1610.reprodroid.data.local.GlobalSettingsEntity
import com.sanka1610.reprodroid.data.local.InstallationSource
import com.sanka1610.reprodroid.data.local.ManagementMode
import com.sanka1610.reprodroid.data.local.PreferredAbi
import com.sanka1610.reprodroid.data.local.RegisteredAppRecord
import com.sanka1610.reprodroid.data.local.ReleaseCandidateEntity
import com.sanka1610.reprodroid.data.local.ReleaseCheckBatteryPolicy
import com.sanka1610.reprodroid.data.local.ReleaseCheckChannel
import com.sanka1610.reprodroid.data.local.ReleaseCheckNetworkPolicy
import com.sanka1610.reprodroid.data.local.ReleaseCheckScheduleMode
import com.sanka1610.reprodroid.data.local.ReleaseCheckSettingsEntity
import com.sanka1610.reprodroid.data.local.ReleaseScheduleStateEntity
import com.sanka1610.reprodroid.data.local.ReleaseVariantPreference
import com.sanka1610.reprodroid.data.local.ThemeMode
import com.sanka1610.reprodroid.data.license.LicenseAssetStore
import com.sanka1610.reprodroid.data.license.LicenseDocument
import com.sanka1610.reprodroid.data.log.AppLogExportResult
import com.sanka1610.reprodroid.ui.*
import com.sanka1610.reprodroid.ui.navigation.ReproDroidRoute
import com.sanka1610.reprodroid.ui.shared.*
@Composable
internal fun UiRSettingsScreen(
    settings: GlobalSettingsEntity,
    onUpdate: (GlobalSettingsEntity) -> Unit,
    onNavigate: (ReproDroidRoute) -> Unit,
) {
    var appearanceExpanded by rememberSaveable { mutableStateOf(true) }
    var defaultsExpanded by rememberSaveable { mutableStateOf(false) }
    var updatesExpanded by rememberSaveable { mutableStateOf(false) }
    var serviceAuthenticationExpanded by rememberSaveable { mutableStateOf(false) }
    var integrationsExpanded by rememberSaveable { mutableStateOf(false) }
    var runnerExpanded by rememberSaveable { mutableStateOf(false) }
    var backupExpanded by rememberSaveable { mutableStateOf(false) }
    var warningsExpanded by rememberSaveable { mutableStateOf(false) }
    var debugExpanded by rememberSaveable { mutableStateOf(false) }
    var aboutExpanded by rememberSaveable { mutableStateOf(false) }
    var showHintsInfo by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Spacer(Modifier.height(6.dp)) }
        item {
            AccordionSection(stringResource(R.string.settings_appearance), appearanceExpanded, { appearanceExpanded = !appearanceExpanded }) {
                DropdownSetting(
                    label = stringResource(R.string.settings_theme),
                    value = settings.themeMode,
                    options = linkedMapOf(
                        ThemeMode.SYSTEM.name to stringResource(R.string.settings_theme_system),
                        ThemeMode.LIGHT.name to stringResource(R.string.settings_theme_light),
                        ThemeMode.DARK.name to stringResource(R.string.settings_theme_dark),
                    ),
                    onSelect = { onUpdate(settings.copy(themeMode = it)) },
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_dynamic_color), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.settings_dynamic_color_body), style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = settings.dynamicColorEnabled,
                        onCheckedChange = { onUpdate(settings.copy(dynamicColorEnabled = it)) },
                    )
                }
            }
        }
        item {
            AccordionSection(stringResource(R.string.settings_app_defaults), defaultsExpanded, { defaultsExpanded = !defaultsExpanded }) {
                DropdownSetting(
                    stringResource(R.string.settings_release_variant),
                    settings.defaultReleaseVariantPreference,
                    ReleaseVariantPreference.entries.associate { it.name to localizedEnumLabel(it.name) },
                    { onUpdate(settings.copy(defaultReleaseVariantPreference = it)) },
                )
                DropdownSetting(
                    stringResource(R.string.settings_abi),
                    settings.defaultPreferredAbi,
                    PreferredAbi.entries.associate { it.name to abiLabel(it.name) },
                    { onUpdate(settings.copy(defaultPreferredAbi = it)) },
                )
                DropdownSetting(
                    stringResource(R.string.app_settings_apk_limit),
                    settings.defaultMaxApkSizeBytes,
                    UI_R_APK_LIMITS.associateWith { "${it / MEBIBYTE} MiB" },
                    { onUpdate(settings.copy(defaultMaxApkSizeBytes = it)) },
                    supportingText = stringResource(R.string.settings_apk_limit_body),
                )
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
                )
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
                )
            }
        }
        item {
            AccordionSection(stringResource(R.string.settings_updates), updatesExpanded, { updatesExpanded = !updatesExpanded }) {
                Text(stringResource(R.string.planned_updates_body), style = MaterialTheme.typography.bodySmall)
                SettingsLink(stringResource(R.string.settings_updates)) {
                    onNavigate(ReproDroidRoute.UpdateSettings)
                }
            }
        }
        item {
            AccordionSection(
                stringResource(R.string.settings_service_authentication),
                serviceAuthenticationExpanded,
                { serviceAuthenticationExpanded = !serviceAuthenticationExpanded },
            ) {
                UnavailableSetting(
                    stringResource(R.string.settings_github_token),
                    stringResource(R.string.settings_service_authentication_unavailable),
                )
                UnavailableSetting(
                    stringResource(R.string.settings_codeberg_token),
                    stringResource(R.string.settings_service_authentication_unavailable),
                )
            }
        }
        item {
            AccordionSection(stringResource(R.string.settings_integrations), integrationsExpanded, { integrationsExpanded = !integrationsExpanded }) {
                UnavailableSetting(
                    stringResource(R.string.settings_shizuku),
                    stringResource(R.string.settings_external_tools_unavailable),
                )
            }
        }
        item {
            AccordionSection(stringResource(R.string.settings_runner), runnerExpanded, { runnerExpanded = !runnerExpanded }) {
                SettingsLink(stringResource(R.string.settings_runner_connections)) {
                    onNavigate(ReproDroidRoute.RunnerSettings)
                }
            }
        }
        item {
            AccordionSection(stringResource(R.string.settings_backup), backupExpanded, { backupExpanded = !backupExpanded }) {
                UnavailableSetting(
                    stringResource(R.string.settings_backup_android),
                    stringResource(R.string.settings_backup_unavailable),
                )
                UnavailableSetting(
                    stringResource(R.string.settings_backup_runner),
                    stringResource(R.string.settings_backup_unavailable),
                )
            }
        }
        item {
            AccordionSection(stringResource(R.string.settings_warnings), warningsExpanded, { warningsExpanded = !warningsExpanded }) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.settings_operation_hints),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { showHintsInfo = true }) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = stringResource(R.string.settings_operation_hints_info),
                        )
                    }
                    Switch(
                        checked = settings.showOperationHints,
                        onCheckedChange = { onUpdate(settings.copy(showOperationHints = it)) },
                    )
                }
            }
        }
        item {
            AccordionSection(stringResource(R.string.settings_debug), debugExpanded, { debugExpanded = !debugExpanded }) {
                SettingsLink(stringResource(R.string.settings_storage)) { onNavigate(ReproDroidRoute.DataManagement) }
                SettingsLink(stringResource(R.string.settings_log_export)) { onNavigate(ReproDroidRoute.LogExport) }
            }
        }
        item {
            AccordionSection(stringResource(R.string.settings_about), aboutExpanded, { aboutExpanded = !aboutExpanded }) {
                SettingsLink(stringResource(R.string.settings_licenses)) { onNavigate(ReproDroidRoute.Licenses) }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
    if (showHintsInfo) {
        AlertDialog(
            onDismissRequest = { showHintsInfo = false },
            title = { Text(stringResource(R.string.settings_operation_hints_info_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.settings_operation_hints_info_body))
                }
            },
            confirmButton = {
                TextButton(onClick = { showHintsInfo = false }) {
                    Text(stringResource(R.string.action_close))
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReleaseUpdateSettingsScreen(
    settings: ReleaseCheckSettingsEntity,
    apps: List<RegisteredAppRecord>,
    overrides: List<AppReleaseCheckOverrideEntity>,
    schedules: List<ReleaseScheduleStateEntity>,
    candidates: List<ReleaseCandidateEntity>,
    onUpdateSettings: (ReleaseCheckSettingsEntity) -> Unit,
    onUpdateOverride: (AppReleaseCheckOverrideEntity) -> Unit,
    onCheckNow: (String) -> Unit,
    onOpenCandidate: (ReleaseCandidateEntity) -> Unit,
    onRequestNotifications: () -> Unit,
    onBack: () -> Unit,
) {
    val overrideByApp = overrides.associateBy { it.registeredAppId }
    val scheduleByApp = schedules.associateBy { it.registeredAppId }
    BackScaffoldTitle(stringResource(R.string.settings_updates), onBack) {
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                UiRDetailCard(stringResource(R.string.release_check_global_settings)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.future_update_schedule))
                            Text(stringResource(R.string.release_check_scope_body), style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(
                            checked = settings.enabled,
                            onCheckedChange = { onUpdateSettings(settings.copy(enabled = it)) },
                        )
                    }
                    DropdownSetting(
                        stringResource(R.string.release_check_schedule_mode),
                        settings.scheduleMode,
                        linkedMapOf(
                            ReleaseCheckScheduleMode.INTERVAL.name to stringResource(R.string.release_check_interval_mode),
                            ReleaseCheckScheduleMode.DAILY_LOCAL_TIME.name to stringResource(R.string.release_check_daily_mode),
                        ),
                        { onUpdateSettings(settings.copy(scheduleMode = it)) },
                    )
                    DropdownSetting(
                        stringResource(R.string.future_update_interval),
                        settings.intervalHours,
                        (1..24).associateWith { pluralStringResource(R.plurals.release_check_hours, it, it) },
                        { onUpdateSettings(settings.copy(intervalHours = it)) },
                    )
                    DailyMinuteSetting(
                        minute = settings.dailyLocalMinute,
                        onSave = { onUpdateSettings(settings.copy(dailyLocalMinute = it)) },
                    )
                    DropdownSetting(
                        stringResource(R.string.release_check_channel),
                        settings.releaseChannel,
                        linkedMapOf(
                            ReleaseCheckChannel.STABLE_ONLY.name to stringResource(R.string.release_check_stable_only),
                            ReleaseCheckChannel.INCLUDE_PRERELEASE.name to stringResource(R.string.release_check_include_prerelease),
                        ),
                        { onUpdateSettings(settings.copy(releaseChannel = it)) },
                    )
                    DropdownSetting(
                        stringResource(R.string.release_check_network),
                        settings.networkPolicy,
                        linkedMapOf(
                            ReleaseCheckNetworkPolicy.ANY_AVAILABLE.name to stringResource(R.string.release_check_any_network),
                            ReleaseCheckNetworkPolicy.UNMETERED_ONLY.name to stringResource(R.string.release_check_unmetered),
                        ),
                        { onUpdateSettings(settings.copy(networkPolicy = it)) },
                    )
                    DropdownSetting(
                        stringResource(R.string.release_check_battery),
                        settings.batteryPolicy,
                        linkedMapOf(
                            ReleaseCheckBatteryPolicy.ANY.name to stringResource(R.string.release_check_any_battery),
                            ReleaseCheckBatteryPolicy.ABOVE_20_PERCENT.name to stringResource(R.string.release_check_above_twenty),
                        ),
                        { onUpdateSettings(settings.copy(batteryPolicy = it)) },
                    )
                    OutlinedButton(onClick = onRequestNotifications, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.release_check_enable_notifications))
                    }
                    Text(stringResource(R.string.release_check_permission_body), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.release_check_manual_only_body), style = MaterialTheme.typography.bodySmall)
                }
            }
            items(apps, key = { it.app.registeredAppId }) { record ->
                val existing = overrideByApp[record.app.registeredAppId]
                val override = existing ?: AppReleaseCheckOverrideEntity(
                    registeredAppId = record.app.registeredAppId,
                    updatedAt = java.time.Instant.EPOCH.toString(),
                )
                val schedule = scheduleByApp[record.app.registeredAppId]
                UiRDetailCard(record.app.resolvedDisplayName) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.release_check_app_enabled), Modifier.weight(1f))
                        Switch(
                            checked = override.enabled ?: true,
                            onCheckedChange = { onUpdateOverride(override.copy(enabled = it)) },
                        )
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.release_check_mute), Modifier.weight(1f))
                        Switch(
                            checked = override.notificationMuted,
                            onCheckedChange = { onUpdateOverride(override.copy(notificationMuted = it)) },
                        )
                    }
                    DropdownSetting(
                        stringResource(R.string.release_check_schedule_mode),
                        override.scheduleMode ?: settings.scheduleMode,
                        linkedMapOf(
                            ReleaseCheckScheduleMode.INTERVAL.name to stringResource(R.string.release_check_interval_mode),
                            ReleaseCheckScheduleMode.DAILY_LOCAL_TIME.name to stringResource(R.string.release_check_daily_mode),
                        ),
                        { onUpdateOverride(override.copy(scheduleMode = it)) },
                    )
                    DropdownSetting(
                        stringResource(R.string.future_update_interval),
                        override.intervalHours ?: settings.intervalHours,
                        (1..24).associateWith { pluralStringResource(R.plurals.release_check_hours, it, it) },
                        { onUpdateOverride(override.copy(intervalHours = it)) },
                    )
                    DailyMinuteSetting(
                        minute = override.dailyLocalMinute ?: settings.dailyLocalMinute,
                        onSave = { onUpdateOverride(override.copy(dailyLocalMinute = it)) },
                    )
                    DropdownSetting(
                        stringResource(R.string.release_check_channel),
                        override.releaseChannel ?: settings.releaseChannel,
                        linkedMapOf(
                            ReleaseCheckChannel.STABLE_ONLY.name to stringResource(R.string.release_check_stable_only),
                            ReleaseCheckChannel.INCLUDE_PRERELEASE.name to stringResource(R.string.release_check_include_prerelease),
                        ),
                        { onUpdateOverride(override.copy(releaseChannel = it)) },
                    )
                    DropdownSetting(
                        stringResource(R.string.release_check_network),
                        override.networkPolicy ?: settings.networkPolicy,
                        linkedMapOf(
                            ReleaseCheckNetworkPolicy.ANY_AVAILABLE.name to stringResource(R.string.release_check_any_network),
                            ReleaseCheckNetworkPolicy.UNMETERED_ONLY.name to stringResource(R.string.release_check_unmetered),
                        ),
                        { onUpdateOverride(override.copy(networkPolicy = it)) },
                    )
                    DropdownSetting(
                        stringResource(R.string.release_check_battery),
                        override.batteryPolicy ?: settings.batteryPolicy,
                        linkedMapOf(
                            ReleaseCheckBatteryPolicy.ANY.name to stringResource(R.string.release_check_any_battery),
                            ReleaseCheckBatteryPolicy.ABOVE_20_PERCENT.name to stringResource(R.string.release_check_above_twenty),
                        ),
                        { onUpdateOverride(override.copy(batteryPolicy = it)) },
                    )
                    OutlinedButton(
                        onClick = {
                            onUpdateOverride(
                                override.copy(
                                    enabled = null,
                                    scheduleMode = null,
                                    intervalHours = null,
                                    dailyLocalMinute = null,
                                    releaseChannel = null,
                                    networkPolicy = null,
                                    batteryPolicy = null,
                                ),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.release_check_use_global)) }
                    UiRDetailValue(
                        stringResource(R.string.release_check_last_attempt),
                        schedule?.lastAttemptAt ?: stringResource(R.string.value_never),
                    )
                    UiRDetailValue(
                        stringResource(R.string.release_check_next),
                        schedule?.nextEligibleAt ?: stringResource(R.string.value_not_available),
                    )
                    UiRDetailValue(
                        stringResource(R.string.release_check_waiting),
                        statusLabel(schedule?.waitingReason),
                    )
                    Button(
                        onClick = { onCheckNow(record.app.registeredAppId) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.release_check_now)) }
                }
            }
            if (candidates.isNotEmpty()) {
                item { Text(stringResource(R.string.release_candidates_title), style = MaterialTheme.typography.titleLarge) }
                items(candidates, key = { it.candidateId }) { candidate ->
                    Card(Modifier.fillMaxWidth().clickable { onOpenCandidate(candidate) }) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(candidate.releaseName, fontWeight = FontWeight.SemiBold)
                            Text(candidate.tagName)
                            Text(statusLabel(candidate.state))
                            if (candidate.unseen) Text(stringResource(R.string.release_candidate_unseen))
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun DailyMinuteSetting(minute: Int, onSave: (Int) -> Unit) {
    val initial = "%02d:%02d".format(minute / 60, minute % 60)
    var value by rememberSaveable(minute) { mutableStateOf(initial) }
    val parsed = remember(value) {
        val parts = value.split(':')
        if (parts.size != 2) null else {
            val hour = parts[0].toIntOrNull()
            val localMinute = parts[1].toIntOrNull()
            if (hour != null && localMinute != null && hour in 0..23 && localMinute in 0..59) {
                hour * 60 + localMinute
            } else {
                null
            }
        }
    }
    OutlinedTextField(
        value = value,
        onValueChange = { value = it.take(5) },
        label = { Text(stringResource(R.string.release_check_daily_time)) },
        supportingText = { Text(stringResource(R.string.release_check_daily_time_body)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedButton(
        enabled = parsed != null && parsed != minute,
        onClick = { parsed?.let(onSave) },
        modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(R.string.action_save)) }
}

@Composable
private fun AccordionSection(
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

@Composable
private fun UnavailableSetting(label: String, body: String) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
    }
}

@Composable
private fun PlannedCard(phase: String, body: String, onOpen: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(body)
            Text(stringResource(R.string.planned_phase, phase), color = MaterialTheme.colorScheme.secondary)
            Text(stringResource(R.string.planned_unavailable), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun DisabledSetting(label: String, phase: String) {
    val description = stringResource(R.string.future_feature_content_description, phase)
    OutlinedButton(
        enabled = false,
        onClick = {},
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = description },
    ) { Text(label) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DataManagementScreen(
    onBack: () -> Unit,
    onStorage: () -> Unit,
    onInactive: () -> Unit,
    onRunner: () -> Unit,
    onLogExport: () -> Unit,
) {
    BackScaffoldTitle(stringResource(R.string.data_management_title), onBack) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsLink(stringResource(R.string.data_android_storage), onStorage)
            SettingsLink(stringResource(R.string.inactive_apps_title), onInactive)
            Text(stringResource(R.string.data_android_deletion_note), style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            SettingsLink(stringResource(R.string.data_runner_separate), onRunner)
            Text(stringResource(R.string.data_runner_note), style = MaterialTheme.typography.bodySmall)
            SettingsLink(stringResource(R.string.settings_log_export), onLogExport)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LogExportScreen(
    result: AppLogExportResult?,
    busy: Boolean,
    onExport: () -> Unit,
    onClearResult: () -> Unit,
    onBack: () -> Unit,
) {
    BackScaffoldTitle(stringResource(R.string.settings_log_export), onBack) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.log_export_scope), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.log_export_missing_warning), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.log_export_sensitive_warning), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.log_export_migration_warning), style = MaterialTheme.typography.bodySmall)
            Button(
                enabled = !busy,
                onClick = onExport,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.log_export_choose_destination))
            }
            result?.let { exported ->
                HorizontalDivider()
                Text(stringResource(R.string.log_export_saved), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.log_export_records, exported.recordCount))
                Text(stringResource(R.string.log_export_size, humanBytes(exported.sizeBytes)))
                if (exported.includesRotatedFile) {
                    Text(stringResource(R.string.log_export_rotated_included))
                }
                TextButton(onClick = onClearResult) {
                    Text(stringResource(R.string.log_export_clear_result))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LicenseScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember(context) { LicenseAssetStore(context) }
    var documents by remember { mutableStateOf<List<LicenseDocument>?>(null) }
    var loadFailed by remember { mutableStateOf(false) }

    LaunchedEffect(store) {
        runCatching { store.loadDocuments() }
            .onSuccess { documents = it }
            .onFailure { loadFailed = true }
    }

    BackScaffoldTitle(stringResource(R.string.settings_licenses), onBack) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.licenses_intro), style = MaterialTheme.typography.bodyMedium)
            when {
                loadFailed -> Text(stringResource(R.string.licenses_load_error))
                documents == null -> Text(stringResource(R.string.licenses_loading))
                else -> documents.orEmpty().forEach { document ->
                    Text(document.title, style = MaterialTheme.typography.titleMedium)
                    Text(document.text, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}
