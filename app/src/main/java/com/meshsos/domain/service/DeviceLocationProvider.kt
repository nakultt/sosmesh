package com.meshsos.domain.service

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.meshsos.domain.model.LocationInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

private const val TAG = "DeviceLocationProvider"

@Singleton
class DeviceLocationProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(timeoutMs: Long = 2_500): LocationInfo? {
        return withTimeoutOrNull(timeoutMs) {
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
                        cont.resume(
                            LocationInfo(
                                lat = androidLocation.latitude,
                                lng = androidLocation.longitude,
                                accuracy = androidLocation.accuracy,
                                address = ""
                            )
                        )
                    }.addOnFailureListener {
                        Log.w(TAG, "Location fetch failed: ${it.message}")
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
        }
    }
}
