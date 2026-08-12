<div align="center">

![Tech Event Banner](https://github.com/user-attachments/assets/c7995ac9-c551-4ad8-b5b0-ea759cf8a63f)

# VIGIA — Mobile Copilot

### Android companion app: DePIN wallet · voice copilot · real-time hazard-alert client for the VIGIA edge node

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-BOM_2025.05-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Hilt](https://img.shields.io/badge/Hilt-2.56.2-FF6F00?style=flat-square&logo=google&logoColor=white)](https://dagger.dev/hilt/)
[![minSdk](https://img.shields.io/badge/minSdk-34-3DDC84?style=flat-square&logo=android&logoColor=white)](https://developer.android.com/about/versions/14)
[![targetSdk](https://img.shields.io/badge/targetSdk-36-3DDC84?style=flat-square&logo=android&logoColor=white)](https://developer.android.com/about/versions/15)
[![Material 3](https://img.shields.io/badge/Material_3-1.3.2-757575?style=flat-square&logo=material-design&logoColor=white)](https://m3.material.io)
[![License](https://img.shields.io/badge/License-MIT-blue?style=flat-square)](LICENSE)

</div>

---

VIGIA Mobile is an advanced Android prototype for a road-intelligence edge node. The repository contains
BLE/GATT pairing, sensor context, a voice copilot, SSE search, MQTT/FCM alert receivers, maps and a
Keystore-wrapped Ed25519 rewards wallet. Several production-critical paths are partial or unsafe under
failure; Stripe cash-out and end-to-end ownership/alert guarantees are **not production-ready**.

> **Production status (2026-08-12): not a release candidate.** Authentication/claim fail-open paths, BLE
> lifecycle, context truth, payment settlement, durable alerts, Room migrations/backups, AWS IoT client auth,
> maps infrastructure, CI/observability and release engineering are tracked in the corrected
> [Architecture Hardening Master Spec](ARCHITECTURE_HARDENING_SPEC.md).

---

## Video Demo

[![VIGIA Demo](https://img.youtube.com/vi/cVD0lM7jQQk/maxresdefault.jpg)](https://youtu.be/cVD0lM7jQQk?si=9XQ2SyRwYv5h02uB)

## The System In Motion

![VigiaSense MultiModal System.](vigia_700p_final.gif)

---

## Table of Contents

1. [Why This App Stands Out](#why-this-app-stands-out)
2. [Role in the VIGIA System](#role-in-the-vigia-system)
3. [App Architecture](#app-architecture)
4. [Module Breakdown](#module-breakdown)
5. [Key Features](#key-features)
6. [Security Model](#security-model)
7. [Tech Stack](#tech-stack)
8. [Data and State](#data-and-state)
9. [Getting Started](#getting-started)
10. [Project Structure](#project-structure)
11. [About the Developer](#about-the-developer)
12. [License](#license)
13. [Resources](#resources)

---

## Why This App Stands Out

**Multi-module foundation.** Ten application modules share convention plugins and a version catalogue.
The target is feature leaves over owned core APIs, but `feature:copilot` currently depends directly on
`feature:maps` and `feature:pairing`, and `core:sensor` is an over-broad integration module.

**Keystore-wrapped software wallet.** The Ed25519 keypair is generated in the app process. Its PKCS#8
private encoding is AES-GCM-encrypted by an Android Keystore key and stored in SharedPreferences; signing
decrypts/reconstructs the private key in app memory. This protects the stored blob but is not a TEE-resident,
non-exportable Ed25519 signing key. Hardware security level is not currently measured.

**On-device voice copilot.** A full conversational loop runs entirely through the app: live microphone → Sarvam `saarika:v2` STT → VIGIASearch SSE streaming → Sarvam `bulbul:v1` TTS → AudioTrack playback. The mic reopens automatically after each AI response, keeping the driver in a hands-free dialogue that persists across turns.

**Best-effort alert prototype.** MQTT/FCM receivers and TTS priority exist, but normal AWS IoT client
authentication is not implemented, the MQTT client ID is random despite persistent-session intent, and FCM
injects into an in-memory flow rather than a durable inbox/system notification. Production requires stable
event IDs, Room dedupe, gap sync and measured delivery.

**Partially offline chat history.** Sessions/messages are persisted in Room and interrupted answers retain
partial state. This narrows loss on network interruption; it is not a guarantee against every device/storage
failure. Wallet, hazard and reward views are not yet durable offline read models.

---

## Role in the VIGIA System

```
┌─────────────────────────────────┐         ┌──────────────────────┐
│       VIGIA Pi 5 Blackbox       │  BLE     │   VIGIA Mobile App   │
│  (vigia-raspi edge node)        │◄────────►│  (this repo)         │
│                                 │  GATT    │                      │
│  • Road-hazard CV inference     │          │  • Voice copilot     │
│  • 256-D spatial latent vector  │          │  • Hazard alerts     │
│  • RRI confidence score         │          │  • DePIN wallet      │
│  • BLE GATT peripheral          │          │  • Map layers        │
└─────────────────────────────────┘          └──────────┬───────────┘
                                                        │ HTTPS / MQTT / TLS
                                             ┌──────────▼───────────┐
                                             │   AWS Cloud Backend  │
                                             │  • API Gateway       │
                                             │  • IoT Core (MQTT)   │
                                             │  • Cognito / Amplify │
                                             │  • Lambda validators │
                                             │  • Stripe Connect    │
                                             │  • Sarvam AI proxy   │
                                             └──────────────────────┘
```

The VIGIA Pi 5 edge node runs continuous road-hazard detection at the kerb. Its BLE GATT peripheral streams 256-dimensional spatial latent vectors and road-roughness index (RRI) scores to this app in real time. The app fuses those with GPS location from the Android platform and packages them as a `VigiaSearchContext` — the rich payload sent to the VIGIASearch Fargate backend. This means every copilot answer is grounded in the vehicle's actual road context, not just the user's typed query.

The cloud backend validates Ed25519-signed telemetry, issues DePIN reward micro-VGA tokens, routes hazard alerts over MQTT, and proxies all Sarvam AI calls so no API key ever appears in the APK.

---

## App Architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│                         :app (shell)                                  │
│  MainActivity · VigiaApplication · AppModule (DI bindings + secrets) │
└────────────────┬───────────────────────────┬─────────────────────────┘
                 │                           │
    ┌────────────▼──────────┐   ┌────────────▼──────────────┐
    │  :feature:copilot     │   │  :feature:maps             │
    │  AppRoot · CopilotRoute│  │  MapsScreen · MapsViewModel│
    │  CopilotViewModel     │   │  MapsRepository            │
    │  AuthScreen · AuthVM  │   │  5 map-layer composables   │
    │  StripePaySheet       │   └────────────────────────────┘
    │  VoiceCallOverlay     │
    └────────────┬──────────┘
                 │  depends on
   ┌─────────────▼──────────────────────────────────────────────┐
   │               core modules (shared, no UI)                  │
   │                                                             │
   │  :core:model    — domain data classes                       │
   │  :core:network  — Retrofit / OkHttp / MQTT / Sarvam / SSE  │
   │  :core:sensor   — BLE GATT · KeystoreManager · CDM · TTS   │
   │  :core:data     — Room database · ChatRepository            │
   │  :core:auth     — Amplify Cognito · AuthRepository          │
   │  :core:wallet   — Ed25519KeyStore · WalletRepository        │
   └─────────────────────────────────────────────────────────────┘
                 │
    ┌────────────▼────────────────────────────────────┐
    │  :feature:pairing (one-time QR + CDM pairing)   │
    └─────────────────────────────────────────────────┘
```

The target is for feature implementations not to depend on one another. Currently `feature:copilot` depends
on `feature:maps` and `feature:pairing`; `:app` remains the composition root. Build configuration includes
public client configuration and endpoints as well as values that must be supplied by CI; required prod values
are not yet fail-fast validated.

---

## Module Breakdown

| Module | Responsibility | Key Dependencies |
|---|---|---|
| `:app` | Shell: `MainActivity`, `VigiaApplication`, `AppModule`, product flavors (`demo`/`prod`) | All feature and core modules |
| `:build-logic:convention` | 6 convention plugins that enforce `compileSdk=36`, `minSdk=34`, Hilt, Compose, and KSP across all modules | AGP 8.13.2, Kotlin 2.0.21 |
| `:core:model` | Pure Kotlin domain types: `HazardAlert`, `TraceFrame`, `ChatMessage`, `BleLinkState`, `VigiaSearchContext`, `RriScore`, `SpatialLatentVector` | none |
| `:core:network` | HTTP clients (Retrofit + OkHttp), Sarvam STT/TTS, VIGIASearch SSE, MQTT alert delivery, Stripe Connect, FCM receiver | OkHttp 4.12, Paho 1.2.5, Stripe 21.5 |
| `:core:sensor` | BLE GATT lifecycle (`BleLinkManager`), BLE data streaming, ECDH P-256 handshake, `KeystoreManager`, CDM presence API, `ContextAggregator`, `TtsManager`, `VoiceAmplitudeMonitor`, foreground service | CameraX 1.4.2, Android Keystore |
| `:core:data` | Room database (`VigiaDatabase`), `ChatMessageDao`, `ChatSessionDao`, `ChatRepository` | Room 2.7.1 |
| `:core:auth` | Amplify Cognito sign-in/sign-up/confirm/Google federation, `AuthRepository` | Amplify 2.19.1, Credential Manager 1.3 |
| `:core:wallet` | `Ed25519KeyStore` (TEE-wrapped), `WalletRepository`, telemetry signing, ownership proofs, balance refresh | Android Keystore |
| `:feature:copilot` | Main copilot UI: `CopilotScreen`, voice overlay, AI orb, wallet panel, Stripe sheet, auth gate, chat history drawer | `:core:*`, Haze 1.5.3 |
| `:feature:maps` | OSMDroid map with 5 layers (hazard, geohash, maintenance, route, trace playback), bottom sheet, sensor status strip | OSMDroid 6.1.20 |
| `:feature:pairing` | One-time QR scan (CameraX + ML Kit), CompanionDeviceManager pairing flow | CameraX 1.4.2, ML Kit 17.3 |

---

## Key Features

### DePIN Rewards Wallet
The wallet identity is an Ed25519 keypair generated in software on first launch. Its private encoding is
AES-GCM-wrapped by an Android Keystore key and stored in SharedPreferences; it is decrypted into app memory
for signing. Registration/balance proof code exists, but freshness, replay, concurrency, backup exclusion,
measured Keystore security level and end-to-end backend enforcement remain production hardening work.

### Ed25519 Telemetry Signing with Frame SHA-256
Every hazard event POSTed to the backend is signed. The payload format is:

```
VIGIA:<type>:<lat>:<lon>:<timestamp>:<confidence>           (no camera frame)
VIGIA:<type>:<lat>:<lon>:<timestamp>:<confidence>:<sha256>  (with JPEG frame)
```

The optional `<sha256>` field binds the raw JPEG bytes to the signature, preventing frame substitution at the validator Lambda.

### Balance Ownership Proofs
Balance queries carry an `X-Wallet-Signature` header: an Ed25519 signature over `"VIGIA-BALANCE:<wallet>:<timestamp>"`. This prevents replay attacks and proves private-key possession without any pre-shared secret.

### Sarvam Voice STT / TTS via Backend Proxy
The app records 16 kHz 16-bit mono WAV, sends it to the backend's `/sarvam-proxy/stt` endpoint (which calls Sarvam `saarika:v2`), receives a transcript, runs the VIGIASearch pipeline, then calls `/sarvam-proxy/tts` (`bulbul:v1`, voice "meera") for audio synthesis. The Sarvam API key lives exclusively in AWS Secrets Manager — the APK contains no AI credentials.

### VIGIASearch Streaming Copilot
Queries are enriched with live GPS coordinates, velocity, RRI score, and a 256-D spatial latent vector from the Pi before being sent to the VIGIASearch Fargate endpoint as a Server-Sent Events stream. The `OkHttpSseSearchClient` emits `SearchEvent.Step`, `SearchEvent.TextDelta`, `SearchEvent.Metadata`, and `SearchEvent.Done` events. During voice mode, each reasoning step is narrated via Sarvam TTS before the final answer plays.

### MQTT Hazard Alerts with TTS
A persistent Eclipse Paho MQTT connection subscribes to `vigia/alerts/{userId}` (QoS 1, `cleanSession=false`). On message receipt, the `HazardAlert` is emitted over a `SharedFlow`, the severity is evaluated, and `TtsManager.speak()` is called with `QUEUE_FLUSH` for CRITICAL or `QUEUE_ADD` for others. FCM is wired as a secondary delivery path for Doze-mode wakeup via `VigiaFcmReceiver`.

### Stripe Connect Payout and Cash-Out — incomplete; disable in production
Repository endpoints and UI components exist, but the current code reports a client secret as
`PaymentSucceeded`, stores mutable proof data on a singleton and does not wire a complete settlement flow.
Production requires server-authoritative quotes/balance, persisted idempotency, verified Stripe webhooks,
processing/reversal states and reconciliation.

### BLE GATT Link to the Edge Node
`BleLinkManager` drives the full connection pipeline in order: LE scan by MAC → GATT connect → MTU 517 negotiation + 2M PHY → LE Secure Connections bond → ECDH P-256 mutual handshake → stream-mode confirmation (REQUEST\_256D opcode) → TELEMETRY\_CHAR notifications. The session key is derived via `HKDF-SHA256(ECDH(Pi_priv, Phone_pub), salt=nonce_pi||nonce_phone, info="vigia-ble-v1")`.

---

## Security Model

| Layer | Mechanism |
|---|---|
| Wallet signing key | Software Ed25519; PKCS#8 encrypted at rest by an Android Keystore AES key; private key enters app memory while signing |
| Private key at rest | AES-256-GCM encrypted ciphertext + IV in SharedPreferences; wrapping key in AndroidKeyStore |
| BLE session key | HKDF-SHA256 over ECDH-P256 shared secret; per-connection nonces prevent replay |
| BLE mutual auth | Pi sends CHALLENGE with ECDSA-signed nonce; phone verifies against pinned Pi public key (from QR) and replies with its own ECDSA-signed RESPONSE; Pi sends HMAC-SHA256 CONFIRM |
| Telemetry integrity | Ed25519 signature over typed payload; optional SHA-256 digest binds JPEG frame |
| Balance ownership | Ed25519 signature over `VIGIA-BALANCE:<wallet>:<ts>` on every balance request |
| Device registration | Proof-of-possession: Ed25519 signature over `VIGIA-REGISTER:<pubkey>` |
| API credentials | Sarvam API key and other secrets live in AWS Secrets Manager; APK contains no AI credentials |
| Cognito JWT | `VigiaAuthInterceptor` injects `Authorization: Bearer <id_token>` on all Vigia backend calls |
| BLE hardware key | `KeystoreManager` generates an EC P-256 key with `PURPOSE_AGREE_KEY | PURPOSE_SIGN`; StrongBox is preferred with silent fallback to regular Keystore TEE |

No secrets, API keys, or private key material appear in the APK. All external service credentials route through the backend proxy.

---

## Tech Stack

| Category | Library | Version |
|---|---|---|
| Language | Kotlin | 2.0.21 |
| UI toolkit | Jetpack Compose BOM | 2025.05.01 |
| Material Design | Material 3 | 1.3.2 |
| DI | Hilt / Dagger | 2.56.2 |
| DI Navigation | hilt-navigation-compose | 1.2.0 |
| Navigation | navigation-compose | 2.9.0 |
| Database | Room | 2.7.1 |
| Preferences | DataStore Preferences | 1.1.4 |
| HTTP | OkHttp | 4.12.0 |
| REST | Retrofit | 2.11.0 |
| MQTT | Eclipse Paho v3 | 1.2.5 |
| Payments | Stripe Android SDK | 21.5.0 |
| Maps | OSMDroid | 6.1.20 |
| Camera | CameraX | 1.4.2 |
| Barcode / QR | ML Kit Barcode Scanning | 17.3.0 |
| Auth | Amplify aws-auth-cognito | 2.19.1 |
| Google Sign-In | Credential Manager | 1.3.0 |
| Coroutines | kotlinx.coroutines | 1.9.0 |
| Blur effects | Haze | 1.5.3 |
| Firebase | Firebase BOM (FCM) | 33.14.0 |
| AWS SDK | aws-sdk-kotlin core | 1.3.100 |
| Build automation | AGP | 8.13.2 |
| Symbol processing | KSP | 2.0.21-1.0.28 |

---

## Data and State

**Room database (`VigiaDatabase`)** — stores `ChatSessionEntity` and `ChatMessageEntity`. `ChatRepository` wraps both DAOs; `ChatRepositoryImpl` exposes `Flow<List<ChatSession>>` and `Flow<List<ChatMessage>>` for reactive UI updates. Messages include `sources`, `reasoningSteps`, `latencyMs`, and a `MessageStatus` (`Complete` / `Partial`) so interrupted streams are preserved.

**DataStore Preferences** — `AppRootViewModel` reads a `isPaired` boolean from DataStore to gate the QR pairing screen. Written once by `AppRootViewModel.onPairingComplete()`.

**StateFlow / MVVM** — every ViewModel exposes `StateFlow` derived from Kotlin coroutines. `CopilotViewModel.uiState` is a sealed `CopilotUiState` with a rich `Active` subtype that carries orb state, voice listening state, wallet UI state, pending alerts list, streaming answer, reasoning steps, and map markers. `MapsViewModel.uiState` carries the full `MapsUiState` including active layers, hazards, route, geohash cells, maintenance POIs, economic zones, and trace playback position.

**ContextAggregator** — combines `locationFlow()` (GPS via `LocationManager.FUSED_PROVIDER`) with `BleDataStreamer.telemetryFrames` (GATT notifications) using `Flow.combine`. Pre-seeds both sources with safe defaults so the combine emits immediately on first real update.

---

## Getting Started

### Prerequisites

- Android Studio Hedgehog or newer
- JDK 17
- Android device or emulator running API 34+
- A provisioned VIGIA Pi 5 Blackbox (for BLE features; optional for copilot and maps)

### Secrets

Create `secrets.properties` at the repo root (gitignored). Use `secrets.properties.example` as the template. Required keys:

```properties
VIGIA_API_BASE_URL=https://<your-api-gateway>.execute-api.<region>.amazonaws.com/prod
MQTT_BROKER_URI=ssl://<your-iot-endpoint>.iot.<region>.amazonaws.com:8883
STRIPE_PUBLISHABLE_KEY=pk_test_...
BLACKBOX_MAC=AA:BB:CC:DD:EE:FF
```

Sarvam API key and AWS credentials are **not** placed in `secrets.properties`; they live in AWS Secrets Manager and are accessed through the VIGIA backend proxy.

### Build Flavors

| Flavor | ApplicationId | Notes |
|---|---|---|
| `demo` | `com.vigia.copilot.demo` | Test Stripe keys, sandbox backend |
| `prod` | `com.vigia.copilot` | Production keys via CI env vars |

### Build Commands

```bash
# Assemble demo debug APK
./gradlew :app:assembleDemoDebug

# Assemble production release APK (requires signing config)
./gradlew :app:assembleProdRelease

# Run unit tests
./gradlew test

# Run instrumented tests
./gradlew connectedAndroidTest

# Install demo debug on connected device
./gradlew :app:installDemoDebug
```

### AWS / Amplify Configuration

Place `amplifyconfiguration.json` (Cognito User Pool + Identity Pool IDs) in `app/src/main/res/raw/`. This file is gitignored. `AmplifyInitializer` initialises Amplify from `ContentProvider` before `Application.onCreate` so auth state is available immediately.

---

## Project Structure

```
vigia2/
├── app/                          # Shell module
│   └── src/main/java/com/vigia/copilot/
│       ├── MainActivity.kt
│       ├── VigiaApplication.kt
│       └── di/AppModule.kt
├── build-logic/convention/        # 6 convention plugins
│   └── src/main/kotlin/
│       ├── AndroidApplicationConventionPlugin.kt
│       ├── AndroidLibraryConventionPlugin.kt
│       ├── AndroidFeatureConventionPlugin.kt
│       ├── AndroidHiltConventionPlugin.kt
│       └── KotlinAndroid.kt
├── core/
│   ├── model/                    # Pure Kotlin domain types
│   ├── network/                  # HTTP, MQTT, Sarvam, Stripe, SSE
│   ├── sensor/                   # BLE, Keystore, CDM, TTS, voice
│   ├── data/                     # Room, ChatRepository
│   ├── auth/                     # Amplify Cognito
│   └── wallet/                   # Ed25519KeyStore, WalletRepository
├── feature/
│   ├── copilot/                  # Main UI, voice overlay, orb, auth gate
│   ├── maps/                     # OSMDroid map, 5 layers, bottom sheet
│   └── pairing/                  # QR scan + CDM pairing flow
├── gradle/libs.versions.toml     # Central version catalog
├── settings.gradle.kts
└── build.gradle.kts
```

---

## About the Developer

**Tom Mathew** (National Institute of Technology, Rourkela) and Team (Ben Biju & Shreeram Balasubramanian).

VIGIA Mobile grew from a fascination with the intersection of embedded systems and intelligent mobile software. The challenge was not just building an Android app — it was building an Android app that meaningfully communicates with a Raspberry Pi 5 running real-time CV inference at the edge, signs cryptographic proofs on behalf of a decentralised participation network, and delivers a voice-first AI copilot that works hands-free in a moving vehicle. Every design decision, from the ECDH P-256 BLE handshake protocol to the coroutine-driven SSE streaming client, was driven by that constraint: reliable, low-latency, security-first, offline-resilient software that earns the driver's trust in conditions where mistakes are expensive.

Skills exercised: multi-module Kotlin/Compose architecture, Android Keystore / TEE crypto, BLE GATT client state machines, server-sent event streaming, Amplify Cognito auth, Stripe Connect, Hilt DI at scale, OSMDroid custom canvas layers, and backend integration across AWS IoT Core, Lambda, and API Gateway.

---

## License

MIT License — Copyright © 2026 Tom Mathew.

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED.

---

## Resources

- [VIGIA Edge Node (vigia-raspi)](https://github.com/VigiaLabs/vigia-raspi) — Pi 5 Blackbox firmware
- [Sarvam AI](https://sarvam.ai) — Indian-language STT (`saarika:v2`) and TTS (`bulbul:v1`)
- [OSMDroid](https://github.com/osmdroid/osmdroid) — OpenStreetMap tile engine for Android
- [Eclipse Paho MQTT](https://github.com/eclipse/paho.mqtt.android) — MQTT client library
- [Amplify Android](https://docs.amplify.aws/android/) — Cognito auth integration
- [Stripe Android SDK](https://stripe.com/docs/mobile/android) — Connect onboarding and payouts
- [Android Keystore System](https://developer.android.com/training/articles/keystore) — TEE-backed key management
- [Hilt dependency injection](https://developer.android.com/training/dependency-injection/hilt-android)
- [Jetpack Compose](https://developer.android.com/jetpack/compose)
