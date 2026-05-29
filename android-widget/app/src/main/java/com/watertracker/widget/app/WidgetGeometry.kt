package com.watertracker.widget.app

import kotlin.math.cos
import kotlin.math.sin

/**
 * Pure geometry for the Variant D ring widget, expressed in the design's 192-unit tile space.
 * Has no Android dependencies so it is unit-testable on the JVM (and reused by the widget's
 * Canvas drawing + Glance layout to keep the "+" exactly on the ring).
 */
object WidgetGeometry {
    const val TILE = 192f
    const val CENTER = TILE / 2f             // 96
    const val STROKE = 12f
    const val RADIUS = (168f - STROKE) / 2f  // 78 (ring stage = 168)
    const val START_ANGLE = 120f             // canvas degrees (design 210° − 90)
    const val SWEEP = 300f                   // 7 o'clock → 5 o'clock (60° bottom gap)
    const val PLUS_SIZE = 30f                // "+" button diameter
    const val PLUS_TAP = 44f                 // (slightly larger) tap target

    /** Fill fraction, clamped to [0, 1]. */
    fun progress(value: Int, goal: Int): Float = (value.toFloat() / goal).coerceIn(0f, 1f)

    /** Degrees of the progress arc to draw for [value]/[goal]. */
    fun progressSweep(value: Int, goal: Int): Float = progress(value, goal) * SWEEP

    /** Point on the ring at a canvas angle (degrees), in tile (192) space. */
    fun pointOnRing(angleDeg: Float): Pair<Float, Float> {
        val rad = Math.toRadians(angleDeg.toDouble())
        return (CENTER + RADIUS * cos(rad)).toFloat() to (CENTER + RADIUS * sin(rad)).toFloat()
    }

    /** Center of the "+" button — the 5 o'clock track terminus — in tile (192) space. */
    val plusCenter: Pair<Float, Float> get() = pointOnRing(START_ANGLE + SWEEP)

    /** Top-left of the "+" tap target (centered on [plusCenter]), in tile (192) space. */
    val plusTapTopLeft: Pair<Float, Float>
        get() {
            val (cx, cy) = plusCenter
            return (cx - PLUS_TAP / 2f) to (cy - PLUS_TAP / 2f)
        }
}
