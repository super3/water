package com.watertracker.widget.app

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

/** A single logged amount of water. Mirrors `Entry = { id, ts, amount }`. */
data class Entry(val id: Int, val ts: Long, val amount: Int)

const val WT_GOAL_DEFAULT = 64 // oz

private val zone: ZoneId get() = ZoneId.systemDefault()
private val timeFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
private val dayLabelFmt = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.US)

fun dayOf(ts: Long): LocalDate = Instant.ofEpochMilli(ts).atZone(zone).toLocalDate()

fun entriesForDay(entries: List<Entry>, day: LocalDate): List<Entry> =
    entries.filter { dayOf(it.ts) == day }.sortedByDescending { it.ts }

fun sumForDay(entries: List<Entry>, day: LocalDate): Int =
    entries.filter { dayOf(it.ts) == day }.sumOf { it.amount }

/** The last [n] days, oldest first, ending today. */
fun lastNDays(n: Int): List<LocalDate> {
    val today = LocalDate.now()
    return (n - 1 downTo 0).map { today.minusDays(it.toLong()) }
}

/** Consecutive days (counting back from today) whose total >= goal; today counts only once met. */
fun currentStreak(entries: List<Entry>, goal: Int): Int {
    val today = LocalDate.now()
    var s = 0
    if (sumForDay(entries, today) >= goal) s++
    var i = 1
    while (i < 400) {
        if (sumForDay(entries, today.minusDays(i.toLong())) >= goal) s++ else break
        i++
    }
    return s
}

fun longestStreak(entries: List<Entry>, goal: Int, lookback: Int = 63): Int {
    val today = LocalDate.now()
    var best = 0
    var run = 0
    for (i in lookback - 1 downTo 0) {
        val d = today.minusDays(i.toLong())
        if (sumForDay(entries, d) >= goal) run++ else run = 0
        best = max(best, run)
    }
    return best
}

fun formatTime(ts: Long): String =
    Instant.ofEpochMilli(ts).atZone(zone).toLocalTime().format(timeFmt)

fun formatDayLabel(day: LocalDate): String = day.format(dayLabelFmt)

fun nextId(entries: List<Entry>): Int = (entries.maxOfOrNull { it.id } ?: 0) + 1

/** Serializes entries as `id,ts,amount` triples joined by `;` (for persistence). */
fun encodeEntries(list: List<Entry>): String =
    list.joinToString(";") { "${it.id},${it.ts},${it.amount}" }

/** Parses [encodeEntries] output, skipping any malformed rows. */
fun decodeEntries(s: String): List<Entry> {
    if (s.isBlank()) return emptyList()
    return s.split(";").mapNotNull { row ->
        val parts = row.split(",")
        if (parts.size != 3) return@mapNotNull null
        val id = parts[0].toIntOrNull() ?: return@mapNotNull null
        val ts = parts[1].toLongOrNull() ?: return@mapNotNull null
        val amount = parts[2].toIntOrNull() ?: return@mapNotNull null
        Entry(id, ts, amount)
    }
}

/** A timestamp that lands on [day]: "now" if it's today, otherwise noon of that day. */
fun timestampFor(day: LocalDate): Long =
    if (day == LocalDate.now()) System.currentTimeMillis()
    else day.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
