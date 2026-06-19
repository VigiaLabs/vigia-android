# core:network

**Layer:** Core  
**Package:** `com.vigia.core.network`  
**Path:** `core/network/`  
**Depends on:** [[core-model]]

All outbound network I/O. Provides two scoped OkHttp clients, the LangGraph SSE search client, Sarvam AI STT/TTS, MQTT alert ingestion, FCM wakeup, and Stripe payment stubs.

## OkHttp Clients

| Qualifier | Purpose |
|-----------|---------|
| *(plain)* `OkHttpClient` | Sarvam AI calls only — no auth header |
| `@Named("VigiaOkHttpClient")` | Maps, Search, FCM registration — carries `Authorization: Bearer <Cognito ID token>` via `VigiaAuthInterceptor` |

### Auth Interceptor
| Type | Description |
|------|-------------|
| `ApiTokenProvider` | Interface: `getIdToken(): String?` — implemented in [[app]]'s `AppModule` via `runBlocking` bridge to `AuthRepository.getIdToken()` |
| `VigiaAuthInterceptor` | OkHttp `Interceptor` — adds `Authorization` header when token is non-null |

## Search (LangGraph SSE)

| Type | Description |
|------|-------------|
| `VigiaSearchClient` | Interface: `search(VigiaSearchContext): Flow<SearchEvent>` |
| `OkHttpSseSearchClient` | Streams `text/event-stream` from Fargate `/v1/search`; uses `@Named("VigiaOkHttpClient")` |
| `SearchEvent` | Sealed: `Step(message, ts) · TextDelta(delta) · Metadata(sources, spatialMarkers, latencyMs) · Done` |

SSE event order: `step*` → `text*` → `metadata` → `done`  
Steps are spoken by [[core-sensor]]'s `TtsManager` as they arrive.

## Sarvam AI

| Type | Description |
|------|-------------|
| `SarvamSttClient` | Interface: `transcribe(wavBytes, languageCode): String` |
| `SarvamSttClientImpl` | `POST https://api.sarvam.ai/speech-to-text`; model `saarika:v2.5`; omits `language_code` when unknown |
| `SarvamTtsClient` | Interface: `synthesize(text, languageCode): ByteArray` |
| `SarvamTtsClientImpl` | `POST https://api.sarvam.ai/text-to-speech`; model `bulbul:v1`; returns raw WAV bytes |

API key: `API-Subscription-Key` header — sourced from `BuildConfig.SARVAM_API_KEY` (never logged).

## MQTT Alerts

| Type | Description |
|------|-------------|
| `MqttAlertRepository` | Interface: `alerts: Flow<HazardAlert> · reconnect() · injectAlert()` |
| `MqttAlertRepositoryImpl` | Paho MQTT over TLS to AWS IoT Core; `ssl://…iot.us-east-1.amazonaws.com:8883` |

Broker URI from `BuildConfig.MQTT_BROKER_URI` (secrets.properties / CI env).

## FCM

| Type | Description |
|------|-------------|
| `VigiaFcmReceiver` | `FirebaseMessagingService` — triggers `reconnect()` on Doze wakeup; injects direct alert if FCM payload contains `type=alert`; registers new token via `@Named("VigiaOkHttpClient")` |

## Stripe

| Type | Description |
|------|-------------|
| `StripePayRepository` | Interface (3 payment methods) |
| `StripePayRepositoryImpl` | Stub implementation — methods not yet complete |
| `PayoutStatus` | `Idle · AwaitingOnboarding · OnboardingInProgress · OnboardingComplete · PaymentPending · PaymentSucceeded · Failed` |

## API Endpoints

| Base URL | Used by |
|----------|---------|
| `https://api.sarvam.ai` | `SarvamSttClientImpl`, `SarvamTtsClientImpl` |
| `https://eepqy4yku7.execute-api.us-east-1.amazonaws.com/prod/` | `SessionApiService`, `IngestionApiService` (Maps) |
| `https://p4qc9upgsf.execute-api.us-east-1.amazonaws.com/prod/` | `InnovationApiService` (Maps) |
| `BuildConfig.VIGIA_API_BASE_URL` | `OkHttpSseSearchClient`, `VigiaFcmReceiver` |

## Dependents
[[core-sensor]] (TtsManager uses SarvamTtsClient · SarvamSttClient) · [[feature-copilot]] (CopilotViewModel) · [[feature-maps]] (MapsModule Retrofit instances) · [[app]] (NetworkModule DI root)
