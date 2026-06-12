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
public class ChatMessageDao_Impl(
  __db: RoomDatabase,
) : ChatMessageDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfChatMessageEntity: EntityInsertAdapter<ChatMessageEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfChatMessageEntity = object : EntityInsertAdapter<ChatMessageEntity>() {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `chat_messages` (`id`,`session_id`,`role`,`body`,`sources`,`reasoning_steps`,`latency_ms`,`attachment_uri`,`status`,`created_at`) VALUES (?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: ChatMessageEntity) {
        statement.bindText(1, entity.id)
        statement.bindText(2, entity.sessionId)
        statement.bindText(3, entity.role)
        statement.bindText(4, entity.body)
        statement.bindText(5, entity.sources)
        statement.bindText(6, entity.reasoningSteps)
        statement.bindLong(7, entity.latencyMs)
        val _tmpAttachmentUri: String? = entity.attachmentUri
        if (_tmpAttachmentUri == null) {
          statement.bindNull(8)
        } else {
          statement.bindText(8, _tmpAttachmentUri)
        }
        statement.bindText(9, entity.status)
        statement.bindLong(10, entity.createdAt)
      }
    }
  }

  public override suspend fun insert(message: ChatMessageEntity): Unit = performSuspending(__db,
      false, true) { _connection ->
    __insertAdapterOfChatMessageEntity.insert(_connection, message)
  }

  public override fun messagesForSession(sessionId: String): Flow<List<ChatMessageEntity>> {
    val _sql: String = "SELECT * FROM chat_messages WHERE session_id = ? ORDER BY created_at ASC"
    return createFlow(__db, false, arrayOf("chat_messages")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, sessionId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfSessionId: Int = getColumnIndexOrThrow(_stmt, "session_id")
        val _columnIndexOfRole: Int = getColumnIndexOrThrow(_stmt, "role")
        val _columnIndexOfBody: Int = getColumnIndexOrThrow(_stmt, "body")
        val _columnIndexOfSources: Int = getColumnIndexOrThrow(_stmt, "sources")
        val _columnIndexOfReasoningSteps: Int = getColumnIndexOrThrow(_stmt, "reasoning_steps")
        val _columnIndexOfLatencyMs: Int = getColumnIndexOrThrow(_stmt, "latency_ms")
        val _columnIndexOfAttachmentUri: Int = getColumnIndexOrThrow(_stmt, "attachment_uri")
        val _columnIndexOfStatus: Int = getColumnIndexOrThrow(_stmt, "status")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _result: MutableList<ChatMessageEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: ChatMessageEntity
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpSessionId: String
          _tmpSessionId = _stmt.getText(_columnIndexOfSessionId)
          val _tmpRole: String
          _tmpRole = _stmt.getText(_columnIndexOfRole)
          val _tmpBody: String
          _tmpBody = _stmt.getText(_columnIndexOfBody)
          val _tmpSources: String
          _tmpSources = _stmt.getText(_columnIndexOfSources)
          val _tmpReasoningSteps: String
          _tmpReasoningSteps = _stmt.getText(_columnIndexOfReasoningSteps)
          val _tmpLatencyMs: Long
          _tmpLatencyMs = _stmt.getLong(_columnIndexOfLatencyMs)
          val _tmpAttachmentUri: String?
          if (_stmt.isNull(_columnIndexOfAttachmentUri)) {
            _tmpAttachmentUri = null
          } else {
            _tmpAttachmentUri = _stmt.getText(_columnIndexOfAttachmentUri)
          }
          val _tmpStatus: String
          _tmpStatus = _stmt.getText(_columnIndexOfStatus)
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          _item =
              ChatMessageEntity(_tmpId,_tmpSessionId,_tmpRole,_tmpBody,_tmpSources,_tmpReasoningSteps,_tmpLatencyMs,_tmpAttachmentUri,_tmpStatus,_tmpCreatedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteAllForSession(sessionId: String) {
    val _sql: String = "DELETE FROM chat_messages WHERE session_id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, sessionId)
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
