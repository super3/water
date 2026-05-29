package com.watertracker.widget.app

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Shared easing matching the prototype's cubic-bezier(.2,.8,.2,1). */
val WtEasing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)

// ── Progress ring (Variant D lineage) ───────────────────────────────────────
@Composable
fun Ring(
    value: Int,
    goal: Int,
    modifier: Modifier = Modifier,
    accent: androidx.compose.ui.graphics.Color = WT.accent,
    size: Dp = 220.dp,
) {
    val reached = value >= goal
    val target = min(value / goal.toFloat(), 1f)
    val progress by animateFloatAsState(target, tween(600, easing = WtEasing), label = "ringProgress")
    val arcColor = if (reached) WT.good else accent
    val pct = (target * 100).roundToInt()

    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val dim = this.size.minDimension
            val strokePx = dim * 0.07f
            val r = (dim - strokePx) / 2f - 2.dp.toPx()
            val diameter = r * 2f
            val topLeft = Offset((dim - diameter) / 2f, (dim - diameter) / 2f)
            val arcSize = Size(diameter, diameter)
            val stroke = Stroke(width = strokePx, cap = StrokeCap.Round)
            // Canvas 0° = 3 o'clock; design 210° (7 o'clock) → 120°. Sweep 300° clockwise.
            drawArc(WT.track, 120f, 300f, false, topLeft, arcSize, style = stroke)
            if (progress > 0f) {
                drawArc(arcColor, 120f, 300f * progress, false, topLeft, arcSize, style = stroke)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            DropIcon(size = (size.value * 0.1f).dp, color = arcColor)
            Spacer(Modifier.height(4.dp))
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontSize = (size.value * 0.2f).sp, fontWeight = FontWeight.W800, color = WT.ink, letterSpacing = (-0.02f).em)) {
                        append("$value")
                    }
                    withStyle(SpanStyle(fontSize = (size.value * 0.085f).sp, fontWeight = FontWeight.W700, color = WT.dim)) {
                        append(" oz")
                    }
                },
                fontFamily = WT.font,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (reached) "Goal reached 🎉" else "$pct% of ${goal}oz",
                fontSize = (size.value * 0.07f).sp,
                fontWeight = FontWeight.W700,
                color = WT.dim,
                fontFamily = WT.font,
            )
        }
    }
}

// ── Bar chart ────────────────────────────────────────────────────────────────
data class BarDatum(val key: LocalDate, val value: Int, val label: String)

@Composable
fun BarChart(
    data: List<BarDatum>,
    goal: Int,
    selectedKey: LocalDate?,
    onSelect: ((LocalDate) -> Unit)? = null,
    height: Dp = 150.dp,
) {
    val maxVal = max(goal * 1.1f, max(data.maxOfOrNull { it.value } ?: 0, 1).toFloat())
    val gap = if (data.size > 12) 3.dp else 8.dp
    val goalFrac = (goal / maxVal).coerceIn(0f, 1f)
    val goalY = height * (1f - goalFrac)

    Column {
        Box(Modifier.fillMaxWidth().height(height)) {
            // Bars
            Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalAlignment = Alignment.Bottom,
            ) {
                data.forEach { d ->
                    val met = d.value >= goal
                    val col = if (met) WT.good else WT.accent
                    val sel = d.key == selectedKey
                    val frac = (d.value / maxVal).coerceIn(0f, 1f)
                    val barH by animateDpAsState((height * frac).coerceAtLeast(4.dp), tween(500, easing = WtEasing), label = "bar")
                    Box(
                        Modifier.weight(1f).fillMaxHeight().clickable(enabled = onSelect != null) { onSelect?.invoke(d.key) },
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Box(
                            Modifier
                                .widthIn(max = 30.dp)
                                .fillMaxWidth()
                                .height(barH)
                                .clip(RoundedCornerShape(7.dp))
                                .background(if (sel) col else tint(col, 0.32f))
                                .then(if (sel) Modifier.border(2.dp, tint(col, 0.7f), RoundedCornerShape(7.dp)) else Modifier),
                        )
                    }
                }
            }
            // Dashed goal line
            Canvas(Modifier.fillMaxSize()) {
                val y = this.size.height * (1f - goalFrac)
                drawLine(
                    color = tint(WT.accent, 0.5f),
                    start = Offset(0f, y),
                    end = Offset(this.size.width, y),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f),
                )
            }
            Text(
                "goal $goal",
                Modifier.align(Alignment.TopEnd).offset(y = goalY - 16.dp),
                fontSize = 10.sp, fontWeight = FontWeight.W700, color = tint(WT.accent, 0.8f), fontFamily = WT.font,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.fillMaxWidth()) {
            data.forEach { d ->
                Text(
                    d.label,
                    Modifier.weight(1f),
                    fontSize = if (data.size > 12) 8.sp else 11.sp,
                    fontWeight = FontWeight.W700,
                    color = if (d.key == selectedKey) WT.ink else WT.faint,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    fontFamily = WT.font,
                )
            }
        }
    }
}

// ── Goal-met heatmap ──────────────────────────────────────────────────────────
data class DayCell(val key: LocalDate, val frac: Float)

@Composable
fun Heatmap(weeks: List<List<DayCell>>) {
    val cell = 15.dp
    val gap = 4.dp
    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
        weeks.forEach { col ->
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                col.forEach { day ->
                    val bg = if (day.frac > 0f) {
                        val lvl = if (day.frac >= 1f) WT.good else WT.accent
                        tint(lvl, min(0.25f + day.frac * 0.75f, 1f))
                    } else WT.track
                    Box(Modifier.size(cell).clip(RoundedCornerShape(4.dp)).background(bg))
                }
            }
        }
    }
}

// ── Entry row ───────────────────────────────────────────────────────────────
@Composable
fun EntryRow(entry: Entry, onTap: (Entry) -> Unit, accent: androidx.compose.ui.graphics.Color = WT.accent) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(WT.card)
            .clickable { onTap(entry) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(tint(accent, 0.14f)),
            contentAlignment = Alignment.Center,
        ) { DropIcon(size = 20.dp, color = accent) }
        Column(Modifier.weight(1f)) {
            Text("${entry.amount} oz", fontSize = 17.sp, fontWeight = FontWeight.W800, color = WT.ink, fontFamily = WT.font)
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                ClockIcon(size = 13.dp, color = WT.faint)
                Text(formatTime(entry.ts), fontSize = 13.sp, fontWeight = FontWeight.W600, color = WT.dim, fontFamily = WT.font)
            }
        }
        ChevronIcon()
    }
}

// ── Quick-add chip ──
@Composable
fun QuickChip(label: String, onClick: () -> Unit, solid: Boolean = false, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (solid) WT.accent else tint(WT.accent, 0.12f))
            .then(if (solid) Modifier else Modifier.border(1.dp, tint(WT.accent, 0.25f), RoundedCornerShape(16.dp)))
            .clickable { onClick() }
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.W800, color = if (solid) WT.onAccent else WT.accent, fontFamily = WT.font)
    }
}

// ── Round stepper button ──
@Composable
fun StepBtn(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(WT.cardHi)
            .border(1.dp, WT.track, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 26.sp, fontWeight = FontWeight.W700, color = WT.ink, fontFamily = WT.font)
    }
}

// ── Section heading ──
@Composable
fun SectionHead(title: String, right: @Composable (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title.uppercase(),
            fontSize = 13.sp, fontWeight = FontWeight.W800, letterSpacing = 0.12.em, color = WT.faint, fontFamily = WT.font,
        )
        right?.invoke()
    }
}

// ── Summary stat card ──
@Composable
fun StatCard(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(WT.card).padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(label.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.W800, letterSpacing = 0.08.em, color = WT.faint, fontFamily = WT.font)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, Modifier.alignByBaseline(), fontSize = 26.sp, fontWeight = FontWeight.W800, color = WT.ink, letterSpacing = (-0.02f).em, fontFamily = WT.font)
            Text(unit, Modifier.alignByBaseline(), fontSize = 13.sp, fontWeight = FontWeight.W700, color = WT.dim, fontFamily = WT.font)
        }
    }
}
