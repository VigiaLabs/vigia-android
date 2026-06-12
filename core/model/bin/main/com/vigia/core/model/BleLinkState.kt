package com.vigia.core.model

sealed interface BleLinkState {
    data object Idle        : BleLinkState
    data object Scanning    : BleLinkState
    data class  Connecting(val deviceAddress: String) : BleLinkState
    data object Pairing     : BleLinkState
    data object Handshaking : BleLinkState
    data object Bound       : BleLinkState
    data class  Error(val reason: BleLinkError, val retryCount: Int = 0) : BleLinkState
}

enum class BleLinkError {
    SCAN_FAILED,
    CONNECTION_TIMEOUT,
    PAIRING_FAILED,
    HANDSHAKE_FAILED,
    GATT_ERROR,
}
