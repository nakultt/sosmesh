package com.meshsos.domain.model

import com.google.gson.Gson
import java.time.Instant
import java.util.UUID

private const val MAX_ROUTE_POINTS = 20
private const val MAX_MESSAGE_LENGTH = 500

private val gson = Gson()

// ── Packet types ──────────────────────────────────────────────────────────────

enum class PacketType { SOS, ACK, PING }

enum class Severity { CRITICAL, HIGH, MEDIUM }

enum class IncidentCategory { MEDICAL, FIRE, VIOLENCE, NATURAL_DISASTER, OTHER }

enum class HelperStatus { ACCEPTED, EN_ROUTE, REACHED, CANNOT_CONTINUE }

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
    fun toJson(): String = gson.toJson(this)

    fun toBytes(): ByteArray = toJson().toByteArray(Charsets.UTF_8)

    fun isExpired(): Boolean {
        val createdAt = Instant.ofEpochSecond(metadata.createdAt)
        val expiry = createdAt.plusSeconds(metadata.ttl.toLong())
        return Instant.now().isAfter(expiry)
    }

    fun isHopLimitReached(): Boolean = metadata.currentHops >= metadata.maxHops

    fun incrementHop(
        relayDeviceId: String,
        relayLocation: LocationInfo? = null,
        hopTimestamp: Long = Instant.now().epochSecond
    ): SosPacket {
        val routeLimit = (metadata.maxHops + 1).coerceIn(2, MAX_ROUTE_POINTS)
        val updatedRoute = (metadata.route + RoutePoint(
            deviceId = relayDeviceId,
            location = relayLocation,
            timestamp = hopTimestamp
        )).takeLast(routeLimit)

        return copy(
            metadata = metadata.copy(
                currentHops = metadata.currentHops + 1,
                route = updatedRoute
            )
        )
    }

    fun markUploaded(): SosPacket = copy(
        uploaded = true,
        uploadTimestamp = Instant.now().epochSecond
    )

    companion object {
        /**
         * Parses and validates a packet. Gson bypasses Kotlin constructors, so we
         * parse into an all-nullable wire class and rebuild the domain object with
         * defaults. Anything that is not a well-formed SOS packet returns null.
         */
        fun fromJson(json: String): SosPacket? = try {
            gson.fromJson(json, SosPacketWire::class.java)?.toDomain()
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

data class RoutePoint(
    val deviceId: String,
    val location: LocationInfo? = null,
    val timestamp: Long = Instant.now().epochSecond
)

data class PacketMetadata(
    val createdAt: Long = Instant.now().epochSecond,
    val ttl: Int = 3600,              // seconds
    val maxHops: Int = 10,
    val currentHops: Int = 0,
    val route: List<RoutePoint> = emptyList(),
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
    val estimatedArrival: String = "",
    val helperStatus: HelperStatus? = null,
    val helperLocation: LocationInfo? = null,
    val helperTimestamp: Long? = null
) {
    fun toBytes(): ByteArray = gson.toJson(this).toByteArray(Charsets.UTF_8)

    companion object {
        const val LOCAL_HELP_ALERT_PREFIX = "LOCAL_HELP:"

        fun fromBytes(bytes: ByteArray): AckPacket? = try {
            gson.fromJson(String(bytes, Charsets.UTF_8), AckPacketWire::class.java)?.toDomain()
        } catch (e: Exception) {
            null
        }
    }
}

fun AckPacket.isLocalHelpUpdate(): Boolean = alertId.startsWith(AckPacket.LOCAL_HELP_ALERT_PREFIX)

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

// ── Wire (nullable) representations used only for safe deserialization ──────

private data class LocationWire(
    val lat: Double?,
    val lng: Double?,
    val accuracy: Float?,
    val address: String?
) {
    fun toDomain(): LocationInfo? {
        val latitude = lat ?: return null
        val longitude = lng ?: return null
        if (latitude.isNaN() || longitude.isNaN()) return null
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
        return LocationInfo(
            lat = latitude,
            lng = longitude,
            accuracy = accuracy?.takeIf { !it.isNaN() && it >= 0f } ?: 0f,
            address = address.orEmpty()
        )
    }
}

private data class RoutePointWire(
    val deviceId: String?,
    val location: LocationWire?,
    val timestamp: Long?
) {
    fun toDomain(fallbackTimestamp: Long): RoutePoint? {
        val id = deviceId?.takeIf { it.isNotBlank() } ?: return null
        return RoutePoint(
            deviceId = id,
            location = location?.toDomain(),
            timestamp = timestamp ?: fallbackTimestamp
        )
    }
}

private data class IncidentWire(
    val severity: Severity?,
    val category: IncidentCategory?,
    val message: String?,
    val location: LocationWire?
)

private data class MetadataWire(
    val createdAt: Long?,
    val ttl: Int?,
    val maxHops: Int?,
    val currentHops: Int?,
    val route: List<RoutePointWire?>?,
    val batteryLevel: Int?
)

private data class SosPacketWire(
    val id: String?,
    val type: PacketType?,
    val senderId: String?,
    val incident: IncidentWire?,
    val metadata: MetadataWire?,
    val uploaded: Boolean?,
    val uploadTimestamp: Long?
) {
    fun toDomain(): SosPacket? {
        if (type != PacketType.SOS) return null
        val packetId = id?.takeIf { it.isNotBlank() } ?: return null
        val sender = senderId?.takeIf { it.isNotBlank() } ?: return null
        val incidentWire = incident ?: return null
        val now = Instant.now().epochSecond
        val createdAt = metadata?.createdAt ?: now
        val maxHops = (metadata?.maxHops ?: 10).coerceIn(1, MAX_ROUTE_POINTS)
        return SosPacket(
            id = packetId,
            type = PacketType.SOS,
            senderId = sender,
            incident = IncidentInfo(
                severity = incidentWire.severity ?: Severity.CRITICAL,
                category = incidentWire.category ?: IncidentCategory.OTHER,
                message = incidentWire.message.orEmpty().take(MAX_MESSAGE_LENGTH),
                location = incidentWire.location?.toDomain()
            ),
            metadata = PacketMetadata(
                createdAt = createdAt,
                ttl = (metadata?.ttl ?: 3600).coerceIn(60, 24 * 3600),
                maxHops = maxHops,
                currentHops = (metadata?.currentHops ?: 0).coerceAtLeast(0),
                route = metadata?.route.orEmpty()
                    .mapNotNull { it?.toDomain(createdAt) }
                    .takeLast(MAX_ROUTE_POINTS),
                batteryLevel = (metadata?.batteryLevel ?: 100).coerceIn(0, 100)
            ),
            uploaded = uploaded ?: false,
            uploadTimestamp = uploadTimestamp
        )
    }
}

private data class AckPacketWire(
    val id: String?,
    val type: PacketType?,
    val originalPacketId: String?,
    val alertId: String?,
    val uploadedBy: String?,
    val respondersNotified: Int?,
    val estimatedArrival: String?,
    val helperStatus: HelperStatus?,
    val helperLocation: LocationWire?,
    val helperTimestamp: Long?
) {
    fun toDomain(): AckPacket? {
        if (type != PacketType.ACK) return null
        val ackId = id?.takeIf { it.isNotBlank() } ?: return null
        val packetId = originalPacketId?.takeIf { it.isNotBlank() } ?: return null
        return AckPacket(
            id = ackId,
            type = PacketType.ACK,
            originalPacketId = packetId,
            alertId = alertId.orEmpty(),
            uploadedBy = uploadedBy.orEmpty(),
            respondersNotified = (respondersNotified ?: 0).coerceAtLeast(0),
            estimatedArrival = estimatedArrival.orEmpty(),
            helperStatus = helperStatus,
            helperLocation = helperLocation?.toDomain(),
            helperTimestamp = helperTimestamp
        )
    }
}
