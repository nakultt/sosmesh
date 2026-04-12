package com.meshsos.data.api

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

/**
 * Retrofit interface for the PACER violation-detection backend.
 * Matches the endpoint used by the Pi detect_service.py exactly:
 *   POST /api/events  (multipart: image + JSON data)
 */
interface PacerApiService {

    @Multipart
    @POST("api/events")
    suspend fun sendEvent(
        @Part image: MultipartBody.Part,
        @Part("data") data: RequestBody
    ): Response<EventResponse>
}

data class EventResponse(
    val status: String? = null,
    val violation_id: String? = null
)
