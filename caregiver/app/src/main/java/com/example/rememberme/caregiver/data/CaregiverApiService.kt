package com.example.rememberme.caregiver.data

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface CaregiverApiService {
    @GET("sos/active")
    suspend fun getActiveSos(@Query("caregiver_phone") phone: String): SosAlert?

    @POST("sos/resolve/{id}")
    suspend fun resolveSos(@Path("id") id: String): Response<Unit>

    @GET("memory-log")
    suspend fun getMemoryLog(@Query("caregiver_phone") phone: String): List<VisitorEntry>

    @GET("daily-summary")
    suspend fun getDailySummary(@Query("caregiver_phone") phone: String): DailySummary
}
