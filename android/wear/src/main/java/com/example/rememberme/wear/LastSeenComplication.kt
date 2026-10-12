package com.example.rememberme.wear

import android.content.Context
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService

class LastSeenComplication : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        return when (type) {
            ComplicationType.SHORT_TEXT -> {
                ShortTextComplicationData.Builder(
                    plainText("Priya"),
                    plainText("Last Recognized")
                ).setTitle(plainText("Daughter")).build()
            }
            ComplicationType.LONG_TEXT -> {
                LongTextComplicationData.Builder(
                    plainText("Priya • Daughter"),
                    plainText("Last Recognized")
                ).build()
            }
            else -> null
        }
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        val prefs = getSharedPreferences("wear_prefs", Context.MODE_PRIVATE)
        val name = prefs.getString("last_seen_name", "None") ?: "None"
        val relationship = prefs.getString("last_seen_rel", "") ?: ""

        val displayText = if (relationship.isNotBlank()) "$name ($relationship)" else name

        return when (request.complicationType) {
            ComplicationType.SHORT_TEXT -> {
                ShortTextComplicationData.Builder(
                    plainText(name),
                    plainText("Last Seen Face")
                ).setTitle(plainText(if (relationship.isNotBlank()) relationship else "Seen")).build()
            }
            ComplicationType.LONG_TEXT -> {
                LongTextComplicationData.Builder(
                    plainText(displayText),
                    plainText("Last Seen Face")
                ).build()
            }
            else -> {
                ShortTextComplicationData.Builder(
                    plainText(name),
                    plainText("Last Seen")
                ).build()
            }
        }
    }

    private fun plainText(text: String): PlainComplicationText {
        return PlainComplicationText.Builder(text).build()
    }
}
