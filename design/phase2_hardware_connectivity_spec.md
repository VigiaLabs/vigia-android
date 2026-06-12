# PHASE 2: Core Hardware Connectivity & Lifecycle Layer
**Status:** Pending  
**Prerequisite:** Phase 1 — all modules compile (`./gradlew assembleDebug` green)  
**Primary Modules:** `:core:sensor`, `:app`  
**Exit Criteria:** CDM presence events received on a real device; `VigiaForegroundService` confirmed running as `connectedDevice` type via `adb shell dumpsys activity services`

---

## 1. Deliverables

| Artifact | Path |
|---|---|
| CDM Presence Repository interface | `core/sensor/src/main/kotlin/cdm/CdmPresenceRepository.kt` |
| CDM Presence Repository impl | `core/sensor/src/main/kotlin/cdm/CdmPresenceRepositoryImpl.kt` |
| CDM Presence Receiver | `core/sensor/src/main/kotlin/cdm/PresenceReceiver.kt` |
| BLE Repository interface | `core/sensor/src/main/kotlin/ble/BleRepository.kt` |
| BLE Repository impl | `core/sensor/src/main/kotlin/ble/BleRepositoryImpl.kt` |
| BLE Link State machine | `core/sensor/src/main/kotlin/ble/BleLinkStateMachine.kt` |
| Vigia Foreground Service | `core/sensor/src/main/kotlin/service/VigiaForegroundService.kt` |
| Sensor DI Module | `core/sensor/src/main/kotlin/di/SensorModule.kt` |
| AndroidManifest (sensor) | `core/sensor/src/main/AndroidManifest.xml` |

---

## 2. AndroidManifest.xml — Required Declarations

```xml
<!-- core/sensor/src/main/AndroidManifest.xml -->
<manifest>

    <!-- CDM association -->
    <uses-permission android:name="android.permission.REQUEST_COMPANION_RUN_IN_BACKGROUND"/>
    <uses-permission android:name="android.permission.REQUEST_COMPANION_USE_DATA_IN_BACKGROUND"/>
    <uses-permission android:name="android.permission.REQUEST_COMPANION_START_FOREGROUND_SERVICES_FROM_BACKGROUND"/>

    <!-- Foreground service — connectedDevice type (bypasses 6h dataSync limit) -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE"/>

    <!-- BLE -->
    <uses-permission android:name="android.permission.BLUETOOTH_SCAN"
        android:usesPermissionFlags="neverForLocation"/>
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT"/>
    <uses-feature android:name="android.hardware.bluetooth_le" android:required="true"/>

    <!-- Location (required for BLE scanning on API < 31 and Fused Location for context) -->
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"/>

    <application>
        <!-- Foreground service typed as connectedDevice -->
        <service
            android:name=".service.VigiaForegroundService"
            android:foregroundServiceType="connectedDevice"
            android:exported="false"/>

        <!-- CDM presence receiver -->
        <receiver
            android:name=".cdm.PresenceReceiver"
            android:exported="false">
            <intent-filter>
                <action android:name="android.companion.action.DEVICE_PRESENCE_UPDATED"/>
            </intent-filter>
        </receiver>
    </application>
</manifest>
```

---

## 3. CompanionDeviceManager Presence Layer

### 3.1 `CdmPresenceRepository` Interface

```kotlin
interface CdmPresenceRepository {
    val presenceState: StateFlow<DevicePresenceState>
    suspend fun registerPresenceObserver(associationId: Int)
    suspend fun unregisterPresenceObserver(associationId: Int)
}

sealed interface DevicePresenceState {
    data object Unknown : DevicePresenceState
    data object Present : DevicePresenceState
    data object Absent : DevicePresenceState
}
```

### 3.2 `CdmPresenceRepositoryImpl` Key Logic

```kotlin
@Singleton
class CdmPresenceRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : CdmPresenceRepository {

    private val _presenceState = MutableStateFlow<DevicePresenceState>(DevicePresenceState.Unknown)
    override val presenceState: StateFlow<DevicePresenceState> = _presenceState.asStateFlow()

    private val companionDeviceManager by lazy {
        context.getSystemService(CompanionDeviceManager::class.java)
    }

    override suspend fun registerPresenceObserver(associationId: Int) {
        withContext(Dispatchers.IO) {
            companionDeviceManager.startObservingDevicePresence(associationId)
        }
    }

    // Called by PresenceReceiver via Hilt injection
    fun onPresenceEvent(event: DevicePresenceEvent) {
        _presenceState.value = when (event.isPresent) {
            true  -> DevicePresenceState.Present
            false -> DevicePresenceState.Absent
        }
    }
}
```

### 3.3 `PresenceReceiver`

```kotlin
@AndroidEntryPoint
class PresenceReceiver : BroadcastReceiver() {
    @Inject lateinit var repository: CdmPresenceRepositoryImpl

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != CompanionDeviceManager.ACTION_DEVICE_PRESENCE_UPDATED) return
        val event = intent.getParcelableExtra<DevicePresenceEvent>(
            CompanionDeviceManager.EXTRA_DEVICE_PRESENCE_EVENT
        ) ?: return
        repository.onPresenceEvent(event)
    }
}
```

---

## 4. BLE Secure Connection State Machine

### 4.1 State Definitions

```kotlin
sealed interface BleLinkState {
    data object Idle         : BleLinkState
    data object Scanning     : BleLinkState
    data class  Connecting(val device: BluetoothDevice) : BleLinkState
    data object Pairing      : BleLinkState   // LE SC Numeric Compare in progress
    data object Handshaking  : BleLinkState   // HMAC challenge-response
    data object Bound        : BleLinkState   // Fully authenticated, data flowing
    data class  Error(val reason: BleLinkError, val retryCount: Int) : BleLinkState
}

enum class BleLinkError { SCAN_FAILED, CONNECTION_TIMEOUT, PAIRING_FAILED, HANDSHAKE_FAILED, GATT_ERROR }
```

### 4.2 `BleRepository` Interface

```kotlin
interface BleRepository {
    val linkState: StateFlow<BleLinkState>
    suspend fun startScan(targetDeviceAddress: String)
    suspend fun disconnect()
    // Emits authenticated GATT data frames after Bound state
    val incomingFrames: SharedFlow<ByteArray>
    suspend fun sendFrame(data: ByteArray)
}
```

### 4.3 HMAC Handshake Sequence

1. After GATT connection and LE SC bonding, write `HELLO` byte to handshake characteristic.
2. Await `CHALLENGE` notification (32-byte nonce).
3. Retrieve `userAccountSecret` from Android Keystore (`KeyStore.getInstance("AndroidKeyStore")`).
4. Compute `HMAC-SHA256(userAccountSecret, nonce)`.
5. Write response to handshake characteristic.
6. Await `BOUND` or `ERR` notification.
7. On `BOUND`: transition `BleLinkState` → `Bound`. On `ERR`: transition → `Error(HANDSHAKE_FAILED)`.

### 4.4 Android Keystore — Key Provisioning

```kotlin
// Key generation (first pairing only)
val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore")
keyGenerator.init(
    KeyGenParameterSpec.Builder("vigia_account_secret", KeyProperties.PURPOSE_SIGN)
        .setIsStrongBoxBacked(true)   // prefer StrongBox TEE
        .setUserAuthenticationRequired(false)  // service must access key without user present
        .build()
)
keyGenerator.generateKey()
```

---

## 5. `VigiaForegroundService`

```kotlin
@AndroidEntryPoint
class VigiaForegroundService : Service() {

    @Inject lateinit var cdmRepository: CdmPresenceRepository
    @Inject lateinit var bleRepository: BleRepository
    @Inject lateinit var ttsEngine: TextToSpeech   // injected pre-warmed instance

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification("Initializing…"))
        prewarmTts()
        observePresence()
    }

    private fun observePresence() {
        serviceScope.launch {
            cdmRepository.presenceState.collectLatest { state ->
                when (state) {
                    DevicePresenceState.Present -> bleRepository.startScan(BuildConfig.BLACKBOX_MAC)
                    DevicePresenceState.Absent  -> bleRepository.disconnect()
                    DevicePresenceState.Unknown -> Unit
                }
                updateNotification(state)
            }
        }
    }

    private fun prewarmTts() { /* see § 5.3 of architecture spec */ }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val NOTIFICATION_ID = 1001
        fun start(context: Context) =
            context.startForegroundService(Intent(context, VigiaForegroundService::class.java))
    }
}
```

---

## 6. DI Bindings (`SensorModule`)

```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class SensorModule {

    @Binds @Singleton
    abstract fun bindCdmPresenceRepository(impl: CdmPresenceRepositoryImpl): CdmPresenceRepository

    @Binds @Singleton
    abstract fun bindBleRepository(impl: BleRepositoryImpl): BleRepository
}
```

---

## 7. Verification Checklist

- [ ] `adb shell dumpsys activity services | grep VigiaForegroundService` shows `type=connectedDevice`
- [ ] `adb logcat -s VigiaPresence` shows `DEVICE_PRESENCE_UPDATED` intent received on approach/departure
- [ ] BLE scan initiates within 2s of CDM `Present` event
- [ ] HMAC handshake completes; `BleLinkState.Bound` logged
- [ ] Service remains alive after 6 hours (CDM `connectedDevice` type does not time out)
- [ ] `./gradlew :core:sensor:assembleDebug` exits 0 with no lint errors
