package com.watertracker.widget.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Local JVM unit tests for the pure logic in [WaterModel] — date math, daily aggregation,
 * streaks, formatting, and entry serialization. No Android dependencies, so these run via
 * `:app:testDebugUnitTest` without a device/emulator.
 */
class WaterModelTest {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val today: LocalDate = LocalDate.now()

    /** An epoch-millis timestamp that lands on [d] at [h]:[m] in the system zone. */
    private fun tsOn(d: LocalDate, h: Int = 9, m: Int = 0): Long =
        d.atTime(h, m).atZone(zone).toInstant().toEpochMilli()

    /** A single entry that meets a 64oz goal on day [d]. */
    private fun metDay(id: Int, d: LocalDate): Entry = Entry(id, tsOn(d, 10), 64)

    // ── nextId ──────────────────────────────────────────────────────────────
    @Test fun nextId_emptyIsOne() {
        assertEquals(1, nextId(emptyList()))
    }

    @Test fun nextId_isMaxPlusOne() {
        assertEquals(8, nextId(listOf(Entry(3, 0, 0), Entry(7, 0, 0), Entry(2, 0, 0))))
    }

    // ── encode / decode ─────────────────────────────────────────────────────
    @Test fun encodeDecode_roundTrips() {
        val entries = listOf(Entry(1, 1000L, 8), Entry(2, 2000L, 16), Entry(3, 3000L, 32))
        assertEquals(entries, decodeEntries(encodeEntries(entries)))
    }

    @Test fun encodeDecode_emptyRoundTrips() {
        assertEquals("", encodeEntries(emptyList()))
        assertTrue(decodeEntries("").isEmpty())
        assertTrue(decodeEntries("   ").isEmpty())
    }

    @Test fun decode_skipsMalformedRows() {
        // "bad" (1 field), "x,y,z" (non-numeric), "1,2" (2 fields) are dropped; only 5,9,3 survives.
        assertEquals(listOf(Entry(5, 9L, 3)), decodeEntries("bad;5,9,3;x,y,z;1,2"))
    }

    // ── dayOf / sumForDay / entriesForDay ────────────────────────────────────
    @Test fun dayOf_mapsTimestampToLocalDate() {
        assertEquals(today, dayOf(tsOn(today, 15, 0)))
    }

    @Test fun sumForDay_sumsOnlyThatDay() {
        val entries = listOf(
            Entry(1, tsOn(today, 8), 8),
            Entry(2, tsOn(today, 12), 16),
            Entry(3, tsOn(today.minusDays(1), 9), 32),
        )
        assertEquals(24, sumForDay(entries, today))
        assertEquals(32, sumForDay(entries, today.minusDays(1)))
        assertEquals(0, sumForDay(entries, today.minusDays(2)))
    }

    @Test fun entriesForDay_filtersAndSortsNewestFirst() {
        val morning = Entry(1, tsOn(today, 8), 8)
        val evening = Entry(2, tsOn(today, 18), 16)
        val noon = Entry(3, tsOn(today, 12), 10)
        val otherDay = Entry(4, tsOn(today.minusDays(1), 10), 5)
        val result = entriesForDay(listOf(morning, evening, noon, otherDay), today)
        assertEquals(listOf(2, 3, 1), result.map { it.id }) // 18:00, 12:00, 08:00
    }

    // ── lastNDays ─────────────────────────────────────────────────────────────
    @Test fun lastNDays_isOldestFirstEndingToday() {
        val days = lastNDays(7)
        assertEquals(7, days.size)
        assertEquals(today, days.last())
        assertEquals(today.minusDays(6), days.first())
    }

    // ── currentStreak ─────────────────────────────────────────────────────────
    @Test fun currentStreak_countsConsecutiveMetDaysFromToday() {
        val entries = listOf(
            metDay(1, today),
            metDay(2, today.minusDays(1)),
            metDay(3, today.minusDays(2)),
            // today-3 not met (gap) → streak stops at 3
        )
        assertEquals(3, currentStreak(entries, 64))
    }

    @Test fun currentStreak_todayUnmetStillCountsPriorRun() {
        // Today has nothing; yesterday met; day-2 unmet. Per spec: 0 (today) + 1 (yesterday) then break.
        val entries = listOf(metDay(1, today.minusDays(1)))
        assertEquals(1, currentStreak(entries, 64))
    }

    @Test fun currentStreak_isZeroWhenNothingMet() {
        val entries = listOf(Entry(1, tsOn(today, 10), 10)) // 10 < 64
        assertEquals(0, currentStreak(entries, 64))
    }

    // ── longestStreak ───────────────────────────────────────────────────────
    @Test fun longestStreak_findsLongestRunInWindow() {
        // Met on today-5, today-4, today-3 (3 in a row); today-1 met alone. Longest = 3.
        val entries = listOf(
            metDay(1, today.minusDays(5)),
            metDay(2, today.minusDays(4)),
            metDay(3, today.minusDays(3)),
            metDay(4, today.minusDays(1)),
        )
        assertEquals(3, longestStreak(entries, 64, lookback = 7))
    }

    // ── formatTime ────────────────────────────────────────────────────────────
    @Test fun formatTime_uses12HourClock() {
        assertEquals("7:30 AM", formatTime(tsOn(today, 7, 30)))
        assertEquals("1:05 PM", formatTime(tsOn(today, 13, 5)))
        assertEquals("12:00 PM", formatTime(tsOn(today, 12, 0)))
        assertEquals("12:00 AM", formatTime(tsOn(today, 0, 0)))
    }

    // ── formatDayLabel ──────────────────────────────────────────────────────
    @Test fun formatDayLabel_isWeekdayMonthDay() {
        assertEquals("Friday, May 29", formatDayLabel(LocalDate.of(2026, 5, 29)))
    }

    // ── timestampFor ──────────────────────────────────────────────────────────
    @Test fun timestampFor_todayLandsOnToday() {
        assertEquals(today, dayOf(timestampFor(today)))
    }

    @Test fun timestampFor_pastDayIsNoonOfThatDay() {
        val yesterday = today.minusDays(1)
        val ts = timestampFor(yesterday)
        assertEquals(yesterday, dayOf(ts))
        assertEquals(12, Instant.ofEpochMilli(ts).atZone(zone).hour)
    }
}
