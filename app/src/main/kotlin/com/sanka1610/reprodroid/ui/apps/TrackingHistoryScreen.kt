package com.sanka1610.reprodroid.ui.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sanka1610.reprodroid.R
import com.sanka1610.reprodroid.data.local.RegisteredAppRecord
import com.sanka1610.reprodroid.data.repository.AppDeletionPreview
import com.sanka1610.reprodroid.ui.*
import com.sanka1610.reprodroid.ui.shared.*
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InactiveAppsScreen(
    apps: List<RegisteredAppRecord>,
    allowCompleteDeletion: Boolean,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onResume: (String) -> Unit,
    onPreviewDelete: (String) -> Unit,
    deletionPreview: AppDeletionPreview?,
    deletionResult: com.sanka1610.reprodroid.data.repository.AppDeletionResult?,
    onDelete: () -> Unit,
    onDismissDelete: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(stringResource(R.string.inactive_apps_title)) }, navigationIcon = { BackButton(onBack) })
        if (apps.isEmpty()) {
            EmptyState(stringResource(R.string.inactive_apps_title), stringResource(R.string.inactive_apps_empty))
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(apps, key = { it.app.registeredAppId }) { record ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(record.app.resolvedDisplayName, style = MaterialTheme.typography.titleMedium)
                            Text(record.app.canonicalRepositoryUrl, style = MaterialTheme.typography.bodySmall)
                            Column(Modifier.fillMaxWidth()) {
                                TextButton(
                                    onClick = { onOpen(record.app.registeredAppId) },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(stringResource(R.string.action_information))
                                }
                                TextButton(
                                    onClick = { onResume(record.app.registeredAppId) },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(stringResource(R.string.action_resume_tracking))
                                }
                                if (allowCompleteDeletion) {
                                    TextButton(
                                        onClick = { onPreviewDelete(record.app.registeredAppId) },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(stringResource(R.string.action_preview_deletion))
                                    }
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(12.dp)) }
            }
        }
    }
    deletionPreview?.takeIf { allowCompleteDeletion }?.let { preview ->
        DeletionPreviewDialog(preview, onDismissDelete, onDelete)
    }
    deletionResult?.takeIf { allowCompleteDeletion }?.let { result ->
        AlertDialog(
            onDismissRequest = onDismissDelete,
            title = {
                Text(
                    stringResource(
                        if (result.failedFileNames.isEmpty()) {
                            R.string.deletion_result_complete
                        } else {
                            R.string.deletion_result_reconciliation
                        },
                    ),
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.deletion_result_files, result.deletedFiles))
                    Text(stringResource(R.string.deletion_result_bytes, humanBytes(result.releasedBytes)))
                    if (result.failedFileNames.isNotEmpty()) {
                        Text(stringResource(R.string.deletion_result_failed_files, result.failedFileNames.joinToString()))
                        Text(stringResource(R.string.deletion_result_cleanup_hint))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismissDelete) { Text(stringResource(R.string.action_dismiss)) }
            },
        )
    }
}

@Composable
private fun DeletionPreviewDialog(preview: AppDeletionPreview, onDismiss: () -> Unit, onDelete: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.deletion_preview_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(preview.displayName, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.deletion_preview_body))
                Text(stringResource(R.string.deletion_release_count, preview.releaseCount))
                Text(stringResource(R.string.deletion_comparison_count, preview.comparisonCount))
                Text(stringResource(R.string.deletion_install_count, preview.installAttemptCount))
                Text(stringResource(R.string.deletion_local_bytes, humanBytes(preview.localBytes)))
                if (preview.protectionReasons.isNotEmpty()) {
                    Text(
                        stringResource(R.string.deletion_protected, preview.protectionReasons.joinToString()),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Text(stringResource(R.string.deletion_audit_hint), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(enabled = preview.protectionReasons.isEmpty(), onClick = onDelete) {
                Text(stringResource(R.string.action_delete_permanently))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
