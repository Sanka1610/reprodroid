package com.sanka1610.reprodroid.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.sanka1610.reprodroid.R
import com.sanka1610.reprodroid.data.license.LicenseAssetStore
import com.sanka1610.reprodroid.data.license.LicenseDocument
import com.sanka1610.reprodroid.data.log.AppLogExportResult
import com.sanka1610.reprodroid.ui.*
import com.sanka1610.reprodroid.ui.shared.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DataManagementScreen(
    onBack: () -> Unit,
    onStorage: () -> Unit,
    onInactive: () -> Unit,
    onCleanup: () -> Unit,
    onAudit: () -> Unit,
    onRunner: () -> Unit,
    onLogExport: () -> Unit,
) {
    BackScaffoldTitle(stringResource(R.string.data_management_title), onBack) {
        SettingsPage {
            SettingsSectionLabel(stringResource(R.string.data_this_device))
            SettingsLink(stringResource(R.string.data_usage_settings), onStorage)
            HorizontalDivider()
            SettingsLink(stringResource(R.string.data_cleanup), onCleanup)
            HorizontalDivider()
            SettingsLink(stringResource(R.string.inactive_apps_title), onInactive)
            SettingsSectionLabel(stringResource(R.string.data_export_section))
            SettingsLink(stringResource(R.string.data_audit_export), onAudit)
            HorizontalDivider()
            SettingsLink(stringResource(R.string.settings_log_export), onLogExport)
            SettingsSectionLabel(stringResource(R.string.data_connected_pc))
            SettingsLink(stringResource(R.string.data_runner_separate), onRunner)
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
            InformationButton(stringResource(R.string.settings_log_export), listOf(
                stringResource(R.string.log_export_missing_warning),
                stringResource(R.string.log_export_sensitive_warning),
                stringResource(R.string.log_export_migration_warning),
            ).joinToString("\n\n"))
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
internal fun LicenseScreen(thirdParty: Boolean = false, onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember(context) { LicenseAssetStore(context) }
    var documents by remember { mutableStateOf<List<LicenseDocument>?>(null) }
    var loadFailed by remember { mutableStateOf(false) }

    LaunchedEffect(store) {
        runCatching {
            if (thirdParty) store.loadThirdPartyDocuments() else store.loadReproDroidLicense()
        }
            .onSuccess { documents = it }
            .onFailure { loadFailed = true }
    }

    BackScaffoldTitle(
        stringResource(
            if (thirdParty) R.string.settings_third_party_notices else R.string.settings_licenses,
        ),
        onBack,
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(
                    if (thirdParty) R.string.third_party_notices_intro else R.string.licenses_intro,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            when {
                loadFailed -> Text(stringResource(R.string.licenses_load_error))
                documents == null -> Text(stringResource(R.string.licenses_loading))
                else -> documents.orEmpty().forEach { document ->
                    Text(licenseDocumentTitle(document), style = MaterialTheme.typography.titleMedium)
                    Text(document.text, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
private fun licenseDocumentTitle(document: LicenseDocument): String = when (document.assetPath) {
    LicenseAssetStore.REPRODROID_LICENSE_ASSET -> stringResource(R.string.license_reprodroid_title)
    LicenseAssetStore.THIRD_PARTY_NOTICES_ASSET -> stringResource(R.string.third_party_notices_title)
    LicenseAssetStore.SMALI_LICENSE_ASSET -> stringResource(R.string.license_smali_title)
    LicenseAssetStore.CHECKER_QUAL_LICENSE_ASSET -> stringResource(R.string.license_checker_qual_title)
    LicenseAssetStore.SLF4J_LICENSE_ASSET -> stringResource(R.string.license_slf4j_title)
    else -> document.title
}
