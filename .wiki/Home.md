# Vigia Android — Repository Map

> Generated from the `codebase-memory-mcp` knowledge graph (3 357 nodes · 6 320 edges).

## Module Dependency Graph

```
┌──────────────────────────────────────────────────────────────┐
│                           :app                               │
│  NavHost · AppModule · BuildConfig · product flavours        │
└───┬──────────────┬─────────────┬──────────────┬─────────────┘
    │              │             │              │
    ▼              ▼             ▼              ▼
:feature:      :feature:    :core:auth    :core:sensor
 copilot         maps            │              │
    │   ╲          │             │              │
    │    ╲─────────┤             │              │
    ▼              ▼             │              │
:core:data    :core:network ◄────┘◄─────────────┘
    │              │
    └──────┬───────┘
           ▼
       :core:model
```

## Module Index

| Module | Layer | Primary responsibility |
|--------|-------|------------------------|
| [[app]] | Entry point | Navigation host, Hilt root, `AppModule` secrets bridge |
| [[feature-copilot]] | Feature | AI voice copilot, chat UI, wallet, aurora overlay |
| [[feature-maps]] | Feature | OSM map, hazard layers, geohash grid, route tracing |
| [[core-auth]] | Core — leaf | AWS Amplify Cognito auth; `DemoAuthRepository` for dev flavour |
| [[core-data]] | Core | Room database, chat session/message persistence |
| [[core-network]] | Core | OkHttp/Retrofit, Sarvam STT+TTS, MQTT alerts, FCM, Stripe |
| [[core-sensor]] | Core | BLE Blackbox link, GPS context aggregation, TTS playback, voice recording |
| [[core-model]] | Core — leaf | Pure Kotlin domain models; zero Android dependencies |
| [[build-logic]] | Infrastructure | Convention Gradle plugins shared across all modules |

## Key Data Flows

### Voice copilot turn
```
VoiceAmplitudeMonitor (mic RMS)
  → CopilotViewModel.endVoiceRecording()
  → SarvamSttClientImpl (POST /speech-to-text, saarika:v2.5)
  → OkHttpSseSearchClient (SSE /v1/search, LangGraph steps → text deltas)
  → TtsManager.speakSarvam() queue (steps spoken in order, then final answer)
  → SarvamTtsClientImpl (POST /text-to-speech, bulbul:v1)
  → AudioTrack (PCM RMS → ttsAmplitude → aurora/orb animation)
```

### Hazard alert pipeline
```
MqttAlertRepositoryImpl (AWS IoT Core MQTT)
  → CopilotViewModel.observeAlerts()
  → TtsManager.speak()  ← Android TTS (works offline / Doze)
VigiaFcmReceiver (FCM high-priority data message)
  → MqttAlertRepository.reconnect()   ← Doze wakeup path
```

### Map data session
```
ContextAggregator (GPS + BLE RRI fused)
  → MapsViewModel
  → SessionApiService  (AWS API Gateway /prod/hazards, /prod/geohash/resolve)
  → InnovationApiService  (p4qc9upgsf… /prod/*)
```

### Auth → authenticated API calls
```
AmplifyAuthRepository.getIdToken()   ← Cognito ID token (cached)
  → ApiTokenProvider (runBlocking bridge in AppModule)
  → VigiaAuthInterceptor
  → @Named("VigiaOkHttpClient")   ← injected into Maps, Search, FCM
```

---
*Pages:* [[app]] · [[feature-copilot]] · [[feature-maps]] · [[core-auth]] · [[core-data]] · [[core-network]] · [[core-sensor]] · [[core-model]] · [[build-logic]]
