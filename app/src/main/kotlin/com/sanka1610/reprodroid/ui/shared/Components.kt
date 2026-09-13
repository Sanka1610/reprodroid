package com.sanka1610.reprodroid.ui.shared

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sanka1610.reprodroid.R
import com.sanka1610.reprodroid.data.local.PreferredAbi
import com.sanka1610.reprodroid.data.local.RegisteredAppRecord
import com.sanka1610.reprodroid.data.local.ReleaseVariantPreference
import com.sanka1610.reprodroid.ui.*
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BackScaffoldTitle(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(title) }, navigationIcon = { BackButton(onBack) })
        Box(Modifier.fillMaxSize()) { content() }
    }
}

@Composable
internal fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
    }
}

@Composable
internal fun EmptyState(
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodyMedium)
            actionLabel?.let { Button(onClick = onAction) { Text(it) } }
        }
    }
}

@Composable
internal fun MissingRecordScreen(onBack: () -> Unit) {
    EmptyState(
        title = stringResource(R.string.value_not_available),
        body = stringResource(R.string.apps_search_empty_body),
        actionLabel = stringResource(R.string.action_back),
        onAction = onBack,
    )
}

@Composable
internal fun StatusChip(value: String) {
    AssistChip(onClick = {}, label = { Text(statusLabel(value)) })
}

@Composable
internal fun UiRDetailCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
internal fun UiRDetailValue(label: String, value: String, monospace: Boolean = false) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f),
            )
            if ((monospace || value.length > LONG_PRESENTATION_VALUE_LENGTH) && value.isNotBlank()) {
                CopyValueButton(value)
            }
        }
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
            maxLines = if (monospace) 4 else 6,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun CopyValueButton(value: String) {
    val clipboard = LocalClipboardManager.current
    TextButton(onClick = { clipboard.setText(AnnotatedString(value)) }) {
        Text(stringResource(R.string.action_copy))
    }
}

internal fun repositoryOwner(repositoryUrl: String): String = runCatching {
    Uri.parse(repositoryUrl).pathSegments.firstOrNull()
}.getOrNull().orEmpty().ifBlank { "UNKNOWN" }

internal fun knownPackageName(record: RegisteredAppRecord): String? =
    record.latestRelease?.selectedAsset?.packageName
        ?: record.releases.asSequence()
            .flatMap { it.assets.asSequence() }
            .mapNotNull { it.packageName }
            .firstOrNull()

internal fun String.humanize(): String = lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)

@Composable
internal fun statusLabel(value: String?): String = statusLabelResource(value)?.let { stringResource(it) }
    ?: requireNotNull(value).humanize()

internal fun statusLabelResource(value: String?): Int? = when (value) {
    "ACTIVE" -> R.string.state_active
    "INACTIVE" -> R.string.state_inactive
    "NOT_CHECKED", "NOT_EVALUATED" -> R.string.state_not_checked
    "CHECKING", "RESOLVING", "SCANNING_TREE" -> R.string.state_checking
    "PENDING", "QUEUED" -> R.string.state_pending
    "RUNNING", "BUILDING" -> R.string.state_running
    "AVAILABLE", "UP_TO_DATE", "SUCCESS", "COMPLETE", "COMPLETED" -> R.string.state_success
    "UPDATE_AVAILABLE" -> R.string.state_update_available
    "NOT_INSTALLED" -> R.string.state_not_installed
    "REPRODUCIBLE", "EQUIVALENT" -> R.string.state_reproducible
    "MATCH" -> R.string.state_match
    "BUILDABLE" -> R.string.state_buildable
    "DIFFERENT" -> R.string.state_different
    "INCOMPARABLE" -> R.string.state_incomparable
    "FAILED", "ERROR" -> R.string.state_error
    "CANCELLED" -> R.string.state_cancelled
    "INTERRUPTED" -> R.string.state_interrupted
    "AWAITING_ASSET_SELECTION" -> R.string.state_awaiting_selection
    "OLDER_THAN_INSTALLED" -> R.string.state_older_than_installed
    "UNKNOWN", null -> R.string.value_unknown
    else -> null
}

@Composable
internal fun localizedEnumLabel(value: String): String = when (value) {
    ReleaseVariantPreference.RELEASE.name -> stringResource(R.string.enum_release)
    ReleaseVariantPreference.PREVIEW.name -> stringResource(R.string.enum_preview)
    ReleaseVariantPreference.DEBUG.name -> stringResource(R.string.enum_debug)
    else -> value.humanize()
}

@Composable
internal fun abiLabel(value: String): String = when (value) {
    PreferredAbi.ARM64_V8A.name -> "arm64-v8a"
    PreferredAbi.ARMEABI_V7A.name -> "armeabi-v7a"
    PreferredAbi.X86_64.name -> "x86_64"
    PreferredAbi.UNIVERSAL.name -> stringResource(R.string.enum_universal)
    else -> value
}

internal fun humanBytes(value: Long): String = when {
    value >= 1024L * 1024 * 1024 -> "%.2f GiB".format(value / (1024.0 * 1024 * 1024))
    value >= 1024L * 1024 -> "%.2f MiB".format(value / (1024.0 * 1024))
    value >= 1024L -> "%.2f KiB".format(value / 1024.0)
    else -> "$value B"
}

internal const val ALL_GROUP_ID = "__all__"
internal const val UNGROUPED_ID = "__ungrouped__"
private const val LONG_PRESENTATION_VALUE_LENGTH = 80
internal const val MAX_GROUP_NAME_LENGTH = 80
internal const val MAX_DISPLAY_NAME_LENGTH = 120
internal const val MAX_AUTHOR_DISPLAY_LENGTH = 160
internal const val MAX_NOTE_LENGTH = 10_000
internal const val MEBIBYTE = 1024L * 1024L
internal val UI_R_APK_LIMITS = listOf(64L * MEBIBYTE, 128L * MEBIBYTE, 256L * MEBIBYTE, 512L * MEBIBYTE)
