package com.vigia.core.network.stripe

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stripe SDK is imported ONLY in this file. PayoutStatus is the boundary type
 * exposed outward — no com.stripe.* import ever appears outside :core:network.
 */
@Singleton
class StripePayRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : StripePayRepository {

    private val _payoutStatus = MutableStateFlow<PayoutStatus>(PayoutStatus.Idle)
    override val payoutStatus: StateFlow<PayoutStatus> = _payoutStatus.asStateFlow()

    override suspend fun startConnectOnboarding() {
        // Phase 4: Stripe Connect Express onboarding sheet
    }

    override suspend fun initiatePayment(amountCents: Long, currency: String) {
        // Phase 4: PaymentSheet flow
    }

    override suspend fun startFinancialConnectionsSession(): String {
        // Phase 4: call VIGIA backend → return client_secret
        return ""
    }
}
