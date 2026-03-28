package com.meshsos.`data`.db.dao

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.meshsos.`data`.db.entity.PendingPacketEntity
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
public class PendingPacketDao_Impl(
  __db: RoomDatabase,
) : PendingPacketDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfPendingPacketEntity: EntityInsertAdapter<PendingPacketEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfPendingPacketEntity = object : EntityInsertAdapter<PendingPacketEntity>()
        {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `pending_packets` (`id`,`packetJson`,`createdAt`,`ttl`,`retryCount`,`lastRetryAt`) VALUES (?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: PendingPacketEntity) {
        statement.bindText(1, entity.id)
        statement.bindText(2, entity.packetJson)
        statement.bindLong(3, entity.createdAt)
        statement.bindLong(4, entity.ttl.toLong())
        statement.bindLong(5, entity.retryCount.toLong())
        val _tmpLastRetryAt: Long? = entity.lastRetryAt
        if (_tmpLastRetryAt == null) {
          statement.bindNull(6)
        } else {
          statement.bindLong(6, _tmpLastRetryAt)
        }
      }
    }
  }

  public override suspend fun insert(packet: PendingPacketEntity): Unit = performSuspending(__db,
      false, true) { _connection ->
    __insertAdapterOfPendingPacketEntity.insert(_connection, packet)
  }

  public override suspend fun getAll(): List<PendingPacketEntity> {
    val _sql: String = "SELECT * FROM pending_packets ORDER BY createdAt ASC"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfPacketJson: Int = getColumnIndexOrThrow(_stmt, "packetJson")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
        val _columnIndexOfTtl: Int = getColumnIndexOrThrow(_stmt, "ttl")
        val _columnIndexOfRetryCount: Int = getColumnIndexOrThrow(_stmt, "retryCount")
        val _columnIndexOfLastRetryAt: Int = getColumnIndexOrThrow(_stmt, "lastRetryAt")
        val _result: MutableList<PendingPacketEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: PendingPacketEntity
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpPacketJson: String
          _tmpPacketJson = _stmt.getText(_columnIndexOfPacketJson)
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpTtl: Int
          _tmpTtl = _stmt.getLong(_columnIndexOfTtl).toInt()
          val _tmpRetryCount: Int
          _tmpRetryCount = _stmt.getLong(_columnIndexOfRetryCount).toInt()
          val _tmpLastRetryAt: Long?
          if (_stmt.isNull(_columnIndexOfLastRetryAt)) {
            _tmpLastRetryAt = null
          } else {
            _tmpLastRetryAt = _stmt.getLong(_columnIndexOfLastRetryAt)
          }
          _item =
              PendingPacketEntity(_tmpId,_tmpPacketJson,_tmpCreatedAt,_tmpTtl,_tmpRetryCount,_tmpLastRetryAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun countFlow(): Flow<Int> {
    val _sql: String = "SELECT COUNT(*) FROM pending_packets"
    return createFlow(__db, false, arrayOf("pending_packets")) { _connection ->
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

  public override suspend fun delete(id: String) {
    val _sql: String = "DELETE FROM pending_packets WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, id)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun incrementRetry(id: String, now: Long) {
    val _sql: String =
        "UPDATE pending_packets SET retryCount = retryCount + 1, lastRetryAt = ? WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, now)
        _argIndex = 2
        _stmt.bindText(_argIndex, id)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteExpired(now: Long) {
    val _sql: String = "DELETE FROM pending_packets WHERE createdAt + ttl < ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, now)
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
