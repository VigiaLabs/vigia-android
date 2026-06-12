package com.vigia.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity        = ChatSessionEntity::class,
            parentColumns = ["id"],
            childColumns  = ["session_id"],
            onDelete      = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("session_id")],
)
data class ChatMessageEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "session_id")   val sessionId: String,
    val role: String,
    val body: String,
    // JSON arrays stored as text — serialized by ChatRepositoryImpl
    val sources: String,
    @ColumnInfo(name = "reasoning_steps") val reasoningSteps: String,
    @ColumnInfo(name = "latency_ms")   val latencyMs: Long,
    @ColumnInfo(name = "attachment_uri") val attachmentUri: String?,
    val status: String,
    @ColumnInfo(name = "created_at")   val createdAt: Long,
)
