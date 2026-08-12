package com.vigia.core.network.stripe

import kotlinx.coroutines.flow.StateFlow

/**
 * Public API of the Stripe payout subsystem.
 * Stripe SDK types never appear in the return types or parameters.
 */
interface StripePayRepository {
    val payoutStatus: StateFlow<PayoutStatus>
    suspend fun startConnectOnboarding(proof: WalletProof)
    suspend fun initiatePayment(amountCents: Long, currency: String, proof: WalletProof)
    suspend fun startFinancialConnectionsSession(proof: WalletProof): String  // returns opaque client_secret
}

/** Per-request wallet proof. It is passed explicitly to avoid mutable singleton request state. */
data class WalletProof(
    val address: String,
    val timestamp: String,
    val signature: String,
)
