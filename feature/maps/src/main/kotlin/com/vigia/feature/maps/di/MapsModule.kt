package com.vigia.feature.maps.di

import com.vigia.feature.maps.data.IngestionApiService
import com.vigia.feature.maps.data.InnovationApiService
import com.vigia.feature.maps.data.MapsRepository
import com.vigia.feature.maps.data.MapsRepositoryImpl
import com.vigia.feature.maps.data.SessionApiService
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MapsModule {

    @Binds @Singleton
    abstract fun bindMapsRepository(impl: MapsRepositoryImpl): MapsRepository

    companion object {

        private const val SESSION_BASE_URL    = "https://eepqy4yku7.execute-api.us-east-1.amazonaws.com/prod/"
        private const val INNOVATION_BASE_URL = "https://p4qc9upgsf.execute-api.us-east-1.amazonaws.com/prod/"
        private const val INGESTION_BASE_URL  = "https://eepqy4yku7.execute-api.us-east-1.amazonaws.com/prod/"

        @Provides @Singleton @Named("SessionApi")
        fun provideSessionRetrofit(@Named("VigiaOkHttpClient") client: OkHttpClient): Retrofit =
            Retrofit.Builder()
                .baseUrl(SESSION_BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

        @Provides @Singleton @Named("InnovationApi")
        fun provideInnovationRetrofit(@Named("VigiaOkHttpClient") client: OkHttpClient): Retrofit =
            Retrofit.Builder()
                .baseUrl(INNOVATION_BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

        @Provides @Singleton
        fun provideSessionApiService(@Named("SessionApi") retrofit: Retrofit): SessionApiService =
            retrofit.create(SessionApiService::class.java)

        @Provides @Singleton
        fun provideInnovationApiService(@Named("InnovationApi") retrofit: Retrofit): InnovationApiService =
            retrofit.create(InnovationApiService::class.java)

        @Provides @Singleton
        fun provideIngestionApiService(@Named("SessionApi") retrofit: Retrofit): IngestionApiService =
            retrofit.create(IngestionApiService::class.java)
    }
}
