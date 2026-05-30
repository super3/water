package com.watertracker.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.watertracker.widget.app.WaterRepository
import com.watertracker.widget.pebble.pushStateToPebble

/** Triggered by the widget "+" button: logs one increment to the shared store. */
class AddWaterAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        WaterRepository.addEntry(context, WaterRepository.INCREMENT, System.currentTimeMillis())
        WaterTrackerWidget().update(context, glanceId)
        pushStateToPebble(context)
    }
}
