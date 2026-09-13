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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> DropdownSetting(
    label: String,
    value: T,
    options: Map<T, String>,
    onSelect: (T) -> Unit,
    enabled: Boolean = true,
    supportingText: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = !expanded },
    ) {
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
        DetailValue(label, "Not available")
        return
    }
    DetailValue(
        label,
        if (evidence.scan.findingCount == 0) {
            "No configured detector findings"
        } else {
            "${evidence.scan.findingCount} configured detector findings"
        },
    )
    DetailValue(
        "$label scope",
        "${evidence.scan.scannedFiles} files, ${evidence.scan.scannedBytes} bytes; " +
            "binary skipped ${evidence.scan.skippedBinaryFiles}, symlinks skipped ${evidence.scan.skippedSymlinks}",
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
        "Static indicators only; this is not a safe/malicious verdict and does not change comparison, trust, update, or install policy.",
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
internal fun DetailValue(label: String, value: String, monospace: Boolean = false) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
            maxLines = if (monospace) 4 else 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

internal fun trustLabel(record: RegisteredAppRecord): String = when (record.trustLevel) {
    TrustLevel.REPRODUCIBLE -> "Reproducible"
    TrustLevel.BUILDABLE -> "Buildable"
    TrustLevel.DIFFERENT -> "Different"
    TrustLevel.INCOMPARABLE -> "Incomparable"
    TrustLevel.FAILED -> "Failed"
    null -> when (record.currentComparison?.status) {
        ComparisonRunStatus.BUILDING.name,
        ComparisonRunStatus.REPEAT_BUILDING.name -> "Building"
        ComparisonRunStatus.COMPARING.name,
        ComparisonRunStatus.COMPARING_REPEAT.name -> "Comparing"
        ComparisonRunStatus.AWAITING_CONFIRMATION.name,
        ComparisonRunStatus.AWAITING_REPEAT_CONFIRMATION.name -> "Confirmation required"
        ComparisonRunStatus.AWAITING_SCAN_REVIEW.name,
        ComparisonRunStatus.AWAITING_REPEAT_SCAN_REVIEW.name -> "Source scan review required"
        else -> "Not evaluated"
    }
}

internal fun updateLabel(status: String?): String = when (status) {
    UpdateStatus.NOT_INSTALLED.name -> "Not installed"
    UpdateStatus.UPDATE_AVAILABLE.name -> "Update available"
    UpdateStatus.UP_TO_DATE.name -> "Up to date"
    UpdateStatus.OLDER_THAN_INSTALLED.name -> "Older release"
    UpdateStatus.UNKNOWN.name -> "Unknown"
    else -> "Not evaluated"
}

@Composable
internal fun updateColor(status: String?): Color = when (status) {
    UpdateStatus.UPDATE_AVAILABLE.name -> MaterialTheme.colorScheme.primary
    UpdateStatus.UNKNOWN.name -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.secondary
}

internal fun signerLabel(status: String?): String = when (status) {
    "SIGNER_MATCH" -> "Signer match"
    "SIGNER_MISMATCH" -> "Signer mismatch"
    "NOT_INSTALLED_OR_NOT_VISIBLE" -> "New install"
    else -> "Signer unknown"
}

internal fun modeLabel(mode: ManagementMode): String =
    if (mode == ManagementMode.VERIFICATION) "Verification" else "Acquisition"

internal fun modeLabel(mode: String): String = modeLabel(enumValue(mode, ManagementMode.VERIFICATION))

internal fun installationSourceLabel(source: InstallationSource): String =
    if (source == InstallationSource.OFFICIAL_RELEASE) "Official release APK" else "Local ReproDroid build"

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

internal fun formatDecimalBytes(value: String): String = value.toLongOrNull()?.let(::formatBytes) ?: "Unavailable"

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
internal val APK_LIMITS = listOf(64L * MIB, 128L * MIB, 256L * MIB, 512L * MIB)
