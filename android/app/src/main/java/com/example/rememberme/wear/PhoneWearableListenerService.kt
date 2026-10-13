package com.example.rememberme.wear

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.example.rememberme.data.NetworkClient
import com.example.rememberme.data.PreferencesManager
import com.example.rememberme.data.SosRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PhoneWearableListenerService : WearableListenerService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == "/sos") {
            Log.d("PhoneWearable", "🚨 Received emergency /sos message from Wear OS watch!")
            triggerEmergencySosFromWatch(this)
        }
    }

    @SuppressLint("MissingPermission")
    private fun triggerEmergencySosFromWatch(context: Context) {
        // Haptic buzz on phone to acknowledge
        buzzPhone(context)

        val prefManager = PreferencesManager(context)
        val patientName = if (prefManager.caregiverName.isNotBlank()) prefManager.caregiverName else "Patient"
        val caregiverPhone = prefManager.caregiverPhone

        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        val cts = CancellationTokenSource()

        try {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                .addOnSuccessListener { location ->
                    val lat = location?.latitude ?: 0.0
                    val lng = location?.longitude ?: 0.0
                    val locationLink = if (lat != 0.0 && lng != 0.0) {
                        "https://maps.google.com/?q=$lat,$lng"
                    } else ""
                    val nowIso = java.time.Instant.now().toString()

                    serviceScope.launch {
                        try {
                            val api = NetworkClient.createService(prefManager.apiUrl)
                            api.sendSos(
                                SosRequest(
                                    person_name = patientName,
                                    caregiver_phone = caregiverPhone,
                                    latitude = lat,
                                    longitude = lng,
                                    location_link = locationLink,
                                    timestamp = nowIso
                                )
                            )
                            Log.d("PhoneWearable", "Watch SOS successfully dispatched to backend!")
                        } catch (e: Exception) {
                            Log.e("PhoneWearable", "Failed to send Watch SOS to backend", e)
                        }
                    }
                }
                .addOnFailureListener {
                    // Send without coordinates if location fails
                    serviceScope.launch {
                        try {
                            val api = NetworkClient.createService(prefManager.apiUrl)
                            api.sendSos(
                                SosRequest(
                                    person_name = patientName,
                                    caregiver_phone = caregiverPhone,
                                    latitude = 0.0,
                                    longitude = 0.0,
                                    location_link = "",
                                    timestamp = java.time.Instant.now().toString()
                                )
                            )
                        } catch (e: Exception) {
                            Log.e("PhoneWearable", "Failed fallback Watch SOS", e)
                        }
                    }
                }
        } catch (e: SecurityException) {
            Log.e("PhoneWearable", "Location permission not granted for watch SOS", e)
        }
    }

    private fun buzzPhone(context: Context) {
        val pattern = longArrayOf(0, 500, 200, 500)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v?.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                v?.vibrate(pattern, -1)
            }
        }
    }
}
