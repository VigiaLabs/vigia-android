# :app

**Layer:** Entry point  
**Package:** `com.vigia.copilot` (application)  
**Path:** `app/`  
**Depends on:** [[feature-copilot]] · [[feature-maps]] · [[core-sensor]] · [[core-network]] · [[core-model]] · [[core-auth]]

The Android application module. Owns the Compose `NavHost`, the Hilt `@SingletonComponent` root (`AppModule`), and bridges build-flavour secrets into the DI graph.

## Key Responsibilities

### Navigation
- Single-activity architecture with `NavHost`
- Routes: `auth/` screen set → main `CopilotRoute` + embedded `MapsRoute`

### AppModule — DI Bridges
`AppModule` is the only place that reads `BuildConfig`, keeping all other modules secret-agnostic:

| Provides | Qualifier | Source |
|----------|-----------|--------|
| `BlackboxConfig` | — | `BuildConfig.BLACKBOX_MAC` |
| `String` (MQTT URI) | `@Named("MqttBrokerUri")` | `BuildConfig.MQTT_BROKER_URI` |
| `String` (API base URL) | `@Named("VigiaApiBaseUrl")` | `BuildConfig.VIGIA_API_BASE_URL` |
| `String` (Sarvam key) | `@Named("SarvamApiKey")` | `BuildConfig.SARVAM_API_KEY` |
| `ApiTokenProvider` | — | `runBlocking { AuthRepository.getIdToken() }` → bridges suspend to OkHttp sync |

### Secrets Management
Secrets come from `secrets.properties` (gitignored) for local dev, or CI environment variables in production. Injected into `BuildConfig` by the convention Gradle plugin. **Never committed, never hardcoded.**

```
secrets.properties (local only, gitignored):
  SARVAM_API_KEY=…
  MQTT_BROKER_URI=ssl://…iot.us-east-1.amazonaws.com:8883
  VIGIA_API_BASE_URL=https://…
  BLACKBOX_MAC=XX:XX:XX:XX:XX:XX
  STRIPE_PUBLISHABLE_KEY=pk_…
```

## Product Flavours

| Flavour | Auth | BLE MAC | API URLs |
|---------|------|---------|----------|
| `demo` | `DemoAuthRepository` (no Cognito) | `00:00:00:00:00:00` (placeholder) | Dev endpoints |
| `prod` | `AmplifyAuthRepository` (real Cognito) | Real MAC from CI `BLACKBOX_MAC` | Production endpoints |

## Theme
`VigiaTheme` wraps the app with Material3 + dynamic colour (Android 12+). Dynamic colour is sourced from the system wallpaper — voice overlay overrides to always-dark `Color(0xFF07060A)`.

## Module Graph Root
```
:app
  compile → :feature:copilot  → :feature:maps
                              → :core:network  → :core:model
                              → :core:sensor   → :core:model
                              → :core:data     → :core:model
                              → :core:auth
         → :core:sensor
         → :core:network
         → :core:model
         → :core:auth
```
