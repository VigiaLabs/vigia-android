---
title: ":app (shell module)"
type: module
tags: [module, app]
source: "app/build.gradle.kts"
related: ["[[feature-copilot]]", "[[di-app-module]]", "[[build-logic]]"]
updated: 2026-06-20
---

# :app (shell module)

Application shell. Ties all feature and core modules together. Owns `@HiltAndroidApp VigiaApplication`, `@AndroidEntryPoint MainActivity`, and `AppModule` which provides named Hilt strings.

## Key Files

| Path | Role |
|---|---|
| `com/vigia/copilot/VigiaApplication.kt` | `@HiltAndroidApp`; Amplify is initialised by `AmplifyInitializer` ContentProvider before this |
| `com/vigia/copilot/MainActivity.kt` | Single-Activity host; calls `AppRoot()` inside `VigiaTheme` |
| `com/vigia/copilot/di/AppModule.kt` | `@Named("VigiaApiBaseUrl")`, `@Named("MqttBrokerUri")`, `@Named("BlackboxMac")` — read from `BuildConfig` fields set by convention plugin |

## Product Flavors

| Flavor | applicationId | Notes |
|---|---|---|
| `demo` | `com.vigia.copilot.demo` | Sandbox Stripe keys, test backend |
| `prod` | `com.vigia.copilot` | Production via CI env vars |

## Build Config Fields (all flavors)

`STRIPE_PUBLISHABLE_KEY`, `MQTT_BROKER_URI`, `VIGIA_API_BASE_URL`, `BLACKBOX_MAC` — sourced from `secrets.properties` or environment variables; never hard-coded.

## AndroidManifest Permissions

`ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `BLUETOOTH_SCAN` (neverForLocation), `BLUETOOTH_CONNECT`, `INTERNET`.

## Links

[[di-app-module]] [[build-logic]] [[feature-copilot]] [[feature-maps]] [[feature-pairing]]
[[core-model]] [[core-network]] [[core-sensor]] [[core-data]] [[core-auth]] [[core-wallet]]
