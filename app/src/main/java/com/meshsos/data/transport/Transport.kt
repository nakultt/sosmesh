package com.meshsos.data.transport

import com.meshsos.domain.model.AckPacket
import com.meshsos.domain.model.SosPacket
import kotlinx.coroutines.flow.SharedFlow

/**
 * Common interface for all transport implementations.
 * TransportManager selects which implementation to use at runtime.
 */
interface Transport {
    val transportName: String
    val peerEvents: SharedFlow<PeerEvent>
    val incomingPackets: SharedFlow<IncomingPacket>
    val incomingAcks: SharedFlow<AckPacket>

    /** Start advertising our presence and discovering peers */
    suspend fun start()

    /** Stop all advertising/discovery */
    suspend fun stop()

    /** Send an SOS packet to all currently connected peers */
    suspend fun broadcastPacket(packet: SosPacket): Result<Unit>

    /** Send an ACK back to the previous hop */
    suspend fun sendAck(ack: AckPacket, toDeviceId: String): Result<Unit>

    /** Currently connected peer count */
    fun connectedPeerCount(): Int

    /** Is this transport mode currently available on device? */
    fun isAvailable(): Boolean
}

data class IncomingPacket(
    val packet: SosPacket,
    val fromDevice: String
)

sealed class PeerEvent {
    data class Connected(val deviceId: String, val endpointName: String) : PeerEvent()
    data class Disconnected(val deviceId: String) : PeerEvent()
    data class ConnectionFailed(val deviceId: String, val reason: String) : PeerEvent()
    data class Error(val message: String) : PeerEvent()
    data class Log(val tag: String, val message: String) : PeerEvent()
}
