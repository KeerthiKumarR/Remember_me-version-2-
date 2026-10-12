package com.example.rememberme.wear

import android.content.ComponentName
import android.content.Context
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

class WearDataListenerService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == "/last_seen") {
                val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                val name = dataMap.getString("name", "Unknown")
                val relationship = dataMap.getString("relationship", "")
                val timestamp = dataMap.getLong("timestamp", System.currentTimeMillis())

                getSharedPreferences("wear_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putString("last_seen_name", name)
                    .putString("last_seen_rel", relationship)
                    .putLong("last_seen_time", timestamp)
                    .apply()

                // Request complication update on watch face
                try {
                    val requester = ComplicationDataSourceUpdateRequester.create(
                        this,
                        ComponentName(this, LastSeenComplication::class.java)
                    )
                    requester.requestUpdateAll()
                } catch (e: Exception) {
                    // Ignored if complication is not currently active on watch face
                }

                // Give patient a subtle confirmation pulse that familiar face was identified
                HapticReminderService.triggerGentleVibration(this)
            }
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            "/trigger_haptic" -> {
                HapticReminderService.triggerGentleVibration(this)
            }
        }
    }
}
