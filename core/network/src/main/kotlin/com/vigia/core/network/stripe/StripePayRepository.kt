package com.vigia.core.network.stripe

import kotlinx.coroutines.flow.StateFlow

/**
 * Public API of the Stripe payout subsystem.
 * Stripe SDK types never appear in the return types or parameters.
 */
interface StripePayRepository {
    val payoutStatus: StateFlow<PayoutStatus>
    suspend fun startConnectOnboarding()
    suspend fun initiatePayment(amountCents: Long, currency: String)
    suspend fun startFinancialConnectionsSession(): String  // returns opaque client_secret
}
