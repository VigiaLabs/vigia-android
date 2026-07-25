# VIGIA Copilot — System Architecture Specification v2.0
**Status:** Pending Sign-Off  
**Date:** 2026-06-10  
**Target Platform:** Android 14 (API 34) / Android 15 (API 35)  
**Language:** Kotlin 2.x · Jetpack Compose · MVVM · Hilt

---

## Table of Contents

1. [Module Topology](#1-module-topology)
2. [Lifecycle & CompanionDeviceManager Presence Layer](#2-lifecycle--companiondevicemanager-presence-layer)
3. [Cryptographic Link Layer — BLE Secure Connections](#3-cryptographic-link-layer--ble-secure-connections)
4. [VIGIASearch Context Aggregator](#4-vigiasearch-context-aggregator)
5. [Alerts Engine — MQTT + FCM + TTS](#5-alerts-engine--mqtt--fcm--tts)
6. [Payout Mechanics — Stripe Integration](#6-payout-mechanics--stripe-integration)
7. [Design System — Cyber-Minimalist Dark Theme](#7-design-system--cyber-minimalist-dark-theme)
8. [Phase Roadmap](#8-phase-roadmap)
9. [Dependency Catalog Snapshot](#9-dependency-catalog-snapshot)
10. [Open Questions & Constraints](#10-open-questions--constraints)

---

## 1. Module Topology

Modeled on the NowInAndroid multi-module reference architecture. Each module boundary enforces a strict unidirectional dependency graph: `:feature` → `:core:*`; `:core:*` modules never import each other's `impl` internals; `:app` is a thin launcher with no business logic.

```
vigia2/
├── app/                          # Thin launcher — Navigation host, Hilt root
├── build-logic/                  # Convention plugins (AGP, Compose, Hilt, etc.)
│   └── convention/
├── gradle/
│   └── libs.versions.toml        # Central version catalog (single source of truth)
├── core/
│   ├── model/                    # Pure Kotlin data models — zero Android deps
│   │   └── src/main/kotlin/
│   │       ├── SpatialLatentVector.kt   # 256-D / 512-D $S_t$ vector
│   │       ├── RriScore.kt
│   │       ├── HazardAlert.kt
│   │       ├── LocationSnapshot.kt
│   │       └── VigiaSearchContext.kt
│   ├── network/                  # Remote clients — Stripe, MQTT TLS 1.2, AWS
│   │   └── src/main/kotlin/
│   │       ├── mqtt/
│   │       ├── stripe/
│   │       └── aws/
│   └── sensor/                   # CDM Presence API + BLE Secure Connections daemon
│       └── src/main/kotlin/
│           ├── cdm/
│           ├── ble/
│           └── service/
└── feature/
    └── copilot/                  # Jetpack Compose UI — Route, Screen, ViewModel
        └── src/main/kotlin/
            ├── CopilotRoute.kt
            ├── CopilotScreen.kt
            ├── CopilotViewModel.kt
            ├── CopilotUiState.kt
            └── orb/
                └── AiOrbComponent.kt
```

**Dependency Direction Rules:**
```
:app  →  :feature:copilot  →  :core:model
                            →  :core:network (via interface only)
                            →  :core:sensor  (via interface only)
:core:sensor  →  :core:model
:core:network →  :core:model
```

---

## 2. Lifecycle & CompanionDeviceManager Presence Layer

### 2.1 CompanionDeviceManager Presence API

Android 14 introduced the CDM **Presence API** (`DevicePresenceEvent`). It delivers wakeups when an associated companion device (the Pi 5 Blackbox) comes in/out of BLE range without requiring the app to maintain a persistent connection.

```kotlin
// Registration (runs once at pairing time)
val manager = getSystemService(CompanionDeviceManager::class.java)
manager.startObservingDevicePresence(associationId)

// Receiver declared in AndroidManifest.xml
<receiver android:name=".sensor.cdm.PresenceReceiver"
          android:exported="false">
    <intent-filter>
        <action android:name="android.companion.action.DEVICE_PRESENCE_UPDATED"/>
    </intent-filter>
</receiver>
```

`PresenceReceiver` transitions a `StateFlow<DevicePresenceState>` in the `CdmPresenceRepository`, which the `CopilotViewModel` observes.

### 2.2 Foreground Service — `connectedDevice` Type

To avoid the **6-hour `dataSync` timeout** introduced in Android 14, the background service is typed as `connectedDevice`. This type is exempt from the 6-hour limit provided a valid CDM association exists.

```xml
<!-- AndroidManifest.xml -->
<service
    android:name=".sensor.service.VigiaForegroundService"
    android:foregroundServiceType="connectedDevice"
    android:exported="false" />
```

```kotlin
// Required permission in manifest
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE"/>
```

**Service lifecycle state machine:**

```
IDLE ──(CDM presence detected)──► CONNECTING
CONNECTING ──(BLE handshake OK)──► ACTIVE
ACTIVE ──(device leaves range)──► IDLE
ACTIVE ──(fatal error)──► ERROR ──(retry backoff)──► CONNECTING
```

`VigiaForegroundService` posts a sticky `ServiceState` via a Hilt-scoped `StateFlow`. The foreground notification is updated reactively via `NotificationCompat.Builder`.

---

## 3. Cryptographic Link Layer — BLE Secure Connections

### 3.1 Pairing Mode

BLE Secure Connections with **Numeric Comparison** (LE SC, IO Capability = `DisplayYesNo`) for on-screen confirmation, with an **OOB** fallback path for headless Pi 5 scenarios. The Android `BluetoothDevice.createBond()` flow is managed by a custom `BluetoothGatt` callback chain inside `:core:sensor`.

```
Phone ──── LE SC Pairing ────► Pi 5 Blackbox
         (Numeric Compare / OOB)
         [Link-layer AES-128 CCM encrypted after bonding]
```

### 3.2 Account-Bound Challenge-Response Handshake

After the encrypted BLE link is established, an application-layer handshake executes **inside** the encrypted GATT tunnel:

```
Phone                         Pi 5
  │── GATT Write: HELLO ────────►│
  │◄─ GATT Notify: CHALLENGE ───│   (32-byte random nonce)
  │── GATT Write: RESPONSE ─────►│   HMAC-SHA256(userAccountSecret, nonce)
  │◄─ GATT Notify: BOUND / ERR ─│
```

- `userAccountSecret` is a 256-bit key stored in Android **Keystore** (hardware-backed where available, `StrongBox` preferred).
- The handshake result updates `BleLinkState` in `BleRepository` — a `StateFlow<BleLinkState>` consumed by the sensor service.
- A failed handshake terminates the GATT connection and increments a rate-limit counter stored in encrypted DataStore.

### 3.3 BLE State Machine

```kotlin
sealed interface BleLinkState {
    data object Idle : BleLinkState
    data object Scanning : BleLinkState
    data class Connecting(val device: BluetoothDevice) : BleLinkState
    data object Pairing : BleLinkState
    data object Handshaking : BleLinkState
    data object Bound : BleLinkState
    data class Error(val reason: BleLinkError) : BleLinkState
}
```

---

## 4. VIGIASearch Context Aggregator

Each search query submitted by the user is enriched with real-time sensor telemetry before dispatch.

### 4.1 Context Payload Schema (`VigiaSearchContext`)

```kotlin
// :core:model
data class VigiaSearchContext(
    val queryText: String,
    val timestampMs: Long,
    val location: LocationSnapshot,          // lat, lon, accuracy, bearing
    val velocityMs: Float,                   // m/s from fused location provider
    val rriScore: RriScore,                  // Road Risk Index — [0.0, 1.0]
    val spatialLatentVector: SpatialLatentVector,  // $S_t$ — 256-D or 512-D Float array
)

data class SpatialLatentVector(
    val dimensions: Int,                     // 256 or 512
    val data: FloatArray,
    val originTimestampMs: Long,
)
```

### 4.2 Context Assembly Pipeline

```
FusedLocationProvider ──────────────────────────────┐
BLE GATT Notify (RRI + $S_t$ from Pi 5) ────────────┼──► ContextAggregator (combine)
                                                     │         │
                                              VigiaSearchContext
                                                         │
                                              VigiaSearchRepository.search()
                                                         │
                                              AWS API Gateway (HTTPS/2, TLS 1.3)
```

`ContextAggregator` is a `@Singleton` Hilt component that merges the latest values from each `StateFlow` using `combine()`. The result is exposed as `Flow<VigiaSearchContext>` and collected in `CopilotViewModel`.

---

## 5. Alerts Engine — MQTT + FCM + TTS

### 5.1 MQTT Client — QoS 1 Persistent Sessions

- Library: **Eclipse Paho Android Client** (or HiveMQ MQTT Client for cleaner coroutine integration)
- Transport: TCP over TLS 1.2 (`SSLSocketFactory` from Android `KeyStore` with pinned server cert)
- QoS: **1** (at-least-once) — guarantees delivery across brief radio interruptions
- Session: `cleanSession = false` — broker queues alerts while the app is backgrounded in Doze

```kotlin
// MqttAlertSubscriber.kt in :core:network
val options = MqttConnectOptions().apply {
    isCleanSession = false
    socketFactory = buildPinnedSslSocketFactory()
    keepAliveInterval = 60
}
client.connect(options)
client.subscribe("vigia/alerts/${userId}", 1) { _, message ->
    alertChannel.trySend(message.toHazardAlert())
}
```

### 5.2 FCM High-Priority Push — Doze Wakeup Path

When the device enters Doze, MQTT is throttled. FCM `data` messages with `priority: high` bypass Doze via the Firebase push channel.

```
Pi 5 / Backend ──► FCM Server ──► (high priority) ──► VigiaFcmReceiver
                                                             │
                                                   MqttAlertSubscriber.reconnect()
                                                             │
                                                   HazardAlert emitted to StateFlow
```

`VigiaFcmReceiver extends FirebaseMessagingService` reconstructs a minimal `HazardAlert` from the FCM payload if MQTT is unavailable, then hands off to the TTS engine.

### 5.3 Pre-Warmed Text-To-Speech Engine

The TTS engine is initialized at service start, not at alert time, to eliminate synthesis latency.

```kotlin
// VigiaForegroundService.onCreate()
ttsEngine = TextToSpeech(applicationContext) { status ->
    if (status == TextToSpeech.SUCCESS) {
        ttsEngine.language = Locale.getDefault()
        ttsEngine.setSpeechRate(1.1f)
        ttsEngine.setPitch(0.95f)
        // Pre-warm by synthesizing a zero-length utterance
        ttsEngine.synthesizeToFile(emptyBundle, params, cacheFile, "prewarm")
    }
}
```

Alerts are queued via `ttsEngine.speak(alertText, QUEUE_FLUSH, null, utteranceId)` with `QUEUE_FLUSH` to interrupt any in-progress speech.

---

## 6. Payout Mechanics — Stripe Integration

### 6.1 Isolation Principle

All Stripe financial data flows are **isolated inside `:core:network`**. No raw card data, account numbers, or Stripe tokens ever cross module boundaries as plain strings. The `:feature:copilot` layer only invokes named use-case functions and observes sealed state.

### 6.2 Stripe Android SDK

```kotlin
// Stripe initialization in :app Application class
PaymentConfiguration.init(applicationContext, BuildConfig.STRIPE_PUBLISHABLE_KEY)
```

- **Payment Sheet** for one-time payments — `PaymentSheet.FlowController` for custom UI integration
- **Stripe Connect Express onboarding** — account creation via backend API, return URL deep-linked to the app

### 6.3 Stripe Financial Connections

```kotlin
// Tokenized bank account linking in :core:network
val params = CollectBankAccountParams.createPaymentIntentParams(
    publishableKey = BuildConfig.STRIPE_PUBLISHABLE_KEY,
    clientSecret = paymentIntentClientSecret,
    params = CollectBankAccountForPaymentParams(
        paymentMethodType = PaymentMethod.Type.USBankAccount,
        billingDetails = billingDetails,
    )
)
collectBankAccountLauncher.launch(params)
```

All `clientSecret` values are fetched from the VIGIA backend — they are never hardcoded or logged.

### 6.4 Security Constraints

- Stripe keys stored in `BuildConfig` fields populated from CI secrets — never in source.
- `StrictMode` policy disables cleartext network traffic; Stripe endpoints enforced via Network Security Config.
- No PII written to Logcat in release builds (R8 ProGuard rule strips `Log.*` calls).

---

## 7. Design System — Cyber-Minimalist Dark Theme

### 7.1 Color Tokens

| Token | Hex | Usage |
|---|---|---|
| `colorBackground` | `#09090B` | Root scaffold background |
| `colorSurface` | `#18181B` | Cards, bottom sheets, dialogs |
| `colorOutline` | `#27272A` | Borders, dividers |
| `colorPrimary` | `#A78BFA` | Primary accent (violet) |
| `colorOnPrimary` | `#09090B` | Text on primary |
| `colorSecondary` | `#38BDF8` | Secondary accent (sky) |
| `colorError` | `#F87171` | Error states |
| `colorOnBackground` | `#FAFAFA` | Primary body text |
| `colorOnSurface` | `#A1A1AA` | Secondary / caption text |

### 7.2 Typography

- Headline: **Inter** or **JetBrains Mono** (monospaced for telemetry readouts)
- Body: Inter Regular 14sp / 16sp
- Caption: Inter Medium 11sp, `letterSpacing = 0.08.em`

### 7.3 AI Orb Component — Zero-Recomposition Architecture

The pulsating radial-gradient orb is the primary ambient status indicator. Its rendering parameters (radius, alpha, gradient offsets) are driven by `Animatable` + `infiniteTransition` and applied **exclusively** via `graphicsLayer {}` lambda deferrals — never via state reads inside the composition tree.

```kotlin
@Composable
fun AiOrb(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "orbScale",
    )

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "orbAlpha",
    )

    // graphicsLayer lambda: reads animated values OUTSIDE composition.
    // The Canvas below never recomposes — only the render thread layer mutates.
    Box(
        modifier = modifier
            .size(180.dp)
            .graphicsLayer {
                scaleX = pulseScale
                scaleY = pulseScale
                alpha = glowAlpha
                renderEffect = BlurMaskFilter(32f, BlurMaskFilter.Blur.NORMAL)
                    .toRenderEffect()
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFA78BFA),
                        Color(0xFF38BDF8),
                        Color.Transparent,
                    ),
                ),
            )
        }
    }
}
```

**Why `graphicsLayer` deferrals avoid recomposition:**  
`graphicsLayer { ... }` lambdas are executed by the render thread during the draw phase. Animated `State` reads inside the lambda are scoped to the graphics layer — they do not trigger recomposition of parent or sibling composables. The `Canvas` draw call is issued once at first composition; all subsequent frames are produced purely by layer mutations on the render thread.

### 7.4 Responsive Layout Constraints

- Minimum touch target: 48×48dp (Material 3 spec)
- Content padding: 16dp horizontal, 12dp vertical (compact); 24dp (expanded)
- Window size class awareness via `WindowSizeClass` — compact / medium / expanded breakpoints
- Edge-to-edge enforced via `WindowCompat.setDecorFitsSystemWindows(window, false)`

---

## 8. Phase Roadmap

| Phase | Scope | Module(s) Affected | Exit Criteria |
|---|---|---|---|
| **0** | Architecture Specification | `design/` | Sign-off on this document |
| **1** | Project Scaffolding & Dependency Catalog | All | `./gradlew assembleDebug` green; all modules compile |
| **2** | Hardware Connectivity & Lifecycle | `:core:sensor`, `:app` | CDM presence events received; foreground service running as `connectedDevice` |
| **3** | Context Aggregator & Alert Engine | `:core:model`, `:core:network` | MQTT subscriber connected; FCM receives test push; TTS speaks test alert |
| **4** | Compose Presentation & Stripe Pay | `:feature:copilot` | AI Orb renders; no recompositions logged; Stripe Financial Connections sheet opens |

---

## 9. Dependency Catalog Snapshot

> Full pinned versions are locked in `gradle/libs.versions.toml` during Phase 1.

| Group | Library | Approximate Version |
|---|---|---|
| Kotlin | `kotlin-stdlib`, `kotlinx-coroutines-android` | 2.x / 1.9.x |
| AGP | `com.android.tools.build:gradle` | 8.5+ |
| Compose BOM | `androidx.compose:compose-bom` | 2024.x |
| Hilt | `com.google.dagger:hilt-android` | 2.51+ |
| Room | `androidx.room:room-runtime` | 2.7+ (KSP) |
| Navigation | `androidx.navigation:navigation-compose` | 2.8+ (Nav3 alpha) |
| CDM | `androidx.companion:companion` | bundled with platform API 34+ |
| MQTT | `org.eclipse.paho:org.eclipse.paho.android.service` | 1.1.1 / HiveMQ 1.3+ |
| Firebase BOM | `com.google.firebase:firebase-bom` | 33.x |
| FCM | `com.google.firebase:firebase-messaging-ktx` | via BOM |
| Stripe Android | `com.stripe:stripe-android` | 20.x |
| Stripe Financial Connections | `com.stripe:financial-connections` | 20.x |
| AWS SDK | `aws.sdk.kotlin:*` | 1.x |
| OkHttp | `com.squareup.okhttp3:okhttp` | 4.12+ |
| DataStore | `androidx.datastore:datastore` | 1.1+ |

---

## 10. Open Questions & Constraints

| # | Question | Owner | Impact |
|---|---|---|---|
| 1 | Which AWS service hosts the VIGIASearch endpoint — API Gateway + Lambda, or AppSync? | Backend team | `:core:network` client choice |
| 2 | MQTT broker host/port/TLS cert pinning values | DevOps | `:core:network` `MqttAlertSubscriber` configuration |
| 3 | Stripe Connect Express account country and currency scope | Finance | Onboarding flow parameters |
| 4 | Pi 5 Blackbox GATT service UUID and characteristic UUIDs for handshake | Hardware team | `:core:sensor` BLE characteristic constants |
| 5 | Is $S_t$ delivered as a raw GATT notification or via a separate BLE data channel (L2CAP CoC)? | Hardware team | `:core:sensor` data ingestion path |
| 6 | Target minimum SDK — API 33 or API 34? (CDM Presence API requires API 34) | Product | `minSdk` in `build.gradle.kts` |
| 7 | Stripe publishable key environment split — dev / staging / prod | DevOps | `BuildConfig` flavor configuration |

---

## 11. Hackathon / Demo Build Constraints

> These are known shortcuts taken to keep the demo runnable without paid infrastructure. Each item has a documented production path.

### 11.1 HTTP Cleartext to Fargate ALB (VIGIASearch)

**Status:** Active in `demo` flavor only.

The VIGIASearch Fargate endpoint (`vigia-ts-search-204472952.us-east-1.elb.amazonaws.com`) is served over plain HTTP. Android blocks cleartext traffic by default since API 28, so the `demo` flavor carries a network security config that whitelists this host:

- `app/src/demo/res/xml/network_security_config.xml` — permits cleartext to the ALB domain only; blocks all other cleartext.
- `app/src/demo/AndroidManifest.xml` — flavor overlay that sets `android:networkSecurityConfig`.

The `prod` flavor has no such override — it inherits Android's default (HTTPS everywhere), so a prod build with the current HTTP URL would crash identically to the bug seen during hackathon.

**Production path (no ongoing cost until real traffic):**
1. Request a free public certificate in **AWS Certificate Manager** (free — ACM certs cost nothing, only the domain does).
2. Add an HTTPS:443 listener to the ALB and attach the cert.
3. Add a DNS CNAME from your domain (or a free subdomain via services like `nip.io` / Cloudflare free tier) to the ALB hostname.
4. Update `VIGIA_API_BASE_URL` in `secrets.properties` to `https://`.
5. Delete `app/src/demo/res/xml/network_security_config.xml` and `app/src/demo/AndroidManifest.xml`.

**Free-tier alternative (no domain purchase required):**
Use `nip.io` — a free wildcard DNS service that maps `<ip>.nip.io` to `<ip>`. Combine with a free Cloudflare tunnel or AWS ALB with a self-signed cert (OkHttp must be configured to trust it). Suitable for a hackathon demo judges run locally; not suitable for TestFlight/Play distribution.

### 11.2 Firebase FCM — Not Provisioned

`google-services.json` is absent; the `google-services` Gradle plugin is commented out. FCM initialization fails silently at startup (logged as a warning, not a crash). MQTT reconnect on Doze wake is therefore not functional in the demo build — the app relies on the persistent MQTT connection only.

**Production path:** Provision a Firebase project, download `google-services.json`, uncomment the plugin in `app/build.gradle.kts`.

### 11.3 MQTT TLS Client Authentication

`MqttAlertRepositoryImpl` connects to AWS IoT Core (`a3re4nls2cuv10-ats.iot.us-east-1.amazonaws.com:8883`) over TLS but with no client certificate or Cognito token. The broker will reject the connection unless the IoT Core policy is set to allow unauthenticated connections (not recommended for production).

**Production path:** Provision an X.509 device certificate in AWS IoT Core, embed the cert + private key via `secrets.properties` (or retrieve from a backend-vended endpoint at first launch), and pass them to the Paho `MqttConnectOptions` SSL context.

### 11.4 Stripe Publishable Key

`secrets.properties` contains `pk_test_REPLACE_ME`. Stripe payment sheet will not launch until replaced with a real test-mode key from the Stripe dashboard (free to obtain).

### 11.5 GATT UUIDs — Placeholder Values

`GattConstants.kt` contains placeholder UUIDs for the Pi 5 Blackbox BLE service and characteristics. BLE pairing and telemetry streaming will not function until the hardware team provides the real UUIDs.

---

*End of Specification v2.0 — Awaiting sign-off before Phase 1 implementation begins.*
