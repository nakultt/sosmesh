package com.meshsos.presentation.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meshsos.background.MeshForegroundService
import com.meshsos.data.db.dao.MeshEventDao
import com.meshsos.data.db.dao.PendingPacketDao
import com.meshsos.data.db.entity.MeshEventEntity
import com.meshsos.data.transport.TransportManager
import com.meshsos.domain.model.AckPacket
import com.meshsos.domain.model.HelperStatus
import com.meshsos.domain.model.IncidentCategory
import com.meshsos.domain.model.MeshEventType
import com.meshsos.domain.model.Severity
import com.meshsos.domain.model.SosPacket
import com.meshsos.domain.service.AdaptiveScanStrategy
import com.meshsos.domain.service.BatteryMonitor
import com.meshsos.domain.service.DeviceLocationProvider
import com.meshsos.domain.service.PowerMode
import com.meshsos.domain.statemachine.MeshState
import com.meshsos.domain.statemachine.MeshStateMachine
import com.meshsos.domain.usecase.SendSosUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject
import javax.inject.Named

@HiltViewModel
class MeshViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val stateMachine: MeshStateMachine,
    private val transportManager: TransportManager,
    private val sendSosUseCase: SendSosUseCase,
    private val batteryMonitor: BatteryMonitor,
    private val deviceLocationProvider: DeviceLocationProvider,
    private val adaptiveScanStrategy: AdaptiveScanStrategy,
    private val meshEventDao: MeshEventDao,
    private val pendingPacketDao: PendingPacketDao,
    @Named("deviceId") val localDeviceId: String
) : ViewModel() {

    // ── Exposed state ─────────────────────────────────────────────────────────

    val meshState: StateFlow<MeshState> = stateMachine.state
        .stateIn(viewModelScope, SharingStarted.Eagerly, MeshState.Idle)

    val activeTransportName: StateFlow<String> = kotlinx.coroutines.flow.flow {
        transportManager.activeTransport.collect { emit(it.transportName) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, "Initializing…")

    val recentEvents: StateFlow<List<MeshEventEntity>> =
        meshEventDao.getRecentFlow(50)
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val allEvents: StateFlow<List<MeshEventEntity>> =
        meshEventDao.getAllFlow()
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val pendingPacketCount: StateFlow<Int> =
        pendingPacketDao.countFlow()
            .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _sendError = MutableStateFlow<String?>(null)
    val sendError: StateFlow<String?> = _sendError.asStateFlow()

    private val _peerCount = MutableStateFlow(0)
    val peerCount: StateFlow<Int> = _peerCount.asStateFlow()

    private val _activeHelperStatus = MutableStateFlow<HelperStatus?>(null)
    val activeHelperStatus: StateFlow<HelperStatus?> = _activeHelperStatus.asStateFlow()

    private var helperTrackingJob: Job? = null
    private var helperTrackingPacketId: String? = null

    val batteryLevel get() = batteryMonitor.getBatteryLevel()

    // ── Actions ───────────────────────────────────────────────────────────────

    fun startService() {
        MeshForegroundService.start(context)
        startPeerCountPoller()
    }

    fun stopService() {
        MeshForegroundService.stop(context)
    }

    fun sendSos(
        category: IncidentCategory = IncidentCategory.OTHER,
        severity: Severity = Severity.CRITICAL,
        message: String = ""
    ) {
        viewModelScope.launch {
            _isSending.value = true
            _sendError.value = null
            val result = sendSosUseCase.execute(category, severity, message)
            result.onFailure { _sendError.value = it.message }
            _isSending.value = false
        }
    }

    fun resetState() {
        stateMachine.reset()
        stopEnRouteTracking()
        helperTrackingPacketId = null
        _activeHelperStatus.value = null
    }

    fun dismissError() {
        _sendError.value = null
    }

    fun clearLogs() {
        viewModelScope.launch {
            meshEventDao.deleteAll()
        }
    }

    fun canSendLocalHelpUpdate(): Boolean = receivedPacketContext(meshState.value) != null

    fun sendLocalHelpUpdate(etaMinutes: Int = 8) {
        sendHelperStatusUpdate(HelperStatus.ACCEPTED, etaMinutes)
    }

    fun sendHelperStatusUpdate(
        status: HelperStatus,
        etaMinutes: Int = 8
    ) {
        viewModelScope.launch {
            val context = receivedPacketContext(meshState.value)
            if (context == null) {
                _sendError.value = "No received SOS available for helper update."
                return@launch
            }
            val result = sendHelperUpdateAck(context, status, etaMinutes, isBackgroundTick = false)
            result.onSuccess {
                helperTrackingPacketId = context.packet.id
                _activeHelperStatus.value = status
                when (status) {
                    HelperStatus.EN_ROUTE -> startEnRouteTracking(etaMinutes)
                    HelperStatus.REACHED, HelperStatus.CANNOT_CONTINUE -> stopEnRouteTracking()
                    HelperStatus.ACCEPTED -> Unit
                }
            }.onFailure {
                _sendError.value = "Failed to send helper update: ${it.message ?: "unknown error"}"
            }
        }
    }

    // ── Peer count polling ────────────────────────────────────────────────────

    private fun startPeerCountPoller() {
        viewModelScope.launch {
            while (true) {
                _peerCount.value = transportManager.connectedPeerCount()
                val interval = adaptiveScanStrategy.getScanIntervalMs(
                    batteryLevel,
                    when (meshState.value) {
                        is MeshState.Originator -> PowerMode.EMERGENCY
                        is MeshState.Relay, is MeshState.Uploading -> PowerMode.RELAY
                        else -> PowerMode.IDLE
                    }
                )
                delay(interval.coerceAtMost(5_000)) // poll UI max every 5s
            }
        }
    }

    private suspend fun sendHelperUpdateAck(
        context: ReceivedPacketContext,
        status: HelperStatus,
        etaMinutes: Int,
        isBackgroundTick: Boolean
    ): Result<Unit> {
        val timestamp = Instant.now().epochSecond
        val location = deviceLocationProvider.getCurrentLocation()
        val etaText = when (status) {
            HelperStatus.ACCEPTED -> "Accepted (ETA ~${etaMinutes} min)"
            HelperStatus.EN_ROUTE -> "En route (ETA ~${etaMinutes} min)"
            HelperStatus.REACHED -> "Reached victim location"
            HelperStatus.CANNOT_CONTINUE -> "Cannot continue"
        }

        val ack = AckPacket(
            originalPacketId = context.packet.id,
            alertId = "${AckPacket.LOCAL_HELP_ALERT_PREFIX}$localDeviceId",
            uploadedBy = localDeviceId,
            respondersNotified = 1,
            estimatedArrival = etaText,
            helperStatus = status,
            helperLocation = location,
            helperTimestamp = timestamp
        )

        val sendResult = transportManager.sendAck(ack, context.replyToDeviceId)
        sendResult.onSuccess {
            val statusText = status.name.replace('_', ' ')
            val modeText = if (isBackgroundTick) "live tick" else "manual"
            meshEventDao.insert(
                MeshEventEntity(
                    timestamp = timestamp,
                    eventType = MeshEventType.ACK_RECEIVED.name,
                    message = "Helper update [$statusText] ($modeText) for packet ${context.packet.id.take(8)}...",
                    packetId = context.packet.id,
                    deviceId = localDeviceId
                )
            )
        }
        return sendResult
    }

    private fun startEnRouteTracking(etaMinutes: Int) {
        stopEnRouteTracking()
        helperTrackingJob = viewModelScope.launch {
            while (isActive && _activeHelperStatus.value == HelperStatus.EN_ROUTE) {
                delay(15_000)
                val context = receivedPacketContext(meshState.value)
                val trackedPacketId = helperTrackingPacketId
                if (
                    context == null ||
                    trackedPacketId == null ||
                    context.packet.id != trackedPacketId
                ) {
                    stopEnRouteTracking()
                    helperTrackingPacketId = null
                    _activeHelperStatus.value = null
                    return@launch
                }
                val result = sendHelperUpdateAck(
                    context = context,
                    status = HelperStatus.EN_ROUTE,
                    etaMinutes = etaMinutes,
                    isBackgroundTick = true
                )
                result.onFailure {
                    _sendError.value = "Live tracking update failed: ${it.message ?: "unknown error"}"
                }
            }
        }
    }

    private fun stopEnRouteTracking() {
        helperTrackingJob?.cancel()
        helperTrackingJob = null
    }

    override fun onCleared() {
        stopEnRouteTracking()
        super.onCleared()
    }

    private data class ReceivedPacketContext(
        val packet: SosPacket,
        val replyToDeviceId: String
    )

    private fun receivedPacketContext(state: MeshState): ReceivedPacketContext? = when (state) {
        is MeshState.Relay -> ReceivedPacketContext(state.packet, state.receivedFromDevice)
        is MeshState.Uploading -> state.receivedFromDevice?.let { ReceivedPacketContext(state.packet, it) }
        is MeshState.AwaitingAck -> {
            val packet = state.packet
            val fromDevice = state.receivedFromDevice
            if (packet != null && fromDevice != null) ReceivedPacketContext(packet, fromDevice) else null
        }
        else -> null
    }
}
