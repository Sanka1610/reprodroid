package com.sanka1610.reprodroid.ui.navigation

/**
 * Inputs that affect back navigation without changing the encoded route itself.
 *
 * These values are intentionally identifiers and booleans rather than database entities so the
 * navigation contract remains testable without moving repository validation into the UI layer.
 */
internal data class ReproDroidBackContext(
    val currentAppIsInactive: Boolean = false,
    val inactiveReturnRoute: String = ReproDroidRoute.InactiveApps.encode(),
    val comparisonOwnerAppId: String? = null,
    val verificationOwnerAppId: String? = null,
)

internal fun backDestination(
    current: ReproDroidRoute,
    context: ReproDroidBackContext = ReproDroidBackContext(),
): ReproDroidRoute {
    val owner = context.verificationOwnerAppId
    if (owner != null && ReproDroidRoute.appInformation(owner) is ReproDroidRoute.AppInformation &&
        (current == ReproDroidRoute.RunnerSettings ||
            (current is ReproDroidRoute.AppAcquisition || current is ReproDroidRoute.AppSettings) && current.appId == owner)
    ) return ReproDroidRoute.AppVerification(owner)
    return when (current) {
        ReproDroidRoute.AppearanceSettings,
        ReproDroidRoute.AcquisitionSettings,
        ReproDroidRoute.ProviderSettings,
        ReproDroidRoute.AboutSettings,
        ReproDroidRoute.InactiveApps,
        ReproDroidRoute.DataManagement,
        ReproDroidRoute.RunnerSettings,
        ReproDroidRoute.UpdateSettings,
        ReproDroidRoute.NotificationSettings,
        -> ReproDroidRoute.Settings
        ReproDroidRoute.Licenses,
        ReproDroidRoute.ThirdPartyNotices,
        -> ReproDroidRoute.AboutSettings
        ReproDroidRoute.LogExport,
        ReproDroidRoute.DataStorage,
        ReproDroidRoute.DataCleanup,
        ReproDroidRoute.DataAudit,
        ReproDroidRoute.DataRunnerStorage,
        ReproDroidRoute.DataInactive,
        -> ReproDroidRoute.DataManagement
        ReproDroidRoute.Authentication,
        ReproDroidRoute.RunnerStorage,
        ReproDroidRoute.Toolchains,
        ReproDroidRoute.Jobs,
        -> ReproDroidRoute.RunnerSettings
        ReproDroidRoute.GitHubStarsImport,
        ReproDroidRoute.AddAnalysis,
        -> ReproDroidRoute.AddSource
        ReproDroidRoute.AddOptions -> ReproDroidRoute.AddSource
        ReproDroidRoute.AddConfirm -> ReproDroidRoute.AddSource
        is ReproDroidRoute.AppEdit,
        is ReproDroidRoute.AppSettings,
        is ReproDroidRoute.AppTechnical,
        is ReproDroidRoute.AppRegistrationComplete,
        is ReproDroidRoute.AppAcquisition,
        is ReproDroidRoute.AppVerification,
        is ReproDroidRoute.AppInstall,
        -> ReproDroidRoute.AppInformation(requireNotNull(current.appId))
        is ReproDroidRoute.AppInformation ->
            if (context.currentAppIsInactive) {
                ReproDroidRoute.parse(context.inactiveReturnRoute)
            } else {
                ReproDroidRoute.Apps
            }
        is ReproDroidRoute.Comparison -> context.comparisonOwnerAppId?.let(ReproDroidRoute::AppInformation)
            ?: ReproDroidRoute.Apps
        ReproDroidRoute.Groups,
        ReproDroidRoute.Settings,
        ReproDroidRoute.AddSource,
        -> ReproDroidRoute.Apps
        else -> current
    }
}

/** Returns the fail-closed destination used after the app catalog proves an app route is stale. */
internal fun missingAppDestination(
    current: ReproDroidRoute,
    appCatalogLoaded: Boolean,
    appRecordPresent: Boolean,
): ReproDroidRoute? = ReproDroidRoute.Apps.takeIf {
    appCatalogLoaded && current.appId != null && !appRecordPresent
}

/** Exact route placed in release-notification intents. */
internal fun releaseNotificationRoute(registeredAppId: String): String =
    ReproDroidRoute.AppInformation(registeredAppId).encode()
