package com.watertracker.widget.app

import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit tests for the pure widget ring geometry. */
class WidgetGeometryTest {

    @Test fun progress_clampsToZeroAndOne() {
        assertEquals(0f, WidgetGeometry.progress(0, 64), 1e-4f)
        assertEquals(0.5f, WidgetGeometry.progress(32, 64), 1e-4f)
        assertEquals(1f, WidgetGeometry.progress(80, 64), 1e-4f) // over goal clamps to 1
    }

    @Test fun progressSweep_scalesToFullSweep() {
        assertEquals(0f, WidgetGeometry.progressSweep(0, 64), 1e-4f)
        assertEquals(WidgetGeometry.SWEEP / 2f, WidgetGeometry.progressSweep(32, 64), 1e-3f)
        assertEquals(WidgetGeometry.SWEEP, WidgetGeometry.progressSweep(64, 64), 1e-4f)
    }

    @Test fun pointOnRing_startIsSevenOClock() {
        // 120° canvas = 7 o'clock (lower-left): x < center, y > center.
        val (x, y) = WidgetGeometry.pointOnRing(WidgetGeometry.START_ANGLE)
        assertEquals(WidgetGeometry.CENTER - WidgetGeometry.RADIUS / 2f, x, 1e-2f) // cos120 = -0.5
        assertEquals(WidgetGeometry.CENTER + WidgetGeometry.RADIUS * 0.8660254f, y, 1e-2f)
    }

    @Test fun plusCenter_isFiveOClockTerminus() {
        // Track end = 120° + 300° = 420° ≡ 60°: cos = 0.5, sin ≈ 0.866 → (135, 163.55).
        val (x, y) = WidgetGeometry.plusCenter
        assertEquals(135f, x, 1e-2f)
        assertEquals(163.55f, y, 1e-1f)
    }

    @Test fun plusTapTopLeft_centersTapTargetOnPlus() {
        val (cx, cy) = WidgetGeometry.plusCenter
        val (tx, ty) = WidgetGeometry.plusTapTopLeft
        assertEquals(cx - WidgetGeometry.PLUS_TAP / 2f, tx, 1e-4f)
        assertEquals(cy - WidgetGeometry.PLUS_TAP / 2f, ty, 1e-4f)
    }
}
