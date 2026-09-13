package com.sanka1610.reprodroid.ui.runner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.sanka1610.reprodroid.data.connection.RunnerConnectionIssue
import com.sanka1610.reprodroid.data.local.RunnerConnectionEntity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.sanka1610.reprodroid.R
import com.sanka1610.reprodroid.data.connection.ManualPairingPayloadParser
import com.sanka1610.reprodroid.data.connection.RunnerConnectionPhase
import com.sanka1610.reprodroid.data.connection.RunnerConnectionStatus
import com.sanka1610.reprodroid.ui.*
import com.sanka1610.reprodroid.ui.settings.SettingsLink
import com.sanka1610.reprodroid.ui.shared.*
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RunnerSettingsScreen(
    onBack: () -> Unit,
    onStorage: () -> Unit,
    onJobs: () -> Unit,
    onToolchains: () -> Unit,
    onAuthentication: () -> Unit,
) {
    BackScaffoldTitle(stringResource(R.string.settings_runner), onBack) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsLink(stringResource(R.string.settings_storage), onStorage)
            SettingsLink(stringResource(R.string.settings_toolchains), onToolchains)
            SettingsLink(stringResource(R.string.settings_jobs), onJobs)
            SettingsLink(stringResource(R.string.settings_authentication), onAuthentication)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RunnerAuthenticationScreen(
    status: RunnerConnectionStatus,
    connections: List<RunnerConnectionEntity>,
    onPair: (String) -> Unit,
    onRefresh: () -> Unit,
    onCancelPending: (String) -> Unit,
    onSelfRevoke: () -> Unit,
    onLocalDelete: (String) -> Unit,
    onBack: () -> Unit,
) {
    var payload by remember { mutableStateOf("") }
    var confirmRevoke by remember { mutableStateOf(false) }
    var deleteRunnerId by remember { mutableStateOf<String?>(null) }
    // Like the editor, this short-lived secret never enters saved state or Room.
    var replacementPayload by remember { mutableStateOf<String?>(null) }
    var endpointHelp by remember { mutableStateOf(false) }
    val parsed = remember(payload) {
        payload.takeIf(String::isNotBlank)?.let { runCatching { ManualPairingPayloadParser.parse(it) } }
    }
    val active = connections.firstOrNull { it.active } ?: status.active
    val pending = connections.firstOrNull { it.pairingState == "PENDING_APPROVAL" } ?: status.pending
    BackScaffoldTitle(stringResource(R.string.settings_authentication), onBack) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.runner_connection_title), style = MaterialTheme.typography.titleMedium)
            Text(
                when (status.phase) {
                    RunnerConnectionPhase.INITIALIZING -> stringResource(R.string.runner_connection_initializing)
                    RunnerConnectionPhase.UNCONFIGURED -> stringResource(R.string.runner_connection_unconfigured)
                    RunnerConnectionPhase.DEVELOPMENT -> stringResource(R.string.runner_connection_development)
                    RunnerConnectionPhase.PENDING_APPROVAL -> stringResource(R.string.runner_connection_pending)
                    RunnerConnectionPhase.CONFIGURED -> stringResource(R.string.runner_connection_configured)
                    RunnerConnectionPhase.CONNECTED -> stringResource(R.string.runner_connection_connected)
                    RunnerConnectionPhase.ERROR -> stringResource(R.string.runner_connection_error)
                },
                fontWeight = FontWeight.SemiBold,
            )
            status.issue?.let { Text(stringResource(runnerConnectionIssueString(it))) }
            active?.let { connection ->
                ConnectionValue(stringResource(R.string.runner_endpoint), connection.endpoint)
                ConnectionValue(stringResource(R.string.runner_id), connection.runnerId)
                ConnectionValue(stringResource(R.string.runner_pin), connection.rootSpkiSha256)
                ConnectionValue(stringResource(R.string.runner_transport), stringResource(
                    if (connection.transportMode == "PAIRED_HTTPS") R.string.runner_transport_paired else R.string.runner_connection_development,
                ))
                ConnectionValue(stringResource(R.string.runner_credential_created), connection.createdAt)
                ConnectionValue(stringResource(R.string.runner_revocation_knowledge), stringResource(
                    when (connection.revocationKnowledge) {
                        "ACTIVE" -> R.string.runner_revocation_active
                        "REVOKED" -> R.string.runner_revocation_confirmed
                        else -> R.string.runner_revocation_unknown
                    },
                ))
                status.lastCheckedAt?.let {
                    ConnectionValue(stringResource(R.string.runner_last_checked), it)
                }
                if (connection.active) {
                    OutlinedButton(onClick = onRefresh, Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.runner_check_connection))
                    }
                }
                OutlinedButton(onClick = { endpointHelp = !endpointHelp }, Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.runner_change_endpoint))
                }
                if (endpointHelp) Text(stringResource(R.string.runner_change_endpoint_help))
                if (connection.active) {
                    OutlinedButton(onClick = { confirmRevoke = true }, Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.runner_self_revoke))
                    }
                }
                TextButton(onClick = { deleteRunnerId = connection.runnerId }, Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.runner_local_delete))
                }
            }
            pending?.let { connection ->
                HorizontalDivider()
                Text(stringResource(R.string.runner_pc_approval), style = MaterialTheme.typography.titleMedium)
                ConnectionValue(stringResource(R.string.runner_id), connection.runnerId)
                ConnectionValue(stringResource(R.string.runner_endpoint), connection.endpoint)
                connection.confirmationFingerprint?.let {
                    ConnectionValue(stringResource(R.string.runner_confirmation_fingerprint), it)
                }
                connection.pairingExpiresAt?.let {
                    ConnectionValue(stringResource(R.string.runner_pairing_expires), it)
                }
                Text(stringResource(R.string.runner_pc_approval_help))
                OutlinedButton(
                    onClick = { onCancelPending(connection.runnerId) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.runner_cancel_pairing)) }
                TextButton(onClick = { deleteRunnerId = connection.runnerId }, Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.runner_local_delete))
                }
            }
            connections.filter { it.runnerId != active?.runnerId && it.runnerId != pending?.runnerId }.forEach { connection ->
                HorizontalDivider()
                Text(stringResource(R.string.runner_retained_record), style = MaterialTheme.typography.titleMedium)
                ConnectionValue(stringResource(R.string.runner_id), connection.runnerId)
                ConnectionValue(stringResource(R.string.runner_endpoint), connection.endpoint)
                Text(stringResource(R.string.runner_change_endpoint_help))
                TextButton(onClick = { deleteRunnerId = connection.runnerId }, Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.runner_local_delete))
                }
            }
            HorizontalDivider()
            Text(stringResource(R.string.runner_manual_pairing), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.runner_manual_pairing_help))
            OutlinedTextField(
                value = payload,
                onValueChange = { if (it.length <= 5_464) payload = it },
                label = { Text(stringResource(R.string.runner_pairing_payload)) },
                visualTransformation = PasswordVisualTransformation(),
                supportingText = {
                    Text(
                        when {
                            payload.isBlank() -> stringResource(R.string.runner_pairing_payload_empty)
                            parsed?.isSuccess == true -> stringResource(R.string.runner_pairing_payload_valid)
                            else -> stringResource(R.string.runner_pairing_payload_invalid)
                        },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
            parsed?.getOrNull()?.let { value ->
                ConnectionValue(stringResource(R.string.runner_endpoint), value.endpoint)
                ConnectionValue(stringResource(R.string.runner_id), value.runnerId)
                ConnectionValue(stringResource(R.string.runner_pin), value.rootSpkiSha256)
                ConnectionValue(stringResource(R.string.runner_pairing_expires), value.expiresAt)
            }
            Button(
                enabled = parsed?.isSuccess == true && connections.none { it.pairingState == "PENDING_APPROVAL" },
                onClick = {
                    val submitted = payload
                    payload = ""
                    if (connections.any { it.active || it.runnerId == parsed?.getOrNull()?.runnerId }) {
                        replacementPayload = submitted
                    } else {
                        onPair(submitted)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.runner_start_pairing)) }
            Text(stringResource(R.string.runner_pairing_boundaries), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.runner_lost_device_help), style = MaterialTheme.typography.bodySmall)
        }
    }
    if (confirmRevoke) {
        AlertDialog(
            onDismissRequest = { confirmRevoke = false },
            title = { Text(stringResource(R.string.runner_self_revoke)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.runner_self_revoke_confirm))
                    active?.let { connection ->
                        ConnectionValue(stringResource(R.string.runner_id), connection.runnerId)
                        ConnectionValue(stringResource(R.string.runner_endpoint), connection.endpoint)
                        ConnectionValue(stringResource(R.string.runner_pin), connection.rootSpkiSha256)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { confirmRevoke = false; onSelfRevoke() }) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = { TextButton(onClick = { confirmRevoke = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    deleteRunnerId?.let { selectedRunnerId ->
        val selectedConnection = connections.firstOrNull { it.runnerId == selectedRunnerId }
        AlertDialog(
            onDismissRequest = { deleteRunnerId = null },
            title = { Text(stringResource(R.string.runner_local_delete)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.runner_local_delete_confirm))
                    ConnectionValue(stringResource(R.string.runner_id), selectedRunnerId)
                    selectedConnection?.let { connection ->
                        ConnectionValue(stringResource(R.string.runner_endpoint), connection.endpoint)
                        ConnectionValue(stringResource(R.string.runner_pin), connection.rootSpkiSha256)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { deleteRunnerId = null; onLocalDelete(selectedRunnerId) }) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = { TextButton(onClick = { deleteRunnerId = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    replacementPayload?.let { submitted ->
        val replacement = runCatching { ManualPairingPayloadParser.parse(submitted) }.getOrNull()
        val changesEndpoint = connections.any {
            replacement != null && it.runnerId == replacement.runnerId && it.endpoint != replacement.endpoint
        }
        AlertDialog(
            onDismissRequest = { replacementPayload = null },
            title = { Text(stringResource(if (changesEndpoint) R.string.runner_change_endpoint else R.string.runner_repair_confirm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(if (changesEndpoint) R.string.runner_endpoint_confirm else R.string.runner_repair_confirm))
                    replacement?.let { connection ->
                        ConnectionValue(stringResource(R.string.runner_id), connection.runnerId)
                        ConnectionValue(stringResource(R.string.runner_endpoint), connection.endpoint)
                        ConnectionValue(stringResource(R.string.runner_pin), connection.rootSpkiSha256)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { replacementPayload = null; onPair(submitted) }) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { replacementPayload = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

private fun runnerConnectionIssueString(issue: RunnerConnectionIssue): Int = when (issue) {
    RunnerConnectionIssue.AMBIGUOUS_RECORDS -> R.string.runner_issue_ambiguous
    RunnerConnectionIssue.CREDENTIAL_UNAVAILABLE -> R.string.runner_issue_credential
    RunnerConnectionIssue.PAIRING_INTERRUPTED -> R.string.runner_issue_interrupted
    RunnerConnectionIssue.PAIRING_RETRY -> R.string.runner_issue_retry
    RunnerConnectionIssue.PAIRING_ENDED -> R.string.runner_issue_ended
    RunnerConnectionIssue.PAIRING_EXPIRED -> R.string.runner_issue_expired
    RunnerConnectionIssue.PAIRING_INVALID -> R.string.runner_issue_invalid_response
    RunnerConnectionIssue.CANCELLED_LOCALLY -> R.string.runner_issue_cancelled
    RunnerConnectionIssue.REVOCATION_PENDING -> R.string.runner_issue_revocation_pending
    RunnerConnectionIssue.REVOCATION_CONFIRMED -> R.string.runner_issue_revocation_confirmed
    RunnerConnectionIssue.AUTHENTICATION_UNKNOWN -> R.string.runner_issue_authentication_unknown
    RunnerConnectionIssue.LOCAL_DELETION -> R.string.runner_issue_local_deletion
    RunnerConnectionIssue.NETWORK_UNAVAILABLE -> R.string.runner_issue_network
    RunnerConnectionIssue.INVALID_PAYLOAD -> R.string.runner_pairing_payload_invalid
}

@Composable
private fun ConnectionValue(label: String, value: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
        Text(value, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlannedFeatureScreen(
    title: String,
    body: String,
    onBack: () -> Unit,
) {
    BackScaffoldTitle(title, onBack) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        ) {
            Text(body)
        }
    }
}
