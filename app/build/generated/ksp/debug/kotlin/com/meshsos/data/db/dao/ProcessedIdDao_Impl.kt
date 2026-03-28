package com.meshsos.`data`.db.dao

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.meshsos.`data`.db.entity.ProcessedIdEntity
import javax.`annotation`.processing.Generated
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.collections.List
import kotlin.reflect.KClass

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class ProcessedIdDao_Impl(
  __db: RoomDatabase,
) : ProcessedIdDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfProcessedIdEntity: EntityInsertAdapter<ProcessedIdEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfProcessedIdEntity = object : EntityInsertAdapter<ProcessedIdEntity>() {
      protected override fun createQuery(): String =
          "INSERT OR IGNORE INTO `processed_ids` (`packetId`,`seenAt`) VALUES (?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: ProcessedIdEntity) {
        statement.bindText(1, entity.packetId)
        statement.bindLong(2, entity.seenAt)
      }
    }
  }

  public override suspend fun insert(entity: ProcessedIdEntity): Unit = performSuspending(__db,
      false, true) { _connection ->
    __insertAdapterOfProcessedIdEntity.insert(_connection, entity)
  }

  public override suspend fun exists(packetId: String): Int {
    val _sql: String = "SELECT COUNT(*) FROM processed_ids WHERE packetId = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, packetId)
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
    val _sql: String = "DELETE FROM processed_ids WHERE seenAt < ?"
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
