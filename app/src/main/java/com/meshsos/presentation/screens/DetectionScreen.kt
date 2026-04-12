package com.meshsos.presentation.screens

import android.Manifest
import android.graphics.Paint
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.meshsos.domain.detection.Detection
import com.meshsos.domain.detection.RoadEyeClasses
import com.meshsos.domain.detection.Violation
import com.meshsos.presentation.theme.MeshTeal
import com.meshsos.presentation.theme.SafeGreen
import com.meshsos.presentation.theme.SosRed
import com.meshsos.presentation.theme.WarnAmber
import com.meshsos.presentation.viewmodels.DetectionViewModel
import java.util.concurrent.Executors

// ═══════════════════════════════════════════════════════════════════════════════
//  Entry point
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun DetectionScreen(viewModel: DetectionViewModel = hiltViewModel()) {
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

    if (cameraPermission.status.isGranted) {
        DetectionContent(viewModel)
    } else {
        CameraPermissionRequest(onRequestPermission = { cameraPermission.launchPermissionRequest() })
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Camera permission request screen
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun CameraPermissionRequest(onRequestPermission: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("\uD83D\uDCF7", fontSize = 48.sp)
                Spacer(Modifier.height(16.dp))
                Text(
                    "Camera Access Required",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "The detection tab needs camera access to analyze live video for traffic violations.",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onRequestPermission,
                    colors = ButtonDefaults.buttonColors(containerColor = MeshTeal),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Grant Camera Permission", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Main detection content
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun DetectionContent(viewModel: DetectionViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val isRunning by viewModel.isRunning.collectAsState()
    val detections by viewModel.detections.collectAsState()
    val violations by viewModel.violations.collectAsState()
    val fps by viewModel.fps.collectAsState()
    val violationsSent by viewModel.violationsSent.collectAsState()
    val modelLoaded by viewModel.modelLoaded.collectAsState()
    val modelError by viewModel.modelError.collectAsState()
    val lastViolation by viewModel.lastViolation.collectAsState()
    val imageWidth by viewModel.imageWidth.collectAsState()
    val imageHeight by viewModel.imageHeight.collectAsState()
    val recentViolations by viewModel.recentViolations.collectAsState()

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember { PreviewView(context) }

    // ── Camera lifecycle ────────────────────────────────────────────────────
    DisposableEffect(lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                        try {
                            val w = imageProxy.width
                            val h = imageProxy.height
                            val buffer = imageProxy.planes[0].buffer
                            val rowStride = imageProxy.planes[0].rowStride
                            val pixelStride = imageProxy.planes[0].pixelStride

                            val bitmap = android.graphics.Bitmap.createBitmap(
                                w, h, android.graphics.Bitmap.Config.ARGB_8888
                            )

                            // Handle row padding if present
                            if (rowStride == w * pixelStride) {
                                buffer.rewind()
                                bitmap.copyPixelsFromBuffer(buffer)
                            } else {
                                // Row stride has padding — copy row by row
                                val rowBytes = w * pixelStride
                                val rowBuffer = ByteArray(rowStride)
                                val pixelBuffer = java.nio.ByteBuffer.allocate(w * h * 4)
                                buffer.rewind()
                                for (row in 0 until h) {
                                    buffer.get(rowBuffer, 0, minOf(rowStride, buffer.remaining()))
                                    pixelBuffer.put(rowBuffer, 0, rowBytes)
                                }
                                pixelBuffer.rewind()
                                bitmap.copyPixelsFromBuffer(pixelBuffer)
                            }

                            viewModel.processFrame(bitmap)
                        } catch (_: Exception) {
                            // Frame processing error — skip
                        } finally {
                            imageProxy.close()
                        }
                    }
                }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis
                )
            } catch (_: Exception) {}
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            try {
                cameraProviderFuture.get().unbindAll()
            } catch (_: Exception) {}
            analysisExecutor.shutdown()
        }
    }

    // ── UI Layout ───────────────────────────────────────────────────────────

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ── Camera preview + bounding box overlay ───────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
        ) {
            // Camera preview
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )

            // Bounding box overlay
            if (isRunning && detections.isNotEmpty()) {
                BoundingBoxOverlay(
                    detections = detections,
                    imageWidth = imageWidth,
                    imageHeight = imageHeight
                )
            }

            // FPS badge (top-right) — using Box + if instead of AnimatedVisibility in BoxScope
            if (isRunning) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                ) {
                    Text(
                        text = "FPS: ${"%.1f".format(fps)}",
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Detection count badge (top-left)
            if (isRunning && detections.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                ) {
                    Text(
                        text = "${detections.size} detections",
                        modifier = Modifier
                            .background(MeshTeal.copy(alpha = 0.75f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Live violation banner (bottom-center)
            if (isRunning && violations.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .background(SosRed.copy(alpha = 0.85f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("\u26A0", fontSize = 16.sp)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            violations.take(3).forEach { v ->
                                Text(
                                    "${v.type.replace('_', ' ').uppercase()} (${"%.0f".format(v.confidence * 100)}%)",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Model loading overlay
            if (!modelLoaded) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = modelError ?: "Loading NCNN model\u2026",
                            color = if (modelError != null) SosRed else Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                    }
                }
            }
        }

        // ── Controls panel ────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Stats row
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatItem("Violations Sent", "$violationsSent", SafeGreen)
                    StatItem(
                        "Status",
                        if (isRunning) "Active" else "Idle",
                        if (isRunning) SafeGreen else WarnAmber
                    )
                    StatItem(
                        "Last",
                        lastViolation?.type?.replace('_', ' ') ?: "\u2014",
                        SosRed
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Start/Stop button
            Button(
                onClick = { viewModel.toggleDetection() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = modelLoaded,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) SosRed else MeshTeal,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = if (isRunning) "\u23F9  Stop Detection" else "\u25B6  Start Detection",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Spacer(Modifier.height(10.dp))

            // Recent violations log
            if (recentViolations.isNotEmpty()) {
                Text(
                    "Recent Violations",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                LazyColumn(
                    modifier = Modifier.height(100.dp)
                ) {
                    items(recentViolations) { violation ->
                        ViolationLogItem(violation)
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Bounding box overlay
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun BoundingBoxOverlay(
    detections: List<Detection>,
    imageWidth: Int,
    imageHeight: Int
) {
    val violationColor = SosRed
    val safeColor = SafeGreen

    Canvas(modifier = Modifier.fillMaxSize()) {
        val scaleX = size.width / imageWidth.toFloat()
        val scaleY = size.height / imageHeight.toFloat()

        detections.forEach { det ->
            val left = det.x1 * scaleX
            val top = det.y1 * scaleY
            val right = det.x2 * scaleX
            val bottom = det.y2 * scaleY

            val isViolation = det.className in RoadEyeClasses.NO_HELMET ||
                    det.className == "wheeling" ||
                    det.className == "pothole" ||
                    det.className in RoadEyeClasses.ANIMALS

            val boxColor = if (isViolation) violationColor else safeColor

            // Draw box
            drawRect(
                color = boxColor,
                topLeft = Offset(left, top),
                size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
                style = Stroke(width = 3f)
            )

            // Draw label background + text
            val labelText = "${det.className} ${"%.0f".format(det.confidence * 100)}%"
            drawIntoCanvas { canvas ->
                val textPaint = Paint().apply {
                    color = Color.White.toArgb()
                    textSize = 28f
                    isAntiAlias = true
                    setShadowLayer(4f, 0f, 0f, android.graphics.Color.BLACK)
                }
                val bgPaint = Paint().apply {
                    color = boxColor.copy(alpha = 0.7f).toArgb()
                    style = Paint.Style.FILL
                }

                val textW = textPaint.measureText(labelText)
                val textH = textPaint.textSize

                canvas.nativeCanvas.drawRect(
                    left, maxOf(top - textH - 6f, 0f),
                    left + textW + 12f, maxOf(top, textH + 6f),
                    bgPaint
                )
                canvas.nativeCanvas.drawText(
                    labelText,
                    left + 4f,
                    maxOf(top - 6f, textH),
                    textPaint
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Sub-components
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun StatItem(label: String, value: String, accent: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            color = accent,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            label,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
            fontSize = 11.sp
        )
    }
}

@Composable
private fun ViolationLogItem(violation: Violation) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(SosRed)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            violation.type.replace('_', ' '),
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        Text(
            "${"%.0f".format(violation.confidence * 100)}%",
            color = WarnAmber,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
