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
            ReproDroidRoute.Groups to ReproDroidRoute.Apps,
            ReproDroidRoute.NotificationSettings to ReproDroidRoute.Settings,
            ReproDroidRoute.DataCleanup to ReproDroidRoute.DataManagement,
            ReproDroidRoute.DataAudit to ReproDroidRoute.DataManagement,
            ReproDroidRoute.DataRunnerStorage to ReproDroidRoute.DataManagement,
            ReproDroidRoute.InactiveApps to ReproDroidRoute.Settings,
            ReproDroidRoute.AppearanceSettings to ReproDroidRoute.Settings,
            ReproDroidRoute.AcquisitionSettings to ReproDroidRoute.Settings,
            ReproDroidRoute.ProviderSettings to ReproDroidRoute.Settings,
            ReproDroidRoute.AboutSettings to ReproDroidRoute.Settings,
            ReproDroidRoute.Settings to ReproDroidRoute.Apps,
            ReproDroidRoute.AddSource to ReproDroidRoute.Apps,
            ReproDroidRoute.DataManagement to ReproDroidRoute.Settings,
            ReproDroidRoute.RunnerSettings to ReproDroidRoute.Settings,
            ReproDroidRoute.UpdateSettings to ReproDroidRoute.Settings,
            ReproDroidRoute.Authentication to ReproDroidRoute.RunnerSettings,
            ReproDroidRoute.LogExport to ReproDroidRoute.DataManagement,
            ReproDroidRoute.Licenses to ReproDroidRoute.AboutSettings,
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
        assertEquals(ReproDroidRoute.AddSource, backDestination(ReproDroidRoute.AddOptions))
        assertEquals(ReproDroidRoute.AddSource, backDestination(ReproDroidRoute.AddConfirm))
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
        assertEquals(
            ReproDroidRoute.AppInformation(appId),
            backDestination(ReproDroidRoute.AppRegistrationComplete(appId)),
        )
        assertEquals(
            ReproDroidRoute.AppInformation(appId),
            backDestination(ReproDroidRoute.AppInstall(appId)),
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
    fun verificationPrerequisitesReturnOnlyToTheMatchingWorkflow() {
        val context = ReproDroidBackContext(verificationOwnerAppId = appId)
        listOf(ReproDroidRoute.AppAcquisition(appId), ReproDroidRoute.AppSettings(appId), ReproDroidRoute.RunnerSettings).forEach {
            assertEquals(ReproDroidRoute.AppVerification(appId), backDestination(it, context))
        }
        assertEquals(ReproDroidRoute.AppInformation(comparisonId), backDestination(ReproDroidRoute.AppSettings(comparisonId), context))
        assertEquals(ReproDroidRoute.RunnerSettings, backDestination(ReproDroidRoute.Authentication, context))
        assertEquals(ReproDroidRoute.Settings, backDestination(ReproDroidRoute.RunnerSettings, ReproDroidBackContext(verificationOwnerAppId = "invalid")))
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
