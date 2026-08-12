package com.vigia.core.auth.di

import com.vigia.core.auth.AmplifyAuthRepository
import com.vigia.core.auth.AmplifyInitializer
import com.vigia.core.auth.AuthRepository
import com.vigia.core.auth.DemoAuthRepository
import com.vigia.core.auth.MisconfiguredAuthRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Binds demo auth only for the explicit demo flavour. Production configuration
 * failures bind a blocking implementation instead of silently weakening identity.
 */
@Module
@InstallIn(SingletonComponent::class)
object AuthModule {

    @Provides
    @Singleton
    fun provideAuthRepository(
        amplify: Provider<AmplifyAuthRepository>,
        demo: Provider<DemoAuthRepository>,
        misconfigured: Provider<MisconfiguredAuthRepository>,
    ): AuthRepository =
        when {
            AmplifyInitializer.isDemoBuild -> demo.get()
            AmplifyInitializer.isConfigured -> amplify.get()
            else -> misconfigured.get()
        }
}
