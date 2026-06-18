package com.vigia.core.sensor.pairing

interface ClaimDeviceRepository {

    /**
     * Registers the 1:1 binding {device_id → wallet_pubkey} on the VIGIA backend.
     *
     * @return [ClaimResult.Ok] if the binding was accepted (new or idempotent re-claim).
     * @return [ClaimResult.DeviceTaken] if the device is already owned by another account.
     * @return [ClaimResult.WalletTaken] if this wallet already owns a different device.
     */
    suspend fun claimDevice(deviceId: String, walletPubkey: String): ClaimResult
}

sealed interface ClaimResult {
    data object Ok           : ClaimResult
    data object DeviceTaken  : ClaimResult
    data object WalletTaken  : ClaimResult
    data class  NetworkError(val message: String) : ClaimResult
}
