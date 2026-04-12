package com.meshsos.presentation.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshsos.domain.model.HelperStatus
import com.meshsos.domain.model.IncidentCategory
import com.meshsos.domain.model.LocationInfo
import com.meshsos.domain.model.SosPacket
import com.meshsos.domain.model.Severity
import com.meshsos.domain.statemachine.LocalHelpUpdate
import com.meshsos.domain.statemachine.MeshState
import com.meshsos.presentation.theme.MeshTeal
import com.meshsos.presentation.theme.SafeGreen
import com.meshsos.presentation.theme.SosRed
import com.meshsos.presentation.theme.SosRedDark
import com.meshsos.presentation.theme.WarnAmber
import com.meshsos.presentation.viewmodels.MeshViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import java.util.Locale

@Composable
fun SosScreen(viewModel: MeshViewModel) {
    val meshState by viewModel.meshState.collectAsState()
    val peerCount by viewModel.peerCount.collectAsState()
    val isSending by viewModel.isSending.collectAsState()
    val sendError by viewModel.sendError.collectAsState()
    val transportName by viewModel.activeTransportName.collectAsState()
    val pendingCount by viewModel.pendingPacketCount.collectAsState()
    val activeHelperStatus by viewModel.activeHelperStatus.collectAsState()

    var selectedCategory by remember { mutableStateOf(IncidentCategory.MEDICAL) }
    var message by remember { mutableStateOf("") }
    var showCategoryDropdown by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(sendError) {
        sendError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissError()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // ── Status bar ────────────────────────────────────────────────────
            StatusBar(
                peerCount = peerCount,
                meshState = meshState,
                transportName = transportName,
                pendingCount = pendingCount,
                batteryLevel = viewModel.batteryLevel
            )

            Spacer(Modifier.height(20.dp))

            // ── State feedback ────────────────────────────────────────────────
            StateCard(
                meshState = meshState,
                onReset = { viewModel.resetState() },
                canSendLocalHelpUpdate = viewModel.canSendLocalHelpUpdate(),
                activeHelperStatus = activeHelperStatus,
                onHelperStatusUpdate = { viewModel.sendHelperStatusUpdate(it) }
            )

            Spacer(Modifier.height(20.dp))

            // ── Incident type ─────────────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Incident Type",
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Box {
                            TextButton(onClick = { showCategoryDropdown = true }) {
                                Text(
                                    selectedCategory.displayName(),
                                    color = WarnAmber,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text("  ▼", color = WarnAmber, fontSize = 12.sp)
                            }
                            DropdownMenu(
                                expanded = showCategoryDropdown,
                                onDismissRequest = { showCategoryDropdown = false }
                            ) {
                                IncidentCategory.values().forEach { cat ->
                                    DropdownMenuItem(
                                        text = { Text(cat.displayName()) },
                                        onClick = {
                                            selectedCategory = cat
                                            showCategoryDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // ── Optional message ──────────────────────────────────────
                    OutlinedTextField(
                        value = message,
                        onValueChange = { message = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                "Optional details (e.g. 2nd floor, blue car)…",
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f),
                                fontSize = 14.sp
                            )
                        },
                        maxLines = 3,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MeshTeal,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            cursorColor = MeshTeal,
                            focusedTextColor = MaterialTheme.colorScheme.onBackground,
                            unfocusedTextColor = MaterialTheme.colorScheme.onBackground
                        )
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            // ── SOS button ────────────────────────────────────────────────────
            SosButton(
                isActive = meshState is MeshState.Originator || isSending,
                enabled = meshState !is MeshState.Confirmed,
                onSos = {
                    viewModel.sendSos(
                        category = selectedCategory,
                        severity = Severity.CRITICAL,
                        message = message
                    )
                }
            )

            Spacer(Modifier.height(12.dp))
            Text(
                "Hold 2 seconds to send emergency SOS",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(32.dp))
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
        )
    }
}

// ── SOS Button (long-press with visual fill) ──────────────────────────────────

@Composable
fun SosButton(
    isActive: Boolean,
    enabled: Boolean,
    onSos: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var progress by remember { mutableFloatStateOf(0f) }
    var isHolding by remember { mutableStateOf(false) }

    val animatedScale by animateFloatAsState(
        targetValue = if (isHolding) 0.92f else 1f,
        animationSpec = tween(150),
        label = "sosScale"
    )

    val buttonColor = when {
        isActive -> SosRedDark
        else -> SosRed
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(200.dp)
            .scale(animatedScale)
            .shadow(
                elevation = if (isActive) 16.dp else 8.dp,
                shape = CircleShape,
                ambientColor = SosRed.copy(alpha = 0.3f),
                spotColor = SosRed.copy(alpha = 0.3f)
            )
            .clip(CircleShape)
            .background(buttonColor)
            .border(
                4.dp,
                if (isActive) Color.White else SosRed.copy(alpha = 0.6f),
                CircleShape
            )
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        isHolding = true
                        progress = 0f
                        scope.launch {
                            repeat(40) {
                                if (!isHolding) return@repeat
                                progress = (it + 1) / 40f
                                delay(50)
                            }
                            if (isHolding) {
                                onSos()
                            }
                        }
                        tryAwaitRelease()
                        isHolding = false
                        progress = 0f
                    }
                )
            }
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (isActive) "SENT" else "SOS",
                color = Color.White,
                fontSize = 40.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 4.sp
            )
            if (isHolding && progress < 1f) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "${(progress * 2).toInt() + 1}s…",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp
                )
            }
        }
    }
}

// ── Status Bar ────────────────────────────────────────────────────────────────

@Composable
fun StatusBar(
    peerCount: Int,
    meshState: MeshState,
    transportName: String,
    pendingCount: Int,
    batteryLevel: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Transport mode
            val isNearby = transportName.contains("Nearby")
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (peerCount > 0) SafeGreen else WarnAmber)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (isNearby) "WiFi Direct + BLE" else "Pure BLE",
                        color = if (isNearby) SafeGreen else WarnAmber,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "$peerCount peer${if (peerCount != 1) "s" else ""} nearby",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    fontSize = 12.sp
                )
            }

            // Center: pending queue
            if (pendingCount > 0) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = WarnAmber.copy(alpha = 0.12f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "$pendingCount queued",
                        color = WarnAmber,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // Battery
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "$batteryLevel%",
                    color = when {
                        batteryLevel > 50 -> SafeGreen
                        batteryLevel > 20 -> WarnAmber
                        else -> SosRed
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "battery",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                    fontSize = 11.sp
                )
            }
        }
    }
}

// ── State feedback card ───────────────────────────────────────────────────────

@Composable
fun StateCard(
    meshState: MeshState,
    onReset: () -> Unit,
    canSendLocalHelpUpdate: Boolean,
    activeHelperStatus: HelperStatus?,
    onHelperStatusUpdate: (HelperStatus) -> Unit
) {
    AnimatedVisibility(visible = meshState !is MeshState.Idle) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = when (meshState) {
                    is MeshState.Confirmed -> SafeGreen.copy(alpha = 0.08f)
                    is MeshState.Error -> SosRed.copy(alpha = 0.08f)
                    is MeshState.Uploading, is MeshState.AwaitingAck -> WarnAmber.copy(alpha = 0.06f)
                    else -> MaterialTheme.colorScheme.surface
                }
            ),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = meshState.title(),
                    fontWeight = FontWeight.Bold,
                    color = meshState.titleColor(),
                    fontSize = 16.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = meshState.subtitle(),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                if (meshState is MeshState.Originator) {
                    Spacer(Modifier.height(8.dp))
                    StateDetailRow("Server Status", "Pending confirmation")
                }
                if (meshState is MeshState.Confirmed) {
                    Spacer(Modifier.height(8.dp))
                    StateDetailRow("Server Status", "Uploaded and acknowledged")
                }
                val receivedPacket = meshState.receivedPacketForDisplay()
                if (receivedPacket != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Received SOS Details",
                        color = WarnAmber,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    StateDetailRow("Type", receivedPacket.type.name)
                    StateDetailRow(
                        "Emergency",
                        "${receivedPacket.incident.category.displayName()} / ${receivedPacket.incident.severity.name}"
                    )
                    StateDetailRow(
                        "Message",
                        receivedPacket.incident.message.ifBlank { "No additional message shared." }
                    )
                    StateDetailRow("Location", receivedPacket.locationSummary())
                    if (canSendLocalHelpUpdate) {
                        Spacer(Modifier.height(8.dp))
                        HelperWorkflowActions(
                            activeStatus = activeHelperStatus,
                            onStatusUpdate = onHelperStatusUpdate
                        )
                    }
                }

                val victimUpdates = meshState.victimLocalHelpUpdates()
                if (victimUpdates.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Nearby Helper Updates",
                        color = SafeGreen,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    victimUpdates.latestByHelper().forEach { update ->
                        StateDetailRow(
                            label = update.helperDeviceId.shortDeviceId(),
                            value = "${update.status.displayName()} • ${update.eta}"
                        )
                    }

                    val victimLocation = meshState.victimPacketForDisplay()?.incident?.location
                    if (victimLocation != null || victimUpdates.any { it.location != null }) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Live Responder Tracking",
                            color = WarnAmber,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        ResponderTrackingMap(
                            victimLocation = victimLocation,
                            helperUpdates = victimUpdates
                        )
                    }
                }
                if (meshState is MeshState.Confirmed || meshState is MeshState.Error) {
                    Spacer(Modifier.height(10.dp))
                    TextButton(onClick = onReset) {
                        Text("Reset", color = MeshTeal, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun HelperWorkflowActions(
    activeStatus: HelperStatus?,
    onStatusUpdate: (HelperStatus) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Helper Workflow",
            color = SafeGreen,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HelperWorkflowButton(
                label = "Accept",
                status = HelperStatus.ACCEPTED,
                activeStatus = activeStatus,
                onClick = onStatusUpdate
            )
            HelperWorkflowButton(
                label = "En route",
                status = HelperStatus.EN_ROUTE,
                activeStatus = activeStatus,
                onClick = onStatusUpdate
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HelperWorkflowButton(
                label = "Reached",
                status = HelperStatus.REACHED,
                activeStatus = activeStatus,
                onClick = onStatusUpdate
            )
            HelperWorkflowButton(
                label = "Cannot continue",
                status = HelperStatus.CANNOT_CONTINUE,
                activeStatus = activeStatus,
                onClick = onStatusUpdate
            )
        }
    }
}

@Composable
private fun RowScope.HelperWorkflowButton(
    label: String,
    status: HelperStatus,
    activeStatus: HelperStatus?,
    onClick: (HelperStatus) -> Unit
) {
    val selected = activeStatus == status
    val accent = when (status) {
        HelperStatus.ACCEPTED -> SafeGreen
        HelperStatus.EN_ROUTE -> WarnAmber
        HelperStatus.REACHED -> SafeGreen
        HelperStatus.CANNOT_CONTINUE -> SosRed
    }
    Button(
        onClick = { onClick(status) },
        modifier = Modifier.weight(1f),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) accent else accent.copy(alpha = 0.12f),
            contentColor = if (selected) Color.White else accent
        )
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ResponderTrackingMap(
    victimLocation: LocationInfo?,
    helperUpdates: List<LocalHelpUpdate>
) {
    val helperTrails = helperUpdates
        .filter { it.location != null }
        .groupBy { it.helperDeviceId }
        .mapValues { (_, updates) -> updates.sortedBy { it.timestamp } }

    if (victimLocation == null && helperTrails.isEmpty()) {
        Text(
            "Live locations are not available yet.",
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
            fontSize = 12.sp
        )
        return
    }

    val allPoints = buildList {
        if (victimLocation != null) add(victimLocation)
        helperTrails.values.forEach { trail ->
            trail.forEach { update ->
                update.location?.let { add(it) }
            }
        }
    }

    val minLat = allPoints.minOf { it.lat }
    val maxLat = allPoints.maxOf { it.lat }
    val minLng = allPoints.minOf { it.lng }
    val maxLng = allPoints.maxOf { it.lng }

    val latSpan = (maxLat - minLat).coerceAtLeast(0.0005)
    val lngSpan = (maxLng - minLng).coerceAtLeast(0.0005)
    val latPad = latSpan * 0.15
    val lngPad = lngSpan * 0.15

    fun project(sizeWidth: Float, sizeHeight: Float, location: LocationInfo): Offset {
        val xRatio = ((location.lng - (minLng - lngPad)) / (lngSpan + 2 * lngPad)).toFloat()
        val yRatio = ((location.lat - (minLat - latPad)) / (latSpan + 2 * latPad)).toFloat()
        return Offset(
            x = xRatio.coerceIn(0f, 1f) * sizeWidth,
            y = (1f - yRatio.coerceIn(0f, 1f)) * sizeHeight
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(190.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline,
                RoundedCornerShape(12.dp)
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(10.dp)) {
            helperTrails.forEach { (helperId, trail) ->
                val points = trail.mapNotNull { it.location?.let { location -> project(size.width, size.height, location) } }
                val baseColor = helperColor(helperId)
                for (i in 0 until points.lastIndex) {
                    drawLine(
                        color = baseColor.copy(alpha = 0.55f),
                        start = points[i],
                        end = points[i + 1],
                        strokeWidth = 4f
                    )
                }
                val latestPoint = points.lastOrNull()
                val latestStatus = trail.lastOrNull()?.status
                if (latestPoint != null) {
                    val markerColor = when (latestStatus) {
                        HelperStatus.CANNOT_CONTINUE -> Color(0xFF9CA3AF)
                        HelperStatus.REACHED -> SafeGreen
                        HelperStatus.EN_ROUTE -> WarnAmber
                        else -> baseColor
                    }
                    drawCircle(color = markerColor, radius = 8f, center = latestPoint)
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.15f),
                        radius = 12f,
                        center = latestPoint,
                        style = Stroke(width = 2f)
                    )
                }
            }

            if (victimLocation != null) {
                val victimPoint = project(size.width, size.height, victimLocation)
                drawCircle(color = SosRed, radius = 10f, center = victimPoint)
                drawCircle(color = Color.White, radius = 4f, center = victimPoint)
            }
        }
    }

    Spacer(Modifier.height(6.dp))
    Text(
        text = "Red: victim location • Colored trails: helper movement",
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
        fontSize = 11.sp
    )
}

// ── Extension helpers ─────────────────────────────────────────────────────────

@Composable
private fun StateDetailRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            label,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(1.dp))
        Text(
            value,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Start
        )
    }
}

fun MeshState.title(): String = when (this) {
    is MeshState.Idle -> "Listening"
    is MeshState.Originator -> "SOS Sent"
    is MeshState.Relay -> "Relaying SOS"
    is MeshState.Uploading -> "Uploading…"
    is MeshState.AwaitingAck -> "Awaiting Confirmation"
    is MeshState.Confirmed -> "SOS Confirmed ✓"
    is MeshState.Error -> "Error"
}

fun MeshState.subtitle(): String = when (this) {
    is MeshState.Idle -> ""
    is MeshState.Originator -> "Relayed to $peersReached peer(s). Waiting for delivery confirmation."
    is MeshState.Relay -> "Hop ${packet.metadata.currentHops}/${packet.metadata.maxHops} — forwarding to peers."
    is MeshState.Uploading -> "Attempt $attemptNumber — uploading to emergency server."
    is MeshState.AwaitingAck -> "Alert ID: $alertId. Waiting for ACK from originating device."
    is MeshState.Confirmed -> "Responders notified. Alert ID: ${ack.alertId}"
    is MeshState.Error -> reason
}

@Composable
fun MeshState.titleColor(): Color = when (this) {
    is MeshState.Confirmed -> SafeGreen
    is MeshState.Error -> SosRed
    is MeshState.Originator, is MeshState.Relay -> SosRed
    is MeshState.Uploading, is MeshState.AwaitingAck -> WarnAmber
    else -> MaterialTheme.colorScheme.onBackground
}

private fun MeshState.receivedPacketForDisplay(): SosPacket? = when (this) {
    is MeshState.Relay -> packet
    is MeshState.Uploading -> packet
    is MeshState.AwaitingAck -> packet
    else -> null
}

private fun MeshState.victimPacketForDisplay(): SosPacket? = when (this) {
    is MeshState.Originator -> packet
    is MeshState.Confirmed -> packet
    else -> null
}

private fun MeshState.victimLocalHelpUpdates(): List<LocalHelpUpdate> = when (this) {
    is MeshState.Originator -> localHelpUpdates
    is MeshState.Confirmed -> localHelpUpdates
    else -> emptyList()
}

private fun List<LocalHelpUpdate>.latestByHelper(limit: Int = 4): List<LocalHelpUpdate> =
    groupBy { it.helperDeviceId }
        .values
        .mapNotNull { updates -> updates.maxByOrNull { it.timestamp } }
        .sortedByDescending { it.timestamp }
        .take(limit)

private fun SosPacket.locationSummary(): String =
    incident.location?.let {
        val coords = String.format(Locale.US, "%.5f, %.5f", it.lat, it.lng)
        if (it.address.isNotBlank()) "$coords (${it.address})" else coords
    } ?: "Not available"

private fun String.shortDeviceId(): String = if (length <= 8) this else take(8) + "..."

private fun HelperStatus.displayName(): String = when (this) {
    HelperStatus.ACCEPTED -> "Accepted"
    HelperStatus.EN_ROUTE -> "En route"
    HelperStatus.REACHED -> "Reached"
    HelperStatus.CANNOT_CONTINUE -> "Cannot continue"
}

private val helperPalette = listOf(
    Color(0xFF22C55E),
    Color(0xFF38BDF8),
    Color(0xFFF59E0B),
    Color(0xFFFB7185),
    Color(0xFFA78BFA),
    Color(0xFF2DD4BF)
)

private fun helperColor(helperId: String): Color =
    helperPalette[helperId.hashCode().absoluteValue % helperPalette.size]

fun IncidentCategory.displayName(): String = when (this) {
    IncidentCategory.MEDICAL -> "Medical Emergency"
    IncidentCategory.FIRE -> "Fire"
    IncidentCategory.VIOLENCE -> "Violence / Threat"
    IncidentCategory.NATURAL_DISASTER -> "Natural Disaster"
    IncidentCategory.OTHER -> "Other Emergency"
}
