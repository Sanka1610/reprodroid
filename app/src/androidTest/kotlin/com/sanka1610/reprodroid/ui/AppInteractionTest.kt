package com.sanka1610.reprodroid.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.sanka1610.reprodroid.R
import com.sanka1610.reprodroid.data.local.RegisteredAppEntity
import com.sanka1610.reprodroid.data.local.RegisteredAppRecord
import com.sanka1610.reprodroid.ui.appdetail.AppInformationScreen
import com.sanka1610.reprodroid.ui.apps.UiRAppsScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AppInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun longPressStartsMultipleSelectionWithoutOpeningTheApp() {
        val selected = mutableStateOf(emptySet<String>())
        var opened = 0
        compose.setContent {
            MaterialTheme {
                Box(Modifier.width(360.dp).height(640.dp)) {
                    UiRAppsScreen(
                        apps = listOf(record("First"), record("Second")), groups = emptyList(), query = "", grouped = false,
                        selectedIds = selected.value,
                        onToggleSelection = { selected.value = if (it in selected.value) selected.value - it else selected.value + it },
                        onSelect = { opened++ }, onAdd = {}, onManageGroups = {},
                    )
                }
            }
        }
        compose.onNodeWithText("First").performTouchInput { longClick() }
        compose.onNodeWithText("First").assertIsSelected()
        compose.onNodeWithText("Second").performClick().assertIsSelected()
        compose.runOnIdle { assertEquals(setOf("First", "Second"), selected.value); assertEquals(0, opened) }
        compose.onNodeWithText("First").performClick().assertIsNotSelected()
    }

    @Test fun listPullDownInvokesReleaseRefresh() {
        var refreshed = 0
        compose.setContent {
            MaterialTheme {
                Box(Modifier.width(360.dp).height(640.dp)) {
                    UiRAppsScreen(listOf(record("First")), emptyList(), "", false,
                        onRefresh = { refreshed++ }, onSelect = {}, onAdd = {}, onManageGroups = {})
                }
            }
        }
        compose.onNode(hasScrollAction()).performTouchInput { swipeDown() }
        compose.runOnIdle { assertEquals(1, refreshed) }
    }

    @Test fun detailHasOneRefreshActionAndAcquisitionBesideLatestVersion() {
        var refreshed = 0
        var acquired = 0
        compose.setContent {
            MaterialTheme {
                Box(Modifier.width(360.dp).height(800.dp)) {
                    AppInformationScreen(record("Example"), active = false, runnerJobs = emptyMap(), candidates = emptyList(), schedule = null,
                        onBack = {}, onOpenCandidate = {}, onTechnical = {}, onInstall = { acquired++ }, onInstallNow = { acquired++ },
                        onVerification = {}, onCheckRelease = { refreshed++ }, onComparison = {}, onResume = {})
                }
            }
        }
        val refresh = context.getString(R.string.app_flow_check_release)
        val acquire = context.getString(R.string.app_acquisition_open)
        compose.onAllNodesWithContentDescription(refresh).assertCountEquals(1)
        compose.onNodeWithContentDescription(refresh).performClick()
        val latestBounds = compose.onNodeWithText(context.getString(R.string.label_latest_version)).fetchSemanticsNode().boundsInRoot
        val acquireBounds = compose.onNodeWithContentDescription(acquire).fetchSemanticsNode().boundsInRoot
        assertTrue(acquireBounds.center.y > latestBounds.top && acquireBounds.center.y < latestBounds.bottom + latestBounds.height * 2)
        compose.onNodeWithContentDescription(acquire).performClick()
        compose.onNode(hasScrollAction()).performTouchInput { swipeDown() }
        compose.runOnIdle { assertEquals(2, refreshed); assertEquals(1, acquired) }
        val label = compose.onNodeWithText(context.getString(R.string.label_verification)).fetchSemanticsNode().boundsInRoot
        val values = compose.onAllNodesWithText(context.getString(R.string.state_not_checked), useUnmergedTree = true)
        val index = values.fetchSemanticsNodes().indexOfFirst { kotlin.math.abs(it.boundsInRoot.center.y - label.center.y) < label.height }
        assertTrue(index >= 0)
        val value = values[index].fetchSemanticsNode().boundsInRoot
        val layouts = mutableListOf<TextLayoutResult>()
        values[index].performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val textStart = value.left + layouts.single().getBoundingBox(0).left
        assertTrue(textStart > label.left)
        assertTrue(textStart < label.left + (value.right - label.left) * 0.65f)
    }

    private fun record(name: String) = RegisteredAppRecord(
        app = RegisteredAppEntity(
            registeredAppId = name, displayName = name,
            repositoryUrl = "https://github.com/Example/Project", canonicalRepositoryUrl = "https://github.com/example/project",
            provider = "GITHUB_RELEASES", managementMode = "ACQUISITION", createdAt = "2026-09-22T00:00:00Z", updatedAt = "2026-09-22T00:00:00Z",
        ), releases = emptyList(), comparisons = emptyList(), releaseInstallAttempts = emptyList(),
    )
}
