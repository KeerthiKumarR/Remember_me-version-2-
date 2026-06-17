package com.example.rememberme.data

data class ImagePayload(
    val image: String
)

data class EnrollRequest(
    val name: String,
    val relationship: String,
    val image: String,
    val caregiver_phone: String? = null
)

data class MemoryLogRequest(
    val person_name: String,
    val relationship: String,
    val summary: String,
    val caregiver_phone: String,
    val timestamp: String
)

data class ManualMemoryLogRequest(
    val person_id: String,
    val note: String
)

data class SummarizeRequest(
    val person_id: String
)

data class PersonMatch(
    val person_id: String,
    val name: String,
    val relationship: String,
    val caregiver_phone: String? = null
)

data class IdentifyResponse(
    val match: PersonMatch?,
    val confidence: Double
)

data class SummaryResponse(
    val person_id: String,
    val name: String,
    val relationship: String,
    val summary: String
)

data class SosRequest(
    val person_name: String,
    val caregiver_phone: String,
    val latitude: Double,
    val longitude: Double,
    val location_link: String,
    val timestamp: String
)
