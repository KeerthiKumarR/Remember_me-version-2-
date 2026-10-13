package com.example.rememberme

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.rememberme.data.NetworkClient
import com.example.rememberme.data.PreferencesManager
import com.example.rememberme.data.SosRequest
import com.example.rememberme.notification.NotificationHelper
import com.example.rememberme.notification.ReminderScheduler
import com.example.rememberme.theme.RememberMeTheme
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MainActivity : ComponentActivity() {

    private var mediaSession: MediaSession? = null

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) scheduleReminderIfNeeded()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        NotificationHelper.createChannel(this)
        checkAndRequestBatteryOptimization()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> scheduleReminderIfNeeded()
                else -> notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            scheduleReminderIfNeeded()
        }

        setupWatchSosMediaSession()

        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val prefManager = remember { PreferencesManager(context) }
            var isDarkMode by remember { mutableStateOf(prefManager.darkMode) }

            RememberMeTheme(darkTheme = isDarkMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = if (isDarkMode) Color(0xFF080B12) else Color(0xFFF3F4F6)
                ) {
                    MainNavigation(
                        isDarkMode = isDarkMode,
                        onThemeChanged = { newMode ->
                            prefManager.darkMode = newMode
                            isDarkMode = newMode
                        }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaSession?.release()
    }

    private fun setupWatchSosMediaSession() {
        try {
            mediaSession = MediaSession(this, "RememberMeWatchSos").apply {
                setCallback(object : MediaSession.Callback() {
                    override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                        val keyEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
                        }
                        if (keyEvent != null && keyEvent.action == KeyEvent.ACTION_DOWN) {
                            triggerEmergencySosFromWatch()
                        }
                        return super.onMediaButtonEvent(mediaButtonIntent)
                    }

                    override fun onSkipToNext() {
                        triggerEmergencySosFromWatch()
                    }

                    override fun onPlay() {
                        triggerEmergencySosFromWatch()
                    }
                })
                val state = PlaybackState.Builder()
                    .setActions(
                        PlaybackState.ACTION_PLAY or
                        PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_SKIP_TO_NEXT or
                        PlaybackState.ACTION_SKIP_TO_PREVIOUS
                    )
                    .setState(PlaybackState.STATE_PLAYING, 0L, 1.0f)
                    .build()
                setPlaybackState(state)
                isActive = true
            }
            Log.d("RememberMe", "Watch SOS MediaSession registered successfully")
        } catch (e: Exception) {
            Log.e("RememberMe", "Failed to setup Watch SOS MediaSession", e)
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun triggerEmergencySosFromWatch() {
        val prefManager = PreferencesManager(this)
        val locationClient = LocationServices.getFusedLocationProviderClient(this)
        val nameToSend = if (prefManager.caregiverName.isNotEmpty()) prefManager.caregiverName else "Patient"
        val phoneToSend = if (prefManager.caregiverPhone.isNotEmpty()) prefManager.caregiverPhone else "9876543210"

        val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())

        locationClient.lastLocation.addOnSuccessListener { loc ->
            val lat = loc?.latitude ?: 12.9716
            val lon = loc?.longitude ?: 77.5946
            val mapsLink = "https://maps.google.com/?q=$lat,$lon"

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val api = NetworkClient.createService(prefManager.apiUrl)
                    api.sendSos(
                        SosRequest(
                            person_name = nameToSend,
                            caregiver_phone = phoneToSend,
                            latitude = lat,
                            longitude = lon,
                            location_link = mapsLink,
                            timestamp = timestamp
                        )
                    )
                    withContext(Dispatchers.Main) {
                        NotificationHelper.showSosTriggeredNotification(
                            this@MainActivity,
                            "🚨 SOS Alert dispatched from Watch to caregiver!"
                        )
                        Toast.makeText(this@MainActivity, "🚨 Emergency SOS sent to caregiver from Watch!", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Log.e("WatchSOS", "Failed to send SOS", e)
                }
            }
        }.addOnFailureListener {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val api = NetworkClient.createService(prefManager.apiUrl)
                    api.sendSos(
                        SosRequest(
                            person_name = nameToSend,
                            caregiver_phone = phoneToSend,
                            latitude = 12.9716,
                            longitude = 77.5946,
                            location_link = "https://maps.google.com/?q=12.9716,77.5946",
                            timestamp = timestamp
                        )
                    )
                    withContext(Dispatchers.Main) {
                        NotificationHelper.showSosTriggeredNotification(
                            this@MainActivity,
                            "🚨 SOS Alert dispatched from Watch to caregiver!"
                        )
                        Toast.makeText(this@MainActivity, "🚨 Emergency SOS sent to caregiver from Watch!", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Log.e("WatchSOS", "Failed fallback SOS", e)
                }
            }
        }
    }

    private fun checkAndRequestBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            val packageName = packageName
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                AlertDialog.Builder(this)
                    .setTitle("Battery Optimization Settings Required")
                    .setMessage("To ensure critical notifications and reminders work reliably for the patient, please set Battery Usage to 'Unrestricted' (Ignore Battery Optimizations) for RememberMe.")
                    .setPositiveButton("Set Unrestricted") { _, _ ->
                        try {
                            val intent = Intent().apply {
                                action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                                data = Uri.parse("package:$packageName")
                            }
                            startActivity(intent)
                        } catch (e: Exception) {
                            Log.e("RememberMe", "Failed to launch battery optimization request", e)
                            Toast.makeText(this, "Could not open settings. Please disable battery optimization manually.", Toast.LENGTH_LONG).show()
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }

    private fun scheduleReminderIfNeeded() {
        if (ReminderScheduler.isEnabled(this) && !ReminderScheduler.isScheduled(this)) {
            ReminderScheduler.schedule(this)
        }
    }
}
