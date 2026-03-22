package com.meshsos.background

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.meshsos.R
import com.meshsos.data.db.dao.MeshEventDao
import com.meshsos.data.db.entity.MeshEventEntity
import com.meshsos.data.transport.TransportManager
import com.meshsos.domain.model.MeshEventType
import com.meshsos.domain.service.AdaptiveScanStrategy
import com.meshsos.domain.service.BatteryMonitor
import com.meshsos.domain.service.PowerMode
import com.meshsos.domain.statemachine.MeshState
import com.meshsos.domain.statemachine.MeshStateMachine
import com.meshsos.domain.usecase.UploadPacketUseCase
import com.meshsos.presentation.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

private const val TAG = "MeshForegroundService"
private const val CHANNEL_ID = "mesh_sos_channel"
private const val NOTIFICATION_ID = 1001
private const val RETRY_PENDING_INTERVAL_MS = 60_000L // 1 min

@AndroidEntryPoint
class MeshForegroundService : LifecycleService() {

    @Inject lateinit var transportManager: TransportManager
    @Inject lateinit var stateMachine: MeshStateMachine
    @Inject lateinit var uploadUseCase: UploadPacketUseCase
    @Inject lateinit var meshEventDao: MeshEventDao
    @Inject lateinit var batteryMonitor: BatteryMonitor
    @Inject lateinit var adaptiveScanStrategy: AdaptiveScanStrategy
    @Inject lateinit var localDeviceId: String

    private var wakeLock: PowerManager.WakeLock? = null
    private var retryJob: Job? = null

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Starting mesh relay…"))
        acquireWakeLock()
        Log.i(TAG, "MeshForegroundService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_STOP -> stopSelf()
            else -> {
                launchMesh()
                launchLogCollector()
                launchRetryWorker()
                launchNotificationUpdater()
            }
        }

        return START_STICKY // Restart if killed by OS
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleScope.launch(Dispatchers.IO) {
            transportManager.stop()
        }
        wakeLock?.release()
        Log.i(TAG, "MeshForegroundService destroyed")
    }

    // ── Mesh startup ──────────────────────────────────────────────────────────

    private fun launchMesh() {
        lifecycleScope.launch(Dispatchers.IO) {
            transportManager.start()
            Log.i(TAG, "Transport started: ${transportManager.activeTransport.value.transportName}")

            // Wire incoming packets → state machine
            launch {
                transportManager.activeTransport.collectLatest { transport ->
                    launch {
                        transport.incomingPackets.collect { incoming ->
                            stateMachine.onPacketReceived(this, incoming.packet, incoming.fromDevice)
                        }
                    }
                    launch {
                        transport.incomingAcks.collect { ack ->
                            stateMachine.onAckReceived(ack)
                        }
                    }
                    launch {
                        transport.peerEvents.collect { event ->
                            when (event) {
                                is com.meshsos.data.transport.PeerEvent.Connected ->
                                    stateMachine.onPeerConnected(event.deviceId)
                                is com.meshsos.data.transport.PeerEvent.Disconnected ->
                                    stateMachine.onPeerDisconnected(event.deviceId)
                                else -> {}
                            }
                        }
                    }
                }
            }

            // Wire state machine forward callback → transport
            stateMachine.setForwardCallback { packet ->
                lifecycleScope.launch(Dispatchers.IO) {
                    transportManager.broadcastPacket(packet)
                }
            }
        }
    }

    // ── Event log collector ───────────────────────────────────────────────────

    private fun launchLogCollector() {
        lifecycleScope.launch(Dispatchers.IO) {
            stateMachine.logEvents.collect { event ->
                meshEventDao.insert(
                    MeshEventEntity(
                        timestamp = event.timestamp,
                        eventType = event.type.name,
                        message = event.message,
                        packetId = event.packetId,
                        deviceId = event.deviceId
                    )
                )
                // Purge events older than 24h to keep DB size in check
                meshEventDao.deleteOlderThan(Instant.now().epochSecond - 86400)
            }
        }
    }

    // ── Retry pending uploads every minute ───────────────────────────────────

    private fun launchRetryWorker() {
        retryJob?.cancel()
        retryJob = lifecycleScope.launch(Dispatchers.IO) {
            while (true) {
                delay(RETRY_PENDING_INTERVAL_MS)
                if (uploadUseCase.hasInternet()) {
                    Log.d(TAG, "Retrying pending packet uploads…")
                    uploadUseCase.retryPending()
                }
            }
        }
    }

    // ── Dynamic notification updates ──────────────────────────────────────────

    private fun launchNotificationUpdater() {
        lifecycleScope.launch {
            combine(
                stateMachine.state,
                transportManager.activeTransport
            ) { state, transport ->
                Pair(state, transport)
            }.collect { (state, transport) ->
                val statusText = when (state) {
                    is MeshState.Idle -> "Listening • ${transport.connectedPeerCount()} peers"
                    is MeshState.Originator -> "SOS sent • ${state.peersReached} peers reached"
                    is MeshState.Relay -> "Relaying SOS (hop ${state.packet.metadata.currentHops})"
                    is MeshState.Uploading -> "Uploading SOS to server…"
                    is MeshState.AwaitingAck -> "Awaiting confirmation…"
                    is MeshState.Confirmed -> "SOS confirmed ✓"
                    is MeshState.Error -> "Error: ${state.reason}"
                }
                updateNotification(statusText)
            }
        }
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Mesh SOS Relay",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps the emergency mesh relay running in background"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(status: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
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
            .setContentIntent(openIntent)
            .addAction(0, "Stop", stopIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(status: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(status))
    }

    // ── WakeLock ──────────────────────────────────────────────────────────────

    private fun acquireWakeLock() {
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "MeshSOS::RelayWakeLock"
        ).also { it.acquire(10 * 60 * 1000L) } // 10 min max, re-acquired by sticky restart
    }

    companion object {
        const val ACTION_STOP = "com.meshsos.ACTION_STOP"

        fun start(context: Context) {
            val intent = Intent(context, MeshForegroundService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, MeshForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
