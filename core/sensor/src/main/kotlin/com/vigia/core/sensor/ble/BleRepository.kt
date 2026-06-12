package com.vigia.core.sensor.ble

import com.vigia.core.model.BleLinkState
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface BleRepository {
    val linkState: StateFlow<BleLinkState>
    suspend fun startScan(targetDeviceAddress: String)
    suspend fun disconnect()
}
