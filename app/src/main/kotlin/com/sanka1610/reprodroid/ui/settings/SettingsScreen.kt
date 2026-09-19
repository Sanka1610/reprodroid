package com.sanka1610.reprodroid.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sanka1610.reprodroid.R
import com.sanka1610.reprodroid.ui.navigation.ReproDroidRoute

@Composable
internal fun SettingsHomeScreen(onNavigate: (ReproDroidRoute) -> Unit) {
    SettingsPage {
        SettingsDestination(R.string.settings_appearance, R.string.settings_appearance_summary) { onNavigate(ReproDroidRoute.AppearanceSettings) }
        SettingsDestination(R.string.settings_updates_notifications, R.string.settings_updates_summary) { onNavigate(ReproDroidRoute.UpdateSettings) }
        SettingsDestination(R.string.settings_acquisition, R.string.settings_acquisition_summary) { onNavigate(ReproDroidRoute.AcquisitionSettings) }
        SettingsDestination(R.string.settings_service_authentication, R.string.settings_auth_summary) { onNavigate(ReproDroidRoute.ProviderSettings) }
        SettingsDestination(R.string.settings_verification_environment, R.string.settings_runner_summary) { onNavigate(ReproDroidRoute.RunnerSettings) }
        SettingsDestination(R.string.settings_storage, R.string.settings_data_summary) { onNavigate(ReproDroidRoute.DataManagement) }
        SettingsDestination(R.string.settings_about, R.string.settings_about_summary) { onNavigate(ReproDroidRoute.AboutSettings) }
    }
}

@Composable
internal fun SettingsPage(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

@Composable
private fun SettingsDestination(title: Int, summary: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(summary), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
}
