package com.meshsos.domain.service

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thread-safe in-memory deduplication using packet UUID.
 * Entries older than [EXPIRY_HOURS] are cleaned up automatically.
 *
 * Why not DB? Speed — dedup check is on the hot path of every received BLE packet.
 * Room query latency (~1-5ms) is too slow when packets can arrive rapidly.
 * The set is capped by TTL so memory stays bounded.
 */
@Singleton
class DeduplicationService @Inject constructor() {

    private val processedIds = ConcurrentHashMap<String, Long>() // id -> epochSecond

    /**
     * Returns true if [packetId] was seen before. Adds it if not.
     * Also cleans entries older than [EXPIRY_HOURS].
     */
    fun isDuplicate(packetId: String): Boolean {
        cleanup()
        if (processedIds.containsKey(packetId)) return true
        processedIds[packetId] = Instant.now().epochSecond
        return false
    }

    /** Force-mark a packet ID as seen (used when we originate) */
    fun markSeen(packetId: String) {
        processedIds[packetId] = Instant.now().epochSecond
    }

    fun clear() {
        processedIds.clear()
    }

    fun size(): Int = processedIds.size

    private fun cleanup() {
        val cutoff = Instant.now().epochSecond - (EXPIRY_HOURS * 3600)
        processedIds.entries.removeIf { it.value < cutoff }
    }

    companion object {
        private const val EXPIRY_HOURS = 1
    }
}
