package com.vigia.core.network.di

import com.vigia.core.network.BuildConfig
import com.vigia.core.network.auth.ApiTokenProvider
import com.vigia.core.network.auth.VigiaAuthInterceptor
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
import javax.inject.Named
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
         * A finite call timeout bounds stuck sockets even when a coroutine owner is
         * lost. SSE callers still have a 120s idle/read timeout and can cancel earlier.
         */
        /**
         * Plain OkHttpClient — no auth header. Used by Sarvam STT/TTS only.
         * Sarvam uses its own API-Subscription-Key header, not Cognito JWT.
         */
        @Provides
        @Singleton
        fun provideOkHttpClient(): OkHttpClient {
            val logging = HttpLoggingInterceptor().apply {
                redactHeader("Authorization")
                redactHeader("X-Wallet-Signature")
                level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                        else HttpLoggingInterceptor.Level.NONE
            }
            return OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .callTimeout(180, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .addInterceptor(logging)
                .build()
        }

        /**
         * Auth-intercepted OkHttpClient for all Vigia backend API calls
         * (Maps, Search, FCM device registration).
         *
         * [VigiaAuthInterceptor] adds "Authorization: Bearer <cognito_id_token>" when
         * the user is signed in to Cognito. In demo mode [ApiTokenProvider.getIdToken]
         * returns null and the header is omitted — requests to public routes still work
         * and protected routes fail fast with 403 instead of silently.
         */
        @Provides
        @Singleton
        @Named("VigiaOkHttpClient")
        fun provideVigiaOkHttpClient(
            authInterceptor: VigiaAuthInterceptor,
        ): OkHttpClient {
            val logging = HttpLoggingInterceptor().apply {
                // Never print Cognito/JWT or payment proof material, even in debug builds.
                redactHeader("Authorization")
                redactHeader("X-Wallet-Signature")
                level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                        else HttpLoggingInterceptor.Level.NONE
            }
            return OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .callTimeout(180, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .addInterceptor(authInterceptor)
                .addInterceptor(logging)
                .build()
        }

        // @Named("VigiaApiBaseUrl") is provided by :app's AppModule so that
        // BuildConfig.VIGIA_API_BASE_URL (set per product flavour in secrets.properties / CI env)
        // can be injected here without creating a compile-time dependency from :core:network → :app.

    }
}
