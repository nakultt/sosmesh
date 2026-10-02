package com.meshsos.background

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.meshsos.R
import com.meshsos.data.settings.MeshSettings
import com.meshsos.data.transport.PeerEvent
import com.meshsos.data.transport.TransportCapabilityChecker
import com.meshsos.data.transport.TransportManager
import com.meshsos.domain.model.MeshEventType
import com.meshsos.domain.model.SosPacket
import com.meshsos.domain.service.MeshEventLogger
import com.meshsos.domain.statemachine.MeshState
import com.meshsos.domain.statemachine.MeshStateMachine
import com.meshsos.domain.usecase.UploadPacketUseCase
import com.meshsos.presentation.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

private const val TAG = "MeshForegroundService"
private const val CHANNEL_ID = "mesh_sos_channel"
private const val ALERT_CHANNEL_ID = "mesh_sos_alerts"
private const val NOTIFICATION_ID = 1001
private const val ALERT_NOTIFICATION_BASE_ID = 2000
private const val RETRY_PENDING_INTERVAL_MS = 60_000L
private const val REBROADCAST_INTERVAL_MS = 45_000L
private const val WAKE_LOCK_TIMEOUT_MS = 10 * 60 * 1000L
private const val WAKE_LOCK_RENEW_MS = 9 * 60 * 1000L

@AndroidEntryPoint
class MeshForegroundService : LifecycleService() {

    @Inject lateinit var transportManager: TransportManager
    @Inject lateinit var stateMachine: MeshStateMachine
    @Inject lateinit var uploadUseCase: UploadPacketUseCase
    @Inject lateinit var eventLogger: MeshEventLogger
    @Inject lateinit var meshSettings: MeshSettings

    private var wakeLock: PowerManager.WakeLock? = null
    private var lastStatusText = "Starting mesh relay…"
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        if (!startInForeground()) {
            stopSelf()
            return
        }
        _isRunning.value = true
        acquireWakeLock()

        // Wire everything exactly once, independent of whether a transport can start yet.
        // TransportManager keeps retrying transports as Bluetooth/permissions become available.
        launchPacketPipeline()
        launchMeshStartup()
        launchRetryWorker()
        launchRebroadcastWorker()
        launchNotificationUpdater()
        launchAlertNotifier()
        launchWakeLockRenewer()
        registerNetworkCallback()
        Log.i(TAG, "MeshForegroundService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> {
                meshSettings.serviceEnabled = false
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_REFRESH -> {
                // Every startForegroundService() call must be answered with startForeground().
                startInForeground()
                transportManager.reconcileAsync()
            }
            else -> startInForeground()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        _isRunning.value = false
        unregisterNetworkCallback()
        // lifecycleScope is cancelled by super.onDestroy(), so stop on the manager's own scope.
        transportManager.stopAsync()
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        super.onDestroy()
        Log.i(TAG, "MeshForegroundService destroyed")
    }

    // ── Foreground start ──────────────────────────────────────────────────────

    /**
     * Android 14+ requires the runtime permissions behind each declared foreground service
     * type. connectedDevice is always allowed (CHANGE_NETWORK_STATE); location is only added
     * when location permission is granted, and dropped if the system refuses it (e.g. boot).
     */
    private fun startInForeground(): Boolean {
        val notification = buildNotification(lastStatusText)
        val connectedDevice = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        } else 0
        val location = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            TransportCapabilityChecker.hasLocationPermission(this)
        ) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0

        val attempts = listOf(connectedDevice or location, connectedDevice).distinct()
        for (type in attempts) {
            try {
                ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
                return true
            } catch (e: Exception) {
                Log.w(TAG, "startForeground(type=$type) failed: ${e.message}")
            }
        }
        return false
    }

    // ── Mesh wiring ───────────────────────────────────────────────────────────

    private fun launchPacketPipeline() {
        lifecycleScope.launch(Dispatchers.IO) {
            transportManager.incomingPackets.collect { incoming ->
                stateMachine.onPacketReceived(incoming.packet, incoming.fromDevice)
            }
        }
        lifecycleScope.launch(Dispatchers.IO) {
            transportManager.incomingAcks.collect { incoming ->
                stateMachine.onAckReceived(incoming.ack, incoming.fromDevice)
            }
        }
        lifecycleScope.launch(Dispatchers.IO) {
            transportManager.peerEvents.collect { event ->
                when (event) {
                    is PeerEvent.Connected ->
                        stateMachine.onPeerConnected(event.deviceId, event.endpointName, event.transport)
                    is PeerEvent.Disconnected ->
                        stateMachine.onPeerDisconnected(event.deviceId, event.transport)
                    is PeerEvent.ConnectionFailed -> eventLogger.log(
                        MeshEventType.ERROR,
                        "Connection to ${event.deviceId} failed: ${event.reason}",
                        deviceId = event.deviceId
                    )
                    is PeerEvent.Error -> eventLogger.log(MeshEventType.ERROR, event.message)
                    is PeerEvent.Log -> eventLogger.log(
                        MeshEventType.TRANSPORT_SWITCHED,
                        "[${event.tag}] ${event.message}"
                    )
                }
            }
        }
    }

    private fun launchMeshStartup() {
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { transportManager.start() }
                .onSuccess {
                    eventLogger.log(
                        MeshEventType.TRANSPORT_SWITCHED,
                        "Mesh started: ${transportManager.activeTransportLabel.value}"
                    )
                }
                .onFailure { error ->
                    Log.e(TAG, "Mesh start failed: ${error.message}", error)
                    eventLogger.log(MeshEventType.ERROR, "Mesh start failed: ${error.message}")
                }
        }
    }

    // ── Upload retry ──────────────────────────────────────────────────────────

    private fun launchRetryWorker() {
        lifecycleScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(RETRY_PENDING_INTERVAL_MS)
                retryPendingUploads()
            }
        }
    }

    private suspend fun retryPendingUploads() {
        if (!uploadUseCase.hasInternet()) return
        val uploaded = runCatching { uploadUseCase.retryPending() }.getOrDefault(emptyList())
        uploaded.forEach { (packet, response) -> stateMachine.onUploadSucceeded(packet, response) }
    }

    private fun registerNetworkCallback() {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                    lifecycleScope.launch(Dispatchers.IO) { retryPendingUploads() }
                }
            }
        }
        runCatching {
            cm.registerDefaultNetworkCallback(callback)
            networkCallback = callback
        }.onFailure { Log.w(TAG, "Network callback registration failed: ${it.message}") }
    }

    private fun unregisterNetworkCallback() {
        val callback = networkCallback ?: return
        runCatching { getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(callback) }
        networkCallback = null
    }

    // ── Store-and-forward safety net ──────────────────────────────────────────

    private fun launchRebroadcastWorker() {
        lifecycleScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(REBROADCAST_INTERVAL_MS)
                if (stateMachine.hasActivePackets() && transportManager.connectedPeerCount() > 0) {
                    runCatching { stateMachine.rebroadcastActivePackets() }
                }
            }
        }
    }

    // ── Notifications ─────────────────────────────────────────────────────────

    private fun launchNotificationUpdater() {
        lifecycleScope.launch {
            combine(
                stateMachine.state,
                transportManager.peers,
                transportManager.activeTransportLabel
            ) { state, peers, transport ->
                when (state) {
                    is MeshState.Idle -> "Listening • ${peers.size} peer(s) • $transport"
                    is MeshState.Originator -> "SOS active • reached ${state.peersReached} peer(s)"
                    is MeshState.Relay -> "Relaying SOS (hop ${state.packet.metadata.currentHops}) • ${peers.size} peer(s)"
                    is MeshState.Uploading -> "Uploading SOS to server…"
                    is MeshState.AwaitingAck -> "SOS uploaded • alert ${state.alertId}"
                    is MeshState.Confirmed -> "SOS confirmed ✓ • responders notified"
                    is MeshState.Error -> "Error: ${state.reason}"
                }
            }.distinctUntilChanged().collect { updateNotification(it) }
        }
    }

    private fun launchAlertNotifier() {
        lifecycleScope.launch {
            var counter = 0
            stateMachine.newAlerts.collect { packet ->
                showAlertNotification(packet, ALERT_NOTIFICATION_BASE_ID + (counter++ % 20))
            }
        }
    }

    private fun showAlertNotification(packet: SosPacket, notificationId: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return

        val incident = packet.incident
        val category = incident.category.name.replace('_', ' ').lowercase(Locale.US)
            .replaceFirstChar { it.titlecase(Locale.US) }
        val location = incident.location?.let {
            it.address.ifBlank { String.format(Locale.US, "%.5f, %.5f", it.lat, it.lng) }
        } ?: "location unknown"
        val body = buildString {
            append("$category emergency nearby ($location)")
            if (incident.message.isNotBlank()) append(" — ${incident.message}")
        }
        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mesh_notification)
            .setContentTitle("SOS received from a nearby device")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()
        runCatching { NotificationManagerCompat.from(this).notify(notificationId, notification) }
    }

    private fun createNotificationChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_LOW).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(ALERT_CHANNEL_ID, "Nearby SOS alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Alerts when someone nearby sends an SOS through the mesh"
                enableVibration(true)
            }
        )
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun buildNotification(status: String): Notification {
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, MeshForegroundService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MeshSOS Active")
            .setContentText(status)
            .setSmallIcon(R.drawable.ic_mesh_notification)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent())
            .addAction(0, "Stop", stopIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(status: String) {
        lastStatusText = status
        runCatching {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(status))
        }
    }

    // ── WakeLock ──────────────────────────────────────────────────────────────

    private fun acquireWakeLock() {
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MeshSOS::RelayWakeLock").apply {
            setReferenceCounted(false)
            acquire(WAKE_LOCK_TIMEOUT_MS)
        }
    }

    /** Timed wake locks expire; renew while the service runs (only matters with an active SOS). */
    private fun launchWakeLockRenewer() {
        lifecycleScope.launch {
            while (isActive) {
                delay(WAKE_LOCK_RENEW_MS)
                val busy = stateMachine.state.value !is MeshState.Idle || stateMachine.hasActivePackets()
                wakeLock?.let { lock ->
                    if (busy) lock.acquire(WAKE_LOCK_TIMEOUT_MS)
                    else if (lock.isHeld) lock.release()
                }
            }
        }
    }

    companion object {
        const val ACTION_STOP = "com.meshsos.ACTION_STOP"
        const val ACTION_REFRESH = "com.meshsos.ACTION_REFRESH"

        private val _isRunning = MutableStateFlow(false)
        /** Whether the relay service is currently running (for the Settings screen). */
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        fun start(context: Context) {
            MeshSettings.setServiceEnabled(context, true)
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, MeshForegroundService::class.java))
            }.onFailure { Log.w(TAG, "Unable to start service: ${it.message}") }
        }

        fun stop(context: Context) {
            MeshSettings.setServiceEnabled(context, false)
            context.stopService(Intent(context, MeshForegroundService::class.java))
        }

        /** Starts the service if needed and re-evaluates transports (e.g. after permissions change). */
        fun refresh(context: Context) {
            if (!MeshSettings.isServiceEnabled(context)) return
            val intent = Intent(context, MeshForegroundService::class.java).apply { action = ACTION_REFRESH }
            runCatching { ContextCompat.startForegroundService(context, intent) }
                .onFailure { Log.w(TAG, "Unable to refresh service: ${it.message}") }
        }
    }
}
