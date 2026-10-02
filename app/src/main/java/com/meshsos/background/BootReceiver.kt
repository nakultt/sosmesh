package com.meshsos.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.meshsos.data.settings.MeshSettings
import com.meshsos.data.transport.TransportCapabilityChecker

/** Restarts the relay after reboot / app update, if the user left it enabled. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return
        if (!MeshSettings.isServiceEnabled(context)) return
        if (TransportCapabilityChecker.missingBlePermissions(context).isNotEmpty()) {
            Log.i("BootReceiver", "Skipping relay auto-start: Bluetooth permissions not granted")
            return
        }
        MeshForegroundService.start(context)
    }
}
