package com.example.rememberme.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

class ReminderReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ReminderReceiver"
        private const val UTTERANCE_ID = "rememberme_reminder"
        private const val SPOKEN_REMINDER =
            "Hello! This is your RememberMe app. " +
            "If you see someone and want to know who they are, just open the app and point the camera at them."
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Reminder alarm fired")
        ReminderScheduler.scheduleNext(context)
        NotificationHelper.showReminderNotification(context)
        speakReminder(context)
    }

    private fun speakReminder(context: Context) {
        val pendingResult = goAsync()
        var tts: TextToSpeech? = null

        tts = TextToSpeech(context.applicationContext) { status ->
            if (status != TextToSpeech.SUCCESS) {
                Log.w(TAG, "TTS init failed with status: $status")
                pendingResult.finish()
                return@TextToSpeech
            }

            val langResult = tts?.setLanguage(Locale.getDefault())
            if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale.US)
            }
            tts?.setSpeechRate(0.85f)
            tts?.setPitch(1.0f)

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}

                override fun onDone(utteranceId: String?) {
                    Log.d(TAG, "TTS speech completed")
                    tts?.shutdown()
                    pendingResult.finish()
                }

                override fun onError(utteranceId: String?) {
                    Log.w(TAG, "TTS speech error")
                    tts?.shutdown()
                    pendingResult.finish()
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?, errorCode: Int) {
                    tts?.shutdown()
                    pendingResult.finish()
                }
            })

            tts?.speak(SPOKEN_REMINDER, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
            Log.d(TAG, "TTS speak() called")
        }
    }
}
