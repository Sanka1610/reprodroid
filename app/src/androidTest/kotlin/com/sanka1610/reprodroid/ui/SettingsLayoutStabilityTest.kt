package com.sanka1610.reprodroid.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.sanka1610.reprodroid.ui.settings.SettingDivider
import com.sanka1610.reprodroid.ui.shared.DropdownSetting
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsLayoutStabilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun dividerVisibilityDoesNotMoveFollowingSetting() {
        val dividerVisible = mutableStateOf(true)
        composeRule.setContent {
            MaterialTheme {
                Column(
                    Modifier.width(320.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Before divider")
                    SettingDivider(dividerVisible.value)
                    Text("After divider")
                }
            }
        }

        val visibleBounds = composeRule.onNodeWithText("After divider").getUnclippedBoundsInRoot()
        composeRule.runOnIdle { dividerVisible.value = false }
        val hiddenBounds = composeRule.onNodeWithText("After divider").getUnclippedBoundsInRoot()

        assertEquals(visibleBounds, hiddenBounds)
    }

    @Test
    fun selectionOutlineVisibilityDoesNotMoveContent() {
        val outlineVisible = mutableStateOf(true)
        composeRule.setContent {
            MaterialTheme {
                Column(Modifier.width(320.dp)) {
                    DropdownSetting(
                        label = "Theme",
                        value = "dark",
                        options = mapOf("dark" to "Stable value"),
                        onSelect = {},
                        showOutline = outlineVisible.value,
                    )
                }
            }
        }

        val visibleBounds = composeRule.onNodeWithText("Stable value").getUnclippedBoundsInRoot()
        composeRule.runOnIdle { outlineVisible.value = false }
        val hiddenBounds = composeRule.onNodeWithText("Stable value").getUnclippedBoundsInRoot()

        assertEquals(visibleBounds, hiddenBounds)
    }
}
