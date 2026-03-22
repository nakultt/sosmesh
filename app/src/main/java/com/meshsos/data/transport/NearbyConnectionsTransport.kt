package com.meshsos.data.transport

import android.content.Context
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import com.meshsos.domain.model.AckPacket
import com.meshsos.domain.model.PacketType
import com.meshsos.domain.model.SosPacket
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

private const val TAG = "NearbyTransport"
private const val SERVICE_ID = "com.meshsos.emergency"

@Singleton
class NearbyConnectionsTransport @Inject constructor(
    @ApplicationContext private val context: Context,
    private val localDeviceId: String,
    private val localDeviceName: String
) : Transport {

    override val transportName = "NearbyConnections (WiFi Direct + BLE)"

    private val connectionsClient: ConnectionsClient = Nearby.getConnectionsClient(context)
    private val connectedEndpoints = mutableSetOf<String>()

    private val _peerEvents = MutableSharedFlow<PeerEvent>(extraBufferCapacity = 32)
    override val peerEvents: SharedFlow<PeerEvent> = _peerEvents.asSharedFlow()

    private val _incomingPackets = MutableSharedFlow<IncomingPacket>(extraBufferCapacity = 32)
    override val incomingPackets: SharedFlow<IncomingPacket> = _incomingPackets.asSharedFlow()

    private val _incomingAcks = MutableSharedFlow<AckPacket>(extraBufferCapacity = 32)
    override val incomingAcks: SharedFlow<AckPacket> = _incomingAcks.asSharedFlow()

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override suspend fun start() {
        startAdvertising()
        startDiscovery()
        Log.i(TAG, "NearbyConnections started. deviceId=$localDeviceId")
    }

    override suspend fun stop() {
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
        connectionsClient.stopAllEndpoints()
        connectedEndpoints.clear()
        Log.i(TAG, "NearbyConnections stopped")
    }

    override fun isAvailable(): Boolean {
        // Nearby Connections is available on all devices with Google Play Services
        // Actual WiFi Direct availability is checked by TransportManager
        return true
    }

    override fun connectedPeerCount(): Int = connectedEndpoints.size

    // ── Advertising ───────────────────────────────────────────────────────────

    private fun startAdvertising() {
        val options = AdvertisingOptions.Builder()
            .setStrategy(Strategy.P2P_CLUSTER) // Many-to-many mesh
            .build()

        connectionsClient.startAdvertising(
            localDeviceName,
            SERVICE_ID,
            connectionLifecycleCallback,
            options
        ).addOnSuccessListener {
            Log.d(TAG, "Advertising started")
        }.addOnFailureListener { e ->
            Log.e(TAG, "Advertising failed: ${e.message}")
        }
    }

    // ── Discovery ─────────────────────────────────────────────────────────────

    private fun startDiscovery() {
        val options = DiscoveryOptions.Builder()
            .setStrategy(Strategy.P2P_CLUSTER)
            .build()

        connectionsClient.startDiscovery(
            SERVICE_ID,
            endpointDiscoveryCallback,
            options
        ).addOnSuccessListener {
            Log.d(TAG, "Discovery started")
        }.addOnFailureListener { e ->
            Log.e(TAG, "Discovery failed: ${e.message}")
        }
    }

    // ── Connection lifecycle ──────────────────────────────────────────────────

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            Log.d(TAG, "Connection initiated from $endpointId (${info.endpointName})")
            // Auto-accept all connections in mesh mode
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    connectedEndpoints.add(endpointId)
                    Log.i(TAG, "Connected: $endpointId (peers=${connectedEndpoints.size})")
                    _peerEvents.tryEmit(PeerEvent.Connected(endpointId, endpointId))
                }
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                    Log.w(TAG, "Connection rejected: $endpointId")
                    _peerEvents.tryEmit(PeerEvent.ConnectionFailed(endpointId, "Rejected"))
                }
                ConnectionsStatusCodes.STATUS_ERROR -> {
                    Log.e(TAG, "Connection error: $endpointId")
                    _peerEvents.tryEmit(PeerEvent.ConnectionFailed(endpointId, "Error"))
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            connectedEndpoints.remove(endpointId)
            Log.i(TAG, "Disconnected: $endpointId (peers=${connectedEndpoints.size})")
            _peerEvents.tryEmit(PeerEvent.Disconnected(endpointId))
        }
    }

    // ── Discovery callback ────────────────────────────────────────────────────

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            Log.d(TAG, "Endpoint found: $endpointId service=${info.serviceId}")
            if (info.serviceId == SERVICE_ID && !connectedEndpoints.contains(endpointId)) {
                connectionsClient.requestConnection(
                    localDeviceName,
                    endpointId,
                    connectionLifecycleCallback
                ).addOnFailureListener { e ->
                    Log.w(TAG, "Request connection failed to $endpointId: ${e.message}")
                }
            }
        }

        override fun onEndpointLost(endpointId: String) {
            Log.d(TAG, "Endpoint lost: $endpointId")
        }
    }

    // ── Payload ───────────────────────────────────────────────────────────────

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type != Payload.Type.BYTES) return
            val bytes = payload.asBytes() ?: return

            Log.d(TAG, "Payload received from $endpointId size=${bytes.size}")

            // Try as SOS packet first
            val sosPacket = SosPacket.fromBytes(bytes)
            if (sosPacket != null && sosPacket.type == PacketType.SOS) {
                _incomingPackets.tryEmit(IncomingPacket(sosPacket, endpointId))
                return
            }

            // Try as ACK
            val ack = AckPacket.fromBytes(bytes)
            if (ack != null) {
                _incomingAcks.tryEmit(ack)
                return
            }

            Log.w(TAG, "Unknown payload from $endpointId")
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // Optional: track transfer progress for large payloads
            if (update.status == PayloadTransferUpdate.Status.FAILURE) {
                Log.w(TAG, "Transfer failed to $endpointId")
            }
        }
    }

    // ── Send ──────────────────────────────────────────────────────────────────

    override suspend fun broadcastPacket(packet: SosPacket): Result<Unit> {
        if (connectedEndpoints.isEmpty()) {
            return Result.failure(IllegalStateException("No connected peers"))
        }

        val bytes = packet.toBytes()
        val payload = Payload.fromBytes(bytes)

        var successCount = 0
        val snapshot = connectedEndpoints.toSet()

        for (endpointId in snapshot) {
            val result = sendPayloadSuspend(endpointId, payload)
            if (result.isSuccess) successCount++
            else Log.w(TAG, "Failed to send to $endpointId")
        }

        return if (successCount > 0) Result.success(Unit)
        else Result.failure(Exception("All sends failed"))
    }

    override suspend fun sendAck(ack: AckPacket, toDeviceId: String): Result<Unit> {
        val payload = Payload.fromBytes(ack.toBytes())
        return sendPayloadSuspend(toDeviceId, payload)
    }

    private suspend fun sendPayloadSuspend(endpointId: String, payload: Payload): Result<Unit> =
        suspendCancellableCoroutine { cont ->
            connectionsClient.sendPayload(endpointId, payload)
                .addOnSuccessListener { cont.resume(Result.success(Unit)) }
                .addOnFailureListener { e -> cont.resume(Result.failure(e)) }
        }
}
