package com.meshsos.domain.service

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.util.Log
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.meshsos.data.transport.TransportCapabilityChecker
import com.meshsos.domain.model.LocationInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

private const val TAG = "DeviceLocationProvider"
private val MAX_LAST_LOCATION_AGE_MS = TimeUnit.MINUTES.toMillis(15)

@Singleton
class DeviceLocationProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val fusedClient by lazy { LocationServices.getFusedLocationProviderClient(context) }

    /**
     * Fresh fix within [timeoutMs], falling back to a recent last-known location.
     * Never throws; returns null when no location is available.
     */
    suspend fun getCurrentLocation(
        timeoutMs: Long = 2_500,
        highAccuracy: Boolean = false
    ): LocationInfo? {
        if (!TransportCapabilityChecker.hasLocationPermission(context)) return null
        val fresh = withTimeoutOrNull(timeoutMs) { requestCurrentLocation(highAccuracy) }
        val location = fresh ?: lastKnownLocation()
        return location?.let {
            LocationInfo(
                lat = it.latitude,
                lng = it.longitude,
                accuracy = it.accuracy,
                address = ""
            )
        }
    }

    /** Best-effort reverse geocoding, off the main thread and bounded by [timeoutMs]. */
    suspend fun reverseGeocode(lat: Double, lng: Double, timeoutMs: Long = 3_000): String {
        if (!Geocoder.isPresent()) return ""
        return withTimeoutOrNull(timeoutMs) {
            withContext(Dispatchers.IO) {
                try {
                    val geocoder = Geocoder(context, Locale.getDefault())
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        suspendCancellableCoroutine { cont ->
                            geocoder.getFromLocation(lat, lng, 1, object : Geocoder.GeocodeListener {
                                override fun onGeocode(addresses: MutableList<android.location.Address>) {
                                    if (cont.isActive) cont.resume(addresses.firstOrNull()?.getAddressLine(0).orEmpty())
                                }

                                override fun onError(errorMessage: String?) {
                                    if (cont.isActive) cont.resume("")
                                }
                            })
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        geocoder.getFromLocation(lat, lng, 1)?.firstOrNull()?.getAddressLine(0).orEmpty()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Geocoding failed: ${e.message}")
                    ""
                }
            }
        } ?: ""
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestCurrentLocation(highAccuracy: Boolean): Location? =
        suspendCancellableCoroutine { cont ->
            try {
                val priority = if (highAccuracy) Priority.PRIORITY_HIGH_ACCURACY
                else Priority.PRIORITY_BALANCED_POWER_ACCURACY
                fusedClient.getCurrentLocation(priority, null)
                    .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
                    .addOnFailureListener {
                        Log.w(TAG, "Location fetch failed: ${it.message}")
                        if (cont.isActive) cont.resume(null)
                    }
            } catch (e: Exception) {
                Log.w(TAG, "Location request failed: ${e.message}")
                if (cont.isActive) cont.resume(null)
            }
        }

    @SuppressLint("MissingPermission")
    private suspend fun lastKnownLocation(): Location? = withTimeoutOrNull(1_000) {
        suspendCancellableCoroutine<Location?> { cont ->
            try {
                fusedClient.lastLocation
                    .addOnSuccessListener { location ->
                        val fresh = location?.takeIf {
                            System.currentTimeMillis() - it.time <= MAX_LAST_LOCATION_AGE_MS
                        }
                        if (cont.isActive) cont.resume(fresh)
                    }
                    .addOnFailureListener { if (cont.isActive) cont.resume(null) }
            } catch (e: Exception) {
                if (cont.isActive) cont.resume(null)
            }
        }
    }
}
