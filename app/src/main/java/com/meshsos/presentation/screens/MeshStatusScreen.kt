package com.meshsos.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshsos.domain.model.SosPacket
import com.meshsos.domain.statemachine.MeshState
import com.meshsos.presentation.theme.MeshTeal
import com.meshsos.presentation.theme.SafeGreen
import com.meshsos.presentation.theme.SosRed
import com.meshsos.presentation.theme.SubtleGray
import com.meshsos.presentation.theme.WarnAmber
import com.meshsos.presentation.viewmodels.MeshViewModel
import java.util.Locale

@Composable
fun MeshStatusScreen(viewModel: MeshViewModel) {
    val meshState by viewModel.meshState.collectAsState()
    val peerCount by viewModel.peerCount.collectAsState()
    val transportName by viewModel.activeTransportName.collectAsState()
    val pendingCount by viewModel.pendingPacketCount.collectAsState()
    val receivedSosDetails = meshState.receivedSosDetails()

    var autoRelayEnabled by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            "Mesh Status",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(16.dp))

        // ── Transport info ────────────────────────────────────────────────────
        SectionCard(title = "Active Transport") {
            InfoRow(
                label = "Mode",
                value = if (transportName.contains("Nearby")) "WiFi Direct + BLE" else "Pure BLE",
                valueColor = if (transportName.contains("Nearby")) SafeGreen else WarnAmber
            )
            InfoRow(label = "Peers Connected", value = "$peerCount", valueColor = MaterialTheme.colorScheme.onBackground)
            InfoRow(
                label = "Pending Queue",
                value = if (pendingCount == 0) "Clear" else "$pendingCount packet(s)",
                valueColor = if (pendingCount == 0) SafeGreen else WarnAmber
            )
        }

        Spacer(Modifier.height(12.dp))

        // ── State machine ─────────────────────────────────────────────────────
        SectionCard(title = "State Machine") {
            InfoRow(
                label = "Current State",
                value = meshState::class.simpleName ?: "Unknown",
                valueColor = meshState.stateColor()
            )
            when (meshState) {
                is MeshState.Relay -> {
                    val relay = meshState as MeshState.Relay
                    InfoRow("Packet ID", relay.packet.id.shortId(), SubtleGray)
                    InfoRow("Hops", "${relay.packet.metadata.currentHops}/${relay.packet.metadata.maxHops}", MaterialTheme.colorScheme.onBackground)
                    InfoRow("From Device", relay.receivedFromDevice.shortId(), SubtleGray)
                }
                is MeshState.Uploading -> {
                    val uploading = meshState as MeshState.Uploading
                    InfoRow("Packet ID", uploading.packet.id.shortId(), SubtleGray)
                    InfoRow("Hops", "${uploading.packet.metadata.currentHops}/${uploading.packet.metadata.maxHops}", MaterialTheme.colorScheme.onBackground)
                    uploading.receivedFromDevice?.let { InfoRow("From Device", it.shortId(), SubtleGray) }
                }
                is MeshState.AwaitingAck -> {
                    val awaiting = meshState as MeshState.AwaitingAck
                    InfoRow("Alert ID", awaiting.alertId, SafeGreen)
                    awaiting.packet?.let {
                        InfoRow("Packet ID", it.id.shortId(), SubtleGray)
                        InfoRow("Hops", "${it.metadata.currentHops}/${it.metadata.maxHops}", MaterialTheme.colorScheme.onBackground)
                    }
                    awaiting.receivedFromDevice?.let { InfoRow("From Device", it.shortId(), SubtleGray) }
                }
                is MeshState.Originator -> {
                    val orig = meshState as MeshState.Originator
                    InfoRow("Packet ID", orig.packet.id.shortId(), SubtleGray)
                    InfoRow("Peers Reached", "${orig.peersReached}", SafeGreen)
                }
                is MeshState.Confirmed -> {
                    val conf = meshState as MeshState.Confirmed
                    InfoRow("Alert ID", conf.ack.alertId, SafeGreen)
                    InfoRow("Responders", "${conf.ack.respondersNotified}", SafeGreen)
                }
                else -> {}
            }
        }

        if (receivedSosDetails != null) {
            Spacer(Modifier.height(12.dp))
            SectionCard(title = "Received SOS Data") {
                ReceivedSosDataContent(details = receivedSosDetails)
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── Device identity ───────────────────────────────────────────────────
        SectionCard(title = "Device Identity") {
            Text(
                text = viewModel.localDeviceId,
                color = MeshTeal,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Text("This ID appears in relay hop trails", color = SubtleGray, fontSize = 12.sp)
        }

        Spacer(Modifier.height(12.dp))

        // ── Settings ──────────────────────────────────────────────────────────
        SectionCard(title = "Settings") {
            SettingsRow(
                label = "Auto Relay (Good Samaritan mode)",
                description = "Automatically relay SOS packets from other devices",
                checked = autoRelayEnabled,
                onCheckedChange = { autoRelayEnabled = it }
            )
        }

        Spacer(Modifier.height(12.dp))

        // ── Service controls ──────────────────────────────────────────────────
        SectionCard(title = "Service Control") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { viewModel.startService() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = SafeGreen)
                ) {
                    Text("Start Service", color = Color.Black, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = { viewModel.stopService() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = SosRed)
                ) {
                    Text("Stop Service", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ── Reusable components ───────────────────────────────────────────────────────

@Composable
fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, color = SubtleGray, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
fun InfoRow(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = SubtleGray, fontSize = 13.sp)
        Text(value, color = valueColor, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            fontFamily = if (label.contains("ID") || label.contains("Packet")) FontFamily.Monospace else FontFamily.Default)
    }
}

@Composable
fun SettingsRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, color = MaterialTheme.colorScheme.onBackground, fontSize = 13.sp)
            Text(description, color = SubtleGray, fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = SafeGreen
            )
        )
    }
}

private data class ReceivedSosDetails(
    val packet: SosPacket,
    val receivedFromDevice: String?
)

private fun MeshState.receivedSosDetails(): ReceivedSosDetails? = when (this) {
    is MeshState.Relay -> ReceivedSosDetails(packet = packet, receivedFromDevice = receivedFromDevice)
    is MeshState.Uploading -> ReceivedSosDetails(packet = packet, receivedFromDevice = receivedFromDevice)
    is MeshState.AwaitingAck -> packet?.let { ReceivedSosDetails(packet = it, receivedFromDevice = receivedFromDevice) }
    else -> null
}

@Composable
private fun ReceivedSosDataContent(details: ReceivedSosDetails) {
    val packet = details.packet
    val incident = packet.incident
    val location = incident.location

    InfoRow("Type", packet.type.name, WarnAmber)
    InfoRow("Emergency", "${incident.category.displayName()} / ${incident.severity.name}", MaterialTheme.colorScheme.onBackground)
    InfoRow("Sender ID", packet.senderId.shortId(), SubtleGray)
    details.receivedFromDevice?.let { InfoRow("Received From", it.shortId(), SubtleGray) }

    Spacer(Modifier.height(4.dp))
    Text("Message", color = SubtleGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    Text(
        text = incident.message.ifBlank { "No additional message shared." },
        color = MaterialTheme.colorScheme.onBackground,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium
    )

    Spacer(Modifier.height(8.dp))
    if (location != null) {
        InfoRow("Latitude", String.format(Locale.US, "%.6f", location.lat), MaterialTheme.colorScheme.onBackground)
        InfoRow("Longitude", String.format(Locale.US, "%.6f", location.lng), MaterialTheme.colorScheme.onBackground)
        InfoRow("Accuracy", String.format(Locale.US, "%.1f m", location.accuracy), MaterialTheme.colorScheme.onBackground)
        if (location.address.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text("Address", color = SubtleGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(
                text = location.address,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    } else {
        Text("Location: Not available", color = SubtleGray, fontSize = 12.sp)
    }
}

private fun String.shortId(): String = if (length <= 8) this else take(8) + "..."

fun MeshState.stateColor(): Color = when (this) {
    is MeshState.Idle -> SubtleGray
    is MeshState.Originator -> SosRed
    is MeshState.Relay -> WarnAmber
    is MeshState.Uploading -> WarnAmber
    is MeshState.AwaitingAck -> WarnAmber
    is MeshState.Confirmed -> SafeGreen
    is MeshState.Error -> SosRed
}
