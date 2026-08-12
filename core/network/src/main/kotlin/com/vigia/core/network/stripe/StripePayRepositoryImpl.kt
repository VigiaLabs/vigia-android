package com.vigia.core.network.stripe

import android.content.Context
import android.util.Log
import com.stripe.android.PaymentConfiguration
import com.stripe.android.financialconnections.FinancialConnectionsSheet
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Stripe SDK is imported ONLY in this file. PayoutStatus is the boundary type
 * exposed outward — no com.stripe.* import ever appears outside :core:network.
 *
 * All three flows call the VIGIA backend (StripePayoutFn Lambda), which holds
 * the Stripe secret key in AWS Secrets Manager. The publishable key (non-secret)
 * is returned by the backend for client-side SDK initialisation.
 */
@Singleton
class StripePayRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    @Named("VigiaOkHttpClient") private val okHttpClient: OkHttpClient,
    @Named("VigiaApiBaseUrl")   private val baseUrl: String,
    @Named("PayoutEnabled") private val payoutEnabled: Boolean,
) : StripePayRepository {

    private val _payoutStatus = MutableStateFlow<PayoutStatus>(PayoutStatus.Idle)
    override val payoutStatus: StateFlow<PayoutStatus> = _payoutStatus.asStateFlow()

    override suspend fun startConnectOnboarding(proof: WalletProof): Unit = withContext(Dispatchers.IO) {
        if (!payoutEnabled) {
            _payoutStatus.value = PayoutStatus.Disabled
            return@withContext
        }
        _payoutStatus.value = PayoutStatus.OnboardingInProgress
        try {
            val response = post("/stripe/onboard-session", JSONObject().apply {
                put("wallet_address", proof.address)
            }, proof)
            val onboardingUrl = response.getString("onboarding_url")
            val accountId     = response.getString("account_id")
            // The caller (ViewModel) opens a Custom Tab to onboardingUrl.
            _payoutStatus.value = PayoutStatus.OnboardingComplete(accountId)
            Log.d(TAG, "Stripe onboarding started → $onboardingUrl")
        } catch (e: Exception) {
            Log.e(TAG, "startConnectOnboarding failed", e)
            _payoutStatus.value = PayoutStatus.Failed(e.message ?: "Onboarding failed")
        }
    }

    override suspend fun initiatePayment(amountCents: Long, currency: String, proof: WalletProof) = withContext(Dispatchers.IO) {
        if (!payoutEnabled) {
            _payoutStatus.value = PayoutStatus.Disabled
            return@withContext
        }
        _payoutStatus.value = PayoutStatus.PaymentPending(amountCents, currency)
        try {
            val response = post("/stripe/payout-session", JSONObject().apply {
                put("wallet_address", proof.address)
                put("amount_cents", amountCents)
                put("currency", currency)
            }, proof)
            val clientSecret   = response.getString("client_secret")
            val publishableKey = response.getString("publishable_key")

            // Initialise Stripe SDK with the publishable key returned by our backend.
            PaymentConfiguration.init(context, publishableKey)

            // Payment confirmation/webhook settlement is handled after the intent is created.
            // A client secret is never a charge ID or a successful payout.
            Log.d(TAG, "PaymentIntent created → ${amountCents}¢ $currency")
            _payoutStatus.value = PayoutStatus.PaymentIntentCreated(clientSecret)
        } catch (e: Exception) {
            Log.e(TAG, "initiatePayment failed", e)
            _payoutStatus.value = PayoutStatus.Failed(e.message ?: "Payment failed")
        }
    }

    override suspend fun startFinancialConnectionsSession(proof: WalletProof): String = withContext(Dispatchers.IO) {
        check(payoutEnabled) { "Payout is disabled in this build" }
        val response = post("/stripe/financial-session", JSONObject().apply {
            put("wallet_address", proof.address)
        }, proof)
        val clientSecret   = response.getString("client_secret")
        val publishableKey = response.getString("publishable_key")
        PaymentConfiguration.init(context, publishableKey)
        clientSecret
    }

    private suspend fun post(path: String, body: JSONObject, proof: WalletProof): JSONObject = withContext(Dispatchers.IO) {
        val url = baseUrl.trimEnd('/') + path
        val request = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .header("X-Wallet-Timestamp", proof.timestamp)
            .header("X-Wallet-Signature", proof.signature)
            .build()

        val responseBody = okHttpClient.newCall(request).execute().use { response ->
            val raw = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                val msg = runCatching { JSONObject(raw).getString("error") }.getOrDefault(raw)
                throw Exception("HTTP ${response.code}: $msg")
            }
            raw
        }
        JSONObject(responseBody)
    }

    private companion object {
        const val TAG = "StripePayRepo"
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
