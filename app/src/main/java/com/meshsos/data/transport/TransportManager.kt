package com.meshsos.data.transport

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "TransportManager"

@Singleton
class TransportManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val nearbyTransport: NearbyConnectionsTransport,
    private val bleGattTransport: BleGattTransport
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _activeTransport = MutableStateFlow<Transport>(nearbyTransport)
    val activeTransport: StateFlow<Transport> = _activeTransport.asStateFlow()

    val currentTransport get() = _activeTransport.value

    private var isRunning = false

    // ── Start with best available transport ───────────────────────────────────

    suspend fun start() {
        isRunning = true
        registerWifiP2pReceiver()
        val transport = selectBestTransport()
        switchTo(transport)
    }

    suspend fun stop() {
        isRunning = false
        try {
            context.unregisterReceiver(wifiP2pReceiver)
        } catch (e: Exception) { /* not registered */ }
        _activeTransport.value.stop()
    }

    // ── Transport selection ───────────────────────────────────────────────────

    private fun selectBestTransport(): Transport {
        val wifiP2pAvailable = isWifiDirectAvailable()
        Log.i(TAG, "WiFi Direct available: $wifiP2pAvailable")
        return if (wifiP2pAvailable) {
            Log.i(TAG, "Selecting NearbyConnections (WiFi Direct + BLE)")
            nearbyTransport
        } else {
            Log.i(TAG, "Selecting BLE GATT fallback (pure BLE)")
            bleGattTransport
        }
    }

    private suspend fun switchTo(newTransport: Transport) {
        val current = _activeTransport.value
        if (current.transportName == newTransport.transportName) return

        Log.i(TAG, "Switching transport: ${current.transportName} -> ${newTransport.transportName}")
        current.stop()
        _activeTransport.value = newTransport
        newTransport.start()
    }

    private fun isWifiDirectAvailable(): Boolean {
        val wifiP2pManager = context.getSystemService(WifiP2pManager::class.java)
        return wifiP2pManager != null
    }

    // ── WiFi P2P state changes (switch transport if WiFi goes away) ───────────

    private val wifiP2pReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION) {
                val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                val wifiEnabled = state == WifiP2pManager.WIFI_P2P_STATE_ENABLED
                Log.d(TAG, "WiFi P2P state changed: enabled=$wifiEnabled")

                if (!isRunning) return

                scope.launch {
                    val bestTransport = if (wifiEnabled) nearbyTransport else bleGattTransport
                    if (_activeTransport.value.transportName != bestTransport.transportName) {
                        Log.i(TAG, "WiFi state change → switching transport to ${bestTransport.transportName}")
                        switchTo(bestTransport)
                    }
                }
            }
        }
    }

    private fun registerWifiP2pReceiver() {
        val filter = IntentFilter(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        context.registerReceiver(wifiP2pReceiver, filter)
    }

    // ── Delegate send methods ─────────────────────────────────────────────────

    suspend fun broadcastPacket(packet: com.meshsos.domain.model.SosPacket): Result<Unit> =
        _activeTransport.value.broadcastPacket(packet)

    suspend fun sendAck(ack: com.meshsos.domain.model.AckPacket, toDeviceId: String): Result<Unit> =
        _activeTransport.value.sendAck(ack, toDeviceId)

    fun connectedPeerCount(): Int = _activeTransport.value.connectedPeerCount()
}
