package com.watertracker.widget.pebble

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.watertracker.widget.WaterTrackerWidget
import com.watertracker.widget.app.WaterRepository
import com.watertracker.widget.app.entriesForDay
import com.watertracker.widget.app.sumForDay
import io.rebble.pebblekit2.client.BasePebbleListenerService
import io.rebble.pebblekit2.client.DefaultPebbleSender
import io.rebble.pebblekit2.common.model.PebbleDictionary
import io.rebble.pebblekit2.common.model.PebbleDictionaryItem
import io.rebble.pebblekit2.common.model.ReceiveResult
import io.rebble.pebblekit2.common.model.WatchIdentifier
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

/**
 * Watch <-> phone contract. The single source of truth for these values is
 * /PROTOCOL.md; keep this object and pebble/package.json in step with it.
 *
 * Keys are the integers the Pebble SDK assigns to pebble/package.json -> messageKeys
 * (see pebble/build/js/message_keys.json), NOT 0-based indices.
 */
object PebbleProtocol {
    val APP_UUID: UUID = UUID.fromString("6b329be5-682a-4283-892b-d596790bdfb1")

    const val KEY_TODAY_OZ: UInt = 10000u     // phone -> watch
    const val KEY_GOAL_OZ: UInt = 10001u      // phone -> watch
    const val KEY_LOG_OZ: UInt = 10002u       // watch -> phone
    const val KEY_REQUEST_SYNC: UInt = 10003u // watch -> phone
    const val KEY_REMOVE_LAST: UInt = 10004u  // watch -> phone (delete most recent entry today)
}

/**
 * Push today's total + goal to the watch. Only lands while the watchapp is open
 * (PebbleKit 2 returns FailedDifferentAppOpen otherwise); the watch reconciles via
 * its RequestSync handshake / our onAppOpened push on next launch.
 */
suspend fun pushStateToPebble(context: Context) {
    val app = context.applicationContext
    val state = WaterRepository.snapshot(app)
    val today = sumForDay(state.entries, LocalDate.now())
    val data: PebbleDictionary = mapOf(
        PebbleProtocol.KEY_TODAY_OZ to PebbleDictionaryItem.Int32(today),
        PebbleProtocol.KEY_GOAL_OZ to PebbleDictionaryItem.Int32(state.goal),
    )
    // Best-effort: if no Pebble app is installed / connected, sending is a no-op and
    // close() can throw "Service not registered" (the bind never happened). Never let
    // a missing watch crash the caller's coroutine.
    val sender = DefaultPebbleSender(app)
    try {
        sender.sendDataToPebble(PebbleProtocol.APP_UUID, data)
    } catch (e: Exception) {
        // watch unreachable — ignore
    } finally {
        runCatching { sender.close() }
    }
}

/**
 * Receives AppMessages from the watch. The Core Devices app binds this service when
 * our watchapp is open (we are listed in the watchapp's package.json companionApp):
 *  - LogOz       -> write an entry through WaterRepository (app + widget update via the
 *                   shared DataStore flow), then reply with the authoritative totals.
 *  - RequestSync -> reply with the current totals.
 *  - onAppOpened -> push totals so the ring is right the moment the watchapp launches.
 */
class PebbleListenerService : BasePebbleListenerService() {

    override suspend fun onMessageReceived(
        watchappUUID: UUID,
        data: PebbleDictionary,
        watch: WatchIdentifier,
    ): ReceiveResult {
        val app = applicationContext

        // Received numbers always arrive as Int32 or UInt32 regardless of their size on the watch.
        val logOz = (data[PebbleProtocol.KEY_LOG_OZ] as? PebbleDictionaryItem.Int32)?.value
            ?: (data[PebbleProtocol.KEY_LOG_OZ] as? PebbleDictionaryItem.UInt32)?.value?.toInt()
        if (logOz != null) {
            WaterRepository.addEntry(app, logOz, System.currentTimeMillis())
            WaterTrackerWidget().updateAll(app)
        }

        // RemoveLast: delete the most recent entry logged today (DOWN on the watch).
        if (data.containsKey(PebbleProtocol.KEY_REMOVE_LAST)) {
            val state = WaterRepository.snapshot(app)
            entriesForDay(state.entries, LocalDate.now()).firstOrNull()?.let { last ->
                WaterRepository.deleteEntry(app, last.id)
                WaterTrackerWidget().updateAll(app)
            }
        }

        // RequestSync, a log, or a removal all get a fresh authoritative snapshot back.
        pushStateToPebble(app)
        return ReceiveResult.Ack
    }

    override fun onAppOpened(watchappUUID: UUID, watch: WatchIdentifier) {
        coroutineScope.launch { pushStateToPebble(applicationContext) }
    }
}
