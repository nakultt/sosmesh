package com.meshsos.presentation.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meshsos.background.MeshForegroundService
import com.meshsos.data.db.dao.MeshEventDao
import com.meshsos.data.db.dao.PendingPacketDao
import com.meshsos.data.db.entity.MeshEventEntity
import com.meshsos.data.settings.MeshSettings
import com.meshsos.data.transport.PeerInfo
import com.meshsos.data.transport.TransportCapabilityChecker
import com.meshsos.data.transport.TransportManager
import com.meshsos.data.transport.TransportStatus
import com.meshsos.domain.model.AckPacket
import com.meshsos.domain.model.HelperStatus
import com.meshsos.domain.model.IncidentCategory
import com.meshsos.domain.model.MeshEventType
import com.meshsos.domain.model.Severity
import com.meshsos.domain.model.SosPacket
import com.meshsos.domain.service.BatteryMonitor
import com.meshsos.domain.service.DeviceLocationProvider
import com.meshsos.domain.service.MeshEventLogger
import com.meshsos.domain.statemachine.MeshState
import com.meshsos.domain.statemachine.MeshStateMachine
import com.meshsos.domain.statemachine.ReceivedAlert
import com.meshsos.domain.usecase.SendSosUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject
import javax.inject.Named

/** What is preventing the mesh from working, for the readiness banner. */
data class MeshReadiness(
    val missingPermissions: List<String> = emptyList(),
    val locationPermissionGranted: Boolean = true,
    val notificationsAllowed: Boolean = true,
    val bluetoothSupported: Boolean = true,
    val bluetoothEnabled: Boolean = true,
    val locationServicesRequired: Boolean = false,
    val locationServicesEnabled: Boolean = true
) {
    val meshReady: Boolean
        get() = missingPermissions.isEmpty() && bluetoothSupported && bluetoothEnabled &&
            (!locationServicesRequired || locationServicesEnabled)
}

@HiltViewModel
class MeshViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val stateMachine: MeshStateMachine,
    private val transportManager: TransportManager,
    private val sendSosUseCase: SendSosUseCase,
    private val batteryMonitor: BatteryMonitor,
    private val deviceLocationProvider: DeviceLocationProvider,
    private val meshEventDao: MeshEventDao,
    private val pendingPacketDao: PendingPacketDao,
    private val eventLogger: MeshEventLogger,
    private val meshSettings: MeshSettings,
    @Named("deviceId") val localDeviceId: String,
    @Named("deviceName") val localDeviceName: String
) : ViewModel() {

    // ── Exposed state ─────────────────────────────────────────────────────────

    val meshState: StateFlow<MeshState> = stateMachine.state

    val receivedAlerts: StateFlow<List<ReceivedAlert>> = stateMachine.receivedAlerts

    val peers: StateFlow<List<PeerInfo>> = transportManager.peers

    val peerCount: StateFlow<Int> = transportManager.peers
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** e.g. "Nearby + BLE", "BLE", or "Offline" */
    val activeTransportName: StateFlow<String> = transportManager.activeTransportLabel

    val transportStatuses: StateFlow<List<TransportStatus>> = transportManager.transportStatuses

    val serviceRunning: StateFlow<Boolean> = MeshForegroundService.isRunning

    val autoRelayEnabled: StateFlow<Boolean> = meshSettings.autoRelayEnabled

    val recentEvents: StateFlow<List<MeshEventEntity>> =
        meshEventDao.getRecentFlow(100)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allEvents: StateFlow<List<MeshEventEntity>> =
        meshEventDao.getRecentFlow(500)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pendingPacketCount: StateFlow<Int> =
        pendingPacketDao.countFlow()
            .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _sendError = MutableStateFlow<String?>(null)
    val sendError: StateFlow<String?> = _sendError.asStateFlow()

    private val _activeHelperStatus = MutableStateFlow<HelperStatus?>(null)
    val activeHelperStatus: StateFlow<HelperStatus?> = _activeHelperStatus.asStateFlow()

    private val _readiness = MutableStateFlow(computeReadiness())
    val readiness: StateFlow<MeshReadiness> = _readiness.asStateFlow()

    private val _batteryLevel = MutableStateFlow(batteryMonitor.getBatteryLevel())
    val batteryLevel: StateFlow<Int> = _batteryLevel.asStateFlow()

    private var helperTrackingJob: Job? = null
    private var helperTrackingPacketId: String? = null

    init {
        // Cheap periodic refresh of things the system does not push to us.
        viewModelScope.launch {
            while (isActive) {
                refreshReadiness()
                _batteryLevel.value = batteryMonitor.getBatteryLevel()
                delay(3_000)
            }
        }
    }

    // ── Service control ───────────────────────────────────────────────────────

    fun startService() {
        MeshForegroundService.start(context)
    }

    /** Starts the service when enabled and re-evaluates transports (permissions/radios changed). */
    fun refreshService() {
        refreshReadiness()
        MeshForegroundService.refresh(context)
    }

    fun stopService() {
        MeshForegroundService.stop(context)
    }

    fun setAutoRelayEnabled(enabled: Boolean) {
        viewModelScope.launch { meshSettings.setAutoRelayEnabled(enabled) }
    }

    fun refreshReadiness() {
        _readiness.value = computeReadiness()
    }

    private fun computeReadiness(): MeshReadiness {
        val checker = TransportCapabilityChecker
        val notificationsAllowed = androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
        return MeshReadiness(
            missingPermissions = checker.missingMeshPermissions(context),
            locationPermissionGranted = checker.hasLocationPermission(context),
            notificationsAllowed = notificationsAllowed,
            bluetoothSupported = checker.isBluetoothSupported(context),
            bluetoothEnabled = checker.isBluetoothEnabled(context),
            locationServicesRequired = checker.bleNeedsLocationServices() || checker.nearbyNeedsLocationServices(),
            locationServicesEnabled = checker.isLocationServicesEnabled(context)
        )
    }

    // ── SOS ───────────────────────────────────────────────────────────────────

    fun sendSos(
        category: IncidentCategory = IncidentCategory.OTHER,
        severity: Severity = Severity.CRITICAL,
        message: String = ""
    ) {
        if (_isSending.value) return
        val current = meshState.value
        if (current is MeshState.Originator) {
            _sendError.value = "An SOS is already active. Cancel it before sending a new one."
            return
        }
        _isSending.value = true
        _sendError.value = null
        // Make sure the relay is running so the SOS can actually leave the device.
        MeshForegroundService.start(context)
        viewModelScope.launch {
            try {
                sendSosUseCase.execute(category, severity, message)
                    .onFailure { _sendError.value = "Failed to send SOS: ${it.message}" }
            } finally {
                _isSending.value = false
            }
        }
    }

    /** Cancels an active SOS, or dismisses the current card, and returns to listening. */
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
        viewModelScope.launch { meshEventDao.deleteAll() }
    }

    fun clearReceivedAlerts() {
        stateMachine.clearReceivedAlerts()
    }

    // ── Helper workflow ───────────────────────────────────────────────────────

    fun canSendLocalHelpUpdate(): Boolean = receivedPacket(meshState.value) != null

    fun sendHelperStatusUpdate(status: HelperStatus, etaMinutes: Int = 8) {
        viewModelScope.launch {
            val packet = receivedPacket(meshState.value)
            if (packet == null) {
                _sendError.value = "No received SOS available for helper update."
                return@launch
            }
            sendHelperUpdateAck(packet, status, etaMinutes, isBackgroundTick = false)
                .onSuccess {
                    helperTrackingPacketId = packet.id
                    _activeHelperStatus.value = status
                    when (status) {
                        HelperStatus.EN_ROUTE -> startEnRouteTracking(etaMinutes)
                        HelperStatus.REACHED, HelperStatus.CANNOT_CONTINUE -> stopEnRouteTracking()
                        HelperStatus.ACCEPTED -> Unit
                    }
                }
                .onFailure {
                    _sendError.value = "Helper update not delivered: ${it.message ?: "no peers in range"}"
                }
        }
    }

    private suspend fun sendHelperUpdateAck(
        packet: SosPacket,
        status: HelperStatus,
        etaMinutes: Int,
        isBackgroundTick: Boolean
    ): Result<Set<String>> {
        val timestamp = Instant.now().epochSecond
        val location = deviceLocationProvider.getCurrentLocation()
        val etaText = when (status) {
            HelperStatus.ACCEPTED -> "Accepted (ETA ~$etaMinutes min)"
            HelperStatus.EN_ROUTE -> "En route (ETA ~$etaMinutes min)"
            HelperStatus.REACHED -> "Reached victim location"
            HelperStatus.CANNOT_CONTINUE -> "Cannot continue"
        }

        val ack = AckPacket(
            originalPacketId = packet.id,
            alertId = "${AckPacket.LOCAL_HELP_ALERT_PREFIX}$localDeviceId",
            uploadedBy = localDeviceId,
            respondersNotified = 1,
            estimatedArrival = etaText,
            helperStatus = status,
            helperLocation = location,
            helperTimestamp = timestamp
        )

        val result = stateMachine.sendHelperUpdate(ack)
        result.onSuccess { peers ->
            val statusText = status.name.replace('_', ' ')
            val modeText = if (isBackgroundTick) "live tick" else "manual"
            eventLogger.log(
                MeshEventType.ACK_RECEIVED,
                "Helper update [$statusText] ($modeText) sent to ${peers.size} peer(s)",
                packet.id,
                localDeviceId
            )
        }
        return result
    }

    private fun startEnRouteTracking(etaMinutes: Int) {
        stopEnRouteTracking()
        helperTrackingJob = viewModelScope.launch {
            while (isActive && _activeHelperStatus.value == HelperStatus.EN_ROUTE) {
                delay(15_000)
                val packet = receivedPacket(meshState.value)
                if (packet == null || packet.id != helperTrackingPacketId) {
                    helperTrackingPacketId = null
                    _activeHelperStatus.value = null
                    return@launch
                }
                sendHelperUpdateAck(packet, HelperStatus.EN_ROUTE, etaMinutes, isBackgroundTick = true)
                // Tick failures are expected while out of range; the next tick retries.
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

    private fun receivedPacket(state: MeshState): SosPacket? = when (state) {
        is MeshState.Relay -> state.packet
        is MeshState.Uploading -> state.packet
        is MeshState.AwaitingAck -> state.packet
        else -> null
    }
}
