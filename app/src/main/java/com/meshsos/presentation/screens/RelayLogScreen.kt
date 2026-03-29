package com.meshsos.presentation.screens

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshsos.data.db.entity.MeshEventEntity
import com.meshsos.domain.model.MeshEventType
import com.meshsos.presentation.theme.SafeGreen
import com.meshsos.presentation.theme.SosRed
import com.meshsos.presentation.theme.SubtleGray
import com.meshsos.presentation.theme.WarnAmber
import com.meshsos.presentation.viewmodels.MeshViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RelayLogScreen(viewModel: MeshViewModel) {
    val events by viewModel.recentEvents.collectAsState()
    val listState = rememberLazyListState()

    // Auto-scroll to top when new event arrives
    LaunchedEffect(events.size) {
        if (events.isNotEmpty()) listState.animateScrollToItem(0)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Mesh Event Log",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "${events.size} events",
                color = SubtleGray,
                fontSize = 13.sp
            )
        }

        Text(
            "Device ID: ${viewModel.localDeviceId}",
            color = SubtleGray,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )

        Spacer(Modifier.height(12.dp))

        if (events.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No mesh events yet.\nSend an SOS or come in range of other devices.",
                    color = SubtleGray, fontSize = 14.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(events, key = { it.rowId }) { event ->
                    EventRow(event)
                }
            }
        }
    }
}

@Composable
fun EventRow(event: MeshEventEntity) {
    val type = runCatching { MeshEventType.valueOf(event.eventType) }.getOrNull()
    val dotColor = type?.dotColor() ?: SubtleGray

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Color dot
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        type?.label() ?: event.eventType,
                        color = dotColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        event.timestamp.toTimeString(),
                        color = SubtleGray,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Text(
                    event.message,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 13.sp
                )
                event.packetId?.let {
                    Text(
                        "pkt: ${it.take(8)}…",
                        color = SubtleGray,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

fun MeshEventType.dotColor(): Color = when (this) {
    MeshEventType.SOS_SENT -> SosRed
    MeshEventType.SOS_RECEIVED -> WarnAmber
    MeshEventType.PACKET_RELAYED -> WarnAmber
    MeshEventType.PACKET_UPLOADED -> SafeGreen
    MeshEventType.ACK_RECEIVED -> SafeGreen
    MeshEventType.PEER_CONNECTED -> SafeGreen
    MeshEventType.PEER_DISCONNECTED -> SubtleGray
    MeshEventType.TRANSPORT_SWITCHED -> Color(0xFF42A5F5)
    MeshEventType.DUPLICATE_DROPPED -> SubtleGray
    MeshEventType.TTL_EXPIRED -> SubtleGray
    MeshEventType.HOP_LIMIT_REACHED -> SubtleGray
    MeshEventType.ERROR -> SosRed
}

fun MeshEventType.label(): String = when (this) {
    MeshEventType.SOS_SENT -> "SOS Sent"
    MeshEventType.SOS_RECEIVED -> "SOS Received"
    MeshEventType.PACKET_RELAYED -> "Relayed"
    MeshEventType.PACKET_UPLOADED -> "Uploaded"
    MeshEventType.ACK_RECEIVED -> "ACK"
    MeshEventType.PEER_CONNECTED -> "Peer Connected"
    MeshEventType.PEER_DISCONNECTED -> "Peer Disconnected"
    MeshEventType.TRANSPORT_SWITCHED -> "Transport Switched"
    MeshEventType.DUPLICATE_DROPPED -> "Duplicate"
    MeshEventType.TTL_EXPIRED -> "TTL Expired"
    MeshEventType.HOP_LIMIT_REACHED -> "Hop Limit"
    MeshEventType.ERROR -> "Error"
}

private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
fun Long.toTimeString(): String = timeFormat.format(Date(this * 1000))
