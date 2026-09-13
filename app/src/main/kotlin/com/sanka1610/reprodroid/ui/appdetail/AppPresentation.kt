package com.sanka1610.reprodroid.ui.appdetail

import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sanka1610.reprodroid.R
import com.sanka1610.reprodroid.data.local.AppGroupEntity
import com.sanka1610.reprodroid.data.local.AppMetadataUpdate
import com.sanka1610.reprodroid.data.local.AppTrackingState
import com.sanka1610.reprodroid.data.local.JobRecord
import com.sanka1610.reprodroid.data.local.RegisteredAppRecord
import com.sanka1610.reprodroid.data.local.ReleaseCandidateEntity
import com.sanka1610.reprodroid.data.local.ReleaseScheduleStateEntity
import com.sanka1610.reprodroid.ui.*
import com.sanka1610.reprodroid.ui.shared.*
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppInformationScreen(
    record: RegisteredAppRecord,
    active: Boolean,
    runnerJobs: Map<String, JobRecord>,
    candidates: List<ReleaseCandidateEntity>,
    schedule: ReleaseScheduleStateEntity?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onCheckMetadata: () -> Unit,
    onOpenCandidate: (ReleaseCandidateEntity) -> Unit,
    onTechnical: () -> Unit,
    onComparison: (String) -> Unit,
    onResume: () -> Unit,
) {
    val asset = record.latestRelease?.selectedAsset
    val comparison = record.currentComparison
    val currentJob = comparison?.let { runnerJobs[it.repeatRunnerJobId ?: it.runnerJobId] }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(record.app.resolvedDisplayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = { BackButton(onBack) },
            actions = {
                IconButton(
                    enabled = !active && record.app.trackingState == AppTrackingState.ACTIVE.name,
                    onClick = onRefresh,
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.action_refresh))
                }
            },
        )
        if (active) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ManagedAppIcon(record)
                    Column(Modifier.padding(start = 14.dp)) {
                        Text(record.app.resolvedDisplayName, style = MaterialTheme.typography.titleLarge)
                        record.app.authorDisplayOverride?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        record.group?.displayName?.let {
                            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item {
                UiRDetailCard(stringResource(R.string.section_current_state)) {
                    UiRDetailValue(
                        stringResource(R.string.label_tracking),
                        stringResource(
                            if (record.app.trackingState == AppTrackingState.ACTIVE.name) {
                                R.string.tracking_active
                            } else {
                                R.string.tracking_inactive
                            },
                        ),
                    )
                    UiRDetailValue(
                        stringResource(R.string.label_installed_version),
                        asset?.installedVersionName ?: stringResource(R.string.value_not_available),
                    )
                    UiRDetailValue(
                        stringResource(R.string.label_latest_version),
                        asset?.versionName ?: record.latestRelease?.snapshot?.tagName ?: stringResource(R.string.value_unknown),
                    )
                    UiRDetailValue(
                        stringResource(R.string.label_update),
                        statusLabel(asset?.updateStatus ?: "NOT_EVALUATED"),
                    )
                    UiRDetailValue(
                        stringResource(R.string.label_last_checked),
                        record.app.lastReleaseCheckedAt ?: stringResource(R.string.value_never),
                    )
                }
            }
            item {
                UiRDetailCard(stringResource(R.string.release_check_section)) {
                    UiRDetailValue(
                        stringResource(R.string.release_check_last_attempt),
                        schedule?.lastAttemptAt ?: stringResource(R.string.value_never),
                    )
                    UiRDetailValue(
                        stringResource(R.string.release_check_next),
                        schedule?.nextEligibleAt ?: stringResource(R.string.value_not_available),
                    )
                    UiRDetailValue(
                        stringResource(R.string.release_check_waiting),
                        statusLabel(schedule?.waitingReason),
                    )
                    Button(
                        enabled = !active && record.app.trackingState == AppTrackingState.ACTIVE.name,
                        onClick = onCheckMetadata,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.release_check_now)) }
                    candidates.take(5).forEach { candidate ->
                        Card(
                            Modifier.fillMaxWidth().clickable { onOpenCandidate(candidate) },
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(candidate.releaseName, fontWeight = FontWeight.SemiBold)
                                Text(candidate.tagName)
                                Text(statusLabel(candidate.state))
                                if (candidate.unseen) Text(stringResource(R.string.release_candidate_unseen))
                            }
                        }
                    }
                }
            }
            item {
                UiRDetailCard(stringResource(R.string.section_reproducibility)) {
                    UiRDetailValue(
                        stringResource(R.string.label_verification),
                        record.trustLevel?.name?.let { statusLabel(it) } ?: stringResource(R.string.value_unknown),
                    )
                    comparison?.let {
                        UiRDetailValue(stringResource(R.string.label_comparison_status), statusLabel(it.status))
                        OutlinedButton(
                            onClick = { onComparison(it.comparisonRunId) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.action_open_comparison)) }
                    }
                }
            }
            item {
                UiRDetailCard(stringResource(R.string.section_build_summary)) {
                    UiRDetailValue(
                        stringResource(R.string.label_build_configuration),
                        record.selectedBuildConfiguration?.let { statusLabel(it.validationState) }
                            ?: stringResource(R.string.value_not_available),
                    )
                    currentJob?.let {
                        UiRDetailValue(
                            stringResource(R.string.label_comparison_status),
                            "${statusLabel(it.job.state)} · ${it.job.progressPercent}%",
                        )
                    }
                }
            }
            item {
                UiRDetailCard(stringResource(R.string.section_source)) {
                    UiRDetailValue(stringResource(R.string.label_provider), record.app.provider)
                    UiRDetailValue(stringResource(R.string.label_repository_owner), repositoryOwner(record.app.canonicalRepositoryUrl))
                    UiRDetailValue(stringResource(R.string.label_repository), record.app.canonicalRepositoryUrl, true)
                    UiRDetailValue(
                        stringResource(R.string.label_branch),
                        record.latestSourceDiscovery?.requestedBranch ?: stringResource(R.string.value_unknown),
                    )
                    UiRDetailValue(
                        stringResource(R.string.label_package),
                        asset?.packageName ?: stringResource(R.string.value_unknown),
                        true,
                    )
                }
            }
            if (record.app.note.isNotBlank()) {
                item { UiRDetailCard(stringResource(R.string.label_note)) { Text(record.app.note) } }
            }
            item {
                OutlinedButton(
                    enabled = record.app.trackingState == AppTrackingState.ACTIVE.name,
                    onClick = onTechnical,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.action_open_details))
                }
            }
            if (record.app.trackingState == AppTrackingState.INACTIVE.name) {
                item {
                    Button(onClick = onResume, Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.action_resume_tracking))
                    }
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppEditScreen(
    record: RegisteredAppRecord,
    groups: List<AppGroupEntity>,
    sourcePreview: SourceEditPreviewState,
    saving: Boolean,
    onBack: () -> Unit,
    onSave: (AppMetadataUpdate) -> Unit,
    onInspectSource: (String) -> Unit,
    onApplySource: () -> Unit,
    onClearSource: () -> Unit,
    onRegisterSeparately: () -> Unit,
) {
    var displayName by rememberSaveable(record.app.registeredAppId, record.app.updatedAt) {
        mutableStateOf(record.app.displayNameOverride.orEmpty())
    }
    var author by rememberSaveable(record.app.registeredAppId, record.app.updatedAt) {
        mutableStateOf(record.app.authorDisplayOverride.orEmpty())
    }
    var note by rememberSaveable(record.app.registeredAppId, record.app.updatedAt) {
        mutableStateOf(record.app.note)
    }
    var groupId by rememberSaveable(record.app.registeredAppId, record.app.updatedAt) {
        mutableStateOf(record.app.groupId)
    }
    var sourceUrl by rememberSaveable(record.app.registeredAppId, record.app.updatedAt) {
        mutableStateOf(record.app.canonicalRepositoryUrl)
    }
    val sourceResult = sourcePreview.repository.takeIf { sourcePreview.registeredAppId == record.app.registeredAppId }
    val identityMatches = sourceResult?.identity?.providerRepositoryId == record.repositoryBinding?.providerRepositoryId
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(stringResource(R.string.app_edit_title)) }, navigationIcon = { BackButton(onBack) })
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { if (it.length <= MAX_DISPLAY_NAME_LENGTH) displayName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.label_display_name)) },
                    supportingText = { Text(stringResource(R.string.label_default_name, record.app.displayName)) },
                    singleLine = true,
                )
            }
            item {
                TextButton(onClick = { displayName = "" }) { Text(stringResource(R.string.action_use_default)) }
            }
            item {
                OutlinedTextField(
                    value = author,
                    onValueChange = { if (it.length <= MAX_AUTHOR_DISPLAY_LENGTH) author = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.label_author_display)) },
                    supportingText = { Text(stringResource(R.string.label_author_not_verified)) },
                    singleLine = true,
                )
            }
            item {
                OutlinedTextField(
                    value = note,
                    onValueChange = { if (it.length <= MAX_NOTE_LENGTH) note = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.label_note)) },
                    supportingText = { Text(stringResource(R.string.label_note_support)) },
                    minLines = 3,
                    maxLines = 8,
                )
            }
            item {
                UiRDetailCard(stringResource(R.string.label_group)) {
                    GroupChoice(null, stringResource(R.string.group_ungrouped), groupId) { groupId = null }
                    groups.forEach { group ->
                        GroupChoice(group.groupId, group.displayName, groupId) { groupId = group.groupId }
                    }
                }
            }
            item {
                Button(
                    enabled = !saving,
                    onClick = {
                        onSave(
                            AppMetadataUpdate(
                                displayNameOverride = displayName,
                                authorDisplayOverride = author,
                                note = note,
                                groupId = groupId,
                                expectedUpdatedAt = record.app.updatedAt,
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.action_save)) }
            }
            item { HorizontalDivider() }
            item {
                OutlinedTextField(
                    value = sourceUrl,
                    onValueChange = { sourceUrl = it; onClearSource() },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.label_source_url)) },
                    supportingText = { Text(stringResource(R.string.source_same_identity_required)) },
                    singleLine = true,
                )
            }
            item {
                OutlinedButton(
                    enabled = sourceUrl.isNotBlank() && !sourcePreview.isLoading,
                    onClick = { onInspectSource(sourceUrl) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.source_inspect)) }
            }
            if (sourcePreview.isLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            sourceResult?.let { result ->
                item {
                    UiRDetailCard(stringResource(R.string.section_source)) {
                        UiRDetailValue(stringResource(R.string.label_repository), result.normalizedInputUrl, true)
                        UiRDetailValue(stringResource(R.string.label_branch), result.identity.defaultBranch)
                        UiRDetailValue(
                            stringResource(R.string.label_commit),
                            result.discovery.resolvedCommitSha ?: stringResource(R.string.value_unknown),
                            true,
                        )
                        Text(
                            stringResource(
                                if (identityMatches) R.string.source_identity_match else R.string.source_identity_mismatch,
                            ),
                            color = if (identityMatches) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                        Button(
                            enabled = identityMatches && !saving,
                            onClick = onApplySource,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.source_apply)) }
                        if (!identityMatches) {
                            OutlinedButton(
                                onClick = onRegisterSeparately,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(stringResource(R.string.source_register_separately)) }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun GroupChoice(id: String?, label: String, selectedId: String?, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onSelect).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = id == selectedId, onClick = onSelect)
        Text(label, Modifier.padding(start = 8.dp))
    }
}


@Composable
internal fun RemoveTrackingDialog(
    record: RegisteredAppRecord,
    otherPackageReferenceCount: Int,
    onDismiss: () -> Unit,
    onStop: () -> Unit,
    onUninstall: (String, Boolean) -> Unit,
) {
    val context = LocalContext.current
    val packageName = knownPackageName(record)
        ?: context.packageName.takeIf { isSelfRegistration(record) }
    val installedVersion = record.latestRelease?.selectedAsset?.installedVersionName
        ?: packageName?.takeIf { it == context.packageName }?.let {
            runCatching {
                context.packageManager.getPackageInfo(it, 0).versionName
            }.getOrNull()
        }
    val canUninstall = remember(packageName) {
        packageName != null && isPackageInstalledForRemoval(context.packageManager, packageName)
    }
    var removeRegistration by rememberSaveable(record.app.registeredAppId, canUninstall) {
        mutableStateOf(!canUninstall)
    }
    var uninstallPackage by rememberSaveable(record.app.registeredAppId, canUninstall) {
        mutableStateOf(canUninstall)
    }
    var reviewing by rememberSaveable(record.app.registeredAppId) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (reviewing) R.string.remove_final_title else R.string.remove_title,
                ),
            )
        },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (!reviewing) {
                    Text(stringResource(R.string.remove_body))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = removeRegistration,
                            onCheckedChange = { removeRegistration = it },
                        )
                        Text(stringResource(R.string.remove_registration_option))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = uninstallPackage,
                            enabled = canUninstall,
                            onCheckedChange = { uninstallPackage = it },
                        )
                        Text(stringResource(R.string.remove_uninstall_option))
                    }
                    Text(
                        stringResource(
                            if (canUninstall) R.string.remove_independent_body else R.string.remove_package_unknown,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Text(record.app.resolvedDisplayName, style = MaterialTheme.typography.titleMedium)
                    if (uninstallPackage) Text(stringResource(R.string.remove_effect_uninstall))
                    if (removeRegistration) Text(stringResource(R.string.remove_effect_registration))
                    UiRDetailValue(
                        stringResource(R.string.label_package),
                        packageName ?: stringResource(R.string.value_unknown),
                        true,
                    )
                    UiRDetailValue(
                        stringResource(R.string.label_installed_version),
                        installedVersion ?: stringResource(R.string.value_not_available),
                    )
                    UiRDetailValue(
                        stringResource(R.string.remove_other_references),
                        otherPackageReferenceCount.toString(),
                    )
                    Text(stringResource(R.string.remove_history_preserved), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            if (reviewing) {
                TextButton(
                    onClick = {
                        if (uninstallPackage) {
                            packageName?.let { onUninstall(it, removeRegistration) }
                        } else {
                            onStop()
                        }
                    },
                ) { Text(stringResource(R.string.action_confirm)) }
            } else {
                TextButton(
                    enabled = removeRegistration || uninstallPackage,
                    onClick = { reviewing = true },
                ) { Text(stringResource(R.string.action_continue)) }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    if (reviewing) reviewing = false else onDismiss()
                },
            ) {
                Text(stringResource(if (reviewing) R.string.action_back else R.string.action_cancel))
            }
        },
    )
}

private fun isPackageInstalledForRemoval(packageManager: PackageManager, packageName: String): Boolean =
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0)
        }
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
