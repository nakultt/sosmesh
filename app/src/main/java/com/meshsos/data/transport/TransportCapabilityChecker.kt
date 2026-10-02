package com.meshsos.data.transport

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Single source of truth for what each transport needs at runtime.
 *
 *  - BLE (API 31+): BLUETOOTH_SCAN/ADVERTISE/CONNECT (scan is declared neverForLocation)
 *  - BLE (API <= 30): ACCESS_FINE_LOCATION and Location services on
 *  - Nearby: the BLE permissions, plus ACCESS_FINE_LOCATION (API <= 32)
 *    or NEARBY_WIFI_DEVICES (API 33+), and Location services on for API <= 32
 */
object TransportCapabilityChecker {

    fun isBleAvailable(context: Context): Boolean = bleUnavailableReason(context) == null

    fun isNearbyAvailable(context: Context): Boolean = nearbyUnavailableReason(context) == null

    fun bleUnavailableReason(context: Context): String? {
        val missingPermissions = missingBlePermissions(context)
        if (missingPermissions.isNotEmpty()) {
            return "BLE unavailable: missing ${missingPermissions.joinToString { it.shortPermissionName() }}"
        }
        if (!isBluetoothSupported(context)) return "BLE unavailable: device has no Bluetooth"
        if (!isBluetoothEnabled(context)) return "BLE unavailable: Bluetooth is turned off"
        if (bleNeedsLocationServices() && !isLocationServicesEnabled(context)) {
            return "BLE unavailable: Location services are turned off"
        }
        return null
    }

    fun nearbyUnavailableReason(context: Context): String? {
        val missingPermissions = missingNearbyPermissions(context)
        if (missingPermissions.isNotEmpty()) {
            return "Nearby unavailable: missing ${missingPermissions.joinToString { it.shortPermissionName() }}"
        }
        if (!isBluetoothSupported(context)) return "Nearby unavailable: device has no Bluetooth"
        if (!isBluetoothEnabled(context)) return "Nearby unavailable: Bluetooth is turned off"
        if (nearbyNeedsLocationServices() && !isLocationServicesEnabled(context)) {
            return "Nearby unavailable: Location services are turned off"
        }
        return null
    }

    /** Every runtime permission the app asks for, in request order. */
    fun requestedPermissions(): List<String> = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_ADVERTISE)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /** Permissions required for at least the mesh transports to work. */
    fun missingMeshPermissions(context: Context): List<String> =
        (missingBlePermissions(context) + missingNearbyPermissions(context)).distinct()

    fun missingBlePermissions(context: Context): List<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!hasPermission(context, Manifest.permission.BLUETOOTH_SCAN)) add(Manifest.permission.BLUETOOTH_SCAN)
            if (!hasPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE)) add(Manifest.permission.BLUETOOTH_ADVERTISE)
            if (!hasPermission(context, Manifest.permission.BLUETOOTH_CONNECT)) add(Manifest.permission.BLUETOOTH_CONNECT)
        } else if (!hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)) {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    fun missingNearbyPermissions(context: Context): List<String> = buildList {
        addAll(missingBlePermissions(context))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!hasPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES)) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        } else if (!hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)) {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }.distinct()

    fun hasLocationPermission(context: Context): Boolean =
        hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
            hasPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)

    fun hasPermission(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun isBluetoothSupported(context: Context): Boolean =
        context.getSystemService(BluetoothManager::class.java)?.adapter != null

    fun isBluetoothEnabled(context: Context): Boolean =
        runCatching { context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true }
            .getOrDefault(false)

    fun isLocationServicesEnabled(context: Context): Boolean {
        val locationManager = context.getSystemService(LocationManager::class.java) ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            locationManager.isLocationEnabled
        } else {
            val gpsEnabled = runCatching {
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            }.getOrDefault(false)
            val networkEnabled = runCatching {
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            }.getOrDefault(false)
            gpsEnabled || networkEnabled
        }
    }

    /** Location services must be on for BLE scans to return results before Android 12. */
    fun bleNeedsLocationServices(): Boolean = Build.VERSION.SDK_INT <= Build.VERSION_CODES.R

    fun nearbyNeedsLocationServices(): Boolean = Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2

    fun String.shortPermissionName(): String = substringAfterLast('.')
}
