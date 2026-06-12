package com.vigia.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatSessionDao {

    @Query("SELECT * FROM chat_sessions ORDER BY updated_at DESC")
    fun allSessions(): Flow<List<ChatSessionEntity>>

    @Query("SELECT * FROM chat_sessions WHERE id = :id LIMIT 1")
    suspend fun sessionById(id: String): ChatSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: ChatSessionEntity)

    /**
     * Targeted metadata update. MUST NOT use INSERT-OR-REPLACE: chat_messages has a
     * CASCADE foreign key on session id, so REPLACE-ing the session row deletes the
     * old row first — cascading away every message in it. A plain UPDATE keeps the
     * row (and its messages) intact.
     */
    @Query("UPDATE chat_sessions SET updated_at = :updatedAt, message_count = message_count + 1 WHERE id = :id")
    suspend fun bump(id: String, updatedAt: Long)

    @Query("DELETE FROM chat_sessions WHERE id = :id")
    suspend fun deleteById(id: String)
}
