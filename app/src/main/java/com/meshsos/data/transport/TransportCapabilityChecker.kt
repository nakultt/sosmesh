package com.meshsos.data.transport

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat

internal object TransportCapabilityChecker {

    fun isBleAvailable(context: Context): Boolean = bleUnavailableReason(context) == null

    fun isNearbyAvailable(context: Context): Boolean = nearbyUnavailableReason(context) == null

    fun bleUnavailableReason(context: Context): String? {
        val missingPermissions = missingBlePermissions(context)
        if (missingPermissions.isNotEmpty()) {
            return "BLE unavailable: missing ${missingPermissions.joinToString()}"
        }

        if (!isBluetoothEnabled(context)) {
            return "BLE unavailable: Bluetooth is turned off"
        }

        if (requiresLocationServicesForScan() && !isLocationServicesEnabled(context)) {
            return "BLE unavailable: device Location service is turned off"
        }

        return null
    }

    fun nearbyUnavailableReason(context: Context): String? {
        val missingPermissions = missingNearbyPermissions(context)
        if (missingPermissions.isNotEmpty()) {
            return "Nearby unavailable: missing ${missingPermissions.joinToString()}"
        }

        if (!isBluetoothEnabled(context)) {
            return "Nearby unavailable: Bluetooth is turned off"
        }

        if (requiresLocationServicesForScan() && !isLocationServicesEnabled(context)) {
            return "Nearby unavailable: device Location service is turned off"
        }

        return null
    }

    private fun missingBlePermissions(context: Context): List<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!hasPermission(context, Manifest.permission.BLUETOOTH_SCAN)) {
                add("BLUETOOTH_SCAN")
            }
            if (!hasPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE)) {
                add("BLUETOOTH_ADVERTISE")
            }
            if (!hasPermission(context, Manifest.permission.BLUETOOTH_CONNECT)) {
                add("BLUETOOTH_CONNECT")
            }
        } else {
            if (!hasLocationPermission(context)) {
                add("ACCESS_FINE_LOCATION/ACCESS_COARSE_LOCATION")
            }
        }
    }

    private fun missingNearbyPermissions(context: Context): List<String> = buildList {
        addAll(missingBlePermissions(context))

        if (!hasLocationPermission(context)) {
            add("ACCESS_FINE_LOCATION/ACCESS_COARSE_LOCATION")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !hasPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES)
        ) {
            add("NEARBY_WIFI_DEVICES")
        }
    }.distinct()

    private fun hasLocationPermission(context: Context): Boolean =
        hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
            hasPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)

    private fun hasPermission(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun isBluetoothEnabled(context: Context): Boolean =
        context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true

    private fun isLocationServicesEnabled(context: Context): Boolean {
        val locationManager = context.getSystemService(LocationManager::class.java) ?: return false
        return try {
            locationManager.isLocationEnabled
        } catch (_: Throwable) {
            val gpsEnabled = runCatching {
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            }.getOrDefault(false)
            val networkEnabled = runCatching {
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            }.getOrDefault(false)
            gpsEnabled || networkEnabled
        }
    }

    private fun requiresLocationServicesForScan(): Boolean =
        Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2
}
