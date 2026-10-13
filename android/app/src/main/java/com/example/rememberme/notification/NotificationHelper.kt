package com.example.rememberme.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.rememberme.MainActivity

object NotificationHelper {

    const val CHANNEL_ID = "rememberme_reminder_channel"
    const val NOTIFICATION_ID_REMINDER = 1001
    const val NOTIFICATION_ID_RECOGNITION = 1002
    const val NOTIFICATION_ID_TEST = 1003
    const val NOTIFICATION_ID_SOS = 1004

    private val VIBRATION_PATTERN = longArrayOf(0, 400, 200, 400)
    private val TEST_VIBRATION_PATTERN = longArrayOf(0, 600, 200, 600, 200, 600)

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "RememberMe Alerts & Reminders",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Haptic alerts for memory prompts, recognized family members, and emergency SOS"
            enableVibration(true)
            vibrationPattern = VIBRATION_PATTERN
            enableLights(true)
            setShowBadge(true)
        }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    /**
     * Periodic reminder sent to phone and mirrored to paired smartwatch over Bluetooth.
     */
    fun showReminderNotification(context: Context) {
        createChannel(context)
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("RememberMe 💙")
            .setContentText("Point your camera to see who is nearby.")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Point your camera at anyone near you — I'll tell you their name and a warm memory about them."
                )
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVibrate(VIBRATION_PATTERN)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID_REMINDER, notification)
    }

    /**
     * Heads-up notification fired immediately when a face is recognized.
     * Mirrored to smartwatch so the wrist buzzes with the person's name & relationship.
     */
    fun showRecognitionNotification(context: Context, personName: String, relationship: String) {
        createChannel(context)
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 1, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("👋 Recognized: $personName")
            .setContentText("$relationship is here with you")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "$personName ($relationship) is near you. Tap to review your shared memories."
                )
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setVibrate(VIBRATION_PATTERN)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID_RECOGNITION, notification)
    }

    /**
     * Immediate pulse test so the user can verify their smartwatch vibrates right now.
     */
    fun triggerWatchVibrationTest(context: Context) {
        createChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("⌚ Watch Vibration Test")
            .setContentText("Haptic pulse active! Feel the vibration on your wrist.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVibrate(TEST_VIBRATION_PATTERN)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID_TEST, notification)
    }

    /**
     * Notification confirming an Emergency SOS event was broadcast.
     */
    fun showSosTriggeredNotification(context: Context, statusMessage: String) {
        createChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("🚨 EMERGENCY SOS BROADCAST")
            .setContentText(statusMessage)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVibrate(TEST_VIBRATION_PATTERN)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID_SOS, notification)
    }
}
