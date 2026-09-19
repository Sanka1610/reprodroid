package com.sanka1610.reprodroid.ui.app

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanka1610.reprodroid.R
import com.sanka1610.reprodroid.data.local.AppTrackingState
import com.sanka1610.reprodroid.data.local.InstallationSource
import com.sanka1610.reprodroid.data.local.ManagementMode
import com.sanka1610.reprodroid.data.local.ReleaseCheckSettingsEntity
import com.sanka1610.reprodroid.ui.*
import com.sanka1610.reprodroid.ui.add.UiRAddFlowScreen
import com.sanka1610.reprodroid.ui.appdetail.*
import com.sanka1610.reprodroid.ui.appdetail.AppEditScreen
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
import com.sanka1610.reprodroid.ui.settings.*
import com.sanka1610.reprodroid.ui.settings.DataManagementScreen
import com.sanka1610.reprodroid.ui.settings.LicenseScreen
import com.sanka1610.reprodroid.ui.settings.LogExportScreen
import com.sanka1610.reprodroid.ui.settings.StorageScreen
import com.sanka1610.reprodroid.ui.shared.BackScaffoldTitle
import com.sanka1610.reprodroid.ui.shared.MissingRecordScreen
import com.sanka1610.reprodroid.ui.shared.isSelfRegistration
import com.sanka1610.reprodroid.ui.shared.knownPackageName
import com.sanka1610.reprodroid.ui.state.ManagedUiResultKind
import com.sanka1610.reprodroid.ui.state.ManagedUiMessage
import com.sanka1610.reprodroid.ui.state.ManagedUiMessageCode
import com.sanka1610.reprodroid.ui.state.relevantDisposition
import com.sanka1610.reprodroid.ui.theme.ReproDroidTheme

@OptIn(ExperimentalMaterial3Api::class)
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
    val providerAuthState by managedViewModel.providerAuthUiState.collectAsStateWithLifecycle()
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
        providerAuthState.message,
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
    var backgroundWorkAllowed by remember { mutableStateOf(appBackgroundWorkAllowed(context)) }

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
    var verificationReturnAppId by rememberSaveable { mutableStateOf<String?>(null) }
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
    fun navigate(destination: ReproDroidRoute) {
        if (destination is ReproDroidRoute.AppVerification) verificationReturnAppId = null
        if (destination is ReproDroidRoute.AppAcquisition && route is ReproDroidRoute.AppVerification ||
            destination is ReproDroidRoute.AppSettings && route is ReproDroidRoute.AppVerification ||
            destination == ReproDroidRoute.RunnerSettings && route is ReproDroidRoute.AppVerification) {
            verificationReturnAppId = route.appId
        }
        if (destination == ReproDroidRoute.Apps || destination == ReproDroidRoute.Settings || destination is ReproDroidRoute.AppInformation) verificationReturnAppId = null
        encodedRoute = destination.encode()
    }

    LaunchedEffect(route) {
        if (route != ReproDroidRoute.Apps) appsSearchExpanded = false
        if (route is ReproDroidRoute.AppRegistrationComplete) {
            navigate(ReproDroidRoute.AppInformation(route.registeredAppId))
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
            val destination = if (result.kind == ManagedUiResultKind.PREFERENCES_SAVED &&
                verificationReturnAppId == result.registeredAppId && route is ReproDroidRoute.AppSettings
            ) ReproDroidRoute.AppVerification(requireNotNull(result.registeredAppId)) else disposition.destination
            navigate(destination)
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
                backgroundWorkAllowed = appBackgroundWorkAllowed(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val installedStateAppId = routeApp?.app?.registeredAppId
    DisposableEffect(lifecycleOwner, installedStateAppId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                installedStateAppId?.let(managedViewModel::refreshInstalledState)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val backContext = ReproDroidBackContext(
        currentAppIsInactive = routeApp?.app?.trackingState == AppTrackingState.INACTIVE.name,
        inactiveReturnRoute = inactiveReturnRoute,
        comparisonOwnerAppId = comparisonRouteApp?.app?.registeredAppId,
        verificationOwnerAppId = verificationReturnAppId,
    )

    BackHandler(enabled = route != ReproDroidRoute.Apps || appsSearchExpanded) {
        if (route == ReproDroidRoute.Apps) appsSearchExpanded = false
        else navigate(backDestination(route, backContext))
    }

    LaunchedEffect(preview.repository) {
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
                route is ReproDroidRoute.AppTechnical ||
                route is ReproDroidRoute.AppAcquisition ||
                route is ReproDroidRoute.AppVerification ||
                route is ReproDroidRoute.AppInstall)
        ) {
            navigate(ReproDroidRoute.AppInformation(routeApp.app.registeredAppId))
        }
    }

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
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                if (route == ReproDroidRoute.Apps) {
                    TopAppBar(
                        title = { Text(stringResource(R.string.apps_title)) },
                        actions = {
                            IconButton(onClick = { appsSearchExpanded = !appsSearchExpanded }) {
                                Icon(Icons.Default.Search, contentDescription = stringResource(if (appsSearchExpanded) R.string.apps_close_search else R.string.apps_open_search))
                            }
                            IconButton(onClick = { navigate(ReproDroidRoute.Settings) }) {
                                Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.root_open_settings))
                            }
                        },
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
                if (route.appId != null && routeApp != null && route !is ReproDroidRoute.AppRegistrationComplete && route !is ReproDroidRoute.AppInstall && route !is ReproDroidRoute.AppAcquisition && route !is ReproDroidRoute.AppVerification) {
                    AppActionBar(route, routeApp.app.trackingState == AppTrackingState.ACTIVE.name, ::navigate, { removalTargetId = routeApp.app.registeredAppId })
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
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
                ReproDroidRoute.Settings -> BackScaffoldTitle(stringResource(R.string.nav_settings), { navigate(ReproDroidRoute.Apps) }) {
                    SettingsHomeScreen(::navigate)
                }
                ReproDroidRoute.AppearanceSettings -> BackScaffoldTitle(stringResource(R.string.settings_appearance), { navigate(ReproDroidRoute.Settings) }) {
                    AppearanceSettingsScreen(globalSettings, managedViewModel::updateGlobalSettings)
                }
                ReproDroidRoute.AcquisitionSettings -> BackScaffoldTitle(stringResource(R.string.settings_acquisition), { navigate(ReproDroidRoute.Settings) }) {
                    AcquisitionSettingsScreen(globalSettings, managedViewModel::updateGlobalSettings)
                }
                ReproDroidRoute.ProviderSettings -> BackScaffoldTitle(stringResource(R.string.settings_service_authentication), { navigate(ReproDroidRoute.Settings) }) {
                    ProviderSettingsScreen(globalSettings, providerAuthState, managedViewModel::saveProviderToken, managedViewModel::deleteProviderToken)
                }
                ReproDroidRoute.AboutSettings -> BackScaffoldTitle(stringResource(R.string.settings_about), { navigate(ReproDroidRoute.Settings) }) {
                    AboutSettingsScreen(::navigate)
                }
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
                    onBack = { navigate(backDestination(route, backContext)) },
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
                ReproDroidRoute.UpdateSettings -> BackScaffoldTitle(stringResource(R.string.settings_updates_notifications), { navigate(ReproDroidRoute.Settings) }) {
                    UpdateSettingsScreen(
                        globalSettings, releaseCheckSettings, notificationsAllowed, backgroundWorkAllowed,
                        managedViewModel::updateReleaseCheckSettings,
                        activityResults::requestNotificationPermission, activityResults::openBackgroundSettings,
                    )
                }
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
                    onBack = { navigate(backDestination(route, backContext)) },
                )
                ReproDroidRoute.Licenses -> LicenseScreen(
                    onBack = { navigate(backDestination(route, backContext)) },
                )
                ReproDroidRoute.ThirdPartyNotices -> LicenseScreen(
                    thirdParty = true,
                    onBack = { navigate(backDestination(route, backContext)) },
                )
                ReproDroidRoute.GitHubStarsImport -> PlannedFeatureScreen(
                    title = stringResource(R.string.github_stars_title),
                    body = stringResource(R.string.github_stars_body),
                    onBack = { navigate(ReproDroidRoute.AddSource) },
                )
                is ReproDroidRoute.AppInformation -> routeApp?.let { record ->
                    val appCandidates = releaseCandidates.filter {
                        it.registeredAppId == record.app.registeredAppId
                    }
                    AppInformationScreen(
                        record = record,
                        active = record.app.registeredAppId in activeAppIds,
                        runnerJobs = runnerJobs.associateBy { it.job.jobId },
                        candidates = appCandidates,
                        schedule = releaseScheduleStates.firstOrNull {
                            it.registeredAppId == record.app.registeredAppId
                        },
                        onBack = { navigate(backDestination(route, backContext)) },
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
                        onInstall = { navigate(ReproDroidRoute.AppAcquisition(record.app.registeredAppId)) },
                        onVerification = { navigate(ReproDroidRoute.AppVerification(record.app.registeredAppId)) },
                        onCheckRelease = { managedViewModel.checkReleaseMetadataNow(record.app.registeredAppId) },
                        onComparison = { comparisonId ->
                            navigate(ReproDroidRoute.Comparison(comparisonId))
                        },
                        onResume = {
                            managedViewModel.resumeTracking(record.app.registeredAppId)
                        },
                    )
                } ?: MissingRecordScreen { navigate(ReproDroidRoute.Apps) }
                is ReproDroidRoute.AppRegistrationComplete -> Unit
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
                        onBack = { navigate(backDestination(route, backContext)) },
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
                    AppTechnicalScreen(
                        record, globalSettings, record.app.registeredAppId in activeAppIds,
                        { navigate(ReproDroidRoute.AppInformation(record.app.registeredAppId)) },
                        { managedViewModel.refresh(record.app.registeredAppId) },
                        { managedViewModel.clearSavedAssetSelection(record.app.registeredAppId) },
                        runnerJobs.associateBy { it.job.jobId }, buildEnvironmentManifests.associateBy { it.manifest.jobId },
                        buildManifestWarnings, sourceScanWarnings, sandboxWarnings, availability,
                    )
                } ?: MissingRecordScreen { navigate(ReproDroidRoute.Apps) }
                is ReproDroidRoute.AppInstall -> {
                    LaunchedEffect(route, routeApp?.app?.managementMode) {
                        routeApp?.let { record -> navigate(
                            if (record.app.managementMode == ManagementMode.VERIFICATION.name) ReproDroidRoute.AppVerification(record.app.registeredAppId)
                            else ReproDroidRoute.AppAcquisition(record.app.registeredAppId)
                        ) }
                    }
                }
                is ReproDroidRoute.AppAcquisition -> routeApp?.let { record ->
                    AppAcquisitionScreen(
                        record, globalSettings, record.app.registeredAppId in activeAppIds, availability,
                        onBack = { navigate(backDestination(route, backContext)) },
                        onRefresh = { managedViewModel.refresh(record.app.registeredAppId) },
                        onSelectReleaseAsset = { snapshotId, assetId, saveCondition -> managedViewModel.selectReleaseAsset(record.app.registeredAppId, snapshotId, assetId, saveCondition) },
                        onInstall = { managedViewModel.install(record.app.registeredAppId, it) },
                        onVerification = { navigate(ReproDroidRoute.AppVerification(record.app.registeredAppId)) },
                        onTechnical = { navigate(ReproDroidRoute.AppTechnical(record.app.registeredAppId)) },
                    )
                } ?: MissingRecordScreen { navigate(ReproDroidRoute.Apps) }
                is ReproDroidRoute.AppVerification -> routeApp?.let { record ->
                    AppVerificationScreen(
                        record, record.app.registeredAppId in activeAppIds, availability,
                        runnerJobs.associateBy { it.job.jobId }, buildEnvironmentManifests.associateBy { it.manifest.jobId },
                        buildManifestWarnings, sourceScanWarnings, sandboxWarnings,
                        onBack = { navigate(ReproDroidRoute.AppInformation(record.app.registeredAppId)) },
                        onAcquire = { navigate(ReproDroidRoute.AppAcquisition(record.app.registeredAppId)) },
                        onSettings = { navigate(ReproDroidRoute.AppSettings(record.app.registeredAppId)) },
                        onRunnerSettings = { navigate(ReproDroidRoute.RunnerSettings) },
                        onStartComparison = { managedViewModel.startComparison(record.app.registeredAppId) },
                        onConfirmComparison = { managedViewModel.confirmComparison(record.app.registeredAppId, it) },
                        onContinueComparisonSourceScan = { managedViewModel.continueComparisonSourceScan(record.app.registeredAppId, it) },
                        onRefreshComparison = { managedViewModel.refreshComparison(record.app.registeredAppId, it) },
                        onComparison = { navigate(ReproDroidRoute.Comparison(it)) },
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
}

private fun appNotificationsAllowed(context: Context): Boolean {
    val runtimeAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    return runtimeAllowed && NotificationManagerCompat.from(context).areNotificationsEnabled()
}

private fun appBackgroundWorkAllowed(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.P ||
        !(context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).isBackgroundRestricted

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
        ManagedUiMessageCode.PROVIDER_AUTHENTICATION_FAILED -> stringResource(R.string.error_provider_authentication_failed)
        ManagedUiMessageCode.NOT_FOUND -> stringResource(R.string.error_not_found)
        ManagedUiMessageCode.STALE_STATE -> stringResource(R.string.error_stale)
        ManagedUiMessageCode.OPERATION_FAILED -> stringResource(R.string.error_operation_failed)
    }
