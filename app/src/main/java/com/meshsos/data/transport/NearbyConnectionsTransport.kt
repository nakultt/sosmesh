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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "NearbyTransport"
private const val SERVICE_ID = "com.meshsos.emergency"

@Singleton
class NearbyConnectionsTransport @Inject constructor(
    @ApplicationContext private val context: Context,
    @Named("deviceId") private val localDeviceId: String,
    @Named("deviceName") private val localDeviceName: String
) : Transport {

    override val transportName = "NearbyConnections (WiFi Direct + BLE)"

    private val connectionsClient: ConnectionsClient = Nearby.getConnectionsClient(context)
    private val connectedEndpoints = mutableSetOf<String>()
    private val pendingConnections = mutableSetOf<String>() // endpoints we're currently connecting to
    private val failedEndpoints = mutableMapOf<String, Int>() // endpoint -> retry count

    private val _peerEvents = MutableSharedFlow<PeerEvent>(extraBufferCapacity = 32)
    override val peerEvents: SharedFlow<PeerEvent> = _peerEvents.asSharedFlow()

    private val _incomingPackets = MutableSharedFlow<IncomingPacket>(extraBufferCapacity = 32)
    override val incomingPackets: SharedFlow<IncomingPacket> = _incomingPackets.asSharedFlow()

    private val _incomingAcks = MutableSharedFlow<AckPacket>(extraBufferCapacity = 32)
    override val incomingAcks: SharedFlow<AckPacket> = _incomingAcks.asSharedFlow()

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override suspend fun start() {
        val unavailableReason = TransportCapabilityChecker.nearbyUnavailableReason(context)
        if (unavailableReason != null) {
            Log.w(TAG, unavailableReason)
            _peerEvents.tryEmit(PeerEvent.Error(unavailableReason))
            throw IllegalStateException(unavailableReason)
        }

        startAdvertising()
        try {
            startDiscovery()
        } catch (e: Throwable) {
            runCatching { connectionsClient.stopAdvertising() }
            throw e
        }

        Log.i(TAG, "NearbyConnections started. deviceId=$localDeviceId name=$localDeviceName")
    }

    override suspend fun stop() {
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
        connectionsClient.stopAllEndpoints()
        connectedEndpoints.clear()
        pendingConnections.clear()
        failedEndpoints.clear()
        Log.i(TAG, "NearbyConnections stopped")
    }

    override fun isAvailable(): Boolean {
        return TransportCapabilityChecker.isNearbyAvailable(context)
    }

    override fun connectedPeerCount(): Int = connectedEndpoints.size

    // ── Advertising ───────────────────────────────────────────────────────────

    private suspend fun startAdvertising() = suspendCancellableCoroutine<Unit> { cont ->
        val options = AdvertisingOptions.Builder()
            .setStrategy(Strategy.P2P_CLUSTER)
            .build()

        connectionsClient.startAdvertising(
            localDeviceName,
            SERVICE_ID,
            connectionLifecycleCallback,
            options
        ).addOnSuccessListener {
            Log.d(TAG, "Advertising started as '$localDeviceName'")
            _peerEvents.tryEmit(PeerEvent.Log("Nearby", "Advertising started as '$localDeviceName'"))
            if (cont.isActive) cont.resume(Unit)
        }.addOnFailureListener { e ->
            Log.e(TAG, "Advertising failed: ${e.message}")
            _peerEvents.tryEmit(PeerEvent.Error("Adv failed: ${e.message}"))
            if (cont.isActive) cont.resumeWithException(e)
        }
    }

    // ── Discovery ─────────────────────────────────────────────────────────────

    private suspend fun startDiscovery() = suspendCancellableCoroutine<Unit> { cont ->
        val options = DiscoveryOptions.Builder()
            .setStrategy(Strategy.P2P_CLUSTER)
            .build()

        connectionsClient.startDiscovery(
            SERVICE_ID,
            endpointDiscoveryCallback,
            options
        ).addOnSuccessListener {
            Log.d(TAG, "Discovery started")
            _peerEvents.tryEmit(PeerEvent.Log("Nearby", "Discovery started"))
            if (cont.isActive) cont.resume(Unit)
        }.addOnFailureListener { e ->
            Log.e(TAG, "Discovery failed: ${e.message}")
            _peerEvents.tryEmit(PeerEvent.Error("Disc failed: ${e.message}"))
            if (cont.isActive) cont.resumeWithException(e)
        }
    }

    // ── Connection lifecycle ──────────────────────────────────────────────────

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            Log.d(TAG, "Connection initiated from $endpointId (${info.endpointName})")
            _peerEvents.tryEmit(PeerEvent.Log("Nearby", "Conn initiated: $endpointId (${info.endpointName})"))
            // Auto-accept all connections in mesh mode
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            pendingConnections.remove(endpointId)
            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    connectedEndpoints.add(endpointId)
                    failedEndpoints.remove(endpointId)
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
                    // Will be retried on next discovery cycle
                }
                else -> {
                    Log.w(TAG, "Connection unknown status ${result.status.statusCode}: $endpointId")
                    _peerEvents.tryEmit(PeerEvent.ConnectionFailed(endpointId, "Status ${result.status.statusCode}"))
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
            Log.d(TAG, "Endpoint found: $endpointId name=${info.endpointName} service=${info.serviceId}")
            _peerEvents.tryEmit(PeerEvent.Log("Nearby", "Endpoint found: $endpointId (${info.endpointName})"))

            if (info.serviceId != SERVICE_ID) return
            if (connectedEndpoints.contains(endpointId)) return
            if (pendingConnections.contains(endpointId)) return

            // ── TIE-BREAKER: prevent both devices from requesting simultaneously ──
            // Only the device with the lexicographically SMALLER name initiates.
            // The other device waits — it will receive the connection via onConnectionInitiated.
            val shouldInitiate = localDeviceName < info.endpointName
            Log.d(TAG, "Tie-breaker: localName='$localDeviceName' remoteName='${info.endpointName}' shouldInitiate=$shouldInitiate")
            _peerEvents.tryEmit(PeerEvent.Log("Nearby", "Tie-break: '$localDeviceName' vs '${info.endpointName}' → ${if (shouldInitiate) "CONNECT" else "WAIT"}"))

            if (!shouldInitiate) {
                // We have the "larger" name — wait for the other side to connect to us
                return
            }

            pendingConnections.add(endpointId)
            connectionsClient.requestConnection(
                localDeviceName,
                endpointId,
                connectionLifecycleCallback
            ).addOnSuccessListener {
                Log.d(TAG, "Connection requested to $endpointId")
                _peerEvents.tryEmit(PeerEvent.Log("Nearby", "Conn requested → $endpointId"))
            }.addOnFailureListener { e ->
                pendingConnections.remove(endpointId)
                val retryCount = failedEndpoints.getOrDefault(endpointId, 0) + 1
                failedEndpoints[endpointId] = retryCount
                Log.w(TAG, "Request connection failed to $endpointId (attempt $retryCount): ${e.message}")
                _peerEvents.tryEmit(PeerEvent.Error("Conn Req failed → $endpointId: ${e.message}"))

                // Retry after a delay if under max retries
                if (retryCount <= 3) {
                    val delayMs = (1000L * retryCount) + (Math.random() * 500).toLong()
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        if (!connectedEndpoints.contains(endpointId) && !pendingConnections.contains(endpointId)) {
                            Log.d(TAG, "Retrying connection to $endpointId (attempt ${retryCount + 1})")
                            _peerEvents.tryEmit(PeerEvent.Log("Nearby", "Retry #${retryCount + 1} → $endpointId"))
                            pendingConnections.add(endpointId)
                            connectionsClient.requestConnection(
                                localDeviceName,
                                endpointId,
                                connectionLifecycleCallback
                            ).addOnFailureListener { retryErr ->
                                pendingConnections.remove(endpointId)
                                Log.w(TAG, "Retry failed to $endpointId: ${retryErr.message}")
                                _peerEvents.tryEmit(PeerEvent.Error("Retry failed → $endpointId: ${retryErr.message}"))
                            }
                        }
                    }, delayMs)
                }
            }
        }

        override fun onEndpointLost(endpointId: String) {
            Log.d(TAG, "Endpoint lost: $endpointId")
            pendingConnections.remove(endpointId)
            failedEndpoints.remove(endpointId)
            _peerEvents.tryEmit(PeerEvent.Log("Nearby", "Endpoint lost: $endpointId"))
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
