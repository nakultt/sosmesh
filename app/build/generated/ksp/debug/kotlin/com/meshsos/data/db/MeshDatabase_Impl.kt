package com.meshsos.`data`.db

import androidx.room.InvalidationTracker
import androidx.room.RoomOpenDelegate
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.room.util.TableInfo
import androidx.room.util.TableInfo.Companion.read
import androidx.room.util.dropFtsSyncTriggers
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.meshsos.`data`.db.dao.MeshEventDao
import com.meshsos.`data`.db.dao.MeshEventDao_Impl
import com.meshsos.`data`.db.dao.PendingPacketDao
import com.meshsos.`data`.db.dao.PendingPacketDao_Impl
import com.meshsos.`data`.db.dao.ProcessedIdDao
import com.meshsos.`data`.db.dao.ProcessedIdDao_Impl
import javax.`annotation`.processing.Generated
import kotlin.Lazy
import kotlin.String
import kotlin.Suppress
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.MutableList
import kotlin.collections.MutableMap
import kotlin.collections.MutableSet
import kotlin.collections.Set
import kotlin.collections.mutableListOf
import kotlin.collections.mutableMapOf
import kotlin.collections.mutableSetOf
import kotlin.reflect.KClass

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class MeshDatabase_Impl : MeshDatabase() {
  private val _pendingPacketDao: Lazy<PendingPacketDao> = lazy {
    PendingPacketDao_Impl(this)
  }

  private val _processedIdDao: Lazy<ProcessedIdDao> = lazy {
    ProcessedIdDao_Impl(this)
  }

  private val _meshEventDao: Lazy<MeshEventDao> = lazy {
    MeshEventDao_Impl(this)
  }

  protected override fun createOpenDelegate(): RoomOpenDelegate {
    val _openDelegate: RoomOpenDelegate = object : RoomOpenDelegate(1,
        "7dd16f219d938f00e40ed05eb34e2d62", "d021e6fde3c0fe6ada5ce048cc524e28") {
      public override fun createAllTables(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `pending_packets` (`id` TEXT NOT NULL, `packetJson` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `ttl` INTEGER NOT NULL, `retryCount` INTEGER NOT NULL, `lastRetryAt` INTEGER, PRIMARY KEY(`id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `processed_ids` (`packetId` TEXT NOT NULL, `seenAt` INTEGER NOT NULL, PRIMARY KEY(`packetId`))")
        connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_processed_ids_packetId` ON `processed_ids` (`packetId`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `mesh_events` (`rowId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `eventType` TEXT NOT NULL, `message` TEXT NOT NULL, `packetId` TEXT, `deviceId` TEXT)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        connection.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '7dd16f219d938f00e40ed05eb34e2d62')")
      }

      public override fun dropAllTables(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS `pending_packets`")
        connection.execSQL("DROP TABLE IF EXISTS `processed_ids`")
        connection.execSQL("DROP TABLE IF EXISTS `mesh_events`")
      }

      public override fun onCreate(connection: SQLiteConnection) {
      }

      public override fun onOpen(connection: SQLiteConnection) {
        internalInitInvalidationTracker(connection)
      }

      public override fun onPreMigrate(connection: SQLiteConnection) {
        dropFtsSyncTriggers(connection)
      }

      public override fun onPostMigrate(connection: SQLiteConnection) {
      }

      public override fun onValidateSchema(connection: SQLiteConnection):
          RoomOpenDelegate.ValidationResult {
        val _columnsPendingPackets: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsPendingPackets.put("id", TableInfo.Column("id", "TEXT", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsPendingPackets.put("packetJson", TableInfo.Column("packetJson", "TEXT", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPendingPackets.put("createdAt", TableInfo.Column("createdAt", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPendingPackets.put("ttl", TableInfo.Column("ttl", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsPendingPackets.put("retryCount", TableInfo.Column("retryCount", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPendingPackets.put("lastRetryAt", TableInfo.Column("lastRetryAt", "INTEGER", false,
            0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysPendingPackets: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesPendingPackets: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoPendingPackets: TableInfo = TableInfo("pending_packets", _columnsPendingPackets,
            _foreignKeysPendingPackets, _indicesPendingPackets)
        val _existingPendingPackets: TableInfo = read(connection, "pending_packets")
        if (!_infoPendingPackets.equals(_existingPendingPackets)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |pending_packets(com.meshsos.data.db.entity.PendingPacketEntity).
              | Expected:
              |""".trimMargin() + _infoPendingPackets + """
              |
              | Found:
              |""".trimMargin() + _existingPendingPackets)
        }
        val _columnsProcessedIds: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsProcessedIds.put("packetId", TableInfo.Column("packetId", "TEXT", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsProcessedIds.put("seenAt", TableInfo.Column("seenAt", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysProcessedIds: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesProcessedIds: MutableSet<TableInfo.Index> = mutableSetOf()
        _indicesProcessedIds.add(TableInfo.Index("index_processed_ids_packetId", true,
            listOf("packetId"), listOf("ASC")))
        val _infoProcessedIds: TableInfo = TableInfo("processed_ids", _columnsProcessedIds,
            _foreignKeysProcessedIds, _indicesProcessedIds)
        val _existingProcessedIds: TableInfo = read(connection, "processed_ids")
        if (!_infoProcessedIds.equals(_existingProcessedIds)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |processed_ids(com.meshsos.data.db.entity.ProcessedIdEntity).
              | Expected:
              |""".trimMargin() + _infoProcessedIds + """
              |
              | Found:
              |""".trimMargin() + _existingProcessedIds)
        }
        val _columnsMeshEvents: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsMeshEvents.put("rowId", TableInfo.Column("rowId", "INTEGER", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsMeshEvents.put("timestamp", TableInfo.Column("timestamp", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsMeshEvents.put("eventType", TableInfo.Column("eventType", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsMeshEvents.put("message", TableInfo.Column("message", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsMeshEvents.put("packetId", TableInfo.Column("packetId", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsMeshEvents.put("deviceId", TableInfo.Column("deviceId", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysMeshEvents: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesMeshEvents: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoMeshEvents: TableInfo = TableInfo("mesh_events", _columnsMeshEvents,
            _foreignKeysMeshEvents, _indicesMeshEvents)
        val _existingMeshEvents: TableInfo = read(connection, "mesh_events")
        if (!_infoMeshEvents.equals(_existingMeshEvents)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |mesh_events(com.meshsos.data.db.entity.MeshEventEntity).
              | Expected:
              |""".trimMargin() + _infoMeshEvents + """
              |
              | Found:
              |""".trimMargin() + _existingMeshEvents)
        }
        return RoomOpenDelegate.ValidationResult(true, null)
      }
    }
    return _openDelegate
  }

  protected override fun createInvalidationTracker(): InvalidationTracker {
    val _shadowTablesMap: MutableMap<String, String> = mutableMapOf()
    val _viewTables: MutableMap<String, Set<String>> = mutableMapOf()
    return InvalidationTracker(this, _shadowTablesMap, _viewTables, "pending_packets",
        "processed_ids", "mesh_events")
  }

  public override fun clearAllTables() {
    super.performClear(false, "pending_packets", "processed_ids", "mesh_events")
  }

  protected override fun getRequiredTypeConverterClasses(): Map<KClass<*>, List<KClass<*>>> {
    val _typeConvertersMap: MutableMap<KClass<*>, List<KClass<*>>> = mutableMapOf()
    _typeConvertersMap.put(PendingPacketDao::class, PendingPacketDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(ProcessedIdDao::class, ProcessedIdDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(MeshEventDao::class, MeshEventDao_Impl.getRequiredConverters())
    return _typeConvertersMap
  }

  public override fun getRequiredAutoMigrationSpecClasses(): Set<KClass<out AutoMigrationSpec>> {
    val _autoMigrationSpecsSet: MutableSet<KClass<out AutoMigrationSpec>> = mutableSetOf()
    return _autoMigrationSpecsSet
  }

  public override
      fun createAutoMigrations(autoMigrationSpecs: Map<KClass<out AutoMigrationSpec>, AutoMigrationSpec>):
      List<Migration> {
    val _autoMigrations: MutableList<Migration> = mutableListOf()
    return _autoMigrations
  }

  public override fun pendingPacketDao(): PendingPacketDao = _pendingPacketDao.value

  public override fun processedIdDao(): ProcessedIdDao = _processedIdDao.value

  public override fun meshEventDao(): MeshEventDao = _meshEventDao.value
}
