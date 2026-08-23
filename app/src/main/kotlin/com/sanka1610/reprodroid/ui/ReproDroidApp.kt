package com.sanka1610.reprodroid.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.Composable
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
import com.sanka1610.reprodroid.data.local.ManagementMode
import com.sanka1610.reprodroid.data.local.PreferredAbi
import com.sanka1610.reprodroid.data.local.ReferenceDownloadStatus
import com.sanka1610.reprodroid.data.local.RegisteredAppRecord
import com.sanka1610.reprodroid.data.local.ReleaseVariantPreference
import androidx.compose.ui.platform.LocalContext
import java.io.File
import java.util.UUID

private enum class MainDestination { APPS, ADD, SETTINGS }

private val ReproDroidColors: ColorScheme = darkColorScheme(
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

@Composable
fun ReproDroidApp(managedViewModel: ManagedAppsViewModel, jobViewModel: JobViewModel) {
    val apps by managedViewModel.apps.collectAsStateWithLifecycle()
    val preview by managedViewModel.preview.collectAsStateWithLifecycle()
    val message by managedViewModel.message.collectAsStateWithLifecycle()
    val activeAppIds by managedViewModel.activeAppIds.collectAsStateWithLifecycle()
    var destination by rememberSaveable { mutableStateOf(MainDestination.APPS) }
    var selectedAppId by rememberSaveable { mutableStateOf<String?>(null) }
    var settingsAppId by rememberSaveable { mutableStateOf<String?>(null) }
    var showRunnerJobs by rememberSaveable { mutableStateOf(false) }

    MaterialTheme(colorScheme = ReproDroidColors) {
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
                                    saving = app.app.registeredAppId in activeAppIds,
                                    onBack = { settingsAppId = null },
                                    onSave = { variant, abi ->
                                        managedViewModel.updatePreferences(
                                            app.app.registeredAppId,
                                            variant,
                                            abi,
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
                                AppDetailScreen(
                                    record = app,
                                    refreshing = app.app.registeredAppId in activeAppIds,
                                    onBack = { selectedAppId = null },
                                    onRefresh = { managedViewModel.refresh(app.app.registeredAppId) },
                                )
                            }
                        }
                        destination == MainDestination.APPS -> AppsScreen(
                            apps = apps,
                            onSelect = { selectedAppId = it },
                            onSettings = { settingsAppId = it },
                        )
                        destination == MainDestination.ADD -> AddAppScreen(
                            preview = preview,
                            onPreview = managedViewModel::preview,
                            onRegister = { mode ->
                                managedViewModel.register(mode) { registeredAppId ->
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
                        else -> SettingsScreen(onOpenRunnerJobs = { showRunnerJobs = true })
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
    onSettings: (String) -> Unit,
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
                            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                                Text(record.app.displayName, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    latest?.snapshot?.tagName ?: "Release not resolved",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    asset?.downloadStatus ?: record.app.releaseDiscoveryStatus,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = statusColor(asset?.downloadStatus),
                                )
                            }
                            IconButton(onClick = { onSettings(record.app.registeredAppId) }) {
                                NavigationGlyph("⋮")
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
    onPreview: (String) -> Unit,
    onRegister: (ManagementMode) -> Unit,
    onUrlChanged: () -> Unit,
) {
    var repositoryUrl by rememberSaveable { mutableStateOf("https://github.com/MorpheApp/MicroG-RE") }
    var mode by rememberSaveable { mutableStateOf(ManagementMode.VERIFICATION) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Spacer(Modifier.height(16.dp))
            Text("Register an app", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text("Phase 2A · public GitHub Releases", style = MaterialTheme.typography.bodyMedium)
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
            Text("Mode", style = MaterialTheme.typography.titleSmall)
            ManagementMode.entries.forEach { candidate ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = mode == candidate, onClick = { mode = candidate })
                    Column {
                        Text(if (candidate == ManagementMode.VERIFICATION) "Verification" else "Acquisition")
                        Text(
                            if (candidate == ManagementMode.VERIFICATION) "Prepare an official reference APK for Phase 2B comparison."
                            else "Track and acquire the latest official release APK.",
                            style = MaterialTheme.typography.bodySmall,
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
                            enabled = !preview.isLoading,
                            onClick = { onRegister(mode) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (preview.isLoading) "Downloading and verifying…" else "Register and download APK") }
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
    refreshing: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val latest = record.latestRelease
    val asset = latest?.selectedAsset
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(record.app.displayName) },
            navigationIcon = {
                IconButton(onClick = onBack) { NavigationGlyph("‹") }
            },
            actions = {
                IconButton(enabled = !refreshing, onClick = onRefresh) {
                    NavigationGlyph("↻")
                }
            },
        )
        if (refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = {}, label = { Text(record.app.managementMode) })
                    AssistChip(onClick = {}, label = { Text(asset?.downloadStatus ?: record.app.releaseDiscoveryStatus) })
                }
            }
            item {
                DetailCard("Repository") {
                    DetailValue("Provider", record.app.provider)
                    DetailValue("URL", record.app.canonicalRepositoryUrl)
                    DetailValue("Last checked", record.app.lastReleaseCheckedAt ?: "Never")
                    DetailValue("Variant preference", record.app.releaseVariantPreference)
                    DetailValue("ABI preference", record.app.preferredAbi)
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
                        DetailValue("Signer", current.currentSignerSha256 ?: "Not inspected", true)
                        DetailValue("Comparison", current.comparisonEligibility)
                        current.incomparableReason?.let { DetailValue("Reason", it) }
                        current.downloadErrorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
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
    saving: Boolean,
    onBack: () -> Unit,
    onSave: (ReleaseVariantPreference, PreferredAbi) -> Unit,
) {
    BackHandler(onBack = onBack)
    var variant by rememberSaveable(record.app.registeredAppId) {
        mutableStateOf(
            ReleaseVariantPreference.entries.firstOrNull {
                it.name == record.app.releaseVariantPreference
            } ?: ReleaseVariantPreference.RELEASE,
        )
    }
    var abi by rememberSaveable(record.app.registeredAppId) {
        mutableStateOf(
            PreferredAbi.entries.firstOrNull { it.name == record.app.preferredAbi }
                ?: PreferredAbi.ARM64_V8A,
        )
    }
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
                Text("Release variant", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Used to disambiguate APK filenames when a release contains multiple files.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            items(ReleaseVariantPreference.entries) { candidate ->
                PreferenceOption(
                    selected = variant == candidate,
                    label = candidate.name.lowercase().replaceFirstChar(Char::uppercase),
                    onSelect = { variant = candidate },
                )
            }
            item {
                HorizontalDivider()
                Text("Preferred ABI", style = MaterialTheme.typography.titleMedium)
            }
            items(PreferredAbi.entries) { candidate ->
                PreferenceOption(
                    selected = abi == candidate,
                    label = candidate.displayName(),
                    onSelect = { abi = candidate },
                )
            }
            item {
                Text(
                    "Saving clears the release metadata cache. Use Refresh on the app detail screen to apply the new selection. Ambiguous matches fail closed.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    enabled = !saving,
                    onClick = { onSave(variant, abi) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (saving) "Saving…" else "Save app settings") }
            }
        }
    }
}

@Composable
private fun PreferenceOption(selected: Boolean, label: String, onSelect: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = onSelect)
            Text(label)
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
private fun SettingsScreen(onOpenRunnerJobs: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Spacer(Modifier.height(16.dp))
            Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        }
        item { SettingRow("Theme", "Dark") }
        item { SettingRow("Release provider", "Public GitHub Releases") }
        item { SettingRow("Default ABI", "arm64-v8a") }
        item { SettingRow("Maximum APK size", "512 MiB") }
        item { SettingRow("Rate limit", "Unauthenticated GitHub API") }
        item {
            HorizontalDivider()
            TextButton(onClick = onOpenRunnerJobs, modifier = Modifier.fillMaxWidth()) {
                Text("Open Runner jobs and Phase 1 tools")
            }
        }
        item { SettingRow("ReproDroid", "0.1.0-alpha01 · Phase 2A") }
    }
}

@Composable
private fun SettingRow(label: String, value: String) {
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

@Composable
private fun statusColor(status: String?): Color = when (status) {
    ReferenceDownloadStatus.VERIFIED.name -> Color(0xFF78DC9A)
    ReferenceDownloadStatus.FAILED.name -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.secondary
}

@Composable
private fun NavigationGlyph(value: String) {
    Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
