package com.meshsos.`data`.db.dao

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.meshsos.`data`.db.entity.MeshEventEntity
import javax.`annotation`.processing.Generated
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.mutableListOf
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class MeshEventDao_Impl(
  __db: RoomDatabase,
) : MeshEventDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfMeshEventEntity: EntityInsertAdapter<MeshEventEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfMeshEventEntity = object : EntityInsertAdapter<MeshEventEntity>() {
      protected override fun createQuery(): String =
          "INSERT OR ABORT INTO `mesh_events` (`rowId`,`timestamp`,`eventType`,`message`,`packetId`,`deviceId`) VALUES (nullif(?, 0),?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: MeshEventEntity) {
        statement.bindLong(1, entity.rowId)
        statement.bindLong(2, entity.timestamp)
        statement.bindText(3, entity.eventType)
        statement.bindText(4, entity.message)
        val _tmpPacketId: String? = entity.packetId
        if (_tmpPacketId == null) {
          statement.bindNull(5)
        } else {
          statement.bindText(5, _tmpPacketId)
        }
        val _tmpDeviceId: String? = entity.deviceId
        if (_tmpDeviceId == null) {
          statement.bindNull(6)
        } else {
          statement.bindText(6, _tmpDeviceId)
        }
      }
    }
  }

  public override suspend fun insert(event: MeshEventEntity): Unit = performSuspending(__db, false,
      true) { _connection ->
    __insertAdapterOfMeshEventEntity.insert(_connection, event)
  }

  public override fun getRecentFlow(limit: Int): Flow<List<MeshEventEntity>> {
    val _sql: String = "SELECT * FROM mesh_events ORDER BY timestamp DESC LIMIT ?"
    return createFlow(__db, false, arrayOf("mesh_events")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, limit.toLong())
        val _columnIndexOfRowId: Int = getColumnIndexOrThrow(_stmt, "rowId")
        val _columnIndexOfTimestamp: Int = getColumnIndexOrThrow(_stmt, "timestamp")
        val _columnIndexOfEventType: Int = getColumnIndexOrThrow(_stmt, "eventType")
        val _columnIndexOfMessage: Int = getColumnIndexOrThrow(_stmt, "message")
        val _columnIndexOfPacketId: Int = getColumnIndexOrThrow(_stmt, "packetId")
        val _columnIndexOfDeviceId: Int = getColumnIndexOrThrow(_stmt, "deviceId")
        val _result: MutableList<MeshEventEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: MeshEventEntity
          val _tmpRowId: Long
          _tmpRowId = _stmt.getLong(_columnIndexOfRowId)
          val _tmpTimestamp: Long
          _tmpTimestamp = _stmt.getLong(_columnIndexOfTimestamp)
          val _tmpEventType: String
          _tmpEventType = _stmt.getText(_columnIndexOfEventType)
          val _tmpMessage: String
          _tmpMessage = _stmt.getText(_columnIndexOfMessage)
          val _tmpPacketId: String?
          if (_stmt.isNull(_columnIndexOfPacketId)) {
            _tmpPacketId = null
          } else {
            _tmpPacketId = _stmt.getText(_columnIndexOfPacketId)
          }
          val _tmpDeviceId: String?
          if (_stmt.isNull(_columnIndexOfDeviceId)) {
            _tmpDeviceId = null
          } else {
            _tmpDeviceId = _stmt.getText(_columnIndexOfDeviceId)
          }
          _item =
              MeshEventEntity(_tmpRowId,_tmpTimestamp,_tmpEventType,_tmpMessage,_tmpPacketId,_tmpDeviceId)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun count(): Int {
    val _sql: String = "SELECT COUNT(*) FROM mesh_events"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _result: Int
        if (_stmt.step()) {
          val _tmp: Int
          _tmp = _stmt.getLong(0).toInt()
          _result = _tmp
        } else {
          _result = 0
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteOlderThan(cutoff: Long) {
    val _sql: String = "DELETE FROM mesh_events WHERE timestamp < ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, cutoff)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
