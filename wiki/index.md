---
title: "VIGIA Android — Map of Content"
type: index
tags: [decision, vigia-android]
source: "settings.gradle.kts"
related: []
updated: 2026-06-20
---

# VIGIA Android — Map of Content

Central hub for the VIGIA Mobile Copilot Obsidian vault. Navigate the graph or follow wikilinks.

VIGIA Mobile is the Android companion for the VIGIA Pi 5 Blackbox: it pairs over BLE GATT, fuses live telemetry with GPS into a voice copilot, delivers real-time MQTT hazard alerts with TTS, and manages the on-device DePIN rewards wallet with Stripe cash-out.

---

## Gradle Modules

### Core
- [[core-model]] — domain data classes (no Android deps)
- [[core-network]] — HTTP / MQTT / Sarvam / Stripe / SSE
- [[core-sensor]] — BLE, Keystore, CDM, TTS, voice capture
- [[core-data]] — Room database and ChatRepository
- [[core-auth]] — Amplify Cognito auth
- [[core-wallet]] — Ed25519 wallet, telemetry signing

### Feature
- [[feature-copilot]] — main copilot UI module
- [[feature-maps]] — OSMDroid map module
- [[feature-pairing]] — QR scan and CDM pairing module

### App / Build
- [[app-module]] — shell, MainActivity, VigiaApplication, AppModule
- [[build-logic]] — 6 convention plugins

---

## Screens
- [[app-root-screen]] — auth + pairing gate composable
- [[auth-screen]] — Cognito sign-in / sign-up / confirm / Google federation
- [[copilot-screen]] — main copilot chat and wallet UI
- [[voice-call-overlay]] — hands-free full-screen voice session
- [[stripe-pay-sheet]] — Stripe payout and onboarding bottom sheet
- [[maps-screen]] — OSMDroid hazard and route map
- [[pairing-screen]] — QR scan + CDM companion pairing

---

## ViewModels
- [[app-root-viewmodel]] — auth state routing, pairing DataStore gate
- [[auth-viewmodel]] — Cognito sign-in / sign-up / Google flows
- [[copilot-viewmodel]] — orchestrator: search, voice, wallet, alerts
- [[maps-viewmodel]] — map state, layer toggles, route, trace playback
- [[pairing-viewmodel]] — QR detection, CDM pairing flow

---

## Repositories
- [[wallet-repository]] — Ed25519 signing, balance refresh, provisioning
- [[chat-repository]] — Room sessions and messages
- [[mqtt-alert-repository]] — MQTT hazard alert subscription
- [[stripe-pay-repository]] — Stripe Connect onboarding and payouts
- [[ble-repository]] — BLE device config persistence
- [[pairing-repository]] — PairedConfig persistence in DataStore
- [[claim-device-repository]] — backend device-claim API
- [[cdm-presence-repository]] — CompanionDeviceManager presence
- [[maps-repository]] — hazards, geohash, route, maintenance, traces API
- [[auth-repository]] — AuthRepository interface + Amplify / Demo impls

---

## Hilt DI Modules
- [[di-network-module]] — OkHttp clients, Sarvam, MQTT, Stripe, SSE bindings
- [[di-sensor-module]] — BLE, CDM, Keystore, TTS, voice
- [[di-data-module]] — Room VigiaDatabase
- [[di-auth-module]] — AuthRepository binding
- [[di-wallet-module]] — Ed25519KeyStore, WalletOkHttpClient
- [[di-maps-module]] — MapsRepository, Retrofit for maps API
- [[di-app-module]] — named strings: API URL, MQTT URI, Blackbox MAC

---

## Network Clients
- [[sarvam-stt-client]] — Sarvam saarika:v2 speech-to-text
- [[sarvam-tts-client]] — Sarvam bulbul:v1 text-to-speech
- [[vigia-search-client]] — VIGIASearch SSE streaming copilot
- [[okhttp-sse-search-client]] — OkHttp SSE implementation
- [[ble-link-manager]] — BLE GATT lifecycle state machine
- [[ble-data-streamer]] — GATT telemetry frame decoder
- [[tts-manager]] — Android TTS + Sarvam TTS coordinator
- [[voice-amplitude-monitor]] — microphone amplitude and WAV export
- [[vigia-fcm-receiver]] — FCM fallback alert delivery

---

## Security
- [[ed25519-keystore]] — TEE-backed Ed25519 wallet keypair
- [[keystore-manager]] — EC P-256 GATT BLE identity key
- [[ecdh-handshake]] — ECDH P-256 BLE mutual authentication utilities
- [[vigia-auth-interceptor]] — Cognito JWT injection on HTTP requests

---

## Data Models
- [[hazard-alert-model]] — HazardAlert and Severity enum
- [[chat-message-model]] — ChatMessage domain type
- [[chat-session-model]] — ChatSession domain type
- [[ble-link-state-model]] — BleLinkState sealed class
- [[trace-frame-model]] — TraceFrame
- [[vigia-search-context-model]] — VigiaSearchContext
- [[rri-score-model]] — RriScore
- [[spatial-latent-vector-model]] — SpatialLatentVector
- [[location-snapshot-model]] — LocationSnapshot
- [[wallet-state-model]] — WalletState and TelemetrySignature
- [[search-event-model]] — SearchEvent sealed class (SSE events)
- [[bezier-route-model]] — BezierRoute
- [[geohash-cell-model]] — GeohashCell
- [[economic-zone-model]] — EconomicZone
- [[maintenance-poi-model]] — MaintenancePoi
- [[search-place-model]] — SearchPlace

---

## End-to-End Flows
- [[flow-voice-copilot]] — mic → STT → search SSE → TTS auto-loop
- [[flow-telemetry-sign-upload]] — BLE frame → Ed25519 sign → POST
- [[flow-wallet-balance-refresh]] — ownership proof → balance fetch
- [[flow-stripe-payout]] — wallet proof → Stripe payment intent
- [[flow-mqtt-hazard-alert]] — MQTT message → HazardAlert → TTS
- [[flow-ble-pairing]] — QR scan → CDM → BLE connect → ECDH handshake

---

## Architecture Decisions
- [[adr-minsdk-34]] — Why minSdk=34 (CDM Presence API)
- [[adr-ecdh-p256]] — Why ECDH P-256 replaced HMAC for BLE auth
- [[adr-sarvam-proxy]] — Why Sarvam calls route through backend proxy
- [[adr-osmdroid]] — Why OSMDroid over Google Maps
- [[adr-room-chat]] — Why Room for offline-first chat history

## External Dependencies
- [[aws-backend]] — API Gateway, IoT Core, Lambda validators
- [[aws-cognito-amplify]] — Cognito User Pools + Hosted UI
- [[stripe-sdk]] — Stripe Android SDK 21.5.0
- [[sarvam-ai]] — Indian-language AI voice models
- [[firebase-fcm]] — Firebase Cloud Messaging fallback
- [[osmdroid]] — OpenStreetMap tile engine
- [[eclipse-paho-mqtt]] — Eclipse Paho MQTT v3 client

## Links

[[core-model]] [[core-network]] [[core-sensor]] [[core-data]] [[core-auth]] [[core-wallet]]
[[feature-copilot]] [[feature-maps]] [[feature-pairing]] [[app-module]] [[build-logic]]
