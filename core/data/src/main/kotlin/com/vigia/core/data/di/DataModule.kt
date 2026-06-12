package com.vigia.core.data.di

import android.content.Context
import androidx.room.Room
import com.vigia.core.data.ChatRepository
import com.vigia.core.data.ChatRepositoryImpl
import com.vigia.core.data.db.ChatMessageDao
import com.vigia.core.data.db.ChatSessionDao
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
            // fallbackToDestructiveMigration is intentional for the demo build.
            // Production: replace with explicit Migration objects.
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideChatSessionDao(db: VigiaDatabase): ChatSessionDao = db.chatSessionDao()

    @Provides
    fun provideChatMessageDao(db: VigiaDatabase): ChatMessageDao = db.chatMessageDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindChatRepository(impl: ChatRepositoryImpl): ChatRepository
}
