package com.vigia.core.sensor.di

import com.vigia.core.sensor.ble.BleDataStreamer
import com.vigia.core.sensor.ble.BleDataStreamerImpl
import com.vigia.core.sensor.ble.BleRepository
import com.vigia.core.sensor.ble.BleRepositoryImpl
import com.vigia.core.sensor.cdm.CdmPresenceRepository
import com.vigia.core.sensor.cdm.CdmPresenceRepositoryImpl
import com.vigia.core.sensor.pairing.ClaimDeviceRepository
import com.vigia.core.sensor.pairing.ClaimDeviceRepositoryImpl
import com.vigia.core.sensor.pairing.PairingRepository
import com.vigia.core.sensor.pairing.PairingRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SensorModule {

    @Binds @Singleton
    abstract fun bindCdmPresenceRepository(impl: CdmPresenceRepositoryImpl): CdmPresenceRepository

    @Binds @Singleton
    abstract fun bindBleRepository(impl: BleRepositoryImpl): BleRepository

    @Binds @Singleton
    abstract fun bindBleDataStreamer(impl: BleDataStreamerImpl): BleDataStreamer

    @Binds @Singleton
    abstract fun bindPairingRepository(impl: PairingRepositoryImpl): PairingRepository

    @Binds @Singleton
    abstract fun bindClaimDeviceRepository(impl: ClaimDeviceRepositoryImpl): ClaimDeviceRepository
}
