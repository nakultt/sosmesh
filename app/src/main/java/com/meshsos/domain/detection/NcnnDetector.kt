package com.meshsos.domain.detection

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.util.Log

/**
 * Kotlin wrapper around the NCNN JNI detector.
 *
 * Usage:
 *   val detector = NcnnDetector()
 *   detector.init(context.assets)       // call once
 *   val dets = detector.detect(bitmap)  // per frame
 *   detector.destroy()                  // on cleanup
 */
class NcnnDetector {

    companion object {
        private const val TAG = "NcnnDetector"

        init {
            try {
                System.loadLibrary("ncnn_detector")
                Log.i(TAG, "Native library loaded")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load native library: ${e.message}")
            }
        }
    }

    @Volatile
    private var initialized = false

    /** Load model from Android assets. Returns true on success. */
    fun init(assetManager: AssetManager, inputSize: Int = 640): Boolean {
        return try {
            initialized = nativeInit(assetManager, inputSize)
            initialized
        } catch (e: Exception) {
            Log.e(TAG, "init failed: ${e.message}")
            false
        }
    }

    /**
     * Run detection on a Bitmap (must be ARGB_8888).
     * Returns detections with pixel coordinates in the original image space.
     */
    fun detect(
        bitmap: Bitmap,
        confThreshold: Float = 0.25f,
        nmsThreshold: Float = 0.45f
    ): List<Detection> {
        if (!initialized) return emptyList()

        val result: FloatArray = try {
            nativeDetect(bitmap, confThreshold, nmsThreshold) ?: return emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "detect failed: ${e.message}")
            return emptyList()
        }

        val numDetections = result[0].toInt()
        val detections = ArrayList<Detection>(numDetections)

        for (i in 0 until numDetections) {
            val off = 1 + i * 6
            val classId = result[off].toInt()
            detections.add(
                Detection(
                    classId    = classId,
                    className  = RoadEyeClasses.nameOf(classId),
                    confidence = result[off + 1],
                    x1         = result[off + 2],
                    y1         = result[off + 3],
                    x2         = result[off + 4],
                    y2         = result[off + 5]
                )
            )
        }
        return detections
    }

    fun destroy() {
        if (initialized) {
            try { nativeDestroy() } catch (_: Exception) {}
            initialized = false
        }
    }

    val isInitialized: Boolean get() = initialized

    // ── JNI declarations ─────────────────────────────────────────────────────
    private external fun nativeInit(assetManager: AssetManager, inputSize: Int): Boolean
    private external fun nativeDetect(bitmap: Bitmap, confThreshold: Float, nmsThreshold: Float): FloatArray?
    private external fun nativeDestroy()
}
