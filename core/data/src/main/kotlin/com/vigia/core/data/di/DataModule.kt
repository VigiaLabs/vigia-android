package com.vigia.core.data.di

import android.content.Context
import androidx.room.Room
import com.vigia.core.data.ChatRepository
import com.vigia.core.data.ChatRepositoryImpl
import com.vigia.core.data.AlertInboxRepository
import com.vigia.core.data.AlertInboxRepositoryImpl
import com.vigia.core.data.db.AlertInboxDao
import com.vigia.core.data.db.ChatMessageDao
import com.vigia.core.data.db.ChatSessionDao
import com.vigia.core.data.db.HarshEventDao
import com.vigia.core.data.db.MIGRATION_1_2
import com.vigia.core.data.db.MIGRATION_2_3
import com.vigia.core.data.db.VigiaDatabase
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideVigiaDatabase(@ApplicationContext context: Context): VigiaDatabase =
        Room.databaseBuilder(context, VigiaDatabase::class.java, "vigia_db")
            .addMigrations(MIGRATION_1_2)
            .addMigrations(MIGRATION_2_3)
            .build()

    @Provides
    fun provideChatSessionDao(db: VigiaDatabase): ChatSessionDao = db.chatSessionDao()

    @Provides
    fun provideChatMessageDao(db: VigiaDatabase): ChatMessageDao = db.chatMessageDao()

    @Provides
    fun provideHarshEventDao(db: VigiaDatabase): HarshEventDao = db.harshEventDao()

    @Provides
    fun provideAlertInboxDao(db: VigiaDatabase): AlertInboxDao = db.alertInboxDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindChatRepository(impl: ChatRepositoryImpl): ChatRepository

    @Binds
    @Singleton
    abstract fun bindAlertInboxRepository(impl: AlertInboxRepositoryImpl): AlertInboxRepository
}
