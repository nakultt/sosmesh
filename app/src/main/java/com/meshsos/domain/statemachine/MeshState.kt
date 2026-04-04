package com.meshsos.domain.statemachine

import com.meshsos.domain.model.AckPacket
import com.meshsos.domain.model.SosPacket
import java.time.Instant

// ── States ────────────────────────────────────────────────────────────────────

sealed class MeshState {
    /** App is open, BLE is scanning passively, no active SOS */
    object Idle : MeshState()

    /** This device originated the SOS — scanning + advertising, waiting for relay/ack */
    data class Originator(
        val packet: SosPacket,
        val peersReached: Int = 0,
        val localHelpUpdates: List<LocalHelpUpdate> = emptyList()
    ) : MeshState()

    /** This device received someone else's SOS and is relaying it */
    data class Relay(
        val packet: SosPacket,
        val receivedFromDevice: String
    ) : MeshState()

    /** Relay node has internet — currently uploading to server */
    data class Uploading(
        val packet: SosPacket,
        val attemptNumber: Int = 1,
        val receivedFromDevice: String? = null
    ) : MeshState()

    /** Upload succeeded — ACK sent back along route, waiting for originator ACK */
    data class AwaitingAck(
        val originalPacketId: String,
        val alertId: String,
        val packet: SosPacket? = null,
        val receivedFromDevice: String? = null
    ) : MeshState()

    /** Originator received ACK — SOS confirmed delivered */
    data class Confirmed(
        val ack: AckPacket
    ) : MeshState()

    /** Something went wrong */
    data class Error(
        val reason: String,
        val recoverable: Boolean = true
    ) : MeshState()
}

data class LocalHelpUpdate(
    val helperDeviceId: String,
    val eta: String,
    val timestamp: Long = Instant.now().epochSecond
)

// ── Events that drive state transitions ───────────────────────────────────────

sealed class MeshEvent {
    data class UserTriggeredSos(val packet: SosPacket) : MeshEvent()
    data class PacketReceived(val packet: SosPacket, val fromDevice: String) : MeshEvent()
    data class AckReceived(val ack: AckPacket) : MeshEvent()
    data class UploadSucceeded(val alertId: String) : MeshEvent()
    data class UploadFailed(val reason: String) : MeshEvent()
    data class PeerConnected(val deviceId: String) : MeshEvent()
    data class PeerDisconnected(val deviceId: String) : MeshEvent()
    object UserCancelledSos : MeshEvent()
    object Reset : MeshEvent()
}
