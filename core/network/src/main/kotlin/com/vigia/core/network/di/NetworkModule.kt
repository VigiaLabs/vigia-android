package com.vigia.core.network.di

import com.vigia.core.network.BuildConfig
import com.vigia.core.network.mqtt.MqttAlertRepository
import com.vigia.core.network.mqtt.MqttAlertRepositoryImpl
import com.vigia.core.network.sarvam.SarvamSttClient
import com.vigia.core.network.sarvam.SarvamSttClientImpl
import com.vigia.core.network.sarvam.SarvamTtsClient
import com.vigia.core.network.sarvam.SarvamTtsClientImpl
import com.vigia.core.network.search.OkHttpSseSearchClient
import com.vigia.core.network.search.VigiaSearchClient
import com.vigia.core.network.stripe.StripePayRepository
import com.vigia.core.network.stripe.StripePayRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkModule {

    // ── interface bindings ────────────────────────────────────────────────────

    // To swap the search backend, replace OkHttpSseSearchClient here only.
    @Binds @Singleton
    abstract fun bindVigiaSearchClient(impl: OkHttpSseSearchClient): VigiaSearchClient

    @Binds @Singleton
    abstract fun bindMqttAlertRepository(impl: MqttAlertRepositoryImpl): MqttAlertRepository

    @Binds @Singleton
    abstract fun bindStripePayRepository(impl: StripePayRepositoryImpl): StripePayRepository

    @Binds @Singleton
    abstract fun bindSarvamTtsClient(impl: SarvamTtsClientImpl): SarvamTtsClient

    @Binds @Singleton
    abstract fun bindSarvamSttClient(impl: SarvamSttClientImpl): SarvamSttClient

    // ── companion: @Provides cannot live in abstract class directly ───────────

    companion object {

        /**
         * OkHttpClient shared across all network calls.
         *
         * Read timeout is 120s to accommodate long VIGIASearch LangGraph runs.
         * The ALB idle timeout is configured to 300s server-side.
         * callTimeout is intentionally 0 (no global cap) — individual call sites
         * cancel via coroutine scope instead.
         */
        @Provides
        @Singleton
        fun provideOkHttpClient(): OkHttpClient {
            val logging = HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                        else HttpLoggingInterceptor.Level.NONE
            }
            return OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .callTimeout(0, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .addInterceptor(logging)
                .build()
        }

        // @Named("VigiaApiBaseUrl") is provided by :app's AppModule so that
        // BuildConfig.VIGIA_API_BASE_URL (set per product flavour in secrets.properties / CI env)
        // can be injected here without creating a compile-time dependency from :core:network → :app.

    }
}
