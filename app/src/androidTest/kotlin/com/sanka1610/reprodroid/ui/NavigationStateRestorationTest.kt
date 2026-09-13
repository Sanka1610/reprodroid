package com.sanka1610.reprodroid.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sanka1610.reprodroid.MainActivity
import com.sanka1610.reprodroid.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationStateRestorationTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun selectedRootRouteSurvivesActivityRecreation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val settings = context.getString(R.string.nav_settings)
        val appearance = context.getString(R.string.settings_appearance)

        compose.onNodeWithText(settings).performClick()
        compose.onNodeWithText(appearance).assertExists()

        compose.activityRule.scenario.recreate()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText(appearance).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(appearance).assertExists()
    }
}
