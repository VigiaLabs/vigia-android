---
title: "core:network"
type: module
tags: [module, network]
source: "core/network/build.gradle.kts"
related: ["[[core-model]]", "[[di-network-module]]", "[[feature-copilot]]"]
updated: 2026-06-20
---

# core:network

Android library module. All outbound I/O: HTTP, SSE streaming, MQTT, Sarvam STT/TTS proxy, Stripe Connect, and FCM push-to-MQTT bridge.

## Key Files

| Path | Role |
|---|---|
| `di/NetworkModule.kt` | Hilt `SingletonComponent` — provides OkHttp clients, binds all interfaces |
| `auth/VigiaAuthInterceptor.kt` | Adds `Authorization: Bearer <id_token>` to all Vigia backend calls |
| `auth/ApiTokenProvider.kt` | Fetches Cognito ID token from Amplify for the interceptor |
| `mqtt/MqttAlertRepositoryImpl.kt` | Eclipse Paho MQTT client; topic `vigia/alerts/{userId}`, QoS 1 |
| `sarvam/SarvamSttClientImpl.kt` | POSTs WAV to backend `/sarvam-proxy/stt`; calls Sarvam saarika:v2 |
| `sarvam/SarvamTtsClientImpl.kt` | POSTs text to `/sarvam-proxy/tts`; returns raw WAV bytes |
| `search/OkHttpSseSearchClient.kt` | SSE stream via OkHttp; emits `SearchEvent` on the calling Flow |
| `search/SearchEvent.kt` | Sealed class: Step, TextDelta, Metadata, Done |
| `stripe/StripePayRepositoryImpl.kt` | Stripe Connect onboarding, PaymentIntent, FinancialConnections |
| `stripe/PayoutStatus.kt` | Stripe payout state machine |
| `fcm/VigiaFcmReceiver.kt` | FCM → `MqttAlertRepository.injectAlert()` fallback |

## OkHttp Clients

Two named clients are provided:
- **Plain `OkHttpClient`** — for Sarvam calls (no auth header)
- **`@Named("VigiaOkHttpClient")`** — has `VigiaAuthInterceptor`; 120 s read timeout, 0 call timeout (stream cancels via coroutine scope)

## Hilt DI

See [[di-network-module]].

## Links

[[di-network-module]] [[sarvam-stt-client]] [[sarvam-tts-client]] [[vigia-search-client]]
[[okhttp-sse-search-client]] [[mqtt-alert-repository]] [[stripe-pay-repository]]
[[vigia-auth-interceptor]] [[vigia-fcm-receiver]] [[search-event-model]] [[core-model]]
