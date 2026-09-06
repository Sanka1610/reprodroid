package com.sanka1610.reprodroid.ui

import android.graphics.BitmapFactory
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.net.toUri
import com.sanka1610.reprodroid.R
import com.sanka1610.reprodroid.data.local.ManagementMode
import com.sanka1610.reprodroid.data.local.AppSettingsUpdate
import com.sanka1610.reprodroid.data.local.ComparisonEligibility
import com.sanka1610.reprodroid.data.local.ComparisonRunStatus
import com.sanka1610.reprodroid.data.local.GlobalSettingsEntity
import com.sanka1610.reprodroid.data.local.InstallationSource
import com.sanka1610.reprodroid.data.local.PreferredAbi
import com.sanka1610.reprodroid.data.local.ReferenceDownloadStatus
import com.sanka1610.reprodroid.data.local.ReleaseDiscoveryStatus
import com.sanka1610.reprodroid.data.local.ReleaseAssetEntity
import com.sanka1610.reprodroid.data.local.RegisteredAppRecord
import com.sanka1610.reprodroid.data.local.ReleaseVariantPreference
import com.sanka1610.reprodroid.data.local.ThemeMode
import com.sanka1610.reprodroid.data.local.TrustLevel
import com.sanka1610.reprodroid.data.local.UpdateStatus
import com.sanka1610.reprodroid.data.local.ResourceAvailabilityEntity
import com.sanka1610.reprodroid.data.local.BuildEnvironmentManifestWithDependencies
import com.sanka1610.reprodroid.data.local.JobRecord
import com.sanka1610.reprodroid.data.local.SourceScanWithDetails
import com.sanka1610.reprodroid.data.repository.BuildManifestWarning
import com.sanka1610.reprodroid.data.repository.BuildConfigurationInput
import com.sanka1610.reprodroid.data.repository.BuildConfigurationValidator
import com.sanka1610.reprodroid.data.repository.SourceScanWarning
import com.sanka1610.reprodroid.data.repository.DependencyDifferenceKind
import com.sanka1610.reprodroid.data.repository.compareBuildEnvironments
import com.sanka1610.reprodroid.data.repository.sandboxSelectionText
import com.sanka1610.reprodroid.data.repository.sandboxManifestText
import com.sanka1610.reprodroid.data.repository.sandboxAcknowledgementAllowed
import com.sanka1610.reprodroid.data.storage.AndroidCleanupPreview
import com.sanka1610.reprodroid.data.storage.AndroidStorageSummary
import com.sanka1610.reprodroid.data.storage.RunnerStorageConnectionState
import com.sanka1610.reprodroid.data.storage.StagedAuditExport
import com.sanka1610.reprodroid.data.network.V2CleanupPreviewResponse
import com.sanka1610.reprodroid.data.network.V2CleanupRunResponse
import com.sanka1610.reprodroid.data.toolchain.ToolchainUiState
import androidx.compose.ui.platform.LocalContext
import java.io.File
import java.util.UUID

private enum class MainDestination { APPS, ADD, SETTINGS }

private val DarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFFB69CFF),
    onPrimary = Color(0xFF24124D),
    primaryContainer = Color(0xFF352762),
    secondary = Color(0xFFCEC1FF),
    background = Color(0xFF171622),
    surface = Color(0xFF1D1B29),
    surfaceVariant = Color(0xFF2A2739),
    outline = Color(0xFF8F899E),
    error = Color(0xFFFFB4AB),
)

private val LightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF6042A6),
    primaryContainer = Color(0xFFE9DDFF),
    secondary = Color(0xFF625B71),
    surfaceVariant = Color(0xFFE8E0EC),
)

@Composable
internal fun LegacyReproDroidApp(managedViewModel: ManagedAppsViewModel, jobViewModel: JobViewModel) {
    val apps by managedViewModel.apps.collectAsStateWithLifecycle()
    val globalSettings by managedViewModel.settings.collectAsStateWithLifecycle()
    val preview by managedViewModel.preview.collectAsStateWithLifecycle()
    val message by managedViewModel.message.collectAsStateWithLifecycle()
    val activeAppIds by managedViewModel.activeAppIds.collectAsStateWithLifecycle()
    val buildEnvironmentManifests by managedViewModel.buildEnvironmentManifests.collectAsStateWithLifecycle()
    val runnerJobs by managedViewModel.runnerJobs.collectAsStateWithLifecycle()
    val buildManifestWarnings by managedViewModel.buildManifestWarnings.collectAsStateWithLifecycle()
    val sourceScanWarnings by managedViewModel.sourceScanWarnings.collectAsStateWithLifecycle()
    val sandboxWarnings by managedViewModel.sandboxWarnings.collectAsStateWithLifecycle()
    val availability by managedViewModel.availability.collectAsStateWithLifecycle()
    val androidStorageSummary by managedViewModel.androidStorageSummary.collectAsStateWithLifecycle()
    val androidCleanupPreview by managedViewModel.androidCleanupPreview.collectAsStateWithLifecycle()
    val runnerStorageState by managedViewModel.runnerStorageState.collectAsStateWithLifecycle()
    val storageBusy by managedViewModel.storageBusy.collectAsStateWithLifecycle()
    val auditExport by managedViewModel.auditExport.collectAsStateWithLifecycle()
    val runnerCleanupPreview by managedViewModel.runnerCleanupPreview.collectAsStateWithLifecycle()
    val runnerCleanupRun by managedViewModel.runnerCleanupRun.collectAsStateWithLifecycle()
    val toolchainState by managedViewModel.toolchainState.collectAsStateWithLifecycle()
    var destination by rememberSaveable { mutableStateOf(MainDestination.APPS) }
    var selectedAppId by rememberSaveable { mutableStateOf<String?>(null) }
    var settingsAppId by rememberSaveable { mutableStateOf<String?>(null) }
    var showRunnerJobs by rememberSaveable { mutableStateOf(false) }
    var showStorage by rememberSaveable { mutableStateOf(false) }
    var showToolchains by rememberSaveable { mutableStateOf(false) }
    val useDark = when (enumValue(globalSettings.themeMode, ThemeMode.DARK)) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    MaterialTheme(colorScheme = if (useDark) DarkColors else LightColors) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                bottomBar = {
                    NavigationBar {
                        NavigationBarItem(
                            selected = destination == MainDestination.APPS,
                            onClick = {
                                destination = MainDestination.APPS
                                selectedAppId = null
                                settingsAppId = null
                                showRunnerJobs = false
                                showStorage = false
                                showToolchains = false
                            },
                            icon = { NavigationGlyph("▦") },
                            label = { Text("Apps") },
                        )
                        NavigationBarItem(
                            selected = destination == MainDestination.ADD,
                            onClick = {
                                destination = MainDestination.ADD
                                selectedAppId = null
                                settingsAppId = null
                                showRunnerJobs = false
                                showStorage = false
                                showToolchains = false
                            },
                            icon = { NavigationGlyph("＋") },
                            label = { Text("Add") },
                        )
                        NavigationBarItem(
                            selected = destination == MainDestination.SETTINGS,
                            onClick = {
                                destination = MainDestination.SETTINGS
                                selectedAppId = null
                                settingsAppId = null
                                showRunnerJobs = false
                                showStorage = false
                                showToolchains = false
                            },
                            icon = { NavigationGlyph("⚙") },
                            label = { Text("Settings") },
                        )
                    }
                },
            ) { contentPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding),
                ) {
                    message?.let { current ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(current, modifier = Modifier.weight(1f))
                                TextButton(onClick = managedViewModel::clearMessage) { Text("Dismiss") }
                            }
                        }
                    }
                    when {
                        destination == MainDestination.APPS && settingsAppId != null -> {
                            val app = apps.firstOrNull { it.app.registeredAppId == settingsAppId }
                            if (app != null) {
                                AppPreferencesScreen(
                                    record = app,
                                    globalSettings = globalSettings,
                                    saving = app.app.registeredAppId in activeAppIds,
                                    onBack = { settingsAppId = null },
                                    onSave = { update ->
                                        managedViewModel.updatePreferences(
                                            app.app.registeredAppId,
                                            update,
                                        ) { settingsAppId = null }
                                    },
                                    onSaveBuildConfiguration = { expectedRevision, input ->
                                        managedViewModel.saveBuildConfiguration(
                                            app.app.registeredAppId,
                                            expectedRevision,
                                            input,
                                        )
                                    },
                                )
                            }
                        }
                        destination == MainDestination.APPS && selectedAppId != null -> {
                            val app = apps.firstOrNull { it.app.registeredAppId == selectedAppId }
                            if (app == null) {
                                selectedAppId = null
                            } else {
                                LaunchedEffect(app.app.registeredAppId) {
                                    managedViewModel.refreshInstalledState(app.app.registeredAppId)
                                }
                                AppDetailScreen(
                                    record = app,
                                    globalSettings = globalSettings,
                                    active = app.app.registeredAppId in activeAppIds,
                                    onBack = { selectedAppId = null },
                                    onSettings = { settingsAppId = app.app.registeredAppId },
                                    onRefresh = { managedViewModel.refresh(app.app.registeredAppId) },
                                    onSelectReleaseAsset = { releaseSnapshotId, providerAssetId ->
                                        managedViewModel.selectReleaseAsset(
                                            app.app.registeredAppId,
                                            releaseSnapshotId,
                                            providerAssetId,
                                        )
                                    },
                                    onInstall = { confirmed ->
                                        managedViewModel.install(app.app.registeredAppId, confirmed)
                                    },
                                    onStartComparison = {
                                        managedViewModel.startComparison(app.app.registeredAppId)
                                    },
                                    onRefreshComparison = { comparisonRunId ->
                                        managedViewModel.refreshComparison(app.app.registeredAppId, comparisonRunId)
                                    },
                                    onConfirmComparison = { comparisonRunId ->
                                        managedViewModel.confirmComparison(app.app.registeredAppId, comparisonRunId)
                                    },
                                    onContinueComparisonSourceScan = { comparisonRunId ->
                                        managedViewModel.continueComparisonSourceScan(
                                            app.app.registeredAppId,
                                            comparisonRunId,
                                        )
                                    },
                                    runnerJobs = runnerJobs.associateBy { it.job.jobId },
                                    buildEnvironmentManifests = buildEnvironmentManifests.associateBy { it.manifest.jobId },
                                    buildManifestWarnings = buildManifestWarnings,
                                    sourceScanWarnings = sourceScanWarnings,
                                    sandboxWarnings = sandboxWarnings,
                                    availability = availability,
                                )
                            }
                        }
                        destination == MainDestination.APPS -> AppsScreen(
                            apps = apps,
                            onSelect = { selectedAppId = it },
                        )
                        destination == MainDestination.ADD -> AddAppScreen(
                            preview = preview,
                            globalSettings = globalSettings,
                            onPreview = managedViewModel::preview,
                            onRegister = { mode, source, confirmed, separateTarget ->
                                managedViewModel.register(mode, source, confirmed, separateTarget) { registeredAppId ->
                                    destination = MainDestination.APPS
                                    selectedAppId = registeredAppId
                                }
                            },
                            onUrlChanged = managedViewModel::clearPreview,
                        )
                        destination == MainDestination.SETTINGS && showRunnerJobs -> {
                            BackHandler { showRunnerJobs = false }
                            Column(modifier = Modifier.fillMaxSize()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { showRunnerJobs = false }) {
                                        NavigationGlyph("‹")
                                    }
                                    Text("Runner jobs", style = MaterialTheme.typography.titleLarge)
                                }
                                JobScreen(jobViewModel)
                            }
                        }
                        destination == MainDestination.SETTINGS && showStorage -> {
                            StorageScreen(
                                apps = apps,
                                settings = globalSettings,
                                androidSummary = androidStorageSummary,
                                runnerState = runnerStorageState,
                                cleanupPreview = androidCleanupPreview,
                                busy = storageBusy,
                                auditExport = auditExport,
                                runnerCleanupPreview = runnerCleanupPreview,
                                runnerCleanupRun = runnerCleanupRun,
                                onBack = {
                                    showStorage = false
                                    managedViewModel.clearAndroidCleanupPreview()
                                },
                                onUpdate = managedViewModel::updateGlobalSettings,
                                onRefresh = managedViewModel::refreshStorage,
                                onPreviewCleanup = managedViewModel::previewAndroidCleanup,
                                onExecuteCleanup = managedViewModel::executeAndroidCleanup,
                                onStageAudit = managedViewModel::stageAuditExport,
                                onCopyAudit = managedViewModel::copyAuditExport,
                                onPreviewRunnerCleanup = managedViewModel::previewRunnerCleanup,
                                onExecuteRunnerCleanup = managedViewModel::executeRunnerCleanup,
                            )
                        }
                        destination == MainDestination.SETTINGS && showToolchains -> {
                            ToolchainScreen(
                                state = toolchainState,
                                onBack = { showToolchains = false },
                                onRefresh = managedViewModel::refreshToolchains,
                                onInstall = managedViewModel::installToolchains,
                                onCancel = managedViewModel::cancelToolchainInstallation,
                                onPreviewRemoval = managedViewModel::previewToolchainRemoval,
                                onExecuteRemoval = managedViewModel::executeToolchainRemoval,
                            )
                        }
                        else -> SettingsScreen(
                            settings = globalSettings,
                            onUpdate = managedViewModel::updateGlobalSettings,
                            onOpenRunnerJobs = { showRunnerJobs = true },
                            onOpenStorage = { showStorage = true; managedViewModel.refreshStorage() },
                            onOpenToolchains = { showToolchains = true; managedViewModel.refreshToolchains() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppsScreen(
    apps: List<RegisteredAppRecord>,
    onSelect: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(apps, query) {
        apps.filter { it.app.displayName.contains(query, true) || it.app.repositoryUrl.contains(query, true) }
    }
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(16.dp))
        Text("Registered apps", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { NavigationGlyph("⌕") },
            placeholder = { Text("Search apps or repositories") },
            singleLine = true,
        )
        Spacer(Modifier.height(16.dp))
        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (apps.isEmpty()) "No registered repositories." else "No matching apps.")
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(filtered, key = { it.app.registeredAppId }) { record ->
                    val latest = record.latestRelease
                    val asset = latest?.selectedAsset
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(record.app.registeredAppId) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ManagedAppIcon(record)
                            Column(
                                modifier = Modifier.weight(1f).padding(start = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        record.app.displayName,
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(asset?.versionName ?: latest?.snapshot?.tagName ?: "—")
                                    Text(
                                        updateLabel(asset?.updateStatus),
                                        color = updateColor(asset?.updateStatus),
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                    AssistChip(onClick = {}, label = { Text(modeLabel(record.app.managementMode)) })
                                    if (record.app.managementMode == ManagementMode.VERIFICATION.name) {
                                        AssistChip(onClick = {}, label = { Text(trustLabel(record)) })
                                    } else {
                                        AssistChip(
                                            onClick = {},
                                            label = { Text(signerLabel(asset?.existingInstallStatus)) },
                                        )
                                    }
                                    if (record.app.installationSource == InstallationSource.LOCAL_BUILD.name) {
                                        AssistChip(onClick = {}, label = { Text("Local build") })
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
}

@Composable
internal fun AddAppScreen(
    preview: RepositoryPreviewState,
    globalSettings: GlobalSettingsEntity,
    onPreview: (String) -> Unit,
    onRegister: (ManagementMode, InstallationSource, Boolean, Boolean) -> Unit,
    onUrlChanged: () -> Unit,
) {
    var repositoryUrl by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable(globalSettings.defaultManagementMode) {
        mutableStateOf(enumValue(globalSettings.defaultManagementMode, ManagementMode.VERIFICATION))
    }
    var installationSource by rememberSaveable(globalSettings.defaultInstallationSource) {
        mutableStateOf(
            enumValue(globalSettings.defaultInstallationSource, InstallationSource.OFFICIAL_RELEASE),
        )
    }
    var localRiskConfirmed by rememberSaveable { mutableStateOf(false) }
    var separateManagementTarget by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Spacer(Modifier.height(16.dp))
            Text("Register a repository", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text("Phase 4.1 · public GitHub source metadata", style = MaterialTheme.typography.bodyMedium)
        }
        item {
            OutlinedTextField(
                value = repositoryUrl,
                onValueChange = { repositoryUrl = it; onUrlChanged() },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("GitHub repository URL") },
                supportingText = { Text("Only https://github.com/{owner}/{repository}") },
                singleLine = true,
                enabled = !preview.isLoading,
            )
        }
        item {
            Row(verticalAlignment = Alignment.Top) {
                Checkbox(
                    checked = separateManagementTarget,
                    onCheckedChange = { separateManagementTarget = it },
                )
                Text(
                    "Register as a separate management target for another package from the same repository. " +
                        "Leave off for the primary target.",
                    Modifier.padding(top = 10.dp),
                )
            }
        }
        item {
            DropdownSetting(
                label = "Management mode",
                value = mode,
                options = ManagementMode.entries.associateWith(::modeLabel),
                onSelect = {
                    mode = it
                    if (it == ManagementMode.ACQUISITION) {
                        installationSource = InstallationSource.OFFICIAL_RELEASE
                        localRiskConfirmed = false
                    }
                },
            )
        }
        item {
            DropdownSetting(
                label = "Installation source",
                value = installationSource,
                options = InstallationSource.entries
                    .filter { mode == ManagementMode.VERIFICATION || it == InstallationSource.OFFICIAL_RELEASE }
                    .associateWith(::installationSourceLabel),
                onSelect = {
                    installationSource = it
                    if (it != InstallationSource.LOCAL_BUILD) localRiskConfirmed = false
                },
                supportingText = "Copied at registration; existing apps do not follow later global changes.",
            )
        }
        if (installationSource == InstallationSource.LOCAL_BUILD) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Checkbox(checked = localRiskConfirmed, onCheckedChange = { localRiskConfirmed = it })
                        Text(
                            "I understand that a local build may use a different signer, cannot replace an " +
                                "installed official app, and is installable only when the compared artifact is signed.",
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                }
            }
        }
        item {
            Button(
                enabled = repositoryUrl.isNotBlank() && !preview.isLoading,
                onClick = { onPreview(repositoryUrl) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (preview.isLoading && preview.repository == null) "Inspecting…" else "Inspect repository") }
        }
        if (preview.isLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        preview.repository?.let { resolved ->
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(resolved.identity.displayName, style = MaterialTheme.typography.titleLarge)
                        DetailValue("Repository ID", resolved.identity.providerRepositoryId, monospace = true)
                        DetailValue("Default branch", resolved.identity.defaultBranch)
                        DetailValue("Discovery", resolved.discovery.state)
                        resolved.discovery.reason?.let { DetailValue("Reason", it) }
                        DetailValue("Resolved commit", resolved.discovery.resolvedCommitSha ?: "Not resolved", true)
                        DetailValue("Root tree", resolved.discovery.rootTreeSha ?: "Not resolved", true)
                        DetailValue("Gradle candidates", resolved.discovery.candidates.size.toString())
                        resolved.discovery.candidates.take(8).forEach { candidate ->
                            DetailValue(candidate.fileKind, candidate.relativePath, monospace = true)
                        }
                        if (resolved.discovery.candidates.size > 8) {
                            Text("${resolved.discovery.candidates.size - 8} more candidates")
                        }
                        DetailValue(
                            "Excluded",
                            "symlink ${resolved.discovery.excludedSymlinkCount}, " +
                                "submodule ${resolved.discovery.excludedSubmoduleCount}, " +
                                "cache tree ${resolved.discovery.excludedCacheTreeCount}",
                        )
                        Text(
                            "Registration stores source metadata only. It does not download an APK, start a Runner job, " +
                                "execute Gradle, compare artifacts, or install a package.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Button(
                            enabled = !preview.isLoading &&
                                (installationSource != InstallationSource.LOCAL_BUILD || localRiskConfirmed),
                            onClick = {
                                onRegister(
                                    mode,
                                    installationSource,
                                    localRiskConfirmed,
                                    separateManagementTarget,
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (preview.isLoading) "Registering…" else "Register repository") }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppDetailScreen(
    record: RegisteredAppRecord,
    globalSettings: GlobalSettingsEntity,
    active: Boolean,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onRefresh: () -> Unit,
    onSelectReleaseAsset: (String, Long) -> Unit,
    onInstall: (Boolean) -> Unit,
    onStartComparison: () -> Unit,
    onRefreshComparison: (String) -> Unit,
    onConfirmComparison: (String) -> Unit,
    onContinueComparisonSourceScan: (String) -> Unit,
    runnerJobs: Map<String, JobRecord>,
    buildEnvironmentManifests: Map<String, BuildEnvironmentManifestWithDependencies>,
    buildManifestWarnings: Map<String, BuildManifestWarning>,
    sourceScanWarnings: Map<String, SourceScanWarning>,
    sandboxWarnings: Map<String, String>,
    availability: List<ResourceAvailabilityEntity>,
) {
    BackHandler(onBack = onBack)
    val backContentDescription = stringResource(R.string.action_back)
    val refreshContentDescription = stringResource(R.string.action_refresh)
    val settingsContentDescription = stringResource(R.string.nav_settings)
    val latest = record.latestRelease
    val asset = latest?.selectedAsset
    var selectedReleaseAssetId by rememberSaveable(
        record.app.registeredAppId,
        latest?.snapshot?.releaseSnapshotId,
    ) { mutableStateOf<Long?>(null) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var installRiskConfirmed by rememberSaveable(record.app.registeredAppId) { mutableStateOf(false) }
    val currentReviewJob = record.currentComparison?.let { comparison ->
        runnerJobs[comparison.repeatRunnerJobId ?: comparison.runnerJobId]
    }
    var sourceScanRiskConfirmed by rememberSaveable(
        record.currentComparison?.comparisonRunId,
        record.currentComparison?.status,
        currentReviewJob?.job?.jobId,
        currentReviewJob?.sourceScan?.scan?.resultSha256,
    ) { mutableStateOf(false) }
    var canRequestPackageInstalls by remember {
        mutableStateOf(context.packageManager.canRequestPackageInstalls())
    }
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canRequestPackageInstalls = context.packageManager.canRequestPackageInstalls()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val canInstall = asset?.updateStatus in setOf(
        UpdateStatus.NOT_INSTALLED.name,
        UpdateStatus.UPDATE_AVAILABLE.name,
    )
    val warningRequired = (
        record.app.managementMode == ManagementMode.VERIFICATION.name &&
            record.trustLevel != TrustLevel.REPRODUCIBLE
        ) ||
        asset?.existingInstallStatus == "SIGNER_MISMATCH"
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(record.app.resolvedDisplayName) },
            navigationIcon = {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.semantics { contentDescription = backContentDescription },
                ) { NavigationGlyph("‹") }
            },
            actions = {
                IconButton(
                    enabled = !active,
                    onClick = onRefresh,
                    modifier = Modifier.semantics { contentDescription = refreshContentDescription },
                ) {
                    NavigationGlyph("↻")
                }
                IconButton(
                    onClick = onSettings,
                    modifier = Modifier.semantics { contentDescription = settingsContentDescription },
                ) { NavigationGlyph("⚙") }
            },
        )
        if (active) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = {}, label = { Text(modeLabel(record.app.managementMode)) })
                    if (record.app.managementMode == ManagementMode.VERIFICATION.name) {
                        AssistChip(onClick = {}, label = { Text(trustLabel(record)) })
                    }
                    AssistChip(
                        onClick = {},
                        label = { Text(installationSourceLabel(record.app.installationSource)) },
                    )
                }
            }
            item {
                DetailCard(stringResource(R.string.technical_repository)) {
                    DetailValue(stringResource(R.string.label_provider), record.app.provider)
                    DetailValue(stringResource(R.string.label_source_url), record.app.canonicalRepositoryUrl)
                    DetailValue(stringResource(R.string.technical_repository_id), record.repositoryBinding?.providerRepositoryId ?: stringResource(R.string.value_unknown), true)
                    DetailValue(stringResource(R.string.technical_identity), record.repositoryBinding?.identityStatus ?: stringResource(R.string.value_not_available))
                    record.latestSourceDiscovery?.let { discovery ->
                        DetailValue(stringResource(R.string.technical_source_discovery), discovery.state)
                        discovery.reason?.let { DetailValue(stringResource(R.string.technical_discovery_reason), it) }
                        DetailValue(stringResource(R.string.technical_source_commit), discovery.resolvedCommitSha ?: stringResource(R.string.value_not_available), true)
                        DetailValue(stringResource(R.string.add_gradle_candidates), discovery.candidateCount.toString())
                    }
                    record.selectedBuildConfiguration?.let { configuration ->
                        DetailValue(
                            "Build settings",
                            "revision ${configuration.revision} · ${configuration.validationState}",
                        )
                        DetailValue(stringResource(R.string.technical_settings_sha256), configuration.contentSha256, true)
                    }
                    DetailValue(stringResource(R.string.technical_release_last_checked), record.app.lastReleaseCheckedAt ?: stringResource(R.string.value_never))
                    DetailValue(stringResource(R.string.technical_release_variant), effectiveVariant(record, globalSettings).displayName())
                    DetailValue(stringResource(R.string.settings_abi), effectiveAbi(record, globalSettings).displayName())
                    DetailValue(stringResource(R.string.technical_apk_limit), "${effectiveLimit(record, globalSettings) / MIB} MiB")
                }
            }
            latest?.let { release ->
                item {
                    DetailCard(stringResource(R.string.technical_latest_release)) {
                        DetailValue(stringResource(R.string.technical_release), release.snapshot.releaseName)
                        DetailValue(stringResource(R.string.technical_tag), release.snapshot.tagName)
                        DetailValue(stringResource(R.string.technical_resolved_commit), release.snapshot.resolvedCommitSha, true)
                        DetailValue(stringResource(R.string.technical_target_commitish), release.snapshot.targetCommitishRaw)
                        DetailValue(stringResource(R.string.technical_published), release.snapshot.publishedAt)
                    }
                }
            }
            if (
                latest != null &&
                latest.snapshot.selectedProviderAssetId == null &&
                latest.assets.isNotEmpty()
            ) {
                item {
                    DetailCard(stringResource(R.string.technical_select_apk)) {
                        Text(
                            stringResource(R.string.technical_select_apk_body),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            stringResource(R.string.technical_select_apk_unknown_body),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        latest.assets
                            .sortedWith(compareBy<ReleaseAssetEntity> { it.assetName.lowercase() }.thenBy { it.providerAssetId })
                            .forEach { candidate ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !active) {
                                            selectedReleaseAssetId = candidate.providerAssetId
                                        }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(
                                        selected = selectedReleaseAssetId == candidate.providerAssetId,
                                        onClick = {
                                            selectedReleaseAssetId = candidate.providerAssetId
                                        },
                                        enabled = !active,
                                    )
                                    Column(modifier = Modifier.padding(start = 8.dp)) {
                                        DetailValue(stringResource(R.string.technical_file), candidate.assetName)
                                        DetailValue(stringResource(R.string.technical_provider_size), formatBytes(candidate.providerSizeBytes))
                                        DetailValue(stringResource(R.string.technical_content_type), candidate.contentType)
                                        DetailValue(stringResource(R.string.technical_filename_hints), releaseCandidateHints(candidate.assetName))
                                        DetailValue(
                                            "Provider SHA-256",
                                            candidate.providerDigestSha256 ?: "Not supplied",
                                            monospace = true,
                                        )
                                    }
                                }
                            }
                        Button(
                            enabled = !active && selectedReleaseAssetId != null,
                            onClick = {
                                selectedReleaseAssetId?.let { providerAssetId ->
                                    onSelectReleaseAsset(latest.snapshot.releaseSnapshotId, providerAssetId)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.technical_select_download)) }
                    }
                }
            }
            asset?.let { current ->
                item {
                    DetailCard(stringResource(R.string.technical_official_apk)) {
                        DetailValue(stringResource(R.string.technical_asset), current.assetName)
                        DetailValue(stringResource(R.string.technical_selection), current.selectionReason)
                        DetailValue(stringResource(R.string.technical_provider_sha256), current.providerDigestSha256 ?: stringResource(R.string.value_not_available), true)
                        DetailValue(stringResource(R.string.technical_computed_sha256), current.computedRawSha256 ?: stringResource(R.string.value_not_available), true)
                        DetailValue(stringResource(R.string.label_package), current.packageName ?: stringResource(R.string.value_unknown))
                        DetailValue(stringResource(R.string.technical_version), current.versionName ?: stringResource(R.string.value_not_available))
                        DetailValue(
                            "Installed",
                            current.installedVersionName?.let { "$it (${current.installedVersionCode})" }
                                ?: "Not installed",
                        )
                        DetailValue(stringResource(R.string.label_update), updateLabel(current.updateStatus))
                        DetailValue(stringResource(R.string.technical_signer_relation), signerLabel(current.existingInstallStatus))
                        DetailValue(stringResource(R.string.technical_signer), current.currentSignerSha256 ?: stringResource(R.string.value_unknown), true)
                        DetailValue(stringResource(R.string.technical_comparison), current.comparisonEligibility)
                        current.incomparableReason?.let { DetailValue(stringResource(R.string.storage_reason), it) }
                        current.downloadErrorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
            item {
                DetailCard(stringResource(R.string.technical_history)) {
                    Text(
                        stringResource(R.string.technical_history_body),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    record.releases
                        .sortedWith(
                            compareByDescending<com.sanka1610.reprodroid.data.local.ReleaseSnapshotWithAssets> {
                                it.snapshot.lastObservedAt
                            }.thenByDescending { it.snapshot.releaseSnapshotId },
                        )
                        .forEach { observation ->
                            val observedAsset = observation.selectedAsset
                            val availabilityState = observedAsset?.let { selected ->
                                availability.firstOrNull {
                                    it.ownerType == "ANDROID" &&
                                        it.resourceKind == "REFERENCE_APK" &&
                                        it.resourceId == selected.releaseAssetId
                                }?.state
                            } ?: "UNKNOWN"
                            HorizontalDivider()
                            DetailValue(stringResource(R.string.technical_release), "${observation.snapshot.tagName} · ${observation.snapshot.publishedAt}")
                            DetailValue(stringResource(R.string.technical_observation), observation.snapshot.observationSha256, monospace = true)
                            DetailValue(stringResource(R.string.technical_last_observed), observation.snapshot.lastObservedAt)
                            DetailValue(stringResource(R.string.technical_apk_availability), availabilityState)
                        }
                    record.comparisons.sortedByDescending { it.createdAt }.forEach { comparison ->
                        HorizontalDivider()
                        DetailValue(stringResource(R.string.technical_comparison), "${comparison.createdAt} · ${comparison.status}")
                        DetailValue(stringResource(R.string.technical_raw_outcomes), buildString {
                            append(comparison.outcome)
                            if (comparison.protocolVersion >= 2) {
                                append(" / ${comparison.repeatOfficialOutcome} / ${comparison.repeatabilityOutcome}")
                            }
                        })
                    }
                    record.releaseInstallAttempts.sortedByDescending { it.createdAt }.forEach { attempt ->
                        HorizontalDivider()
                        DetailValue(stringResource(R.string.technical_install_attempt), "${attempt.createdAt} · ${attempt.status}")
                    }
                }
            }
            if (record.app.managementMode == ManagementMode.VERIFICATION.name) {
                val comparison = record.currentComparison
                item {
                    DetailCard(stringResource(R.string.technical_comparison)) {
                        DetailValue(stringResource(R.string.technical_trust), trustLabel(record))
                        Text(
                            stringResource(R.string.technical_comparison_body),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (comparison == null) {
                            Text(
                                stringResource(R.string.technical_build_compare_body),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Button(
                                enabled = !active &&
                                    record.app.releaseDiscoveryStatus == ReleaseDiscoveryStatus.AVAILABLE.name &&
                                    asset?.downloadStatus == ReferenceDownloadStatus.VERIFIED.name &&
                                    asset.comparisonEligibility != ComparisonEligibility.INCOMPARABLE.name,
                                onClick = onStartComparison,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(stringResource(R.string.technical_build_compare)) }
                        } else {
                            DetailValue(stringResource(R.string.technical_status), comparison.status)
                            DetailValue(stringResource(R.string.label_official_primary), comparison.outcome)
                            if (comparison.protocolVersion >= 2) {
                                DetailValue(stringResource(R.string.label_official_repeat), comparison.repeatOfficialOutcome)
                                DetailValue(stringResource(R.string.label_local_repeatability), comparison.repeatabilityOutcome)
                            }
                            DetailValue(stringResource(R.string.technical_expected_recipe), comparison.expectedRecipeId)
                            DetailValue(
                                stringResource(R.string.technical_dependency_pinning, "A"),
                                dependencyPinningLabel(comparison.runnerDependencyPinning),
                            )
                            if (comparison.protocolVersion >= 2 && comparison.repeatRunnerJobId != null) {
                                DetailValue(
                                    stringResource(R.string.technical_dependency_pinning, "B"),
                                    dependencyPinningLabel(comparison.repeatRunnerDependencyPinning),
                                )
                                if (comparison.runnerDependencyPinning != comparison.repeatRunnerDependencyPinning) {
                                    Text(
                                        "Build A and Build B used different dependency-pinning policies. " +
                                            "This is advisory evidence and does not change comparison, trust, update, or install decisions.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                            if (
                                comparison.runnerDependencyPinning == "LOCKFILE_OFFLINE" ||
                                comparison.repeatRunnerDependencyPinning == "LOCKFILE_OFFLINE"
                            ) {
                                Text(
                                    stringResource(R.string.technical_offline_warning),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            DetailValue(stringResource(R.string.technical_expected_commit), comparison.expectedCommitSha, true)
                            comparison.runnerResolvedCommitSha?.let { DetailValue(stringResource(R.string.technical_runner_commit), it, true) }
                            comparison.repeatRunnerResolvedCommitSha?.let {
                                DetailValue(stringResource(R.string.technical_repeat_runner_commit), it, true)
                            }
                            if (comparison.protocolVersion >= 2) {
                                val buildARecord = runnerJobs[comparison.runnerJobId]
                                val buildBRecord = comparison.repeatRunnerJobId?.let(runnerJobs::get)
                                val buildAJob = buildARecord?.job
                                val buildBJob = buildBRecord?.job
                                val buildAManifest = buildEnvironmentManifests[comparison.runnerJobId]
                                val buildBManifest = comparison.repeatRunnerJobId?.let(buildEnvironmentManifests::get)
                                val environmentComparison = compareBuildEnvironments(
                                    buildAJob,
                                    buildBJob,
                                    buildAManifest,
                                    buildBManifest,
                                )
                                Text(stringResource(R.string.technical_environment_evidence), style = MaterialTheme.typography.titleSmall)
                                DetailValue(stringResource(R.string.technical_build_sandbox, "A"), sandboxSelectionText(buildAJob))
                                DetailValue(stringResource(R.string.technical_build_sandbox, "B"), sandboxSelectionText(buildBJob))
                                DetailValue(stringResource(R.string.technical_build_execution, "A"), sandboxManifestText(buildAManifest?.manifest?.sandboxJson))
                                DetailValue(stringResource(R.string.technical_build_execution, "B"), sandboxManifestText(buildBManifest?.manifest?.sandboxJson))
                                sandboxWarnings[comparison.runnerJobId]?.let { DetailValue(stringResource(R.string.technical_build_sandbox_warning, "A"), it) }
                                comparison.repeatRunnerJobId?.let(sandboxWarnings::get)?.let { DetailValue(stringResource(R.string.technical_build_sandbox_warning, "B"), it) }
                                Text(
                                    stringResource(R.string.technical_environment_evidence_body),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                SourceScanEvidence(stringResource(R.string.technical_build_source_scan, "A"), buildARecord?.sourceScan)
                                SourceScanEvidence(stringResource(R.string.technical_build_source_scan, "B"), buildBRecord?.sourceScan)
                                sourceScanWarnings[comparison.runnerJobId]?.let { warning ->
                                    DetailValue(stringResource(R.string.technical_build_source_scan_warning, "A"), "${warning.code}: ${warning.message}")
                                }
                                comparison.repeatRunnerJobId?.let(sourceScanWarnings::get)?.let { warning ->
                                    DetailValue(stringResource(R.string.technical_build_source_scan_warning, "B"), "${warning.code}: ${warning.message}")
                                }
                                buildAManifest?.let { evidence ->
                                    DetailValue(
                                        "Build A environment",
                                        "Java ${evidence.manifest.javaVersion} (${evidence.manifest.javaVendor}), " +
                                            "Gradle ${evidence.manifest.gradleVersion}, SDK API " +
                                            "${evidence.manifest.androidSdkApiLevel}, Build Tools " +
                                            "${evidence.manifest.buildToolsVersion}; determinism: " +
                                            determinismSummary(
                                                evidence.manifest.sourceDateEpoch,
                                                evidence.manifest.noBuildCache,
                                                evidence.manifest.fixedLocale,
                                            ),
                                    )
                                }
                                buildBManifest?.let { evidence ->
                                    DetailValue(
                                        "Build B environment",
                                        "Java ${evidence.manifest.javaVersion} (${evidence.manifest.javaVendor}), " +
                                            "Gradle ${evidence.manifest.gradleVersion}, SDK API " +
                                            "${evidence.manifest.androidSdkApiLevel}, Build Tools " +
                                            "${evidence.manifest.buildToolsVersion}; determinism: " +
                                            determinismSummary(
                                                evidence.manifest.sourceDateEpoch,
                                                evidence.manifest.noBuildCache,
                                                evidence.manifest.fixedLocale,
                                            ),
                                    )
                                }
                                buildManifestWarnings[comparison.runnerJobId]?.let { warning ->
                                    DetailValue(stringResource(R.string.technical_build_manifest_warning, "A"), "${warning.code}: ${warning.message}")
                                }
                                comparison.repeatRunnerJobId?.let(buildManifestWarnings::get)?.let { warning ->
                                    DetailValue(stringResource(R.string.technical_build_manifest_warning, "B"), "${warning.code}: ${warning.message}")
                                }
                                if (environmentComparison.comparable) {
                                    DetailValue(
                                        "Dependency multiset",
                                        "same ${environmentComparison.sameCount}, changed ${environmentComparison.changedCount}, " +
                                            "Build A only ${environmentComparison.buildAOnlyCount}, " +
                                            "Build B only ${environmentComparison.buildBOnlyCount}",
                                    )
                                    environmentComparison.differences.asSequence()
                                        .filter { it.kind != DependencyDifferenceKind.SAME }
                                        .take(MAX_DEPENDENCY_DIFFERENCES_IN_UI)
                                        .forEach { difference ->
                                            DetailValue(
                                                difference.fileName,
                                                when (difference.kind) {
                                                    DependencyDifferenceKind.CHANGED -> "changed"
                                                    DependencyDifferenceKind.BUILD_A_ONLY -> "Build A only"
                                                    DependencyDifferenceKind.BUILD_B_ONLY -> "Build B only"
                                                    DependencyDifferenceKind.SAME -> "same"
                                                },
                                            )
                                        }
                                } else {
                                    DetailValue(stringResource(R.string.technical_dependency_comparison), environmentComparison.reason ?: stringResource(R.string.value_not_available))
                                }
                                if (
                                    buildAJob?.effectiveRecipeId != buildBJob?.effectiveRecipeId ||
                                    buildAJob?.effectiveVariantName != buildBJob?.effectiveVariantName ||
                                    buildAManifest?.manifest?.javaVersion != buildBManifest?.manifest?.javaVersion ||
                                    buildAManifest?.manifest?.sourceDateEpoch != buildBManifest?.manifest?.sourceDateEpoch ||
                                    buildAManifest?.manifest?.noBuildCache != buildBManifest?.manifest?.noBuildCache ||
                                    buildAManifest?.manifest?.fixedLocale != buildBManifest?.manifest?.fixedLocale
                                ) {
                                    Text(
                                        "Build recipe, variant, Java, or determinism controls differ. " +
                                            "This is advisory evidence and is not presented as a cause or used to change raw outcomes.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                            comparison.incomparableReason?.let { DetailValue(stringResource(R.string.storage_reason), it) }
                            comparison.repeatIncomparableReason?.let { DetailValue(stringResource(R.string.technical_repeat_reason), it) }
                            record.currentAdvancedComparisonSummaries.forEach { summary ->
                                Text(
                                    when (summary.axis) {
                                        "OFFICIAL_PRIMARY" -> "Advanced evidence: Official vs Build A"
                                        "OFFICIAL_REPEAT" -> "Advanced evidence: Official vs Build B"
                                        "LOCAL_REPEATABILITY" -> "Advanced evidence: Build A vs Build B"
                                        else -> "Advanced evidence: ${summary.axis}"
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                DetailValue(stringResource(R.string.technical_apk_entries), "${summary.inventoryOutcome} (${summary.entryCount})")
                                DetailValue(
                                    "Entry changes",
                                    "same ${summary.sameCount}, changed ${summary.changedCount}, " +
                                        "added ${summary.addedCount}, missing ${summary.missingCount}",
                                )
                                DetailValue(stringResource(R.string.technical_dex_structure), summary.dexStructuralOutcome)
                                DetailValue(stringResource(R.string.technical_manifest_meaning), summary.manifestSemanticOutcome)
                                DetailValue(stringResource(R.string.technical_resource_table_meaning), summary.resourceTableSemanticOutcome)
                                DetailValue(stringResource(R.string.technical_semantic_differences), summary.semanticDifferenceCount.toString())
                                record.currentSemanticDifferenceEvidence
                                    .asSequence()
                                    .filter { it.axis == summary.axis }
                                    .take(MAX_SEMANTIC_DIFFERENCES_IN_UI)
                                    .forEach { difference ->
                                        DetailValue(
                                            difference.component,
                                            "${difference.result}: ${difference.stableKey}",
                                        )
                                    }
                                summary.reason?.let { DetailValue(stringResource(R.string.technical_advanced_reason), it) }
                            }
                            when (comparison.status) {
                                ComparisonRunStatus.AWAITING_CONFIRMATION.name,
                                ComparisonRunStatus.AWAITING_REPEAT_CONFIRMATION.name -> {
                                    val confirmationJobId = if (comparison.status == ComparisonRunStatus.AWAITING_REPEAT_CONFIRMATION.name)
                                        comparison.repeatRunnerJobId else comparison.runnerJobId
                                    val confirmationJob = confirmationJobId?.let(runnerJobs::get)?.job
                                    val confirmationAllowed = confirmationJob != null && sandboxAcknowledgementAllowed(confirmationJob) &&
                                        sandboxWarnings[confirmationJobId] == null
                                    Text(
                                        if (confirmationJob?.sandboxMode == "DOCKER") {
                                        stringResource(R.string.technical_repeat_docker_warning)
                                        } else if (comparison.status == ComparisonRunStatus.AWAITING_REPEAT_CONFIRMATION.name) {
                                            stringResource(R.string.technical_repeat_build_warning)
                                        } else {
                                            stringResource(R.string.technical_primary_build_warning)
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                    Button(
                                        enabled = !active && confirmationAllowed,
                                        onClick = { onConfirmComparison(comparison.comparisonRunId) },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(
                                            if (comparison.status == ComparisonRunStatus.AWAITING_REPEAT_CONFIRMATION.name) {
                                                stringResource(R.string.technical_confirm_repeat)
                                            } else {
                                                stringResource(R.string.technical_confirm_primary)
                                            },
                                        )
                                    }
                                }
                                ComparisonRunStatus.AWAITING_SCAN_REVIEW.name,
                                ComparisonRunStatus.AWAITING_REPEAT_SCAN_REVIEW.name -> {
                                    val repeatReview = comparison.status ==
                                        ComparisonRunStatus.AWAITING_REPEAT_SCAN_REVIEW.name
                                    val reviewJobId = if (repeatReview) {
                                        comparison.repeatRunnerJobId
                                    } else {
                                        comparison.runnerJobId
                                    }
                                    val scan = reviewJobId?.let(runnerJobs::get)?.sourceScan
                                    val reviewJob = reviewJobId?.let(runnerJobs::get)?.job
                                    val sandboxReviewAllowed = reviewJob != null && sandboxAcknowledgementAllowed(reviewJob) && sandboxWarnings[reviewJobId] == null
                                    Text(
                                        if (repeatReview) {
                                            stringResource(R.string.technical_review_repeat_scan)
                                        } else {
                                            stringResource(R.string.technical_review_primary_scan)
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = sourceScanRiskConfirmed,
                                            onCheckedChange = { sourceScanRiskConfirmed = it },
                                        )
                                        Text(stringResource(R.string.technical_reviewed_findings))
                                    }
                                    Button(
                                        enabled = !active && sandboxReviewAllowed && sourceScanRiskConfirmed &&
                                            (scan?.scan?.let { it.requiresReview && !it.reviewed } == true),
                                        onClick = {
                                            onContinueComparisonSourceScan(comparison.comparisonRunId)
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(
                                            if (repeatReview) {
                                                stringResource(R.string.technical_ack_repeat)
                                            } else {
                                                stringResource(R.string.technical_ack_primary)
                                            },
                                        )
                                    }
                                }
                                ComparisonRunStatus.COMPLETED.name -> Button(
                                    enabled = !active,
                                    onClick = onStartComparison,
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text(stringResource(R.string.technical_run_again)) }
                                else -> Button(
                                    enabled = !active,
                                    onClick = { onRefreshComparison(comparison.comparisonRunId) },
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text(stringResource(R.string.technical_refresh_comparison)) }
                            }
                        }
                    }
                }
            }
            item {
                DetailCard(stringResource(R.string.technical_installation)) {
                    DetailValue(stringResource(R.string.technical_source), installationSourceLabel(record.app.installationSource))
                    if (record.app.installationSource == InstallationSource.LOCAL_BUILD.name) {
                        Text(
                            stringResource(R.string.technical_local_install_body),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (warningRequired && canInstall) {
                        Row(verticalAlignment = Alignment.Top) {
                            Checkbox(
                                checked = installRiskConfirmed,
                                onCheckedChange = { installRiskConfirmed = it },
                            )
                            Text(
                                stringResource(R.string.technical_install_risk),
                                modifier = Modifier.padding(top = 10.dp),
                            )
                        }
                    }
                    if (!canRequestPackageInstalls) {
                        TextButton(
                            onClick = {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                        "package:${context.packageName}".toUri(),
                                    ),
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.technical_allow_installs)) }
                    }
                    Button(
                        enabled = !active && canInstall &&
                            canRequestPackageInstalls &&
                            (!warningRequired || installRiskConfirmed),
                        onClick = { onInstall(installRiskConfirmed) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(
                                if (asset?.updateStatus == UpdateStatus.UPDATE_AVAILABLE.name) {
                                    R.string.technical_update
                                } else {
                                    R.string.technical_install
                                },
                            ),
                        )
                    }
                    record.latestReleaseInstallAttempt?.let { attempt ->
                        DetailValue(stringResource(R.string.technical_latest_install_attempt), attempt.status)
                        attempt.statusMessage?.let { DetailValue(stringResource(R.string.technical_installer_message), it) }
                    }
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppPreferencesScreen(
    record: RegisteredAppRecord,
    globalSettings: GlobalSettingsEntity,
    saving: Boolean,
    onBack: () -> Unit,
    onSave: (AppSettingsUpdate) -> Unit,
    onSaveBuildConfiguration: (Long?, BuildConfigurationInput) -> Unit,
) {
    BackHandler(onBack = onBack)
    val appSettingsBackDescription = stringResource(R.string.action_back)
    var mode by rememberSaveable(record.app.registeredAppId) {
        mutableStateOf(enumValue(record.app.managementMode, ManagementMode.VERIFICATION))
    }
    var source by rememberSaveable(record.app.registeredAppId) {
        mutableStateOf(enumValue(record.app.installationSource, InstallationSource.OFFICIAL_RELEASE))
    }
    var variantChoice by rememberSaveable(record.app.registeredAppId) {
        mutableStateOf(if (record.app.useGlobalReleaseVariant) "GLOBAL" else record.app.releaseVariantPreference)
    }
    var abiChoice by rememberSaveable(record.app.registeredAppId) {
        mutableStateOf(if (record.app.useGlobalPreferredAbi) "GLOBAL" else record.app.preferredAbi)
    }
    var limitChoice by rememberSaveable(record.app.registeredAppId) {
        mutableStateOf(if (record.app.useGlobalMaxApkSize) -1L else record.app.maxApkSizeBytes)
    }
    var localRiskConfirmed by rememberSaveable(record.app.registeredAppId) {
        mutableStateOf(source == InstallationSource.LOCAL_BUILD)
    }
    val sourceLocked = record.latestRelease?.selectedAsset?.installedVersionCode != null
    val initialBuildConfiguration = remember(record.app.registeredAppId) {
        record.selectedBuildConfiguration?.let { configuration ->
            runCatching {
                BuildConfigurationValidator.decodeCanonical(
                    configuration.canonicalJson,
                    configuration.contentSha256,
                )
            }.getOrNull()
        } ?: BuildConfigurationInput()
    }
    var buildRoot by rememberSaveable(record.app.registeredAppId) {
        mutableStateOf(initialBuildConfiguration.buildRoot.orEmpty())
    }
    var modulePath by rememberSaveable(record.app.registeredAppId) {
        mutableStateOf(initialBuildConfiguration.modulePath.orEmpty())
    }
    var buildVariant by rememberSaveable(record.app.registeredAppId) {
        mutableStateOf(initialBuildConfiguration.variant.orEmpty())
    }
    var buildTasks by rememberSaveable(record.app.registeredAppId) {
        mutableStateOf(initialBuildConfiguration.tasks.joinToString("\n"))
    }
    var javaMajor by rememberSaveable(record.app.registeredAppId) {
        mutableStateOf(initialBuildConfiguration.javaMajor?.toString().orEmpty())
    }
    var gradleVersion by rememberSaveable(record.app.registeredAppId) {
        mutableStateOf(initialBuildConfiguration.gradleVersion.orEmpty())
    }
    var compileSdk by rememberSaveable(record.app.registeredAppId) {
        mutableStateOf(initialBuildConfiguration.compileSdk?.toString().orEmpty())
    }
    var buildToolsVersion by rememberSaveable(record.app.registeredAppId) {
        mutableStateOf(initialBuildConfiguration.buildToolsVersion.orEmpty())
    }
    var ndkVersion by rememberSaveable(record.app.registeredAppId) {
        mutableStateOf(initialBuildConfiguration.ndkVersion.orEmpty())
    }
    var cmakeVersion by rememberSaveable(record.app.registeredAppId) {
        mutableStateOf(initialBuildConfiguration.cmakeVersion.orEmpty())
    }
    val numericBuildFieldsValid = listOf(javaMajor, compileSdk).all { value ->
        value.isBlank() || value.trim().toIntOrNull() != null
    }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.app_settings_for, record.app.resolvedDisplayName)) },
            navigationIcon = {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.semantics { contentDescription = appSettingsBackDescription },
                ) { NavigationGlyph("‹") }
            },
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionTitle(
                    stringResource(R.string.app_settings_registration),
                    stringResource(R.string.app_settings_registration_body),
                )
            }
            item {
                DropdownSetting(
                    stringResource(R.string.settings_management_mode),
                    mode,
                    ManagementMode.entries
                        .filter {
                            !sourceLocked || source != InstallationSource.LOCAL_BUILD ||
                                it == ManagementMode.VERIFICATION
                        }
                        .associateWith {
                            stringResource(
                                if (it == ManagementMode.VERIFICATION) {
                                    R.string.mode_verification
                                } else {
                                    R.string.mode_acquisition
                                },
                            )
                        },
                    onSelect = {
                        mode = it
                        if (it == ManagementMode.ACQUISITION && !sourceLocked) {
                            source = InstallationSource.OFFICIAL_RELEASE
                        }
                    },
                )
            }
            item {
                DropdownSetting(
                    stringResource(R.string.settings_installation_source),
                    source,
                    InstallationSource.entries
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
                    onSelect = {
                        source = it
                        if (it == InstallationSource.LOCAL_BUILD) localRiskConfirmed = false
                    },
                    enabled = !sourceLocked,
                    supportingText = if (sourceLocked) {
                        stringResource(
                            R.string.app_settings_source_locked,
                            record.latestRelease?.selectedAsset?.packageName
                                ?: stringResource(R.string.app_settings_target_package),
                        )
                    } else {
                        stringResource(R.string.app_settings_source_help)
                    },
                )
            }
            if (source == InstallationSource.LOCAL_BUILD && !sourceLocked) {
                item {
                    Row(verticalAlignment = Alignment.Top) {
                        Checkbox(checked = localRiskConfirmed, onCheckedChange = { localRiskConfirmed = it })
                        Text(stringResource(R.string.app_settings_local_risk), Modifier.padding(top = 10.dp))
                    }
                }
            }
            item { HorizontalDivider() }
            item {
                SectionTitle(
                    stringResource(R.string.app_settings_future),
                    stringResource(R.string.app_settings_future_body),
                )
            }
            item { PlannedAppSetting(stringResource(R.string.app_settings_scheduled_checks), "4.5") }
            item { PlannedAppSetting(stringResource(R.string.app_settings_release_channel), "4.5") }
            item { PlannedAppSetting(stringResource(R.string.app_settings_automatic_actions), "4.5") }
            item { PlannedAppSetting(stringResource(R.string.app_settings_notifications), "4.5") }
            item { HorizontalDivider() }
            item {
                SectionTitle(
                    stringResource(R.string.app_settings_inherited),
                    stringResource(R.string.app_settings_inherited_body),
                )
            }
            item {
                DropdownSetting(
                    stringResource(R.string.settings_release_variant),
                    variantChoice,
                    linkedMapOf(
                        "GLOBAL" to
                            stringResource(
                                R.string.app_settings_use_global,
                                globalSettings.defaultReleaseVariantPreference.displayEnum(),
                            ),
                    ) + ReleaseVariantPreference.entries.associate { it.name to it.displayName() },
                    onSelect = { variantChoice = it },
                )
            }
            item {
                DropdownSetting(
                    stringResource(R.string.settings_abi),
                    abiChoice,
                    linkedMapOf(
                        "GLOBAL" to stringResource(
                            R.string.app_settings_use_global,
                            globalSettings.defaultPreferredAbi.displayEnum(),
                        ),
                    ) + PreferredAbi.entries.associate { it.name to it.displayName() },
                    onSelect = { abiChoice = it },
                )
            }
            item {
                DropdownSetting(
                    stringResource(R.string.app_settings_apk_limit),
                    limitChoice,
                    linkedMapOf(
                        -1L to stringResource(
                            R.string.app_settings_use_global,
                            "${globalSettings.defaultMaxApkSizeBytes / MIB} MiB",
                        ),
                    ) + APK_LIMITS.associateWith { "${it / MIB} MiB" },
                    onSelect = { limitChoice = it },
                )
            }
            item {
                Text(
                    stringResource(R.string.app_settings_selection_change),
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    enabled = !saving && (source != InstallationSource.LOCAL_BUILD || localRiskConfirmed),
                    onClick = {
                        onSave(
                            AppSettingsUpdate(
                                managementMode = mode,
                                installationSource = source,
                                releaseVariantPreference = enumValue(
                                    variantChoice.takeUnless { it == "GLOBAL" }
                                        ?: globalSettings.defaultReleaseVariantPreference,
                                    ReleaseVariantPreference.RELEASE,
                                ),
                                useGlobalReleaseVariant = variantChoice == "GLOBAL",
                                preferredAbi = enumValue(
                                    abiChoice.takeUnless { it == "GLOBAL" }
                                        ?: globalSettings.defaultPreferredAbi,
                                    PreferredAbi.ARM64_V8A,
                                ),
                                useGlobalPreferredAbi = abiChoice == "GLOBAL",
                                maxApkSizeBytes = if (limitChoice == -1L) {
                                    globalSettings.defaultMaxApkSizeBytes
                                } else {
                                    limitChoice
                                },
                                useGlobalMaxApkSize = limitChoice == -1L,
                                localBuildRiskConfirmed = localRiskConfirmed,
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(
                            if (saving) R.string.app_settings_saving else R.string.app_settings_save,
                        ),
                    )
                }
            }
            item { HorizontalDivider() }
            item {
                SectionTitle(
                    stringResource(R.string.app_settings_build_configuration),
                    stringResource(R.string.app_settings_build_configuration_body),
                )
            }
            item { BuildSettingField(stringResource(R.string.build_root), buildRoot, { buildRoot = it }, ". or relative path") }
            item { BuildSettingField(stringResource(R.string.build_module_path), modulePath, { modulePath = it }, ":app") }
            item { BuildSettingField(stringResource(R.string.build_variant), buildVariant, { buildVariant = it }, "release") }
            item {
                OutlinedTextField(
                    value = buildTasks,
                    onValueChange = { buildTasks = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.build_tasks)) },
                    supportingText = { Text(":app:assembleRelease") },
                    minLines = 2,
                )
            }
            item { BuildSettingField(stringResource(R.string.build_java_major), javaMajor, { javaMajor = it }, "21") }
            item { BuildSettingField(stringResource(R.string.build_gradle_version), gradleVersion, { gradleVersion = it }, "9.1.0") }
            item { BuildSettingField("compileSdk", compileSdk, { compileSdk = it }, "36") }
            item { BuildSettingField(stringResource(R.string.build_tools_version), buildToolsVersion, { buildToolsVersion = it }, "36.0.0") }
            item { BuildSettingField(stringResource(R.string.build_ndk_version), ndkVersion, { ndkVersion = it }, "") }
            item { BuildSettingField(stringResource(R.string.build_cmake_version), cmakeVersion, { cmakeVersion = it }, "") }
            item {
                Button(
                    enabled = !saving && numericBuildFieldsValid,
                    onClick = {
                        fun optional(value: String) = value.trim().takeIf(String::isNotEmpty)
                        onSaveBuildConfiguration(
                            record.selectedBuildConfiguration?.revision,
                            BuildConfigurationInput(
                                buildRoot = optional(buildRoot),
                                modulePath = optional(modulePath),
                                variant = optional(buildVariant),
                                tasks = buildTasks.lines().map(String::trim).filter(String::isNotEmpty),
                                javaMajor = optional(javaMajor)?.toIntOrNull(),
                                gradleVersion = optional(gradleVersion),
                                compileSdk = optional(compileSdk)?.toIntOrNull(),
                                buildToolsVersion = optional(buildToolsVersion),
                                ndkVersion = optional(ndkVersion),
                                cmakeVersion = optional(cmakeVersion),
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(
                            if (saving) R.string.app_settings_saving else R.string.build_save_configuration,
                        ),
                    )
                }
                if (!numericBuildFieldsValid) {
                    Text(
                        stringResource(R.string.build_numeric_error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun BuildSettingField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    hint: String,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        supportingText = hint.takeIf(String::isNotEmpty)?.let { text -> { Text(text) } },
        singleLine = true,
    )
}

@Composable
private fun PlannedAppSetting(label: String, phase: String) {
    OutlinedTextField(
        value = stringResource(R.string.planned_phase, phase),
        onValueChange = {},
        modifier = Modifier.fillMaxWidth(),
        enabled = false,
        readOnly = true,
        label = { Text(label) },
        supportingText = { Text(stringResource(R.string.planned_unavailable)) },
    )
}

@Composable
internal fun ManagedAppIcon(record: RegisteredAppRecord) {
    val context = LocalContext.current
    val assetId = record.latestRelease?.selectedAsset
        ?.takeIf { it.downloadStatus == ReferenceDownloadStatus.VERIFIED.name }
        ?.releaseAssetId
    val iconFile = remember(context.filesDir, assetId) {
        val safeId = assetId?.let { runCatching { UUID.fromString(it).toString() }.getOrNull() }
        safeId?.let { File(context.filesDir, "reference-icons/$it.png") }
    }
    val lastModified = iconFile?.takeIf(File::isFile)?.lastModified() ?: 0L
    val image = remember(iconFile?.absolutePath, lastModified) {
        iconFile?.takeIf(File::isFile)?.let { BitmapFactory.decodeFile(it.absolutePath) }?.asImageBitmap()
    }
    Surface(
        modifier = Modifier.size(52.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.large),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Text(record.app.resolvedDisplayName.take(2).uppercase(), color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

private fun PreferredAbi.displayName(): String = when (this) {
    PreferredAbi.ARM64_V8A -> "arm64-v8a"
    PreferredAbi.ARMEABI_V7A -> "armeabi-v7a"
    PreferredAbi.X86_64 -> "x86_64"
    PreferredAbi.UNIVERSAL -> "universal"
}

private fun releaseCandidateHints(assetName: String): String {
    val filename = assetName.lowercase()
    val abi = when {
        "arm64-v8a" in filename || "arm64_v8a" in filename -> "arm64-v8a"
        "armeabi-v7a" in filename || "armeabi_v7a" in filename || "arm-v7a" in filename -> "armeabi-v7a"
        "x86_64" in filename || "x86-64" in filename -> "x86_64"
        "universal" in filename -> "universal"
        else -> "not inferred"
    }
    val variant = when {
        "debug" in filename -> "debug"
        "preview" in filename -> "preview"
        "release" in filename -> "release"
        else -> "not inferred"
    }
    return "ABI: $abi; variant: $variant (filename only)"
}

@Composable
private fun SettingsScreen(
    settings: GlobalSettingsEntity,
    onUpdate: (GlobalSettingsEntity) -> Unit,
    onOpenRunnerJobs: () -> Unit,
    onOpenStorage: () -> Unit,
    onOpenToolchains: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Spacer(Modifier.height(16.dp))
            Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        }
        item { SectionTitle("Appearance", "Applied immediately to ReproDroid.") }
        item {
            DropdownSetting(
                "Theme",
                enumValue(settings.themeMode, ThemeMode.DARK),
                ThemeMode.entries.associateWith { it.name.displayEnum() },
                onSelect = { onUpdate(settings.copy(themeMode = it.name)) },
            )
        }
        item { HorizontalDivider() }
        item {
            SectionTitle(
                "Inherited app defaults",
                "Apps set to Use global default follow future changes.",
            )
        }
        item {
            DropdownSetting(
                "Default release variant",
                enumValue(settings.defaultReleaseVariantPreference, ReleaseVariantPreference.RELEASE),
                ReleaseVariantPreference.entries.associateWith { it.displayName() },
                onSelect = { onUpdate(settings.copy(defaultReleaseVariantPreference = it.name)) },
            )
        }
        item {
            DropdownSetting(
                "Default ABI",
                enumValue(settings.defaultPreferredAbi, PreferredAbi.ARM64_V8A),
                PreferredAbi.entries.associateWith { it.displayName() },
                onSelect = { onUpdate(settings.copy(defaultPreferredAbi = it.name)) },
            )
        }
        item {
            DropdownSetting(
                "Default APK download limit",
                settings.defaultMaxApkSizeBytes,
                APK_LIMITS.associateWith { "${it / MIB} MiB" },
                onSelect = { onUpdate(settings.copy(defaultMaxApkSizeBytes = it)) },
                supportingText = "The security hard limit remains 512 MiB.",
            )
        }
        item { HorizontalDivider() }
        item {
            SectionTitle(
                "Registration defaults",
                "Copied into new apps. Existing registered apps do not follow changes.",
            )
        }
        item {
            DropdownSetting(
                "Default management mode",
                enumValue(settings.defaultManagementMode, ManagementMode.VERIFICATION),
                ManagementMode.entries.associateWith(::modeLabel),
                onSelect = { mode ->
                    onUpdate(
                        settings.copy(
                            defaultManagementMode = mode.name,
                            defaultInstallationSource = if (mode == ManagementMode.ACQUISITION) {
                                InstallationSource.OFFICIAL_RELEASE.name
                            } else {
                                settings.defaultInstallationSource
                            },
                        ),
                    )
                },
            )
        }
        item {
            val mode = enumValue(settings.defaultManagementMode, ManagementMode.VERIFICATION)
            DropdownSetting(
                "Default installation source",
                enumValue(settings.defaultInstallationSource, InstallationSource.OFFICIAL_RELEASE),
                InstallationSource.entries
                    .filter { mode == ManagementMode.VERIFICATION || it == InstallationSource.OFFICIAL_RELEASE }
                    .associateWith(::installationSourceLabel),
                onSelect = { onUpdate(settings.copy(defaultInstallationSource = it.name)) },
                supportingText = "Local build still requires explicit acknowledgement for every registration.",
            )
        }
        item { HorizontalDivider() }
        item { SettingInfo("Release provider", "Public GitHub Releases") }
        item { SettingInfo("GitHub API", "Unauthenticated · manual refresh") }
        item {
            HorizontalDivider()
            TextButton(onClick = onOpenToolchains, modifier = Modifier.fillMaxWidth()) {
                Text("Open managed build toolchains")
            }
        }
        item {
            TextButton(onClick = onOpenStorage, modifier = Modifier.fillMaxWidth()) {
                Text("Open Storage, cleanup, and audit tools")
            }
        }
        item {
            TextButton(onClick = onOpenRunnerJobs, modifier = Modifier.fillMaxWidth()) {
                Text("Open Runner jobs and Phase 1 tools")
            }
        }
        item { SettingInfo("ReproDroid", "0.1.0-alpha01 · Phase 4.4") }
    }
}

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
    onCopyAudit: (android.net.Uri) -> Unit,
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
    val auditDestination = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(AUDIT_MIME_TYPE),
    ) { destination -> destination?.let(onCopyAudit) }
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
                            onClick = { auditDestination.launch(export.suggestedName) },
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
        OutlinedTextField(
            value = options[value] ?: value.toString(),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            supportingText = supportingText?.let { text -> ({ Text(text) }) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled)
                .fillMaxWidth(),
        )
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
private fun SectionTitle(title: String, explanation: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(explanation, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SettingInfo(label: String, value: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DetailCard(title: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun SourceScanEvidence(label: String, evidence: SourceScanWithDetails?) {
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
private fun DetailValue(label: String, value: String, monospace: Boolean = false) {
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

private fun trustLabel(record: RegisteredAppRecord): String = when (record.trustLevel) {
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

private fun updateLabel(status: String?): String = when (status) {
    UpdateStatus.NOT_INSTALLED.name -> "Not installed"
    UpdateStatus.UPDATE_AVAILABLE.name -> "Update available"
    UpdateStatus.UP_TO_DATE.name -> "Up to date"
    UpdateStatus.OLDER_THAN_INSTALLED.name -> "Older release"
    UpdateStatus.UNKNOWN.name -> "Unknown"
    else -> "Not evaluated"
}

@Composable
private fun updateColor(status: String?): Color = when (status) {
    UpdateStatus.UPDATE_AVAILABLE.name -> MaterialTheme.colorScheme.primary
    UpdateStatus.UNKNOWN.name -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.secondary
}

private fun signerLabel(status: String?): String = when (status) {
    "SIGNER_MATCH" -> "Signer match"
    "SIGNER_MISMATCH" -> "Signer mismatch"
    "NOT_INSTALLED_OR_NOT_VISIBLE" -> "New install"
    else -> "Signer unknown"
}

private fun modeLabel(mode: ManagementMode): String =
    if (mode == ManagementMode.VERIFICATION) "Verification" else "Acquisition"

private fun modeLabel(mode: String): String = modeLabel(enumValue(mode, ManagementMode.VERIFICATION))

private fun installationSourceLabel(source: InstallationSource): String =
    if (source == InstallationSource.OFFICIAL_RELEASE) "Official release APK" else "Local ReproDroid build"

private fun installationSourceLabel(source: String): String =
    installationSourceLabel(enumValue(source, InstallationSource.OFFICIAL_RELEASE))

private fun ReleaseVariantPreference.displayName(): String = name.lowercase().replaceFirstChar(Char::uppercase)

private fun effectiveVariant(
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

private fun effectiveAbi(record: RegisteredAppRecord, settings: GlobalSettingsEntity): PreferredAbi = enumValue(
    if (record.app.useGlobalPreferredAbi) settings.defaultPreferredAbi else record.app.preferredAbi,
    PreferredAbi.ARM64_V8A,
)

private fun effectiveLimit(record: RegisteredAppRecord, settings: GlobalSettingsEntity): Long =
    if (record.app.useGlobalMaxApkSize) settings.defaultMaxApkSizeBytes else record.app.maxApkSizeBytes

private inline fun <reified T : Enum<T>> enumValue(value: String, fallback: T): T =
    enumValues<T>().firstOrNull { it.name == value } ?: fallback

private fun String.displayEnum(): String = lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)

private fun formatDecimalBytes(value: String): String = value.toLongOrNull()?.let(::formatBytes) ?: "Unavailable"

private fun formatBytes(value: Long): String = when {
    value >= 1024L * 1024L * 1024L -> "%.2f GiB".format(value.toDouble() / (1024L * 1024L * 1024L))
    value >= MIB -> "%.2f MiB".format(value.toDouble() / MIB)
    value >= 1024L -> "%.2f KiB".format(value.toDouble() / 1024L)
    else -> "$value B"
}

@Composable
private fun NavigationGlyph(value: String) {
    Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private const val MIB = 1024L * 1024L
private const val MAX_SEMANTIC_DIFFERENCES_IN_UI = 3
private const val MAX_DEPENDENCY_DIFFERENCES_IN_UI = 40
private const val MAX_SOURCE_SCAN_FINDINGS_IN_UI = 40
private const val AUDIT_MIME_TYPE = "application/vnd.reprodroid.audit+json"
private val APK_LIMITS = listOf(64L * MIB, 128L * MIB, 256L * MIB, 512L * MIB)
