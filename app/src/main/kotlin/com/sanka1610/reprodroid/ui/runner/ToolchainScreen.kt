package com.sanka1610.reprodroid.ui.runner

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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sanka1610.reprodroid.R
import com.sanka1610.reprodroid.data.toolchain.ToolchainUiState


import com.sanka1610.reprodroid.ui.*
import com.sanka1610.reprodroid.ui.shared.*
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ToolchainScreen(
    state: ToolchainUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onInstall: (Set<String>) -> Unit,
    onCancel: () -> Unit,
    onPreviewRemoval: (Set<String>) -> Unit,
    onExecuteRemoval: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val toolchainBackDescription = stringResource(R.string.action_back)
    val toolchainRefreshDescription = stringResource(R.string.action_refresh)
    var acceptedLicenses by remember(state.plan?.planSha256) { mutableStateOf(emptySet<String>()) }
    var selectedArtifacts by remember(state.inventory?.items?.map { it.artifactId }) { mutableStateOf(emptySet<String>()) }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.settings_toolchains)) },
            navigationIcon = {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.semantics { contentDescription = toolchainBackDescription },
                ) { NavigationGlyph("‹") }
            },
            actions = {
                IconButton(
                    enabled = !state.busy,
                    onClick = onRefresh,
                    modifier = Modifier.semantics { contentDescription = toolchainRefreshDescription },
                ) { NavigationGlyph("↻") }
            },
        )
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                SectionTitle(
                    stringResource(R.string.toolchains_catalog_plan),
                    stringResource(R.string.toolchains_catalog_plan_body),
                )
            }
            val plan = state.plan
            if (plan == null) {
                item { Text(stringResource(R.string.toolchains_no_plan)) }
            } else {
                items(plan.items, key = { "plan-${it.artifactId}" }) { item ->
                    DetailCard(item.component.name.displayEnum()) {
                        DetailValue(stringResource(R.string.technical_version), item.version)
                        DetailValue(stringResource(R.string.toolchains_download), formatBytes(item.downloadBytes.toLong()))
                        DetailValue(stringResource(R.string.toolchains_reservation), formatBytes(item.reservedBytes.toLong()))
                        DetailValue(
                            stringResource(R.string.technical_status),
                            stringResource(
                                if (item.alreadyInstalled) {
                                    R.string.toolchains_status_installed
                                } else {
                                    R.string.toolchains_status_required
                                },
                            ),
                        )
                    }
                }
                items(plan.requiredLicenses, key = { "license-${it.licenseId}" }) { license ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Top) {
                            Checkbox(
                                checked = license.licenseId in acceptedLicenses,
                                onCheckedChange = { checked ->
                                    acceptedLicenses = if (checked) acceptedLicenses + license.licenseId else acceptedLicenses - license.licenseId
                                },
                            )
                            Column(Modifier.weight(1f)) {
                                Text(license.displayName, fontWeight = FontWeight.SemiBold)
                                Text(license.text, style = MaterialTheme.typography.bodySmall)
                                Text(stringResource(R.string.toolchains_terms_source, license.sourceUrl), style = MaterialTheme.typography.labelSmall)
                                Text(stringResource(R.string.toolchains_consent_digest, license.textSha256), style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
                item {
                    Button(
                        enabled = !state.busy && acceptedLicenses == plan.requiredLicenses.map { it.licenseId }.toSet(),
                        onClick = { onInstall(acceptedLicenses) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.toolchains_install_plan)) }
                }
            }
            state.installation?.let { installation ->
                item {
                    DetailCard(stringResource(R.string.toolchains_installation)) {
                        DetailValue(stringResource(R.string.storage_state), installation.state.name.displayEnum())
                        DetailValue(stringResource(R.string.toolchains_progress), "${installation.progressPercent}%")
                        installation.reason?.let { DetailValue(stringResource(R.string.storage_reason), "${it.code}: ${it.message}") }
                        if (installation.state.name !in setOf("INSTALLED", "CANCELLED", "FAILED", "RECONCILIATION_REQUIRED")) {
                            TextButton(onClick = onCancel) { Text(stringResource(R.string.toolchains_cancel)) }
                        }
                    }
                }
            }
            item {
                HorizontalDivider()
                SectionTitle(
                    stringResource(R.string.toolchains_runner_inventory),
                    stringResource(R.string.toolchains_runner_inventory_body),
                )
            }
            val inventory = state.inventory
            if (inventory == null || inventory.items.isEmpty()) {
                item { Text(stringResource(R.string.toolchains_empty)) }
            } else {
                items(inventory.items, key = { "inventory-${it.artifactId}" }) { item ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = item.artifactId in selectedArtifacts,
                                enabled = item.state == "VERIFIED" && !state.busy,
                                onCheckedChange = { checked -> selectedArtifacts = if (checked) selectedArtifacts + item.artifactId else selectedArtifacts - item.artifactId },
                            )
                            Column(Modifier.weight(1f)) {
                                Text("${item.component.name.displayEnum()} ${item.version}", fontWeight = FontWeight.SemiBold)
                                Text("${item.state} · ${formatBytes(item.installedBytes.toLong())}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                item {
                    Button(enabled = selectedArtifacts.isNotEmpty() && !state.busy, onClick = { onPreviewRemoval(selectedArtifacts) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.toolchains_preview_selected))
                    }
                }
                state.removalPreview?.let { preview ->
                    item {
                        DetailCard(stringResource(R.string.toolchains_removal_confirmation)) {
                            DetailValue(stringResource(R.string.toolchains_selected_entries), preview.artifactIds.size.toString())
                            DetailValue(stringResource(R.string.toolchains_releasable), formatBytes(preview.releasableBytes.toLong()))
                            DetailValue(stringResource(R.string.toolchains_preview_expires), preview.expiresAt)
                            Text(stringResource(R.string.toolchains_removal_body))
                            Button(enabled = !state.busy, onClick = onExecuteRemoval, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.toolchains_confirm_removal))
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
