package com.vigia.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ChatSessionEntity::class, ChatMessageEntity::class, HarshEventEntity::class],
    version  = 2,
    exportSchema = false,
)
abstract class VigiaDatabase : RoomDatabase() {
    abstract fun chatSessionDao(): ChatSessionDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun harshEventDao(): HarshEventDao
}
