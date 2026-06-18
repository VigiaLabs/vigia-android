package com.vigia.feature.pairing

import android.content.IntentSender

/** UI state for the QR pairing flow (design spec §5). */
sealed interface PairingState {

    /** Camera preview is active, waiting for a QR code. */
    data object Scanning : PairingState

    /**
     * Valid QR detected. Waiting for the system CDM chooser to be launched.
     * [intentSender] must be delivered to [PairingScreen] so the Activity can launch it.
     */
    data class AwaitingCdmLaunch(
        val intentSender: IntentSender,
        val mac: String,
        val piPublicKeyBytes: ByteArray,
        val deviceId: String,
    ) : PairingState

    /** CDM pairing dialog dismissed / rejected. User can retry. */
    data object PairingRejected : PairingState

    /** CDM.associate() succeeded and pairing data has been persisted. */
    data class Success(val deviceId: String) : PairingState

    data class Error(val message: String) : PairingState

    /**
     * Server rejected the claim: device already bound to another account ("device_taken"),
     * or this account already owns a different device ("wallet_taken").
     */
    data class DeviceAlreadyClaimed(val deviceId: String, val reason: String) : PairingState
}
