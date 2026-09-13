package com.sanka1610.reprodroid.ui.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
import com.sanka1610.reprodroid.ui.settings.StorageScreen
import com.sanka1610.reprodroid.ui.settings.UiRSettingsScreen
import com.sanka1610.reprodroid.ui.shared.BackScaffoldTitle
import com.sanka1610.reprodroid.ui.shared.MissingRecordScreen
import com.sanka1610.reprodroid.ui.shared.knownPackageName
import com.sanka1610.reprodroid.ui.shared.isSelfRegistration
import com.sanka1610.reprodroid.ui.state.ManagedUiMessage
import com.sanka1610.reprodroid.ui.state.ManagedUiMessageCode
import com.sanka1610.reprodroid.ui.state.relevantDisposition
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
    val message = listOfNotNull(
        appsState.message,
        registrationState.message,
        appDetailState.message,
        releaseState.message,
        storageState.message,
        runnerState.message,
        toolchainFeatureState.message,
        deletionState.message,
    ).firstOrNull()
    val pendingResults =
        appsState.results + registrationState.results + appDetailState.results + releaseState.results +
            storageState.results + runnerState.results + toolchainFeatureState.results + deletionState.results
    val orderedPendingResults = pendingResults.sortedBy { it.id }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var notificationsAllowed by remember { mutableStateOf(appNotificationsAllowed(context)) }

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
    var pendingCandidateId by remember { mutableStateOf<String?>(null) }
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
    var appsSearchExpanded by rememberSaveable { mutableStateOf(false) }
    var showExitConfirmation by rememberSaveable { mutableStateOf(false) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val rootScope = rememberCoroutineScope()
    val rootPages = remember {
        listOf(ReproDroidRoute.Apps, ReproDroidRoute.AddSource, ReproDroidRoute.Settings)
    }
    val rootPagerState = rememberPagerState(
        initialPage = rootPageIndex(route),
        pageCount = { rootPages.size },
    )

    fun navigate(destination: ReproDroidRoute) {
        encodedRoute = destination.encode()
    }

    LaunchedEffect(route) {
        if (route != ReproDroidRoute.Apps) appsSearchExpanded = false
        if (route == ReproDroidRoute.UpdateSettings) navigate(ReproDroidRoute.Settings)
        if (route.isRoot) {
            val targetPage = rootPageIndex(route)
            if (rootPagerState.currentPage != targetPage) rootPagerState.scrollToPage(targetPage)
        }
    }

    LaunchedEffect(rootPagerState) {
        snapshotFlow { rootPagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                val destination = rootPages[page]
                if (ReproDroidRoute.parse(encodedRoute).isRoot && encodedRoute != destination.encode()) {
                    navigate(destination)
                }
            }
    }

    LaunchedEffect(orderedPendingResults, route, removalTargetId, pendingCandidateId) {
        val result = orderedPendingResults.firstOrNull() ?: return@LaunchedEffect
        val disposition = result.relevantDisposition(route, removalTargetId, pendingCandidateId)
        if (disposition != null) {
            if (disposition.resetRegistrationDraft) {
                addRepositoryUrl = ""
                addRiskConfirmed = false
                addSeparateTarget = false
            }
            if (disposition.clearRemovalTarget) removalTargetId = null
            navigate(disposition.destination)
        }
        if (result.candidateId != null && result.candidateId == pendingCandidateId) pendingCandidateId = null
        managedViewModel.acknowledgeResult(result.id)
    }

    val activityResults = rememberAppActivityResultCoordinator(
        onUninstallResult = { target ->
            if (target.stopTracking) {
                managedViewModel.stopTrackingAfterConfirmedUninstall(
                    target.registeredAppId,
                    ReproDroidRoute.AppInformation(target.registeredAppId).encode(),
                )
            } else {
                managedViewModel.confirmUninstall(
                    target.registeredAppId,
                    ReproDroidRoute.AppInformation(target.registeredAppId).encode(),
                )
            }
        },
        onNotificationPermissionResult = {
            notificationsAllowed = appNotificationsAllowed(context)
        },
        onLogDestination = managedViewModel::exportAppLogs,
        onAuditDestination = managedViewModel::copyAuditExport,
    )

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsAllowed = appNotificationsAllowed(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(
        appCatalogLoaded,
        globalSettings.notificationPermissionPrompted,
        notificationsAllowed,
    ) {
        if (
            appCatalogLoaded &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !globalSettings.notificationPermissionPrompted &&
            !notificationsAllowed
        ) {
            managedViewModel.updateGlobalSettings(
                globalSettings.copy(notificationPermissionPrompted = true),
            )
            activityResults.requestNotificationPermission()
        }
    }

    val backContext = ReproDroidBackContext(
        currentAppIsInactive = routeApp?.app?.trackingState == AppTrackingState.INACTIVE.name,
        inactiveReturnRoute = inactiveReturnRoute,
        comparisonOwnerAppId = comparisonRouteApp?.app?.registeredAppId,
    )

    BackHandler(enabled = drawerState.isOpen) { rootScope.launch { drawerState.close() } }
    BackHandler(enabled = !drawerState.isOpen) {
        when {
            !route.isRoot -> navigate(backDestination(route, backContext))
            route != ReproDroidRoute.Apps -> navigate(ReproDroidRoute.Apps)
            appsSearchExpanded -> appsSearchExpanded = false
            else -> showExitConfirmation = true
        }
    }

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
                searchExpanded = appsSearchExpanded,
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
                    managedViewModel.resumeRegistration(id, destination.encode())
                },
                onRegister = { mode, source, confirmed, separateTarget ->
                    managedViewModel.register(mode, source, confirmed, separateTarget, destination.encode())
                },
            )
            ReproDroidRoute.Settings -> UiRSettingsScreen(
                settings = globalSettings,
                releaseSettings = releaseCheckSettings,
                notificationsAllowed = notificationsAllowed,
                onUpdate = managedViewModel::updateGlobalSettings,
                onUpdateReleaseSettings = managedViewModel::updateReleaseCheckSettings,
                onRequestNotifications = activityResults::requestNotificationPermission,
                onNavigate = ::navigate,
            )
            else -> Unit
        }
    }

    val showRootShell = route.isRoot || route.isAddFlow || route == ReproDroidRoute.GitHubStarsImport
    val density = LocalDensity.current
    val containerWidth = with(density) { LocalWindowInfo.current.containerSize.width.toDp() }
    val drawerWidth = (containerWidth * 0.58f).coerceIn(220.dp, 320.dp)
    val snackbarHostState = remember { SnackbarHostState() }
    val visibleMessage = message?.let { managedMessageText(it) }
    val dismissMessageLabel = stringResource(R.string.action_dismiss)
    LaunchedEffect(message?.id, visibleMessage) {
        val currentMessage = message ?: return@LaunchedEffect
        val text = visibleMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            message = text,
            actionLabel = dismissMessageLabel,
            duration = SnackbarDuration.Long,
        )
        managedViewModel.acknowledgeMessage(currentMessage.id)
    }
    ReproDroidTheme(globalSettings) {
        Surface(Modifier.fillMaxSize()) {
            ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = drawerState.isOpen,
                drawerContent = {
                    ModalDrawerSheet(modifier = Modifier.width(drawerWidth)) {
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
                Box(Modifier.fillMaxSize()) {
                Scaffold(
                snackbarHost = {
                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier.padding(bottom = if (showRootShell) 64.dp else 0.dp),
                    )
                },
                topBar = {
                    if (showRootShell) {
                        RootTopBar(
                            route = route,
                            onOpenDrawer = { rootScope.launch { drawerState.open() } },
                            searchExpanded = appsSearchExpanded,
                            onToggleSearch = { appsSearchExpanded = !appsSearchExpanded },
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
                    if (route.appId != null && routeApp != null) AppActionBar(
                            route = route,
                            active = routeApp.app.trackingState == AppTrackingState.ACTIVE.name,
                            onNavigate = ::navigate,
                            onRemove = { removalTargetId = routeApp.app.registeredAppId },
                        )
                },
                ) { contentPadding ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(contentPadding),
                ) {
                    if (route.isRoot) {
                        HorizontalPager(
                            state = rootPagerState,
                            modifier = Modifier.fillMaxSize(),
                            beyondViewportPageCount = 1,
                        ) { page ->
                            rootContent(rootPages[page])
                        }
                    } else {
                        when (route) {
                        ReproDroidRoute.Apps -> UiRAppsScreen(
                            apps = apps,
                            groups = groups,
                            searchExpanded = appsSearchExpanded,
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
                                managedViewModel.executeCompleteDeletion(route.encode())
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
                                managedViewModel.resumeRegistration(id, route.encode())
                            },
                            onRegister = { mode, source, confirmed, separateTarget ->
                                managedViewModel.register(mode, source, confirmed, separateTarget, route.encode())
                            },
                        )
                        ReproDroidRoute.Settings -> UiRSettingsScreen(
                            settings = globalSettings,
                            releaseSettings = releaseCheckSettings,
                            notificationsAllowed = notificationsAllowed,
                            onUpdate = managedViewModel::updateGlobalSettings,
                            onUpdateReleaseSettings = managedViewModel::updateReleaseCheckSettings,
                            onRequestNotifications = activityResults::requestNotificationPermission,
                            onNavigate = ::navigate,
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
                            onDelete = { managedViewModel.executeCompleteDeletion(route.encode()) },
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
                        ReproDroidRoute.UpdateSettings -> UiRSettingsScreen(
                            settings = globalSettings,
                            releaseSettings = releaseCheckSettings,
                            notificationsAllowed = notificationsAllowed,
                            onUpdate = managedViewModel::updateGlobalSettings,
                            onUpdateReleaseSettings = managedViewModel::updateReleaseCheckSettings,
                            onRequestNotifications = activityResults::requestNotificationPermission,
                            onNavigate = ::navigate,
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
                        ReproDroidRoute.ThirdPartyNotices -> LicenseScreen(
                            thirdParty = true,
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
                                    pendingCandidateId = candidate.candidateId
                                    managedViewModel.openReleaseCandidate(
                                        candidate.registeredAppId,
                                        candidate.candidateId,
                                        route.encode(),
                                    )
                                },
                                onTechnical = {
                                    navigate(ReproDroidRoute.AppTechnical(record.app.registeredAppId))
                                },
                                onComparison = { comparisonId ->
                                    navigate(ReproDroidRoute.Comparison(comparisonId))
                                },
                                onResume = {
                                    managedViewModel.resumeTracking(record.app.registeredAppId)
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
                                    managedViewModel.updateMetadata(record.app.registeredAppId, update, route.encode())
                                },
                                onInspectSource = { url ->
                                    managedViewModel.previewSourceEdit(
                                        record.app.registeredAppId,
                                        record.app.updatedAt,
                                        url,
                                    )
                                },
                                onApplySource = {
                                    managedViewModel.applySourceEdit(record.app.registeredAppId, route.encode())
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
                                releaseSettings = releaseCheckSettings,
                                releaseOverride = releaseCheckOverrides.firstOrNull {
                                    it.registeredAppId == record.app.registeredAppId
                                } ?: com.sanka1610.reprodroid.data.local.AppReleaseCheckOverrideEntity(
                                    registeredAppId = record.app.registeredAppId,
                                    updatedAt = java.time.Instant.EPOCH.toString(),
                                ),
                                saving = record.app.registeredAppId in activeAppIds,
                                onBack = { navigate(ReproDroidRoute.AppInformation(record.app.registeredAppId)) },
                                onSave = { update ->
                                    managedViewModel.updatePreferences(record.app.registeredAppId, update, route.encode())
                                },
                                onSaveBuildConfiguration = { revision, input ->
                                    managedViewModel.saveBuildConfiguration(record.app.registeredAppId, revision, input)
                                },
                                onUpdateReleaseOverride = managedViewModel::updateReleaseCheckOverride,
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
                if (showRootShell) {
                    RootPageIndicator(
                        route = route,
                        onNavigate = ::navigate,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = 4.dp),
                    )
                }
                }
            }
        }
    }

    removalTargetId?.let { appId ->
        allApps.firstOrNull { it.app.registeredAppId == appId }?.let { record ->
            ReproDroidTheme(globalSettings) {
                val effectivePackageName = knownPackageName(record)
                    ?: context.packageName.takeIf { isSelfRegistration(record) }
                RemoveTrackingDialog(
                    record = record,
                    otherPackageReferenceCount = effectivePackageName?.let { packageName ->
                        allApps.count { candidate ->
                            candidate.app.registeredAppId != record.app.registeredAppId &&
                                knownPackageName(candidate) == packageName
                        }
                    } ?: 0,
                    onDismiss = { removalTargetId = null },
                    onStop = {
                        managedViewModel.stopTracking(appId, route.encode())
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
    if (showExitConfirmation) {
        ReproDroidTheme(globalSettings) {
            AlertDialog(
                onDismissRequest = { showExitConfirmation = false },
                title = { Text(stringResource(R.string.exit_title)) },
                text = { Text(stringResource(R.string.exit_body)) },
                dismissButton = {
                    TextButton(onClick = { showExitConfirmation = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
                confirmButton = {
                    TextButton(onClick = { (context as? Activity)?.finish() }) {
                        Text(stringResource(R.string.action_exit))
                    }
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
    searchExpanded: Boolean,
    onToggleSearch: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val title = when {
        route == ReproDroidRoute.Apps -> stringResource(R.string.apps_title)
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
            if (route == ReproDroidRoute.Apps) {
                IconButton(onClick = onToggleSearch) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = stringResource(
                            if (searchExpanded) R.string.apps_close_search else R.string.apps_open_search,
                        ),
                    )
                }
            }
            if (route != ReproDroidRoute.Settings) {
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.root_open_settings))
                }
            }
        },
    )
}

@Composable
private fun RootPageIndicator(
    route: ReproDroidRoute,
    onNavigate: (ReproDroidRoute) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = rootPageIndex(route)
    val pages = listOf(
        Triple(stringResource(R.string.nav_apps), ReproDroidRoute.Apps, Icons.Default.Home),
        Triple(stringResource(R.string.nav_add), ReproDroidRoute.AddSource, Icons.Default.Add),
        Triple(stringResource(R.string.nav_settings), ReproDroidRoute.Settings, Icons.Default.Settings),
    )
    Surface(
        modifier = modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent(PointerEventPass.Final).changes.forEach { change ->
                        if (!change.isConsumed) change.consume()
                    }
                }
            }
        },
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            pages.forEachIndexed { index, (label, destination, icon) ->
                val description = stringResource(
                    if (index == selected) R.string.root_page_selected else R.string.root_page_open,
                    label,
                )
                IconButton(
                    onClick = { onNavigate(destination) },
                    modifier = Modifier.semantics { contentDescription = description },
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (index == selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
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

private fun appNotificationsAllowed(context: Context): Boolean {
    val runtimeAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    return runtimeAllowed && NotificationManagerCompat.from(context).areNotificationsEnabled()
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
private fun managedMessageText(message: ManagedUiMessage): String = when (message.code) {
        ManagedUiMessageCode.ALREADY_REGISTERED_PRIMARY -> stringResource(R.string.error_already_registered)
        ManagedUiMessageCode.DIFFERENT_REPOSITORY -> stringResource(R.string.error_different_repository)
        ManagedUiMessageCode.RATE_LIMIT -> stringResource(R.string.error_rate_limit)
        ManagedUiMessageCode.NOT_FOUND -> stringResource(R.string.error_not_found)
        ManagedUiMessageCode.STALE_STATE -> stringResource(R.string.error_stale)
        ManagedUiMessageCode.OPERATION_FAILED -> stringResource(R.string.error_operation_failed)
    }
