package com.example.rememberme.caregiver.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "caregiver_prefs")

class PreferencesManager(private val context: Context) {

    companion object {
        private val KEY_PATIENT_NAME = stringPreferencesKey("patient_name")
        private val KEY_PATIENT_PHONE = stringPreferencesKey("patient_phone")
        private val KEY_CAREGIVER_PHONE = stringPreferencesKey("caregiver_phone")
        private val KEY_API_URL = stringPreferencesKey("api_url")
        
        private const val DEFAULT_API_URL = "https://miraiwininghacathonproject-production.up.railway.app"
    }

    val patientNameFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_PATIENT_NAME] ?: "Patient"
    }

    val patientPhoneFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_PATIENT_PHONE] ?: "1234567890"
    }

    val caregiverPhoneFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_CAREGIVER_PHONE] ?: "9876543210"
    }

    val apiUrlFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_API_URL] ?: DEFAULT_API_URL
    }

    var patientNameSync: String
        get() = runBlocking { context.dataStore.data.first()[KEY_PATIENT_NAME] ?: "Patient" }
        set(value) = runBlocking { context.dataStore.edit { it[KEY_PATIENT_NAME] = value } }

    var patientPhoneSync: String
        get() = runBlocking { context.dataStore.data.first()[KEY_PATIENT_PHONE] ?: "1234567890" }
        set(value) = runBlocking { context.dataStore.edit { it[KEY_PATIENT_PHONE] = value } }

    var caregiverPhoneSync: String
        get() = runBlocking { context.dataStore.data.first()[KEY_CAREGIVER_PHONE] ?: "9876543210" }
        set(value) = runBlocking { context.dataStore.edit { it[KEY_CAREGIVER_PHONE] = value } }

    var apiUrlSync: String
        get() = runBlocking { context.dataStore.data.first()[KEY_API_URL] ?: DEFAULT_API_URL }
        set(value) = runBlocking { context.dataStore.edit { it[KEY_API_URL] = value } }

    suspend fun saveSettings(
        patientName: String,
        patientPhone: String,
        caregiverPhone: String,
        apiUrl: String
    ) {
        context.dataStore.edit { preferences ->
            preferences[KEY_PATIENT_NAME] = patientName
            preferences[KEY_PATIENT_PHONE] = patientPhone
            preferences[KEY_CAREGIVER_PHONE] = caregiverPhone
            preferences[KEY_API_URL] = apiUrl
        }
    }
}
