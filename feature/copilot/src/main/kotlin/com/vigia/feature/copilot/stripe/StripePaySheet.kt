package com.vigia.feature.copilot.stripe

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.stripe.android.financialconnections.FinancialConnectionsSheet
import com.stripe.android.financialconnections.FinancialConnectionsSheetResult
import com.stripe.android.financialconnections.rememberFinancialConnectionsSheet

/**
 * Launches the Stripe Financial Connections sheet for bank-account onboarding.
 *
 * API contract (verified against com.stripe:financial-connections:21.5.0):
 *   [rememberFinancialConnectionsSheet] returns a [FinancialConnectionsSheet] object.
 *   Calling [FinancialConnectionsSheet.present] launches the native sheet.
 *   [FinancialConnectionsSheet.Configuration] requires clientSecret + publishableKey.
 *
 * Security contract (Phase 4 spec §5.3):
 *   - [clientSecret] must come exclusively from the VIGIA backend (POST /v1/stripe/session).
 *     Never derive it locally.
 *   - [publishableKey] is a non-secret identifier (pk_test_ / pk_live_) — safe in APK.
 *   - Financial data flows only through :core:network (StripePayRepository).
 *     :feature:copilot receives only opaque PayoutStatus sealed states.
 *   - This file is the single Stripe SDK import point in the feature layer.
 */
@Composable
internal fun StripePaySheet(
    clientSecret: String,
    publishableKey: String,
    onResult: (FinancialConnectionsSheetResult) -> Unit,
) {
    val sheet = rememberFinancialConnectionsSheet(onResult)

    // Re-launches if clientSecret rotates (session expiry + refresh from backend).
    LaunchedEffect(clientSecret) {
        sheet.present(
            configuration = FinancialConnectionsSheet.Configuration(
                financialConnectionsSessionClientSecret = clientSecret,
                publishableKey                          = publishableKey,
            ),
        )
    }
}
