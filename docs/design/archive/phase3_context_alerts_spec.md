# PHASE 3: Context Aggregator & Low-Latency Alert Engine
**Status:** Pending  
**Prerequisite:** Phase 2 — CDM + BLE + ForegroundService verified on device  
**Primary Modules:** `:core:model`, `:core:network`  
**Exit Criteria:** MQTT subscriber connected to broker; test FCM `data` message received in Doze; TTS speaks synthesized alert text without perceptible delay

---

## 1. Deliverables

| Artifact | Path |
|---|---|
| `VigiaSearchContext` model | `core/model/src/.../VigiaSearchContext.kt` |
| `SpatialLatentVector` model | `core/model/src/.../SpatialLatentVector.kt` |
| `HazardAlert` model | `core/model/src/.../HazardAlert.kt` |
| `LocationSnapshot` model | `core/model/src/.../LocationSnapshot.kt` |
| `RriScore` model | `core/model/src/.../RriScore.kt` |
| `ContextAggregator` | `core/network/src/.../context/ContextAggregator.kt` |
| `VigiaSearchRepository` interface + impl | `core/network/src/.../search/` |
| `MqttAlertSubscriber` | `core/network/src/.../mqtt/MqttAlertSubscriber.kt` |
| `VigiaFcmReceiver` | `core/network/src/.../fcm/VigiaFcmReceiver.kt` |
| `TtsManager` | `core/network/src/.../tts/TtsManager.kt` |
| `NetworkModule` (Hilt) | `core/network/src/.../di/NetworkModule.kt` |

---

## 2. Core Models (`:core:model`)

```kotlin
// SpatialLatentVector.kt
data class SpatialLatentVector(
    val dimensions: Int,              // 256 or 512
    val data: FloatArray,
    val originTimestampMs: Long,
) {
    init { require(data.size == dimensions) }
}

// RriScore.kt
@JvmInline
value class RriScore(val value: Float) {
    init { require(value in 0f..1f) { "RriScore must be in [0, 1]" } }
}

// LocationSnapshot.kt
data class LocationSnapshot(
    val latitudeDeg: Double,
    val longitudeDeg: Double,
    val accuracyMeters: Float,
    val bearingDeg: Float,
    val timestampMs: Long,
)

// HazardAlert.kt
data class HazardAlert(
    val id: String,
    val severity: Severity,
    val messageText: String,
    val timestampMs: Long,
    val locationSnapshot: LocationSnapshot?,
) {
    enum class Severity { LOW, MEDIUM, HIGH, CRITICAL }
}

// VigiaSearchContext.kt
data class VigiaSearchContext(
    val queryText: String,
    val timestampMs: Long,
    val location: LocationSnapshot,
    val velocityMs: Float,
    val rriScore: RriScore,
    val spatialLatentVector: SpatialLatentVector,
)
```

---

## 3. Context Aggregator

`ContextAggregator` is a `@Singleton` that merges live `StateFlow` streams from the sensor layer (via injected interfaces) into a single `Flow<VigiaSearchContext>`. It uses `combine()` so every update to any upstream source produces a fresh context.

```kotlin
@Singleton
class ContextAggregator @Inject constructor(
    private val locationProvider: LocationProvider,      // wraps FusedLocationProviderClient
    private val bleRepository: BleRepository,            // RRI + $S_t$ from GATT frames
) {
    // Each BLE GATT frame from Pi 5 carries: RriScore (4 bytes) + SpatialLatentVector (1024/2048 bytes)
    private val bleData: Flow<Pair<RriScore, SpatialLatentVector>> =
        bleRepository.incomingFrames.map { frame -> frame.toRriAndVector() }

    fun contextFor(queryText: String): Flow<VigiaSearchContext> =
        combine(
            locationProvider.locationFlow,
            bleData,
        ) { location, (rri, vector) ->
            VigiaSearchContext(
                queryText        = queryText,
                timestampMs      = System.currentTimeMillis(),
                location         = location,
                velocityMs       = location.velocityMs,
                rriScore         = rri,
                spatialLatentVector = vector,
            )
        }.take(1)   // collect first combined emission and complete
}
```

### 3.1 `LocationProvider` (FusedLocationProvider wrapper)

```kotlin
@Singleton
class LocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val client = LocationServices.getFusedLocationProviderClient(context)

    val locationFlow: Flow<LocationSnapshot> = callbackFlow {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1_000L).build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { loc ->
                    trySend(loc.toSnapshot())
                }
            }
        }
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        awaitClose { client.removeLocationUpdates(callback) }
    }.shareIn(GlobalScope, SharingStarted.WhileSubscribed(), replay = 1)
}
```

---

## 4. MQTT Alert Subscriber

### 4.1 TLS Configuration

Server certificate pinned via `CertificatePinner` (OkHttp) or custom `TrustManager` wrapping the bundled CA cert in `res/raw/vigia_ca.crt`.

```kotlin
private fun buildPinnedSslSocketFactory(): SSLSocketFactory {
    val keyStore = KeyStore.getInstance("BKS").apply {
        load(context.resources.openRawResource(R.raw.vigia_ca), null)
    }
    val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
    tmf.init(keyStore)
    val sslCtx = SSLContext.getInstance("TLSv1.2")
    sslCtx.init(null, tmf.trustManagers, SecureRandom())
    return sslCtx.socketFactory
}
```

### 4.2 Subscriber Implementation

```kotlin
@Singleton
class MqttAlertSubscriber @Inject constructor(
    @ApplicationContext private val context: Context,
    private val config: MqttConfig,               // injected from NetworkModule
) {
    private val _alerts = MutableSharedFlow<HazardAlert>(replay = 1, extraBufferCapacity = 16)
    val alerts: SharedFlow<HazardAlert> = _alerts.asSharedFlow()

    private lateinit var client: MqttAndroidClient

    fun connect(userId: String) {
        client = MqttAndroidClient(context, config.brokerUri, "${userId}_android")
        val options = MqttConnectOptions().apply {
            isCleanSession    = false          // QoS 1 persistent session
            socketFactory     = buildPinnedSslSocketFactory()
            keepAliveInterval = 60
            userName          = config.username
            password          = config.password.toCharArray()
        }
        client.connect(options, null, object : IMqttActionListener {
            override fun onSuccess(token: IMqttToken?) {
                client.subscribe("vigia/alerts/$userId", 1) { _, message ->
                    val alert = message.payload.toHazardAlert()
                    _alerts.tryEmit(alert)
                }
            }
            override fun onFailure(token: IMqttToken?, exception: Throwable?) {
                // exponential backoff retry managed by VigiaForegroundService
            }
        })
    }

    fun reconnect() { if (!client.isConnected) client.reconnect() }
}
```

---

## 5. FCM High-Priority Push Receiver

```kotlin
@AndroidEntryPoint
class VigiaFcmReceiver : FirebaseMessagingService() {

    @Inject lateinit var mqttSubscriber: MqttAlertSubscriber
    @Inject lateinit var ttsManager: TtsManager

    override fun onMessageReceived(message: RemoteMessage) {
        if (message.data.isEmpty()) return

        // Reconstruct minimal alert from FCM payload (MQTT may be unreachable in Doze)
        val alert = HazardAlert(
            id            = message.data["alert_id"] ?: return,
            severity      = HazardAlert.Severity.valueOf(message.data["severity"] ?: "HIGH"),
            messageText   = message.data["message"] ?: return,
            timestampMs   = System.currentTimeMillis(),
            locationSnapshot = null,
        )

        // Attempt to reconnect MQTT — it will then deliver queued QoS 1 messages
        mqttSubscriber.reconnect()

        // Speak alert immediately via pre-warmed TTS
        ttsManager.speak(alert.messageText, priority = TtsManager.Priority.HIGH)
    }

    override fun onNewToken(token: String) {
        // POST token to VIGIA backend to register FCM target
    }
}
```

FCM `data` message payload contract:
```json
{
  "to": "<fcm_token>",
  "priority": "high",
  "data": {
    "alert_id":  "uuid-v4",
    "severity":  "CRITICAL",
    "message":   "Ice patch detected 200m ahead — reduce speed"
  }
}
```

---

## 6. `TtsManager` — Pre-Warmed Engine

```kotlin
@Singleton
class TtsManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    enum class Priority { NORMAL, HIGH }

    private var engine: TextToSpeech? = null
    private val ready = AtomicBoolean(false)

    init { initialize() }

    private fun initialize() {
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.language   = Locale.getDefault()
                engine?.setSpeechRate(1.1f)
                engine?.setPitch(0.95f)
                prewarm()
                ready.set(true)
            }
        }
    }

    private fun prewarm() {
        // Synthesize silence to force engine initialization of audio pipeline
        val params = Bundle().apply { putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "prewarm") }
        engine?.speak("", TextToSpeech.QUEUE_FLUSH, params, "prewarm")
    }

    fun speak(text: String, priority: Priority = Priority.NORMAL) {
        if (!ready.get()) return
        val queueMode = if (priority == Priority.HIGH) TextToSpeech.QUEUE_FLUSH
                        else TextToSpeech.QUEUE_ADD
        engine?.speak(text, queueMode, null, UUID.randomUUID().toString())
    }

    fun shutdown() { engine?.shutdown(); ready.set(false) }
}
```

---

## 7. DI — `NetworkModule`

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides @Singleton
    fun provideMqttConfig(): MqttConfig = MqttConfig(
        brokerUri = BuildConfig.MQTT_BROKER_URI,
        username  = BuildConfig.MQTT_USERNAME,
        password  = BuildConfig.MQTT_PASSWORD,
    )

    @Provides @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                    else HttpLoggingInterceptor.Level.NONE
        })
        .build()

    @Provides @Singleton
    fun provideVigiaSearchApi(client: OkHttpClient): VigiaSearchApi =
        Retrofit.Builder()
            .baseUrl(BuildConfig.VIGIA_API_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(VigiaSearchApi::class.java)
}
```

---

## 8. Verification Checklist

- [ ] `adb logcat -s MqttAlertSubscriber` shows `CONNACK` from broker (QoS 1 persistent session)
- [ ] Send test FCM via Firebase Console — `onMessageReceived` fires in background/Doze
- [ ] TTS speaks test alert text within 300ms of `ttsManager.speak()` call
- [ ] `VigiaSearchContext` serialized correctly — verify `spatialLatentVector.data.size == dimensions`
- [ ] No PII fields logged in release build (R8 strips `Log.*`)
- [ ] `./gradlew :core:network:assembleDebug` and `./gradlew :core:model:build` exit 0
