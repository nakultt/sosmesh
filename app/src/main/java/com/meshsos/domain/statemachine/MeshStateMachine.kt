package com.meshsos.domain.statemachine

import android.util.Log
import com.meshsos.data.api.UploadResponse
import com.meshsos.data.settings.MeshSettings
import com.meshsos.domain.model.AckPacket
import com.meshsos.domain.model.HelperStatus
import com.meshsos.domain.model.MeshEventType
import com.meshsos.domain.model.SosPacket
import com.meshsos.domain.model.isLocalHelpUpdate
import com.meshsos.domain.service.DeduplicationService
import com.meshsos.domain.service.DeviceLocationProvider
import com.meshsos.domain.service.MeshEventLogger
import com.meshsos.domain.usecase.RelayPacketUseCase
import com.meshsos.domain.usecase.UploadPacketUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

private const val TAG = "MeshStateMachine"
private const val MAX_LOCAL_HELP_UPDATES = 120
private const val MAX_RECEIVED_ALERTS = 20
private const val ACK_DEDUP_PREFIX = "ack:"

/**
 * Core mesh logic.
 *
 * - Every packet this device originates or relays is kept in [activePackets] and re-sent
 *   whenever a new peer becomes reachable (store-carry-forward), until it expires.
 * - ACKs and helper updates are flooded through the mesh and deduplicated by ACK id, so they
 *   reach the originator even when the original route has changed.
 * - [state] drives the UI. The originator keeps its own SOS state even when other devices'
 *   SOS packets arrive; those are tracked in [receivedAlerts].
 */
@Singleton
class MeshStateMachine @Inject constructor(
    private val deduplicationService: DeduplicationService,
    private val deviceLocationProvider: DeviceLocationProvider,
    private val relayPacketUseCase: RelayPacketUseCase,
    private val uploadPacketUseCase: UploadPacketUseCase,
    private val eventLogger: MeshEventLogger,
    private val meshSettings: MeshSettings,
    @Named("deviceId") private val deviceId: String
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<MeshState>(MeshState.Idle)
    val state: StateFlow<MeshState> = _state.asStateFlow()

    private val _receivedAlerts = MutableStateFlow<List<ReceivedAlert>>(emptyList())
    val receivedAlerts: StateFlow<List<ReceivedAlert>> = _receivedAlerts.asStateFlow()

    /** Emits each newly received (non-duplicate) SOS from another device, for notifications. */
    private val _newAlerts = MutableSharedFlow<SosPacket>(extraBufferCapacity = 16)
    val newAlerts: SharedFlow<SosPacket> = _newAlerts.asSharedFlow()

    private val activePackets = ConcurrentHashMap<String, SosPacket>()
    private val ownPacketIds: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val reachedPeersByPacket = ConcurrentHashMap<String, MutableSet<String>>()

    // ── Originating ───────────────────────────────────────────────────────────

    /** Starts broadcasting a new SOS from this device and uploads it directly if online. */
    fun originate(packet: SosPacket) {
        deduplicationService.markSeen(packet.id)
        ownPacketIds += packet.id
        activePackets[packet.id] = packet
        _state.value = MeshState.Originator(packet)
        emitLog(MeshEventType.SOS_SENT, "SOS sent: ${packet.incident.category.name}", packet.id)

        scope.launch {
            val reached = forward(packet)
            if (reached.isEmpty()) {
                emitLog(
                    MeshEventType.PACKET_RELAYED,
                    "No peers in range yet; SOS will be sent as soon as a device connects",
                    packet.id
                )
            } else {
                emitLog(MeshEventType.PACKET_RELAYED, "SOS delivered to ${reached.size} peer(s)", packet.id)
            }

            if (uploadPacketUseCase.hasInternet()) {
                uploadPacketUseCase.upload(packet, packet.incident.location)
                    .onSuccess { response -> onUploadSucceeded(packet, response) }
                    .onFailure { error ->
                        emitLog(MeshEventType.ERROR, "Direct upload failed: ${error.message}; relying on mesh", packet.id)
                    }
            } else {
                Log.i(TAG, "No internet for direct upload, relying on mesh broadcast")
            }
        }
    }

    /** Stops this device's own SOS (cancel or dismiss after confirmation) and returns to Idle. */
    fun reset() {
        val current = _state.value
        val ownPacket = when (current) {
            is MeshState.Originator -> current.packet
            is MeshState.Confirmed -> current.packet
            else -> null
        }
        ownPacketIds.forEach { id ->
            activePackets.remove(id)
            reachedPeersByPacket.remove(id)
        }
        if (current is MeshState.Originator && ownPacket != null) {
            emitLog(MeshEventType.SOS_SENT, "SOS cancelled by user", ownPacket.id)
        }
        _state.value = MeshState.Idle
    }

    // ── Receiving packets ─────────────────────────────────────────────────────

    fun onPacketReceived(packet: SosPacket, fromDevice: String) {
        scope.launch {
            runCatching { processIncomingPacket(packet, fromDevice) }
                .onFailure { Log.e(TAG, "Failed processing packet ${packet.id}", it) }
        }
    }

    private suspend fun processIncomingPacket(packet: SosPacket, fromDevice: String) {
        // Our own SOS echoed back by a relay
        if (packet.id in ownPacketIds || packet.senderId == deviceId) {
            deduplicationService.markSeen(packet.id)
            return
        }
        // Rebroadcasts of packets we already handled are expected and silently ignored
        if (deduplicationService.isDuplicate(packet.id)) {
            Log.d(TAG, "Duplicate dropped: ${packet.id}")
            return
        }
        if (packet.isExpired()) {
            emitLog(MeshEventType.TTL_EXPIRED, "Packet TTL expired", packet.id, fromDevice)
            return
        }

        val canForward = !packet.isHopLimitReached()
        val relayLocation = deviceLocationProvider.getCurrentLocation()
        val hopPacket = packet.incrementHop(relayDeviceId = deviceId, relayLocation = relayLocation)

        addReceivedAlert(ReceivedAlert(hopPacket, fromDevice))
        _newAlerts.tryEmit(hopPacket)

        val incident = hopPacket.incident
        val locationSummary = incident.location?.let {
            String.format(Locale.US, "%.6f, %.6f (+/- %.1fm)", it.lat, it.lng, it.accuracy)
        } ?: "Location unavailable"
        emitLog(
            MeshEventType.SOS_RECEIVED,
            "SOS from ${packet.senderId} via $fromDevice | ${incident.category.name}/${incident.severity.name} | " +
                "message=${incident.message.ifBlank { "none" }} | location=$locationSummary | hop=${hopPacket.metadata.currentHops}",
            packet.id,
            fromDevice
        )

        // The originator keeps showing its own emergency; everyone else shows the new one.
        val showInUi = _state.value.let { it !is MeshState.Originator && it !is MeshState.Confirmed }
        if (showInUi) _state.value = MeshState.Relay(hopPacket, fromDevice)

        // 1. Forward through the mesh (store-and-forward keeps it for peers that appear later)
        when {
            !canForward -> emitLog(
                MeshEventType.HOP_LIMIT_REACHED,
                "Hop limit reached; not forwarding further",
                packet.id
            )
            !meshSettings.autoRelayEnabled.value -> emitLog(
                MeshEventType.PACKET_RELAYED,
                "Auto relay is off; packet not forwarded",
                packet.id
            )
            else -> {
                activePackets[packet.id] = hopPacket
                val reached = forward(hopPacket)
                emitLog(
                    MeshEventType.PACKET_RELAYED,
                    if (reached.isEmpty()) "Queued for relay (no other peers yet)"
                    else "Relayed to ${reached.size} peer(s), hop=${hopPacket.metadata.currentHops}",
                    packet.id
                )
            }
        }

        // 2. Upload if this device is online
        if (uploadPacketUseCase.hasInternet()) {
            if (showInUi) {
                _state.update { current ->
                    if (current is MeshState.Relay && current.packet.id == hopPacket.id) {
                        MeshState.Uploading(packet = hopPacket, receivedFromDevice = fromDevice)
                    } else current
                }
            }
            uploadPacketUseCase.upload(hopPacket, relayLocation)
                .onSuccess { response -> onUploadSucceeded(hopPacket, response) }
                .onFailure { error ->
                    emitLog(MeshEventType.ERROR, "Upload failed (queued for retry): ${error.message}", hopPacket.id)
                    _state.update { current ->
                        if (current is MeshState.Uploading && current.packet.id == hopPacket.id) {
                            MeshState.Relay(hopPacket, fromDevice)
                        } else current
                    }
                }
        }
    }

    /** Called after any successful server upload (direct, relayed, or from the retry queue). */
    fun onUploadSucceeded(packet: SosPacket, response: UploadResponse) {
        val alertId = response.alertId.orEmpty()
        val source = if (response.deduplicated) "already uploaded (deduped)" else "uploaded now"
        emitLog(MeshEventType.PACKET_UPLOADED, "Server $source. alertId=$alertId", packet.id)
        updateReceivedAlert(packet.id) { it.copy(alertId = alertId) }

        val serverAck = AckPacket(
            originalPacketId = packet.id,
            alertId = alertId,
            uploadedBy = deviceId,
            respondersNotified = response.respondersNotified,
            estimatedArrival = response.estimatedArrival
        )
        handleAck(serverAck, fromDevice = deviceId, isLocal = true)
    }

    // ── ACKs / helper updates ─────────────────────────────────────────────────

    fun onAckReceived(ack: AckPacket, fromDevice: String) {
        handleAck(ack, fromDevice, isLocal = false)
    }

    /** Sends a helper status update created on this device into the mesh. */
    suspend fun sendHelperUpdate(ack: AckPacket): Result<Set<String>> {
        deduplicationService.markSeen(ACK_DEDUP_PREFIX + ack.id)
        return relayPacketUseCase.relayAck(ack)
    }

    private fun handleAck(ack: AckPacket, fromDevice: String, isLocal: Boolean) {
        if (deduplicationService.isDuplicate(ACK_DEDUP_PREFIX + ack.id)) return

        if (ack.isLocalHelpUpdate()) {
            applyLocalHelpUpdate(ack)
        } else {
            applyServerAck(ack)
            updateReceivedAlert(ack.originalPacketId) { it.copy(alertId = ack.alertId) }
        }

        // Flood onward unless we are the final destination (the originator).
        val weAreOriginator = ack.originalPacketId in ownPacketIds
        if (!weAreOriginator && (isLocal || meshSettings.autoRelayEnabled.value)) {
            scope.launch {
                relayPacketUseCase.relayAck(ack).onSuccess { peers ->
                    if (!isLocal) {
                        Log.d(TAG, "Forwarded ACK ${ack.id.take(8)} from $fromDevice to ${peers.size} peer(s)")
                    }
                }
            }
        }
    }

    private fun applyLocalHelpUpdate(ack: AckPacket) {
        val update = ack.toLocalHelpUpdate()
        var handled = false
        _state.update { current ->
            when {
                current is MeshState.Originator && current.packet.id == ack.originalPacketId -> {
                    handled = true
                    current.copy(localHelpUpdates = mergeLocalHelpUpdates(current.localHelpUpdates, update))
                }
                current is MeshState.Confirmed && current.ack.originalPacketId == ack.originalPacketId -> {
                    handled = true
                    current.copy(localHelpUpdates = mergeLocalHelpUpdates(current.localHelpUpdates, update))
                }
                else -> current
            }
        }
        if (handled) {
            emitLog(
                MeshEventType.ACK_RECEIVED,
                "Helper ${update.helperDeviceId} is ${update.status.displayName()} (${update.eta})",
                ack.originalPacketId,
                update.helperDeviceId
            )
        }
    }

    private fun applyServerAck(ack: AckPacket) {
        var message: String? = null
        _state.update { current ->
            when (current) {
                is MeshState.Originator -> if (current.packet.id == ack.originalPacketId) {
                    message = "Delivery confirmed: alertId=${ack.alertId}"
                    MeshState.Confirmed(
                        ack = ack,
                        packet = current.packet,
                        localHelpUpdates = current.localHelpUpdates
                    )
                } else current
                is MeshState.Confirmed -> if (current.ack.originalPacketId == ack.originalPacketId) {
                    current.copy(ack = ack)
                } else current
                is MeshState.Relay -> if (current.packet.id == ack.originalPacketId) {
                    message = "Server upload confirmed for packet ${ack.originalPacketId.take(8)}"
                    MeshState.AwaitingAck(
                        originalPacketId = ack.originalPacketId,
                        alertId = ack.alertId,
                        packet = current.packet,
                        receivedFromDevice = current.receivedFromDevice
                    )
                } else current
                is MeshState.Uploading -> if (current.packet.id == ack.originalPacketId) {
                    message = "Server upload confirmed for packet ${ack.originalPacketId.take(8)}"
                    MeshState.AwaitingAck(
                        originalPacketId = ack.originalPacketId,
                        alertId = ack.alertId,
                        packet = current.packet,
                        receivedFromDevice = current.receivedFromDevice
                    )
                } else current
                is MeshState.AwaitingAck -> if (current.originalPacketId == ack.originalPacketId) {
                    current.copy(alertId = ack.alertId)
                } else current
                else -> current
            }
        }
        message?.let { emitLog(MeshEventType.ACK_RECEIVED, it, ack.originalPacketId, ack.uploadedBy) }
    }

    // ── Peers / store-and-forward ─────────────────────────────────────────────

    fun onPeerConnected(peerId: String, name: String, transport: String) {
        emitLog(MeshEventType.PEER_CONNECTED, "Peer connected: $name via $transport", deviceId = peerId)
        scope.launch { rebroadcastActivePackets() }
    }

    fun onPeerDisconnected(peerId: String, transport: String) {
        emitLog(MeshEventType.PEER_DISCONNECTED, "Peer disconnected ($transport)", deviceId = peerId)
    }

    /** Re-sends every non-expired packet we originated or relayed. Receivers deduplicate. */
    suspend fun rebroadcastActivePackets() {
        activePackets.entries.removeIf { it.value.isExpired() }
        activePackets.values.toList().forEach { packet -> forward(packet) }
    }

    fun hasActivePackets(): Boolean = activePackets.isNotEmpty()

    private suspend fun forward(packet: SosPacket): Set<String> {
        val reached = relayPacketUseCase.relay(packet).getOrDefault(emptySet())
        if (packet.id in ownPacketIds && reached.isNotEmpty()) {
            val allReached = reachedPeersByPacket.getOrPut(packet.id) { ConcurrentHashMap.newKeySet() }
            allReached += reached
            _state.update { current ->
                if (current is MeshState.Originator && current.packet.id == packet.id) {
                    current.copy(peersReached = allReached.size)
                } else current
            }
        }
        return reached
    }

    // ── Received alerts ───────────────────────────────────────────────────────

    private fun addReceivedAlert(alert: ReceivedAlert) {
        _receivedAlerts.update { existing ->
            (listOf(alert) + existing.filterNot { it.packet.id == alert.packet.id }).take(MAX_RECEIVED_ALERTS)
        }
    }

    private fun updateReceivedAlert(packetId: String, transform: (ReceivedAlert) -> ReceivedAlert) {
        _receivedAlerts.update { list ->
            list.map { if (it.packet.id == packetId) transform(it) else it }
        }
    }

    fun clearReceivedAlerts() {
        _receivedAlerts.value = emptyList()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun AckPacket.toLocalHelpUpdate(): LocalHelpUpdate {
        val helperId = uploadedBy.ifBlank {
            alertId.removePrefix(AckPacket.LOCAL_HELP_ALERT_PREFIX).ifBlank { "unknown-helper" }
        }
        val status = helperStatus ?: HelperStatus.ACCEPTED
        val etaText = estimatedArrival.ifBlank {
            when (status) {
                HelperStatus.ACCEPTED -> "Accepted"
                HelperStatus.EN_ROUTE -> "En route"
                HelperStatus.REACHED -> "Reached"
                HelperStatus.CANNOT_CONTINUE -> "Cannot continue"
            }
        }
        return LocalHelpUpdate(
            helperDeviceId = helperId,
            eta = etaText,
            status = status,
            location = helperLocation,
            timestamp = helperTimestamp ?: Instant.now().epochSecond
        )
    }

    private fun mergeLocalHelpUpdates(
        existing: List<LocalHelpUpdate>,
        incoming: LocalHelpUpdate
    ): List<LocalHelpUpdate> {
        val filtered = existing.filterNot {
            it.helperDeviceId == incoming.helperDeviceId && it.timestamp == incoming.timestamp
        }
        return (listOf(incoming) + filtered)
            .sortedByDescending { it.timestamp }
            .take(MAX_LOCAL_HELP_UPDATES)
    }

    private fun HelperStatus.displayName(): String = when (this) {
        HelperStatus.ACCEPTED -> "accepted"
        HelperStatus.EN_ROUTE -> "en route"
        HelperStatus.REACHED -> "reached"
        HelperStatus.CANNOT_CONTINUE -> "cannot continue"
    }

    private fun emitLog(
        type: MeshEventType,
        message: String,
        packetId: String? = null,
        deviceId: String? = null
    ) {
        eventLogger.log(type, message, packetId, deviceId)
    }
}
