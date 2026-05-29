package com.watertracker.widget.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Icons recreated from the inline SVGs in `wt-ui.jsx`. Each is drawn on a 24×24
 * viewport scaled to the requested size, reusing the original path data where possible.
 */

private const val VIEWPORT = 24f

/** Draws an SVG path string into the current 24-unit-scaled DrawScope. */
private fun DrawScope.svgPath(
    data: String,
    color: Color,
    strokeWidth: Float? = null,
) {
    val path = PathParser().parsePathString(data).toPath()
    val s = size.minDimension / VIEWPORT
    scale(s, s, pivot = Offset.Zero) {
        if (strokeWidth == null) {
            drawPath(path, color)
        } else {
            drawPath(
                path, color,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

@Composable
private fun PathIcon(data: String, size: Dp, color: Color, strokeWidth: Float? = null) {
    val cached = remember(data) { data }
    Canvas(Modifier.size(size)) { svgPath(cached, color, strokeWidth) }
}

@Composable
fun DropIcon(size: Dp = 20.dp, color: Color = WT.accent) =
    PathIcon("M12 2.5c0 0 7 7.6 7 12.3a7 7 0 1 1-14 0C5 10.1 12 2.5 12 2.5z", size, color)

@Composable
fun FlameIcon(size: Dp = 22.dp, color: Color = WT.streak) =
    PathIcon(
        "M12 2.5s5.5 4.3 5.5 9.5a5.5 5.5 0 1 1-11 0c0-1.6.7-2.9 1.4-3.8.2 1.2 1 2 1.9 2 1.3 0 1.4-1.6 1-3.3-.5-2.1.2-3.8 1.2-4.4z",
        size, color,
    )

@Composable
fun ClockIcon(size: Dp = 15.dp, color: Color = WT.faint) =
    PathIcon("M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0 M12 7v5l3 2", size, color, strokeWidth = 2f)

@Composable
fun TrashIcon(size: Dp = 18.dp, color: Color = WT.danger) =
    PathIcon(
        "M4 7h16M9 7V5a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2m2 0v12a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2V7",
        size, color, strokeWidth = 2f,
    )

@Composable
fun ChevronIcon(size: Dp = 18.dp, color: Color = WT.faint) =
    PathIcon("M9 6l6 6-6 6", size, color, strokeWidth = 2f)

@Composable
fun CloseIcon(size: Dp = 22.dp, color: Color = WT.dim) =
    PathIcon("M6 6l12 12M18 6L6 18", size, color, strokeWidth = 2f)

@Composable
fun GearIcon(size: Dp = 22.dp, color: Color = WT.dim) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension / VIEWPORT
        scale(s, s, pivot = Offset.Zero) {
            for (i in 0 until 8) {
                rotate(degrees = i * 45f, pivot = Offset(12f, 12f)) {
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(10.7f, 1.4f),
                        size = Size(2.6f, 4.2f),
                        cornerRadius = CornerRadius(1f, 1f),
                    )
                }
            }
            drawCircle(color, radius = 6.4f, center = Offset(12f, 12f), style = Stroke(2.4f))
            drawCircle(color, radius = 2.6f, center = Offset(12f, 12f), style = Stroke(2f))
        }
    }
}

@Composable
fun ChartIcon(size: Dp = 22.dp, color: Color = WT.faint) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension / VIEWPORT
        scale(s, s, pivot = Offset.Zero) {
            val r = CornerRadius(1.2f, 1.2f)
            drawRoundRect(color, Offset(3f, 12f), Size(4f, 8f), r)
            drawRoundRect(color, Offset(10f, 7f), Size(4f, 13f), r)
            drawRoundRect(color, Offset(17f, 3f), Size(4f, 17f), r)
        }
    }
}
