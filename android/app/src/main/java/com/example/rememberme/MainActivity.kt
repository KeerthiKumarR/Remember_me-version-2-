package com.example.rememberme

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.rememberme.notification.NotificationHelper
import com.example.rememberme.notification.ReminderScheduler
import com.example.rememberme.theme.RememberMeTheme
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.app.AlertDialog
import android.util.Log
import android.widget.Toast

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) scheduleReminderIfNeeded()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ReminderScheduler.setIntervalMinutes(this, 2)
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

        enableEdgeToEdge()
        setContent {
            RememberMeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainNavigation()
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
