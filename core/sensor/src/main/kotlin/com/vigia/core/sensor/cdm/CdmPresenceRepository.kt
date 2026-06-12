package com.vigia.core.sensor.cdm

import com.vigia.core.model.DevicePresenceState
import kotlinx.coroutines.flow.StateFlow

interface CdmPresenceRepository {
    val presenceState: StateFlow<DevicePresenceState>
    suspend fun registerPresenceObserver(associationId: Int)
    suspend fun unregisterPresenceObserver(associationId: Int)
}
