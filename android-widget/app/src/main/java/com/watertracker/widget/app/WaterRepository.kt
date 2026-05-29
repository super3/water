package com.watertracker.widget.app

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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

    fun stateFlow(context: Context): Flow<WaterState> = context.waterStore.data.map { p ->
        WaterState(decodeEntries(p[ENTRIES] ?: ""), p[GOAL] ?: WT_GOAL_DEFAULT)
    }

    suspend fun snapshot(context: Context): WaterState {
        val p = context.waterStore.data.first()
        return WaterState(decodeEntries(p[ENTRIES] ?: ""), p[GOAL] ?: WT_GOAL_DEFAULT)
    }

    suspend fun addEntry(context: Context, amount: Int, ts: Long) = context.waterStore.edit { p ->
        val list = decodeEntries(p[ENTRIES] ?: "").toMutableList()
        list.add(Entry(nextId(list), ts, amount))
        p[ENTRIES] = encodeEntries(list)
    }

    suspend fun editEntry(context: Context, id: Int, amount: Int) = context.waterStore.edit { p ->
        val list = decodeEntries(p[ENTRIES] ?: "").map { if (it.id == id) it.copy(amount = amount) else it }
        p[ENTRIES] = encodeEntries(list)
    }

    suspend fun deleteEntry(context: Context, id: Int) = context.waterStore.edit { p ->
        val list = decodeEntries(p[ENTRIES] ?: "").filter { it.id != id }
        p[ENTRIES] = encodeEntries(list)
    }

    suspend fun setGoal(context: Context, goal: Int) = context.waterStore.edit { p ->
        p[GOAL] = goal
    }
}
