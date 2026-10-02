package com.meshsos.domain.statemachine

import com.meshsos.domain.model.AckPacket
import com.meshsos.domain.model.HelperStatus
import com.meshsos.domain.model.LocationInfo
import com.meshsos.domain.model.SosPacket
import java.time.Instant

// ── States ────────────────────────────────────────────────────────────────────

sealed class MeshState {
    /** App is open, mesh is listening, no active SOS */
    object Idle : MeshState()

    /** This device originated the SOS — broadcasting it and waiting for an upload ACK */
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

    /** Upload succeeded — ACK sent back through the mesh toward the originator */
    data class AwaitingAck(
        val originalPacketId: String,
        val alertId: String,
        val packet: SosPacket? = null,
        val receivedFromDevice: String? = null
    ) : MeshState()

    /** Originator received ACK — SOS confirmed delivered */
    data class Confirmed(
        val ack: AckPacket,
        val packet: SosPacket? = null,
        val localHelpUpdates: List<LocalHelpUpdate> = emptyList()
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
    val status: HelperStatus = HelperStatus.ACCEPTED,
    val location: LocationInfo? = null,
    val timestamp: Long = Instant.now().epochSecond
)

/** An SOS from another device that reached this one (kept even while we are the originator). */
data class ReceivedAlert(
    val packet: SosPacket,
    val fromDevice: String,
    val receivedAt: Long = Instant.now().epochSecond,
    val alertId: String? = null
)
