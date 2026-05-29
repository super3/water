package com.watertracker.widget.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.updateAll
import com.watertracker.widget.WaterTrackerWidget
import kotlinx.coroutines.launch

private sealed interface EntrySheet {
    data class New(val ts: Long) : EntrySheet
    data class Edit(val entry: Entry) : EntrySheet
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaterApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Sync the widget when the app opens. The app starts empty — no demo data.
    LaunchedEffect(Unit) { WaterTrackerWidget().updateAll(context) }
    val state by WaterRepository.stateFlow(context).collectAsState(initial = WaterState(emptyList(), WT_GOAL_DEFAULT))
    val entries = state.entries
    val goal = state.goal

    var tab by remember { mutableStateOf("today") }
    var entrySheet by remember { mutableStateOf<EntrySheet?>(null) }
    var goalOpen by remember { mutableStateOf(false) }

    // Persist a mutation, then refresh the home-screen widget so both surfaces stay in sync.
    fun persist(block: suspend () -> Unit) {
        scope.launch {
            block()
            WaterTrackerWidget().updateAll(context)
        }
    }

    fun quickAdd(amount: Int, ts: Long) {
        persist { WaterRepository.addEntry(context, amount, ts) }
    }

    Box(Modifier.fillMaxSize().background(WT.bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    "today" -> TodayScreen(
                        entries = entries,
                        goal = goal,
                        onAdd = ::quickAdd,
                        onOpenNew = { ts -> entrySheet = EntrySheet.New(ts) },
                        onEditEntry = { entrySheet = EntrySheet.Edit(it) },
                        onOpenGoal = { goalOpen = true },
                    )
                    "history" -> HistoryScreen(entries, goal, onEditEntry = { entrySheet = EntrySheet.Edit(it) })
                    "stats" -> StatsScreen(entries, goal)
                }
            }
            BottomNav(tab) { tab = it }
        }
    }

    // Entry add/edit sheet
    if (entrySheet != null) {
        val scope = rememberCoroutineScope()
        val sheetState = rememberModalBottomSheetState()
        val current = entrySheet
        fun close() = scope.launch { sheetState.hide() }.invokeOnCompletion { entrySheet = null }
        ModalBottomSheet(
            onDismissRequest = { entrySheet = null },
            sheetState = sheetState,
            containerColor = WT.card,
            shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
            dragHandle = { SheetHandle() },
        ) {
            Box(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
                EntrySheetBody(
                    draft = (current as? EntrySheet.Edit)?.entry,
                    isNew = current is EntrySheet.New,
                    onSave = { amount ->
                        when (current) {
                            is EntrySheet.Edit -> persist { WaterRepository.editEntry(context, current.entry.id, amount) }
                            is EntrySheet.New -> persist { WaterRepository.addEntry(context, amount, current.ts) }
                            else -> {}
                        }
                        close()
                    },
                    onDelete = {
                        (current as? EntrySheet.Edit)?.let { e -> persist { WaterRepository.deleteEntry(context, e.entry.id) } }
                        close()
                    },
                    onClose = { close() },
                )
            }
        }
    }

    // Settings (goal) sheet
    if (goalOpen) {
        val scope = rememberCoroutineScope()
        val sheetState = rememberModalBottomSheetState()
        fun close() = scope.launch { sheetState.hide() }.invokeOnCompletion { goalOpen = false }
        ModalBottomSheet(
            onDismissRequest = { goalOpen = false },
            sheetState = sheetState,
            containerColor = WT.card,
            shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
            dragHandle = { SheetHandle() },
        ) {
            Box(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
                GoalSheetBody(
                    goal = goal,
                    onSave = { g -> persist { WaterRepository.setGoal(context, g) }; close() },
                    onClose = { close() },
                )
            }
        }
    }
}

@Composable
private fun SheetHandle() {
    Box(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(width = 40.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(WT.track))
    }
}

/**
 * Bottom navigation. Per the handoff, the prototype ships a single destination so the
 * nav is hidden; History/Stats are built and can be re-enabled by adding their items here.
 */
@Composable
private fun BottomNav(tab: String, setTab: (String) -> Unit) {
    data class NavItem(val id: String, val label: String, val icon: @Composable (Color) -> Unit)
    val items = listOf(
        NavItem("today", "Today") { c -> DropIcon(size = 22.dp, color = c) },
        // NavItem("history", "History") { c -> ChartIcon(size = 22.dp, color = c) },
        // NavItem("stats", "Stats") { c -> FlameIcon(size = 22.dp, color = c) },
    )
    if (items.size < 2) return // single destination — hide the nav entirely

    Row(Modifier.fillMaxWidth().background(WT.card).padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 6.dp)) {
        items.forEach { item ->
            val on = tab == item.id
            Column(
                Modifier.weight(1f).clickable { setTab(item.id) }.padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    Modifier.clip(RoundedCornerShape(999.dp)).background(if (on) tint(WT.accent, 0.16f) else Color.Transparent).padding(horizontal = 18.dp, vertical = 4.dp),
                ) { item.icon(if (on) WT.accent else WT.faint) }
                Text(item.label, fontSize = 11.sp, fontWeight = FontWeight.W800, color = if (on) WT.accent else WT.faint, fontFamily = WT.font)
            }
        }
    }
}
