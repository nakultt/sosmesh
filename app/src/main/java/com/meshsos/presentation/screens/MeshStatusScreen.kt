package com.meshsos.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshsos.domain.model.HelperStatus
import com.meshsos.domain.model.SosPacket
import com.meshsos.domain.statemachine.MeshState
import com.meshsos.presentation.theme.SafeGreen
import com.meshsos.presentation.theme.SosRed
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
    val canSendLocalHelpUpdate = viewModel.canSendLocalHelpUpdate()
    val activeHelperStatus by viewModel.activeHelperStatus.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // ── Screen title ────────────────────────────────────────────────────
        Text(
            "Mesh Status",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Network state and active emergency details",
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            fontSize = 14.sp
        )
        Spacer(Modifier.height(24.dp))

        // ── Transport info ────────────────────────────────────────────────────
        SectionCard(title = "ACTIVE TRANSPORT") {
            InfoRow(
                label = "Mode",
                value = if (transportName.contains("Nearby")) "WiFi Direct + BLE" else "Pure BLE",
                valueColor = if (transportName.contains("Nearby")) SafeGreen else WarnAmber
            )
            InfoRow(
                label = "Peers Connected",
                value = "$peerCount",
                valueColor = MaterialTheme.colorScheme.onBackground
            )
            InfoRow(
                label = "Pending Queue",
                value = if (pendingCount == 0) "Clear" else "$pendingCount packet(s)",
                valueColor = if (pendingCount == 0) SafeGreen else WarnAmber
            )
        }

        Spacer(Modifier.height(16.dp))

        // ── State machine ─────────────────────────────────────────────────────
        SectionCard(title = "STATE MACHINE") {
            InfoRow(
                label = "Current State",
                value = meshState::class.simpleName ?: "Unknown",
                valueColor = meshState.stateColor()
            )
            when (meshState) {
                is MeshState.Relay -> {
                    val relay = meshState as MeshState.Relay
                    InfoRow("Packet ID", relay.packet.id.shortId(), MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                    InfoRow("Hops", "${relay.packet.metadata.currentHops}/${relay.packet.metadata.maxHops}", MaterialTheme.colorScheme.onBackground)
                    InfoRow("From Device", relay.receivedFromDevice.shortId(), MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                }
                is MeshState.Uploading -> {
                    val uploading = meshState as MeshState.Uploading
                    InfoRow("Packet ID", uploading.packet.id.shortId(), MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                    InfoRow("Hops", "${uploading.packet.metadata.currentHops}/${uploading.packet.metadata.maxHops}", MaterialTheme.colorScheme.onBackground)
                    uploading.receivedFromDevice?.let { InfoRow("From Device", it.shortId(), MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)) }
                }
                is MeshState.AwaitingAck -> {
                    val awaiting = meshState as MeshState.AwaitingAck
                    InfoRow("Alert ID", awaiting.alertId, SafeGreen)
                    awaiting.packet?.let {
                        InfoRow("Packet ID", it.id.shortId(), MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                        InfoRow("Hops", "${it.metadata.currentHops}/${it.metadata.maxHops}", MaterialTheme.colorScheme.onBackground)
                    }
                    awaiting.receivedFromDevice?.let { InfoRow("From Device", it.shortId(), MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)) }
                }
                is MeshState.Originator -> {
                    val orig = meshState as MeshState.Originator
                    InfoRow("Packet ID", orig.packet.id.shortId(), MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                    InfoRow("Peers Reached", "${orig.peersReached}", SafeGreen)
                    InfoRow("Server Upload", "Pending confirmation", WarnAmber)
                    InfoRow(
                        "Helpers En Route",
                        "${orig.localHelpUpdates.groupBy { it.helperDeviceId }.size}",
                        if (orig.localHelpUpdates.isNotEmpty()) SafeGreen else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
                is MeshState.Confirmed -> {
                    val conf = meshState as MeshState.Confirmed
                    InfoRow("Alert ID", conf.ack.alertId, SafeGreen)
                    InfoRow("Server Upload", "Confirmed", SafeGreen)
                    InfoRow("Responders", "${conf.ack.respondersNotified}", SafeGreen)
                    InfoRow(
                        "Helpers Reporting",
                        "${conf.localHelpUpdates.groupBy { it.helperDeviceId }.size}",
                        if (conf.localHelpUpdates.isNotEmpty()) SafeGreen else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
                else -> {}
            }
        }

        if (receivedSosDetails != null) {
            Spacer(Modifier.height(16.dp))
            SectionCard(title = "RECEIVED SOS DATA") {
                ReceivedSosDataContent(details = receivedSosDetails)
                if (canSendLocalHelpUpdate) {
                    Spacer(Modifier.height(12.dp))
                    HelperStatusActionGrid(
                        activeStatus = activeHelperStatus,
                        onStatusUpdate = { viewModel.sendHelperStatusUpdate(it) }
                    )
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

// ── Reusable components ───────────────────────────────────────────────────────

@Composable
fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                title,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun InfoRow(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            fontSize = 14.sp
        )
        Text(
            value,
            color = valueColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = if (label.contains("ID") || label.contains("Packet")) FontFamily.Monospace else FontFamily.Default
        )
    }
}

@Composable
private fun HelperStatusActionGrid(
    activeStatus: HelperStatus?,
    onStatusUpdate: (HelperStatus) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Helper Workflow",
            color = SafeGreen,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HelperStatusButton(
                text = "Accept",
                status = HelperStatus.ACCEPTED,
                activeStatus = activeStatus,
                onStatusUpdate = onStatusUpdate
            )
            HelperStatusButton(
                text = "En route",
                status = HelperStatus.EN_ROUTE,
                activeStatus = activeStatus,
                onStatusUpdate = onStatusUpdate
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HelperStatusButton(
                text = "Reached",
                status = HelperStatus.REACHED,
                activeStatus = activeStatus,
                onStatusUpdate = onStatusUpdate
            )
            HelperStatusButton(
                text = "Cannot continue",
                status = HelperStatus.CANNOT_CONTINUE,
                activeStatus = activeStatus,
                onStatusUpdate = onStatusUpdate
            )
        }
    }
}

@Composable
private fun RowScope.HelperStatusButton(
    text: String,
    status: HelperStatus,
    activeStatus: HelperStatus?,
    onStatusUpdate: (HelperStatus) -> Unit
) {
    val selected = activeStatus == status
    val tint = when (status) {
        HelperStatus.ACCEPTED -> SafeGreen
        HelperStatus.EN_ROUTE -> WarnAmber
        HelperStatus.REACHED -> SafeGreen
        HelperStatus.CANNOT_CONTINUE -> SosRed
    }
    TextButton(
        onClick = { onStatusUpdate(status) },
        modifier = Modifier.weight(1f),
        colors = ButtonDefaults.textButtonColors(
            containerColor = if (selected) tint.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (selected) tint else MaterialTheme.colorScheme.onBackground
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        Text(
            text,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 13.sp
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
    InfoRow(
        "Emergency",
        "${incident.category.displayName()} / ${incident.severity.name}",
        MaterialTheme.colorScheme.onBackground
    )
    InfoRow("Sender ID", packet.senderId.shortId(), MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
    details.receivedFromDevice?.let { InfoRow("Received From", it.shortId(), MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)) }

    Spacer(Modifier.height(8.dp))
    Text("Message", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(2.dp))
    Text(
        text = incident.message.ifBlank { "No additional message shared." },
        color = MaterialTheme.colorScheme.onBackground,
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal
    )

    Spacer(Modifier.height(12.dp))
    if (location != null) {
        InfoRow("Latitude", String.format(Locale.US, "%.6f", location.lat), MaterialTheme.colorScheme.onBackground)
        InfoRow("Longitude", String.format(Locale.US, "%.6f", location.lng), MaterialTheme.colorScheme.onBackground)
        InfoRow("Accuracy", String.format(Locale.US, "%.1f m", location.accuracy), MaterialTheme.colorScheme.onBackground)
        if (location.address.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text("Address", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text(
                text = location.address,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal
            )
        }
    } else {
        Text("Location: Not available", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontSize = 13.sp)
    }
}

private fun String.shortId(): String = if (length <= 8) this else take(8) + "..."

@Composable
fun MeshState.stateColor(): Color = when (this) {
    is MeshState.Idle -> MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
    is MeshState.Originator -> SosRed
    is MeshState.Relay -> WarnAmber
    is MeshState.Uploading -> WarnAmber
    is MeshState.AwaitingAck -> WarnAmber
    is MeshState.Confirmed -> SafeGreen
    is MeshState.Error -> SosRed
}
