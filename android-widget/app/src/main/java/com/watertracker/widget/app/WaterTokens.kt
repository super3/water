package com.watertracker.widget.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

/**
 * Design tokens for the Water Tracker app, mirroring the `WT` object in the
 * `wt-ui.jsx` handoff prototype.
 */
object WT {
    val bg = Color(0xFF10141B)        // app canvas
    val card = Color(0xFF181D27)      // cards, sheet, settings button
    val cardHi = Color(0xFF1F2530)    // stepper buttons, toggle selected
    val ink = Color(0xFFE8EEF5)       // primary text
    val dim = Color(0xFF8A93A3)       // secondary text
    val faint = Color(0xFF5B6473)     // tertiary text / section labels
    val track = Color(0xFF272D39)     // ring track, borders, heatmap empty
    val accent = Color(0xFF7AA2FF)    // progress, primary actions, bars
    val accentDeep = Color(0xFF5B84E6)
    val good = Color(0xFF5BE3C4)      // goal met
    val streak = Color(0xFFFF9436)    // flame + streak chip
    val danger = Color(0xFFFF5A6E)    // delete
    val onAccent = Color(0xFF0C1017)  // text on solid accent

    // The handoff specifies Nunito (rounded geometric sans). Bundle the Nunito
    // font files in res/font and swap this to FontFamily(...) for full fidelity;
    // the system default is used here so the build has no font dependency.
    val font: FontFamily = FontFamily.Default
}

/** Returns [hex] (an ARGB/RGB Color) at alpha [a], matching the prototype's `tint()`. */
fun tint(color: Color, a: Float): Color = color.copy(alpha = a)
