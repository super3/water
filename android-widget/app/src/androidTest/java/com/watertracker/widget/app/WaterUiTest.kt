package com.watertracker.widget.app

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/**
 * Instrumented Compose UI tests (run on a device/emulator). They render the screens, sheets,
 * shared components, and icons and exercise their interactions — covering the UI layer that
 * JVM unit tests can't reach.
 */
class WaterUiTest {

    @get:Rule val rule = createComposeRule()

    @Test
    fun todayScreen_rendersAndQuickAddFires() {
        var added = -1
        rule.setContent {
            TodayScreen(
                entries = listOf(Entry(1, System.currentTimeMillis(), 32)),
                goal = 64,
                onAdd = { amount, _ -> added = amount },
                onOpenNew = {},
                onEditEntry = {},
                onOpenGoal = {},
            )
        }
        rule.onNodeWithText("Today").assertExists()
        rule.onNodeWithText("+8 oz").assertExists()
        rule.onNodeWithText("+16 oz").assertExists()
        rule.onNodeWithText("+32 oz").performClick()
        assertEquals(32, added)
    }

    @Test
    fun entrySheet_stepAndSave() {
        var saved = -1
        rule.setContent {
            EntrySheetBody(
                draft = Entry(1, 1_000L, 16),
                isNew = false,
                onSave = { saved = it },
                onDelete = {},
                onClose = {},
            )
        }
        rule.onNodeWithText("Edit entry").assertExists()
        rule.onNodeWithText("+").performClick()       // 16 -> 20 (+4)
        rule.onNodeWithText("Save changes").performClick()
        assertEquals(20, saved)
    }

    @Test
    fun goalSheet_presetAndSave() {
        var savedGoal = -1
        rule.setContent {
            GoalSheetBody(goal = 64, onSave = { savedGoal = it }, onClose = {})
        }
        rule.onNodeWithText("Settings").assertExists()
        rule.onNodeWithText("96").performClick()       // preset chip
        rule.onNodeWithText("Save goal").performClick()
        assertEquals(96, savedGoal)
    }

    @Test
    fun sharedComponentsAndIcons_render() {
        rule.setContent {
            Column {
                Ring(value = 40, goal = 64)
                BarChart(
                    data = listOf(
                        BarDatum(LocalDate.now().minusDays(1), 50, "T"),
                        BarDatum(LocalDate.now(), 30, "F"),
                    ),
                    goal = 64,
                    selectedKey = LocalDate.now(),
                    onSelect = {},
                )
                EntryRow(Entry(1, System.currentTimeMillis(), 12), onTap = {})
                QuickChip("+8 oz", {})
                StatCard("Avg / day", "50", "oz")
                Heatmap(listOf(listOf(DayCell(LocalDate.now(), 0.5f))))
                DropIcon(); FlameIcon(); GearIcon(); ClockIcon()
                TrashIcon(); ChevronIcon(); CloseIcon(); ChartIcon()
            }
        }
        rule.onNodeWithText("12 oz").assertExists()    // EntryRow amount
    }
}
