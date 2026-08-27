package com.sanka1610.reprodroid.ui

import android.graphics.BitmapFactory
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.net.toUri
import com.sanka1610.reprodroid.data.local.ManagementMode
import com.sanka1610.reprodroid.data.local.AppSettingsUpdate
import com.sanka1610.reprodroid.data.local.ComparisonEligibility
import com.sanka1610.reprodroid.data.local.ComparisonRunStatus
import com.sanka1610.reprodroid.data.local.GlobalSettingsEntity
import com.sanka1610.reprodroid.data.local.InstallationSource
import com.sanka1610.reprodroid.data.local.PreferredAbi
import com.sanka1610.reprodroid.data.local.ReferenceDownloadStatus
import com.sanka1610.reprodroid.data.local.ReleaseDiscoveryStatus
import com.sanka1610.reprodroid.data.local.RegisteredAppRecord
import com.sanka1610.reprodroid.data.local.ReleaseVariantPreference
import com.sanka1610.reprodroid.data.local.ThemeMode
import com.sanka1610.reprodroid.data.local.TrustLevel
import com.sanka1610.reprodroid.data.local.UpdateStatus
import com.sanka1610.reprodroid.data.local.BuildEnvironmentManifestWithDependencies
import com.sanka1610.reprodroid.data.local.JobRecord
import com.sanka1610.reprodroid.data.repository.BuildManifestWarning
import com.sanka1610.reprodroid.data.repository.DependencyDifferenceKind
import com.sanka1610.reprodroid.data.repository.compareBuildEnvironments
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
fun ReproDroidApp(managedViewModel: ManagedAppsViewModel, jobViewModel: JobViewModel) {
    val apps by managedViewModel.apps.collectAsStateWithLifecycle()
    val globalSettings by managedViewModel.settings.collectAsStateWithLifecycle()
    val preview by managedViewModel.preview.collectAsStateWithLifecycle()
    val message by managedViewModel.message.collectAsStateWithLifecycle()
    val activeAppIds by managedViewModel.activeAppIds.collectAsStateWithLifecycle()
    val buildEnvironmentManifests by managedViewModel.buildEnvironmentManifests.collectAsStateWithLifecycle()
    val runnerJobs by managedViewModel.runnerJobs.collectAsStateWithLifecycle()
    val buildManifestWarnings by managedViewModel.buildManifestWarnings.collectAsStateWithLifecycle()
    var destination by rememberSaveable { mutableStateOf(MainDestination.APPS) }
    var selectedAppId by rememberSaveable { mutableStateOf<String?>(null) }
    var settingsAppId by rememberSaveable { mutableStateOf<String?>(null) }
    var showRunnerJobs by rememberSaveable { mutableStateOf(false) }
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
                            onClick = { destination = MainDestination.APPS; selectedAppId = null; settingsAppId = null },
                            icon = { NavigationGlyph("▦") },
                            label = { Text("Apps") },
                        )
                        NavigationBarItem(
                            selected = destination == MainDestination.ADD,
                            onClick = { destination = MainDestination.ADD; selectedAppId = null; settingsAppId = null },
                            icon = { NavigationGlyph("＋") },
                            label = { Text("Add") },
                        )
                        NavigationBarItem(
                            selected = destination == MainDestination.SETTINGS,
                            onClick = { destination = MainDestination.SETTINGS; selectedAppId = null; settingsAppId = null },
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
                                    runnerJobs = runnerJobs.associateBy { it.job.jobId },
                                    buildEnvironmentManifests = buildEnvironmentManifests.associateBy { it.manifest.jobId },
                                    buildManifestWarnings = buildManifestWarnings,
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
                            onRegister = { mode, source, confirmed ->
                                managedViewModel.register(mode, source, confirmed) { registeredAppId ->
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
                        else -> SettingsScreen(
                            settings = globalSettings,
                            onUpdate = managedViewModel::updateGlobalSettings,
                            onOpenRunnerJobs = { showRunnerJobs = true },
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
                Text(if (apps.isEmpty()) "No registered apps. Add MicroG-RE to begin." else "No matching apps.")
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
private fun AddAppScreen(
    preview: ReleasePreviewState,
    globalSettings: GlobalSettingsEntity,
    onPreview: (String) -> Unit,
    onRegister: (ManagementMode, InstallationSource, Boolean) -> Unit,
    onUrlChanged: () -> Unit,
) {
    var repositoryUrl by rememberSaveable { mutableStateOf("https://github.com/MorpheApp/MicroG-RE") }
    var mode by rememberSaveable(globalSettings.defaultManagementMode) {
        mutableStateOf(enumValue(globalSettings.defaultManagementMode, ManagementMode.VERIFICATION))
    }
    var installationSource by rememberSaveable(globalSettings.defaultInstallationSource) {
        mutableStateOf(
            enumValue(globalSettings.defaultInstallationSource, InstallationSource.OFFICIAL_RELEASE),
        )
    }
    var localRiskConfirmed by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Spacer(Modifier.height(16.dp))
            Text("Register an app", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text("Phase 2C · public GitHub Releases", style = MaterialTheme.typography.bodyMedium)
        }
        item {
            OutlinedTextField(
                value = repositoryUrl,
                onValueChange = { repositoryUrl = it; onUrlChanged() },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("GitHub repository URL") },
                supportingText = { Text("Only https://github.com/{owner}/{repository}") },
                singleLine = true,
            )
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
            ) { Text(if (preview.isLoading && preview.release == null) "Resolving…" else "Preview latest release") }
        }
        if (preview.isLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        preview.release?.let { resolved ->
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(resolved.repository.name, style = MaterialTheme.typography.titleLarge)
                        DetailValue("Release", resolved.release.name ?: resolved.release.tagName)
                        DetailValue("Tag", resolved.release.tagName)
                        DetailValue("Resolved commit", resolved.resolvedCommitSha, monospace = true)
                        DetailValue("APK", resolved.selectedAsset.asset.name)
                        DetailValue("Selection", resolved.selectedAsset.reason)
                        DetailValue("Size", "${resolved.selectedAsset.asset.size} bytes")
                        DetailValue("Provider SHA-256", resolved.selectedAsset.providerSha256 ?: "Not supplied", true)
                        Text(
                            "target_commitish is recorded but is not used as the checkout commit.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Button(
                            enabled = !preview.isLoading &&
                                (installationSource != InstallationSource.LOCAL_BUILD || localRiskConfirmed),
                            onClick = { onRegister(mode, installationSource, localRiskConfirmed) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (preview.isLoading) "Downloading and verifying…" else "Register and verify APK") }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppDetailScreen(
    record: RegisteredAppRecord,
    globalSettings: GlobalSettingsEntity,
    active: Boolean,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onRefresh: () -> Unit,
    onInstall: (Boolean) -> Unit,
    onStartComparison: () -> Unit,
    onRefreshComparison: (String) -> Unit,
    onConfirmComparison: (String) -> Unit,
    runnerJobs: Map<String, JobRecord>,
    buildEnvironmentManifests: Map<String, BuildEnvironmentManifestWithDependencies>,
    buildManifestWarnings: Map<String, BuildManifestWarning>,
) {
    BackHandler(onBack = onBack)
    val latest = record.latestRelease
    val asset = latest?.selectedAsset
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var installRiskConfirmed by rememberSaveable(record.app.registeredAppId) { mutableStateOf(false) }
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
            title = { Text(record.app.displayName) },
            navigationIcon = {
                IconButton(onClick = onBack) { NavigationGlyph("‹") }
            },
            actions = {
                IconButton(enabled = !active, onClick = onRefresh) {
                    NavigationGlyph("↻")
                }
                IconButton(onClick = onSettings) { NavigationGlyph("⚙") }
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
                DetailCard("Repository") {
                    DetailValue("Provider", record.app.provider)
                    DetailValue("URL", record.app.canonicalRepositoryUrl)
                    DetailValue("Last checked", record.app.lastReleaseCheckedAt ?: "Never")
                    DetailValue("Release variant", effectiveVariant(record, globalSettings).displayName())
                    DetailValue("ABI", effectiveAbi(record, globalSettings).displayName())
                    DetailValue("APK limit", "${effectiveLimit(record, globalSettings) / MIB} MiB")
                }
            }
            latest?.let { release ->
                item {
                    DetailCard("Latest release") {
                        DetailValue("Release", release.snapshot.releaseName)
                        DetailValue("Tag", release.snapshot.tagName)
                        DetailValue("Resolved commit", release.snapshot.resolvedCommitSha, true)
                        DetailValue("target_commitish (record only)", release.snapshot.targetCommitishRaw)
                        DetailValue("Published", release.snapshot.publishedAt)
                    }
                }
            }
            asset?.let { current ->
                item {
                    DetailCard("Official APK") {
                        DetailValue("Asset", current.assetName)
                        DetailValue("Selection", current.selectionReason)
                        DetailValue("Provider SHA-256", current.providerDigestSha256 ?: "Not supplied", true)
                        DetailValue("Computed SHA-256", current.computedRawSha256 ?: "Not downloaded", true)
                        DetailValue("Package", current.packageName ?: "Not inspected")
                        DetailValue("Version", current.versionName ?: "—")
                        DetailValue(
                            "Installed",
                            current.installedVersionName?.let { "$it (${current.installedVersionCode})" }
                                ?: "Not installed",
                        )
                        DetailValue("Update", updateLabel(current.updateStatus))
                        DetailValue("Signer relation", signerLabel(current.existingInstallStatus))
                        DetailValue("Signer", current.currentSignerSha256 ?: "Not inspected", true)
                        DetailValue("Comparison", current.comparisonEligibility)
                        current.incomparableReason?.let { DetailValue("Reason", it) }
                        current.downloadErrorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
            if (record.app.managementMode == ManagementMode.VERIFICATION.name) {
                val comparison = record.currentComparison
                item {
                    DetailCard("Reproducibility comparison") {
                        DetailValue("Trust", trustLabel(record))
                        Text(
                            "Phase 2D exact reproducibility requires two independent builds of the same commit and recipe. " +
                                "The official APK must match both builds, and both local builds must match each other, " +
                                "within the DEX/native-library byte scope. Full APK inventory and DEX/Manifest/resource " +
                                "semantic results are explanatory evidence; they never promote a raw difference to Reproducible.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (comparison == null) {
                            Text(
                                "Build the fixed release profile from the independently resolved release tag, then compare DEX and native libraries.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Button(
                                enabled = !active &&
                                    record.app.releaseDiscoveryStatus == ReleaseDiscoveryStatus.AVAILABLE.name &&
                                    asset?.downloadStatus == ReferenceDownloadStatus.VERIFIED.name &&
                                    asset.comparisonEligibility != ComparisonEligibility.INCOMPARABLE.name,
                                onClick = onStartComparison,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Build and compare") }
                        } else {
                            DetailValue("Status", comparison.status)
                            DetailValue("Official vs Build A", comparison.outcome)
                            if (comparison.protocolVersion >= 2) {
                                DetailValue("Official vs Build B", comparison.repeatOfficialOutcome)
                                DetailValue("Build A vs Build B", comparison.repeatabilityOutcome)
                            }
                            DetailValue("Expected recipe", comparison.expectedRecipeId)
                            DetailValue("Expected commit", comparison.expectedCommitSha, true)
                            comparison.runnerResolvedCommitSha?.let { DetailValue("Runner commit", it, true) }
                            comparison.repeatRunnerResolvedCommitSha?.let {
                                DetailValue("Repeat Runner commit", it, true)
                            }
                            if (comparison.protocolVersion >= 2) {
                                val buildAJob = runnerJobs[comparison.runnerJobId]?.job
                                val buildBJob = comparison.repeatRunnerJobId?.let(runnerJobs::get)?.job
                                val buildAManifest = buildEnvironmentManifests[comparison.runnerJobId]
                                val buildBManifest = comparison.repeatRunnerJobId?.let(buildEnvironmentManifests::get)
                                val environmentComparison = compareBuildEnvironments(
                                    buildAJob,
                                    buildBJob,
                                    buildAManifest,
                                    buildBManifest,
                                )
                                Text("Build environment evidence", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "This evidence explains build conditions only. It does not change raw APK outcomes, trust, or installation policy.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                buildAManifest?.let { evidence ->
                                    DetailValue(
                                        "Build A environment",
                                        "Java ${evidence.manifest.javaVersion} (${evidence.manifest.javaVendor}), " +
                                            "Gradle ${evidence.manifest.gradleVersion}, SDK API " +
                                            "${evidence.manifest.androidSdkApiLevel}, Build Tools " +
                                            evidence.manifest.buildToolsVersion,
                                    )
                                }
                                buildBManifest?.let { evidence ->
                                    DetailValue(
                                        "Build B environment",
                                        "Java ${evidence.manifest.javaVersion} (${evidence.manifest.javaVendor}), " +
                                            "Gradle ${evidence.manifest.gradleVersion}, SDK API " +
                                            "${evidence.manifest.androidSdkApiLevel}, Build Tools " +
                                            evidence.manifest.buildToolsVersion,
                                    )
                                }
                                buildManifestWarnings[comparison.runnerJobId]?.let { warning ->
                                    DetailValue("Build A Manifest warning", "${warning.code}: ${warning.message}")
                                }
                                comparison.repeatRunnerJobId?.let(buildManifestWarnings::get)?.let { warning ->
                                    DetailValue("Build B Manifest warning", "${warning.code}: ${warning.message}")
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
                                    DetailValue("Dependency comparison", environmentComparison.reason ?: "Not available")
                                }
                                if (
                                    buildAJob?.effectiveRecipeId != buildBJob?.effectiveRecipeId ||
                                    buildAJob?.effectiveVariantName != buildBJob?.effectiveVariantName ||
                                    buildAManifest?.manifest?.javaVersion != buildBManifest?.manifest?.javaVersion
                                ) {
                                    Text(
                                        "Build recipe, variant, or Java differs. Dependency differences are not presented as a cause.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                            comparison.incomparableReason?.let { DetailValue("Reason", it) }
                            comparison.repeatIncomparableReason?.let { DetailValue("Repeat reason", it) }
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
                                DetailValue("APK entries", "${summary.inventoryOutcome} (${summary.entryCount})")
                                DetailValue(
                                    "Entry changes",
                                    "same ${summary.sameCount}, changed ${summary.changedCount}, " +
                                        "added ${summary.addedCount}, missing ${summary.missingCount}",
                                )
                                DetailValue("DEX structure", summary.dexStructuralOutcome)
                                DetailValue("Manifest meaning", summary.manifestSemanticOutcome)
                                DetailValue("Resource table meaning", summary.resourceTableSemanticOutcome)
                                DetailValue("Semantic differences", summary.semanticDifferenceCount.toString())
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
                                summary.reason?.let { DetailValue("Advanced reason", it) }
                            }
                            when (comparison.status) {
                                ComparisonRunStatus.AWAITING_CONFIRMATION.name,
                                ComparisonRunStatus.AWAITING_REPEAT_CONFIRMATION.name -> {
                                    Text(
                                        if (comparison.status == ComparisonRunStatus.AWAITING_REPEAT_CONFIRMATION.name) {
                                            "Build A completed. The repeat Job independently resolved the same commit and fixed " +
                                                "profile. Continuing runs Gradle build scripts again as arbitrary code on the Runner host."
                                        } else {
                                            "The commit and fixed release profile match. Continuing runs Gradle build scripts " +
                                                "as arbitrary code on the Runner host."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                    Button(
                                        enabled = !active,
                                        onClick = { onConfirmComparison(comparison.comparisonRunId) },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(
                                            if (comparison.status == ComparisonRunStatus.AWAITING_REPEAT_CONFIRMATION.name) {
                                                "Confirm repeat build and host RCE risk"
                                            } else {
                                                "Confirm commit and host RCE risk"
                                            },
                                        )
                                    }
                                }
                                ComparisonRunStatus.COMPLETED.name -> Button(
                                    enabled = !active,
                                    onClick = onStartComparison,
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text("Run another comparison") }
                                else -> Button(
                                    enabled = !active,
                                    onClick = { onRefreshComparison(comparison.comparisonRunId) },
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text("Refresh comparison") }
                            }
                        }
                    }
                }
            }
            item {
                DetailCard("Installation") {
                    DetailValue("Source", installationSourceLabel(record.app.installationSource))
                    if (record.app.installationSource == InstallationSource.LOCAL_BUILD.name) {
                        Text(
                            "Only an already-signed local artifact from the current comparison can be installed. " +
                                "Phase 2C does not generate or manage a ReproDroid signing key.",
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
                                "I understand that signer compatibility or reproducibility is not confirmed; Android " +
                                    "PackageInstaller makes the final signing-lineage decision.",
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
                        ) { Text("Allow installs from ReproDroid") }
                    }
                    Button(
                        enabled = !active && canInstall &&
                            canRequestPackageInstalls &&
                            (!warningRequired || installRiskConfirmed),
                        onClick = { onInstall(installRiskConfirmed) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (asset?.updateStatus == UpdateStatus.UPDATE_AVAILABLE.name) "Update" else "Install")
                    }
                    record.latestReleaseInstallAttempt?.let { attempt ->
                        DetailValue("Latest install attempt", attempt.status)
                        attempt.statusMessage?.let { DetailValue("Installer message", it) }
                    }
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppPreferencesScreen(
    record: RegisteredAppRecord,
    globalSettings: GlobalSettingsEntity,
    saving: Boolean,
    onBack: () -> Unit,
    onSave: (AppSettingsUpdate) -> Unit,
) {
    BackHandler(onBack = onBack)
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
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("${record.app.displayName} settings") },
            navigationIcon = {
                IconButton(onClick = onBack) { NavigationGlyph("‹") }
            },
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionTitle(
                    "Registration settings",
                    "These values do not follow later global changes.",
                )
            }
            item {
                DropdownSetting(
                    "Management mode",
                    mode,
                    ManagementMode.entries
                        .filter {
                            !sourceLocked || source != InstallationSource.LOCAL_BUILD ||
                                it == ManagementMode.VERIFICATION
                        }
                        .associateWith(::modeLabel),
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
                    "Installation source",
                    source,
                    InstallationSource.entries
                        .filter { mode == ManagementMode.VERIFICATION || it == InstallationSource.OFFICIAL_RELEASE }
                        .associateWith(::installationSourceLabel),
                    onSelect = {
                        source = it
                        if (it == InstallationSource.LOCAL_BUILD) localRiskConfirmed = false
                    },
                    enabled = !sourceLocked,
                    supportingText = if (sourceLocked) {
                        "Locked while ${record.latestRelease?.selectedAsset?.packageName ?: "the target package"} is installed."
                    } else {
                        "Local build is allowed only in Verification mode and while the package is not installed."
                    },
                )
            }
            if (source == InstallationSource.LOCAL_BUILD && !sourceLocked) {
                item {
                    Row(verticalAlignment = Alignment.Top) {
                        Checkbox(checked = localRiskConfirmed, onCheckedChange = { localRiskConfirmed = it })
                        Text("I accept the local signing and future-update risks.", Modifier.padding(top = 10.dp))
                    }
                }
            }
            item { HorizontalDivider() }
            item { SectionTitle("Inherited defaults", "Use global default follows future changes.") }
            item {
                DropdownSetting(
                    "Release variant",
                    variantChoice,
                    linkedMapOf(
                        "GLOBAL" to
                            "Use global default (${globalSettings.defaultReleaseVariantPreference.displayEnum()})",
                    ) + ReleaseVariantPreference.entries.associate { it.name to it.displayName() },
                    onSelect = { variantChoice = it },
                )
            }
            item {
                DropdownSetting(
                    "Preferred ABI",
                    abiChoice,
                    linkedMapOf(
                        "GLOBAL" to "Use global default (${globalSettings.defaultPreferredAbi.displayEnum()})",
                    ) + PreferredAbi.entries.associate { it.name to it.displayName() },
                    onSelect = { abiChoice = it },
                )
            }
            item {
                DropdownSetting(
                    "APK download limit",
                    limitChoice,
                    linkedMapOf(
                        -1L to "Use global default (${globalSettings.defaultMaxApkSizeBytes / MIB} MiB)",
                    ) + APK_LIMITS.associateWith { "${it / MIB} MiB" },
                    onSelect = { limitChoice = it },
                )
            }
            item {
                Text(
                    "Changing variant or ABI clears the release metadata cache. Refresh before trusting a new selection.",
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
                ) { Text(if (saving) "Saving…" else "Save app settings") }
            }
        }
    }
}

@Composable
private fun ManagedAppIcon(record: RegisteredAppRecord) {
    val context = LocalContext.current
    val assetId = record.latestRelease?.selectedAsset?.releaseAssetId
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
                contentDescription = "${record.app.displayName} icon",
                modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.large),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Text(record.app.displayName.take(2).uppercase(), color = MaterialTheme.colorScheme.primary)
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

@Composable
private fun SettingsScreen(
    settings: GlobalSettingsEntity,
    onUpdate: (GlobalSettingsEntity) -> Unit,
    onOpenRunnerJobs: () -> Unit,
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
            TextButton(onClick = onOpenRunnerJobs, modifier = Modifier.fillMaxWidth()) {
                Text("Open Runner jobs and Phase 1 tools")
            }
        }
        item { SettingInfo("ReproDroid", "0.1.0-alpha01 · Phase 2C") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> DropdownSetting(
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

@Composable
private fun NavigationGlyph(value: String) {
    Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private const val MIB = 1024L * 1024L
private const val MAX_SEMANTIC_DIFFERENCES_IN_UI = 3
private const val MAX_DEPENDENCY_DIFFERENCES_IN_UI = 40
private val APK_LIMITS = listOf(64L * MIB, 128L * MIB, 256L * MIB, 512L * MIB)
