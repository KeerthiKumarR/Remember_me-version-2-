package com.example.rememberme.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

object ReminderScheduler {

    private const val TAG = "ReminderScheduler"
    private const val REQUEST_CODE = 2001

    fun schedule(context: Context) {
        scheduleAt(context, System.currentTimeMillis() + getIntervalMs(context))
        setEnabled(context, true)
    }

    fun scheduleNext(context: Context) {
        if (!isEnabled(context)) return
        scheduleAt(context, System.currentTimeMillis() + getIntervalMs(context))
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(buildPendingIntent(context))
        setEnabled(context, false)
    }

    fun isScheduled(context: Context): Boolean {
        if (!isEnabled(context)) return false
        return PendingIntent.getBroadcast(
            context, REQUEST_CODE,
            Intent(context, ReminderReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) != null
    }

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences("rememberme_prefs", Context.MODE_PRIVATE)
            .getBoolean("reminders_enabled", true)

    private fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences("rememberme_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("reminders_enabled", enabled).apply()
    }

    fun getIntervalMinutes(context: Context): Int =
        context.getSharedPreferences("rememberme_prefs", Context.MODE_PRIVATE)
            .getInt("reminder_interval_minutes", 90)

    fun setIntervalMinutes(context: Context, minutes: Int) {
        context.getSharedPreferences("rememberme_prefs", Context.MODE_PRIVATE)
            .edit().putInt("reminder_interval_minutes", minutes).apply()
    }

    private fun getIntervalMs(context: Context) = getIntervalMinutes(context) * 60L * 1_000L

    private fun scheduleAt(context: Context, triggerAt: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = buildPendingIntent(context)
        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) am.canScheduleExactAlarms() else true
        if (canExact) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
        Log.d(TAG, "Alarm set in ${(triggerAt - System.currentTimeMillis()) / 60_000} min")
    }

    private fun buildPendingIntent(context: Context) = PendingIntent.getBroadcast(
        context, REQUEST_CODE,
        Intent(context, ReminderReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
