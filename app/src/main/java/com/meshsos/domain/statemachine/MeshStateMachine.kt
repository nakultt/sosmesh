package com.meshsos.domain.statemachine

import android.util.Log
import com.meshsos.domain.model.MeshEvent as MeshLogEvent
import com.meshsos.domain.model.MeshEventType
import com.meshsos.domain.model.SosPacket
import com.meshsos.domain.service.DeduplicationService
import com.meshsos.domain.usecase.RelayPacketUseCase
import com.meshsos.domain.usecase.UploadPacketUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

private const val TAG = "MeshStateMachine"

@Singleton
class MeshStateMachine @Inject constructor(
    private val deduplicationService: DeduplicationService,
    private val relayPacketUseCase: RelayPacketUseCase,
    private val uploadPacketUseCase: UploadPacketUseCase,
    @Named("deviceId") private val deviceId: String
) {
    private val _state = MutableStateFlow<MeshState>(MeshState.Idle)
    val state: StateFlow<MeshState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<MeshLogEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<MeshLogEvent> = _events.asSharedFlow()

    // ── Public event emission ─────────────────────────────────────────────────

    fun dispatch(event: MeshEvent.UserTriggeredSos) {
        _state.value = MeshState.Originator(event.packet)
        emitLog(MeshEventType.SOS_SENT, "SOS sent: ${event.packet.id}", event.packet.id)
    }

    fun onPacketReceived(scope: CoroutineScope, packet: SosPacket, fromDevice: String) {
        scope.launch {
            processIncomingPacket(packet, fromDevice)
        }
    }

    fun onAckReceived(ack: com.meshsos.domain.model.AckPacket) {
        val current = _state.value
        if (current is MeshState.Originator && current.packet.id == ack.originalPacketId) {
            _state.value = MeshState.Confirmed(ack)
            emitLog(MeshEventType.ACK_RECEIVED, "ACK confirmed: alertId=${ack.alertId}", ack.originalPacketId)
        }
    }

    fun onPeerConnected(deviceId: String) {
        emitLog(MeshEventType.PEER_CONNECTED, "Peer connected: $deviceId", deviceId = deviceId)
        // If we are an originator or relay with a queued packet, try to forward immediately
        val current = _state.value
        if (current is MeshState.Originator) {
            scope_forward(current.packet)
        }
    }

    fun onPeerDisconnected(deviceId: String) {
        emitLog(MeshEventType.PEER_DISCONNECTED, "Peer disconnected: $deviceId", deviceId = deviceId)
    }

    fun updatePeersReached(count: Int) {
        val current = _state.value
        if (current is MeshState.Originator) {
            _state.value = current.copy(peersReached = count)
        }
    }

    fun reset() {
        _state.value = MeshState.Idle
    }

    // ── Internal processing ───────────────────────────────────────────────────

    private suspend fun processIncomingPacket(packet: SosPacket, fromDevice: String) {
        Log.d(TAG, "processIncomingPacket: id=${packet.id} from=$fromDevice")

        // 1. Deduplication
        if (deduplicationService.isDuplicate(packet.id)) {
            Log.d(TAG, "Duplicate dropped: ${packet.id}")
            emitLog(MeshEventType.DUPLICATE_DROPPED, "Duplicate packet dropped", packet.id)
            return
        }

        // 2. TTL check
        if (packet.isExpired()) {
            Log.d(TAG, "TTL expired: ${packet.id}")
            emitLog(MeshEventType.TTL_EXPIRED, "Packet TTL expired", packet.id)
            return
        }

        // 3. Hop limit check
        if (packet.isHopLimitReached()) {
            Log.d(TAG, "Hop limit reached: ${packet.id}")
            emitLog(MeshEventType.HOP_LIMIT_REACHED, "Hop limit reached", packet.id)
            return
        }

        // 4. Update state to relay
        val incrementedPacket = packet.incrementHop(deviceId)
        _state.value = MeshState.Relay(incrementedPacket, fromDevice)
        val incident = incrementedPacket.incident
        val messagePreview = incident.message.ifBlank { "No message" }
        val locationSummary = incident.location?.let {
            String.format(
                Locale.US,
                "%.6f, %.6f (+/- %.1fm)",
                it.lat,
                it.lng,
                it.accuracy
            )
        } ?: "Location unavailable"
        emitLog(
            MeshEventType.SOS_RECEIVED,
            "SOS received from $fromDevice | type=${incrementedPacket.type.name} | emergency=${incident.category.name}/${incident.severity.name} | message=$messagePreview | location=$locationSummary | hop=${incrementedPacket.metadata.currentHops}",
            packet.id
        )

        // 5. Check internet — try upload first, relay in parallel
        val hasInternet = uploadPacketUseCase.hasInternet()
        if (hasInternet) {
            _state.value = MeshState.Uploading(
                packet = incrementedPacket,
                receivedFromDevice = fromDevice
            )
            val result = uploadPacketUseCase.upload(incrementedPacket)
            result.fold(
                onSuccess = { response ->
                    val uploaded = incrementedPacket.markUploaded()
                    _state.value = MeshState.AwaitingAck(
                        originalPacketId = uploaded.id,
                        alertId = response.alertId,
                        packet = uploaded,
                        receivedFromDevice = fromDevice
                    )
                    emitLog(MeshEventType.PACKET_UPLOADED, "Uploaded. alertId=${response.alertId}", uploaded.id)
                },
                onFailure = { error ->
                    Log.e(TAG, "Upload failed: ${error.message}")
                    emitLog(MeshEventType.ERROR, "Upload failed: ${error.message}", incrementedPacket.id)
                    // Fall through to relay even if upload failed
                    relayForward(incrementedPacket)
                }
            )
        } else {
            relayForward(incrementedPacket)
        }
    }

    private fun relayForward(packet: SosPacket) {
        scope_forward(packet)
        emitLog(MeshEventType.PACKET_RELAYED, "Relaying forward hop=${packet.metadata.currentHops}", packet.id)
    }

    // Called externally from transport layer
    private var forwardCallback: ((SosPacket) -> Unit)? = null

    fun setForwardCallback(cb: (SosPacket) -> Unit) {
        forwardCallback = cb
    }

    private fun scope_forward(packet: SosPacket) {
        forwardCallback?.invoke(packet)
    }

    // ── Event log ─────────────────────────────────────────────────────────────

    private val _logEvents = MutableSharedFlow<MeshLogEvent>(extraBufferCapacity = 64)
    val logEvents: SharedFlow<MeshLogEvent> = _logEvents.asSharedFlow()

    private fun emitLog(
        type: MeshEventType,
        message: String,
        packetId: String? = null,
        deviceId: String? = null
    ) {
        val event = MeshLogEvent(
            type = type,
            message = message,
            packetId = packetId,
            deviceId = deviceId
        )
        _logEvents.tryEmit(event)
    }
}
