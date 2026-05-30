package com.watertracker.widget.app

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** The single DataStore shared by the app UI and the home-screen widget. */
private val Context.waterStore: DataStore<Preferences> by preferencesDataStore(name = "water_tracker")

/** Snapshot of everything the UI needs. */
data class WaterState(val entries: List<Entry>, val goal: Int)

/**
 * Single source of truth for water entries + goal, persisted to DataStore so the app and the
 * widget always show the same numbers. Entries are encoded as `id,ts,amount` triples joined by `;`.
 */
object WaterRepository {
    const val INCREMENT = 8 // oz added by the widget "+" button

    private val ENTRIES = stringPreferencesKey("entries")
    private val GOAL = intPreferencesKey("goal")
    // High-water mark of the last watch op seq applied, for de-duping resent watch logs.
    private val LAST_WATCH_SEQ = longPreferencesKey("last_watch_seq")

    // ── Public API (Context-based) — delegates to the DataStore-based impl below. ──
    fun stateFlow(context: Context): Flow<WaterState> = stateFlow(context.waterStore)
    suspend fun snapshot(context: Context): WaterState = snapshot(context.waterStore)
    suspend fun addEntry(context: Context, amount: Int, ts: Long) = addEntry(context.waterStore, amount, ts)
    suspend fun editEntry(context: Context, id: Int, amount: Int) = editEntry(context.waterStore, id, amount)
    suspend fun deleteEntry(context: Context, id: Int) = deleteEntry(context.waterStore, id)
    suspend fun setGoal(context: Context, goal: Int) = setGoal(context.waterStore, goal)
    suspend fun lastWatchSeq(context: Context): Long = lastWatchSeq(context.waterStore)
    suspend fun setLastWatchSeq(context: Context, seq: Long) = setLastWatchSeq(context.waterStore, seq)

    // ── DataStore-based impl — `internal` so it can be unit-tested on the JVM with a
    //    file-backed DataStore (no Android Context / emulator needed). ──
    internal fun stateFlow(store: DataStore<Preferences>): Flow<WaterState> = store.data.map { p ->
        WaterState(decodeEntries(p[ENTRIES] ?: ""), p[GOAL] ?: WT_GOAL_DEFAULT)
    }

    internal suspend fun snapshot(store: DataStore<Preferences>): WaterState {
        val p = store.data.first()
        return WaterState(decodeEntries(p[ENTRIES] ?: ""), p[GOAL] ?: WT_GOAL_DEFAULT)
    }

    internal suspend fun addEntry(store: DataStore<Preferences>, amount: Int, ts: Long) {
        store.edit { p ->
            val list = decodeEntries(p[ENTRIES] ?: "").toMutableList()
            list.add(Entry(nextId(list), ts, amount))
            p[ENTRIES] = encodeEntries(list)
        }
    }

    internal suspend fun editEntry(store: DataStore<Preferences>, id: Int, amount: Int) {
        store.edit { p ->
            val list = decodeEntries(p[ENTRIES] ?: "").map { if (it.id == id) it.copy(amount = amount) else it }
            p[ENTRIES] = encodeEntries(list)
        }
    }

    internal suspend fun deleteEntry(store: DataStore<Preferences>, id: Int) {
        store.edit { p ->
            val list = decodeEntries(p[ENTRIES] ?: "").filter { it.id != id }
            p[ENTRIES] = encodeEntries(list)
        }
    }

    internal suspend fun setGoal(store: DataStore<Preferences>, goal: Int) {
        store.edit { p -> p[GOAL] = goal }
    }

    internal suspend fun lastWatchSeq(store: DataStore<Preferences>): Long =
        store.data.first()[LAST_WATCH_SEQ] ?: 0L

    internal suspend fun setLastWatchSeq(store: DataStore<Preferences>, seq: Long) {
        store.edit { p -> p[LAST_WATCH_SEQ] = seq }
    }
}
