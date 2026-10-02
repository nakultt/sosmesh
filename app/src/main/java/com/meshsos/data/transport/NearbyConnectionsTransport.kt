package com.meshsos.data.transport

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.common.api.ApiException
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
import com.meshsos.domain.model.SosPacket
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.coroutines.resume

private const val TAG = "NearbyTransport"
private const val SERVICE_ID = "com.meshsos.emergency"
private const val MAINTENANCE_INTERVAL_MS = 3_000L
private const val TIE_BREAK_GRACE_MS = 6_000L
private const val PENDING_TIMEOUT_MS = 30_000L
private const val MAX_BACKOFF_MS = 30_000L
private const val SEND_TIMEOUT_MS = 10_000L

@Singleton
class NearbyConnectionsTransport @Inject constructor(
    @ApplicationContext private val context: Context,
    @Named("deviceId") private val localDeviceId: String,
    @Named("deviceName") private val localDeviceName: String
) : Transport {

    override val transportName = "NearbyConnections (WiFi Direct + BLE)"
    override val shortName = "Nearby"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connectionsClient: ConnectionsClient = Nearby.getConnectionsClient(context)
    private val localEndpointName = WireCodec.encodeIdentity(localDeviceId, localDeviceName)

    private data class Endpoint(
        val endpointId: String,
        val peerId: String,
        val name: String,
        val discoveredAt: Long
    )

    private val discovered = ConcurrentHashMap<String, Endpoint>()   // endpointId -> discovered info
    private val connected = ConcurrentHashMap<String, Endpoint>()    // endpointId -> connected peer
    private val initiated = ConcurrentHashMap<String, Endpoint>()    // endpointId -> handshake info
    private val pending = ConcurrentHashMap<String, Long>()          // endpointId -> request time
    private val retryAfter = ConcurrentHashMap<String, Long>()
    private val failures = ConcurrentHashMap<String, Int>()

    private val _peerEvents = MutableSharedFlow<PeerEvent>(extraBufferCapacity = 64)
    override val peerEvents: SharedFlow<PeerEvent> = _peerEvents.asSharedFlow()

    private val _incomingPackets = MutableSharedFlow<IncomingPacket>(extraBufferCapacity = 64)
    override val incomingPackets: SharedFlow<IncomingPacket> = _incomingPackets.asSharedFlow()

    private val _incomingAcks = MutableSharedFlow<IncomingAck>(extraBufferCapacity = 64)
    override val incomingAcks: SharedFlow<IncomingAck> = _incomingAcks.asSharedFlow()

    private val _peers = MutableStateFlow<List<PeerInfo>>(emptyList())
    override val peers: StateFlow<List<PeerInfo>> = _peers.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    override val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    override val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val lifecycleLock = Mutex()
    private var maintenanceJob: Job? = null

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override suspend fun start() {
        lifecycleLock.withLock {
            if (_isRunning.value) return@withLock
            val unavailableReason = unavailableReason()
            if (unavailableReason != null) {
                _lastError.value = unavailableReason
                _peerEvents.tryEmit(PeerEvent.Error(unavailableReason))
                throw IllegalStateException(unavailableReason)
            }

            try {
                startAdvertising()
                startDiscovery()
            } catch (e: Throwable) {
                stopInternal()
                val message = "Nearby start failed: ${e.describe()}"
                _lastError.value = message
                _peerEvents.tryEmit(PeerEvent.Error(message))
                throw e
            }

            _isRunning.value = true
            _lastError.value = null
            maintenanceJob = scope.launch { runMaintenance() }
            log("Nearby started as '$localEndpointName'")
        }
    }

    override suspend fun stop() {
        lifecycleLock.withLock { stopInternal() }
        Log.i(TAG, "NearbyConnections stopped")
    }

    private fun stopInternal() {
        _isRunning.value = false
        maintenanceJob?.cancel()
        maintenanceJob = null
        runCatching { connectionsClient.stopAdvertising() }
        runCatching { connectionsClient.stopDiscovery() }
        runCatching { connectionsClient.stopAllEndpoints() }
        discovered.clear()
        connected.clear()
        initiated.clear()
        pending.clear()
        retryAfter.clear()
        failures.clear()
        updatePeers()
    }

    override fun isAvailable(): Boolean = TransportCapabilityChecker.isNearbyAvailable(context)

    override fun unavailableReason(): String? = TransportCapabilityChecker.nearbyUnavailableReason(context)

    // ── Advertising / discovery ───────────────────────────────────────────────

    private suspend fun startAdvertising() {
        val options = AdvertisingOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()
        awaitTask("advertising", ConnectionsStatusCodes.STATUS_ALREADY_ADVERTISING) { onSuccess, onFailure ->
            connectionsClient.startAdvertising(localEndpointName, SERVICE_ID, connectionLifecycleCallback, options)
                .addOnSuccessListener { onSuccess() }
                .addOnFailureListener { onFailure(it) }
        }
    }

    private suspend fun startDiscovery() {
        val options = DiscoveryOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()
        awaitTask("discovery", ConnectionsStatusCodes.STATUS_ALREADY_DISCOVERING) { onSuccess, onFailure ->
            connectionsClient.startDiscovery(SERVICE_ID, endpointDiscoveryCallback, options)
                .addOnSuccessListener { onSuccess() }
                .addOnFailureListener { onFailure(it) }
        }
    }

    private suspend fun awaitTask(
        label: String,
        alreadyRunningStatus: Int,
        block: (onSuccess: () -> Unit, onFailure: (Exception) -> Unit) -> Unit
    ) = suspendCancellableCoroutine<Unit> { cont ->
        block(
            { if (cont.isActive) cont.resume(Unit) },
            { error ->
                if ((error as? ApiException)?.statusCode == alreadyRunningStatus) {
                    if (cont.isActive) cont.resume(Unit)
                } else {
                    Log.e(TAG, "Nearby $label failed: ${error.describe()}")
                    if (cont.isActive) cont.resumeWith(Result.failure(error))
                }
            }
        )
    }

    // ── Discovery callback ────────────────────────────────────────────────────

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            if (info.serviceId != SERVICE_ID) return
            val (peerId, name) = WireCodec.parseIdentity(info.endpointName) ?: return
            if (peerId == localDeviceId) return
            discovered[endpointId] = Endpoint(endpointId, peerId, name, SystemClock.elapsedRealtime())
            Log.d(TAG, "Endpoint found: $endpointId peer=$peerId name=$name")
            maybeConnect(endpointId)
        }

        override fun onEndpointLost(endpointId: String) {
            Log.d(TAG, "Endpoint lost: $endpointId")
            discovered.remove(endpointId)
            if (!connected.containsKey(endpointId)) {
                pending.remove(endpointId)
                failures.remove(endpointId)
                retryAfter.remove(endpointId)
            }
        }
    }

    private suspend fun runMaintenance() {
        while (scope.isActive && _isRunning.value) {
            delay(MAINTENANCE_INTERVAL_MS)
            val now = SystemClock.elapsedRealtime()
            // Requests that never produced a result are considered failed.
            pending.entries.filter { now - it.value > PENDING_TIMEOUT_MS }.forEach { (endpointId, _) ->
                pending.remove(endpointId)
                scheduleRetry(endpointId)
            }
            discovered.keys.forEach { maybeConnect(it) }
        }
    }

    /**
     * Tie-breaker: the device with the smaller mesh id requests the connection immediately.
     * The other device waits for a grace period and then connects anyway, so a one-sided
     * discovery or identical names never leave two phones unconnected.
     */
    private fun maybeConnect(endpointId: String) {
        if (!_isRunning.value) return
        val endpoint = discovered[endpointId] ?: return
        if (connected.containsKey(endpointId) || pending.containsKey(endpointId)) return
        if (connected.values.any { it.peerId == endpoint.peerId }) return
        val now = SystemClock.elapsedRealtime()
        if ((retryAfter[endpointId] ?: 0L) > now) return
        val weInitiate = localDeviceId < endpoint.peerId
        if (!weInitiate && now - endpoint.discoveredAt < TIE_BREAK_GRACE_MS) return
        requestConnection(endpoint)
    }

    private fun requestConnection(endpoint: Endpoint) {
        val endpointId = endpoint.endpointId
        if (pending.putIfAbsent(endpointId, SystemClock.elapsedRealtime()) != null) return
        Log.d(TAG, "Requesting connection to ${endpoint.peerId} ($endpointId)")
        connectionsClient.requestConnection(localEndpointName, endpointId, connectionLifecycleCallback)
            .addOnSuccessListener {
                _peerEvents.tryEmit(PeerEvent.Log(shortName, "Connection requested → ${endpoint.name}"))
            }
            .addOnFailureListener { error ->
                val status = (error as? ApiException)?.statusCode
                if (status == ConnectionsStatusCodes.STATUS_ALREADY_CONNECTED_TO_ENDPOINT) {
                    // The other side connected to us first; onConnectionResult will follow.
                    return@addOnFailureListener
                }
                pending.remove(endpointId)
                Log.w(TAG, "requestConnection to $endpointId failed: ${error.describe()}")
                scheduleRetry(endpointId)
            }
    }

    private fun scheduleRetry(endpointId: String) {
        val attempt = (failures[endpointId] ?: 0) + 1
        failures[endpointId] = attempt
        val backoff = (1_000L shl (attempt - 1).coerceAtMost(5)).coerceAtMost(MAX_BACKOFF_MS) +
            (Math.random() * 750).toLong()
        retryAfter[endpointId] = SystemClock.elapsedRealtime() + backoff
    }

    // ── Connection lifecycle ──────────────────────────────────────────────────

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            val (peerId, name) = WireCodec.parseIdentity(info.endpointName) ?: (endpointId to endpointId)
            initiated[endpointId] = Endpoint(endpointId, peerId, name, SystemClock.elapsedRealtime())
            pending.putIfAbsent(endpointId, SystemClock.elapsedRealtime())
            Log.d(TAG, "Connection initiated with $endpointId ($name), accepting")
            // Mesh mode: auto-accept everyone running the SOS service.
            connectionsClient.acceptConnection(endpointId, payloadCallback)
                .addOnFailureListener { Log.w(TAG, "acceptConnection failed: ${it.describe()}") }
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            pending.remove(endpointId)
            val statusCode = result.status.statusCode
            when (statusCode) {
                ConnectionsStatusCodes.STATUS_OK,
                ConnectionsStatusCodes.STATUS_ALREADY_CONNECTED_TO_ENDPOINT -> {
                    val endpoint = initiated.remove(endpointId)
                        ?: discovered[endpointId]
                        ?: Endpoint(endpointId, endpointId, endpointId, SystemClock.elapsedRealtime())
                    connected[endpointId] = endpoint
                    failures.remove(endpointId)
                    retryAfter.remove(endpointId)
                    Log.i(TAG, "Connected: ${endpoint.peerId} via $endpointId (peers=${connected.size})")
                    updatePeers()
                }
                else -> {
                    initiated.remove(endpointId)
                    val reason = when (statusCode) {
                        ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> "rejected"
                        ConnectionsStatusCodes.STATUS_ERROR -> "error"
                        else -> "status $statusCode"
                    }
                    Log.w(TAG, "Connection to $endpointId failed: $reason")
                    _peerEvents.tryEmit(PeerEvent.ConnectionFailed(discovered[endpointId]?.peerId ?: endpointId, reason))
                    scheduleRetry(endpointId)
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            connected.remove(endpointId)
            pending.remove(endpointId)
            Log.i(TAG, "Disconnected: $endpointId (peers=${connected.size})")
            updatePeers()
            // Reconnect quickly if the endpoint is still around.
            retryAfter[endpointId] = SystemClock.elapsedRealtime() + 1_000L
        }
    }

    // ── Payload ───────────────────────────────────────────────────────────────

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type != Payload.Type.BYTES) return
            val bytes = payload.asBytes() ?: return
            val fromPeer = connected[endpointId]?.peerId ?: initiated[endpointId]?.peerId ?: endpointId

            when (val message = WireCodec.decode(bytes)) {
                is WireCodec.Message.Sos -> _incomingPackets.tryEmit(IncomingPacket(message.packet, fromPeer))
                is WireCodec.Message.Ack -> _incomingAcks.tryEmit(IncomingAck(message.ack, fromPeer))
                is WireCodec.Message.Hello -> Unit // Identity already comes from the endpoint name
                null -> Log.w(TAG, "Unknown payload (${bytes.size} bytes) from $endpointId")
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            if (update.status == PayloadTransferUpdate.Status.FAILURE) {
                Log.w(TAG, "Payload transfer to/from $endpointId failed")
            }
        }
    }

    // ── Send ──────────────────────────────────────────────────────────────────

    override suspend fun broadcastPacket(packet: SosPacket): Result<Set<String>> =
        sendToAll(WireCodec.encodeSos(packet))

    override suspend fun broadcastAck(ack: AckPacket): Result<Set<String>> =
        sendToAll(WireCodec.encodeAck(ack))

    private suspend fun sendToAll(bytes: ByteArray): Result<Set<String>> {
        if (!_isRunning.value) return Result.failure(IllegalStateException("Nearby transport not running"))
        val snapshot = connected.values.toList()
        if (snapshot.isEmpty()) return Result.failure(IllegalStateException("No Nearby peers connected"))

        val ok = sendPayload(snapshot.map { it.endpointId }, Payload.fromBytes(bytes))
        return if (ok) Result.success(snapshot.map { it.peerId }.toSet())
        else Result.failure(IllegalStateException("Nearby send failed"))
    }

    private suspend fun sendPayload(endpointIds: List<String>, payload: Payload): Boolean =
        withTimeoutOrNull(SEND_TIMEOUT_MS) {
            suspendCancellableCoroutine<Boolean> { cont ->
                connectionsClient.sendPayload(endpointIds, payload)
                    .addOnSuccessListener { if (cont.isActive) cont.resume(true) }
                    .addOnFailureListener { error ->
                        Log.w(TAG, "sendPayload failed: ${error.describe()}")
                        if (cont.isActive) cont.resume(false)
                    }
            }
        } ?: false

    // ── Peers ─────────────────────────────────────────────────────────────────

    @Synchronized
    private fun updatePeers() {
        val updated = connected.values
            .groupBy { it.peerId }
            .map { (peerId, endpoints) -> PeerInfo(peerId, endpoints.first().name, setOf(shortName)) }
            .sortedBy { it.peerId }
        val previous = _peers.value
        if (updated == previous) return
        _peers.value = updated

        val previousIds = previous.map { it.peerId }.toSet()
        val updatedIds = updated.map { it.peerId }.toSet()
        updated.filter { it.peerId !in previousIds }.forEach {
            _peerEvents.tryEmit(PeerEvent.Connected(it.peerId, it.name, shortName))
        }
        (previousIds - updatedIds).forEach {
            _peerEvents.tryEmit(PeerEvent.Disconnected(it, shortName))
        }
    }

    private fun log(message: String) {
        Log.i(TAG, message)
        _peerEvents.tryEmit(PeerEvent.Log(shortName, message))
    }

    private fun Throwable.describe(): String {
        val code = (this as? ApiException)?.statusCode
        val codeName = code?.let { ConnectionsStatusCodes.getStatusCodeString(it) }
        return listOfNotNull(codeName, message).joinToString(": ").ifBlank { javaClass.simpleName }
    }
}
