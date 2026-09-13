package com.sanka1610.reprodroid.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.sanka1610.reprodroid.R
import com.sanka1610.reprodroid.data.local.AppGroupEntity
import com.sanka1610.reprodroid.ui.apps.UiRAppsScreen
import com.sanka1610.reprodroid.ui.shared.UiRDetailValue
import org.junit.Rule
import org.junit.Test

class Phase55PolishTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun groupFiltersRemainSelectableAndHorizontallyReachableAtFontScaleTwo() {
        val allLabel = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.group_all)
        val lastGroup = "A deliberately long final group"
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 2f)) {
                MaterialTheme {
                    Box(Modifier.width(320.dp).height(640.dp)) {
                        UiRAppsScreen(
                            apps = emptyList(),
                            groups = listOf(
                                group("one", "First long group", 0),
                                group("two", "Second long group", 1),
                                group("three", lastGroup, 2),
                            ),
                            searchExpanded = true,
                            onSelect = {},
                            onAdd = {},
                            onCreateGroup = {},
                            onRenameGroup = { _, _ -> },
                            onReorderGroups = {},
                            onDeleteGroup = {},
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithText(allLabel).assertIsDisplayed().assertIsSelected()
        composeRule.onNodeWithText(lastGroup).performScrollTo().assertIsDisplayed().performClick().assertIsSelected()
    }

    @Test
    fun truncatedLongTechnicalValueExposesCopyActionAtFontScaleTwo() {
        val copyLabel = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.action_copy)
        val value = "https://example.invalid/" + "a".repeat(160)
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 2f)) {
                MaterialTheme {
                    Box(Modifier.width(320.dp)) {
                        UiRDetailValue("Repository", value, monospace = true)
                    }
                }
            }
        }

        composeRule.onNodeWithText(value).assertIsDisplayed()
        composeRule.onNodeWithText(copyLabel).assertIsDisplayed().assertHasClickAction()
    }

    private fun group(id: String, name: String, order: Long) = AppGroupEntity(
        groupId = id,
        displayName = name,
        sortOrder = order,
        createdAt = "2026-09-13T00:00:00Z",
        updatedAt = "2026-09-13T00:00:00Z",
    )
}
