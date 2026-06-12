package com.vigia.core.`data`.db

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
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
public class ChatSessionDao_Impl(
  __db: RoomDatabase,
) : ChatSessionDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfChatSessionEntity: EntityInsertAdapter<ChatSessionEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfChatSessionEntity = object : EntityInsertAdapter<ChatSessionEntity>() {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `chat_sessions` (`id`,`title`,`created_at`,`updated_at`,`message_count`) VALUES (?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: ChatSessionEntity) {
        statement.bindText(1, entity.id)
        statement.bindText(2, entity.title)
        statement.bindLong(3, entity.createdAt)
        statement.bindLong(4, entity.updatedAt)
        statement.bindLong(5, entity.messageCount.toLong())
      }
    }
  }

  public override suspend fun upsert(session: ChatSessionEntity): Unit = performSuspending(__db,
      false, true) { _connection ->
    __insertAdapterOfChatSessionEntity.insert(_connection, session)
  }

  public override fun allSessions(): Flow<List<ChatSessionEntity>> {
    val _sql: String = "SELECT * FROM chat_sessions ORDER BY updated_at DESC"
    return createFlow(__db, false, arrayOf("chat_sessions")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _columnIndexOfMessageCount: Int = getColumnIndexOrThrow(_stmt, "message_count")
        val _result: MutableList<ChatSessionEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: ChatSessionEntity
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          val _tmpMessageCount: Int
          _tmpMessageCount = _stmt.getLong(_columnIndexOfMessageCount).toInt()
          _item = ChatSessionEntity(_tmpId,_tmpTitle,_tmpCreatedAt,_tmpUpdatedAt,_tmpMessageCount)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun sessionById(id: String): ChatSessionEntity? {
    val _sql: String = "SELECT * FROM chat_sessions WHERE id = ? LIMIT 1"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, id)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _columnIndexOfMessageCount: Int = getColumnIndexOrThrow(_stmt, "message_count")
        val _result: ChatSessionEntity?
        if (_stmt.step()) {
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          val _tmpMessageCount: Int
          _tmpMessageCount = _stmt.getLong(_columnIndexOfMessageCount).toInt()
          _result = ChatSessionEntity(_tmpId,_tmpTitle,_tmpCreatedAt,_tmpUpdatedAt,_tmpMessageCount)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun bump(id: String, updatedAt: Long) {
    val _sql: String =
        "UPDATE chat_sessions SET updated_at = ?, message_count = message_count + 1 WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, updatedAt)
        _argIndex = 2
        _stmt.bindText(_argIndex, id)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteById(id: String) {
    val _sql: String = "DELETE FROM chat_sessions WHERE id = ?"
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

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
