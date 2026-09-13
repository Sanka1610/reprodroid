package com.sanka1610.reprodroid.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.sanka1610.reprodroid.data.connection.RunnerConnectionIssue
import com.sanka1610.reprodroid.data.local.RunnerConnectionEntity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanka1610.reprodroid.R
import com.sanka1610.reprodroid.data.local.AppGroupEntity
import com.sanka1610.reprodroid.data.local.AppMetadataUpdate
import com.sanka1610.reprodroid.data.local.AppReleaseCheckOverrideEntity
import com.sanka1610.reprodroid.data.local.AppTrackingState
import com.sanka1610.reprodroid.data.local.ComparisonRunEntity
import com.sanka1610.reprodroid.data.local.GlobalSettingsEntity
import com.sanka1610.reprodroid.data.local.InstallationSource
import com.sanka1610.reprodroid.data.local.JobRecord
import com.sanka1610.reprodroid.data.local.ManagementMode
import com.sanka1610.reprodroid.data.local.PreferredAbi
import com.sanka1610.reprodroid.data.local.RegisteredAppRecord
import com.sanka1610.reprodroid.data.local.ReleaseCandidateEntity
import com.sanka1610.reprodroid.data.local.ReleaseCheckBatteryPolicy
import com.sanka1610.reprodroid.data.local.ReleaseCheckChannel
import com.sanka1610.reprodroid.data.local.ReleaseCheckNetworkPolicy
import com.sanka1610.reprodroid.data.local.ReleaseCheckScheduleMode
import com.sanka1610.reprodroid.data.local.ReleaseCheckSettingsEntity
import com.sanka1610.reprodroid.data.local.ReleaseScheduleStateEntity
import com.sanka1610.reprodroid.data.local.ReleaseVariantPreference
import com.sanka1610.reprodroid.data.local.ThemeMode
import com.sanka1610.reprodroid.data.license.LicenseAssetStore
import com.sanka1610.reprodroid.data.license.LicenseDocument
import com.sanka1610.reprodroid.data.log.AppLogExportManager
import com.sanka1610.reprodroid.data.log.AppLogExportResult
import com.sanka1610.reprodroid.data.repository.AppDeletionPreview
import com.sanka1610.reprodroid.data.connection.ManualPairingPayloadParser
import com.sanka1610.reprodroid.data.connection.RunnerConnectionPhase
import com.sanka1610.reprodroid.data.connection.RunnerConnectionStatus
import com.sanka1610.reprodroid.ui.navigation.ReproDroidBackContext
import com.sanka1610.reprodroid.ui.navigation.ReproDroidRoute
import com.sanka1610.reprodroid.ui.navigation.backDestination
import com.sanka1610.reprodroid.ui.navigation.missingAppDestination
import com.sanka1610.reprodroid.ui.theme.ReproDroidTheme

@Composable
fun ReproDroidApp(
    managedViewModel: ManagedAppsViewModel,
    jobViewModel: JobViewModel,
    initialRoute: String? = null,
) {
    val apps by managedViewModel.apps.collectAsStateWithLifecycle()
    val inactiveApps by managedViewModel.inactiveApps.collectAsStateWithLifecycle()
    val appCatalogLoaded by managedViewModel.appCatalogLoaded.collectAsStateWithLifecycle()
    val groups by managedViewModel.groups.collectAsStateWithLifecycle()
    val globalSettings by managedViewModel.settings.collectAsStateWithLifecycle()
    val preview by managedViewModel.preview.collectAsStateWithLifecycle()
    val sourceEditPreview by managedViewModel.sourceEditPreview.collectAsStateWithLifecycle()
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
    val appLogExport by managedViewModel.appLogExport.collectAsStateWithLifecycle()
    val runnerCleanupPreview by managedViewModel.runnerCleanupPreview.collectAsStateWithLifecycle()
    val runnerCleanupRun by managedViewModel.runnerCleanupRun.collectAsStateWithLifecycle()
    val toolchainState by managedViewModel.toolchainState.collectAsStateWithLifecycle()
    val deletionPreview by managedViewModel.deletionPreview.collectAsStateWithLifecycle()
    val deletionResult by managedViewModel.deletionResult.collectAsStateWithLifecycle()
    val releaseCheckSettingsState by managedViewModel.releaseCheckSettings.collectAsStateWithLifecycle()
    val releaseCheckOverrides by managedViewModel.releaseCheckOverrides.collectAsStateWithLifecycle()
    val releaseScheduleStates by managedViewModel.releaseScheduleStates.collectAsStateWithLifecycle()
    val releaseCandidates by managedViewModel.releaseCandidates.collectAsStateWithLifecycle()
    val runnerConnectionStatus by managedViewModel.runnerConnectionStatus.collectAsStateWithLifecycle()
    val runnerConnections by managedViewModel.runnerConnections.collectAsStateWithLifecycle()
    val releaseCheckSettings = releaseCheckSettingsState
        ?: ReleaseCheckSettingsEntity(updatedAt = java.time.Instant.EPOCH.toString())

    var encodedRoute by rememberSaveable(initialRoute) {
        mutableStateOf(ReproDroidRoute.parse(initialRoute).encode())
    }
    LaunchedEffect(initialRoute) {
        if (initialRoute != null) encodedRoute = ReproDroidRoute.parse(initialRoute).encode()
    }
    val route = remember(encodedRoute) { ReproDroidRoute.parse(encodedRoute) }
    val allApps = remember(apps, inactiveApps) { apps + inactiveApps }
    val routeApp = route.appId?.let { id -> allApps.firstOrNull { it.app.registeredAppId == id } }
    val comparisonRouteApp = (route as? ReproDroidRoute.Comparison)?.let { comparisonRoute ->
        allApps.firstOrNull { record ->
            record.comparisons.any { it.comparisonRunId == comparisonRoute.comparisonRunId }
        }
    }
    var removalTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    var uninstallTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    var uninstallStopsTracking by rememberSaveable { mutableStateOf(false) }
    var addRepositoryUrl by rememberSaveable { mutableStateOf("") }
    var addModeName by rememberSaveable(globalSettings.updatedAt) {
        mutableStateOf(globalSettings.defaultManagementMode)
    }
    var addInstallationSourceName by rememberSaveable(globalSettings.updatedAt) {
        mutableStateOf(globalSettings.defaultInstallationSource)
    }
    var addRiskConfirmed by rememberSaveable { mutableStateOf(false) }
    var addSeparateTarget by rememberSaveable { mutableStateOf(false) }
    var inactiveReturnRoute by rememberSaveable { mutableStateOf(ReproDroidRoute.InactiveApps.encode()) }
    var updateSettingsReturnRoute by rememberSaveable { mutableStateOf(ReproDroidRoute.Settings.encode()) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val rootScope = rememberCoroutineScope()

    fun navigate(destination: ReproDroidRoute) {
        encodedRoute = destination.encode()
    }

    val backContext = ReproDroidBackContext(
        currentAppIsInactive = routeApp?.app?.trackingState == AppTrackingState.INACTIVE.name,
        inactiveReturnRoute = inactiveReturnRoute,
        comparisonOwnerAppId = comparisonRouteApp?.app?.registeredAppId,
    )

    BackHandler(enabled = drawerState.isOpen) { rootScope.launch { drawerState.close() } }
    BackHandler(enabled = !route.isRoot && !drawerState.isOpen) { navigate(backDestination(route, backContext)) }

    LaunchedEffect(preview.repository, route) {
        if (route == ReproDroidRoute.AddSource && preview.repository != null) {
            navigate(ReproDroidRoute.AddAnalysis)
        }
    }

    val uninstallLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        uninstallTargetId?.let { appId ->
            val onConfirmed = {
                uninstallTargetId = null
                removalTargetId = null
                if (uninstallStopsTracking) {
                    navigate(ReproDroidRoute.Apps)
                } else {
                    navigate(ReproDroidRoute.AppInformation(appId))
                }
                uninstallStopsTracking = false
            }
            if (uninstallStopsTracking) {
                managedViewModel.stopTrackingAfterConfirmedUninstall(appId, onConfirmed)
            } else {
                managedViewModel.confirmUninstall(appId, onConfirmed)
            }
        }
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    val appLogDestinationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(AppLogExportManager.MIME_TYPE),
    ) { destination -> destination?.let(managedViewModel::exportAppLogs) }

    LaunchedEffect(route, routeApp?.app?.trackingState, appCatalogLoaded) {
        val missingDestination = missingAppDestination(
            current = route,
            appCatalogLoaded = appCatalogLoaded,
            appRecordPresent = routeApp != null,
        )
        if (missingDestination != null) {
            navigate(missingDestination)
        } else if (
            routeApp?.app?.trackingState == AppTrackingState.INACTIVE.name &&
            (route is ReproDroidRoute.AppEdit ||
                route is ReproDroidRoute.AppSettings ||
                route is ReproDroidRoute.AppTechnical)
        ) {
            navigate(ReproDroidRoute.AppInformation(routeApp.app.registeredAppId))
        }
    }

    val rootContent: @Composable (ReproDroidRoute) -> Unit = { destination ->
        when (destination) {
            ReproDroidRoute.Apps -> UiRAppsScreen(
                apps = apps,
                groups = groups,
                onSelect = { navigate(ReproDroidRoute.AppInformation(it)) },
                onAdd = { navigate(ReproDroidRoute.AddSource) },
                onCreateGroup = managedViewModel::createGroup,
                onRenameGroup = managedViewModel::renameGroup,
                onReorderGroups = managedViewModel::reorderGroups,
                onDeleteGroup = managedViewModel::deleteGroup,
            )
            ReproDroidRoute.AddSource -> UiRAddFlowScreen(
                route = destination,
                preview = preview,
                repositoryUrl = addRepositoryUrl,
                onRepositoryUrlChange = {
                    addRepositoryUrl = it
                    managedViewModel.clearPreview()
                },
                mode = ManagementMode.entries.firstOrNull { it.name == addModeName }
                    ?: ManagementMode.VERIFICATION,
                onModeChange = {
                    addModeName = it.name
                    if (it == ManagementMode.ACQUISITION) {
                        addInstallationSourceName = InstallationSource.OFFICIAL_RELEASE.name
                        addRiskConfirmed = false
                    }
                },
                installationSource = InstallationSource.entries.firstOrNull {
                    it.name == addInstallationSourceName
                } ?: InstallationSource.OFFICIAL_RELEASE,
                onInstallationSourceChange = {
                    addInstallationSourceName = it.name
                    if (it != InstallationSource.LOCAL_BUILD) addRiskConfirmed = false
                },
                localRiskConfirmed = addRiskConfirmed,
                onLocalRiskConfirmedChange = { addRiskConfirmed = it },
                separateManagementTarget = addSeparateTarget,
                onSeparateManagementTargetChange = { addSeparateTarget = it },
                onPreview = managedViewModel::preview,
                onCancelPreview = managedViewModel::clearPreview,
                onNavigate = ::navigate,
                onResume = { id ->
                    managedViewModel.resumeTracking(id) {
                        managedViewModel.clearPreview()
                        navigate(ReproDroidRoute.AppInformation(id))
                    }
                },
                onRegister = { mode, source, confirmed, separateTarget ->
                    managedViewModel.register(mode, source, confirmed, separateTarget) { id ->
                        addRepositoryUrl = ""
                        addRiskConfirmed = false
                        addSeparateTarget = false
                        navigate(ReproDroidRoute.AppInformation(id))
                    }
                },
            )
            ReproDroidRoute.Settings -> UiRSettingsScreen(
                settings = globalSettings,
                onUpdate = managedViewModel::updateGlobalSettings,
                onNavigate = { target ->
                    if (target == ReproDroidRoute.UpdateSettings) {
                        updateSettingsReturnRoute = ReproDroidRoute.Settings.encode()
                    }
                    navigate(target)
                },
            )
            else -> Unit
        }
    }

    val showRootShell = route.isRoot || route.isAddFlow || route == ReproDroidRoute.GitHubStarsImport
    ReproDroidTheme(globalSettings) {
        Surface(Modifier.fillMaxSize()) {
            ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = showRootShell,
                drawerContent = {
                    ModalDrawerSheet {
                        Text(
                            stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(24.dp),
                        )
                        RootDrawerItem(
                            label = stringResource(R.string.nav_apps),
                            icon = Icons.Default.Home,
                            selected = route == ReproDroidRoute.Apps,
                        ) {
                            navigate(ReproDroidRoute.Apps)
                            rootScope.launch { drawerState.close() }
                        }
                        RootDrawerItem(
                            label = stringResource(R.string.nav_add),
                            icon = Icons.Default.Add,
                            selected = route.isAddFlow || route == ReproDroidRoute.GitHubStarsImport,
                        ) {
                            navigate(ReproDroidRoute.AddSource)
                            rootScope.launch { drawerState.close() }
                        }
                        RootDrawerItem(
                            label = stringResource(R.string.nav_settings),
                            icon = Icons.Default.Settings,
                            selected = route == ReproDroidRoute.Settings,
                        ) {
                            navigate(ReproDroidRoute.Settings)
                            rootScope.launch { drawerState.close() }
                        }
                    }
                },
            ) {
                Scaffold(
                topBar = {
                    if (showRootShell) {
                        RootTopBar(
                            route = route,
                            onOpenDrawer = { rootScope.launch { drawerState.open() } },
                            onOpenSettings = { navigate(ReproDroidRoute.Settings) },
                        )
                    }
                },
                floatingActionButton = {
                    if (route == ReproDroidRoute.Apps) {
                        FloatingActionButton(onClick = { navigate(ReproDroidRoute.AddSource) }) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_add_app))
                        }
                    }
                },
                bottomBar = {
                    when {
                        route.isRoot || route.isAddFlow || route == ReproDroidRoute.GitHubStarsImport ->
                            RootPageIndicator(route, ::navigate)
                        route.appId != null && routeApp != null -> AppActionBar(
                            route = route,
                            active = routeApp.app.trackingState == AppTrackingState.ACTIVE.name,
                            onNavigate = ::navigate,
                            onRemove = { removalTargetId = routeApp.app.registeredAppId },
                        )
                    }
                },
                ) { contentPadding ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(contentPadding),
                ) {
                    message?.let { UiRMessageBanner(it, managedViewModel::clearMessage) }
                    if (route.isRoot) {
                        AnimatedContent(
                            targetState = route,
                            transitionSpec = {
                                val forward = rootPageIndex(targetState) >= rootPageIndex(initialState)
                                slideInHorizontally { width -> if (forward) width else -width } togetherWith
                                    slideOutHorizontally { width -> if (forward) -width else width }
                            },
                            label = "root-route",
                        ) { destination ->
                            rootContent(destination)
                        }
                    } else {
                        when (route) {
                        ReproDroidRoute.Apps -> UiRAppsScreen(
                            apps = apps,
                            groups = groups,
                            onSelect = { navigate(ReproDroidRoute.AppInformation(it)) },
                            onAdd = { navigate(ReproDroidRoute.AddSource) },
                            onCreateGroup = managedViewModel::createGroup,
                            onRenameGroup = managedViewModel::renameGroup,
                            onReorderGroups = managedViewModel::reorderGroups,
                            onDeleteGroup = managedViewModel::deleteGroup,
                        )
                        ReproDroidRoute.InactiveApps -> InactiveAppsScreen(
                            apps = inactiveApps,
                            allowCompleteDeletion = false,
                            onBack = { navigate(ReproDroidRoute.Settings) },
                            onOpen = {
                                inactiveReturnRoute = ReproDroidRoute.InactiveApps.encode()
                                navigate(ReproDroidRoute.AppInformation(it))
                            },
                            onResume = { id -> managedViewModel.resumeTracking(id) },
                            onPreviewDelete = managedViewModel::previewCompleteDeletion,
                            deletionPreview = deletionPreview,
                            deletionResult = deletionResult,
                            onDelete = {
                                managedViewModel.executeCompleteDeletion {
                                    navigate(ReproDroidRoute.InactiveApps)
                                }
                            },
                            onDismissDelete = managedViewModel::clearDeletionState,
                        )
                        ReproDroidRoute.AddSource,
                        ReproDroidRoute.AddAnalysis,
                        ReproDroidRoute.AddOptions,
                        ReproDroidRoute.AddConfirm,
                        -> UiRAddFlowScreen(
                            route = route,
                            preview = preview,
                            repositoryUrl = addRepositoryUrl,
                            onRepositoryUrlChange = {
                                addRepositoryUrl = it
                                managedViewModel.clearPreview()
                            },
                            mode = ManagementMode.entries.firstOrNull { it.name == addModeName }
                                ?: ManagementMode.VERIFICATION,
                            onModeChange = {
                                addModeName = it.name
                                if (it == ManagementMode.ACQUISITION) {
                                    addInstallationSourceName = InstallationSource.OFFICIAL_RELEASE.name
                                    addRiskConfirmed = false
                                }
                            },
                            installationSource = InstallationSource.entries.firstOrNull {
                                it.name == addInstallationSourceName
                            } ?: InstallationSource.OFFICIAL_RELEASE,
                            onInstallationSourceChange = {
                                addInstallationSourceName = it.name
                                if (it != InstallationSource.LOCAL_BUILD) addRiskConfirmed = false
                            },
                            localRiskConfirmed = addRiskConfirmed,
                            onLocalRiskConfirmedChange = { addRiskConfirmed = it },
                            separateManagementTarget = addSeparateTarget,
                            onSeparateManagementTargetChange = { addSeparateTarget = it },
                            onPreview = managedViewModel::preview,
                            onCancelPreview = managedViewModel::clearPreview,
                            onNavigate = ::navigate,
                            onResume = { id ->
                                managedViewModel.resumeTracking(id) {
                                    managedViewModel.clearPreview()
                                    navigate(ReproDroidRoute.AppInformation(id))
                                }
                            },
                            onRegister = { mode, source, confirmed, separateTarget ->
                                managedViewModel.register(mode, source, confirmed, separateTarget) { id ->
                                    addRepositoryUrl = ""
                                    addRiskConfirmed = false
                                    addSeparateTarget = false
                                    navigate(ReproDroidRoute.AppInformation(id))
                                }
                            },
                        )
                        ReproDroidRoute.Settings -> UiRSettingsScreen(
                            settings = globalSettings,
                            onUpdate = managedViewModel::updateGlobalSettings,
                            onNavigate = { destination ->
                                if (destination == ReproDroidRoute.UpdateSettings) {
                                    updateSettingsReturnRoute = ReproDroidRoute.Settings.encode()
                                }
                                navigate(destination)
                            },
                        )
                        ReproDroidRoute.DataManagement -> DataManagementScreen(
                            onBack = { navigate(ReproDroidRoute.Settings) },
                            onStorage = {
                                managedViewModel.refreshAndroidStorage()
                                navigate(ReproDroidRoute.DataStorage)
                            },
                            onInactive = { navigate(ReproDroidRoute.DataInactive) },
                            onRunner = { navigate(ReproDroidRoute.RunnerSettings) },
                            onLogExport = { navigate(ReproDroidRoute.LogExport) },
                        )
                        ReproDroidRoute.DataInactive -> InactiveAppsScreen(
                            apps = inactiveApps,
                            allowCompleteDeletion = true,
                            onBack = {
                                managedViewModel.clearDeletionState()
                                navigate(ReproDroidRoute.DataManagement)
                            },
                            onOpen = {
                                inactiveReturnRoute = ReproDroidRoute.DataInactive.encode()
                                navigate(ReproDroidRoute.AppInformation(it))
                            },
                            onResume = { id -> managedViewModel.resumeTracking(id) },
                            onPreviewDelete = managedViewModel::previewCompleteDeletion,
                            deletionPreview = deletionPreview,
                            deletionResult = deletionResult,
                            onDelete = { managedViewModel.executeCompleteDeletion() },
                            onDismissDelete = managedViewModel::clearDeletionState,
                        )
                        ReproDroidRoute.DataStorage -> StorageScreen(
                            apps = allApps,
                            settings = globalSettings,
                            androidSummary = androidStorageSummary,
                            runnerState = runnerStorageState,
                            cleanupPreview = androidCleanupPreview,
                            busy = storageBusy,
                            auditExport = auditExport,
                            runnerCleanupPreview = runnerCleanupPreview,
                            runnerCleanupRun = runnerCleanupRun,
                            onBack = {
                                managedViewModel.clearAndroidCleanupPreview()
                                navigate(ReproDroidRoute.DataManagement)
                            },
                            onUpdate = managedViewModel::updateGlobalSettings,
                            onRefresh = managedViewModel::refreshAndroidStorage,
                            onPreviewCleanup = managedViewModel::previewAndroidCleanup,
                            onExecuteCleanup = managedViewModel::executeAndroidCleanup,
                            onStageAudit = managedViewModel::stageAuditExport,
                            onCopyAudit = managedViewModel::copyAuditExport,
                            onPreviewRunnerCleanup = managedViewModel::previewRunnerCleanup,
                            onExecuteRunnerCleanup = managedViewModel::executeRunnerCleanup,
                            showAndroid = true,
                            showRunner = false,
                        )
                        ReproDroidRoute.RunnerSettings -> RunnerSettingsScreen(
                            onBack = { navigate(ReproDroidRoute.Settings) },
                            onStorage = {
                                managedViewModel.refreshRunnerStorage()
                                navigate(ReproDroidRoute.RunnerStorage)
                            },
                            onJobs = { navigate(ReproDroidRoute.Jobs) },
                            onToolchains = {
                                managedViewModel.refreshToolchains()
                                navigate(ReproDroidRoute.Toolchains)
                            },
                            onAuthentication = { navigate(ReproDroidRoute.Authentication) },
                        )
                        ReproDroidRoute.RunnerStorage -> StorageScreen(
                            apps = allApps,
                            settings = globalSettings,
                            androidSummary = androidStorageSummary,
                            runnerState = runnerStorageState,
                            cleanupPreview = androidCleanupPreview,
                            busy = storageBusy,
                            auditExport = auditExport,
                            runnerCleanupPreview = runnerCleanupPreview,
                            runnerCleanupRun = runnerCleanupRun,
                            onBack = {
                                managedViewModel.clearAndroidCleanupPreview()
                                navigate(ReproDroidRoute.RunnerSettings)
                            },
                            onUpdate = managedViewModel::updateGlobalSettings,
                            onRefresh = managedViewModel::refreshRunnerStorage,
                            onPreviewCleanup = managedViewModel::previewAndroidCleanup,
                            onExecuteCleanup = managedViewModel::executeAndroidCleanup,
                            onStageAudit = managedViewModel::stageAuditExport,
                            onCopyAudit = managedViewModel::copyAuditExport,
                            onPreviewRunnerCleanup = managedViewModel::previewRunnerCleanup,
                            onExecuteRunnerCleanup = managedViewModel::executeRunnerCleanup,
                            showAndroid = false,
                            showRunner = true,
                        )
                        ReproDroidRoute.Toolchains -> ToolchainScreen(
                            state = toolchainState,
                            onBack = { navigate(ReproDroidRoute.RunnerSettings) },
                            onRefresh = managedViewModel::refreshToolchains,
                            onInstall = managedViewModel::installToolchains,
                            onCancel = managedViewModel::cancelToolchainInstallation,
                            onPreviewRemoval = managedViewModel::previewToolchainRemoval,
                            onExecuteRemoval = managedViewModel::executeToolchainRemoval,
                        )
                        ReproDroidRoute.Jobs -> BackScaffoldTitle(
                            title = stringResource(R.string.settings_jobs),
                            onBack = { navigate(ReproDroidRoute.RunnerSettings) },
                        ) { JobScreen(jobViewModel) }
                        ReproDroidRoute.UpdateSettings -> ReleaseUpdateSettingsScreen(
                            settings = releaseCheckSettings,
                            apps = apps,
                            overrides = releaseCheckOverrides,
                            schedules = releaseScheduleStates,
                            candidates = releaseCandidates,
                            onUpdateSettings = managedViewModel::updateReleaseCheckSettings,
                            onUpdateOverride = managedViewModel::updateReleaseCheckOverride,
                            onCheckNow = managedViewModel::checkReleaseMetadataNow,
                            onOpenCandidate = { candidate ->
                                managedViewModel.openReleaseCandidate(
                                    candidate.registeredAppId,
                                    candidate.candidateId,
                                ) {
                                    navigate(ReproDroidRoute.AppTechnical(candidate.registeredAppId))
                                }
                            },
                            onRequestNotifications = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                            onBack = {
                                navigate(ReproDroidRoute.parse(updateSettingsReturnRoute))
                            },
                        )
                        ReproDroidRoute.Authentication -> RunnerAuthenticationScreen(
                            status = runnerConnectionStatus,
                            connections = runnerConnections,
                            onPair = managedViewModel::pairRunner,
                            onRefresh = managedViewModel::refreshRunnerConnection,
                            onCancelPending = managedViewModel::cancelRunnerPairing,
                            onSelfRevoke = managedViewModel::selfRevokeRunner,
                            onLocalDelete = managedViewModel::deleteLocalRunnerConnection,
                            onBack = { navigate(ReproDroidRoute.RunnerSettings) },
                        )
                        ReproDroidRoute.LogExport -> LogExportScreen(
                            result = appLogExport,
                            busy = storageBusy,
                            onExport = {
                                appLogDestinationLauncher.launch(AppLogExportManager.SUGGESTED_FILE_NAME)
                            },
                            onClearResult = managedViewModel::clearAppLogExport,
                            onBack = { navigate(ReproDroidRoute.Settings) },
                        )
                        ReproDroidRoute.Licenses -> LicenseScreen(
                            onBack = { navigate(ReproDroidRoute.Settings) },
                        )
                        ReproDroidRoute.GitHubStarsImport -> PlannedFeatureScreen(
                            title = stringResource(R.string.github_stars_title),
                            body = stringResource(R.string.github_stars_body),
                            onBack = { navigate(ReproDroidRoute.AddSource) },
                        )
                        is ReproDroidRoute.AppInformation -> routeApp?.let { record ->
                            AppInformationScreen(
                                record = record,
                                active = record.app.registeredAppId in activeAppIds,
                                runnerJobs = runnerJobs.associateBy { it.job.jobId },
                                candidates = releaseCandidates.filter {
                                    it.registeredAppId == record.app.registeredAppId
                                },
                                schedule = releaseScheduleStates.firstOrNull {
                                    it.registeredAppId == record.app.registeredAppId
                                },
                                onBack = { navigate(backDestination(route)) },
                                onRefresh = { managedViewModel.refresh(record.app.registeredAppId) },
                                onCheckMetadata = {
                                    managedViewModel.checkReleaseMetadataNow(record.app.registeredAppId)
                                },
                                onOpenCandidate = { candidate ->
                                    managedViewModel.openReleaseCandidate(
                                        candidate.registeredAppId,
                                        candidate.candidateId,
                                    ) {
                                        navigate(ReproDroidRoute.AppTechnical(candidate.registeredAppId))
                                    }
                                },
                                onTechnical = {
                                    navigate(ReproDroidRoute.AppTechnical(record.app.registeredAppId))
                                },
                                onComparison = { comparisonId ->
                                    navigate(ReproDroidRoute.Comparison(comparisonId))
                                },
                                onResume = {
                                    managedViewModel.resumeTracking(record.app.registeredAppId) {
                                        navigate(ReproDroidRoute.AppInformation(record.app.registeredAppId))
                                    }
                                },
                            )
                        } ?: MissingRecordScreen { navigate(ReproDroidRoute.Apps) }
                        is ReproDroidRoute.AppEdit -> routeApp?.let { record ->
                            AppEditScreen(
                                record = record,
                                groups = groups,
                                sourcePreview = sourceEditPreview,
                                saving = record.app.registeredAppId in activeAppIds,
                                onBack = { navigate(ReproDroidRoute.AppInformation(record.app.registeredAppId)) },
                                onSave = { update ->
                                    managedViewModel.updateMetadata(record.app.registeredAppId, update) {
                                        navigate(ReproDroidRoute.AppInformation(record.app.registeredAppId))
                                    }
                                },
                                onInspectSource = { url ->
                                    managedViewModel.previewSourceEdit(
                                        record.app.registeredAppId,
                                        record.app.updatedAt,
                                        url,
                                    )
                                },
                                onApplySource = {
                                    managedViewModel.applySourceEdit(record.app.registeredAppId) {
                                        navigate(ReproDroidRoute.AppInformation(record.app.registeredAppId))
                                    }
                                },
                                onClearSource = managedViewModel::clearSourceEditPreview,
                                onRegisterSeparately = {
                                    addRepositoryUrl = sourceEditPreview.requestedUrl.orEmpty()
                                    managedViewModel.clearSourceEditPreview()
                                    managedViewModel.clearPreview()
                                    navigate(ReproDroidRoute.AddSource)
                                },
                            )
                        } ?: MissingRecordScreen { navigate(ReproDroidRoute.Apps) }
                        is ReproDroidRoute.AppSettings -> routeApp?.let { record ->
                            AppPreferencesScreen(
                                record = record,
                                globalSettings = globalSettings,
                                saving = record.app.registeredAppId in activeAppIds,
                                onBack = { navigate(ReproDroidRoute.AppInformation(record.app.registeredAppId)) },
                                onSave = { update ->
                                    managedViewModel.updatePreferences(record.app.registeredAppId, update) {
                                        navigate(ReproDroidRoute.AppInformation(record.app.registeredAppId))
                                    }
                                },
                                onSaveBuildConfiguration = { revision, input ->
                                    managedViewModel.saveBuildConfiguration(record.app.registeredAppId, revision, input)
                                },
                                onOpenUpdateSettings = {
                                    updateSettingsReturnRoute = ReproDroidRoute.AppSettings(
                                        record.app.registeredAppId,
                                    ).encode()
                                    navigate(ReproDroidRoute.UpdateSettings)
                                },
                            )
                        } ?: MissingRecordScreen { navigate(ReproDroidRoute.Apps) }
                        is ReproDroidRoute.AppTechnical -> routeApp?.let { record ->
                            AppDetailScreen(
                                record = record,
                                globalSettings = globalSettings,
                                active = record.app.registeredAppId in activeAppIds,
                                onBack = { navigate(ReproDroidRoute.AppInformation(record.app.registeredAppId)) },
                                onSettings = { navigate(ReproDroidRoute.AppSettings(record.app.registeredAppId)) },
                                onRefresh = { managedViewModel.refresh(record.app.registeredAppId) },
                                onSelectReleaseAsset = { snapshotId, assetId, saveCondition ->
                                    managedViewModel.selectReleaseAsset(
                                        record.app.registeredAppId,
                                        snapshotId,
                                        assetId,
                                        saveCondition,
                                    )
                                },
                                onClearSavedAssetSelection = {
                                    managedViewModel.clearSavedAssetSelection(record.app.registeredAppId)
                                },
                                onInstall = { confirmed -> managedViewModel.install(record.app.registeredAppId, confirmed) },
                                onStartComparison = { managedViewModel.startComparison(record.app.registeredAppId) },
                                onRefreshComparison = { managedViewModel.refreshComparison(record.app.registeredAppId, it) },
                                onConfirmComparison = { managedViewModel.confirmComparison(record.app.registeredAppId, it) },
                                onContinueComparisonSourceScan = {
                                    managedViewModel.continueComparisonSourceScan(record.app.registeredAppId, it)
                                },
                                runnerJobs = runnerJobs.associateBy { it.job.jobId },
                                buildEnvironmentManifests = buildEnvironmentManifests.associateBy { it.manifest.jobId },
                                buildManifestWarnings = buildManifestWarnings,
                                sourceScanWarnings = sourceScanWarnings,
                                sandboxWarnings = sandboxWarnings,
                                availability = availability,
                            )
                        } ?: MissingRecordScreen { navigate(ReproDroidRoute.Apps) }
                        is ReproDroidRoute.Comparison -> comparisonRouteApp?.let { record ->
                            val comparison = record.comparisons.firstOrNull {
                                it.comparisonRunId == route.comparisonRunId
                            }
                            if (comparison == null) {
                                MissingRecordScreen { navigate(ReproDroidRoute.Apps) }
                            } else {
                                ComparisonEvidenceScreen(
                                    comparison = comparison,
                                    onBack = {
                                        navigate(ReproDroidRoute.AppInformation(record.app.registeredAppId))
                                    },
                                )
                            }
                        } ?: MissingRecordScreen { navigate(ReproDroidRoute.Apps) }
                        }
                    }
                }
                }
            }
        }
    }

    removalTargetId?.let { appId ->
        allApps.firstOrNull { it.app.registeredAppId == appId }?.let { record ->
            RemoveTrackingDialog(
                record = record,
                otherPackageReferenceCount = knownPackageName(record)?.let { packageName ->
                    allApps.count { candidate ->
                        candidate.app.registeredAppId != record.app.registeredAppId &&
                            knownPackageName(candidate) == packageName
                    }
                } ?: 0,
                onDismiss = { removalTargetId = null },
                onStop = {
                    managedViewModel.stopTracking(appId) {
                        removalTargetId = null
                        navigate(ReproDroidRoute.Apps)
                    }
                },
                onUninstall = { packageName, stopTracking ->
                    uninstallTargetId = appId
                    uninstallStopsTracking = stopTracking
                    uninstallLauncher.launch(
                        Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName"))
                            .putExtra(Intent.EXTRA_RETURN_RESULT, true),
                    )
                },
            )
        }
    }
}

@Composable
private fun RootDrawerItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    NavigationDrawerItem(
        label = { Text(label) },
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = null) },
        modifier = Modifier.padding(horizontal = 12.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RootTopBar(
    route: ReproDroidRoute,
    onOpenDrawer: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val title = when {
        route == ReproDroidRoute.Apps -> stringResource(R.string.nav_apps)
        route.isAddFlow || route == ReproDroidRoute.GitHubStarsImport -> stringResource(R.string.nav_add)
        else -> stringResource(R.string.nav_settings)
    }
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onOpenDrawer) {
                Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.root_open_navigation))
            }
        },
        actions = {
            if (route != ReproDroidRoute.Settings) {
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.root_open_settings))
                }
            }
        },
    )
}

@Composable
private fun RootPageIndicator(route: ReproDroidRoute, onNavigate: (ReproDroidRoute) -> Unit) {
    val selected = rootPageIndex(route)
    val pages = listOf(
        stringResource(R.string.nav_apps) to ReproDroidRoute.Apps,
        stringResource(R.string.nav_add) to ReproDroidRoute.AddSource,
        stringResource(R.string.nav_settings) to ReproDroidRoute.Settings,
    )
    Surface(tonalElevation = 2.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            pages.forEachIndexed { index, (label, destination) ->
                val description = stringResource(
                    if (index == selected) R.string.root_page_selected else R.string.root_page_open,
                    label,
                )
                IconButton(
                    onClick = { onNavigate(destination) },
                    modifier = Modifier.semantics { contentDescription = description },
                ) {
                    Box(
                        Modifier
                            .size(if (index == selected) 10.dp else 7.dp)
                            .background(
                                color = if (index == selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                shape = CircleShape,
                            ),
                    )
                }
            }
        }
    }
}

private fun rootPageIndex(route: ReproDroidRoute): Int = when {
    route == ReproDroidRoute.Apps -> 0
    route.isAddFlow || route == ReproDroidRoute.GitHubStarsImport -> 1
    else -> 2
}

@Composable
private fun AppActionBar(
    route: ReproDroidRoute,
    active: Boolean,
    onNavigate: (ReproDroidRoute) -> Unit,
    onRemove: () -> Unit,
) {
    val appId = requireNotNull(route.appId)
    NavigationBar {
        NavigationBarItem(
            selected = route is ReproDroidRoute.AppInformation,
            onClick = { onNavigate(ReproDroidRoute.AppInformation(appId)) },
            icon = { Icon(Icons.Default.Info, contentDescription = null) },
            label = { Text(stringResource(R.string.action_information)) },
        )
        NavigationBarItem(
            selected = route is ReproDroidRoute.AppEdit,
            enabled = active,
            onClick = { onNavigate(ReproDroidRoute.AppEdit(appId)) },
            icon = { Icon(Icons.Default.Edit, contentDescription = null) },
            label = { Text(stringResource(R.string.action_edit)) },
        )
        NavigationBarItem(
            selected = route is ReproDroidRoute.AppSettings,
            enabled = active,
            onClick = { onNavigate(ReproDroidRoute.AppSettings(appId)) },
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
            label = { Text(stringResource(R.string.nav_settings)) },
        )
        NavigationBarItem(
            selected = false,
            enabled = active,
            onClick = onRemove,
            icon = { Icon(Icons.Default.Delete, contentDescription = null) },
            label = { Text(stringResource(R.string.action_remove)) },
        )
    }
}

@Composable
private fun UiRMessageBanner(message: String, onDismiss: () -> Unit) {
    val visibleMessage = when {
        message.contains("already registered as the primary", ignoreCase = true) ->
            stringResource(R.string.error_already_registered)
        message.contains("different repository", ignoreCase = true) ->
            stringResource(R.string.error_different_repository)
        message.contains("rate limit", ignoreCase = true) -> stringResource(R.string.error_rate_limit)
        message.contains("not found", ignoreCase = true) -> stringResource(R.string.error_not_found)
        message.contains("reload", ignoreCase = true) -> stringResource(R.string.error_stale)
        else -> message.take(MAX_VISIBLE_ERROR_LENGTH)
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(visibleMessage, Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_dismiss)) }
        }
    }
}

@Composable
private fun UiRAppsScreen(
    apps: List<RegisteredAppRecord>,
    groups: List<AppGroupEntity>,
    onSelect: (String) -> Unit,
    onAdd: () -> Unit,
    onCreateGroup: (String) -> Unit,
    onRenameGroup: (String, String) -> Unit,
    onReorderGroups: (List<String>) -> Unit,
    onDeleteGroup: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedGroupId by rememberSaveable { mutableStateOf(ALL_GROUP_ID) }
    var showGroups by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(groups, selectedGroupId) {
        if (
            selectedGroupId != ALL_GROUP_ID &&
            selectedGroupId != UNGROUPED_ID &&
            groups.none { it.groupId == selectedGroupId }
        ) {
            selectedGroupId = ALL_GROUP_ID
        }
    }
    val filtered = remember(apps, query, selectedGroupId) {
        apps.filter { record ->
            val inGroup = when (selectedGroupId) {
                ALL_GROUP_ID -> true
                UNGROUPED_ID -> record.app.groupId == null
                else -> record.app.groupId == selectedGroupId
            }
            inGroup && (
                record.app.resolvedDisplayName.contains(query, ignoreCase = true) ||
                    record.app.canonicalRepositoryUrl.contains(query, ignoreCase = true) ||
                    record.latestRelease?.selectedAsset?.packageName?.contains(query, ignoreCase = true) == true
                )
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = { showGroups = true }) { Text(stringResource(R.string.action_manage_groups)) }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            placeholder = { Text(stringResource(R.string.apps_search_hint)) },
            singleLine = true,
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = selectedGroupId == ALL_GROUP_ID,
                onClick = { selectedGroupId = ALL_GROUP_ID },
                label = { Text(stringResource(R.string.group_all)) },
            )
            FilterChip(
                selected = selectedGroupId == UNGROUPED_ID,
                onClick = { selectedGroupId = UNGROUPED_ID },
                label = { Text(stringResource(R.string.group_ungrouped)) },
            )
            groups.forEach { group ->
                FilterChip(
                    selected = selectedGroupId == group.groupId,
                    onClick = { selectedGroupId = group.groupId },
                    label = { Text(group.displayName) },
                )
            }
        }
        if (filtered.isEmpty()) {
            EmptyState(
                title = stringResource(if (apps.isEmpty()) R.string.apps_empty_title else R.string.apps_search_empty_title),
                body = stringResource(if (apps.isEmpty()) R.string.apps_empty_body else R.string.apps_search_empty_body),
                actionLabel = if (apps.isEmpty()) stringResource(R.string.action_add_app) else null,
                onAction = onAdd,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { Spacer(Modifier.height(4.dp)) }
                items(filtered, key = { it.app.registeredAppId }) { record ->
                    AppListCard(record, onSelect)
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
    if (showGroups) {
        GroupManagerDialog(
            groups = groups,
            onDismiss = { showGroups = false },
            onCreate = onCreateGroup,
            onRename = onRenameGroup,
            onReorder = onReorderGroups,
            onDelete = onDeleteGroup,
        )
    }
}

@Composable
private fun AppListCard(record: RegisteredAppRecord, onSelect: (String) -> Unit) {
    val latest = record.latestRelease
    val asset = latest?.selectedAsset
    Card(
        Modifier.fillMaxWidth().clickable { onSelect(record.app.registeredAppId) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            ManagedAppIcon(record)
            Column(Modifier.weight(1f).padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        record.app.resolvedDisplayName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(asset?.versionName ?: latest?.snapshot?.tagName ?: stringResource(R.string.value_unknown))
                }
                Text(
                    record.group?.displayName ?: stringResource(R.string.group_ungrouped),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusChip(record.trustLevel?.name ?: record.app.releaseDiscoveryStatus)
                    asset?.updateStatus?.let { StatusChip(it) }
                }
            }
        }
    }
}

@Composable
private fun GroupManagerDialog(
    groups: List<AppGroupEntity>,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onReorder: (List<String>) -> Unit,
    onDelete: (String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.groups_title)) },
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= MAX_GROUP_NAME_LENGTH) name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.group_name)) },
                    singleLine = true,
                )
                Button(
                    enabled = name.isNotBlank(),
                    onClick = {
                        val editing = editingId
                        if (editing == null) onCreate(name) else onRename(editing, name)
                        name = ""
                        editingId = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(if (editingId == null) R.string.action_create_group else R.string.action_rename_group))
                }
                if (groups.isEmpty()) Text(stringResource(R.string.groups_empty))
                groups.forEachIndexed { index, group ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp)) {
                            Text(group.displayName, style = MaterialTheme.typography.titleSmall)
                            Column(Modifier.fillMaxWidth()) {
                                TextButton(
                                    enabled = index > 0,
                                    onClick = {
                                        val ids = groups.map(AppGroupEntity::groupId).toMutableList()
                                        ids[index - 1] = group.groupId
                                        ids[index] = groups[index - 1].groupId
                                        onReorder(ids)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text(stringResource(R.string.action_move_up)) }
                                TextButton(
                                    enabled = index < groups.lastIndex,
                                    onClick = {
                                        val ids = groups.map(AppGroupEntity::groupId).toMutableList()
                                        ids[index + 1] = group.groupId
                                        ids[index] = groups[index + 1].groupId
                                        onReorder(ids)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text(stringResource(R.string.action_move_down)) }
                                TextButton(
                                    onClick = { editingId = group.groupId; name = group.displayName },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(stringResource(R.string.action_edit))
                                }
                                TextButton(
                                    onClick = { deletingId = group.groupId },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(stringResource(R.string.action_remove))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_dismiss)) } },
    )
    deletingId?.let { id ->
        AlertDialog(
            onDismissRequest = { deletingId = null },
            title = { Text(stringResource(R.string.action_delete_group)) },
            text = { Text(stringResource(R.string.group_delete_explanation)) },
            confirmButton = {
                TextButton(onClick = { onDelete(id); deletingId = null }) {
                    Text(stringResource(R.string.action_delete_group))
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingId = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UiRAddFlowScreen(
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
                            Text(stringResource(R.string.add_more_candidates, resolved.discovery.candidates.size - 8))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppInformationScreen(
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
                        Text(record.group?.displayName ?: stringResource(R.string.group_ungrouped))
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
                    Text(
                        stringResource(R.string.release_check_manual_only_body),
                        style = MaterialTheme.typography.bodySmall,
                    )
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
private fun ComparisonEvidenceScreen(
    comparison: ComparisonRunEntity,
    onBack: () -> Unit,
) {
    BackScaffoldTitle(stringResource(R.string.comparison_evidence_title), onBack) {
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                UiRDetailCard(stringResource(R.string.section_current_state)) {
                    UiRDetailValue(
                        stringResource(R.string.label_comparison_status),
                        statusLabel(comparison.status),
                    )
                    UiRDetailValue(stringResource(R.string.label_verification), statusLabel(comparison.outcome))
                    if (comparison.protocolVersion >= 2) {
                        UiRDetailValue(
                            stringResource(R.string.label_official_repeat),
                            statusLabel(comparison.repeatOfficialOutcome),
                        )
                        UiRDetailValue(
                            stringResource(R.string.label_local_repeatability),
                            statusLabel(comparison.repeatabilityOutcome),
                        )
                    }
                }
            }
            item {
                UiRDetailCard(stringResource(R.string.section_identity)) {
                    UiRDetailValue(
                        stringResource(R.string.comparison_id),
                        comparison.comparisonRunId,
                        true,
                    )
                    UiRDetailValue(
                        stringResource(R.string.label_commit),
                        comparison.expectedCommitSha,
                        true,
                    )
                    UiRDetailValue(
                        stringResource(R.string.comparison_reference_asset),
                        comparison.referenceAssetId,
                        true,
                    )
                    UiRDetailValue(
                        stringResource(R.string.comparison_runner_job),
                        comparison.runnerJobId,
                        true,
                    )
                    comparison.repeatRunnerJobId?.let {
                        UiRDetailValue(stringResource(R.string.comparison_repeat_job), it, true)
                    }
                }
            }
            comparison.incomparableReason?.let { reason ->
                item { UiRDetailCard(stringResource(R.string.comparison_reason)) { Text(reason) } }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppEditScreen(
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
                        UiRDetailValue(stringResource(R.string.label_commit), result.discovery.resolvedCommitSha ?: "UNKNOWN", true)
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
private fun UiRSettingsScreen(
    settings: GlobalSettingsEntity,
    onUpdate: (GlobalSettingsEntity) -> Unit,
    onNavigate: (ReproDroidRoute) -> Unit,
) {
    var appearanceExpanded by rememberSaveable { mutableStateOf(true) }
    var defaultsExpanded by rememberSaveable { mutableStateOf(false) }
    var updatesExpanded by rememberSaveable { mutableStateOf(false) }
    var serviceAuthenticationExpanded by rememberSaveable { mutableStateOf(false) }
    var integrationsExpanded by rememberSaveable { mutableStateOf(false) }
    var runnerExpanded by rememberSaveable { mutableStateOf(false) }
    var backupExpanded by rememberSaveable { mutableStateOf(false) }
    var warningsExpanded by rememberSaveable { mutableStateOf(false) }
    var debugExpanded by rememberSaveable { mutableStateOf(false) }
    var aboutExpanded by rememberSaveable { mutableStateOf(false) }
    var showHintsInfo by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Spacer(Modifier.height(6.dp)) }
        item {
            AccordionSection(stringResource(R.string.settings_appearance), appearanceExpanded, { appearanceExpanded = !appearanceExpanded }) {
                DropdownSetting(
                    label = stringResource(R.string.settings_theme),
                    value = settings.themeMode,
                    options = linkedMapOf(
                        ThemeMode.SYSTEM.name to stringResource(R.string.settings_theme_system),
                        ThemeMode.LIGHT.name to stringResource(R.string.settings_theme_light),
                        ThemeMode.DARK.name to stringResource(R.string.settings_theme_dark),
                    ),
                    onSelect = { onUpdate(settings.copy(themeMode = it)) },
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_dynamic_color), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.settings_dynamic_color_body), style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = settings.dynamicColorEnabled,
                        onCheckedChange = { onUpdate(settings.copy(dynamicColorEnabled = it)) },
                    )
                }
            }
        }
        item {
            AccordionSection(stringResource(R.string.settings_app_defaults), defaultsExpanded, { defaultsExpanded = !defaultsExpanded }) {
                DropdownSetting(
                    stringResource(R.string.settings_release_variant),
                    settings.defaultReleaseVariantPreference,
                    ReleaseVariantPreference.entries.associate { it.name to localizedEnumLabel(it.name) },
                    { onUpdate(settings.copy(defaultReleaseVariantPreference = it)) },
                )
                DropdownSetting(
                    stringResource(R.string.settings_abi),
                    settings.defaultPreferredAbi,
                    PreferredAbi.entries.associate { it.name to abiLabel(it.name) },
                    { onUpdate(settings.copy(defaultPreferredAbi = it)) },
                )
                DropdownSetting(
                    stringResource(R.string.app_settings_apk_limit),
                    settings.defaultMaxApkSizeBytes,
                    UI_R_APK_LIMITS.associateWith { "${it / MEBIBYTE} MiB" },
                    { onUpdate(settings.copy(defaultMaxApkSizeBytes = it)) },
                    supportingText = stringResource(R.string.settings_apk_limit_body),
                )
                DropdownSetting(
                    stringResource(R.string.settings_management_mode),
                    settings.defaultManagementMode,
                    linkedMapOf(
                        ManagementMode.VERIFICATION.name to stringResource(R.string.mode_verification),
                        ManagementMode.ACQUISITION.name to stringResource(R.string.mode_acquisition),
                    ),
                    {
                        onUpdate(
                            settings.copy(
                                defaultManagementMode = it,
                                defaultInstallationSource = if (it == ManagementMode.ACQUISITION.name) {
                                    InstallationSource.OFFICIAL_RELEASE.name
                                } else {
                                    settings.defaultInstallationSource
                                },
                            ),
                        )
                    },
                )
                DropdownSetting(
                    stringResource(R.string.settings_installation_source),
                    settings.defaultInstallationSource,
                    InstallationSource.entries
                        .filter {
                            settings.defaultManagementMode == ManagementMode.VERIFICATION.name ||
                                it == InstallationSource.OFFICIAL_RELEASE
                        }
                        .associate {
                            it.name to stringResource(
                                if (it == InstallationSource.OFFICIAL_RELEASE) {
                                    R.string.installation_official
                                } else {
                                    R.string.installation_local
                                },
                            )
                        },
                    { onUpdate(settings.copy(defaultInstallationSource = it)) },
                )
            }
        }
        item {
            AccordionSection(stringResource(R.string.settings_updates), updatesExpanded, { updatesExpanded = !updatesExpanded }) {
                Text(stringResource(R.string.planned_updates_body), style = MaterialTheme.typography.bodySmall)
                SettingsLink(stringResource(R.string.settings_updates)) {
                    onNavigate(ReproDroidRoute.UpdateSettings)
                }
            }
        }
        item {
            AccordionSection(
                stringResource(R.string.settings_service_authentication),
                serviceAuthenticationExpanded,
                { serviceAuthenticationExpanded = !serviceAuthenticationExpanded },
            ) {
                UnavailableSetting(
                    stringResource(R.string.settings_github_token),
                    stringResource(R.string.settings_service_authentication_unavailable),
                )
                UnavailableSetting(
                    stringResource(R.string.settings_codeberg_token),
                    stringResource(R.string.settings_service_authentication_unavailable),
                )
            }
        }
        item {
            AccordionSection(stringResource(R.string.settings_integrations), integrationsExpanded, { integrationsExpanded = !integrationsExpanded }) {
                UnavailableSetting(
                    stringResource(R.string.settings_shizuku),
                    stringResource(R.string.settings_external_tools_unavailable),
                )
            }
        }
        item {
            AccordionSection(stringResource(R.string.settings_runner), runnerExpanded, { runnerExpanded = !runnerExpanded }) {
                SettingsLink(stringResource(R.string.settings_runner_connections)) {
                    onNavigate(ReproDroidRoute.RunnerSettings)
                }
            }
        }
        item {
            AccordionSection(stringResource(R.string.settings_backup), backupExpanded, { backupExpanded = !backupExpanded }) {
                UnavailableSetting(
                    stringResource(R.string.settings_backup_android),
                    stringResource(R.string.settings_backup_unavailable),
                )
                UnavailableSetting(
                    stringResource(R.string.settings_backup_runner),
                    stringResource(R.string.settings_backup_unavailable),
                )
            }
        }
        item {
            AccordionSection(stringResource(R.string.settings_warnings), warningsExpanded, { warningsExpanded = !warningsExpanded }) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.settings_operation_hints),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { showHintsInfo = true }) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = stringResource(R.string.settings_operation_hints_info),
                        )
                    }
                    Switch(
                        checked = settings.showOperationHints,
                        onCheckedChange = { onUpdate(settings.copy(showOperationHints = it)) },
                    )
                }
            }
        }
        item {
            AccordionSection(stringResource(R.string.settings_debug), debugExpanded, { debugExpanded = !debugExpanded }) {
                SettingsLink(stringResource(R.string.settings_storage)) { onNavigate(ReproDroidRoute.DataManagement) }
                SettingsLink(stringResource(R.string.settings_log_export)) { onNavigate(ReproDroidRoute.LogExport) }
            }
        }
        item {
            AccordionSection(stringResource(R.string.settings_about), aboutExpanded, { aboutExpanded = !aboutExpanded }) {
                SettingsLink(stringResource(R.string.settings_licenses)) { onNavigate(ReproDroidRoute.Licenses) }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
    if (showHintsInfo) {
        AlertDialog(
            onDismissRequest = { showHintsInfo = false },
            title = { Text(stringResource(R.string.settings_operation_hints_info_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.settings_operation_hints_info_body))
                }
            },
            confirmButton = {
                TextButton(onClick = { showHintsInfo = false }) {
                    Text(stringResource(R.string.action_close))
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReleaseUpdateSettingsScreen(
    settings: ReleaseCheckSettingsEntity,
    apps: List<RegisteredAppRecord>,
    overrides: List<AppReleaseCheckOverrideEntity>,
    schedules: List<ReleaseScheduleStateEntity>,
    candidates: List<ReleaseCandidateEntity>,
    onUpdateSettings: (ReleaseCheckSettingsEntity) -> Unit,
    onUpdateOverride: (AppReleaseCheckOverrideEntity) -> Unit,
    onCheckNow: (String) -> Unit,
    onOpenCandidate: (ReleaseCandidateEntity) -> Unit,
    onRequestNotifications: () -> Unit,
    onBack: () -> Unit,
) {
    val overrideByApp = overrides.associateBy { it.registeredAppId }
    val scheduleByApp = schedules.associateBy { it.registeredAppId }
    BackScaffoldTitle(stringResource(R.string.settings_updates), onBack) {
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                UiRDetailCard(stringResource(R.string.release_check_global_settings)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.future_update_schedule))
                            Text(stringResource(R.string.release_check_scope_body), style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(
                            checked = settings.enabled,
                            onCheckedChange = { onUpdateSettings(settings.copy(enabled = it)) },
                        )
                    }
                    DropdownSetting(
                        stringResource(R.string.release_check_schedule_mode),
                        settings.scheduleMode,
                        linkedMapOf(
                            ReleaseCheckScheduleMode.INTERVAL.name to stringResource(R.string.release_check_interval_mode),
                            ReleaseCheckScheduleMode.DAILY_LOCAL_TIME.name to stringResource(R.string.release_check_daily_mode),
                        ),
                        { onUpdateSettings(settings.copy(scheduleMode = it)) },
                    )
                    DropdownSetting(
                        stringResource(R.string.future_update_interval),
                        settings.intervalHours,
                        (1..24).associateWith { pluralStringResource(R.plurals.release_check_hours, it, it) },
                        { onUpdateSettings(settings.copy(intervalHours = it)) },
                    )
                    DailyMinuteSetting(
                        minute = settings.dailyLocalMinute,
                        onSave = { onUpdateSettings(settings.copy(dailyLocalMinute = it)) },
                    )
                    DropdownSetting(
                        stringResource(R.string.release_check_channel),
                        settings.releaseChannel,
                        linkedMapOf(
                            ReleaseCheckChannel.STABLE_ONLY.name to stringResource(R.string.release_check_stable_only),
                            ReleaseCheckChannel.INCLUDE_PRERELEASE.name to stringResource(R.string.release_check_include_prerelease),
                        ),
                        { onUpdateSettings(settings.copy(releaseChannel = it)) },
                    )
                    DropdownSetting(
                        stringResource(R.string.release_check_network),
                        settings.networkPolicy,
                        linkedMapOf(
                            ReleaseCheckNetworkPolicy.ANY_AVAILABLE.name to stringResource(R.string.release_check_any_network),
                            ReleaseCheckNetworkPolicy.UNMETERED_ONLY.name to stringResource(R.string.release_check_unmetered),
                        ),
                        { onUpdateSettings(settings.copy(networkPolicy = it)) },
                    )
                    DropdownSetting(
                        stringResource(R.string.release_check_battery),
                        settings.batteryPolicy,
                        linkedMapOf(
                            ReleaseCheckBatteryPolicy.ANY.name to stringResource(R.string.release_check_any_battery),
                            ReleaseCheckBatteryPolicy.ABOVE_20_PERCENT.name to stringResource(R.string.release_check_above_twenty),
                        ),
                        { onUpdateSettings(settings.copy(batteryPolicy = it)) },
                    )
                    OutlinedButton(onClick = onRequestNotifications, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.release_check_enable_notifications))
                    }
                    Text(stringResource(R.string.release_check_permission_body), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.release_check_manual_only_body), style = MaterialTheme.typography.bodySmall)
                }
            }
            items(apps, key = { it.app.registeredAppId }) { record ->
                val existing = overrideByApp[record.app.registeredAppId]
                val override = existing ?: AppReleaseCheckOverrideEntity(
                    registeredAppId = record.app.registeredAppId,
                    updatedAt = java.time.Instant.EPOCH.toString(),
                )
                val schedule = scheduleByApp[record.app.registeredAppId]
                UiRDetailCard(record.app.resolvedDisplayName) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.release_check_app_enabled), Modifier.weight(1f))
                        Switch(
                            checked = override.enabled ?: true,
                            onCheckedChange = { onUpdateOverride(override.copy(enabled = it)) },
                        )
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.release_check_mute), Modifier.weight(1f))
                        Switch(
                            checked = override.notificationMuted,
                            onCheckedChange = { onUpdateOverride(override.copy(notificationMuted = it)) },
                        )
                    }
                    DropdownSetting(
                        stringResource(R.string.release_check_schedule_mode),
                        override.scheduleMode ?: settings.scheduleMode,
                        linkedMapOf(
                            ReleaseCheckScheduleMode.INTERVAL.name to stringResource(R.string.release_check_interval_mode),
                            ReleaseCheckScheduleMode.DAILY_LOCAL_TIME.name to stringResource(R.string.release_check_daily_mode),
                        ),
                        { onUpdateOverride(override.copy(scheduleMode = it)) },
                    )
                    DropdownSetting(
                        stringResource(R.string.future_update_interval),
                        override.intervalHours ?: settings.intervalHours,
                        (1..24).associateWith { pluralStringResource(R.plurals.release_check_hours, it, it) },
                        { onUpdateOverride(override.copy(intervalHours = it)) },
                    )
                    DailyMinuteSetting(
                        minute = override.dailyLocalMinute ?: settings.dailyLocalMinute,
                        onSave = { onUpdateOverride(override.copy(dailyLocalMinute = it)) },
                    )
                    DropdownSetting(
                        stringResource(R.string.release_check_channel),
                        override.releaseChannel ?: settings.releaseChannel,
                        linkedMapOf(
                            ReleaseCheckChannel.STABLE_ONLY.name to stringResource(R.string.release_check_stable_only),
                            ReleaseCheckChannel.INCLUDE_PRERELEASE.name to stringResource(R.string.release_check_include_prerelease),
                        ),
                        { onUpdateOverride(override.copy(releaseChannel = it)) },
                    )
                    DropdownSetting(
                        stringResource(R.string.release_check_network),
                        override.networkPolicy ?: settings.networkPolicy,
                        linkedMapOf(
                            ReleaseCheckNetworkPolicy.ANY_AVAILABLE.name to stringResource(R.string.release_check_any_network),
                            ReleaseCheckNetworkPolicy.UNMETERED_ONLY.name to stringResource(R.string.release_check_unmetered),
                        ),
                        { onUpdateOverride(override.copy(networkPolicy = it)) },
                    )
                    DropdownSetting(
                        stringResource(R.string.release_check_battery),
                        override.batteryPolicy ?: settings.batteryPolicy,
                        linkedMapOf(
                            ReleaseCheckBatteryPolicy.ANY.name to stringResource(R.string.release_check_any_battery),
                            ReleaseCheckBatteryPolicy.ABOVE_20_PERCENT.name to stringResource(R.string.release_check_above_twenty),
                        ),
                        { onUpdateOverride(override.copy(batteryPolicy = it)) },
                    )
                    OutlinedButton(
                        onClick = {
                            onUpdateOverride(
                                override.copy(
                                    enabled = null,
                                    scheduleMode = null,
                                    intervalHours = null,
                                    dailyLocalMinute = null,
                                    releaseChannel = null,
                                    networkPolicy = null,
                                    batteryPolicy = null,
                                ),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.release_check_use_global)) }
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
                        onClick = { onCheckNow(record.app.registeredAppId) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.release_check_now)) }
                }
            }
            if (candidates.isNotEmpty()) {
                item { Text(stringResource(R.string.release_candidates_title), style = MaterialTheme.typography.titleLarge) }
                items(candidates, key = { it.candidateId }) { candidate ->
                    Card(Modifier.fillMaxWidth().clickable { onOpenCandidate(candidate) }) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(candidate.releaseName, fontWeight = FontWeight.SemiBold)
                            Text(candidate.tagName)
                            Text(statusLabel(candidate.state))
                            if (candidate.unseen) Text(stringResource(R.string.release_candidate_unseen))
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun DailyMinuteSetting(minute: Int, onSave: (Int) -> Unit) {
    val initial = "%02d:%02d".format(minute / 60, minute % 60)
    var value by rememberSaveable(minute) { mutableStateOf(initial) }
    val parsed = remember(value) {
        val parts = value.split(':')
        if (parts.size != 2) null else {
            val hour = parts[0].toIntOrNull()
            val localMinute = parts[1].toIntOrNull()
            if (hour != null && localMinute != null && hour in 0..23 && localMinute in 0..59) {
                hour * 60 + localMinute
            } else {
                null
            }
        }
    }
    OutlinedTextField(
        value = value,
        onValueChange = { value = it.take(5) },
        label = { Text(stringResource(R.string.release_check_daily_time)) },
        supportingText = { Text(stringResource(R.string.release_check_daily_time_body)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedButton(
        enabled = parsed != null && parsed != minute,
        onClick = { parsed?.let(onSave) },
        modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(R.string.action_save)) }
}

@Composable
private fun AccordionSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(
                        if (expanded) R.string.action_collapse else R.string.action_expand,
                    ),
                )
            }
            AnimatedVisibility(expanded) {
                Column(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = content,
                )
            }
        }
    }
}

@Composable
private fun SettingsLink(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, Modifier.fillMaxWidth()) { Text(label) }
}

@Composable
private fun UnavailableSetting(label: String, body: String) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
    }
}

@Composable
private fun PlannedCard(phase: String, body: String, onOpen: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(body)
            Text(stringResource(R.string.planned_phase, phase), color = MaterialTheme.colorScheme.secondary)
            Text(stringResource(R.string.planned_unavailable), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun DisabledSetting(label: String, phase: String) {
    val description = stringResource(R.string.future_feature_content_description, phase)
    OutlinedButton(
        enabled = false,
        onClick = {},
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = description },
    ) { Text(label) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DataManagementScreen(
    onBack: () -> Unit,
    onStorage: () -> Unit,
    onInactive: () -> Unit,
    onRunner: () -> Unit,
    onLogExport: () -> Unit,
) {
    BackScaffoldTitle(stringResource(R.string.data_management_title), onBack) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsLink(stringResource(R.string.data_android_storage), onStorage)
            SettingsLink(stringResource(R.string.inactive_apps_title), onInactive)
            Text(stringResource(R.string.data_android_deletion_note), style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            SettingsLink(stringResource(R.string.data_runner_separate), onRunner)
            Text(stringResource(R.string.data_runner_note), style = MaterialTheme.typography.bodySmall)
            SettingsLink(stringResource(R.string.settings_log_export), onLogExport)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RunnerSettingsScreen(
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
private fun RunnerAuthenticationScreen(
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
private fun PlannedFeatureScreen(
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogExportScreen(
    result: AppLogExportResult?,
    busy: Boolean,
    onExport: () -> Unit,
    onClearResult: () -> Unit,
    onBack: () -> Unit,
) {
    BackScaffoldTitle(stringResource(R.string.settings_log_export), onBack) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.log_export_scope), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.log_export_missing_warning), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.log_export_sensitive_warning), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.log_export_migration_warning), style = MaterialTheme.typography.bodySmall)
            Button(
                enabled = !busy,
                onClick = onExport,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.log_export_choose_destination))
            }
            result?.let { exported ->
                HorizontalDivider()
                Text(stringResource(R.string.log_export_saved), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.log_export_records, exported.recordCount))
                Text(stringResource(R.string.log_export_size, humanBytes(exported.sizeBytes)))
                if (exported.includesRotatedFile) {
                    Text(stringResource(R.string.log_export_rotated_included))
                }
                TextButton(onClick = onClearResult) {
                    Text(stringResource(R.string.log_export_clear_result))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LicenseScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember(context) { LicenseAssetStore(context) }
    var documents by remember { mutableStateOf<List<LicenseDocument>?>(null) }
    var loadFailed by remember { mutableStateOf(false) }

    LaunchedEffect(store) {
        runCatching { store.loadDocuments() }
            .onSuccess { documents = it }
            .onFailure { loadFailed = true }
    }

    BackScaffoldTitle(stringResource(R.string.settings_licenses), onBack) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.licenses_intro), style = MaterialTheme.typography.bodyMedium)
            when {
                loadFailed -> Text(stringResource(R.string.licenses_load_error))
                documents == null -> Text(stringResource(R.string.licenses_loading))
                else -> documents.orEmpty().forEach { document ->
                    Text(document.title, style = MaterialTheme.typography.titleMedium)
                    Text(document.text, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InactiveAppsScreen(
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

@Composable
private fun RemoveTrackingDialog(
    record: RegisteredAppRecord,
    otherPackageReferenceCount: Int,
    onDismiss: () -> Unit,
    onStop: () -> Unit,
    onUninstall: (String, Boolean) -> Unit,
) {
    val context = LocalContext.current
    val packageName = knownPackageName(record)
    val installedVersion = record.latestRelease?.selectedAsset?.installedVersionName
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackScaffoldTitle(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(title) }, navigationIcon = { BackButton(onBack) })
        Box(Modifier.fillMaxSize()) { content() }
    }
}

@Composable
private fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
    }
}

@Composable
private fun EmptyState(
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodyMedium)
            actionLabel?.let { Button(onClick = onAction) { Text(it) } }
        }
    }
}

@Composable
private fun MissingRecordScreen(onBack: () -> Unit) {
    EmptyState(
        title = stringResource(R.string.value_not_available),
        body = stringResource(R.string.apps_search_empty_body),
        actionLabel = stringResource(R.string.action_back),
        onAction = onBack,
    )
}

@Composable
private fun StatusChip(value: String) {
    AssistChip(onClick = {}, label = { Text(statusLabel(value)) })
}

@Composable
private fun UiRDetailCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun UiRDetailValue(label: String, value: String, monospace: Boolean = false) {
    val clipboard = LocalClipboardManager.current
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f),
            )
            if (monospace && value.isNotBlank()) {
                TextButton(onClick = { clipboard.setText(AnnotatedString(value)) }) {
                    Text(stringResource(R.string.action_copy))
                }
            }
        }
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
            overflow = TextOverflow.Visible,
        )
    }
}

private fun repositoryOwner(repositoryUrl: String): String = runCatching {
    Uri.parse(repositoryUrl).pathSegments.firstOrNull()
}.getOrNull().orEmpty().ifBlank { "UNKNOWN" }

private fun knownPackageName(record: RegisteredAppRecord): String? =
    record.latestRelease?.selectedAsset?.packageName
        ?: record.releases.asSequence()
            .flatMap { it.assets.asSequence() }
            .mapNotNull { it.packageName }
            .firstOrNull()

private fun String.humanize(): String = lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)

@Composable
private fun statusLabel(value: String?): String = statusLabelResource(value)?.let { stringResource(it) }
    ?: requireNotNull(value).humanize()

internal fun statusLabelResource(value: String?): Int? = when (value) {
    "ACTIVE" -> R.string.state_active
    "INACTIVE" -> R.string.state_inactive
    "NOT_CHECKED", "NOT_EVALUATED" -> R.string.state_not_checked
    "CHECKING", "RESOLVING", "SCANNING_TREE" -> R.string.state_checking
    "PENDING", "QUEUED" -> R.string.state_pending
    "RUNNING", "BUILDING" -> R.string.state_running
    "AVAILABLE", "UP_TO_DATE", "SUCCESS", "COMPLETE", "COMPLETED" -> R.string.state_success
    "UPDATE_AVAILABLE" -> R.string.state_update_available
    "NOT_INSTALLED" -> R.string.state_not_installed
    "REPRODUCIBLE", "EQUIVALENT" -> R.string.state_reproducible
    "MATCH" -> R.string.state_match
    "BUILDABLE" -> R.string.state_buildable
    "DIFFERENT" -> R.string.state_different
    "INCOMPARABLE" -> R.string.state_incomparable
    "FAILED", "ERROR" -> R.string.state_error
    "CANCELLED" -> R.string.state_cancelled
    "INTERRUPTED" -> R.string.state_interrupted
    "AWAITING_ASSET_SELECTION" -> R.string.state_awaiting_selection
    "OLDER_THAN_INSTALLED" -> R.string.state_older_than_installed
    "UNKNOWN", null -> R.string.value_unknown
    else -> null
}

@Composable
private fun localizedEnumLabel(value: String): String = when (value) {
    ReleaseVariantPreference.RELEASE.name -> stringResource(R.string.enum_release)
    ReleaseVariantPreference.PREVIEW.name -> stringResource(R.string.enum_preview)
    ReleaseVariantPreference.DEBUG.name -> stringResource(R.string.enum_debug)
    else -> value.humanize()
}

@Composable
private fun abiLabel(value: String): String = when (value) {
    PreferredAbi.ARM64_V8A.name -> "arm64-v8a"
    PreferredAbi.ARMEABI_V7A.name -> "armeabi-v7a"
    PreferredAbi.X86_64.name -> "x86_64"
    PreferredAbi.UNIVERSAL.name -> stringResource(R.string.enum_universal)
    else -> value
}

private fun humanBytes(value: Long): String = when {
    value >= 1024L * 1024 * 1024 -> "%.2f GiB".format(value / (1024.0 * 1024 * 1024))
    value >= 1024L * 1024 -> "%.2f MiB".format(value / (1024.0 * 1024))
    value >= 1024L -> "%.2f KiB".format(value / 1024.0)
    else -> "$value B"
}

private const val ALL_GROUP_ID = "__all__"
private const val UNGROUPED_ID = "__ungrouped__"
private const val MAX_VISIBLE_ERROR_LENGTH = 800
private const val MAX_GROUP_NAME_LENGTH = 80
private const val MAX_DISPLAY_NAME_LENGTH = 120
private const val MAX_AUTHOR_DISPLAY_LENGTH = 160
private const val MAX_NOTE_LENGTH = 10_000
private const val MEBIBYTE = 1024L * 1024L
private val UI_R_APK_LIMITS = listOf(64L * MEBIBYTE, 128L * MEBIBYTE, 256L * MEBIBYTE, 512L * MEBIBYTE)
