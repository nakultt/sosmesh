package com.meshsos.presentation.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshsos.domain.model.IncidentCategory
import com.meshsos.domain.model.Severity
import com.meshsos.domain.statemachine.MeshState
import com.meshsos.presentation.theme.SafeGreen
import com.meshsos.presentation.theme.SosRed
import com.meshsos.presentation.theme.SosRedDark
import com.meshsos.presentation.theme.SubtleGray
import com.meshsos.presentation.theme.WarnAmber
import com.meshsos.presentation.viewmodels.MeshViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SosScreen(viewModel: MeshViewModel) {
    val meshState by viewModel.meshState.collectAsState()
    val peerCount by viewModel.peerCount.collectAsState()
    val isSending by viewModel.isSending.collectAsState()
    val sendError by viewModel.sendError.collectAsState()
    val transportName by viewModel.activeTransportName.collectAsState()
    val pendingCount by viewModel.pendingPacketCount.collectAsState()

    var selectedCategory by remember { mutableStateOf(IncidentCategory.MEDICAL) }
    var message by remember { mutableStateOf("") }
    var showCategoryDropdown by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(sendError) {
        sendError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissError()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
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

            Spacer(Modifier.height(32.dp))

            // ── State feedback ────────────────────────────────────────────────
            StateCard(meshState = meshState, onReset = { viewModel.resetState() })

            Spacer(Modifier.height(32.dp))

            // ── Incident type ─────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Incident Type", color = SubtleGray, fontSize = 13.sp)
                Box {
                    TextButton(onClick = { showCategoryDropdown = true }) {
                        Text(
                            selectedCategory.displayName(),
                            color = WarnAmber,
                            fontWeight = FontWeight.Bold
                        )
                        Text("  ▼", color = WarnAmber)
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

            // ── Optional message ──────────────────────────────────────────────
            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Optional details (e.g. 2nd floor, blue car)…", color = SubtleGray) },
                maxLines = 3,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(Modifier.weight(1f))

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
                color = SubtleGray,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
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
            .clip(CircleShape)
            .background(buttonColor)
            .border(4.dp, if (isActive) Color.White else SosRedDark, CircleShape)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        isHolding = true
                        progress = 0f
                        var held = false
                        // Tick progress over 2 seconds
                        scope.launch {
                            repeat(40) {
                                if (!isHolding) return@repeat
                                progress = (it + 1) / 40f
                                delay(50)
                            }
                            if (isHolding) {
                                held = true
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Transport mode
            val isNearby = transportName.contains("Nearby")
            Column {
                Text(
                    if (isNearby) "WiFi Direct + BLE" else "Pure BLE",
                    color = if (isNearby) SafeGreen else WarnAmber,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "$peerCount peer${if (peerCount != 1) "s" else ""} nearby",
                    color = SubtleGray,
                    fontSize = 11.sp
                )
            }

            // Center: pending queue
            if (pendingCount > 0) {
                Text(
                    "$pendingCount queued",
                    color = WarnAmber,
                    fontSize = 11.sp
                )
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
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text("battery", color = SubtleGray, fontSize = 11.sp)
            }
        }
    }
}

// ── State feedback card ───────────────────────────────────────────────────────

@Composable
fun StateCard(meshState: MeshState, onReset: () -> Unit) {
    AnimatedVisibility(visible = meshState !is MeshState.Idle) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = when (meshState) {
                    is MeshState.Confirmed -> SafeGreen.copy(alpha = 0.15f)
                    is MeshState.Error -> SosRed.copy(alpha = 0.15f)
                    is MeshState.Uploading, is MeshState.AwaitingAck -> WarnAmber.copy(alpha = 0.1f)
                    else -> MaterialTheme.colorScheme.surface
                }
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = meshState.title(),
                    fontWeight = FontWeight.Bold,
                    color = meshState.titleColor(),
                    fontSize = 15.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = meshState.subtitle(),
                    color = SubtleGray,
                    fontSize = 13.sp
                )
                if (meshState is MeshState.Confirmed || meshState is MeshState.Error) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onReset) {
                        Text("Reset", color = WarnAmber)
                    }
                }
            }
        }
    }
}

// ── Extension helpers ─────────────────────────────────────────────────────────

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
    else -> Color.White
}

fun IncidentCategory.displayName(): String = when (this) {
    IncidentCategory.MEDICAL -> "Medical Emergency"
    IncidentCategory.FIRE -> "Fire"
    IncidentCategory.VIOLENCE -> "Violence / Threat"
    IncidentCategory.NATURAL_DISASTER -> "Natural Disaster"
    IncidentCategory.OTHER -> "Other Emergency"
}
