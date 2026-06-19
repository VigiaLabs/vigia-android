---
title: "AWS Cloud Backend"
type: external
tags: [external, cloud]
source: "app/src/main/java/com/vigia/copilot/di/AppModule.kt"
related: ["[[wallet-repository]]", "[[mqtt-alert-repository]]", "[[stripe-pay-repository]]", "[[maps-repository]]", "[[aws-cognito-amplify]]"]
updated: 2026-06-20
---

# AWS Cloud Backend

The VIGIA cloud backend. `VIGIA_API_BASE_URL` and `MQTT_BROKER_URI` are injected via `BuildConfig` from `secrets.properties` / CI env vars.

## Endpoints Used by the App

| Path | Method | Used By |
|---|---|---|
| `/register-device` | POST | `WalletRepositoryImpl.ensureProvisioned()` |
| `/rewards-balance` | GET | `WalletRepositoryImpl.refreshBalance()` |
| `/submit-telemetry` | POST | Telemetry upload |
| `/claim-device` | POST | `ClaimDeviceRepositoryImpl` |
| `/sarvam-proxy/stt` | POST | `SarvamSttClientImpl` |
| `/sarvam-proxy/tts` | POST | `SarvamTtsClientImpl` |
| `/v1/search` | POST (SSE) | `OkHttpSseSearchClient` |
| `/stripe/connect-onboarding` | POST | `StripePayRepositoryImpl` |
| `/stripe/create-payment-intent` | POST | `StripePayRepositoryImpl` |
| `/stripe/financial-connections-session` | POST | `StripePayRepositoryImpl` |
| Maps endpoints | Various | `MapsApiService` (Retrofit) |

## IoT Core

MQTT broker at `ssl://<endpoint>.iot.<region>.amazonaws.com:8883`. Topic: `vigia/alerts/{userId}`.

## Links

[[wallet-repository]] [[mqtt-alert-repository]] [[stripe-pay-repository]] [[maps-repository]]
[[claim-device-repository]] [[sarvam-stt-client]] [[sarvam-tts-client]] [[vigia-search-client]]
[[aws-cognito-amplify]]
