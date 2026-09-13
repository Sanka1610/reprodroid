package com.sanka1610.reprodroid.ui.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanka1610.reprodroid.R
import com.sanka1610.reprodroid.data.local.AppTrackingState
import com.sanka1610.reprodroid.data.local.InstallationSource
import com.sanka1610.reprodroid.data.local.ManagementMode
import com.sanka1610.reprodroid.data.local.ReleaseCheckSettingsEntity
import com.sanka1610.reprodroid.ui.*
import com.sanka1610.reprodroid.ui.add.UiRAddFlowScreen
import com.sanka1610.reprodroid.ui.appdetail.AppEditScreen
import com.sanka1610.reprodroid.ui.appdetail.AppDetailScreen
import com.sanka1610.reprodroid.ui.appdetail.AppInformationScreen
import com.sanka1610.reprodroid.ui.appdetail.AppPreferencesScreen
import com.sanka1610.reprodroid.ui.appdetail.RemoveTrackingDialog
import com.sanka1610.reprodroid.ui.apps.InactiveAppsScreen
import com.sanka1610.reprodroid.ui.apps.UiRAppsScreen
import com.sanka1610.reprodroid.ui.comparison.ComparisonEvidenceScreen
import com.sanka1610.reprodroid.ui.jobs.JobScreen
import com.sanka1610.reprodroid.ui.navigation.ReproDroidBackContext
import com.sanka1610.reprodroid.ui.navigation.ReproDroidRoute
import com.sanka1610.reprodroid.ui.navigation.backDestination
import com.sanka1610.reprodroid.ui.navigation.missingAppDestination
import com.sanka1610.reprodroid.ui.runner.PlannedFeatureScreen
import com.sanka1610.reprodroid.ui.runner.RunnerAuthenticationScreen
import com.sanka1610.reprodroid.ui.runner.RunnerSettingsScreen
import com.sanka1610.reprodroid.ui.runner.ToolchainScreen
import com.sanka1610.reprodroid.ui.settings.DataManagementScreen
import com.sanka1610.reprodroid.ui.settings.LicenseScreen
import com.sanka1610.reprodroid.ui.settings.LogExportScreen
import com.sanka1610.reprodroid.ui.settings.ReleaseUpdateSettingsScreen
import com.sanka1610.reprodroid.ui.settings.StorageScreen
import com.sanka1610.reprodroid.ui.settings.UiRSettingsScreen
import com.sanka1610.reprodroid.ui.shared.BackScaffoldTitle
import com.sanka1610.reprodroid.ui.shared.MAX_VISIBLE_ERROR_LENGTH
import com.sanka1610.reprodroid.ui.shared.MissingRecordScreen
import com.sanka1610.reprodroid.ui.shared.knownPackageName
import com.sanka1610.reprodroid.ui.theme.ReproDroidTheme

@Composable
fun ReproDroidApp(
    managedViewModel: ManagedAppsViewModel,
    jobViewModel: JobViewModel,
    initialRoute: String? = null,
) {
    val appsState by managedViewModel.appsUiState.collectAsStateWithLifecycle()
    val registrationState by managedViewModel.registrationUiState.collectAsStateWithLifecycle()
    val appDetailState by managedViewModel.appDetailUiState.collectAsStateWithLifecycle()
    val releaseState by managedViewModel.releaseUiState.collectAsStateWithLifecycle()
    val storageState by managedViewModel.storageUiState.collectAsStateWithLifecycle()
    val runnerState by managedViewModel.runnerUiState.collectAsStateWithLifecycle()
    val toolchainFeatureState by managedViewModel.toolchainUiState.collectAsStateWithLifecycle()
    val deletionState by managedViewModel.deletionExportUiState.collectAsStateWithLifecycle()
    val message by managedViewModel.message.collectAsStateWithLifecycle()
    val apps = appsState.apps
    val inactiveApps = appsState.inactiveApps
    val appCatalogLoaded = appsState.catalogLoaded
    val groups = appsState.groups
    val globalSettings = appsState.settings
    val activeAppIds = appsState.activeAppIds
    val preview = registrationState.preview
    val sourceEditPreview = registrationState.sourceEditPreview
    val buildEnvironmentManifests = appDetailState.buildEnvironmentManifests
    val runnerJobs = appDetailState.runnerJobs
    val buildManifestWarnings = appDetailState.buildManifestWarnings
    val sourceScanWarnings = appDetailState.sourceScanWarnings
    val sandboxWarnings = appDetailState.sandboxWarnings
    val availability = appDetailState.availability
    val androidStorageSummary = storageState.androidSummary
    val androidCleanupPreview = storageState.androidCleanupPreview
    val runnerStorageState = storageState.runnerState
    val storageBusy = storageState.busy
    val auditExport = storageState.auditExport
    val appLogExport = storageState.appLogExport
    val runnerCleanupPreview = storageState.runnerCleanupPreview
    val runnerCleanupRun = storageState.runnerCleanupRun
    val toolchainState = toolchainFeatureState.coordinator
    val deletionPreview = deletionState.preview
    val deletionResult = deletionState.result
    val releaseCheckOverrides = releaseState.overrides
    val releaseScheduleStates = releaseState.schedules
    val releaseCandidates = releaseState.candidates
    val runnerConnectionStatus = runnerState.status
    val runnerConnections = runnerState.connections
    val releaseCheckSettings = releaseState.settings
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

    val activityResults = rememberAppActivityResultCoordinator(
        onUninstallResult = { target ->
            val onConfirmed = {
                removalTargetId = null
                if (target.stopTracking) {
                    navigate(ReproDroidRoute.Apps)
                } else {
                    navigate(ReproDroidRoute.AppInformation(target.registeredAppId))
                }
            }
            if (target.stopTracking) {
                managedViewModel.stopTrackingAfterConfirmedUninstall(target.registeredAppId, onConfirmed)
            } else {
                managedViewModel.confirmUninstall(target.registeredAppId, onConfirmed)
            }
        },
        onLogDestination = managedViewModel::exportAppLogs,
        onAuditDestination = managedViewModel::copyAuditExport,
    )

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
                            onChooseAuditDestination = activityResults::createAuditExportDocument,
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
                            onChooseAuditDestination = activityResults::createAuditExportDocument,
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
                            onRequestNotifications = activityResults::requestNotificationPermission,
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
                            onExport = activityResults::createLogExportDocument,
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
                    activityResults.uninstall(
                        UninstallActivityTarget(
                            registeredAppId = appId,
                            packageName = packageName,
                            stopTracking = stopTracking,
                        ),
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
