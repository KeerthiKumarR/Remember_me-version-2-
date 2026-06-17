package com.example.rememberme.data

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

interface ApiService {
    @POST("identify")
    suspend fun identifyFace(@Body payload: ImagePayload): IdentifyResponse

    @POST("summarize")
    suspend fun summarizePerson(@Body payload: SummarizeRequest): SummaryResponse

    @POST("enroll")
    suspend fun enrollPerson(@Body payload: EnrollRequest): PersonMatch

    @POST("memory/log")
    suspend fun logMemory(@Body payload: MemoryLogRequest): Map<String, Any>

    @POST("memory/log")
    suspend fun logManualMemory(@Body payload: ManualMemoryLogRequest): Map<String, Any>

    @POST("sos")
    suspend fun sendSos(@Body payload: SosRequest): Map<String, Any>
}

object NetworkClient {
    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val httpClient = OkHttpClient.Builder()
        .addInterceptor(logging)
        .build()

    fun createService(baseUrl: String): ApiService {
        val formattedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return Retrofit.Builder()
            .baseUrl(formattedUrl)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
