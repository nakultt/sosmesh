package com.meshsos.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// ── Pending packet (not yet uploaded to server) ───────────────────────────────

@Entity(tableName = "pending_packets")
data class PendingPacketEntity(
    @PrimaryKey val id: String,
    val packetJson: String,
    val createdAt: Long,
    val ttl: Int,
    val retryCount: Int = 0,
    val lastRetryAt: Long? = null
)

// ── Processed packet IDs (dedup cache, backed by DB for persistence across restarts) ──

@Entity(
    tableName = "processed_ids",
    indices = [Index(value = ["packetId"], unique = true)]
)
data class ProcessedIdEntity(
    @PrimaryKey val packetId: String,
    val seenAt: Long
)

// ── Mesh event log ─────────────────────────────────────────────────────────────

@Entity(tableName = "mesh_events")
data class MeshEventEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val timestamp: Long,
    val eventType: String,
    val message: String,
    val packetId: String?,
    val deviceId: String?
)
