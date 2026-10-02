package com.meshsos.presentation.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshsos.data.db.entity.MeshEventEntity
import com.meshsos.domain.model.MeshEventType
import com.meshsos.presentation.theme.MeshTeal
import com.meshsos.presentation.theme.SafeGreen
import com.meshsos.presentation.theme.SosRed
import com.meshsos.presentation.theme.SubtleGray
import com.meshsos.presentation.theme.SurfaceVariant
import com.meshsos.presentation.theme.WarnAmber
import com.meshsos.presentation.viewmodels.MeshViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val TERMINAL_BG = Color(0xFF0D1117)
private val TERMINAL_BORDER = Color(0xFF30363D)
private val TERMINAL_HEADER = Color(0xFF161B22)
private val TIMESTAMP_COLOR = Color(0xFF6E7681)
private val BRACKET_COLOR = Color(0xFF8B949E)

// Pre-defined category groups for filter chips
private enum class LogCategory(val label: String, val types: Set<MeshEventType>, val color: Color) {
    ALL("All", MeshEventType.entries.toSet(), MeshTeal),
    SOS("SOS", setOf(MeshEventType.SOS_SENT, MeshEventType.SOS_RECEIVED), SosRed),
    NETWORK(
        "Network",
        setOf(MeshEventType.PEER_CONNECTED, MeshEventType.PEER_DISCONNECTED, MeshEventType.TRANSPORT_SWITCHED),
        SafeGreen
    ),
    RELAY(
        "Relay",
        setOf(MeshEventType.PACKET_RELAYED, MeshEventType.PACKET_UPLOADED, MeshEventType.ACK_RECEIVED),
        WarnAmber
    ),
    ERRORS(
        "Errors",
        setOf(MeshEventType.ERROR, MeshEventType.TTL_EXPIRED, MeshEventType.HOP_LIMIT_REACHED, MeshEventType.DUPLICATE_DROPPED),
        SosRed
    );
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DebugConsoleScreen(viewModel: MeshViewModel) {
    val allEvents by viewModel.allEvents.collectAsState()
    val peerCount by viewModel.peerCount.collectAsState()
    val transportName by viewModel.activeTransportName.collectAsState()
    val meshState by viewModel.meshState.collectAsState()
    val listState = rememberLazyListState()

    var selectedCategory by remember { mutableStateOf(LogCategory.ALL) }
    var autoScroll by remember { mutableStateOf(true) }

    val filteredEvents = remember(allEvents, selectedCategory) {
        if (selectedCategory == LogCategory.ALL) allEvents
        else allEvents.filter { event ->
            val type = runCatching { MeshEventType.valueOf(event.eventType) }.getOrNull()
            type != null && type in selectedCategory.types
        }
    }

    // Auto-scroll to top on new events
    LaunchedEffect(filteredEvents.size, autoScroll) {
        if (autoScroll && filteredEvents.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TERMINAL_BG)
    ) {
        // ── Terminal header bar ─────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(TERMINAL_HEADER)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Traffic light dots
                Box(Modifier.size(10.dp).clip(CircleShape).background(SosRed))
                Spacer(Modifier.width(6.dp))
                Box(Modifier.size(10.dp).clip(CircleShape).background(WarnAmber))
                Spacer(Modifier.width(6.dp))
                Box(Modifier.size(10.dp).clip(CircleShape).background(SafeGreen))
                Spacer(Modifier.width(12.dp))
                Text(
                    "Debug Console",
                    color = BRACKET_COLOR,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Event count
                Text(
                    "${filteredEvents.size} events",
                    color = TIMESTAMP_COLOR,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = { viewModel.clearLogs() },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.Delete, "Clear logs", tint = BRACKET_COLOR, modifier = Modifier.size(16.dp))
                }
                IconButton(
                    onClick = { autoScroll = !autoScroll },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        "Auto-scroll",
                        tint = if (autoScroll) SafeGreen else BRACKET_COLOR,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // ── Live status strip ──────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0A0E14))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val statusColor by animateColorAsState(
                    if (peerCount > 0) SafeGreen else WarnAmber,
                    label = "statusDot"
                )
                Box(Modifier.size(6.dp).clip(CircleShape).background(statusColor))
                Spacer(Modifier.width(6.dp))
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = BRACKET_COLOR)) { append("transport") }
                        withStyle(SpanStyle(color = Color(0xFF79C0FF))) {
                            append("=${transportName.lowercase().replace(" ", "")}")
                        }
                        withStyle(SpanStyle(color = BRACKET_COLOR)) { append(" peers") }
                        withStyle(SpanStyle(color = if (peerCount > 0) SafeGreen else SosRed)) {
                            append("=$peerCount")
                        }
                    },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
            }
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = BRACKET_COLOR)) { append("state") }
                    withStyle(SpanStyle(color = meshState.stateColor())) {
                        append("=${meshState::class.simpleName?.lowercase()}")
                    }
                },
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            )
        }

        HorizontalDivider(color = TERMINAL_BORDER, thickness = 1.dp)

        // ── Filter chips ───────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            LogCategory.entries.forEach { cat ->
                val isSelected = cat == selectedCategory
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedCategory = cat },
                    label = {
                        val count = if (cat == LogCategory.ALL) allEvents.size
                        else allEvents.count { event ->
                            val t = runCatching { MeshEventType.valueOf(event.eventType) }.getOrNull()
                            t != null && t in cat.types
                        }
                        Text(
                            "${cat.label} ($count)",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (isSelected) Color.Black else cat.color
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color.Transparent,
                        selectedContainerColor = cat.color,
                        labelColor = cat.color,
                        selectedLabelColor = Color.Black
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = cat.color.copy(alpha = 0.3f),
                        selectedBorderColor = cat.color,
                        enabled = true,
                        selected = isSelected
                    ),
                    shape = RoundedCornerShape(6.dp)
                )
            }
        }

        HorizontalDivider(color = TERMINAL_BORDER, thickness = 1.dp)

        // ── Log entries ────────────────────────────────────────────────────
        if (filteredEvents.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = BRACKET_COLOR)) {
                            append("$ ")
                        }
                        withStyle(SpanStyle(color = TIMESTAMP_COLOR)) {
                            append("waiting for events...\n\n")
                        }
                        withStyle(SpanStyle(color = TIMESTAMP_COLOR)) {
                            append("Start the service and send an SOS\n")
                            append("or come in range of another device\n")
                            append("to see internal debug logs here.")
                        }
                    },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                items(filteredEvents, key = { it.rowId }) { event ->
                    DebugLogRow(event)
                }
            }
        }
    }
}

@Composable
private fun DebugLogRow(event: MeshEventEntity) {
    val type = runCatching { MeshEventType.valueOf(event.eventType) }.getOrNull()
    val typeColor = type?.debugColor() ?: BRACKET_COLOR
    val typeTag = type?.debugTag() ?: event.eventType

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Timestamp
        Text(
            event.timestamp.toDebugTimeString(),
            color = TIMESTAMP_COLOR,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(62.dp)
        )

        // Color indicator line
        Box(
            Modifier
                .padding(top = 3.dp, end = 6.dp)
                .size(width = 2.dp, height = 12.dp)
                .background(typeColor, RoundedCornerShape(1.dp))
        )

        // Content
        Column(modifier = Modifier.weight(1f)) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = typeColor, fontWeight = FontWeight.Bold)) {
                        append("[$typeTag]")
                    }
                    append(" ")
                    withStyle(SpanStyle(color = Color(0xFFE6EDF3))) {
                        append(event.message)
                    }
                },
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )

            // Metadata line for packet/device IDs
            if (event.packetId != null || event.deviceId != null) {
                Text(
                    buildAnnotatedString {
                        event.packetId?.let {
                            withStyle(SpanStyle(color = Color(0xFF79C0FF))) {
                                append("pkt:${it.take(8)}")
                            }
                        }
                        if (event.packetId != null && event.deviceId != null) {
                            withStyle(SpanStyle(color = BRACKET_COLOR)) { append(" · ") }
                        }
                        event.deviceId?.let {
                            withStyle(SpanStyle(color = Color(0xFFD2A8FF))) {
                                append("dev:${it.take(8)}")
                            }
                        }
                    },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp
                )
            }
        }
    }
}

// ── Type → terminal color ──────────────────────────────────────────────────────

private fun MeshEventType.debugColor(): Color = when (this) {
    MeshEventType.SOS_SENT -> Color(0xFFFF7B72)         // bright red
    MeshEventType.SOS_RECEIVED -> Color(0xFFFFA657)     // orange
    MeshEventType.PACKET_RELAYED -> Color(0xFFFFA657)   // orange
    MeshEventType.PACKET_UPLOADED -> Color(0xFF7EE787)   // green
    MeshEventType.ACK_RECEIVED -> Color(0xFF7EE787)      // green
    MeshEventType.PEER_CONNECTED -> Color(0xFF56D364)    // bright green
    MeshEventType.PEER_DISCONNECTED -> Color(0xFF8B949E) // gray
    MeshEventType.TRANSPORT_SWITCHED -> Color(0xFF79C0FF) // blue
    MeshEventType.DUPLICATE_DROPPED -> Color(0xFF6E7681) // dim gray
    MeshEventType.TTL_EXPIRED -> Color(0xFF6E7681)       // dim gray
    MeshEventType.HOP_LIMIT_REACHED -> Color(0xFF6E7681) // dim gray
    MeshEventType.ERROR -> Color(0xFFF85149)             // error red
}

private fun MeshEventType.debugTag(): String = when (this) {
    MeshEventType.SOS_SENT -> "SOS:SEND"
    MeshEventType.SOS_RECEIVED -> "SOS:RECV"
    MeshEventType.PACKET_RELAYED -> "RELAY"
    MeshEventType.PACKET_UPLOADED -> "UPLOAD"
    MeshEventType.ACK_RECEIVED -> "ACK"
    MeshEventType.PEER_CONNECTED -> "NET:CONN"
    MeshEventType.PEER_DISCONNECTED -> "NET:DISC"
    MeshEventType.TRANSPORT_SWITCHED -> "TRANSPORT"
    MeshEventType.DUPLICATE_DROPPED -> "DEDUP"
    MeshEventType.TTL_EXPIRED -> "TTL:EXP"
    MeshEventType.HOP_LIMIT_REACHED -> "HOP:MAX"
    MeshEventType.ERROR -> "ERROR"
}

// Event timestamps are epoch seconds, so sub-second precision is not available.
private fun Long.toDebugTimeString(): String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(this * 1000))
