package com.vigia.core.data

import com.vigia.core.data.db.ChatMessageDao
import com.vigia.core.data.db.ChatMessageEntity
import com.vigia.core.data.db.ChatSessionDao
import com.vigia.core.data.db.ChatSessionEntity
import com.vigia.core.model.ChatMessage
import com.vigia.core.model.ChatSession
import com.vigia.core.model.MessageRole
import com.vigia.core.model.MessageSource
import com.vigia.core.model.MessageStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val sessionDao: ChatSessionDao,
    private val messageDao: ChatMessageDao,
) : ChatRepository {

    override fun allSessions(): Flow<List<ChatSession>> =
        sessionDao.allSessions().map { list -> list.map { it.toDomain() } }

    override fun messagesForSession(sessionId: String): Flow<List<ChatMessage>> =
        messageDao.messagesForSession(sessionId).map { list -> list.map { it.toDomain() } }

    override suspend fun createSession(session: ChatSession) =
        sessionDao.upsert(session.toEntity())

    override suspend fun bumpSession(sessionId: String) {
        // Plain UPDATE — NOT upsert. The session is the CASCADE parent of its
        // messages; an INSERT-OR-REPLACE here would delete them all.
        sessionDao.bump(sessionId, System.currentTimeMillis())
    }

    override suspend fun deleteSession(sessionId: String) =
        sessionDao.deleteById(sessionId)

    override suspend fun insertMessage(message: ChatMessage) =
        messageDao.insert(message.toEntity())

    // ── mapping ───────────────────────────────────────────────────────────────

    private fun ChatSessionEntity.toDomain() = ChatSession(
        id           = id,
        title        = title,
        createdAt    = createdAt,
        updatedAt    = updatedAt,
        messageCount = messageCount,
    )

    private fun ChatSession.toEntity() = ChatSessionEntity(
        id           = id,
        title        = title,
        createdAt    = createdAt,
        updatedAt    = updatedAt,
        messageCount = messageCount,
    )

    private fun ChatMessageEntity.toDomain() = ChatMessage(
        id             = id,
        sessionId      = sessionId,
        role           = MessageRole.valueOf(role),
        body           = body,
        sources        = deserializeSources(sources),
        reasoningSteps = deserializeStrings(reasoningSteps),
        latencyMs      = latencyMs,
        attachmentUri  = attachmentUri,
        status         = MessageStatus.valueOf(status),
        createdAt      = createdAt,
    )

    private fun ChatMessage.toEntity() = ChatMessageEntity(
        id             = id,
        sessionId      = sessionId,
        role           = role.name,
        body           = body,
        sources        = serializeSources(sources),
        reasoningSteps = serializeStrings(reasoningSteps),
        latencyMs      = latencyMs,
        attachmentUri  = attachmentUri,
        status         = status.name,
        createdAt      = createdAt,
    )

    // ── JSON serialization (org.json — part of Android SDK, no extra dep) ────

    private fun serializeStrings(list: List<String>): String {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        return arr.toString()
    }

    private fun deserializeStrings(json: String): List<String> {
        if (json.isBlank() || json == "[]") return emptyList()
        val arr = JSONArray(json)
        return List(arr.length()) { arr.getString(it) }
    }

    private fun serializeSources(list: List<MessageSource>): String {
        val arr = JSONArray()
        list.forEach { src ->
            arr.put(
                JSONObject().apply {
                    put("id", src.id)
                    put("url", src.url)
                    put("label", src.label)
                    put("trustLevel", src.trustLevel)
                }
            )
        }
        return arr.toString()
    }

    private fun deserializeSources(json: String): List<MessageSource> {
        if (json.isBlank() || json == "[]") return emptyList()
        val arr = JSONArray(json)
        return List(arr.length()) { i ->
            val obj = arr.getJSONObject(i)
            MessageSource(
                id         = obj.optString("id"),
                url        = obj.optString("url"),
                label      = obj.optString("label"),
                trustLevel = obj.optString("trustLevel"),
            )
        }
    }
}
