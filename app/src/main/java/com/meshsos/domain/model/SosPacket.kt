package com.meshsos.domain.model

import com.google.gson.Gson
import java.time.Instant
import java.util.UUID

// ── Packet types ──────────────────────────────────────────────────────────────

enum class PacketType { SOS, ACK, PING }

enum class Severity { CRITICAL, HIGH, MEDIUM }

enum class IncidentCategory { MEDICAL, FIRE, VIOLENCE, NATURAL_DISASTER, OTHER }

// ── Core packet sent across the mesh ─────────────────────────────────────────

data class SosPacket(
    val id: String = UUID.randomUUID().toString(),
    val type: PacketType = PacketType.SOS,
    val senderId: String,
    val incident: IncidentInfo,
    val metadata: PacketMetadata = PacketMetadata(),
    val uploaded: Boolean = false,
    val uploadTimestamp: Long? = null
) {
    fun toJson(): String = Gson().toJson(this)

    fun toBytes(): ByteArray = toJson().toByteArray(Charsets.UTF_8)

    fun isExpired(): Boolean {
        val createdAt = Instant.ofEpochSecond(metadata.createdAt)
        val expiry = createdAt.plusSeconds(metadata.ttl.toLong())
        return Instant.now().isAfter(expiry)
    }

    fun isHopLimitReached(): Boolean = metadata.currentHops >= metadata.maxHops

    fun incrementHop(relayDeviceId: String): SosPacket = copy(
        metadata = metadata.copy(
            currentHops = metadata.currentHops + 1,
            route = metadata.route + relayDeviceId
        )
    )

    fun markUploaded(): SosPacket = copy(
        uploaded = true,
        uploadTimestamp = Instant.now().epochSecond
    )

    companion object {
        fun fromJson(json: String): SosPacket? = try {
            Gson().fromJson(json, SosPacket::class.java)
        } catch (e: Exception) {
            null
        }

        fun fromBytes(bytes: ByteArray): SosPacket? =
            fromJson(String(bytes, Charsets.UTF_8))
    }
}

data class IncidentInfo(
    val severity: Severity = Severity.CRITICAL,
    val category: IncidentCategory = IncidentCategory.OTHER,
    val message: String = "",
    val location: LocationInfo? = null
)

data class LocationInfo(
    val lat: Double,
    val lng: Double,
    val accuracy: Float,
    val address: String = ""
)

data class PacketMetadata(
    val createdAt: Long = Instant.now().epochSecond,
    val ttl: Int = 3600,              // seconds
    val maxHops: Int = 10,
    val currentHops: Int = 0,
    val route: List<String> = emptyList(),
    val batteryLevel: Int = 100
)

// ── ACK packet sent back along route ─────────────────────────────────────────

data class AckPacket(
    val id: String = UUID.randomUUID().toString(),
    val type: PacketType = PacketType.ACK,
    val originalPacketId: String,
    val alertId: String,
    val uploadedBy: String,
    val respondersNotified: Int = 0,
    val estimatedArrival: String = ""
) {
    fun toBytes(): ByteArray = Gson().toJson(this).toByteArray(Charsets.UTF_8)

    companion object {
        fun fromBytes(bytes: ByteArray): AckPacket? = try {
            Gson().fromJson(String(bytes, Charsets.UTF_8), AckPacket::class.java)
        } catch (e: Exception) {
            null
        }
    }
}

// ── Mesh event (for log screen) ───────────────────────────────────────────────

data class MeshEvent(
    val timestamp: Long = Instant.now().epochSecond,
    val type: MeshEventType,
    val message: String,
    val packetId: String? = null,
    val deviceId: String? = null
)

enum class MeshEventType {
    SOS_SENT, SOS_RECEIVED, PACKET_RELAYED, PACKET_UPLOADED,
    ACK_RECEIVED, PEER_CONNECTED, PEER_DISCONNECTED,
    TRANSPORT_SWITCHED, TTL_EXPIRED, HOP_LIMIT_REACHED,
    DUPLICATE_DROPPED, ERROR
}
