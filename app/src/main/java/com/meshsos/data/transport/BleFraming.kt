package com.meshsos.data.transport

import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Splits messages into GATT-sized frames and reassembles them.
 *
 * Frame layout: [seq: 2 bytes][chunk index: 2 bytes][total chunks: 2 bytes][payload].
 * Each frame fits in a single ATT write/notification (MTU - 3 bytes, max 512), so Android
 * never falls back to prepared (long) writes. Frames of different messages may interleave;
 * they are told apart by (link, seq).
 */
internal class BleFraming(private val clock: () -> Long) {

    private class PartialMessage(val total: Int, val createdAt: Long) {
        val parts = arrayOfNulls<ByteArray>(total)
        var received = 0
    }

    private val partials = ConcurrentHashMap<String, PartialMessage>()
    private val sequence = AtomicInteger(0)

    fun buildFrames(message: ByteArray, mtu: Int): List<ByteArray> {
        val valueSize = (mtu - ATT_HEADER_SIZE).coerceIn(DEFAULT_MTU - ATT_HEADER_SIZE, MAX_ATT_VALUE_SIZE)
        val payloadSize = valueSize - HEADER_SIZE
        val total = maxOf(1, (message.size + payloadSize - 1) / payloadSize)
        if (total > 0xFFFF) return emptyList()
        val seq = sequence.incrementAndGet() and 0xFFFF
        return (0 until total).map { index ->
            val start = index * payloadSize
            val end = minOf(message.size, start + payloadSize)
            val frame = ByteArray(HEADER_SIZE + (end - start))
            frame[0] = (seq shr 8).toByte()
            frame[1] = seq.toByte()
            frame[2] = (index shr 8).toByte()
            frame[3] = index.toByte()
            frame[4] = (total shr 8).toByte()
            frame[5] = total.toByte()
            System.arraycopy(message, start, frame, HEADER_SIZE, end - start)
            frame
        }
    }

    /** Feeds one received frame; returns the full message once its last missing frame arrives. */
    fun accept(linkKey: String, frame: ByteArray): ByteArray? {
        if (frame.size < HEADER_SIZE) return null
        val seq = readU16(frame, 0)
        val index = readU16(frame, 2)
        val total = readU16(frame, 4)
        if (total == 0 || index >= total) return null
        val payload = frame.copyOfRange(HEADER_SIZE, frame.size)
        if (total == 1) return payload

        val key = "$linkKey#$seq"
        val partial = partials.getOrPut(key) { PartialMessage(total, clock()) }
        synchronized(partial) {
            if (partial.total != total) {
                partials.remove(key)
                return null
            }
            if (partial.parts[index] == null) {
                partial.parts[index] = payload
                partial.received++
            }
            if (partial.received < total) return null
            partials.remove(key)
            val out = ByteArrayOutputStream()
            partial.parts.forEach { out.write(it ?: return null) }
            return out.toByteArray()
        }
    }

    fun dropLink(linkKey: String) {
        partials.keys.removeIf { it.startsWith("$linkKey#") }
    }

    fun pruneOlderThan(maxAgeMs: Long) {
        val now = clock()
        partials.entries.removeIf { now - it.value.createdAt > maxAgeMs }
    }

    fun clear() = partials.clear()

    private fun readU16(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)

    companion object {
        const val DEFAULT_MTU = 23
        const val ATT_HEADER_SIZE = 3
        const val MAX_ATT_VALUE_SIZE = 512
        const val HEADER_SIZE = 6
    }
}
