package com.vigia.core.sensor.pairing

interface ClaimDeviceRepository {

    /**
     * Registers the 1:1 binding {device_id → wallet_pubkey} on the VIGIA backend.
     *
     * P0-6: the backend now requires dual proof-of-possession over the message
     *   VIGIA-BIND:<device_id>:<wallet_pubkey>:<ts>
     *   - [walletSig]: Ed25519 signature by the wallet key (base58) — produced here.
     *   - [deviceSig]: ATECC608A ECDSA P-256 signature (hex) by the paired Pi over the
     *     SAME message. Must be obtained from the Pi over BLE — requires firmware support
     *     for signing an app-supplied binding challenge. Pass "" until that lands; the
     *     server will reject (401) and the caller falls back to local-only pairing.
     *
     * @return [ClaimResult.Ok] if the binding was accepted (new or idempotent re-claim).
     * @return [ClaimResult.DeviceTaken] if the device is already owned by another account.
     * @return [ClaimResult.WalletTaken] if this wallet already owns a different device.
     */
    suspend fun claimDevice(
        deviceId: String,
        walletPubkey: String,
        ts: Long,
        walletSig: String,
        deviceSig: String,
    ): ClaimResult
}

sealed interface ClaimResult {
    data object Ok           : ClaimResult
    data object DeviceTaken  : ClaimResult
    data object WalletTaken  : ClaimResult
    data class  NetworkError(val message: String) : ClaimResult
}
