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
    const val KEY_WATCH_SEQ: UInt = 10005u    // watch -> phone (op id, for de-dup)
    const val KEY_LOG_TS: UInt = 10006u       // watch -> phone (op timestamp, unix seconds)
}

/** Reads a dictionary number that arrives as either Int32 or UInt32. */
private fun PebbleDictionary.intValue(key: UInt): Long? =
    (this[key] as? PebbleDictionaryItem.Int32)?.value?.toLong()
        ?: (this[key] as? PebbleDictionaryItem.UInt32)?.value?.toLong()

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

        // De-dup resent ops by a monotonic high-water mark. A lost ACK makes the watch
        // resend the same seq; skip applying it (but still ACK + echo so the watch advances).
        val seq = data.intValue(PebbleProtocol.KEY_WATCH_SEQ)
        val alreadyApplied = seq != null && seq <= WaterRepository.lastWatchSeq(app)

        if (!alreadyApplied) {
            val logOz = data.intValue(PebbleProtocol.KEY_LOG_OZ)?.toInt()
            if (logOz != null) {
                // Record at the watch's timestamp so offline logs land on the right day.
                val ts = data.intValue(PebbleProtocol.KEY_LOG_TS)?.times(1000L) ?: System.currentTimeMillis()
                WaterRepository.addEntry(app, logOz, ts)
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

            if (seq != null) WaterRepository.setLastWatchSeq(app, seq)
        }

        // RequestSync, a log, or a removal all get a fresh authoritative snapshot back.
        pushStateToPebble(app)
        return ReceiveResult.Ack
    }

    override fun onAppOpened(watchappUUID: UUID, watch: WatchIdentifier) {
        coroutineScope.launch { pushStateToPebble(applicationContext) }
    }
}
