package com.example.rememberme.caregiver.data

data class SosAlert(
    val id: String,
    val patientName: String,
    val latitude: Double,
    val longitude: Double,
    val locationLink: String,
    val timestamp: String,
    val resolved: Boolean
)

data class VisitorEntry(
    val personName: String,
    val relationship: String,
    val summary: String,
    val timestamp: String
)

data class DailySummary(
    val date: String,
    val summary: String,
    val peopleMetCount: Int,
    val sosCount: Int,
    val appOpenCount: Int
)
