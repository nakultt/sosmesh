package com.meshsos.domain.service

import javax.inject.Inject
import javax.inject.Singleton

enum class PowerMode {
    EMERGENCY,  // User just sent SOS — scan aggressively
    RELAY,      // Received a packet, relaying — moderate scan
    IDLE        // Just listening passively — save battery
}

@Singleton
class AdaptiveScanStrategy @Inject constructor() {

    /**
     * Returns scan interval in milliseconds.
     *
     * Rules:
     * - EMERGENCY mode: always 1s regardless of battery (life-critical)
     * - Battery < 10%: back off to 5 min to avoid dying before relay completes
     * - Battery < 20%: 2 min
     * - Battery < 50%: 30s
     * - Battery >= 50%: mode-dependent
     */
    fun getScanIntervalMs(batteryLevel: Int, mode: PowerMode): Long {
        if (mode == PowerMode.EMERGENCY) return 1_000L

        return when {
            batteryLevel < 10 -> 300_000L  // 5 min
            batteryLevel < 20 -> 120_000L  // 2 min
            batteryLevel < 50 -> when (mode) {
                PowerMode.RELAY -> 30_000L
                else -> 60_000L
            }
            else -> when (mode) {
                PowerMode.RELAY -> 10_000L
                else -> 30_000L
            }
        }
    }

    /**
     * Nearby Connections scan mode based on current power mode.
     * Maps to com.google.android.gms.nearby.connection.DiscoveryOptions scan mode.
     *
     * 0 = LOW_POWER, 1 = BALANCED, 2 = HIGH_POWER
     */
    fun getNearbyDiscoveryScanMode(batteryLevel: Int, mode: PowerMode): Int {
        if (mode == PowerMode.EMERGENCY) return 2 // HIGH_POWER
        return when {
            batteryLevel < 20 -> 0 // LOW_POWER
            batteryLevel < 50 -> 1 // BALANCED
            else -> when (mode) {
                PowerMode.RELAY -> 2 // HIGH_POWER
                else -> 1 // BALANCED
            }
        }
    }
}
