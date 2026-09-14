package com.sanka1610.reprodroid.ui

import android.Manifest
import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sanka1610.reprodroid.MainActivity
import com.sanka1610.reprodroid.R
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationStateRestorationTest {
    private val notificationPermission = object : ExternalResource() {
        override fun before() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val instrumentation = InstrumentationRegistry.getInstrumentation()
                instrumentation.uiAutomation.grantRuntimePermission(
                    instrumentation.targetContext.packageName,
                    Manifest.permission.POST_NOTIFICATIONS,
                )
            }
        }
    }
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(notificationPermission).around(compose)

    @Test
    fun selectedRootRouteSurvivesActivityRecreation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val settings = context.getString(R.string.root_open_settings)
        val appearance = context.getString(R.string.settings_appearance)

        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithContentDescription(settings).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodesWithContentDescription(settings)[0].performClick()
        compose.onNodeWithText(appearance).assertExists()

        compose.activityRule.scenario.recreate()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText(appearance).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(appearance).assertExists()
    }

    @Test
    fun rootNavigationKeepsSelectedPageAndContentInSync() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val settings = context.getString(R.string.root_open_settings)
        val appearance = context.getString(R.string.settings_appearance)
        val apps = context.getString(R.string.nav_apps)
        val openApps = context.getString(R.string.root_page_open, apps)
        val appsTitle = context.getString(R.string.apps_title)

        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithContentDescription(settings).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodesWithContentDescription(settings)[0].performClick()
        compose.onNodeWithText(appearance).assertExists()

        compose.onNodeWithContentDescription(openApps).performClick()
        compose.onNodeWithText(appsTitle).assertExists()
        compose.onNodeWithText(appearance).assertDoesNotExist()
    }
}
