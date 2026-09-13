package com.sanka1610.reprodroid.ui.add

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
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sanka1610.reprodroid.R
import com.sanka1610.reprodroid.data.local.AppTrackingState
import com.sanka1610.reprodroid.data.local.InstallationSource
import com.sanka1610.reprodroid.data.local.ManagementMode
import com.sanka1610.reprodroid.ui.*
import com.sanka1610.reprodroid.ui.navigation.ReproDroidRoute
import com.sanka1610.reprodroid.ui.shared.*
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UiRAddFlowScreen(
    route: ReproDroidRoute,
    preview: RepositoryPreviewState,
    repositoryUrl: String,
    onRepositoryUrlChange: (String) -> Unit,
    mode: ManagementMode,
    onModeChange: (ManagementMode) -> Unit,
    installationSource: InstallationSource,
    onInstallationSourceChange: (InstallationSource) -> Unit,
    localRiskConfirmed: Boolean,
    onLocalRiskConfirmedChange: (Boolean) -> Unit,
    separateManagementTarget: Boolean,
    onSeparateManagementTargetChange: (Boolean) -> Unit,
    onPreview: (String) -> Unit,
    onCancelPreview: () -> Unit,
    onNavigate: (ReproDroidRoute) -> Unit,
    onResume: (String) -> Unit,
    onRegister: (ManagementMode, InstallationSource, Boolean, Boolean) -> Unit,
) {
    val resolved = preview.repository
    val existing = preview.existingPrimaryRegistration
    val canCreatePrimary = existing == null || separateManagementTarget
    val content: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            AddPhaseHeader(route)
            when (route) {
            ReproDroidRoute.AddSource -> {
                Text(stringResource(R.string.add_source_help), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = repositoryUrl,
                    onValueChange = onRepositoryUrlChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.add_repository_url)) },
                    supportingText = { Text(stringResource(R.string.add_supported_providers)) },
                    singleLine = true,
                    enabled = !preview.isLoading,
                )
                Button(
                    enabled = repositoryUrl.isNotBlank() && !preview.isLoading,
                    onClick = { onPreview(repositoryUrl) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.add_inspect_repository)) }
                if (preview.isLoading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(stringResource(R.string.add_inspecting), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onCancelPreview, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            }
            ReproDroidRoute.AddAnalysis -> {
                if (resolved == null) {
                    AddPreviewMissing { onNavigate(ReproDroidRoute.AddSource) }
                } else {
                    UiRDetailCard(stringResource(R.string.add_repository_verified)) {
                        UiRDetailValue(stringResource(R.string.label_repository), resolved.normalizedInputUrl, true)
                        UiRDetailValue(stringResource(R.string.add_repository_id), resolved.identity.providerRepositoryId, true)
                        UiRDetailValue(stringResource(R.string.label_branch), resolved.identity.defaultBranch)
                        UiRDetailValue(
                            stringResource(R.string.label_commit),
                            resolved.discovery.resolvedCommitSha ?: stringResource(R.string.value_not_available),
                            true,
                        )
                        UiRDetailValue(stringResource(R.string.add_discovery_state), resolved.discovery.state)
                        resolved.discovery.reason?.let { reason ->
                            UiRDetailValue(
                                stringResource(R.string.technical_discovery_reason),
                                listOfNotNull(reason, resolved.discovery.diagnostic).joinToString(": "),
                            )
                        }
                        UiRDetailValue(
                            stringResource(R.string.add_gradle_candidates),
                            resolved.discovery.candidates.size.toString(),
                        )
                        resolved.discovery.candidates.take(8).forEach { candidate ->
                            UiRDetailValue(candidate.fileKind, candidate.relativePath, true)
                        }
                        if (resolved.discovery.candidates.size > 8) {
                            val additionalCandidates = resolved.discovery.candidates.size - 8
                            Text(
                                pluralStringResource(
                                    R.plurals.add_more_candidates,
                                    additionalCandidates,
                                    additionalCandidates,
                                ),
                            )
                        }
                        Text(stringResource(R.string.add_known_facts), style = MaterialTheme.typography.bodySmall)
                    }
                    existing?.let {
                        ExistingRegistrationCard(it, onResume)
                    }
                    Button(
                        onClick = { onNavigate(ReproDroidRoute.AddOptions) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.action_continue)) }
                }
            }
            ReproDroidRoute.AddOptions -> {
                if (resolved == null) {
                    AddPreviewMissing { onNavigate(ReproDroidRoute.AddSource) }
                } else {
                    DropdownSetting(
                        label = stringResource(R.string.settings_management_mode),
                        value = mode,
                        options = linkedMapOf(
                            ManagementMode.VERIFICATION to stringResource(R.string.mode_verification),
                            ManagementMode.ACQUISITION to stringResource(R.string.mode_acquisition),
                        ),
                        onSelect = onModeChange,
                    )
                    DropdownSetting(
                        label = stringResource(R.string.settings_installation_source),
                        value = installationSource,
                        options = InstallationSource.entries
                            .filter { mode == ManagementMode.VERIFICATION || it == InstallationSource.OFFICIAL_RELEASE }
                            .associateWith {
                                stringResource(
                                    if (it == InstallationSource.OFFICIAL_RELEASE) {
                                        R.string.installation_official
                                    } else {
                                        R.string.installation_local
                                    },
                                )
                            },
                        onSelect = onInstallationSourceChange,
                        supportingText = stringResource(R.string.add_options_copy_note),
                    )
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Checkbox(
                            checked = separateManagementTarget,
                            onCheckedChange = onSeparateManagementTargetChange,
                        )
                        Text(stringResource(R.string.add_separate_target), Modifier.padding(top = 12.dp))
                    }
                    if (installationSource == InstallationSource.LOCAL_BUILD) {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Top) {
                                Checkbox(checked = localRiskConfirmed, onCheckedChange = onLocalRiskConfirmedChange)
                                Text(stringResource(R.string.add_local_build_risk), Modifier.padding(top = 12.dp))
                            }
                        }
                    }
                    Button(
                        enabled = installationSource != InstallationSource.LOCAL_BUILD || localRiskConfirmed,
                        onClick = { onNavigate(ReproDroidRoute.AddConfirm) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.action_continue)) }
                }
            }
            ReproDroidRoute.AddConfirm -> {
                if (resolved == null) {
                    AddPreviewMissing { onNavigate(ReproDroidRoute.AddSource) }
                } else {
                    UiRDetailCard(stringResource(R.string.add_confirm_title)) {
                        UiRDetailValue(stringResource(R.string.label_repository), resolved.normalizedInputUrl, true)
                        UiRDetailValue(stringResource(R.string.add_repository_id), resolved.identity.providerRepositoryId, true)
                        UiRDetailValue(stringResource(R.string.settings_management_mode), mode.name)
                        UiRDetailValue(stringResource(R.string.settings_installation_source), installationSource.name)
                        UiRDetailValue(
                            stringResource(R.string.add_management_slot),
                            stringResource(
                                if (separateManagementTarget) R.string.add_slot_separate else R.string.add_slot_primary,
                            ),
                        )
                        Text(stringResource(R.string.add_no_automatic_work), style = MaterialTheme.typography.bodySmall)
                    }
                    existing?.takeIf { !separateManagementTarget }?.let {
                        ExistingRegistrationCard(it, onResume)
                    }
                    Button(
                        enabled = canCreatePrimary && !preview.isLoading,
                        onClick = {
                            onRegister(mode, installationSource, localRiskConfirmed, separateManagementTarget)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(
                                if (preview.isLoading) R.string.add_registering else R.string.add_register,
                            ),
                        )
                    }
                    if (preview.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
                else -> Unit
            }
        }
    }
    if (route == ReproDroidRoute.AddSource) {
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { Spacer(Modifier.height(16.dp)) }
            item { content() }
            item { Spacer(Modifier.height(16.dp)) }
        }
    } else {
        BackScaffoldTitle(
            title = stringResource(R.string.add_title),
            onBack = {
                onNavigate(
                    when (route) {
                        ReproDroidRoute.AddAnalysis -> ReproDroidRoute.AddSource
                        ReproDroidRoute.AddOptions -> ReproDroidRoute.AddAnalysis
                        ReproDroidRoute.AddConfirm -> ReproDroidRoute.AddOptions
                        else -> ReproDroidRoute.AddSource
                    },
                )
            },
        ) {
            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item { Spacer(Modifier.height(8.dp)) }
                item { content() }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun AddPhaseHeader(route: ReproDroidRoute) {
    val phase = when (route) {
        ReproDroidRoute.AddSource -> R.string.add_source_phase
        ReproDroidRoute.AddAnalysis -> R.string.add_analysis_phase
        ReproDroidRoute.AddOptions -> R.string.add_options_phase
        ReproDroidRoute.AddConfirm -> R.string.add_confirm_phase
        else -> R.string.add_source_phase
    }
    Text(stringResource(phase), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun AddPreviewMissing(onReturn: () -> Unit) {
    EmptyState(
        title = stringResource(R.string.add_preview_missing_title),
        body = stringResource(R.string.add_preview_missing_body),
        actionLabel = stringResource(R.string.action_back),
        onAction = onReturn,
    )
}

@Composable
private fun ExistingRegistrationCard(
    existing: com.sanka1610.reprodroid.data.repository.ExistingPrimaryRegistration,
    onResume: (String) -> Unit,
) {
    UiRDetailCard(stringResource(R.string.add_existing_title)) {
        Text(existing.displayName)
        Text(
            stringResource(
                if (existing.trackingState == AppTrackingState.INACTIVE.name) {
                    R.string.add_existing_inactive
                } else {
                    R.string.add_existing_active
                },
            ),
        )
        if (existing.trackingState == AppTrackingState.INACTIVE.name) {
            Button(onClick = { onResume(existing.registeredAppId) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_resume_tracking))
            }
        }
    }
}
