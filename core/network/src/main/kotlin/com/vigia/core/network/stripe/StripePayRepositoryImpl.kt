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
) : StripePayRepository {

    private val _payoutStatus = MutableStateFlow<PayoutStatus>(PayoutStatus.Idle)
    override val payoutStatus: StateFlow<PayoutStatus> = _payoutStatus.asStateFlow()

    // Wallet address + ownership proof headers are set by the caller via [setWalletProof].
    private var walletAddress: String = ""
    private var walletTimestamp: String = ""
    private var walletSignature: String = ""

    fun setWalletProof(address: String, timestamp: String, signature: String) {
        walletAddress  = address
        walletTimestamp = timestamp
        walletSignature = signature
    }

    override suspend fun startConnectOnboarding(): Unit = withContext(Dispatchers.IO) {
        _payoutStatus.value = PayoutStatus.OnboardingInProgress
        try {
            val response = post("/stripe/onboard-session", JSONObject().apply {
                put("wallet_address", walletAddress)
            })
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

    override suspend fun initiatePayment(amountCents: Long, currency: String) = withContext(Dispatchers.IO) {
        _payoutStatus.value = PayoutStatus.PaymentPending(amountCents, currency)
        try {
            val response = post("/stripe/payout-session", JSONObject().apply {
                put("wallet_address", walletAddress)
                put("amount_cents", amountCents)
                put("currency", currency)
            })
            val clientSecret   = response.getString("client_secret")
            val publishableKey = response.getString("publishable_key")

            // Initialise Stripe SDK with the publishable key returned by our backend.
            PaymentConfiguration.init(context, publishableKey)

            // Payment confirmation is handled by the feature layer (PaymentSheet).
            // The client_secret is stored in PayoutStatus so the UI can launch the sheet.
            Log.d(TAG, "PaymentIntent created → ${amountCents}¢ $currency")
            _payoutStatus.value = PayoutStatus.PaymentSucceeded(clientSecret)
        } catch (e: Exception) {
            Log.e(TAG, "initiatePayment failed", e)
            _payoutStatus.value = PayoutStatus.Failed(e.message ?: "Payment failed")
        }
    }

    override suspend fun startFinancialConnectionsSession(): String = withContext(Dispatchers.IO) {
        val response = post("/stripe/financial-session", JSONObject().apply {
            put("wallet_address", walletAddress)
        })
        val clientSecret   = response.getString("client_secret")
        val publishableKey = response.getString("publishable_key")
        PaymentConfiguration.init(context, publishableKey)
        clientSecret
    }

    private suspend fun post(path: String, body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val url = baseUrl.trimEnd('/') + path
        val request = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .header("X-Wallet-Timestamp", walletTimestamp)
            .header("X-Wallet-Signature", walletSignature)
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
