package com.vigia.core.data

import com.vigia.core.model.ChatMessage
import com.vigia.core.model.ChatSession
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun allSessions(): Flow<List<ChatSession>>
    fun messagesForSession(sessionId: String): Flow<List<ChatMessage>>
    suspend fun createSession(session: ChatSession)
    suspend fun bumpSession(sessionId: String)
    suspend fun deleteSession(sessionId: String)
    suspend fun insertMessage(message: ChatMessage)
}
