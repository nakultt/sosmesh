package com.meshsos.presentation.viewmodels

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meshsos.data.api.ViolationUploader
import com.meshsos.domain.detection.Detection
import com.meshsos.domain.detection.NcnnDetector
import com.meshsos.domain.detection.Violation
import com.meshsos.domain.detection.ViolationAnalyzer
import com.meshsos.domain.service.DeviceLocationProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

@HiltViewModel
class DetectionViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ncnnDetector: NcnnDetector,
    private val violationUploader: ViolationUploader,
    private val deviceLocationProvider: DeviceLocationProvider
) : ViewModel() {

    companion object {
        private const val TAG = "DetectionVM"
        private const val DETECTION_INTERVAL_MS = 1000L // match Python 1 s
    }

    // ── Exposed UI state ────────────────────────────────────────────────────

    private val _modelLoaded = MutableStateFlow(false)
    val modelLoaded: StateFlow<Boolean> = _modelLoaded.asStateFlow()

    private val _modelError = MutableStateFlow<String?>(null)
    val modelError: StateFlow<String?> = _modelError.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _detections = MutableStateFlow<List<Detection>>(emptyList())
    val detections: StateFlow<List<Detection>> = _detections.asStateFlow()

    private val _violations = MutableStateFlow<List<Violation>>(emptyList())
    val violations: StateFlow<List<Violation>> = _violations.asStateFlow()

    private val _fps = MutableStateFlow(0f)
    val fps: StateFlow<Float> = _fps.asStateFlow()

    private val _violationsSent = MutableStateFlow(0)
    val violationsSent: StateFlow<Int> = _violationsSent.asStateFlow()

    private val _lastViolation = MutableStateFlow<Violation?>(null)
    val lastViolation: StateFlow<Violation?> = _lastViolation.asStateFlow()

    private val _imageWidth = MutableStateFlow(1)
    val imageWidth: StateFlow<Int> = _imageWidth.asStateFlow()

    private val _imageHeight = MutableStateFlow(1)
    val imageHeight: StateFlow<Int> = _imageHeight.asStateFlow()

    // ── Internal ─────────────────────────────────────────────────────────────

    private val isProcessing = AtomicBoolean(false)
    private var lastInferenceTime = 0L
    private val violationAnalyzer = ViolationAnalyzer()

    // Recent violations log (keeps last 20)
    private val _recentViolations = MutableStateFlow<List<Violation>>(emptyList())
    val recentViolations: StateFlow<List<Violation>> = _recentViolations.asStateFlow()

    // ── Init ────────────────────────────────────────────────────────────────

    init {
        loadModel()
    }

    private fun loadModel() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val ok = ncnnDetector.init(context.assets, 640)
                _modelLoaded.value = ok
                if (!ok) _modelError.value = "Failed to load NCNN model from assets"
            } catch (e: Exception) {
                Log.e(TAG, "Model load error", e)
                _modelError.value = "Model load error: ${e.message}"
            }
        }
    }

    // ── Controls ─────────────────────────────────────────────────────────────

    fun toggleDetection() {
        _isRunning.value = !_isRunning.value
        if (!_isRunning.value) {
            _detections.value = emptyList()
            _violations.value = emptyList()
        }
    }

    // ── Frame processing (called from CameraX analyzer) ─────────────────────

    fun processFrame(bitmap: Bitmap) {
        if (!_isRunning.value || !_modelLoaded.value) {
            bitmap.recycle()
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastInferenceTime < DETECTION_INTERVAL_MS) {
            bitmap.recycle()
            return
        }
        if (!isProcessing.compareAndSet(false, true)) {
            bitmap.recycle()
            return
        }

        lastInferenceTime = now
        val startNs = System.nanoTime()

        _imageWidth.value = bitmap.width
        _imageHeight.value = bitmap.height

        viewModelScope.launch(Dispatchers.Default) {
            try {
                // Run NCNN inference
                val dets = ncnnDetector.detect(bitmap)
                val viols = violationAnalyzer.analyze(dets)

                _detections.value = dets
                _violations.value = viols

                // FPS
                val elapsedSec = (System.nanoTime() - startNs) / 1_000_000_000f
                _fps.value = if (elapsedSec > 0) 1f / elapsedSec else 0f

                // Upload each new violation
                for (violation in viols) {
                    if (!violationUploader.isOnCooldown(violation.type)) {
                        val uploadBitmap = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)
                        launch(Dispatchers.IO) {
                            try {
                                val loc = deviceLocationProvider.getCurrentLocation()
                                val ok = violationUploader.uploadViolation(
                                    violation = violation,
                                    bitmap = uploadBitmap,
                                    gpsLat = loc?.lat ?: 0.0,
                                    gpsLng = loc?.lng ?: 0.0
                                )
                                if (ok) {
                                    _violationsSent.value++
                                    _lastViolation.value = violation
                                    _recentViolations.value = (listOf(violation) +
                                            _recentViolations.value).take(20)
                                }
                            } finally {
                                uploadBitmap.recycle()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Detection error", e)
            } finally {
                bitmap.recycle()
                isProcessing.set(false)
            }
        }
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    override fun onCleared() {
        ncnnDetector.destroy()
        super.onCleared()
    }
}
