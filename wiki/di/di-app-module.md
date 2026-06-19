---
title: "AppModule (Hilt)"
type: di
tags: [di, app]
source: "app/src/main/java/com/vigia/copilot/di/AppModule.kt"
related: ["[[app-module]]", "[[di-network-module]]", "[[di-wallet-module]]"]
updated: 2026-06-20
---

# AppModule (Hilt)

`@Module @InstallIn(SingletonComponent)`. Provides the named string bindings that carry `BuildConfig` secrets into the DI graph without `:core:network` or `:core:wallet` directly depending on `:app`.

## Provides

| Name | Source |
|---|---|
| `@Named("VigiaApiBaseUrl")` | `BuildConfig.VIGIA_API_BASE_URL` |
| `@Named("MqttBrokerUri")` | `BuildConfig.MQTT_BROKER_URI` |
| `@Named("BlackboxMac")` | `BuildConfig.BLACKBOX_MAC` |
| `BlackboxConfig` | `BlackboxConfig(deviceAddress = BuildConfig.BLACKBOX_MAC)` |
| `ApiTokenProvider` | Bridges `AuthRepository.getIdToken()` for OkHttp interceptors |

## Links

[[app-module]] [[di-network-module]] [[di-wallet-module]] [[vigia-auth-interceptor]]
