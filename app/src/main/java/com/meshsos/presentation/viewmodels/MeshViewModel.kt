package com.meshsos.presentation.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meshsos.background.MeshForegroundService
import com.meshsos.data.db.dao.MeshEventDao
import com.meshsos.data.db.dao.PendingPacketDao
import com.meshsos.data.db.entity.MeshEventEntity
import com.meshsos.data.transport.TransportManager
import com.meshsos.domain.model.IncidentCategory
import com.meshsos.domain.model.Severity
import com.meshsos.domain.service.AdaptiveScanStrategy
import com.meshsos.domain.service.BatteryMonitor
import com.meshsos.domain.service.PowerMode
import com.meshsos.domain.statemachine.MeshState
import com.meshsos.domain.statemachine.MeshStateMachine
import com.meshsos.domain.usecase.SendSosUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Named

@HiltViewModel
class MeshViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val stateMachine: MeshStateMachine,
    private val transportManager: TransportManager,
    private val sendSosUseCase: SendSosUseCase,
    private val batteryMonitor: BatteryMonitor,
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
    }

    fun dismissError() {
        _sendError.value = null
    }

    fun clearLogs() {
        viewModelScope.launch {
            meshEventDao.deleteAll()
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
                kotlinx.coroutines.delay(interval.coerceAtMost(5_000)) // poll UI max every 5s
            }
        }
    }
}
