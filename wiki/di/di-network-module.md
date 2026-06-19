---
title: "NetworkModule (Hilt)"
type: di
tags: [di, network]
source: "core/network/src/main/kotlin/com/vigia/core/network/di/NetworkModule.kt"
related: ["[[core-network]]", "[[sarvam-stt-client]]", "[[vigia-search-client]]", "[[mqtt-alert-repository]]"]
updated: 2026-06-20
---

# NetworkModule (Hilt)

`@Module @InstallIn(SingletonComponent)`. Abstract class with `@Binds` for interface→impl and `companion object` with `@Provides` for concrete instances.

## Bindings

| Interface | Implementation |
|---|---|
| `VigiaSearchClient` | `OkHttpSseSearchClient` |
| `MqttAlertRepository` | `MqttAlertRepositoryImpl` |
| `StripePayRepository` | `StripePayRepositoryImpl` |
| `SarvamTtsClient` | `SarvamTtsClientImpl` |
| `SarvamSttClient` | `SarvamSttClientImpl` |

## Provided

- **Plain `OkHttpClient`** — no auth; 15 s connect, 120 s read, 30 s write; logging interceptor (BODY in debug)
- **`@Named("VigiaOkHttpClient")`** — adds `VigiaAuthInterceptor` before logging interceptor

Comment: `@Named("VigiaApiBaseUrl")` is provided by `:app`'s `AppModule` to avoid compile-time dep from `:core:network` → `:app`.

## Links

[[core-network]] [[vigia-auth-interceptor]] [[sarvam-stt-client]] [[sarvam-tts-client]]
[[vigia-search-client]] [[mqtt-alert-repository]] [[stripe-pay-repository]] [[di-app-module]]
