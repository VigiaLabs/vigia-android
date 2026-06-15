package com.vigia.core.wallet.di

import android.content.Context
import com.vigia.core.wallet.Ed25519KeyStore
import com.vigia.core.wallet.WalletRepository
import com.vigia.core.wallet.WalletRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class WalletModule {

    @Binds
    @Singleton
    abstract fun bindWalletRepository(impl: WalletRepositoryImpl): WalletRepository

    companion object {

        @Provides
        @Singleton
        fun provideEd25519KeyStore(@ApplicationContext context: Context): Ed25519KeyStore =
            Ed25519KeyStore(context)

        @Provides
        @Singleton
        @Named("WalletOkHttpClient")
        fun provideWalletHttpClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .addInterceptor(HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                })
                .build()
    }
}
