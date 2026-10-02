package com.meshsos.data.transport

import com.meshsos.domain.model.AckPacket
import com.meshsos.domain.model.SosPacket

/**
 * Framing shared by all transports: a single type byte followed by the body.
 *
 *  - SOS   : UTF-8 JSON of [SosPacket]
 *  - ACK   : UTF-8 JSON of [AckPacket]
 *  - HELLO : UTF-8 "deviceId|displayName", exchanged so peers learn each other's mesh id
 *
 * Raw JSON (first byte '{') from older app versions is still accepted.
 */
internal object WireCodec {
    private const val TYPE_SOS: Byte = 0x01
    private const val TYPE_ACK: Byte = 0x02
    private const val TYPE_HELLO: Byte = 0x03
    private const val LEGACY_JSON_START = '{'.code.toByte()

    sealed class Message {
        data class Sos(val packet: SosPacket) : Message()
        data class Ack(val ack: AckPacket) : Message()
        data class Hello(val deviceId: String, val name: String) : Message()
    }

    fun encodeSos(packet: SosPacket): ByteArray = byteArrayOf(TYPE_SOS) + packet.toBytes()

    fun encodeAck(ack: AckPacket): ByteArray = byteArrayOf(TYPE_ACK) + ack.toBytes()

    fun encodeHello(deviceId: String, name: String): ByteArray =
        byteArrayOf(TYPE_HELLO) + "$deviceId|$name".toByteArray(Charsets.UTF_8)

    fun decode(bytes: ByteArray): Message? {
        if (bytes.isEmpty()) return null
        val body = bytes.copyOfRange(1, bytes.size)
        return when (bytes[0]) {
            TYPE_SOS -> SosPacket.fromBytes(body)?.let { Message.Sos(it) }
            TYPE_ACK -> AckPacket.fromBytes(body)?.let { Message.Ack(it) }
            TYPE_HELLO -> parseIdentity(String(body, Charsets.UTF_8))?.let { (id, name) ->
                Message.Hello(id, name)
            }
            LEGACY_JSON_START -> SosPacket.fromBytes(bytes)?.let { Message.Sos(it) }
                ?: AckPacket.fromBytes(bytes)?.let { Message.Ack(it) }
            else -> null
        }
    }

    /** Endpoint/advertised names are encoded as "deviceId|displayName". */
    fun encodeIdentity(deviceId: String, name: String): String = "$deviceId|$name"

    fun parseIdentity(raw: String): Pair<String, String>? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val separator = trimmed.indexOf('|')
        return if (separator > 0) {
            trimmed.substring(0, separator) to trimmed.substring(separator + 1).ifBlank { trimmed.substring(0, separator) }
        } else {
            // Legacy peers advertised only a display name; use it as the id too.
            trimmed to trimmed
        }
    }
}
