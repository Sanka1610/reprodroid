package com.sanka1610.reprodroid.ui.appdetail

import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sanka1610.reprodroid.R
import com.sanka1610.reprodroid.data.local.AppGroupEntity
import com.sanka1610.reprodroid.data.local.AppMetadataUpdate
import com.sanka1610.reprodroid.data.local.AppTrackingState
import com.sanka1610.reprodroid.data.local.JobRecord
import com.sanka1610.reprodroid.data.local.ManagementMode
import com.sanka1610.reprodroid.data.local.RegisteredAppRecord
import com.sanka1610.reprodroid.data.local.ReleaseCandidateEntity
import com.sanka1610.reprodroid.data.local.ReleaseScheduleStateEntity
import com.sanka1610.reprodroid.data.local.UpdateStatus
import com.sanka1610.reprodroid.ui.*
import com.sanka1610.reprodroid.ui.shared.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppInformationScreen(
    record: RegisteredAppRecord,
    active: Boolean,
    refreshing: Boolean = false,
    runnerJobs: Map<String, JobRecord>,
    candidates: List<ReleaseCandidateEntity>,
    schedule: ReleaseScheduleStateEntity?,
    onBack: () -> Unit,
    onOpenCandidate: (ReleaseCandidateEntity) -> Unit,
    onTechnical: () -> Unit,
    onInstall: () -> Unit,
    onInstallNow: () -> Unit,
    onVerification: () -> Unit,
    onCheckRelease: () -> Unit,
    onComparison: (String) -> Unit,
    onResume: () -> Unit,
) {
    val asset = record.latestRelease?.selectedAsset
    val comparison = record.currentComparison
    val currentJob = comparison?.let { runnerJobs[it.repeatRunnerJobId ?: it.runnerJobId] }
    val candidate = candidates.latestInstallableCandidate(record.latestRelease)?.takeIf { candidateNeedsPreparation(it, record.latestRelease) }
    val statusSummary = when {
        candidate != null -> stringResource(R.string.app_status_uninspected)
        asset?.updateStatus == UpdateStatus.UPDATE_AVAILABLE.name -> stringResource(
            R.string.app_status_update_summary,
            asset.installedVersionName.orEmpty(),
            asset.versionName ?: stringResource(R.string.value_unknown),
        )
        asset?.updateStatus == UpdateStatus.NOT_INSTALLED.name -> stringResource(
            R.string.app_status_install_summary,
            asset.versionName ?: stringResource(R.string.value_unknown),
        )
        asset?.updateStatus == UpdateStatus.UP_TO_DATE.name -> stringResource(R.string.app_status_latest)
        asset?.updateStatus == UpdateStatus.OLDER_THAN_INSTALLED.name -> stringResource(R.string.state_older_than_installed)
        record.app.trackingState != AppTrackingState.ACTIVE.name -> stringResource(R.string.tracking_inactive)
        else -> stringResource(R.string.app_status_tracking)
    }
    val latestVersion = candidate?.tagName
        ?: asset?.versionName
        ?: record.latestRelease?.snapshot?.tagName
        ?: stringResource(R.string.value_unknown)
    val lastChecked = schedule?.lastAttemptAt ?: record.app.lastReleaseCheckedAt
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            expandedHeight = 56.dp,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ManagedAppIcon(record, 32.dp)
                    Text(
                        record.app.resolvedDisplayName,
                        modifier = Modifier.padding(start = 10.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            navigationIcon = { BackButton(onBack) },
        )
        if (active) LinearProgressIndicator(Modifier.fillMaxWidth())
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { if (!active && record.app.trackingState == AppTrackingState.ACTIVE.name) onCheckRelease() },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (record.app.authorDisplayOverride != null || record.group != null) item {
                    Text(
                        listOfNotNull(record.app.authorDisplayOverride, record.group?.displayName).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item {
                    CompactAppSection(stringResource(R.string.app_information_section)) {
                        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.label_tracking), style = MaterialTheme.typography.titleSmall)
                                Text(statusSummary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (record.app.trackingState == AppTrackingState.ACTIVE.name) {
                                OutlinedIconButton(enabled = !active, onClick = onCheckRelease) {
                                    Icon(Icons.Default.Refresh, stringResource(R.string.app_flow_check_release))
                                }
                            }
                        }
                        HorizontalDivider()
                        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.label_latest_version), style = MaterialTheme.typography.titleSmall)
                                Text(latestVersion, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (record.app.trackingState == AppTrackingState.ACTIVE.name) {
                                OutlinedIconButton(enabled = !active, onClick = {
                                    when {
                                        candidate != null -> onOpenCandidate(candidate)
                                        asset?.updateStatus in setOf(UpdateStatus.UPDATE_AVAILABLE.name, UpdateStatus.NOT_INSTALLED.name) -> onInstallNow()
                                        else -> onInstall()
                                    }
                                }) { InstallActionIcon(stringResource(R.string.app_acquisition_open)) }
                            }
                        }
                        HorizontalDivider()
                        CompactAppRow(
                            stringResource(R.string.label_verification),
                            trustLabel(record),
                        )
                        HorizontalDivider()
                        CompactAppRow(
                            stringResource(R.string.label_last_checked),
                            lastChecked?.let(::formatAppTimestamp) ?: stringResource(R.string.value_never),
                        )
                    }
                }
                item {
                    CompactAppSection(stringResource(R.string.app_details_section)) {
                        CompactAppRow(
                            stringResource(R.string.section_source),
                            record.app.displayName,
                            link = record.app.canonicalRepositoryUrl,
                        )
                        HorizontalDivider()
                        CompactAppRow(
                            stringResource(R.string.label_author),
                            record.displayAuthor(),
                            link = record.app.canonicalRepositoryUrl.trimEnd('/').substringBeforeLast('/'),
                        )
                        HorizontalDivider()
                        CompactAppRow(
                            stringResource(R.string.label_package),
                            asset?.packageName ?: stringResource(R.string.value_unknown),
                        )
                        HorizontalDivider()
                        CompactAppRow(
                            stringResource(R.string.release_check_next),
                            schedule?.nextEligibleAt?.let(::formatAppTimestamp)
                                ?: stringResource(R.string.value_not_available),
                        )
                        currentJob?.let {
                            HorizontalDivider()
                            CompactAppRow(
                                stringResource(R.string.section_build_summary),
                                "${statusLabel(it.job.state)} · ${it.job.progressPercent}%",
                            )
                        }
                        comparison?.let {
                            HorizontalDivider()
                            OutlinedButton(
                                onClick = { onComparison(it.comparisonRunId) },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(stringResource(R.string.action_open_comparison)) }
                        }
                    }
                }
                if (record.app.trackingState == AppTrackingState.ACTIVE.name) {
                    if (record.app.managementMode == ManagementMode.VERIFICATION.name) item {
                        OutlinedButton(onClick = onVerification, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.app_verification_open)) }
                    }
                    if (schedule?.waitingReason in setOf("RETRY_BACKOFF", "PROVIDER_COOLDOWN", "INVALID_STATE")) item {
                        Text(stringResource(if (schedule?.waitingReason == "PROVIDER_COOLDOWN") R.string.registration_complete_cooldown else R.string.error_operation_failed), style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (record.app.note.isNotBlank()) {
                    item { CompactAppSection(stringResource(R.string.label_note)) { Text(record.app.note) } }
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
}

@Composable
private fun CompactAppSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun CompactAppRow(label: String, value: String, link: String? = null) {
    val uriHandler = LocalUriHandler.current
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(0.42f))
        Text(
            value, style = MaterialTheme.typography.bodyMedium,
            color = if (link == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
            textDecoration = if (link != null) TextDecoration.Underline else null,
            modifier = Modifier.weight(0.58f).padding(start = 12.dp).then(if (link != null) Modifier.clickable { uriHandler.openUri(link) }.padding(vertical = 12.dp) else Modifier),
        )
    }
}

private fun formatAppTimestamp(value: String): String = runCatching {
    APP_TIMESTAMP_FORMAT.format(Instant.parse(value).atZone(ZoneId.systemDefault()))
}.getOrDefault(value)

private val APP_TIMESTAMP_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d HH:mm")

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
        TopAppBar(
            expandedHeight = 56.dp,
            title = { Text(stringResource(R.string.app_edit_title)) }, navigationIcon = { BackButton(onBack) })
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
                    trailingIcon = { InformationButton(stringResource(R.string.label_author_display), stringResource(R.string.label_author_not_verified)) },
                    singleLine = true,
                )
            }
            item {
                OutlinedTextField(
                    value = note,
                    onValueChange = { if (it.length <= MAX_NOTE_LENGTH) note = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.label_note)) },
                    trailingIcon = { InformationButton(stringResource(R.string.label_note), stringResource(R.string.label_note_support)) },
                    minLines = 3,
                    maxLines = 8,
                )
            }
            item {
                DropdownSetting(
                    label = stringResource(R.string.label_group), value = groupId,
                    options = linkedMapOf<String?, String>(null to stringResource(R.string.group_ungrouped)) + groups.associate { it.groupId to it.displayName },
                    onSelect = { groupId = it }, compact = true,
                )
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
            item {
                HorizontalDivider()
                Text(stringResource(R.string.section_source), Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleSmall)
            }
            item {
                OutlinedTextField(
                    value = sourceUrl,
                    onValueChange = { sourceUrl = it; onClearSource() },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.label_source_url)) },
                    trailingIcon = { InformationButton(stringResource(R.string.label_source_url), stringResource(R.string.source_same_identity_required)) },
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
                    CompactAppSection(stringResource(R.string.section_source)) {
                        TechnicalValue(stringResource(R.string.label_repository), result.normalizedInputUrl, true)
                        TechnicalValue(stringResource(R.string.label_branch), result.identity.defaultBranch)
                        TechnicalValue(
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
                        Text(stringResource(R.string.remove_registration_option), Modifier.weight(1f))
                        InformationButton(stringResource(R.string.remove_registration_option), stringResource(R.string.remove_registration_info))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = uninstallPackage,
                            enabled = canUninstall,
                            onCheckedChange = { uninstallPackage = it },
                        )
                        Text(stringResource(R.string.remove_uninstall_option), Modifier.weight(1f))
                        InformationButton(stringResource(R.string.remove_uninstall_option), stringResource(R.string.remove_uninstall_info))
                    }
                    if (!canUninstall) Text(stringResource(R.string.remove_package_unknown), style = MaterialTheme.typography.bodySmall)
                } else {
                    Text(record.app.resolvedDisplayName, style = MaterialTheme.typography.titleMedium)
                    if (uninstallPackage) Text(stringResource(R.string.remove_effect_uninstall))
                    if (removeRegistration) Text(stringResource(R.string.remove_effect_registration))
                    TechnicalValue(
                        stringResource(R.string.label_package),
                        packageName ?: stringResource(R.string.value_unknown),
                        true,
                    )
                    TechnicalValue(
                        stringResource(R.string.label_installed_version),
                        installedVersion ?: stringResource(R.string.value_not_available),
                    )
                    TechnicalValue(
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
