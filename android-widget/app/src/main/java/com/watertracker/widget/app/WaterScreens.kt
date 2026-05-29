package com.watertracker.widget.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val weekdayFullFmt = DateTimeFormatter.ofPattern("EEEE", Locale.US)
private val monthDayFmt = DateTimeFormatter.ofPattern("MMMM d", Locale.US)
private fun weekdayLetter(d: LocalDate) =
    d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US).first().toString()

// Small circular chevron button for day navigation (card-filled when enabled, dimmed when not).
@Composable
private fun NavArrow(pointLeft: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .size(32.dp)
            .alpha(if (enabled) 1f else 0.3f)
            .clip(CircleShape)
            .then(if (enabled) Modifier.background(WT.card).border(1.dp, WT.track, CircleShape) else Modifier)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Box(if (pointLeft) Modifier.rotate(180f) else Modifier) {
            ChevronIcon(size = 17.dp, color = WT.dim)
        }
    }
}

// ════════════════════════════ TODAY ════════════════════════════
@Composable
fun TodayScreen(
    entries: List<Entry>,
    goal: Int,
    onAdd: (Int, Long) -> Unit,
    onOpenNew: (Long) -> Unit,
    onEditEntry: (Entry) -> Unit,
    onOpenGoal: () -> Unit,
) {
    val today = LocalDate.now()
    var selectedDay by remember { mutableStateOf(today) }
    val isToday = selectedDay == today

    val total = sumForDay(entries, selectedDay)
    val dayEntries = entriesForDay(entries, selectedDay)
    val streak = currentStreak(entries, goal)
    val remaining = max(0, goal - total)

    val weekData = lastNDays(7).map { BarDatum(it, sumForDay(entries, it), weekdayLetter(it)) }
    val weekAvg = (weekData.sumOf { it.value } / 7.0).roundToInt()
    val weekMet = weekData.count { it.value >= goal }

    val title = when {
        isToday -> "Today"
        selectedDay == today.minusDays(1) -> "Yesterday"
        else -> selectedDay.format(weekdayFullFmt)
    }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 24.dp),
    ) {
        // App-bar header: streak pill (left) · day stepper (center) · settings gear (right).
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            // Streak pill
            Row(
                Modifier.height(40.dp).clip(RoundedCornerShape(999.dp)).background(tint(WT.streak, 0.14f)).padding(horizontal = 13.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FlameIcon(size = 17.dp, color = WT.streak)
                Text("$streak", fontSize = 15.sp, fontWeight = FontWeight.W800, color = WT.streak, fontFamily = WT.font)
            }

            // Centered day stepper
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NavArrow(pointLeft = true) { selectedDay = selectedDay.minusDays(1) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(title, fontSize = 17.sp, fontWeight = FontWeight.W800, color = WT.ink, letterSpacing = (-0.01f).em, maxLines = 1, fontFamily = WT.font)
                    Text(selectedDay.format(monthDayFmt), fontSize = 13.sp, fontWeight = FontWeight.W600, color = WT.dim, maxLines = 1, fontFamily = WT.font)
                }
                NavArrow(pointLeft = false, enabled = !isToday) { if (!isToday) selectedDay = selectedDay.plusDays(1) }
            }

            // Settings gear
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(WT.card).border(1.dp, WT.track, CircleShape)
                    .clickable { onOpenGoal() }
                    .semantics { contentDescription = "Settings" },
                contentAlignment = Alignment.Center,
            ) { GearIcon(size = 20.dp, color = WT.dim) }
        }

        // Ring
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Ring(value = total, goal = goal, size = 232.dp)
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (remaining > 0) {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = WT.ink)) { append("$remaining oz") }
                        withStyle(SpanStyle(color = WT.dim)) { append(if (isToday) " to go" else " under goal") }
                    },
                    fontSize = 14.sp, fontWeight = FontWeight.W700, fontFamily = WT.font,
                )
            } else {
                Text(if (isToday) "You hit your goal today" else "Goal reached", fontSize = 14.sp, fontWeight = FontWeight.W700, color = WT.dim, fontFamily = WT.font)
            }
        }

        // Quick add (logs onto the day you're viewing)
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickChip("+8 oz", { onAdd(8, timestampFor(selectedDay)) }, modifier = Modifier.weight(1f))
            QuickChip("+16 oz", { onAdd(16, timestampFor(selectedDay)) }, modifier = Modifier.weight(1f))
            QuickChip("+32 oz", { onAdd(32, timestampFor(selectedDay)) }, modifier = Modifier.weight(1f))
            Box(
                Modifier.width(50.dp).height(48.dp).clip(RoundedCornerShape(16.dp)).background(tint(WT.accent, 0.12f))
                    .border(1.dp, tint(WT.accent, 0.25f), RoundedCornerShape(16.dp)).clickable { onOpenNew(timestampFor(selectedDay)) },
                contentAlignment = Alignment.Center,
            ) { Text("⋯", fontSize = 22.sp, fontWeight = FontWeight.W800, color = WT.accent, fontFamily = WT.font) }
        }

        // This week (tap a bar to jump to that day)
        Spacer(Modifier.height(26.dp))
        SectionHead("This week") {
            Text("avg $weekAvg oz · $weekMet/7 met", fontSize = 13.sp, fontWeight = FontWeight.W700, color = WT.dim, fontFamily = WT.font)
        }
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(WT.card).padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 12.dp)) {
            BarChart(data = weekData, goal = goal, selectedKey = selectedDay, onSelect = { selectedDay = it }, height = 120.dp)
        }

        // Log for the selected day
        Spacer(Modifier.height(26.dp))
        SectionHead(if (isToday) "Today’s log" else "Log") {
            Text("${dayEntries.size} ${if (dayEntries.size == 1) "entry" else "entries"}", fontSize = 13.sp, fontWeight = FontWeight.W700, color = WT.dim, fontFamily = WT.font)
        }
        if (dayEntries.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(vertical = 28.dp), contentAlignment = Alignment.Center) {
                Text(if (isToday) "No water logged yet — tap a button above." else "Nothing logged this day.", fontSize = 14.sp, fontWeight = FontWeight.W600, color = WT.faint, fontFamily = WT.font)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                dayEntries.forEach { EntryRow(it, onEditEntry) }
            }
        }
    }
}

// ════════════════════════════ HISTORY ════════════════════════════
@Composable
fun HistoryScreen(entries: List<Entry>, goal: Int, onEditEntry: (Entry) -> Unit) {
    var mode by remember { mutableStateOf("week") }
    val days = if (mode == "week") lastNDays(7) else lastNDays(30)
    val data = days.map { d ->
        val label = if (mode == "week") weekdayLetter(d)
        else if (d.dayOfMonth % 5 == 0 || d.dayOfMonth == 1) d.dayOfMonth.toString() else ""
        BarDatum(d, sumForDay(entries, d), label)
    }
    var selected by remember(mode) { mutableStateOf(data.last().key) }
    val selData = entriesForDay(entries, selected)
    val selTotal = sumForDay(entries, selected)
    val avg = (data.sumOf { it.value } / data.size.toDouble()).roundToInt()
    val metCount = data.count { it.value >= goal }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp)) {
        Text("History", fontSize = 28.sp, fontWeight = FontWeight.W800, color = WT.ink, letterSpacing = (-0.02f).em, fontFamily = WT.font)
        Spacer(Modifier.height(16.dp))

        // Week/Month toggle
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(WT.card).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("week" to "Week", "month" to "Month").forEach { (id, label) ->
                val on = mode == id
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(11.dp)).background(if (on) WT.cardHi else androidx.compose.ui.graphics.Color.Transparent).clickable { mode = id }.padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(label, fontSize = 14.sp, fontWeight = FontWeight.W800, color = if (on) WT.ink else WT.dim, fontFamily = WT.font) }
            }
        }

        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("Avg / day", "$avg", "oz", Modifier.weight(1f))
            StatCard("Goal met", "$metCount", if (mode == "week") "/ 7 days" else "/ 30 days", Modifier.weight(1f))
        }

        Spacer(Modifier.height(20.dp))
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(WT.card).padding(start = 18.dp, end = 18.dp, top = 22.dp, bottom = 14.dp)) {
            BarChart(data = data, goal = goal, selectedKey = selected, onSelect = { selected = it }, height = if (mode == "week") 150.dp else 130.dp)
        }

        Spacer(Modifier.height(22.dp))
        SectionHead(formatDayLabel(selected)) {
            Text("$selTotal oz", fontSize = 13.sp, fontWeight = FontWeight.W800, color = if (selTotal >= goal) WT.good else WT.dim, fontFamily = WT.font)
        }
        if (selData.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                Text("No entries this day.", fontSize = 14.sp, fontWeight = FontWeight.W600, color = WT.faint, fontFamily = WT.font)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { selData.forEach { EntryRow(it, onEditEntry) } }
        }
    }
}

// ════════════════════════════ STATS ════════════════════════════
@Composable
fun StatsScreen(entries: List<Entry>, goal: Int) {
    val streak = currentStreak(entries, goal)
    val longest = longestStreak(entries, goal)
    val last7 = lastNDays(7).map { sumForDay(entries, it) }
    val last30 = lastNDays(30).map { sumForDay(entries, it) }
    val avg7 = (last7.sum() / 7.0).roundToInt()
    val avg30 = (last30.sum() / 30.0).roundToInt()
    val totalLogged = entries.sumOf { it.amount }
    val best = last30.maxOrNull() ?: 0

    // Heatmap: 9 weeks, columns split on Sunday.
    val weeks = ArrayList<List<DayCell>>()
    var col = ArrayList<DayCell>()
    lastNDays(63).forEach { d ->
        col.add(DayCell(d, min(sumForDay(entries, d) / goal.toFloat(), 1f)))
        if (d.dayOfWeek.value == 7) { weeks.add(col); col = ArrayList() } // Sunday ends a column
    }
    if (col.isNotEmpty()) weeks.add(col)

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp)) {
        Text("Stats", fontSize = 28.sp, fontWeight = FontWeight.W800, color = WT.ink, letterSpacing = (-0.02f).em, fontFamily = WT.font)
        Spacer(Modifier.height(16.dp))

        // Streak hero
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(WT.card).padding(22.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(tint(WT.streak, 0.18f)), contentAlignment = Alignment.Center) {
                FlameIcon(size = 36.dp, color = WT.streak)
            }
            Column {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontSize = 40.sp, fontWeight = FontWeight.W800, color = WT.ink, letterSpacing = (-0.03f).em)) { append("$streak") }
                        withStyle(SpanStyle(fontSize = 18.sp, color = WT.dim)) { append("  days") }
                    },
                    fontFamily = WT.font,
                )
                Spacer(Modifier.height(4.dp))
                Text("Current streak · best $longest", fontSize = 14.sp, fontWeight = FontWeight.W700, color = WT.dim, fontFamily = WT.font)
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("7-day avg", "$avg7", "oz", Modifier.weight(1f))
            StatCard("30-day avg", "$avg30", "oz", Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("Best day", "$best", "oz", Modifier.weight(1f))
            val totalStr = if (totalLogged >= 1000) String.format(Locale.US, "%.1fk", totalLogged / 1000.0) else "$totalLogged"
            StatCard("Total logged", totalStr, "oz", Modifier.weight(1f))
        }

        Spacer(Modifier.height(22.dp))
        SectionHead("Last 9 weeks")
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(WT.card).padding(18.dp)) {
            Heatmap(weeks)
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Less", fontSize = 11.sp, fontWeight = FontWeight.W700, color = WT.faint, fontFamily = WT.font)
                listOf(0.2f, 0.45f, 0.7f, 1f).forEach { f ->
                    Box(Modifier.size(14.dp).clip(RoundedCornerShape(4.dp)).background(tint(if (f >= 1f) WT.good else WT.accent, min(0.25f + f * 0.75f, 1f))))
                }
                Text("Goal", fontSize = 11.sp, fontWeight = FontWeight.W700, color = WT.faint, fontFamily = WT.font)
            }
        }
    }
}

// ════════════════════════ EDIT / ADD SHEET BODY ════════════════════════
@Composable
fun EntrySheetBody(draft: Entry?, isNew: Boolean, onSave: (Int) -> Unit, onDelete: () -> Unit, onClose: () -> Unit) {
    var amount by remember { mutableIntStateOf(draft?.amount ?: 16) }
    fun step(n: Int) { amount = max(1, min(400, amount + n)) }

    Column {
        Row(Modifier.fillMaxWidth().padding(bottom = 18.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(if (isNew) "Add water" else "Edit entry", fontSize = 20.sp, fontWeight = FontWeight.W800, color = WT.ink, fontFamily = WT.font)
            Box(Modifier.size(30.dp).clickable { onClose() }, contentAlignment = Alignment.Center) { CloseIcon() }
        }

        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            StepBtn("−") { step(-4) }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("$amount", Modifier.alignByBaseline(), fontSize = 52.sp, fontWeight = FontWeight.W800, color = WT.ink, letterSpacing = (-0.03f).em, fontFamily = WT.font)
                Text("oz", Modifier.alignByBaseline(), fontSize = 20.sp, fontWeight = FontWeight.W700, color = WT.dim, fontFamily = WT.font)
            }
            StepBtn("+") { step(4) }
        }

        if (!isNew && draft != null) {
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                ClockIcon(size = 14.dp, color = WT.faint)
                Text("Logged at ${formatTime(draft.ts)}", fontSize = 13.sp, fontWeight = FontWeight.W700, color = WT.dim, fontFamily = WT.font)
            }
        }

        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!isNew) {
                Box(
                    Modifier.size(54.dp).clip(RoundedCornerShape(16.dp)).background(tint(WT.danger, 0.12f)).border(1.dp, tint(WT.danger, 0.3f), RoundedCornerShape(16.dp)).clickable { onDelete() },
                    contentAlignment = Alignment.Center,
                ) { TrashIcon(size = 20.dp, color = WT.danger) }
            }
            PrimaryButton(if (isNew) "Add" else "Save changes", Modifier.weight(1f)) { onSave(amount) }
        }
    }
}

// ════════════════════════ GOAL SHEET BODY ════════════════════════
@Composable
fun GoalSheetBody(goal: Int, onSave: (Int) -> Unit, onClose: () -> Unit) {
    var g by remember { mutableIntStateOf(goal) }
    fun step(n: Int) { g = max(16, min(200, g + n)) }

    Column {
        Row(Modifier.fillMaxWidth().padding(bottom = 18.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Settings", fontSize = 20.sp, fontWeight = FontWeight.W800, color = WT.ink, fontFamily = WT.font)
            Box(Modifier.size(30.dp).clickable { onClose() }, contentAlignment = Alignment.Center) { CloseIcon() }
        }

        Text("DAILY GOAL", fontSize = 13.sp, fontWeight = FontWeight.W800, letterSpacing = 0.1.em, color = WT.faint, fontFamily = WT.font)

        Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            StepBtn("−") { step(-8) }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("$g", Modifier.alignByBaseline(), fontSize = 52.sp, fontWeight = FontWeight.W800, color = WT.ink, letterSpacing = (-0.03f).em, fontFamily = WT.font)
                Text("oz", Modifier.alignByBaseline(), fontSize = 20.sp, fontWeight = FontWeight.W700, color = WT.dim, fontFamily = WT.font)
            }
            StepBtn("+") { step(8) }
        }

        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(48, 64, 96, 128).forEach { v ->
                val on = g == v
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (on) tint(WT.accent, 0.16f) else WT.cardHi)
                        .then(if (on) Modifier.border(1.dp, tint(WT.accent, 0.4f), RoundedCornerShape(12.dp)) else Modifier)
                        .clickable { g = v }.padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("$v", fontSize = 14.sp, fontWeight = FontWeight.W800, color = if (on) WT.accent else WT.dim, fontFamily = WT.font) }
            }
        }

        Spacer(Modifier.height(22.dp))
        PrimaryButton("Save goal", Modifier.fillMaxWidth()) { onSave(g) }
    }
}

@Composable
fun PrimaryButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(16.dp)).background(WT.accent).clickable { onClick() }.padding(vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontSize = 16.sp, fontWeight = FontWeight.W800, color = WT.onAccent, fontFamily = WT.font) }
}
