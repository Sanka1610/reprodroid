package com.sanka1610.reprodroid.ui.settings

import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import com.sanka1610.reprodroid.R
import com.sanka1610.reprodroid.ui.*
import com.sanka1610.reprodroid.ui.navigation.ReproDroidRoute
import com.sanka1610.reprodroid.ui.shared.*

@Composable
internal fun AboutSettingsScreen(onNavigate: (ReproDroidRoute) -> Unit) {
    val uriHandler = LocalUriHandler.current
    SettingsPage {
        SettingsLink(stringResource(R.string.settings_licenses)) { onNavigate(ReproDroidRoute.Licenses) }
        HorizontalDivider()
        SettingsLink(stringResource(R.string.settings_third_party_notices)) {
            onNavigate(ReproDroidRoute.ThirdPartyNotices)
        }
        HorizontalDivider()
        ExternalSettingsLink(stringResource(R.string.settings_github_repository)) {
            uriHandler.openUri("https://github.com/Sanka1610/reprodroid")
        }
        HorizontalDivider()
        ExternalSettingsLink(stringResource(R.string.settings_github_author)) {
            uriHandler.openUri("https://github.com/Sanka1610")
        }
    }
}
