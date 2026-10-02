package com.meshsos.data.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import android.os.SystemClock
import android.util.Log
import com.meshsos.domain.model.AckPacket
import com.meshsos.domain.model.SosPacket
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

private const val TAG = "BleGattTransport"

// Custom UUIDs for our SOS mesh service
private val SOS_SERVICE_UUID: UUID = UUID.fromString("12345678-1234-1234-1234-123456789abc")
private val SOS_PACKET_CHAR_UUID: UUID = UUID.fromString("12345678-1234-1234-1234-123456789abd")
private val CLIENT_CONFIG_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

private const val DEFAULT_MTU = BleFraming.DEFAULT_MTU
private const val REQUESTED_MTU = 517
private const val MAX_ADVERTISED_ID_BYTES = 12

private const val MAX_CLIENT_LINKS = 6
private const val CONNECT_TIMEOUT_MS = 15_000L
private const val TIE_BREAK_GRACE_MS = 8_000L
private const val REASSEMBLY_TIMEOUT_MS = 30_000L
private const val SCAN_RESTART_INTERVAL_MS = 10 * 60_000L
private const val MAINTENANCE_INTERVAL_MS = 5_000L

private const val OP_NOT_STARTED = -1
private const val OP_TIMEOUT = -2
private const val OP_DISCONNECTED = -3

/**
 * Pure-BLE transport.
 *
 * Every device runs a GATT server (receives writes, sends notifications) and scans for
 * other servers to connect to as a client. Peers learn each other's mesh device id from
 * the advertisement scan response and a HELLO message, so one physical peer is counted
 * once no matter how many links exist, and either side of a single link can send:
 * the client writes to the characteristic and the server notifies it.
 */
@Singleton
@SuppressLint("MissingPermission")
class BleGattTransport @Inject constructor(
    @ApplicationContext private val context: Context,
    @Named("deviceId") private val localDeviceId: String,
    @Named("deviceName") private val localDeviceName: String
) : Transport {

    override val transportName = "BLE GATT (pure BLE)"
    override val shortName = "BLE"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
    private val adapter get() = bluetoothManager?.adapter

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
    private val serverNotifyMutex = Mutex()
    private var maintenanceJob: Job? = null
    private var lastScanStart = 0L

    @Volatile private var gattServer: BluetoothGattServer? = null
    @Volatile private var serverSosChar: BluetoothGattCharacteristic? = null
    @Volatile private var serviceAdded: CompletableDeferred<Int>? = null

    private val clientLinks = ConcurrentHashMap<String, ClientLink>() // address -> outgoing link
    private val serverLinks = ConcurrentHashMap<String, ServerLink>() // address -> incoming link
    private val retryAfter = ConcurrentHashMap<String, Long>()        // address -> elapsedRealtime
    private val failureCount = ConcurrentHashMap<String, Int>()
    private val firstSeenPeer = ConcurrentHashMap<String, Long>()     // peerId -> elapsedRealtime
    private val framing = BleFraming { SystemClock.elapsedRealtime() }

    override fun isAvailable(): Boolean = TransportCapabilityChecker.isBleAvailable(context)

    override fun unavailableReason(): String? = TransportCapabilityChecker.bleUnavailableReason(context)

    // ── Start / Stop ──────────────────────────────────────────────────────────

    override suspend fun start() {
        lifecycleLock.withLock {
            if (_isRunning.value) return@withLock

            val unavailableReason = unavailableReason()
            if (unavailableReason != null) {
                _lastError.value = unavailableReason
                _peerEvents.tryEmit(PeerEvent.Error(unavailableReason))
                throw IllegalStateException(unavailableReason)
            }

            var advertising = false
            try {
                startGattServer()
                advertising = startAdvertising(includeScanResponse = true)
                if (!startScanning()) throw IllegalStateException("BLE scanner unavailable")
            } catch (e: Throwable) {
                val message = when (e) {
                    is SecurityException -> "BLE start blocked by permissions: ${e.message}"
                    else -> "BLE start failed: ${e.message ?: e.javaClass.simpleName}"
                }
                Log.e(TAG, message, e)
                stopInternal()
                _lastError.value = message
                _peerEvents.tryEmit(PeerEvent.Error(message))
                throw e
            }

            _isRunning.value = true
            _lastError.value = if (advertising) null else "BLE advertising not supported; discovery is one-way"
            maintenanceJob = scope.launch { runMaintenance() }
            log("BLE started as $localDeviceId (advertising=$advertising)")
        }
    }

    override suspend fun stop() {
        lifecycleLock.withLock {
            stopInternal()
        }
        Log.i(TAG, "BLE GATT transport stopped")
    }

    private fun stopInternal() {
        _isRunning.value = false
        maintenanceJob?.cancel()
        maintenanceJob = null
        runCatching { adapter?.bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback) }
        runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }

        clientLinks.values.toList().forEach { link ->
            clientLinks.remove(link.address)
            link.ready = false
            link.pendingOp?.complete(OP_DISCONNECTED)
            link.setupJob?.cancel()
            closeQuietly(link)
        }
        serverLinks.values.toList().forEach { link ->
            link.pendingNotify?.complete(OP_DISCONNECTED)
            runCatching { gattServer?.cancelConnection(link.device) }
        }
        serverLinks.clear()
        runCatching { gattServer?.clearServices() }
        runCatching { gattServer?.close() }
        gattServer = null
        serverSosChar = null

        framing.clear()
        retryAfter.clear()
        failureCount.clear()
        firstSeenPeer.clear()
        updatePeers()
    }

    private suspend fun runMaintenance() {
        while (scope.isActive && _isRunning.value) {
            delay(MAINTENANCE_INTERVAL_MS)
            val now = SystemClock.elapsedRealtime()
            framing.pruneOlderThan(REASSEMBLY_TIMEOUT_MS)
            // Android silently downgrades long-running scans; restart periodically.
            if (now - lastScanStart > SCAN_RESTART_INTERVAL_MS) {
                runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
                if (!startScanning()) {
                    _lastError.value = "BLE scan restart failed"
                }
            }
        }
    }

    // ── GATT Server (other devices connect to us) ─────────────────────────────

    private suspend fun startGattServer() {
        val server = bluetoothManager?.openGattServer(context, gattServerCallback)
            ?: throw IllegalStateException("Unable to open GATT server")
        gattServer = server

        val service = BluetoothGattService(SOS_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val sosChar = BluetoothGattCharacteristic(
            SOS_PACKET_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        sosChar.addDescriptor(
            BluetoothGattDescriptor(
                CLIENT_CONFIG_UUID,
                BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
            )
        )
        service.addCharacteristic(sosChar)

        val added = CompletableDeferred<Int>()
        serviceAdded = added
        if (!server.addService(service)) {
            throw IllegalStateException("Unable to add SOS GATT service")
        }
        val status = withTimeoutOrNull(3_000) { added.await() }
        if (status != null && status != BluetoothGatt.GATT_SUCCESS) {
            throw IllegalStateException("Adding SOS GATT service failed (status=$status)")
        }
        serverSosChar = server.getService(SOS_SERVICE_UUID)?.getCharacteristic(SOS_PACKET_CHAR_UUID) ?: sosChar
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onServiceAdded(status: Int, service: BluetoothGattService) {
            serviceAdded?.complete(status)
        }

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            val address = device.address
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    serverLinks.putIfAbsent(address, ServerLink(device))
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    val link = serverLinks.remove(address)
                    link?.pendingNotify?.complete(OP_DISCONNECTED)
                    link?.peerId?.let { firstSeenPeer.remove(it) }
                    dropReassembly("S:$address")
                    updatePeers()
                }
            }
        }

        override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
            serverLinks.getOrPut(device.address) { ServerLink(device) }.mtu = mtu
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            val accepted = characteristic.uuid == SOS_PACKET_CHAR_UUID && !preparedWrite && offset == 0 && value != null
            if (responseNeeded) {
                val status = if (accepted) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED
                runCatching { gattServer?.sendResponse(device, requestId, status, 0, null) }
            }
            if (!accepted || value == null) return
            serverLinks.putIfAbsent(device.address, ServerLink(device))
            val message = framing.accept("S:${device.address}", value) ?: return
            handleMessage(message, device.address, fromServerSide = true)
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            if (descriptor.uuid == CLIENT_CONFIG_UUID) {
                val link = serverLinks.getOrPut(device.address) { ServerLink(device) }
                link.subscribed = value != null &&
                    value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            }
            if (responseNeeded) {
                runCatching { gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null) }
            }
        }

        override fun onNotificationSent(device: BluetoothDevice, status: Int) {
            serverLinks[device.address]?.pendingNotify?.complete(status)
        }
    }

    // ── Advertising (so others can discover us) ───────────────────────────────

    private fun startAdvertising(includeScanResponse: Boolean): Boolean {
        val advertiser = adapter?.bluetoothLeAdvertiser
        if (advertiser == null) {
            Log.w(TAG, "BLE peripheral mode not supported on this device")
            return false
        }
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addServiceUuid(ParcelUuid(SOS_SERVICE_UUID))
            .build()

        return runCatching {
            if (includeScanResponse) {
                val idBytes = localDeviceId.toByteArray(Charsets.UTF_8).take(MAX_ADVERTISED_ID_BYTES).toByteArray()
                val scanResponse = AdvertiseData.Builder()
                    .setIncludeDeviceName(false)
                    .setIncludeTxPowerLevel(false)
                    .addServiceData(ParcelUuid(SOS_SERVICE_UUID), idBytes)
                    .build()
                advertiser.startAdvertising(settings, data, scanResponse, advertiseCallback)
            } else {
                advertiser.startAdvertising(settings, data, advertiseCallback)
            }
            true
        }.getOrElse {
            Log.e(TAG, "startAdvertising threw: ${it.message}")
            false
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Log.d(TAG, "BLE advertising started")
        }

        override fun onStartFailure(errorCode: Int) {
            when (errorCode) {
                ADVERTISE_FAILED_ALREADY_STARTED -> Unit
                ADVERTISE_FAILED_DATA_TOO_LARGE -> {
                    Log.w(TAG, "Scan response too large, advertising without device id")
                    startAdvertising(includeScanResponse = false)
                }
                else -> {
                    val message = "BLE advertising failed (error $errorCode)"
                    _lastError.value = message
                    _peerEvents.tryEmit(PeerEvent.Error(message))
                }
            }
        }
    }

    // ── Scanning (we discover others) ─────────────────────────────────────────

    private fun startScanning(): Boolean {
        val scanner = adapter?.bluetoothLeScanner ?: return false
        val filters = listOf(
            ScanFilter.Builder().setServiceUuid(ParcelUuid(SOS_SERVICE_UUID)).build()
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .setReportDelay(0)
            .build()
        return runCatching {
            scanner.startScan(filters, settings, scanCallback)
            lastScanStart = SystemClock.elapsedRealtime()
            true
        }.getOrElse {
            Log.e(TAG, "startScan threw: ${it.message}")
            false
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            onDeviceSeen(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { onDeviceSeen(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            if (errorCode == SCAN_FAILED_ALREADY_STARTED) return
            val message = "BLE scan failed (error $errorCode)"
            Log.e(TAG, message)
            _lastError.value = message
            _peerEvents.tryEmit(PeerEvent.Error(message))
            // Force a restart on the next maintenance tick
            lastScanStart = 0L
        }
    }

    private fun onDeviceSeen(result: ScanResult) {
        if (!_isRunning.value) return
        val device = result.device ?: return
        val address = device.address ?: return
        val advertisedId = result.scanRecord
            ?.getServiceData(ParcelUuid(SOS_SERVICE_UUID))
            ?.let { String(it, Charsets.UTF_8).trim() }
            ?.takeIf { it.isNotEmpty() }

        if (advertisedId == localDeviceId) return
        if (clientLinks.containsKey(address)) return
        if (advertisedId != null && hasLinkToPeer(advertisedId)) return
        if (clientLinks.size >= MAX_CLIENT_LINKS) return

        val now = SystemClock.elapsedRealtime()
        if ((retryAfter[address] ?: 0L) > now) return

        if (advertisedId != null && localDeviceId > advertisedId) {
            // Tie-breaker: the device with the smaller id initiates. We still connect
            // after a grace period in case the other side cannot see us.
            val firstSeen = firstSeenPeer.getOrPut(advertisedId) { now }
            if (now - firstSeen < TIE_BREAK_GRACE_MS) return
        }

        connect(device, address, advertisedId)
    }

    private fun hasLinkToPeer(peerId: String): Boolean =
        clientLinks.values.any { it.peerId == peerId } ||
            serverLinks.values.any { it.peerId == peerId }

    // ── GATT Client (we connect to other devices' servers) ────────────────────

    private fun connect(device: BluetoothDevice, address: String, advertisedId: String?) {
        val link = ClientLink(address, advertisedId)
        if (clientLinks.putIfAbsent(address, link) != null) return

        Log.d(TAG, "Connecting to ${advertisedId ?: address}")
        val gatt = try {
            device.connectGatt(context, false, link.callback, BluetoothDevice.TRANSPORT_LE)
        } catch (e: Exception) {
            Log.w(TAG, "connectGatt failed for $address: ${e.message}")
            null
        }
        if (gatt == null) {
            clientLinks.remove(address, link)
            markFailure(address)
            return
        }
        link.gatt = gatt
        link.setupJob = scope.launch {
            delay(CONNECT_TIMEOUT_MS)
            if (!link.connected) dropClientLink(link, "connect timeout")
        }
    }

    private suspend fun setUpClientLink(link: ClientLink) {
        val gatt = link.gatt ?: return
        delay(250) // Some stacks fail service discovery issued right after connecting

        val discoverStatus = link.runOp(10_000) { gatt.discoverServices() }
        val service = gatt.getService(SOS_SERVICE_UUID)
        if (discoverStatus != BluetoothGatt.GATT_SUCCESS || service == null) {
            dropClientLink(link, "SOS service not found (status=$discoverStatus)")
            return
        }

        // Larger MTU = fewer chunks. Failure is fine: we fall back to the default MTU.
        link.runOp(5_000) { gatt.requestMtu(REQUESTED_MTU) }

        val characteristic = service.getCharacteristic(SOS_PACKET_CHAR_UUID)
        if (characteristic == null) {
            dropClientLink(link, "SOS characteristic missing")
            return
        }
        runCatching { gatt.setCharacteristicNotification(characteristic, true) }
        val cccd = characteristic.getDescriptor(CLIENT_CONFIG_UUID)
        if (cccd != null) {
            link.runOp(5_000) {
                writeDescriptorCompat(gatt, cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            }
        }

        if (!link.sendMessage(WireCodec.encodeHello(localDeviceId, localDeviceName))) {
            dropClientLink(link, "HELLO handshake failed")
            return
        }

        // Wait briefly for the server's HELLO so we learn its mesh id when it was not advertised.
        withTimeoutOrNull(3_000) {
            while (link.peerId == null) delay(100)
        }
        val peerId = link.peerId ?: "BLE-${link.address}".also { link.peerId = it }

        if (clientLinks.values.any { it !== link && it.ready && it.peerId == peerId }) {
            dropClientLink(link, "duplicate link to $peerId")
            return
        }

        link.ready = true
        link.everReady = true
        failureCount.remove(link.address)
        updatePeers()
    }

    private fun dropClientLink(link: ClientLink, reason: String) {
        val removed = clientLinks.remove(link.address, link)
        val wasReady = link.ready
        link.ready = false
        link.pendingOp?.complete(OP_DISCONNECTED)
        closeQuietly(link)
        if (!removed) return

        Log.d(TAG, "Client link ${link.peerId ?: link.address} dropped: $reason")
        if (link.everReady) {
            // Was working: allow a quick reconnect.
            retryAfter[link.address] = SystemClock.elapsedRealtime() + 2_000L
        } else {
            markFailure(link.address)
            if (!wasReady) {
                _peerEvents.tryEmit(PeerEvent.ConnectionFailed(link.peerId ?: link.address, reason))
            }
        }
        link.peerId?.let { firstSeenPeer.remove(it) }
        dropReassembly("C:${link.address}")
        updatePeers()
        link.setupJob?.cancel()
    }

    private fun closeQuietly(link: ClientLink) {
        val gatt = link.gatt ?: return
        link.gatt = null
        runCatching { gatt.disconnect() }
        runCatching { gatt.close() }
    }

    private fun markFailure(address: String) {
        val failures = (failureCount[address] ?: 0) + 1
        failureCount[address] = failures
        val backoff = (2_000L shl (failures - 1).coerceAtMost(5)).coerceAtMost(60_000L)
        retryAfter[address] = SystemClock.elapsedRealtime() + backoff
    }

    // ── Sending ───────────────────────────────────────────────────────────────

    override suspend fun broadcastPacket(packet: SosPacket): Result<Set<String>> =
        sendToAllPeers(WireCodec.encodeSos(packet))

    override suspend fun broadcastAck(ack: AckPacket): Result<Set<String>> =
        sendToAllPeers(WireCodec.encodeAck(ack))

    private suspend fun sendToAllPeers(bytes: ByteArray): Result<Set<String>> {
        if (!_isRunning.value) return Result.failure(IllegalStateException("BLE transport not running"))
        val targets = _peers.value.map { it.peerId }
        if (targets.isEmpty()) return Result.failure(IllegalStateException("No BLE peers connected"))

        val delivered = coroutineScope {
            targets.map { peerId ->
                async { if (sendToPeer(peerId, bytes)) peerId else null }
            }.awaitAll().filterNotNull().toSet()
        }
        return if (delivered.isNotEmpty()) Result.success(delivered)
        else Result.failure(IllegalStateException("All BLE sends failed"))
    }

    private suspend fun sendToPeer(peerId: String, bytes: ByteArray): Boolean {
        clientLinks.values.filter { it.ready && it.peerId == peerId }.forEach { link ->
            if (link.sendMessage(bytes)) return true
        }
        serverLinks.values.filter { it.peerId == peerId && it.subscribed }.forEach { link ->
            if (link.sendMessage(bytes)) return true
        }
        return false
    }

    private fun buildFrames(bytes: ByteArray, mtu: Int): List<ByteArray> = framing.buildFrames(bytes, mtu)

    // ── Receiving ─────────────────────────────────────────────────────────────

    private fun dropReassembly(linkKey: String) = framing.dropLink(linkKey)

    private fun onClientNotification(link: ClientLink, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
        if (characteristic.uuid != SOS_PACKET_CHAR_UUID) return
        val message = framing.accept("C:${link.address}", value) ?: return
        handleMessage(message, link.address, fromServerSide = false)
    }

    private fun handleMessage(bytes: ByteArray, address: String, fromServerSide: Boolean) {
        when (val message = WireCodec.decode(bytes)) {
            is WireCodec.Message.Hello -> onHello(message, address, fromServerSide)
            is WireCodec.Message.Sos -> {
                Log.d(TAG, "SOS ${message.packet.id} received from $address")
                _incomingPackets.tryEmit(IncomingPacket(message.packet, peerIdFor(address, fromServerSide)))
            }
            is WireCodec.Message.Ack -> {
                _incomingAcks.tryEmit(IncomingAck(message.ack, peerIdFor(address, fromServerSide)))
            }
            null -> Log.w(TAG, "Dropped undecodable message (${bytes.size} bytes) from $address")
        }
    }

    private fun onHello(hello: WireCodec.Message.Hello, address: String, fromServerSide: Boolean) {
        if (hello.deviceId == localDeviceId) return
        if (fromServerSide) {
            val link = serverLinks[address] ?: return
            val isNew = link.peerId == null
            link.peerId = hello.deviceId
            link.peerName = hello.name
            updatePeers()
            if (isNew) {
                // Reply so the client learns our id even if it could not read our advertisement.
                scope.launch { link.sendMessage(WireCodec.encodeHello(localDeviceId, localDeviceName)) }
            }
        } else {
            val link = clientLinks[address] ?: return
            if (link.peerId == null || link.peerId!!.startsWith("BLE-")) link.peerId = hello.deviceId
            link.peerName = hello.name
            if (link.ready) updatePeers()
        }
    }

    private fun peerIdFor(address: String, fromServerSide: Boolean): String =
        (if (fromServerSide) serverLinks[address]?.peerId else clientLinks[address]?.peerId) ?: address

    // ── Peer bookkeeping ──────────────────────────────────────────────────────

    @Synchronized
    private fun updatePeers() {
        val names = LinkedHashMap<String, String>()
        clientLinks.values.filter { it.ready }.forEach { link ->
            val id = link.peerId ?: return@forEach
            names[id] = link.peerName ?: id
        }
        serverLinks.values.forEach { link ->
            val id = link.peerId ?: return@forEach
            names.putIfAbsent(id, link.peerName ?: id)
        }
        val updated = names.map { (id, name) -> PeerInfo(id, name, setOf(shortName)) }.sortedBy { it.peerId }
        val previous = _peers.value
        if (updated == previous) return
        _peers.value = updated

        val previousIds = previous.map { it.peerId }.toSet()
        val updatedIds = updated.map { it.peerId }.toSet()
        updated.filter { it.peerId !in previousIds }.forEach {
            Log.i(TAG, "Peer ready: ${it.peerId} (${it.name})")
            _peerEvents.tryEmit(PeerEvent.Connected(it.peerId, it.name, shortName))
        }
        (previousIds - updatedIds).forEach {
            Log.i(TAG, "Peer gone: $it")
            _peerEvents.tryEmit(PeerEvent.Disconnected(it, shortName))
        }
    }

    private fun log(message: String) {
        Log.i(TAG, message)
        _peerEvents.tryEmit(PeerEvent.Log(shortName, message))
    }

    // ── API-level compat helpers ──────────────────────────────────────────────

    @Suppress("DEPRECATION")
    private fun writeCharacteristicCompat(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        gatt.writeCharacteristic(
            characteristic,
            value,
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        ) == BluetoothStatusCodes.SUCCESS
    } else {
        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        characteristic.value = value
        gatt.writeCharacteristic(characteristic)
    }

    @Suppress("DEPRECATION")
    private fun writeDescriptorCompat(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        value: ByteArray
    ): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        gatt.writeDescriptor(descriptor, value) == BluetoothStatusCodes.SUCCESS
    } else {
        descriptor.value = value
        gatt.writeDescriptor(descriptor)
    }

    @Suppress("DEPRECATION")
    private fun notifyCompat(
        server: BluetoothGattServer,
        device: BluetoothDevice,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        server.notifyCharacteristicChanged(device, characteristic, false, value) == BluetoothStatusCodes.SUCCESS
    } else {
        characteristic.value = value
        server.notifyCharacteristicChanged(device, characteristic, false)
    }

    // ── Link types ────────────────────────────────────────────────────────────

    /** Outgoing connection: we are the GATT client and write to the peer's server. */
    private inner class ClientLink(val address: String, @Volatile var peerId: String?) {
        @Volatile var gatt: BluetoothGatt? = null
        @Volatile var mtu: Int = DEFAULT_MTU
        @Volatile var connected = false
        @Volatile var ready = false
        @Volatile var everReady = false
        @Volatile var peerName: String? = null
        @Volatile var pendingOp: CompletableDeferred<Int>? = null
        @Volatile var setupJob: Job? = null
        private val opMutex = Mutex()

        /** Runs one GATT operation at a time and waits for its callback status. */
        suspend fun runOp(timeoutMs: Long, start: () -> Boolean): Int = opMutex.withLock {
            val deferred = CompletableDeferred<Int>()
            pendingOp = deferred
            val started = try {
                start()
            } catch (e: Exception) {
                Log.w(TAG, "GATT op failed to start on $address: ${e.message}")
                false
            }
            val status = if (started) withTimeoutOrNull(timeoutMs) { deferred.await() } ?: OP_TIMEOUT else OP_NOT_STARTED
            pendingOp = null
            status
        }

        suspend fun sendMessage(bytes: ByteArray): Boolean {
            val currentGatt = gatt ?: return false
            val characteristic = currentGatt.getService(SOS_SERVICE_UUID)
                ?.getCharacteristic(SOS_PACKET_CHAR_UUID) ?: return false
            val frames = buildFrames(bytes, mtu)
            if (frames.isEmpty()) return false
            for (frame in frames) {
                val status = runOp(5_000) { writeCharacteristicCompat(currentGatt, characteristic, frame) }
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Log.w(TAG, "Write to ${peerId ?: address} failed (status=$status)")
                    if (status == OP_TIMEOUT) dropClientLink(this, "write timeout")
                    return false
                }
            }
            return true
        }

        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                    this@ClientLink.gatt = gatt
                    connected = true
                    setupJob?.cancel()
                    setupJob = scope.launch { setUpClientLink(this@ClientLink) }
                } else {
                    if (this@ClientLink.gatt == null) {
                        runCatching { gatt.close() }
                    }
                    dropClientLink(
                        this@ClientLink,
                        if (status != BluetoothGatt.GATT_SUCCESS) "connection error (status=$status)" else "disconnected"
                    )
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                pendingOp?.complete(status)
            }

            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) this@ClientLink.mtu = mtu
                pendingOp?.complete(status)
            }

            override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                pendingOp?.complete(status)
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                pendingOp?.complete(status)
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray
            ) {
                onClientNotification(this@ClientLink, characteristic, value)
            }

            @Deprecated("Deprecated in Java")
            @Suppress("DEPRECATION")
            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                // API 33+ delivers through the overload above.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
                val value = characteristic.value ?: return
                onClientNotification(this@ClientLink, characteristic, value.copyOf())
            }
        }
    }

    /** Incoming connection: the peer is a GATT client of our server; we reply via notifications. */
    private inner class ServerLink(val device: BluetoothDevice) {
        @Volatile var mtu: Int = DEFAULT_MTU
        @Volatile var peerId: String? = null
        @Volatile var peerName: String? = null
        @Volatile var subscribed = false
        @Volatile var pendingNotify: CompletableDeferred<Int>? = null

        suspend fun sendMessage(bytes: ByteArray): Boolean {
            val frames = buildFrames(bytes, mtu)
            if (frames.isEmpty()) return false
            for (frame in frames) {
                val ok = serverNotifyMutex.withLock {
                    val server = gattServer ?: return@withLock false
                    val characteristic = serverSosChar ?: return@withLock false
                    val deferred = CompletableDeferred<Int>()
                    pendingNotify = deferred
                    val started = runCatching { notifyCompat(server, device, characteristic, frame) }.getOrDefault(false)
                    val status = if (started) withTimeoutOrNull(3_000) { deferred.await() } ?: OP_TIMEOUT else OP_NOT_STARTED
                    pendingNotify = null
                    status == BluetoothGatt.GATT_SUCCESS
                }
                if (!ok) {
                    Log.w(TAG, "Notify to ${peerId ?: device.address} failed")
                    return false
                }
            }
            return true
        }
    }
}
