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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import com.sanka1610.reprodroid.R
import com.sanka1610.reprodroid.data.local.GlobalSettingsEntity
import com.sanka1610.reprodroid.data.local.RegisteredAppRecord
import com.sanka1610.reprodroid.data.network.V2CleanupPreviewResponse
import com.sanka1610.reprodroid.data.network.V2CleanupRunResponse
import com.sanka1610.reprodroid.data.storage.AndroidCleanupPreview
import com.sanka1610.reprodroid.data.storage.AndroidStorageSummary
import com.sanka1610.reprodroid.data.storage.RunnerStorageConnectionState
import com.sanka1610.reprodroid.data.storage.StagedAuditExport
import com.sanka1610.reprodroid.ui.*
import com.sanka1610.reprodroid.ui.shared.*
internal enum class StoragePage { USAGE, CLEANUP, AUDIT }

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
    page: StoragePage = StoragePage.USAGE,
    onClearAudit: () -> Unit = {},
    showAndroid: Boolean = true,
    showRunner: Boolean = true,
) {
    BackHandler(onBack = onBack)
    val storageBackDescription = stringResource(R.string.action_back)
    val storageRefreshDescription = stringResource(R.string.action_refresh)
    var selectedItemIds by remember(cleanupPreview?.previewId) { mutableStateOf(emptySet<String>()) }
    var auditScope by rememberSaveable { mutableStateOf("ALL") }
    var selectedRunnerItemIds by remember(runnerCleanupPreview?.previewId) { mutableStateOf(emptySet<String>()) }
    var confirmCleanup by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            expandedHeight = 56.dp,
            title = {
                Text(
                    stringResource(
                        if (!showAndroid) R.string.data_runner_separate else when (page) {
                            StoragePage.USAGE -> R.string.data_usage_settings
                            StoragePage.CLEANUP -> R.string.data_cleanup
                            StoragePage.AUDIT -> R.string.data_audit_export
                        },
                    ),
                )
            },
            navigationIcon = {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.semantics { contentDescription = storageBackDescription },
                ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
            },
            actions = {
                if (!showAndroid || page != StoragePage.AUDIT) {
                    IconButton(
                        enabled = !busy,
                        onClick = if (showAndroid && page == StoragePage.CLEANUP) onPreviewCleanup else onRefresh,
                        modifier = Modifier.semantics { contentDescription = storageRefreshDescription },
                    ) { Icon(Icons.Default.Refresh, contentDescription = null) }
                }
            },
        )
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (showAndroid && page == StoragePage.USAGE) {
            item {
                DetailCard(stringResource(R.string.storage_android_private)) {
                    if (androidSummary == null) {
                        Text(stringResource(R.string.storage_not_measured))
                    } else {
                        TechnicalValue(stringResource(R.string.storage_state), "${storageLabel(androidSummary.state)} · ${storageLabel(androidSummary.measurementState)}")
                        TechnicalValue(stringResource(R.string.storage_used), formatBytes(androidSummary.usedBytes))
                        TechnicalValue(stringResource(R.string.storage_reserved), formatBytes(androidSummary.reservedBytes))
                        TechnicalValue(stringResource(R.string.storage_budget), formatBytes(androidSummary.budgetBytes))
                        TechnicalValue(
                            stringResource(R.string.storage_unclassified),
                            androidSummary.unclassifiedBytes?.let(::formatBytes)
                                ?: stringResource(R.string.value_not_available),
                        )
                        TechnicalValue(stringResource(R.string.storage_usable_filesystem), androidSummary.usableBytes?.let(::formatBytes) ?: stringResource(R.string.value_not_available))
                        TechnicalValue(stringResource(R.string.storage_measured), androidSummary.measuredAt)
                    }
                    InformationButton(stringResource(R.string.storage_android_private), stringResource(R.string.storage_budget_no_automatic_delete))
                }
            }
            item {
                DropdownSetting(
                    label = stringResource(R.string.storage_android_budget),
                    value = settings.androidStorageBudgetBytes,
                    options = listOf(1L, 2L, 4L, 8L, 16L, 32L, 64L)
                        .associate { gib -> gib * 1024L * 1024L * 1024L to "$gib GiB" },
                    onSelect = { onUpdate(settings.copy(androidStorageBudgetBytes = it)) },
                    compact = true,
                    enabled = !busy,
                )
            }
            item {
                DropdownSetting(
                    label = stringResource(R.string.storage_warning_threshold),
                    value = settings.storageWarningPercent,
                    options = listOf(50, 60, 70, 80, 90, 95).associateWith { "$it%" },
                    onSelect = { onUpdate(settings.copy(storageWarningPercent = it)) },
                    compact = true,
                    enabled = !busy,
                )
            }
            }
            if (showRunner) {
            item {
                DetailCard(stringResource(R.string.storage_runner)) {
                    TechnicalValue(stringResource(R.string.storage_connection), storageLabel(runnerState.status.name))
                    runnerState.runnerId?.let { TechnicalValue(stringResource(R.string.storage_runner_id), it, monospace = true) }
                    runnerState.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    runnerState.summary?.areas?.forEach { area ->
                        HorizontalDivider()
                        TechnicalValue(stringResource(R.string.storage_area), area.area)
                        TechnicalValue(stringResource(R.string.storage_state), "${storageLabel(area.state)} · ${storageLabel(area.measurementState)}")
                        TechnicalValue(stringResource(R.string.storage_used_reserved), "${formatDecimalBytes(area.usedBytes)} / ${formatDecimalBytes(area.reservedBytes)}")
                        TechnicalValue(stringResource(R.string.storage_budget), formatDecimalBytes(area.budgetBytes))
                        TechnicalValue(stringResource(R.string.storage_usable_filesystem), formatDecimalBytes(area.usableBytes))
                    }
                    InformationButton(stringResource(R.string.storage_runner), stringResource(R.string.storage_runner_unavailable))
                }
            }
            }
            if (showAndroid && page == StoragePage.AUDIT) {
            item {
                DetailCard(stringResource(R.string.storage_local_audit)) {
                    InformationButton(stringResource(R.string.storage_local_audit), stringResource(R.string.storage_audit_boundary))
                    DropdownSetting(
                        label = stringResource(R.string.storage_scope),
                        value = auditScope,
                        options = linkedMapOf("ALL" to stringResource(R.string.storage_scope_all_apps)) +
                            apps.associate { it.app.registeredAppId to it.app.displayName },
                        onSelect = { auditScope = it; onClearAudit() },
                        compact = true,
                        enabled = !busy,
                    )
                    Button(
                        enabled = !busy && (auditScope == "ALL" || apps.any { it.app.registeredAppId == auditScope }),
                        onClick = { onStageAudit(if (auditScope == "ALL") emptySet() else setOf(auditScope)) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.storage_stage_audit)) }
                    auditExport?.let { export ->
                        TechnicalValue(stringResource(R.string.storage_state), storageLabel(export.state))
                        TechnicalValue(stringResource(R.string.storage_records), export.recordCount.toString())
                        TechnicalValue(stringResource(R.string.storage_size), formatBytes(export.sizeBytes))
                        TechnicalSection(stringResource(R.string.app_flow_details)) {
                            TechnicalValue(stringResource(R.string.storage_payload_sha256), export.payloadSha256, monospace = true)
                        }
                        export.errorCode?.let { TechnicalValue(stringResource(R.string.storage_error), it) }
                        Button(
                            enabled = !busy && export.state in setOf("STAGED", "FAILED"),
                            onClick = { onChooseAuditDestination(export.suggestedName) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.storage_choose_destination)) }
                    }
                }
            }
            }
            if (showAndroid && page == StoragePage.CLEANUP) {
            item {
                Button(enabled = !busy, onClick = onPreviewCleanup, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.storage_preview_android))
                }
            }
            cleanupPreview?.let { preview ->
                item {
                    DetailCard(stringResource(R.string.storage_manual_preview)) {
                        TechnicalValue(stringResource(R.string.storage_state), storageLabel(preview.state))
                        TechnicalValue(stringResource(R.string.storage_expires), preview.expiresAt)
                        TechnicalValue(stringResource(R.string.storage_items), preview.items.size.toString())
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
                                Text("${storageLabel(item.resourceKind)} · ${formatBytes(item.observedBytes)}")
                                InformationButton(stringResource(R.string.app_flow_details), item.resourceId + "\n" + stringResource(R.string.storage_eligible, item.eligibleAt))
                                if (item.protectionReasons.isNotEmpty()) {
                                    Text(stringResource(R.string.storage_protected, item.protectionReasons.map { storageLabel(it) }.joinToString()), color = MaterialTheme.colorScheme.error)
                                }
                                item.result?.let { TechnicalValue(stringResource(R.string.storage_result), storageLabel(it)) }
                                item.reasonCode?.let { TechnicalValue(stringResource(R.string.storage_reason), storageLabel(it)) }
                            }
                        }
                    }
                }
                item {
                    Button(
                        enabled = !busy && selectedItemIds.isNotEmpty() && !preview.truncated && preview.state == "PREVIEWED",
                        onClick = { confirmCleanup = true },
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
                        TechnicalValue(stringResource(R.string.storage_state), storageLabel(preview.state))
                        TechnicalValue(stringResource(R.string.storage_expires), preview.expiresAt)
                        TechnicalValue(stringResource(R.string.storage_items), preview.items.size.toString())
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
                                Text("${storageLabel(item.resourceKind)} · ${formatDecimalBytes(item.observedBytes)}")
                                InformationButton(stringResource(R.string.app_flow_details), item.resourceId + "\n" + stringResource(R.string.storage_eligible, item.eligibleAt))
                                if (item.protectionReasons.isNotEmpty()) {
                                    Text(stringResource(R.string.storage_protected, item.protectionReasons.map { storageLabel(it) }.joinToString()), color = MaterialTheme.colorScheme.error)
                                }
                                runItem?.let {
                                    TechnicalValue(stringResource(R.string.storage_result), storageLabel(it.result))
                                    it.reason?.let { reason -> TechnicalValue(stringResource(R.string.storage_reason), reason.code) }
                                }
                            }
                        }
                    }
                }
                item {
                    Button(
                        enabled = !busy && selectedRunnerItemIds.isNotEmpty() && !preview.truncated &&
                            runnerCleanupRun == null,
                        onClick = { confirmCleanup = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.storage_delete_runner)) }
                }
            }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
    if (confirmCleanup) AlertDialog(
        onDismissRequest = { confirmCleanup = false },
        title = { Text(stringResource(R.string.data_cleanup_confirm)) },
        text = { Text(stringResource(R.string.data_cleanup_selected, if (showAndroid) selectedItemIds.size else selectedRunnerItemIds.size)) },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                confirmCleanup = false
                if (showAndroid) onExecuteCleanup(selectedItemIds) else onExecuteRunnerCleanup(selectedRunnerItemIds)
            }) { Text(stringResource(R.string.action_delete)) }
        },
        dismissButton = { TextButton(onClick = { confirmCleanup = false }) { Text(stringResource(R.string.action_cancel)) } },
    )

}
