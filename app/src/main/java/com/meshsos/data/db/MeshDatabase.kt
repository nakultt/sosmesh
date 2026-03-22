package com.meshsos.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.meshsos.data.db.dao.MeshEventDao
import com.meshsos.data.db.dao.PendingPacketDao
import com.meshsos.data.db.dao.ProcessedIdDao
import com.meshsos.data.db.entity.MeshEventEntity
import com.meshsos.data.db.entity.PendingPacketEntity
import com.meshsos.data.db.entity.ProcessedIdEntity

@Database(
    entities = [
        PendingPacketEntity::class,
        ProcessedIdEntity::class,
        MeshEventEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class MeshDatabase : RoomDatabase() {
    abstract fun pendingPacketDao(): PendingPacketDao
    abstract fun processedIdDao(): ProcessedIdDao
    abstract fun meshEventDao(): MeshEventDao
}
