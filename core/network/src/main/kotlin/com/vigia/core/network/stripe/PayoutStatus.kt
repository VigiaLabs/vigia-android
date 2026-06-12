package com.vigia.core.network.stripe

/**
 * Domain-level payout state exposed to :feature:copilot.
 *
 * The Stripe SDK is an implementation detail of :core:network.
 * No Stripe type, class, or import ever crosses this boundary.
 * Feature-layer code observes only this sealed interface.
 */
sealed interface PayoutStatus {
    data object Idle : PayoutStatus
    data object AwaitingOnboarding : PayoutStatus
    data object OnboardingInProgress : PayoutStatus
    data class  OnboardingComplete(val accountId: String) : PayoutStatus
    data class  PaymentPending(val amountCents: Long, val currency: String) : PayoutStatus
    data class  PaymentSucceeded(val chargeId: String) : PayoutStatus
    data class  Failed(val userMessage: String) : PayoutStatus
}
