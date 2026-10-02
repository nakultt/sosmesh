package com.meshsos.domain.usecase

import android.util.Log
import com.meshsos.domain.model.IncidentCategory
import com.meshsos.domain.model.IncidentInfo
import com.meshsos.domain.model.PacketMetadata
import com.meshsos.domain.model.RoutePoint
import com.meshsos.domain.model.Severity
import com.meshsos.domain.model.SosPacket
import com.meshsos.domain.service.BatteryMonitor
import com.meshsos.domain.service.DeviceLocationProvider
import com.meshsos.domain.statemachine.MeshStateMachine
import java.time.Instant
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

private const val TAG = "SendSosUseCase"
private const val LOCATION_TIMEOUT_MS = 5_000L
private const val GEOCODE_TIMEOUT_MS = 2_000L

@Singleton
class SendSosUseCase @Inject constructor(
    private val stateMachine: MeshStateMachine,
    private val deviceLocationProvider: DeviceLocationProvider,
    private val batteryMonitor: BatteryMonitor,
    @Named("deviceId") private val localDeviceId: String
) {
    /**
     * Builds the SOS packet (location is best effort and never blocks longer than a few
     * seconds) and hands it to the state machine, which broadcasts it on every transport,
     * keeps re-sending it to new peers, and uploads it directly when online.
     */
    suspend fun execute(
        category: IncidentCategory,
        severity: Severity = Severity.CRITICAL,
        message: String = ""
    ): Result<SosPacket> = runCatching {
        Log.i(TAG, "Sending SOS: category=$category severity=$severity")

        val fix = deviceLocationProvider.getCurrentLocation(
            timeoutMs = LOCATION_TIMEOUT_MS,
            highAccuracy = true
        )
        val location = fix?.let {
            it.copy(address = deviceLocationProvider.reverseGeocode(it.lat, it.lng, GEOCODE_TIMEOUT_MS))
        }

        val now = Instant.now().epochSecond
        val packet = SosPacket(
            senderId = localDeviceId,
            incident = IncidentInfo(
                severity = severity,
                category = category,
                message = message.trim(),
                location = location
            ),
            metadata = PacketMetadata(
                createdAt = now,
                route = listOf(RoutePoint(deviceId = localDeviceId, location = location, timestamp = now)),
                batteryLevel = batteryMonitor.getBatteryLevel()
            )
        )

        stateMachine.originate(packet)
        packet
    }
}
