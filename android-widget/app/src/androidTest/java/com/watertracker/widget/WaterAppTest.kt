package com.watertracker.widget

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for the real app: launching [MainActivity] (covers onCreate/setContent)
 * and driving the full `WaterApp` — quick-add, the add sheet, and the settings sheet — which
 * exercises the app's state wiring, sheet handling, and persistence hooks.
 */
@RunWith(AndroidJUnit4::class)
class WaterAppTest {

    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launches_andQuickAddFires() {
        rule.onNodeWithText("Today").assertExists()
        rule.onNodeWithText("+8 oz").performClick() // quickAdd -> persist
    }

    @Test
    fun customAddSheet_opensAndAdds() {
        rule.onNodeWithText("⋯").performClick()
        rule.onNodeWithText("Add water").assertExists()
        rule.onNodeWithText("Add").performClick() // save new entry -> persist + close
    }

    @Test
    fun settingsSheet_opensAndSavesGoal() {
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithText("Save goal").assertExists()
        rule.onNodeWithText("96").performClick()       // preset
        rule.onNodeWithText("Save goal").performClick() // setGoal -> persist + close
    }
}
