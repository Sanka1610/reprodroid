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
)

internal fun backDestination(
    current: ReproDroidRoute,
    context: ReproDroidBackContext = ReproDroidBackContext(),
): ReproDroidRoute = when (current) {
    ReproDroidRoute.InactiveApps,
    ReproDroidRoute.DataManagement,
    ReproDroidRoute.RunnerSettings,
    ReproDroidRoute.UpdateSettings,
    ReproDroidRoute.Authentication,
    ReproDroidRoute.LogExport,
    ReproDroidRoute.Licenses,
    -> ReproDroidRoute.Settings
    ReproDroidRoute.DataStorage,
    ReproDroidRoute.DataInactive,
    -> ReproDroidRoute.DataManagement
    ReproDroidRoute.RunnerStorage,
    ReproDroidRoute.Toolchains,
    ReproDroidRoute.Jobs,
    -> ReproDroidRoute.RunnerSettings
    ReproDroidRoute.GitHubStarsImport,
    ReproDroidRoute.AddAnalysis,
    -> ReproDroidRoute.AddSource
    ReproDroidRoute.AddOptions -> ReproDroidRoute.AddAnalysis
    ReproDroidRoute.AddConfirm -> ReproDroidRoute.AddOptions
    is ReproDroidRoute.AppEdit,
    is ReproDroidRoute.AppSettings,
    is ReproDroidRoute.AppTechnical,
    -> ReproDroidRoute.AppInformation(requireNotNull(current.appId))
    is ReproDroidRoute.AppInformation ->
        if (context.currentAppIsInactive) {
            ReproDroidRoute.parse(context.inactiveReturnRoute)
        } else {
            ReproDroidRoute.Apps
        }
    is ReproDroidRoute.Comparison -> context.comparisonOwnerAppId?.let(ReproDroidRoute::AppInformation)
        ?: ReproDroidRoute.Apps
    else -> current
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
