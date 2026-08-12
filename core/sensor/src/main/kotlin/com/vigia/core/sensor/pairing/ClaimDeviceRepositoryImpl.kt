package com.vigia.core.sensor.pairing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class ClaimDeviceRepositoryImpl @Inject constructor(
    @Named("WalletOkHttpClient") private val httpClient: OkHttpClient,
    @Named("VigiaApiBaseUrl") private val baseUrl: String,
) : ClaimDeviceRepository {

    override suspend fun claimDevice(
        deviceId: String,
        walletPubkey: String,
        ts: Long,
        walletSig: String,
        deviceSig: String,
    ): ClaimResult =
        withContext(Dispatchers.IO) {
            try {
                val body = JSONObject()
                    .put("device_id", deviceId)
                    .put("wallet_pubkey", walletPubkey)
                    .put("ts", ts)
                    .put("wallet_sig", walletSig)   // P0-6: Ed25519 wallet PoP
                    .put("device_sig", deviceSig)   // P0-6: ATECC ECDSA Pi PoP (via BLE)
                    .toString()
                    .toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url("$baseUrl/claim-device")
                    .post(body)
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    when (response.code) {
                        200 -> ClaimResult.Ok
                        409 -> {
                            val detail = runCatching {
                                JSONObject(response.body!!.string()).getString("detail")
                            }.getOrDefault("device_taken")
                            if (detail == "wallet_taken") ClaimResult.WalletTaken
                            else ClaimResult.DeviceTaken
                        }
                        401 -> ClaimResult.Unauthorized
                        403 -> ClaimResult.Forbidden
                        in 400..499 -> ClaimResult.InvalidRequest(
                            "HTTP ${response.code}: ${response.body?.string().orEmpty().take(200)}"
                        )
                        else -> ClaimResult.ServerError(response.code, "HTTP ${response.code}")
                    }
                }
            } catch (e: IOException) {
                ClaimResult.TransientNetworkError(e.message ?: "network error")
            } catch (e: Exception) {
                ClaimResult.ServerError(0, e.message ?: "claim request failed")
            }
        }
}
