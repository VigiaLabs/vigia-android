package com.vigia.core.sensor.ble

import com.vigia.core.model.BleLinkState
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BleRepositoryImpl @Inject constructor(
    private val linkManager: BleLinkManager,
) : BleRepository {

    override val linkState: StateFlow<BleLinkState> = linkManager.linkState

    override suspend fun startScan(targetDeviceAddress: String, piPublicKeyBytes: ByteArray?) {
        linkManager.connect(targetDeviceAddress, piPublicKeyBytes)
    }

    override suspend fun disconnect() {
        linkManager.disconnect()
    }

    override suspend fun requestDims(dimsCode: Byte) {
        linkManager.requestDims(dimsCode)
    }

    override suspend fun sendTtcThreshold(ttcS: Float) {
        linkManager.sendTtcThreshold(ttcS)
    }
}
