package com.vigia.core.wallet

import kotlinx.coroutines.flow.StateFlow

interface WalletRepository {

    /** Live balance/identity state. Emits immediately from cache, refreshes via [refreshBalance]. */
    val state: StateFlow<WalletState>

    /**
     * Generates Ed25519 keypair (no-op if already provisioned) and registers the device
     * with the VIGIA backend. Safe to call multiple times — idempotent on both sides.
     */
    suspend fun ensureProvisioned()

    /** Fetches current balance from the rewards ledger API and updates [state]. */
    suspend fun refreshBalance()

    /**
     * Builds the exact signed payload required by the validator Lambda.
     * Returned [TelemetrySignature] must be included verbatim in the telemetry POST body.
     */
    /**
     * [frameSha256] is the lowercase hex SHA-256 digest of the raw JPEG frame bytes,
     * if a frame is included in the upload. When non-null the digest is appended to the
     * signed payload, binding the frame to the signature (H2 integrity fix).
     */
    fun signTelemetry(
        hazardType: String,
        lat: Double,
        lon: Double,
        timestamp: Long,
        confidence: Double,
        frameSha256: String? = null,
    ): TelemetrySignature
}

data class WalletState(
    val publicKey: String = "",
    val pendingBalanceMicroVigia: Long = 0L,
    val totalEarnedMicroVigia: Long = 0L,
    val totalClaimedMicroVigia: Long = 0L,
    val isSyncing: Boolean = false,
    val isProvisioned: Boolean = false,
) {
    val pendingBalanceVigia: Double get() = pendingBalanceMicroVigia / 1_000_000.0
    val totalEarnedVigia: Double get() = totalEarnedMicroVigia / 1_000_000.0
}

data class TelemetrySignature(
    val publicKey: String,
    val signature: String,
)
