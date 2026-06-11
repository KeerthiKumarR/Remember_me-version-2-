package com.example.rememberme.data

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("rememberme_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_API_URL = "api_url"
        private const val DEFAULT_API_URL = "https://miraiwininghacathonproject-production.up.railway.app"
        private const val KEY_CAREGIVER_NAME = "caregiver_name"
        private const val KEY_CAREGIVER_PHONE = "caregiver_phone"
    }

    var apiUrl: String
        get() {
            val current = prefs.getString(KEY_API_URL, DEFAULT_API_URL) ?: DEFAULT_API_URL
            return if (current.contains("10.201.") || current.contains("127.0.0.1") || current.contains("localhost")) {
                prefs.edit().putString(KEY_API_URL, DEFAULT_API_URL).apply()
                DEFAULT_API_URL
            } else {
                current
            }
        }
        set(value) {
            prefs.edit().putString(KEY_API_URL, value).apply()
        }

    var caregiverName: String
        get() = prefs.getString(KEY_CAREGIVER_NAME, "Caregiver") ?: "Caregiver"
        set(value) {
            prefs.edit().putString(KEY_CAREGIVER_NAME, value).apply()
        }

    var caregiverPhone: String
        get() = prefs.getString(KEY_CAREGIVER_PHONE, "9876543210") ?: "9876543210"
        set(value) {
            prefs.edit().putString(KEY_CAREGIVER_PHONE, value).apply()
        }

    var remindersEnabled: Boolean
        get() = prefs.getBoolean("reminders_enabled", true)
        set(value) { prefs.edit().putBoolean("reminders_enabled", value).apply() }

    var reminderIntervalMinutes: Int
        get() = prefs.getInt("reminder_interval_minutes", 2)
        set(value) { prefs.edit().putInt("reminder_interval_minutes", value).apply() }

    var cameraEnabled: Boolean
        get() = prefs.getBoolean("camera_enabled", true)
        set(value) { prefs.edit().putBoolean("camera_enabled", value).apply() }

}
