package com.meshsos.data.transport

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.meshsos.domain.model.AckPacket
import com.meshsos.domain.model.SosPacket
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "TransportManager"
private const val HEALTH_CHECK_INTERVAL_MS = 15_000L

/** Snapshot of one transport for the UI. */
data class TransportStatus(
    val name: String,
    val shortName: String,
    val running: Boolean,
    val peerCount: Int,
    val error: String?
)

/**
 * Runs every available transport at the same time (Nearby Connections + pure BLE GATT)
 * so devices can find each other no matter which radios/permissions each one has.
 * Packets are sent on all transports; receivers deduplicate by packet id.
 */
@Singleton
class TransportManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val nearbyTransport: NearbyConnectionsTransport,
    private val bleGattTransport: BleGattTransport
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val transports: List<Transport> = listOf(nearbyTransport, bleGattTransport)
    private val lifecycleLock = Mutex()
    private var healthJob: Job? = null
    private var receiverRegistered = false

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    val incomingPackets: Flow<IncomingPacket> = merge(*transports.map { it.incomingPackets }.toTypedArray())
    val incomingAcks: Flow<IncomingAck> = merge(*transports.map { it.incomingAcks }.toTypedArray())
    val peerEvents: Flow<PeerEvent> = merge(*transports.map { it.peerEvents }.toTypedArray())

    /** Unique peers across all transports, merged by mesh device id. */
    val peers: StateFlow<List<PeerInfo>> = combine(transports.map { it.peers }) { perTransport ->
        perTransport.toList()
            .flatMap { it }
            .groupBy { it.peerId }
            .map { (peerId, entries) ->
                PeerInfo(
                    peerId = peerId,
                    name = entries.firstOrNull { it.name != peerId }?.name ?: peerId,
                    transports = entries.flatMap { it.transports }.toSortedSet()
                )
            }
            .sortedBy { it.name }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    val transportStatuses: StateFlow<List<TransportStatus>> = combine(
        transports.flatMap { listOf(it.isRunning, it.peers, it.lastError) }
    ) { _ ->
        transports.map { transport ->
            TransportStatus(
                name = transport.transportName,
                shortName = transport.shortName,
                running = transport.isRunning.value,
                peerCount = transport.peers.value.size,
                error = transport.lastError.value
            )
        }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** Short label for the status bar, e.g. "Nearby + BLE" or "Offline". */
    val activeTransportLabel: StateFlow<String> = combine(transports.map { it.isRunning }) { running ->
        val active = transports.filterIndexed { index, _ -> running[index] }.map { it.shortName }
        if (active.isEmpty()) "Offline" else active.joinToString(" + ")
    }.stateIn(scope, SharingStarted.Eagerly, "Offline")

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    suspend fun start() {
        lifecycleLock.withLock {
            _isRunning.value = true
            registerSystemStateReceiver()
            reconcileLocked()
            if (healthJob?.isActive != true) {
                healthJob = scope.launch {
                    while (isActive) {
                        delay(HEALTH_CHECK_INTERVAL_MS)
                        reconcile()
                    }
                }
            }
        }
    }

    suspend fun stop() {
        lifecycleLock.withLock {
            _isRunning.value = false
            healthJob?.cancel()
            healthJob = null
            unregisterSystemStateReceiver()
            transports.forEach { transport ->
                runCatching { transport.stop() }
                    .onFailure { Log.w(TAG, "Stopping ${transport.shortName} failed: ${it.message}") }
            }
        }
    }

    /** Fire-and-forget stop that survives the caller's scope being cancelled (e.g. Service.onDestroy). */
    fun stopAsync() {
        scope.launch { stop() }
    }

    /** Re-evaluate each transport: start the ones that became available, stop the broken ones. */
    suspend fun reconcile() {
        lifecycleLock.withLock { reconcileLocked() }
    }

    fun reconcileAsync() {
        scope.launch { reconcile() }
    }

    private suspend fun reconcileLocked() {
        if (!_isRunning.value) return
        for (transport in transports) {
            val available = transport.isAvailable()
            val running = transport.isRunning.value
            when {
                available && !running -> {
                    runCatching { transport.start() }
                        .onSuccess { Log.i(TAG, "${transport.shortName} started") }
                        .onFailure { Log.w(TAG, "${transport.shortName} failed to start: ${it.message}") }
                }
                !available && running -> {
                    Log.i(TAG, "${transport.shortName} no longer available (${transport.unavailableReason()}), stopping")
                    runCatching { transport.stop() }
                }
            }
        }
    }

    // ── Bluetooth / Location state changes ────────────────────────────────────

    private val systemStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG, "System state changed: ${intent.action}")
            reconcileAsync()
        }
    }

    private fun registerSystemStateReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
        }
        runCatching {
            ContextCompat.registerReceiver(context, systemStateReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
            receiverRegistered = true
        }.onFailure { Log.w(TAG, "Failed to register state receiver: ${it.message}") }
    }

    private fun unregisterSystemStateReceiver() {
        if (!receiverRegistered) return
        runCatching { context.unregisterReceiver(systemStateReceiver) }
        receiverRegistered = false
    }

    // ── Sending ───────────────────────────────────────────────────────────────

    /** Sends on every running transport. Returns the set of peer ids reached. */
    suspend fun broadcastPacket(packet: SosPacket): Result<Set<String>> =
        sendOnAll { it.broadcastPacket(packet) }

    suspend fun broadcastAck(ack: AckPacket): Result<Set<String>> =
        sendOnAll { it.broadcastAck(ack) }

    private suspend fun sendOnAll(send: suspend (Transport) -> Result<Set<String>>): Result<Set<String>> {
        val running = transports.filter { it.isRunning.value }
        if (running.isEmpty()) return Result.failure(IllegalStateException("No transport running"))
        val reached = mutableSetOf<String>()
        val errors = mutableListOf<String>()
        for (transport in running) {
            send(transport)
                .onSuccess { reached += it }
                .onFailure { errors += "${transport.shortName}: ${it.message}" }
        }
        return if (reached.isNotEmpty()) Result.success(reached)
        else Result.failure(IllegalStateException(errors.joinToString("; ").ifBlank { "No peers connected" }))
    }

    fun connectedPeerCount(): Int = peers.value.size
}
