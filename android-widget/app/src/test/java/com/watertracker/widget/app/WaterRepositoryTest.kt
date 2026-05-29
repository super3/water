package com.watertracker.widget.app

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Tests the DataStore-backed [WaterRepository] CRUD against a real, file-backed DataStore on
 * a temp file — pure JVM (no emulator, no Robolectric), so it runs in CI and is counted by
 * JaCoCo coverage. Each test gets its own store, so they're fully isolated.
 */
class WaterRepositoryTest {

    @get:Rule val tmp = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var store: DataStore<Preferences>

    @Before
    fun setUp() {
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val dir = tmp.newFolder()
        store = PreferenceDataStoreFactory.create(scope = scope) { File(dir, "water.preferences_pb") }
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun startsEmptyWithDefaultGoal() = runBlocking {
        val s = WaterRepository.snapshot(store)
        assertTrue(s.entries.isEmpty())
        assertEquals(WT_GOAL_DEFAULT, s.goal)
    }

    @Test
    fun addEntry_appendsWithUniqueIds() = runBlocking {
        WaterRepository.addEntry(store, amount = 8, ts = 1_000L)
        WaterRepository.addEntry(store, amount = 16, ts = 2_000L)
        val s = WaterRepository.snapshot(store)
        assertEquals(listOf(8, 16), s.entries.map { it.amount })
        assertEquals(2, s.entries.map { it.id }.toSet().size)
    }

    @Test
    fun editEntry_updatesAmount() = runBlocking {
        WaterRepository.addEntry(store, amount = 8, ts = 1_000L)
        val id = WaterRepository.snapshot(store).entries.first().id
        WaterRepository.editEntry(store, id = id, amount = 20)
        assertEquals(20, WaterRepository.snapshot(store).entries.first { it.id == id }.amount)
    }

    @Test
    fun deleteEntry_removesById() = runBlocking {
        WaterRepository.addEntry(store, amount = 8, ts = 1_000L)
        WaterRepository.addEntry(store, amount = 16, ts = 2_000L)
        val target = WaterRepository.snapshot(store).entries.first()
        WaterRepository.deleteEntry(store, target.id)
        val s = WaterRepository.snapshot(store)
        assertEquals(1, s.entries.size)
        assertFalse(s.entries.any { it.id == target.id })
    }

    @Test
    fun setGoal_persists() = runBlocking {
        WaterRepository.setGoal(store, 96)
        assertEquals(96, WaterRepository.snapshot(store).goal)
    }

    @Test
    fun stateFlow_reflectsLatestWrite() = runBlocking {
        WaterRepository.addEntry(store, amount = 12, ts = 5_000L)
        WaterRepository.addEntry(store, amount = 20, ts = 6_000L)
        val s = WaterRepository.stateFlow(store).first()
        assertEquals(32, s.entries.sumOf { it.amount })
    }
}
