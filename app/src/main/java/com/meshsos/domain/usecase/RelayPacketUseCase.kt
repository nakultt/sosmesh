package com.meshsos.domain.usecase

import android.util.Log
import com.meshsos.data.transport.TransportManager
import com.meshsos.domain.model.AckPacket
import com.meshsos.domain.model.SosPacket
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RelayPacketUseCase"

@Singleton
class RelayPacketUseCase @Inject constructor(
    private val transportManager: TransportManager
) {
    /**
     * Forward [packet] to every reachable peer on every running transport.
     * Returns the ids of the peers it reached (failure when nobody was reachable).
     */
    suspend fun relay(packet: SosPacket): Result<Set<String>> {
        if (transportManager.connectedPeerCount() == 0) {
            Log.d(TAG, "No peers connected — packet ${packet.id.take(8)} kept for store-and-forward")
            return Result.failure(IllegalStateException("No peers available"))
        }
        return transportManager.broadcastPacket(packet)
            .onSuccess { Log.d(TAG, "Packet ${packet.id.take(8)} delivered to ${it.size} peer(s)") }
            .onFailure { Log.w(TAG, "Relay of ${packet.id.take(8)} failed: ${it.message}") }
    }

    /** Flood an ACK / helper update to every reachable peer. */
    suspend fun relayAck(ack: AckPacket): Result<Set<String>> {
        if (transportManager.connectedPeerCount() == 0) {
            return Result.failure(IllegalStateException("No peers available"))
        }
        return transportManager.broadcastAck(ack)
    }
}
