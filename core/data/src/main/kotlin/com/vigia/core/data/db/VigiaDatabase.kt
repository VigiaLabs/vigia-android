package com.vigia.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ChatSessionEntity::class,
        ChatMessageEntity::class,
        HarshEventEntity::class,
        AlertInboxEntity::class,
    ],
    version  = 3,
    exportSchema = true,
)
abstract class VigiaDatabase : RoomDatabase() {
    abstract fun chatSessionDao(): ChatSessionDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun harshEventDao(): HarshEventDao
    abstract fun alertInboxDao(): AlertInboxDao
}
