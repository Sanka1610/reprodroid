package com.sanka1610.reprodroid.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReproDroidRouteTest {
    @Test
    fun fixedRoutesRoundTrip() {
        val routes = listOf(
            ReproDroidRoute.Apps,
            ReproDroidRoute.InactiveApps,
            ReproDroidRoute.AddSource,
            ReproDroidRoute.AddAnalysis,
            ReproDroidRoute.AddOptions,
            ReproDroidRoute.AddConfirm,
            ReproDroidRoute.Settings,
            ReproDroidRoute.AppearanceSettings,
            ReproDroidRoute.AcquisitionSettings,
            ReproDroidRoute.ProviderSettings,
            ReproDroidRoute.AboutSettings,
            ReproDroidRoute.DataManagement,
            ReproDroidRoute.DataStorage,
            ReproDroidRoute.DataInactive,
            ReproDroidRoute.RunnerSettings,
            ReproDroidRoute.RunnerStorage,
            ReproDroidRoute.Toolchains,
            ReproDroidRoute.Jobs,
            ReproDroidRoute.UpdateSettings,
            ReproDroidRoute.Authentication,
            ReproDroidRoute.LogExport,
            ReproDroidRoute.Licenses,
            ReproDroidRoute.ThirdPartyNotices,
            ReproDroidRoute.GitHubStarsImport,
        )

        routes.forEach { route -> assertEquals(route, ReproDroidRoute.parse(route.encode())) }
        assertEquals(ReproDroidRoute.LogExport, ReproDroidRoute.parse("settings/backup"))
    }

    @Test
    fun appRoutesUseCanonicalUuidAndRoundTrip() {
        val id = "00000000-0000-0000-0000-000000000001"
        val routes = listOf(
            ReproDroidRoute.AppInformation(id),
            ReproDroidRoute.AppRegistrationComplete(id),
            ReproDroidRoute.AppInstall(id),
            ReproDroidRoute.AppAcquisition(id),
            ReproDroidRoute.AppVerification(id),
            ReproDroidRoute.AppEdit(id),
            ReproDroidRoute.AppSettings(id),
            ReproDroidRoute.AppTechnical(id),
            ReproDroidRoute.Comparison(id),
        )

        routes.forEach { route -> assertEquals(route, ReproDroidRoute.parse(route.encode())) }
        assertEquals(ReproDroidRoute.AppInformation(id), ReproDroidRoute.appInformation(id))
    }

    @Test
    fun malformedOrNonCanonicalAppIdsFallBackToApps() {
        assertEquals(ReproDroidRoute.Apps, ReproDroidRoute.parse("apps/not-a-uuid/information"))
        assertEquals(
            ReproDroidRoute.Apps,
            ReproDroidRoute.parse("apps/00000000-0000-0000-0000-000000000001/unknown"),
        )
        assertEquals(ReproDroidRoute.Apps, ReproDroidRoute.appInformation("NOT-A-UUID"))
    }

    @Test
    fun appsIsTheOnlyRootAndLegacyAddStepsRemainReadable() {
        assertTrue(ReproDroidRoute.Apps.isRoot)
        assertFalse(ReproDroidRoute.AddSource.isRoot)
        assertFalse(ReproDroidRoute.Settings.isRoot)
        ReproDroidRoute.addFlowRoutes.forEach { route -> assertTrue(route.isAddFlow) }
    }
}
