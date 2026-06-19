package com.vigia.core.wallet

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class WalletRepositoryImpl @Inject constructor(
    private val keyStore: Ed25519KeyStore,
    @Named("WalletOkHttpClient") private val httpClient: OkHttpClient,
    @Named("VigiaApiBaseUrl") private val baseUrl: String,
) : WalletRepository {

    private val _state = MutableStateFlow(WalletState())
    override val state: StateFlow<WalletState> = _state.asStateFlow()

    override suspend fun ensureProvisioned() = withContext(Dispatchers.IO) {
        keyStore.provision()
        val pubKey = keyStore.publicKeyBase58
        _state.update { it.copy(publicKey = pubKey, isProvisioned = true) }

        try {
            // Proof-of-possession: backend requires an Ed25519 signature over
            // "VIGIA-REGISTER:<pubkey>" to prove we hold the device private key.
            val signature = keyStore.sign("VIGIA-REGISTER:$pubKey".toByteArray(Charsets.UTF_8))
            val body = JSONObject().apply {
                put("device_address", pubKey)
                put("signature", signature)
            }.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$baseUrl/register-device")
                .post(body)
                .build()
            httpClient.newCall(request).execute().use { response ->
                Log.d(TAG, "register-device → HTTP ${response.code}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "register-device failed: ${e.message}")
        }

        refreshBalance()
    }

    override suspend fun refreshBalance() = withContext(Dispatchers.IO) {
        val pubKey = if (keyStore.isProvisioned) keyStore.publicKeyBase58 else return@withContext
        _state.update { it.copy(isSyncing = true) }

        try {
            val request = Request.Builder()
                .url("$baseUrl/rewards-balance?wallet_address=$pubKey")
                .get()
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "rewards-balance HTTP ${response.code}")
                    _state.update { it.copy(isSyncing = false) }
                    return@withContext
                }
                val json = JSONObject(response.body!!.string())
                _state.update {
                    it.copy(
                        publicKey                = pubKey,
                        pendingBalanceMicroVigia = json.optString("pending_balance", "0").toLongOrNull() ?: 0L,
                        totalEarnedMicroVigia    = json.optString("total_earned", "0").toLongOrNull() ?: 0L,
                        totalClaimedMicroVigia   = json.optString("total_claimed", "0").toLongOrNull() ?: 0L,
                        isSyncing                = false,
                        isProvisioned            = true,
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "refreshBalance failed: ${e.message}")
            _state.update { it.copy(isSyncing = false) }
        }
    }

    override fun signTelemetry(
        hazardType: String,
        lat: Double,
        lon: Double,
        timestamp: Long,
        confidence: Double,
    ): TelemetrySignature {
        // Must match validator/index.ts: `VIGIA:${hazardType}:${lat}:${lon}:${timestamp}:${confidence}`
        val payload = "VIGIA:$hazardType:$lat:$lon:$timestamp:$confidence"
        val signature = keyStore.sign(payload.toByteArray(Charsets.UTF_8))
        return TelemetrySignature(
            publicKey = keyStore.publicKeyBase58,
            signature = signature,
        )
    }

    private companion object { const val TAG = "VigiaWallet" }
}
