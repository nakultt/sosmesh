package com.meshsos.domain.usecase

import android.util.Log
import com.meshsos.data.transport.TransportManager
import com.meshsos.domain.model.SosPacket
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RelayPacketUseCase"

@Singleton
class RelayPacketUseCase @Inject constructor(
    private val transportManager: TransportManager
) {
    /**
     * Forward packet to all currently connected peers.
     * Called when this device is offline and acting as a relay node.
     */
    suspend fun relay(packet: SosPacket): Result<Unit> {
        val peerCount = transportManager.connectedPeerCount()
        if (peerCount == 0) {
            Log.d(TAG, "No peers connected — packet queued for when peers arrive")
            return Result.failure(Exception("No peers available"))
        }
        Log.d(TAG, "Relaying packet ${packet.id} to $peerCount peers via ${transportManager.activeTransport.value.transportName}")
        return transportManager.broadcastPacket(packet)
    }
}
