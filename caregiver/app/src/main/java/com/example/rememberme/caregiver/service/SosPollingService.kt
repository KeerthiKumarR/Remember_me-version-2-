package com.example.rememberme.caregiver.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.rememberme.caregiver.MainActivity
import com.example.rememberme.caregiver.data.NetworkClient
import com.example.rememberme.caregiver.data.PreferencesManager
import com.example.rememberme.caregiver.data.SosAlert
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SosPollingService : Service() {

    companion object {
        private const val TAG = "SosPollingService"
        private const val FOREGROUND_CHANNEL_ID = "caregiver_service_channel"
        private const val FOREGROUND_NOTIF_ID = 1001
        
        private const val SOS_CHANNEL_ID = "sos_alerts_channel"
        private const val SOS_NOTIF_ID = 1002
    }

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private lateinit var preferencesManager: PreferencesManager
    private var lastSeenSosId: String? = null

    override fun onCreate() {
        super.onCreate()
        preferencesManager = PreferencesManager(this)
        createNotificationChannels()
        startForeground(FOREGROUND_NOTIF_ID, buildForegroundNotification())
        startPollingLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "SosPollingService Started")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        Log.d(TAG, "SosPollingService Destroyed")
    }

    private fun startPollingLoop() {
        serviceScope.launch {
            while (isActive) {
                try {
                    val caregiverPhone = preferencesManager.caregiverPhoneSync
                    val apiUrl = preferencesManager.apiUrlSync
                    
                    Log.d(TAG, "Polling active SOS alert for caregiver phone: $caregiverPhone at $apiUrl")
                    val api = NetworkClient.createService(apiUrl)
                    val activeSos: SosAlert? = api.getActiveSos(caregiverPhone)
                    
                    if (activeSos != null && !activeSos.resolved) {
                        Log.d(TAG, "Active SOS Alert Detected: $activeSos")
                        if (activeSos.id != lastSeenSosId) {
                            lastSeenSosId = activeSos.id
                            triggerSosAlertNotification(activeSos.patientName)
                        }
                    } else {
                        lastSeenSosId = null
                    }
                } catch (e: retrofit2.HttpException) {
                    if (e.code() == 404) {
                        Log.d(TAG, "No active SOS found (404)")
                        lastSeenSosId = null
                    } else {
                        Log.e(TAG, "HTTP error during SOS polling: ${e.message()}", e)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error during SOS polling: ${e.message}", e)
                }
                delay(30000)
            }
        }
    }

    private fun buildForegroundNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, FOREGROUND_CHANNEL_ID)
            .setContentTitle("RememberMe Caregiver Active")
            .setContentText("Monitoring patient status in background...")
            .setSmallIcon(android.R.drawable.ic_menu_myplaces)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun triggerSosAlertNotification(patientName: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("LAUNCH_SOS_SCREEN", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val builder = NotificationCompat.Builder(this, SOS_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("🚨 EMERGENCY SOS ACTIVE")
            .setContentText("$patientName needs assistance. Click to open map.")
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(SOS_NOTIF_ID, builder.build())
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            val foregroundChannel = NotificationChannel(
                FOREGROUND_CHANNEL_ID,
                "Caregiver Monitoring Status",
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(foregroundChannel)

            val sosChannel = NotificationChannel(
                SOS_CHANNEL_ID,
                "SOS Emergency Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                enableLights(true)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
            }
            manager.createNotificationChannel(sosChannel)
        }
    }
}
