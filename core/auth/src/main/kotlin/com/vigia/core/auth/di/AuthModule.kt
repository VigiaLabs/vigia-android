package com.vigia.core.auth.di

import com.vigia.core.auth.AmplifyAuthRepository
import com.vigia.core.auth.AmplifyInitializer
import com.vigia.core.auth.AuthRepository
import com.vigia.core.auth.DemoAuthRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Binds the real Cognito backend when Amplify configured at startup, otherwise
 * the runnable demo backend. [Provider] ensures only the chosen impl is built.
 */
@Module
@InstallIn(SingletonComponent::class)
object AuthModule {

    @Provides
    @Singleton
    fun provideAuthRepository(
        amplify: Provider<AmplifyAuthRepository>,
        demo: Provider<DemoAuthRepository>,
    ): AuthRepository =
        if (AmplifyInitializer.isConfigured) amplify.get() else demo.get()
}
