package com.meshsos.domain.usecase

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.os.Build
import android.util.Log
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.meshsos.data.transport.TransportManager
import com.meshsos.domain.model.IncidentCategory
import com.meshsos.domain.model.IncidentInfo
import com.meshsos.domain.model.LocationInfo
import com.meshsos.domain.model.PacketMetadata
import com.meshsos.domain.model.RoutePoint
import com.meshsos.domain.model.Severity
import com.meshsos.domain.model.SosPacket
import com.meshsos.domain.service.DeduplicationService
import com.meshsos.domain.statemachine.MeshEvent
import com.meshsos.domain.statemachine.MeshStateMachine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.coroutines.resume

private const val TAG = "SendSosUseCase"

@Singleton
class SendSosUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val stateMachine: MeshStateMachine,
    private val transportManager: TransportManager,
    private val deduplicationService: DeduplicationService,
    @Named("deviceId") private val localDeviceId: String,
    private val batteryMonitor: com.meshsos.domain.service.BatteryMonitor
) {
    @SuppressLint("MissingPermission")
    suspend fun execute(
        category: IncidentCategory,
        severity: Severity = Severity.CRITICAL,
        message: String = ""
    ): Result<SosPacket> {
        Log.i(TAG, "Sending SOS: category=$category severity=$severity")

        // 1. Get location (timeout 5s — don't block SOS for GPS)
        val location = withTimeoutOrNull(5_000) { getLastLocation() }

        // 2. Build packet
        val now = java.time.Instant.now().epochSecond
        val packet = SosPacket(
            senderId = localDeviceId,
            incident = IncidentInfo(
                severity = severity,
                category = category,
                message = message,
                location = location
            ),
            metadata = PacketMetadata(
                createdAt = now,
                route = listOf(
                    RoutePoint(
                        deviceId = localDeviceId,
                        location = location,
                        timestamp = now
                    )
                ),
                batteryLevel = batteryMonitor.getBatteryLevel()
            )
        )

        // 3. Mark as seen so we don't relay our own packet back to ourselves
        deduplicationService.markSeen(packet.id)

        // 4. Transition state machine
        stateMachine.dispatch(MeshEvent.UserTriggeredSos(packet))

        // 5. Broadcast to connected peers immediately
        val result = transportManager.broadcastPacket(packet)
        val peerCount = transportManager.connectedPeerCount()
        stateMachine.updatePeersReached(peerCount)
        Log.d(TAG, "Broadcast result: $result peers=$peerCount")

        return Result.success(packet)
    }

    private suspend fun getLastLocation(): LocationInfo? =
        suspendCancellableCoroutine { cont ->
            try {
                val fusedClient = LocationServices.getFusedLocationProviderClient(context)
                fusedClient.getCurrentLocation(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    null
                ).addOnSuccessListener { androidLocation ->
                    if (androidLocation == null) {
                        cont.resume(null)
                        return@addOnSuccessListener
                    }
                    val address = reverseGeocode(androidLocation.latitude, androidLocation.longitude)
                    cont.resume(
                        LocationInfo(
                            lat = androidLocation.latitude,
                            lng = androidLocation.longitude,
                            accuracy = androidLocation.accuracy,
                            address = address
                        )
                    )
                }.addOnFailureListener {
                    Log.w(TAG, "Location failed: ${it.message}")
                    cont.resume(null)
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Location permission missing: ${e.message}")
                cont.resume(null)
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching location: ${e.message}")
                cont.resume(null)
            }
        }

    private fun reverseGeocode(lat: Double, lng: Double): String {
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Async on API 33+
                var result = ""
                geocoder.getFromLocation(lat, lng, 1) { addresses ->
                    result = addresses.firstOrNull()?.getAddressLine(0) ?: ""
                }
                result
            } else {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(lat, lng, 1)
                    ?.firstOrNull()
                    ?.getAddressLine(0) ?: ""
            }
        } catch (e: Exception) {
            Log.w(TAG, "Geocoding failed: ${e.message}")
            ""
        }
    }
}
