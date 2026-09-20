package com.sanka1610.reprodroid.ui.appdetail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import com.sanka1610.reprodroid.data.local.AppReleaseCheckOverrideEntity
import com.sanka1610.reprodroid.data.local.AppSettingsUpdate
import com.sanka1610.reprodroid.data.local.GlobalSettingsEntity
import com.sanka1610.reprodroid.data.local.InstallationSource
import com.sanka1610.reprodroid.data.local.ManagementMode
import com.sanka1610.reprodroid.data.local.PreferredAbi
import com.sanka1610.reprodroid.data.local.RegisteredAppRecord
import com.sanka1610.reprodroid.data.local.ReleaseCheckSettingsEntity
import com.sanka1610.reprodroid.data.local.ReleaseVariantPreference
import com.sanka1610.reprodroid.data.repository.BuildConfigurationInput
import com.sanka1610.reprodroid.data.repository.BuildConfigurationValidator
import com.sanka1610.reprodroid.ui.*
import com.sanka1610.reprodroid.ui.settings.AccordionSection
import com.sanka1610.reprodroid.ui.settings.AppUpdateSettingsContent
import com.sanka1610.reprodroid.ui.shared.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppPreferencesScreen(
    record: RegisteredAppRecord,
    globalSettings: GlobalSettingsEntity,
    releaseSettings: ReleaseCheckSettingsEntity,
    releaseOverride: AppReleaseCheckOverrideEntity,
    saving: Boolean,
    onBack: () -> Unit,
    onSave: (AppSettingsUpdate) -> Unit,
    onSaveBuildConfiguration: (Long?, BuildConfigurationInput) -> Unit,
    onUpdateReleaseOverride: (AppReleaseCheckOverrideEntity) -> Unit,
) {
    BackHandler(onBack = onBack)
    val appSettingsBackDescription = stringResource(R.string.action_back)
    var mode by rememberSaveable(record.app.registeredAppId) {
        mutableStateOf(enumValue(record.app.managementMode, ManagementMode.VERIFICATION))
    }
    var source by rememberSaveable(record.app.registeredAppId) {
        mutableStateOf(enumValue(record.app.installationSource, InstallationSource.OFFICIAL_RELEASE))
    }
    var abiChoice by rememberSaveable(record.app.registeredAppId) {
        mutableStateOf(if (record.app.useGlobalPreferredAbi) "GLOBAL" else record.app.preferredAbi)
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
    var registrationExpanded by rememberSaveable(record.app.registeredAppId, "registration") {
        mutableStateOf(false)
    }
    var updatesExpanded by rememberSaveable(record.app.registeredAppId, "updates") {
        mutableStateOf(false)
    }
    var defaultsExpanded by rememberSaveable(record.app.registeredAppId, "defaults") {
        mutableStateOf(false)
    }
    var buildExpanded by rememberSaveable(record.app.registeredAppId, "build") {
        mutableStateOf(false)
    }
    val savePreferences = {
        onSave(
            AppSettingsUpdate(
                managementMode = mode,
                installationSource = source,
                releaseVariantPreference = enumValue(
                    record.app.releaseVariantPreference,
                    ReleaseVariantPreference.RELEASE,
                ),
                useGlobalReleaseVariant = record.app.useGlobalReleaseVariant,
                preferredAbi = enumValue(
                    abiChoice.takeUnless { it == "GLOBAL" }
                        ?: globalSettings.defaultPreferredAbi,
                    PreferredAbi.ARM64_V8A,
                ),
                useGlobalPreferredAbi = abiChoice == "GLOBAL",
                maxApkSizeBytes = record.app.maxApkSizeBytes,
                useGlobalMaxApkSize = record.app.useGlobalMaxApkSize,
                localBuildRiskConfirmed = localRiskConfirmed,
            ),
        )
    }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            expandedHeight = 56.dp,
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
                AccordionSection(
                    title = stringResource(R.string.app_settings_registration),
                    expanded = registrationExpanded,
                    onToggle = { registrationExpanded = !registrationExpanded },
                ) {
                    Text(
                        stringResource(R.string.app_settings_registration_body),
                        style = MaterialTheme.typography.bodySmall,
                    )
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
                    DropdownSetting(
                        stringResource(R.string.settings_installation_source),
                        source,
                        InstallationSource.entries
                            .filter {
                                mode == ManagementMode.VERIFICATION ||
                                    it == InstallationSource.OFFICIAL_RELEASE
                            }
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
                    if (source == InstallationSource.LOCAL_BUILD && !sourceLocked) {
                    Row(verticalAlignment = Alignment.Top) {
                        Checkbox(checked = localRiskConfirmed, onCheckedChange = { localRiskConfirmed = it })
                        Text(stringResource(R.string.app_settings_local_risk), Modifier.padding(top = 10.dp))
                    }
                    }
                    Button(
                        enabled = !saving && (source != InstallationSource.LOCAL_BUILD || localRiskConfirmed),
                        onClick = savePreferences,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(
                                if (saving) R.string.app_settings_saving else R.string.app_settings_save,
                            ),
                        )
                    }
                }
            }
            item {
                AccordionSection(
                    title = stringResource(R.string.settings_updates),
                    expanded = updatesExpanded,
                    onToggle = { updatesExpanded = !updatesExpanded },
                ) {
                    AppUpdateSettingsContent(
                        global = releaseSettings,
                        override = releaseOverride,
                        showDividers = globalSettings.showSettingsDividers,
                        onUpdate = onUpdateReleaseOverride,
                    )
                }
            }
            item {
                AccordionSection(
                    title = stringResource(R.string.app_settings_inherited),
                    expanded = defaultsExpanded,
                    onToggle = { defaultsExpanded = !defaultsExpanded },
                ) {
                    Text(
                        stringResource(R.string.app_settings_inherited_body),
                        style = MaterialTheme.typography.bodySmall,
                    )
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
                    Text(
                        stringResource(R.string.app_settings_selection_change),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Button(
                        enabled = !saving && (source != InstallationSource.LOCAL_BUILD || localRiskConfirmed),
                        onClick = savePreferences,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(
                                if (saving) R.string.app_settings_saving else R.string.app_settings_save,
                            ),
                        )
                    }
                }
            }
            item {
                AccordionSection(
                    title = stringResource(R.string.app_settings_build_configuration),
                    expanded = buildExpanded,
                    onToggle = { buildExpanded = !buildExpanded },
                ) {
                    Text(
                        stringResource(R.string.app_settings_build_configuration_body),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    BuildSettingField(stringResource(R.string.build_root), buildRoot, { buildRoot = it }, ". or relative path")
                    BuildSettingField(stringResource(R.string.build_module_path), modulePath, { modulePath = it }, ":app")
                    BuildSettingField(stringResource(R.string.build_variant), buildVariant, { buildVariant = it }, "release")
                    OutlinedTextField(
                        value = buildTasks,
                        onValueChange = { buildTasks = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.build_tasks)) },
                        supportingText = { Text(":app:assembleRelease") },
                        minLines = 2,
                    )
                    BuildSettingField(stringResource(R.string.build_java_major), javaMajor, { javaMajor = it }, "21")
                    BuildSettingField(stringResource(R.string.build_gradle_version), gradleVersion, { gradleVersion = it }, "9.1.0")
                    BuildSettingField("compileSdk", compileSdk, { compileSdk = it }, "36")
                    BuildSettingField(stringResource(R.string.build_tools_version), buildToolsVersion, { buildToolsVersion = it }, "36.0.0")
                    BuildSettingField(stringResource(R.string.build_ndk_version), ndkVersion, { ndkVersion = it }, "")
                    BuildSettingField(stringResource(R.string.build_cmake_version), cmakeVersion, { cmakeVersion = it }, "")
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
