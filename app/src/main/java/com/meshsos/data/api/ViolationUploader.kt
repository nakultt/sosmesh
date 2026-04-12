package com.meshsos.data.api

import android.graphics.Bitmap
import android.util.Log
import com.google.gson.Gson
import com.meshsos.domain.detection.Violation
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.time.Instant

/**
 * Uploads violation events to the PACER backend.
 * Mirrors the Python detect_service.py logic:
 * - Cooldown per violation type (prevents flooding)
 * - Multipart POST with image + JSON data
 * - Offline-resilient (catches network errors silently)
 */
class ViolationUploader(
    private val apiService: PacerApiService,
    private val cameraId: String,
    private val gson: Gson,
    private val tempDir: File,
    private val cooldownSeconds: Long = 30
) {
    companion object {
        private const val TAG = "ViolationUploader"
    }

    private val cooldownTracker = mutableMapOf<String, Long>()

    fun isOnCooldown(violationType: String): Boolean {
        val last = cooldownTracker[violationType] ?: return false
        return System.currentTimeMillis() - last < cooldownSeconds * 1000
    }

    private fun markSent(violationType: String) {
        cooldownTracker[violationType] = System.currentTimeMillis()
    }

    /**
     * Upload a violation event with a snapshot image.
     * Returns true on success.
     */
    suspend fun uploadViolation(
        violation: Violation,
        bitmap: Bitmap,
        gpsLat: Double,
        gpsLng: Double,
        locationLabel: String = "Android Detection"
    ): Boolean {
        if (isOnCooldown(violation.type)) return false

        val tmpFile = File(tempDir, "pacer_${System.currentTimeMillis()}_${violation.type}.jpg")
        try {
            // Save frame as JPEG
            FileOutputStream(tmpFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }

            // Build bounding-box list (matching Python clean_boxes format)
            val cleanBoxes = violation.boundingBoxes.map { box ->
                mapOf(
                    "x" to box.x,
                    "y" to box.y,
                    "w" to box.w,
                    "h" to box.h,
                    "label" to box.label,
                    "confidence" to box.confidence
                )
            }

            // Build event data (matching Python event_data structure exactly)
            val eventData = mapOf(
                "violation_type"  to violation.type,
                "confidence"      to violation.confidence,
                "timestamp"       to Instant.now().toString(),
                "camera_source"   to "android_camera",
                "camera_id"       to cameraId,
                "gps_lat"         to gpsLat,
                "gps_lng"         to gpsLng,
                "location_label"  to locationLabel,
                "bounding_boxes"  to cleanBoxes
            )

            val imageBody = tmpFile.asRequestBody("image/jpeg".toMediaType())
            val imagePart = MultipartBody.Part.createFormData("image", "frame.jpg", imageBody)
            val dataPart = gson.toJson(eventData).toRequestBody("text/plain".toMediaType())

            val response = apiService.sendEvent(imagePart, dataPart)

            return if (response.isSuccessful) {
                val body = response.body()
                if (body?.status == "deduplicated") {
                    Log.i(TAG, "→ Deduplicated by backend")
                } else {
                    Log.i(TAG, "→ Sent OK (violation_id: ${body?.violation_id ?: "?"})")
                }
                markSent(violation.type)
                true
            } else {
                Log.w(TAG, "→ Backend returned HTTP ${response.code()}")
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "→ Send failed: ${e.message}")
            return false
        } finally {
            tmpFile.delete()
        }
    }
}
