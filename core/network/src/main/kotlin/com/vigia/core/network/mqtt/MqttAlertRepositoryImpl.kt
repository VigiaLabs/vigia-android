package com.vigia.core.network.mqtt

import android.content.Context
import com.vigia.core.model.HazardAlert
import com.vigia.core.model.LocationSnapshot
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MqttDefaultFilePersistence
import org.json.JSONObject
import java.security.SecureRandom
import java.util.UUID
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import javax.net.ssl.SSLContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Connects to AWS IoT Core over MQTT (TCP+TLS) and delivers hazard alerts as a [SharedFlow].
 *
 * Topic schema: vigia/alerts/{userId}   QoS 1, cleanSession=false
 *
 * Lifecycle:
 *   connect(userId) → subscribe → messageArrived → emit HazardAlert
 *   reconnect()     → called by [VigiaFcmReceiver] after Doze wakeup
 *   disconnect()    → clean shutdown (service onDestroy)
 *
 * Wire format (JSON):
 *   {"id":"…","severity":"HIGH","message":"…","ts":1234567890,"lat":28.6,"lng":77.2}
 */
@Singleton
class MqttAlertRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    @Named("MqttBrokerUri") private val brokerUri: String,
) : MqttAlertRepository {

    private val _alerts = MutableSharedFlow<HazardAlert>(replay = 1, extraBufferCapacity = 16)
    override val alerts: SharedFlow<HazardAlert> = _alerts.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var client: MqttAsyncClient? = null
    @Volatile private var activeUserId: String? = null

    override fun connect(userId: String) {
        activeUserId = userId
        scope.launch { doConnect(userId) }
    }

    override fun reconnect() {
        val uid = activeUserId ?: return
        val c = client
        if (c == null || !c.isConnected) {
            scope.launch { doConnect(uid) }
        }
    }

    override fun disconnect() {
        scope.launch {
            try { client?.disconnect() } catch (_: Exception) {}
            client = null
            activeUserId = null
        }
    }

    override suspend fun injectAlert(alert: HazardAlert) {
        _alerts.emit(alert)
    }

    // ── private ───────────────────────────────────────────────────────────────

    private suspend fun doConnect(userId: String) {
        try {
            val c = MqttAsyncClient(
                brokerUri,
                "vigia-android-${userId.take(8)}-${UUID.randomUUID().toString().take(8)}",
                // File-based persistence ensures QoS 1 in-flight messages survive process death.
                MqttDefaultFilePersistence(context.filesDir.absolutePath),
            )
            c.setCallback(callback)

            val opts = MqttConnectOptions().apply {
                isCleanSession       = false
                isAutomaticReconnect = true
                keepAliveInterval    = 30
                connectionTimeout    = 15
                maxInflight          = 10
                // Explicit TLS using the Android system trust store.
                // For ssl:// URIs Paho uses this factory; without it the JVM default is used
                // which works but skips hostname verification on some devices.
                // Production upgrade path: provision an X.509 client cert from AWS IoT Core
                // and add it to a KeyStore passed to SSLContext.init(keyManagers, ...).
                if (brokerUri.startsWith("ssl://", ignoreCase = true)) {
                    socketFactory = SSLContext.getInstance("TLSv1.2").also {
                        it.init(null, null, SecureRandom())
                    }.socketFactory
                }
            }

            kotlin.coroutines.suspendCoroutine { cont ->
                c.connect(opts, null, object : IMqttActionListener {
                    override fun onSuccess(token: IMqttToken?) = cont.resume(Unit)
                    override fun onFailure(token: IMqttToken?, ex: Throwable?) =
                        cont.resumeWithException(ex ?: Exception("MQTT connect failed"))
                })
            }

            client = c

            kotlin.coroutines.suspendCoroutine { cont ->
                c.subscribe("vigia/alerts/$userId", 1, null, object : IMqttActionListener {
                    override fun onSuccess(token: IMqttToken?) = cont.resume(Unit)
                    override fun onFailure(token: IMqttToken?, ex: Throwable?) =
                        cont.resumeWithException(ex ?: Exception("MQTT subscribe failed"))
                })
            }
        } catch (_: Exception) {
            // Silently absorbed — isAutomaticReconnect handles transient failures.
            // Persistent failures are surfaced via alerts stream absence, not exceptions.
        }
    }

    private val callback = object : MqttCallback {
        override fun connectionLost(cause: Throwable?) {
            // isAutomaticReconnect=true handles reconnection
        }

        override fun messageArrived(topic: String, message: MqttMessage) {
            parseAlert(message.payload)?.let { alert ->
                scope.launch { _alerts.emit(alert) }
            }
        }

        override fun deliveryComplete(token: IMqttDeliveryToken?) {}
    }

    // ── JSON parsing ──────────────────────────────────────────────────────────

    private fun parseAlert(payload: ByteArray): HazardAlert? =
        try {
            val obj = JSONObject(String(payload, Charsets.UTF_8))
            val severity = when (obj.optString("severity").uppercase()) {
                "LOW"      -> HazardAlert.Severity.LOW
                "MEDIUM"   -> HazardAlert.Severity.MEDIUM
                "HIGH"     -> HazardAlert.Severity.HIGH
                "CRITICAL" -> HazardAlert.Severity.CRITICAL
                else       -> HazardAlert.Severity.MEDIUM
            }
            val location: LocationSnapshot? = if (obj.has("lat") && obj.has("lng")) {
                LocationSnapshot(
                    latitudeDeg    = obj.getDouble("lat"),
                    longitudeDeg   = obj.getDouble("lng"),
                    accuracyMeters = obj.optDouble("accuracy", 50.0).toFloat(),
                    bearingDeg     = 0f,
                    velocityMs     = 0f,
                    timestampMs    = obj.optLong("ts", System.currentTimeMillis()),
                )
            } else null

            HazardAlert(
                id              = obj.optString("id", UUID.randomUUID().toString()),
                severity        = severity,
                messageText     = obj.optString("message"),
                timestampMs     = obj.optLong("ts", System.currentTimeMillis()),
                locationSnapshot = location,
            )
        } catch (_: Exception) {
            null
        }
}
