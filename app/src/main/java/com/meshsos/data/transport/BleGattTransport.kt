package com.meshsos.data.transport

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
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import com.meshsos.domain.model.AckPacket
import com.meshsos.domain.model.PacketType
import com.meshsos.domain.model.SosPacket
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "BleGattTransport"

// Custom UUIDs for our SOS mesh service
private val SOS_SERVICE_UUID: UUID = UUID.fromString("12345678-1234-1234-1234-123456789abc")
private val SOS_PACKET_CHAR_UUID: UUID = UUID.fromString("12345678-1234-1234-1234-123456789abd")
private val ACK_CHAR_UUID: UUID = UUID.fromString("12345678-1234-1234-1234-123456789abe")
private val CLIENT_CONFIG_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

// BLE MTU is negotiated; we assume safe 512 bytes max per chunk
private const val CHUNK_SIZE = 500
private const val CHUNK_HEADER_SIZE = 4 // 2 bytes: chunk index, 2 bytes: total chunks

@Singleton
class BleGattTransport @Inject constructor(
    @ApplicationContext private val context: Context,
) : Transport {

    override val transportName = "BLE GATT (pure BLE fallback)"

    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
    private val bluetoothAdapter = bluetoothManager?.adapter
    private val bleAdvertiser get() = bluetoothAdapter?.bluetoothLeAdvertiser
    private val bleScanner get() = bluetoothAdapter?.bluetoothLeScanner

    private var gattServer: BluetoothGattServer? = null
    private val connectedGattClients = mutableSetOf<BluetoothDevice>()
    private val connectedGatts = mutableMapOf<String, BluetoothGatt>() // address -> gatt

    // Chunk reassembly buffer: deviceAddress -> list of chunks
    private val chunkBuffer = mutableMapOf<String, MutableList<ByteArray>>()

    private val _peerEvents = MutableSharedFlow<PeerEvent>(extraBufferCapacity = 32)
    override val peerEvents: SharedFlow<PeerEvent> = _peerEvents.asSharedFlow()

    private val _incomingPackets = MutableSharedFlow<IncomingPacket>(extraBufferCapacity = 32)
    override val incomingPackets: SharedFlow<IncomingPacket> = _incomingPackets.asSharedFlow()

    private val _incomingAcks = MutableSharedFlow<AckPacket>(extraBufferCapacity = 32)
    override val incomingAcks: SharedFlow<AckPacket> = _incomingAcks.asSharedFlow()

    override fun isAvailable(): Boolean = bluetoothAdapter?.isEnabled == true

    override fun connectedPeerCount(): Int = connectedGattClients.size + connectedGatts.size

    // ── Start / Stop ──────────────────────────────────────────────────────────

    override suspend fun start() {
        startGattServer()
        startAdvertising()
        startScanning()
        Log.i(TAG, "BLE GATT transport started")
    }

    override suspend fun stop() {
        bleAdvertiser?.stopAdvertising(advertiseCallback)
        bleScanner?.stopScan(scanCallback)
        connectedGatts.values.forEach { it.close() }
        connectedGatts.clear()
        gattServer?.close()
        gattServer = null
        connectedGattClients.clear()
        Log.i(TAG, "BLE GATT transport stopped")
    }

    // ── GATT Server (we receive connections from other scanning devices) ───────

    private fun startGattServer() {
        gattServer = bluetoothManager?.openGattServer(context, gattServerCallback)

        val service = BluetoothGattService(SOS_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        // SOS packet characteristic — clients write packets here
        val sosChar = BluetoothGattCharacteristic(
            SOS_PACKET_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or
                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE or
                    BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        val cccd = BluetoothGattDescriptor(
            CLIENT_CONFIG_UUID,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        )
        sosChar.addDescriptor(cccd)

        // ACK characteristic — clients write ACKs here
        val ackChar = BluetoothGattCharacteristic(
            ACK_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or
                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )

        service.addCharacteristic(sosChar)
        service.addCharacteristic(ackChar)
        gattServer?.addService(service)

        Log.d(TAG, "GATT server started with SOS service")
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connectedGattClients.add(device)
                    Log.d(TAG, "GATT client connected: ${device.address}")
                    _peerEvents.tryEmit(PeerEvent.Connected(device.address, device.address))
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    connectedGattClients.remove(device)
                    chunkBuffer.remove(device.address)
                    Log.d(TAG, "GATT client disconnected: ${device.address}")
                    _peerEvents.tryEmit(PeerEvent.Disconnected(device.address))
                }
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }

            when (characteristic.uuid) {
                SOS_PACKET_CHAR_UUID -> handleChunkedWrite(device.address, value, isSos = true)
                ACK_CHAR_UUID -> handleChunkedWrite(device.address, value, isSos = false)
            }
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice, requestId: Int,
            descriptor: BluetoothGattDescriptor, preparedWrite: Boolean,
            responseNeeded: Boolean, offset: Int, value: ByteArray
        ) {
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }
        }
    }

    // ── BLE Advertising (so others can discover us) ───────────────────────────

    private fun startAdvertising() {
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setConnectable(true)
            .setTimeout(0) // Advertise indefinitely
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false) // Save space
            .addServiceUuid(ParcelUuid(SOS_SERVICE_UUID))
            .build()

        bleAdvertiser?.startAdvertising(settings, data, advertiseCallback)
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Log.d(TAG, "BLE advertising started")
        }
        override fun onStartFailure(errorCode: Int) {
            Log.e(TAG, "BLE advertising failed: errorCode=$errorCode")
        }
    }

    // ── BLE Scanning (we discover others) ────────────────────────────────────

    private fun startScanning() {
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SOS_SERVICE_UUID))
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .build()

        bleScanner?.startScan(listOf(filter), settings, scanCallback)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val address = device.address
            if (!connectedGatts.containsKey(address) &&
                !connectedGattClients.any { it.address == address }
            ) {
                Log.d(TAG, "SOS device found via scan: $address — connecting")
                connectToGattServer(device)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE scan failed: errorCode=$errorCode")
        }
    }

    // ── GATT Client (we connect to other devices' GATT servers to send) ───────

    private fun connectToGattServer(device: BluetoothDevice) {
        device.connectGatt(context, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        Log.d(TAG, "GATT connected to ${gatt.device.address}")
                        connectedGatts[gatt.device.address] = gatt
                        gatt.discoverServices()
                        _peerEvents.tryEmit(PeerEvent.Connected(gatt.device.address, gatt.device.address))
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Log.d(TAG, "GATT disconnected from ${gatt.device.address}")
                        connectedGatts.remove(gatt.device.address)
                        gatt.close()
                        _peerEvents.tryEmit(PeerEvent.Disconnected(gatt.device.address))
                    }
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d(TAG, "Services discovered on ${gatt.device.address}")
                    // Negotiate higher MTU for chunked transfers
                    gatt.requestMtu(512)
                }
            }

            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                Log.d(TAG, "MTU changed to $mtu on ${gatt.device.address}")
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Log.w(TAG, "Characteristic write failed on ${gatt.device.address}")
                }
            }
        }, BluetoothDevice.TRANSPORT_LE)
    }

    // ── Chunked send ──────────────────────────────────────────────────────────

    override suspend fun broadcastPacket(packet: SosPacket): Result<Unit> {
        val bytes = packet.toBytes()
        Log.d(TAG, "Broadcasting packet (${bytes.size} bytes) to ${connectedGatts.size} peers")

        var successCount = 0
        for ((address, gatt) in connectedGatts.toMap()) {
            val service = gatt.getService(SOS_SERVICE_UUID) ?: continue
            val char = service.getCharacteristic(SOS_PACKET_CHAR_UUID) ?: continue
            if (writeChunked(gatt, char, bytes)) successCount++
        }

        return if (successCount > 0) Result.success(Unit)
        else if (connectedGatts.isEmpty()) Result.failure(Exception("No connected GATT clients"))
        else Result.failure(Exception("All writes failed"))
    }

    override suspend fun sendAck(ack: AckPacket, toDeviceId: String): Result<Unit> {
        val gatt = connectedGatts[toDeviceId] ?: return Result.failure(Exception("Device not connected: $toDeviceId"))
        val service = gatt.getService(SOS_SERVICE_UUID) ?: return Result.failure(Exception("Service not found"))
        val char = service.getCharacteristic(ACK_CHAR_UUID) ?: return Result.failure(Exception("Char not found"))
        return if (writeChunked(gatt, char, ack.toBytes())) Result.success(Unit)
        else Result.failure(Exception("Write failed"))
    }

    /**
     * Splits [data] into chunks of [CHUNK_SIZE] bytes and writes them sequentially.
     * Each chunk is prefixed with: [chunkIndex: 2 bytes][totalChunks: 2 bytes]
     */
    @Suppress("DEPRECATION")
    private fun writeChunked(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        data: ByteArray
    ): Boolean {
        val chunks = data.toList().chunked(CHUNK_SIZE - CHUNK_HEADER_SIZE).map { it.toByteArray() }
        val total = chunks.size

        for ((index, chunk) in chunks.withIndex()) {
            val framed = ByteArray(chunk.size + CHUNK_HEADER_SIZE)
            framed[0] = (index shr 8).toByte()
            framed[1] = (index and 0xFF).toByte()
            framed[2] = (total shr 8).toByte()
            framed[3] = (total and 0xFF).toByte()
            System.arraycopy(chunk, 0, framed, CHUNK_HEADER_SIZE, chunk.size)

            characteristic.value = framed
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            if (!gatt.writeCharacteristic(characteristic)) {
                Log.w(TAG, "writeCharacteristic failed at chunk $index/$total")
                return false
            }
            // Small delay between chunks to avoid buffer overflow
            Thread.sleep(20)
        }
        return true
    }

    // ── Chunk reassembly ─────────────────────────────────────────────────────

    private fun handleChunkedWrite(fromAddress: String, value: ByteArray, isSos: Boolean) {
        if (value.size < CHUNK_HEADER_SIZE) return

        val chunkIndex = (value[0].toInt() and 0xFF shl 8) or (value[1].toInt() and 0xFF)
        val totalChunks = (value[2].toInt() and 0xFF shl 8) or (value[3].toInt() and 0xFF)
        val payload = value.copyOfRange(CHUNK_HEADER_SIZE, value.size)

        val buffer = chunkBuffer.getOrPut(fromAddress) { mutableListOf() }

        if (chunkIndex == 0) buffer.clear() // Fresh packet start
        buffer.add(payload)

        if (buffer.size == totalChunks) {
            val fullData = buffer.flatMap { it.toList() }.toByteArray()
            chunkBuffer.remove(fromAddress)
            reassembled(fromAddress, fullData, isSos)
        }
    }

    private fun reassembled(fromAddress: String, data: ByteArray, isSos: Boolean) {
        if (isSos) {
            val packet = SosPacket.fromBytes(data)
            if (packet != null && packet.type == PacketType.SOS) {
                Log.d(TAG, "Reassembled SOS packet from $fromAddress id=${packet.id}")
                _incomingPackets.tryEmit(IncomingPacket(packet, fromAddress))
            } else {
                Log.w(TAG, "Failed to deserialize reassembled SOS from $fromAddress")
            }
        } else {
            val ack = AckPacket.fromBytes(data)
            if (ack != null) {
                Log.d(TAG, "Reassembled ACK from $fromAddress id=${ack.id}")
                _incomingAcks.tryEmit(ack)
            }
        }
    }
}
