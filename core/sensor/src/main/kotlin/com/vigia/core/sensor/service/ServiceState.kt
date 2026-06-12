package com.vigia.core.sensor.service

import com.vigia.core.model.BleLinkState

/**
 * Hilt-scoped observable state for [VigiaForegroundService].
 * Consumed by ViewModels in :feature:copilot to drive UI chrome.
 */
sealed interface ServiceState {
    data object Idle                                    : ServiceState
    data object AwaitingPresence                        : ServiceState
    data class  Connecting(val deviceAddress: String)   : ServiceState
    data class  Connected(val deviceAddress: String)    : ServiceState
    data class  Error(val cause: String, val retries: Int = 0) : ServiceState

    companion object {
        fun fromLinkState(address: String, link: BleLinkState): ServiceState = when (link) {
            BleLinkState.Idle              -> AwaitingPresence
            BleLinkState.Scanning          -> Connecting(address)
            is BleLinkState.Connecting     -> Connecting(link.deviceAddress)
            BleLinkState.Pairing           -> Connecting(address)
            BleLinkState.Handshaking       -> Connecting(address)
            BleLinkState.Bound             -> Connected(address)
            is BleLinkState.Error          -> Error(link.reason.name, link.retryCount)
        }
    }
}
