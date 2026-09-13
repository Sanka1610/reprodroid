package com.sanka1610.reprodroid.ui.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sanka1610.reprodroid.R
import com.sanka1610.reprodroid.data.local.ManagementMode
import com.sanka1610.reprodroid.data.local.ComparisonRunStatus
import com.sanka1610.reprodroid.data.local.GlobalSettingsEntity
import com.sanka1610.reprodroid.data.local.InstallationSource
import com.sanka1610.reprodroid.data.local.PreferredAbi
import com.sanka1610.reprodroid.data.local.RegisteredAppRecord
import com.sanka1610.reprodroid.data.local.ReleaseVariantPreference
import com.sanka1610.reprodroid.data.local.TrustLevel
import com.sanka1610.reprodroid.data.local.UpdateStatus
import com.sanka1610.reprodroid.data.local.SourceScanWithDetails


import com.sanka1610.reprodroid.ui.*
import com.sanka1610.reprodroid.ui.theme.LocalSelectionBoxOutlines
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> DropdownSetting(
    label: String,
    value: T,
    options: Map<T, String>,
    onSelect: (T) -> Unit,
    enabled: Boolean = true,
    supportingText: String? = null,
    showOutline: Boolean? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val outlined = showOutline ?: LocalSelectionBoxOutlines.current
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = !expanded },
    ) {
        if (outlined) {
            OutlinedTextField(
                value = options[value] ?: value.toString(),
                onValueChange = {},
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled)
                    .fillMaxWidth(),
                enabled = enabled,
                readOnly = true,
                singleLine = false,
                maxLines = 2,
                label = { Text(label) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                supportingText = supportingText?.let { text -> { Text(text) } },
            )
        } else {
            Column(
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled)
                    .fillMaxWidth(),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text(
                        options[value] ?: value.toString(),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (enabled) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        },
                        textAlign = TextAlign.End,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded)
                }
                supportingText?.let { text ->
                    Text(
                        text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (candidate, candidateLabel) ->
                DropdownMenuItem(
                    text = { Text(candidateLabel) },
                    onClick = { expanded = false; onSelect(candidate) },
                )
            }
        }
    }
}

@Composable
internal fun SectionTitle(title: String, explanation: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(explanation, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun SettingInfo(label: String, value: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun DetailCard(title: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
internal fun SourceScanEvidence(label: String, evidence: SourceScanWithDetails?) {
    if (evidence == null) {
        DetailValue(label, stringResource(R.string.value_not_available))
        return
    }
    DetailValue(
        label,
        if (evidence.scan.findingCount == 0) {
            stringResource(R.string.jobs_source_scan_clean)
        } else {
            pluralStringResource(
                R.plurals.jobs_source_scan_findings,
                evidence.scan.findingCount,
                evidence.scan.findingCount,
            )
        },
    )
    DetailValue(
        stringResource(R.string.technical_source_scan_scope, label),
        listOf(
            pluralStringResource(
                R.plurals.jobs_source_scan_files,
                evidence.scan.scannedFiles,
                evidence.scan.scannedFiles,
            ),
            pluralStringResource(
                R.plurals.jobs_source_scan_bytes,
                evidence.scan.scannedBytes.pluralQuantity(),
                evidence.scan.scannedBytes,
            ),
            pluralStringResource(
                R.plurals.jobs_source_scan_binary_skipped,
                evidence.scan.skippedBinaryFiles,
                evidence.scan.skippedBinaryFiles,
            ),
            pluralStringResource(
                R.plurals.jobs_source_scan_symlinks_skipped,
                evidence.scan.skippedSymlinks,
                evidence.scan.skippedSymlinks,
            ),
        ).joinToString(" · "),
    )
    DetailValue(stringResource(R.string.technical_scan_result, label), evidence.scan.resultSha256, true)
    evidence.detectorCounts.sortedBy { it.detectorId }.forEach { count ->
        DetailValue(count.detectorId, count.count.toString())
    }
    evidence.findings.sortedBy { it.ordinal }.take(MAX_SOURCE_SCAN_FINDINGS_IN_UI).forEach { finding ->
        val position = finding.line?.let { line -> ":$line:${finding.column}" }.orEmpty()
        DetailValue(finding.detectorId, "${finding.displayPath}$position", true)
    }
    if (evidence.findings.size > MAX_SOURCE_SCAN_FINDINGS_IN_UI) {
        DetailValue(
            stringResource(R.string.technical_additional_findings),
            (evidence.findings.size - MAX_SOURCE_SCAN_FINDINGS_IN_UI).toString(),
        )
    }
    Text(
        stringResource(R.string.technical_source_scan_boundary),
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
internal fun DetailValue(label: String, value: String, monospace: Boolean = false) {
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f),
            )
            if ((monospace || value.length > LONG_TECHNICAL_VALUE_LENGTH) && value.isNotBlank()) {
                CopyValueButton(value)
            }
        }
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
            maxLines = if (monospace) 4 else 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun trustLabel(record: RegisteredAppRecord): String = when (record.trustLevel) {
    TrustLevel.REPRODUCIBLE -> stringResource(R.string.state_reproducible)
    TrustLevel.BUILDABLE -> stringResource(R.string.state_buildable)
    TrustLevel.DIFFERENT -> stringResource(R.string.state_different)
    TrustLevel.INCOMPARABLE -> stringResource(R.string.state_incomparable)
    TrustLevel.FAILED -> stringResource(R.string.state_error)
    null -> when (record.currentComparison?.status) {
        ComparisonRunStatus.BUILDING.name,
        ComparisonRunStatus.REPEAT_BUILDING.name -> stringResource(R.string.state_building)
        ComparisonRunStatus.COMPARING.name,
        ComparisonRunStatus.COMPARING_REPEAT.name -> stringResource(R.string.state_comparing)
        ComparisonRunStatus.AWAITING_CONFIRMATION.name,
        ComparisonRunStatus.AWAITING_REPEAT_CONFIRMATION.name -> stringResource(R.string.state_awaiting_confirmation)
        ComparisonRunStatus.AWAITING_SCAN_REVIEW.name,
        ComparisonRunStatus.AWAITING_REPEAT_SCAN_REVIEW.name -> stringResource(R.string.state_awaiting_scan_review)
        else -> stringResource(R.string.state_not_checked)
    }
}

@Composable
internal fun updateLabel(status: String?): String = when (status) {
    UpdateStatus.NOT_INSTALLED.name -> stringResource(R.string.state_not_installed)
    UpdateStatus.UPDATE_AVAILABLE.name -> stringResource(R.string.state_update_available)
    UpdateStatus.UP_TO_DATE.name -> stringResource(R.string.state_success)
    UpdateStatus.OLDER_THAN_INSTALLED.name -> stringResource(R.string.state_older_than_installed)
    UpdateStatus.UNKNOWN.name -> stringResource(R.string.value_unknown)
    else -> stringResource(R.string.state_not_checked)
}

@Composable
internal fun updateColor(status: String?): Color = when (status) {
    UpdateStatus.UPDATE_AVAILABLE.name -> MaterialTheme.colorScheme.primary
    UpdateStatus.UNKNOWN.name -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.secondary
}

@Composable
internal fun signerLabel(status: String?): String = when (status) {
    "SIGNER_MATCH" -> stringResource(R.string.technical_signer_match)
    "SIGNER_MISMATCH" -> stringResource(R.string.technical_signer_mismatch)
    "NOT_INSTALLED_OR_NOT_VISIBLE" -> stringResource(R.string.technical_signer_new_install)
    else -> stringResource(R.string.technical_signer_unknown)
}

@Composable
internal fun modeLabel(mode: ManagementMode): String =
    stringResource(if (mode == ManagementMode.VERIFICATION) R.string.mode_verification else R.string.mode_acquisition)

@Composable
internal fun modeLabel(mode: String): String = modeLabel(enumValue(mode, ManagementMode.VERIFICATION))

@Composable
internal fun installationSourceLabel(source: InstallationSource): String =
    stringResource(
        if (source == InstallationSource.OFFICIAL_RELEASE) {
            R.string.technical_official_release_apk
        } else {
            R.string.technical_local_reprodroid_build
        },
    )

@Composable
internal fun installationSourceLabel(source: String): String =
    installationSourceLabel(enumValue(source, InstallationSource.OFFICIAL_RELEASE))

internal fun ReleaseVariantPreference.displayName(): String = name.lowercase().replaceFirstChar(Char::uppercase)

internal fun effectiveVariant(
    record: RegisteredAppRecord,
    settings: GlobalSettingsEntity,
): ReleaseVariantPreference = enumValue(
    if (record.app.useGlobalReleaseVariant) {
        settings.defaultReleaseVariantPreference
    } else {
        record.app.releaseVariantPreference
    },
    ReleaseVariantPreference.RELEASE,
)

internal fun effectiveAbi(record: RegisteredAppRecord, settings: GlobalSettingsEntity): PreferredAbi = enumValue(
    if (record.app.useGlobalPreferredAbi) settings.defaultPreferredAbi else record.app.preferredAbi,
    PreferredAbi.ARM64_V8A,
)

internal fun effectiveLimit(record: RegisteredAppRecord, settings: GlobalSettingsEntity): Long =
    if (record.app.useGlobalMaxApkSize) settings.defaultMaxApkSizeBytes else record.app.maxApkSizeBytes

internal inline fun <reified T : Enum<T>> enumValue(value: String, fallback: T): T =
    enumValues<T>().firstOrNull { it.name == value } ?: fallback

internal fun String.displayEnum(): String = lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)

@Composable
internal fun formatDecimalBytes(value: String): String =
    value.toLongOrNull()?.let(::formatBytes) ?: stringResource(R.string.value_not_available)

internal fun formatBytes(value: Long): String = when {
    value >= 1024L * 1024L * 1024L -> "%.2f GiB".format(value.toDouble() / (1024L * 1024L * 1024L))
    value >= MIB -> "%.2f MiB".format(value.toDouble() / MIB)
    value >= 1024L -> "%.2f KiB".format(value.toDouble() / 1024L)
    else -> "$value B"
}

@Composable
internal fun NavigationGlyph(value: String) {
    Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

internal const val MIB = 1024L * 1024L
internal const val MAX_SEMANTIC_DIFFERENCES_IN_UI = 3
internal const val MAX_DEPENDENCY_DIFFERENCES_IN_UI = 40
internal const val MAX_SOURCE_SCAN_FINDINGS_IN_UI = 40
internal const val AUDIT_MIME_TYPE = "application/vnd.reprodroid.audit+json"

private fun Long.pluralQuantity(): Int = coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
private const val LONG_TECHNICAL_VALUE_LENGTH = 80
internal val APK_LIMITS = listOf(64L * MIB, 128L * MIB, 256L * MIB, 512L * MIB)
