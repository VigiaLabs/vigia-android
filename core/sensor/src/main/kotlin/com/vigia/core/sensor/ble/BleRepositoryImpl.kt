package com.vigia.core.sensor.ble

import com.vigia.core.model.BleLinkState
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin delegation layer. All state and GATT logic lives in [BleLinkManager],
 * which is testable in isolation without needing a real [BleRepository] consumer.
 */
@Singleton
class BleRepositoryImpl @Inject constructor(
    private val linkManager: BleLinkManager,
) : BleRepository {

    override val linkState: StateFlow<BleLinkState> = linkManager.linkState

    override suspend fun startScan(targetDeviceAddress: String) {
        linkManager.connect(targetDeviceAddress)
    }

    override suspend fun disconnect() {
        linkManager.disconnect()
    }
}
