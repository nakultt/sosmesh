package com.meshsos.data.transport

import com.meshsos.domain.model.AckPacket
import com.meshsos.domain.model.SosPacket
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Common interface for all transport implementations.
 * [TransportManager] runs every available transport at the same time and merges them.
 */
interface Transport {
    /** Human readable name, e.g. "NearbyConnections (WiFi Direct + BLE)" */
    val transportName: String

    /** Short label used in peer lists, e.g. "Nearby" or "BLE" */
    val shortName: String

    val peerEvents: SharedFlow<PeerEvent>
    val incomingPackets: SharedFlow<IncomingPacket>
    val incomingAcks: SharedFlow<IncomingAck>

    /** Peers that are fully connected and ready to exchange data */
    val peers: StateFlow<List<PeerInfo>>

    /** Whether advertising/discovery is currently active */
    val isRunning: StateFlow<Boolean>

    /** Last start/runtime error, null when healthy */
    val lastError: StateFlow<String?>

    /** Start advertising our presence and discovering peers. Throws if it cannot start. */
    suspend fun start()

    /** Stop all advertising/discovery and drop connections */
    suspend fun stop()

    /** Send an SOS packet to every ready peer. Returns the ids of peers it was delivered to. */
    suspend fun broadcastPacket(packet: SosPacket): Result<Set<String>>

    /** Send an ACK / helper update to every ready peer (ACKs are flooded and deduplicated by id). */
    suspend fun broadcastAck(ack: AckPacket): Result<Set<String>>

    /** Is this transport usable right now (permissions granted, radios on)? */
    fun isAvailable(): Boolean

    /** Why the transport is unavailable, or null when it is available */
    fun unavailableReason(): String?
}

/**
 * A peer reachable over one or more transports.
 * [peerId] is the remote mesh device id when known (exchanged during connection setup).
 */
data class PeerInfo(
    val peerId: String,
    val name: String,
    val transports: Set<String>
)

data class IncomingPacket(
    val packet: SosPacket,
    val fromDevice: String
)

data class IncomingAck(
    val ack: AckPacket,
    val fromDevice: String
)

sealed class PeerEvent {
    data class Connected(val deviceId: String, val endpointName: String, val transport: String = "") : PeerEvent()
    data class Disconnected(val deviceId: String, val transport: String = "") : PeerEvent()
    data class ConnectionFailed(val deviceId: String, val reason: String) : PeerEvent()
    data class Error(val message: String) : PeerEvent()
    data class Log(val tag: String, val message: String) : PeerEvent()
}
