package com.sanka1610.reprodroid.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.sanka1610.reprodroid.R
import com.sanka1610.reprodroid.data.local.GlobalSettingsEntity
import com.sanka1610.reprodroid.data.local.ThemeMode
import com.sanka1610.reprodroid.ui.*
import com.sanka1610.reprodroid.ui.shared.*

@Composable
internal fun AppearanceSettingsScreen(settings: GlobalSettingsEntity, onUpdate: (GlobalSettingsEntity) -> Unit) {
    SettingsPage {
        DropdownSetting(
            label = stringResource(R.string.settings_theme),
            value = settings.themeMode,
            options = linkedMapOf(
                ThemeMode.SYSTEM.name to stringResource(R.string.settings_theme_system),
                ThemeMode.LIGHT.name to stringResource(R.string.settings_theme_light),
                ThemeMode.DARK.name to stringResource(R.string.settings_theme_dark),
                ThemeMode.PURE_BLACK.name to stringResource(R.string.settings_theme_pure_black),
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
        SettingDivider(settings.showSettingsDividers)
        SwitchSetting(
            label = stringResource(R.string.settings_show_dividers),
            checked = settings.showSettingsDividers,
            onCheckedChange = { onUpdate(settings.copy(showSettingsDividers = it)) },
        )
        SettingDivider(settings.showSettingsDividers)
        SwitchSetting(
            label = stringResource(R.string.settings_show_selection_outlines),
            checked = settings.showSelectionBoxOutlines,
            onCheckedChange = { onUpdate(settings.copy(showSelectionBoxOutlines = it)) },
        )
        SettingDivider(settings.showSettingsDividers)
        SwitchSetting(stringResource(R.string.settings_operation_hints), settings.showOperationHints, { onUpdate(settings.copy(showOperationHints = it)) })
    }
}
