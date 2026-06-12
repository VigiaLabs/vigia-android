package com.vigia.core.model

sealed interface DevicePresenceState {
    data object Unknown : DevicePresenceState
    data object Present : DevicePresenceState
    data object Absent  : DevicePresenceState
}
