package com.sanka1610.reprodroid.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReproDroidNavigationPolicyTest {
    private val appId = "00000000-0000-0000-0000-000000000001"
    private val comparisonId = "00000000-0000-0000-0000-000000000002"

    @Test
    fun settingsAndRunnerChildrenReturnToTheirCurrentParents() {
        val expected = mapOf(
            ReproDroidRoute.InactiveApps to ReproDroidRoute.Settings,
            ReproDroidRoute.DataManagement to ReproDroidRoute.Settings,
            ReproDroidRoute.RunnerSettings to ReproDroidRoute.Settings,
            ReproDroidRoute.UpdateSettings to ReproDroidRoute.Settings,
            ReproDroidRoute.Authentication to ReproDroidRoute.Settings,
            ReproDroidRoute.LogExport to ReproDroidRoute.Settings,
            ReproDroidRoute.Licenses to ReproDroidRoute.Settings,
            ReproDroidRoute.DataStorage to ReproDroidRoute.DataManagement,
            ReproDroidRoute.DataInactive to ReproDroidRoute.DataManagement,
            ReproDroidRoute.RunnerStorage to ReproDroidRoute.RunnerSettings,
            ReproDroidRoute.Toolchains to ReproDroidRoute.RunnerSettings,
            ReproDroidRoute.Jobs to ReproDroidRoute.RunnerSettings,
        )

        expected.forEach { (current, destination) ->
            assertEquals(destination, backDestination(current))
        }
    }

    @Test
    fun addFlowAndAppChildrenReturnOneCurrentStep() {
        assertEquals(ReproDroidRoute.AddSource, backDestination(ReproDroidRoute.GitHubStarsImport))
        assertEquals(ReproDroidRoute.AddSource, backDestination(ReproDroidRoute.AddAnalysis))
        assertEquals(ReproDroidRoute.AddAnalysis, backDestination(ReproDroidRoute.AddOptions))
        assertEquals(ReproDroidRoute.AddOptions, backDestination(ReproDroidRoute.AddConfirm))
        assertEquals(
            ReproDroidRoute.AppInformation(appId),
            backDestination(ReproDroidRoute.AppEdit(appId)),
        )
        assertEquals(
            ReproDroidRoute.AppInformation(appId),
            backDestination(ReproDroidRoute.AppSettings(appId)),
        )
        assertEquals(
            ReproDroidRoute.AppInformation(appId),
            backDestination(ReproDroidRoute.AppTechnical(appId)),
        )
    }

    @Test
    fun inactiveAndComparisonBackRoutesUseTheirRecordedOwners() {
        assertEquals(
            ReproDroidRoute.InactiveApps,
            backDestination(
                ReproDroidRoute.AppInformation(appId),
                ReproDroidBackContext(currentAppIsInactive = true),
            ),
        )
        assertEquals(
            ReproDroidRoute.Settings,
            backDestination(
                ReproDroidRoute.AppInformation(appId),
                ReproDroidBackContext(
                    currentAppIsInactive = true,
                    inactiveReturnRoute = ReproDroidRoute.Settings.encode(),
                ),
            ),
        )
        assertEquals(
            ReproDroidRoute.AppInformation(appId),
            backDestination(
                ReproDroidRoute.Comparison(comparisonId),
                ReproDroidBackContext(comparisonOwnerAppId = appId),
            ),
        )
        assertEquals(ReproDroidRoute.Apps, backDestination(ReproDroidRoute.Comparison(comparisonId)))
    }

    @Test
    fun missingAppRouteWaitsForCatalogThenFailsClosedToApps() {
        val route = ReproDroidRoute.AppInformation(appId)

        assertNull(missingAppDestination(route, appCatalogLoaded = false, appRecordPresent = false))
        assertNull(missingAppDestination(route, appCatalogLoaded = true, appRecordPresent = true))
        assertNull(
            missingAppDestination(
                ReproDroidRoute.Comparison(comparisonId),
                appCatalogLoaded = true,
                appRecordPresent = false,
            ),
        )
        assertEquals(
            ReproDroidRoute.Apps,
            missingAppDestination(route, appCatalogLoaded = true, appRecordPresent = false),
        )
    }

    @Test
    fun releaseNotificationTargetsExactAppInformationRoute() {
        assertEquals("apps/$appId/information", releaseNotificationRoute(appId))
        assertEquals(
            ReproDroidRoute.AppInformation(appId),
            ReproDroidRoute.parse(releaseNotificationRoute(appId)),
        )
    }
}
