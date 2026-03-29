package com.meshsos.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.meshsos.data.db.entity.MeshEventEntity
import com.meshsos.data.db.entity.PendingPacketEntity
import com.meshsos.data.db.entity.ProcessedIdEntity
import kotlinx.coroutines.flow.Flow

// ── Pending packets ────────────────────────────────────────────────────────────

@Dao
interface PendingPacketDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(packet: PendingPacketEntity)

    @Query("SELECT * FROM pending_packets ORDER BY createdAt ASC")
    suspend fun getAll(): List<PendingPacketEntity>

    @Query("DELETE FROM pending_packets WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE pending_packets SET retryCount = retryCount + 1, lastRetryAt = :now WHERE id = :id")
    suspend fun incrementRetry(id: String, now: Long)

    @Query("DELETE FROM pending_packets WHERE createdAt + ttl < :now")
    suspend fun deleteExpired(now: Long)

    @Query("SELECT COUNT(*) FROM pending_packets")
    fun countFlow(): Flow<Int>
}

// ── Processed IDs ──────────────────────────────────────────────────────────────

@Dao
interface ProcessedIdDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: ProcessedIdEntity)

    @Query("SELECT COUNT(*) FROM processed_ids WHERE packetId = :packetId")
    suspend fun exists(packetId: String): Int

    @Query("DELETE FROM processed_ids WHERE seenAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
}

// ── Event log ──────────────────────────────────────────────────────────────────

@Dao
interface MeshEventDao {
    @Insert
    suspend fun insert(event: MeshEventEntity)

    @Query("SELECT * FROM mesh_events ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentFlow(limit: Int = 100): Flow<List<MeshEventEntity>>

    @Query("SELECT * FROM mesh_events ORDER BY timestamp DESC")
    fun getAllFlow(): Flow<List<MeshEventEntity>>

    @Query("DELETE FROM mesh_events WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)

    @Query("DELETE FROM mesh_events")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM mesh_events")
    suspend fun count(): Int
}
