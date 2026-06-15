package com.vigia.core.network.fcm

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.vigia.core.model.HazardAlert
import com.vigia.core.model.LocationSnapshot
import com.vigia.core.network.mqtt.MqttAlertRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Named

/**
 * FCM push receiver — two responsibilities:
 *
 * 1. **Doze wakeup**: A high-priority FCM data message wakes the device and triggers
 *    [MqttAlertRepository.reconnect] so the MQTT connection is re-established before
 *    the next radio window closes.
 *
 * 2. **Direct alert delivery**: If the FCM payload itself contains alert data (fallback
 *    for when MQTT is unreachable), the alert is injected directly via [injectAlert].
 *    This ensures CRITICAL alerts are never silently dropped.
 *
 * FCM data payload keys (all optional):
 *   type     → "alert" to trigger direct injection, anything else → reconnect-only
 *   severity → LOW | MEDIUM | HIGH | CRITICAL
 *   message  → display text
 *   lat, lng → location of the hazard
 *   id       → deduplication key
 *   ts       → epoch milliseconds
 */
@AndroidEntryPoint
class VigiaFcmReceiver : FirebaseMessagingService() {

    @Inject lateinit var mqttAlertRepository: MqttAlertRepository
    @Inject @Named("VigiaOkHttpClient") lateinit var okHttpClient: OkHttpClient
    @Inject @Named("VigiaApiBaseUrl") lateinit var baseUrl: String

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onMessageReceived(message: RemoteMessage) {
        // Always trigger MQTT reconnect — this is the primary Doze wakeup path.
        mqttAlertRepository.reconnect()

        // If the payload contains a full alert, inject it without waiting for MQTT.
        val data = message.data
        when (data["type"]) {
            "alert" -> buildAlert(data)?.let { alert ->
                scope.launch { mqttAlertRepository.injectAlert(alert) }
            }
            "reward" -> {
                // A $VIGIA reward has been minted to the user's ATA.
                // The wallet ViewModel polls on next resume. Full push-triggered
                // balance refresh is wired in Phase 6 via WorkManager.
                Log.d(TAG, "Reward: hazard=${data["hazard_id"]} amount=${data["amount_vigia"]} \$VIGIA")
            }
        }
    }

    override fun onNewToken(token: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val body = JSONObject().apply { put("fcmToken", token) }
                    .toString()
                    .toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("$baseUrl/v1/device/register")
                    .post(body)
                    .build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "FCM token registration failed: HTTP ${response.code}")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "FCM token registration error: ${e.message}")
            }
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private companion object { const val TAG = "VigiaFcm" }

    private fun buildAlert(data: Map<String, String>): HazardAlert? {
        val message = data["message"] ?: return null
        val severity = when (data["severity"]?.uppercase()) {
            "LOW"      -> HazardAlert.Severity.LOW
            "MEDIUM"   -> HazardAlert.Severity.MEDIUM
            "HIGH"     -> HazardAlert.Severity.HIGH
            "CRITICAL" -> HazardAlert.Severity.CRITICAL
            else       -> HazardAlert.Severity.MEDIUM
        }
        val location: LocationSnapshot? = if (data.containsKey("lat") && data.containsKey("lng")) {
            runCatching {
                LocationSnapshot(
                    latitudeDeg    = data["lat"]!!.toDouble(),
                    longitudeDeg   = data["lng"]!!.toDouble(),
                    accuracyMeters = data["accuracy"]?.toFloatOrNull() ?: 50f,
                    bearingDeg     = 0f,
                    velocityMs     = 0f,
                    timestampMs    = data["ts"]?.toLongOrNull() ?: System.currentTimeMillis(),
                )
            }.getOrNull()
        } else null

        return HazardAlert(
            id               = data["id"] ?: UUID.randomUUID().toString(),
            severity         = severity,
            messageText      = message,
            timestampMs      = data["ts"]?.toLongOrNull() ?: System.currentTimeMillis(),
            locationSnapshot = location,
        )
    }
}
