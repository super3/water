package com.watertracker.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.LocalSize
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.watertracker.widget.app.WaterRepository
import com.watertracker.widget.app.WidgetGeometry
import com.watertracker.widget.app.sumForDay
import java.time.LocalDate

class WaterTrackerWidget : GlanceAppWidget() {

    // Exact so LocalSize reports the widget's real on-screen size (not its declared minimum),
    // which the "+" position depends on.
    override val sizeMode: SizeMode = SizeMode.Exact

    private object Tokens {
        val accent = Color(0xFF7AA2FF)
        val good = Color(0xFF5BE3C4)
        val ink = Color(0xFFE8EEF5)
        val inkDim = Color(0xFF6A7280)
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Use the current persisted value as the initial render (no flash), then OBSERVE the
        // shared store so the widget re-renders whenever water changes — from its own "+" or
        // from the app — instead of relying solely on explicit update() calls.
        val initial = WaterRepository.snapshot(context)
        provideContent {
            val state by WaterRepository.stateFlow(context).collectAsState(initial = initial)
            GlanceTheme {
                WaterTrackerContent(sumForDay(state.entries, LocalDate.now()), state.goal)
            }
        }
    }

    @Composable
    private fun WaterTrackerContent(ounces: Int, goal: Int) {
        val progress = WidgetGeometry.progress(ounces, goal)

        // The ring is a square image centered in the widget; everything is positioned in that
        // same square so the "+" lands exactly on the ring (design tile = 192 units).
        val square = minOf(LocalSize.current.width, LocalSize.current.height)
        val unit = square / WidgetGeometry.TILE // one design unit, in dp
        val s = square.value / WidgetGeometry.TILE // scale factor for text/spacing
        // Transparent tap target over the baked-in "+", positioned from the shared geometry.
        val (tapX, tapY) = WidgetGeometry.plusTapTopLeft
        val tapSize = unit * WidgetGeometry.PLUS_TAP
        val tapStart = unit * tapX
        val tapTop = unit * tapY

        Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // Tile + ring + centered text. Tapping here (not the "+") opens the app.
            // Open-app and the "+" are SIBLINGS (not nested) so their clicks don't conflict.
            Box(
                GlanceModifier.size(square).clickable(actionStartActivity<MainActivity>()),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    provider = ImageProvider(createRingBitmap(progress)),
                    contentDescription = "Water progress",
                    modifier = GlanceModifier.size(square),
                    contentScale = ContentScale.Fit,
                )
                // Center text column — sizes/spacing scale with the ring (mockup: tile = 192).
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_droplet),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(ColorProvider(Tokens.accent)),
                        modifier = GlanceModifier.size((16f * s).dp).padding(bottom = (6f * s).dp),
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "$ounces",
                            style = TextStyle(color = ColorProvider(Tokens.ink), fontSize = (38f * s).sp, fontWeight = FontWeight.Bold),
                        )
                        Text(
                            text = "oz",
                            style = TextStyle(color = ColorProvider(Tokens.inkDim), fontSize = (18f * s).sp, fontWeight = FontWeight.Medium),
                            modifier = GlanceModifier.padding(start = (2f * s).dp, bottom = (4f * s).dp),
                        )
                    }
                    Text(
                        text = "/ ${goal}oz",
                        style = TextStyle(color = ColorProvider(Tokens.inkDim), fontSize = (16f * s).sp, fontWeight = FontWeight.Medium),
                        modifier = GlanceModifier.padding(top = (4f * s).dp),
                    )
                }
            }

            // Transparent tap target over the baked-in "+" — the ONLY clickable region.
            // Positioned with spacers (reliable in Glance) within the same square as the ring.
            Box(GlanceModifier.size(square)) {
                Column(GlanceModifier.fillMaxSize()) {
                    Spacer(GlanceModifier.height(tapTop))
                    Row(GlanceModifier.fillMaxSize()) {
                        Spacer(GlanceModifier.width(tapStart))
                        Box(
                            GlanceModifier.size(tapSize)
                                .clickable(actionRunCallback<AddWaterAction>()),
                        ) {}
                    }
                }
            }
        }
    }

    /** Variant D ring: 192px tile, 300° track (7→5 o'clock, 60° bottom gap) + flush "+". */
    private fun createRingBitmap(progress: Float): Bitmap {
        val scale = 3
        val size = 192 * scale
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val center = size / 2f
        val ringRadius = WidgetGeometry.RADIUS * scale
        val strokeWidth = WidgetGeometry.STROKE * scale

        canvas.drawCircle(center, center, center, Paint().apply {
            color = 0xFF0F1418.toInt(); style = Paint.Style.FILL; isAntiAlias = true
        })

        val rect = RectF(center - ringRadius, center - ringRadius, center + ringRadius, center + ringRadius)
        val startAngle = WidgetGeometry.START_ANGLE
        val sweepAngle = WidgetGeometry.SWEEP

        canvas.drawArc(rect, startAngle, sweepAngle, false, Paint().apply {
            color = 0xFF2A3038.toInt(); style = Paint.Style.STROKE; this.strokeWidth = strokeWidth
            strokeCap = Paint.Cap.ROUND; isAntiAlias = true
        })

        if (progress > 0f) {
            canvas.drawArc(rect, startAngle, sweepAngle * progress, false, Paint().apply {
                color = 0xFF7AA2FF.toInt()
                style = Paint.Style.STROKE; this.strokeWidth = strokeWidth
                strokeCap = Paint.Cap.ROUND; isAntiAlias = true
            })
        }

        // Flush "+" button at the 5 o'clock terminus (from the shared, tested geometry).
        val (px, py) = WidgetGeometry.plusCenter
        val bx = px * scale
        val by = py * scale
        val btnR = (WidgetGeometry.PLUS_SIZE / 2f) * scale
        canvas.drawCircle(bx, by, btnR, Paint().apply {
            color = 0xFF7AA2FF.toInt(); style = Paint.Style.FILL; isAntiAlias = true
        })
        val glyph = Paint().apply {
            color = 0xFF0F1418.toInt(); isAntiAlias = true; textSize = 18f * scale
            textAlign = Paint.Align.CENTER; isFakeBoldText = true
        }
        canvas.drawText("+", bx, by - (glyph.descent() + glyph.ascent()) / 2f, glyph)

        return bitmap
    }
}
