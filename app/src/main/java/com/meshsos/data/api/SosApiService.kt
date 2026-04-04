package com.meshsos.data.api

import com.meshsos.domain.model.LocationInfo
import com.meshsos.domain.model.SosPacket
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

// ── API models ────────────────────────────────────────────────────────────────

data class UploadRequest(
    val packet: SosPacket,
    val relayDeviceId: String,
    val relayLocation: LocationInfo?
)

data class UploadResponse(
    val success: Boolean,
    val alertId: String,
    val respondersNotified: Int = 0,
    val estimatedArrival: String = "",
    val deduplicated: Boolean = false
)

// ── Retrofit interface ────────────────────────────────────────────────────────

interface SosApiService {
    @POST("api/emergency/sos")
    suspend fun uploadSos(@Body request: UploadRequest): UploadResponse
}

// ── Factory ───────────────────────────────────────────────────────────────────

@Singleton
class SosApiServiceFactory @Inject constructor() {
    fun create(baseUrl: String): SosApiService {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .addInterceptor(RetryInterceptor(maxRetries = 3))
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SosApiService::class.java)
    }
}
