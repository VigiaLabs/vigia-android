package com.vigia.copilot.di

import com.vigia.copilot.BuildConfig
import com.vigia.core.sensor.BlackboxConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

/**
 * Provides build-flavour-specific values that :core:sensor cannot access directly.
 *
 * [BlackboxConfig.macAddress] comes from BuildConfig (injected by Gradle product flavours):
 *   - demo  → "00:00:00:00:00:00" (placeholder — no physical device needed)
 *   - prod  → real MAC from CI env var BLACKBOX_MAC
 *
 * [BlackboxConfig.associationId] starts at 0 (no active association).
 * The real association ID is written to DataStore after the first successful CDM pairing
 * in Phase 3 and passed to [CdmPresenceRepository.registerPresenceObserver].
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideBlackboxConfig(): BlackboxConfig = BlackboxConfig(
        macAddress    = BuildConfig.BLACKBOX_MAC,
        associationId = 0,
    )

    /**
     * AWS IoT Core MQTT broker URI — sourced from BuildConfig (injected by the
     * convention plugin from secrets.properties locally, or env vars in CI).
     * Format: ssl://<endpoint>.iot.<region>.amazonaws.com:8883
     */
    @Provides
    @Singleton
    @Named("MqttBrokerUri")
    fun provideMqttBrokerUri(): String = BuildConfig.MQTT_BROKER_URI
}
