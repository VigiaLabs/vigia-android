package com.vigia.core.sensor.di

import com.vigia.core.sensor.ble.BleDataStreamer
import com.vigia.core.sensor.ble.BleDataStreamerImpl
import com.vigia.core.sensor.ble.BleRepository
import com.vigia.core.sensor.ble.BleRepositoryImpl
import com.vigia.core.sensor.cdm.CdmPresenceRepository
import com.vigia.core.sensor.cdm.CdmPresenceRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds sensor-layer interfaces to their implementations.
 *
 * [BlackboxConfig] is NOT bound here — it is provided by the :app module's AppModule
 * so that the flavour-specific MAC address and association ID (from BuildConfig) can be
 * injected without :core:sensor having a compile-time dependency on :app.
 *
 * [KeystoreManager] and [BleLinkManager] use @Inject constructors and need no explicit binding.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SensorModule {

    @Binds @Singleton
    abstract fun bindCdmPresenceRepository(impl: CdmPresenceRepositoryImpl): CdmPresenceRepository

    @Binds @Singleton
    abstract fun bindBleRepository(impl: BleRepositoryImpl): BleRepository

    @Binds @Singleton
    abstract fun bindBleDataStreamer(impl: BleDataStreamerImpl): BleDataStreamer
}
