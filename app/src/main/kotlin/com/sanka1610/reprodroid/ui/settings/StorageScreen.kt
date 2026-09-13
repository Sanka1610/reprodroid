package com.sanka1610.reprodroid.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.sanka1610.reprodroid.R
import com.sanka1610.reprodroid.data.local.GlobalSettingsEntity
import com.sanka1610.reprodroid.data.local.RegisteredAppRecord
import com.sanka1610.reprodroid.data.storage.AndroidCleanupPreview
import com.sanka1610.reprodroid.data.storage.AndroidStorageSummary
import com.sanka1610.reprodroid.data.storage.RunnerStorageConnectionState
import com.sanka1610.reprodroid.data.storage.StagedAuditExport
import com.sanka1610.reprodroid.data.network.V2CleanupPreviewResponse
import com.sanka1610.reprodroid.data.network.V2CleanupRunResponse


import com.sanka1610.reprodroid.ui.*
import com.sanka1610.reprodroid.ui.shared.*
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StorageScreen(
    apps: List<RegisteredAppRecord>,
    settings: GlobalSettingsEntity,
    androidSummary: AndroidStorageSummary?,
    runnerState: RunnerStorageConnectionState,
    cleanupPreview: AndroidCleanupPreview?,
    busy: Boolean,
    auditExport: StagedAuditExport?,
    runnerCleanupPreview: V2CleanupPreviewResponse?,
    runnerCleanupRun: V2CleanupRunResponse?,
    onBack: () -> Unit,
    onUpdate: (GlobalSettingsEntity) -> Unit,
    onRefresh: () -> Unit,
    onPreviewCleanup: () -> Unit,
    onExecuteCleanup: (Set<String>) -> Unit,
    onStageAudit: (Set<String>) -> Unit,
    onChooseAuditDestination: (String) -> Unit,
    onPreviewRunnerCleanup: () -> Unit,
    onExecuteRunnerCleanup: (Set<String>) -> Unit,
    showAndroid: Boolean = true,
    showRunner: Boolean = true,
) {
    BackHandler(onBack = onBack)
    val storageBackDescription = stringResource(R.string.action_back)
    val storageRefreshDescription = stringResource(R.string.action_refresh)
    var selectedItemIds by remember(cleanupPreview?.previewId) { mutableStateOf(emptySet<String>()) }
    var auditScope by rememberSaveable { mutableStateOf("ALL") }
    var selectedRunnerItemIds by remember(runnerCleanupPreview?.previewId) { mutableStateOf(emptySet<String>()) }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    stringResource(
                        if (showAndroid) R.string.data_management_title else R.string.data_runner_separate,
                    ),
                )
            },
            navigationIcon = {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.semantics { contentDescription = storageBackDescription },
                ) { NavigationGlyph("‹") }
            },
            actions = {
                IconButton(
                    enabled = !busy,
                    onClick = onRefresh,
                    modifier = Modifier.semantics { contentDescription = storageRefreshDescription },
                ) { NavigationGlyph("↻") }
            },
        )
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (showAndroid) {
            item {
                DetailCard(stringResource(R.string.storage_android_private)) {
                    if (androidSummary == null) {
                        Text(stringResource(R.string.storage_not_measured))
                    } else {
                        DetailValue(stringResource(R.string.storage_state), "${androidSummary.state} · ${androidSummary.measurementState}")
                        DetailValue(stringResource(R.string.storage_used), formatBytes(androidSummary.usedBytes))
                        DetailValue(stringResource(R.string.storage_reserved), formatBytes(androidSummary.reservedBytes))
                        DetailValue(stringResource(R.string.storage_budget), formatBytes(androidSummary.budgetBytes))
                        DetailValue(
                            "Unclassified",
                            androidSummary.unclassifiedBytes?.let(::formatBytes) ?: "Unavailable",
                        )
                        DetailValue(stringResource(R.string.storage_usable_filesystem), androidSummary.usableBytes?.let(::formatBytes) ?: stringResource(R.string.value_not_available))
                        DetailValue(stringResource(R.string.storage_measured), androidSummary.measuredAt)
                    }
                    Text(
                        "Lowering the budget below current usage never deletes files automatically.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            item {
                DropdownSetting(
                    label = stringResource(R.string.storage_android_budget),
                    value = settings.androidStorageBudgetBytes,
                    options = listOf(1L, 2L, 4L, 8L, 16L, 32L, 64L)
                        .associate { gib -> gib * 1024L * 1024L * 1024L to "$gib GiB" },
                    onSelect = { onUpdate(settings.copy(androidStorageBudgetBytes = it)) },
                    enabled = !busy,
                )
            }
            item {
                DropdownSetting(
                    label = stringResource(R.string.storage_warning_threshold),
                    value = settings.storageWarningPercent,
                    options = listOf(50, 60, 70, 80, 90, 95).associateWith { "$it%" },
                    onSelect = { onUpdate(settings.copy(storageWarningPercent = it)) },
                    enabled = !busy,
                )
            }
            }
            if (showRunner) {
            item {
                DetailCard(stringResource(R.string.storage_runner)) {
                    DetailValue(stringResource(R.string.storage_connection), runnerState.status.name)
                    runnerState.runnerId?.let { DetailValue(stringResource(R.string.storage_runner_id), it, monospace = true) }
                    runnerState.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    runnerState.summary?.areas?.forEach { area ->
                        HorizontalDivider()
                        DetailValue(stringResource(R.string.storage_area), area.area)
                        DetailValue(stringResource(R.string.storage_state), "${area.state} · ${area.measurementState}")
                        DetailValue(stringResource(R.string.storage_used_reserved), "${formatDecimalBytes(area.usedBytes)} / ${formatDecimalBytes(area.reservedBytes)}")
                        DetailValue(stringResource(R.string.storage_budget), formatDecimalBytes(area.budgetBytes))
                        DetailValue(stringResource(R.string.storage_usable_filesystem), formatDecimalBytes(area.usableBytes))
                    }
                    Text(
                        "Unavailable or incompatible Runner storage is never treated as empty. Local Android history remains available.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            }
            if (showAndroid) {
            item {
                DetailCard(stringResource(R.string.storage_local_audit)) {
                    Text(
                        "Exports public allowlisted history only. APKs, source text, private Manifests, raw logs, credentials, and storage paths are excluded.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    DropdownSetting(
                        label = stringResource(R.string.storage_scope),
                        value = auditScope,
                        options = linkedMapOf("ALL" to "All registered apps") +
                            apps.associate { it.app.registeredAppId to it.app.displayName },
                        onSelect = { auditScope = it },
                        enabled = !busy,
                    )
                    Button(
                        enabled = !busy && (auditScope == "ALL" || apps.any { it.app.registeredAppId == auditScope }),
                        onClick = { onStageAudit(if (auditScope == "ALL") emptySet() else setOf(auditScope)) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.storage_stage_audit)) }
                    auditExport?.let { export ->
                        DetailValue(stringResource(R.string.storage_state), export.state)
                        DetailValue(stringResource(R.string.storage_records), export.recordCount.toString())
                        DetailValue(stringResource(R.string.storage_size), formatBytes(export.sizeBytes))
                        DetailValue(stringResource(R.string.storage_payload_sha256), export.payloadSha256, monospace = true)
                        export.errorCode?.let { DetailValue(stringResource(R.string.storage_error), it) }
                        Button(
                            enabled = !busy && export.state in setOf("STAGED", "FAILED"),
                            onClick = { onChooseAuditDestination(export.suggestedName) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.storage_choose_destination)) }
                    }
                }
            }
            item {
                Button(enabled = !busy, onClick = onPreviewCleanup, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.storage_preview_android))
                }
            }
            cleanupPreview?.let { preview ->
                item {
                    DetailCard(stringResource(R.string.storage_manual_preview)) {
                        DetailValue(stringResource(R.string.storage_state), preview.state)
                        DetailValue(stringResource(R.string.storage_expires), preview.expiresAt)
                        DetailValue(stringResource(R.string.storage_items), preview.items.size.toString())
                        if (preview.truncated) {
                            Text(stringResource(R.string.storage_truncated), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                items(preview.items, key = { it.itemId }) { item ->
                    val selectable = item.protectionReasons.isEmpty() && item.result == null && !preview.truncated
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Checkbox(
                                checked = item.itemId in selectedItemIds,
                                enabled = selectable && !busy,
                                onCheckedChange = { selected ->
                                    selectedItemIds = if (selected) selectedItemIds + item.itemId else selectedItemIds - item.itemId
                                },
                            )
                            Column(Modifier.weight(1f)) {
                                Text("${item.resourceKind} · ${formatBytes(item.observedBytes)}")
                                Text(item.resourceId, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                                Text(stringResource(R.string.storage_eligible, item.eligibleAt), style = MaterialTheme.typography.bodySmall)
                                if (item.protectionReasons.isNotEmpty()) {
                                    Text(stringResource(R.string.storage_protected, item.protectionReasons.joinToString()), color = MaterialTheme.colorScheme.error)
                                }
                                item.result?.let { DetailValue(stringResource(R.string.storage_result), it) }
                                item.reasonCode?.let { DetailValue(stringResource(R.string.storage_reason), it) }
                            }
                        }
                    }
                }
                item {
                    Button(
                        enabled = !busy && selectedItemIds.isNotEmpty() && !preview.truncated && preview.state == "PREVIEWED",
                        onClick = { onExecuteCleanup(selectedItemIds) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.storage_delete_android)) }
                }
            }
            }
            if (showRunner) {
            item {
                HorizontalDivider()
                Button(
                    enabled = !busy && runnerState.status.name == "AVAILABLE",
                    onClick = onPreviewRunnerCleanup,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.storage_preview_runner)) }
            }
            runnerCleanupPreview?.let { preview ->
                item {
                    DetailCard(stringResource(R.string.storage_runner_manual_preview)) {
                        DetailValue(stringResource(R.string.storage_state), preview.state)
                        DetailValue(stringResource(R.string.storage_expires), preview.expiresAt)
                        DetailValue(stringResource(R.string.storage_items), preview.items.size.toString())
                        if (preview.truncated) {
                            Text(stringResource(R.string.storage_truncated), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                items(preview.items, key = { "runner-${it.itemId}" }) { item ->
                    val runItem = runnerCleanupRun?.items?.firstOrNull { it.itemId == item.itemId }
                    val selectable = item.protectionReasons.isEmpty() && runItem == null && !preview.truncated
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Top) {
                            Checkbox(
                                checked = item.itemId in selectedRunnerItemIds,
                                enabled = selectable && !busy,
                                onCheckedChange = { selected ->
                                    selectedRunnerItemIds = if (selected) {
                                        selectedRunnerItemIds + item.itemId
                                    } else {
                                        selectedRunnerItemIds - item.itemId
                                    }
                                },
                            )
                            Column(Modifier.weight(1f)) {
                                Text("${item.resourceKind} · ${formatDecimalBytes(item.observedBytes)}")
                                Text(item.resourceId, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                                Text(stringResource(R.string.storage_eligible, item.eligibleAt), style = MaterialTheme.typography.bodySmall)
                                if (item.protectionReasons.isNotEmpty()) {
                                    Text(stringResource(R.string.storage_protected, item.protectionReasons.joinToString()), color = MaterialTheme.colorScheme.error)
                                }
                                runItem?.let {
                                    DetailValue(stringResource(R.string.storage_result), it.result)
                                    it.reason?.let { reason -> DetailValue(stringResource(R.string.storage_reason), reason.code) }
                                }
                            }
                        }
                    }
                }
                item {
                    Button(
                        enabled = !busy && selectedRunnerItemIds.isNotEmpty() && !preview.truncated &&
                            runnerCleanupRun == null,
                        onClick = { onExecuteRunnerCleanup(selectedRunnerItemIds) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.storage_delete_runner)) }
                }
            }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}
