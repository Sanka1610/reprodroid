package com.sanka1610.reprodroid.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sanka1610.reprodroid.R
import com.sanka1610.reprodroid.ui.navigation.ReproDroidRoute

@Composable
internal fun SettingsHomeScreen(showDividers: Boolean, onNavigate: (ReproDroidRoute) -> Unit) {
    SettingsPage {
        SettingsDestination(R.string.settings_appearance, R.string.settings_appearance_summary) { onNavigate(ReproDroidRoute.AppearanceSettings) }
        SettingDivider(showDividers)
        SettingsDestination(R.string.settings_updates_notifications, R.string.settings_updates_summary) { onNavigate(ReproDroidRoute.UpdateSettings) }
        SettingDivider(showDividers)
        SettingsDestination(R.string.settings_notifications_permissions, R.string.settings_notifications_summary) { onNavigate(ReproDroidRoute.NotificationSettings) }
        SettingDivider(showDividers)
        SettingsDestination(R.string.settings_acquisition, R.string.settings_acquisition_summary) { onNavigate(ReproDroidRoute.AcquisitionSettings) }
        SettingDivider(showDividers)
        SettingsDestination(R.string.settings_service_authentication, R.string.settings_auth_summary) { onNavigate(ReproDroidRoute.ProviderSettings) }
        SettingDivider(showDividers)
        SettingsDestination(R.string.settings_verification_environment, R.string.settings_runner_summary) { onNavigate(ReproDroidRoute.RunnerSettings) }
        SettingDivider(showDividers)
        SettingsDestination(R.string.settings_storage, R.string.settings_data_summary) { onNavigate(ReproDroidRoute.DataManagement) }
        SettingDivider(showDividers)
        SettingsDestination(R.string.settings_about, R.string.settings_about_summary) { onNavigate(ReproDroidRoute.AboutSettings) }
    }
}

@Composable
internal fun SettingsPage(content: @Composable ColumnScope.() -> Unit) {
    CompositionLocalProvider(LocalCompactSettings provides true) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
    }
}

internal val LocalCompactSettings = staticCompositionLocalOf { false }

@Composable
private fun SettingsDestination(title: Int, summary: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(summary), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
}
