# core:sensor

**Layer:** Core — internal (fan-in 3, fan-out 20)  
**Package:** `com.vigia.core.sensor`  
**Path:** `core/sensor/`  
**Depends on:** [[core-model]] · [[core-network]]

Hardware abstraction and signal processing layer. Manages the BLE Blackbox device link, GPS/RRI context fusion, TTS playback queue, and mic recording for voice sessions.

## BLE — Blackbox Link

| Type | Description |
|------|-------------|
| `BleRepository` | Interface: connect/disconnect/observe the VIGIA Blackbox device |
| `BleRepositoryImpl` | Android BLE GATT state machine; scans for `BlackboxConfig.macAddress` |
| `BleLinkManager` | Internal coroutine orchestrator; emits `GattEvent` sealed type |
| `BleDataStreamer` | Interface: continuous `TelemetryFrame` stream from device notifications |
| `BleDataStreamerImpl` | Decodes GATT characteristic bytes → `TelemetryFrame` |
| `GattConstants` | GATT service/characteristic UUIDs and `Protocol` framing constants |
| `BlackboxConfig` | `macAddress: String · associationId: Int` — injected from [[app]] `AppModule` |

### BLE State (`BleLinkState` in [[core-model]])
`Idle → Scanning → Connecting → Pairing → Handshaking → Bound → Error`

## Context Aggregation

| Type | Description |
|------|-------------|
| `ContextAggregator` | Fuses GPS `LocationSnapshot` + BLE `RriScore` into `VigiaSearchContext`; exposes `searchContext: StateFlow` |

Only GPS and BLE are used — Maps API errors do not affect context.

## TTS Playback

| Type | Description |
|------|-------------|
| `TtsManager` | Two-tier speech: **Sarvam AI** (primary, natural voice) + **Android TTS** (offline fallback / CRITICAL alerts) |

### TtsManager queue design
- `Channel<SpeechItem>(UNLIMITED)` — single drain coroutine plays items sequentially in arrival order
- `speakSarvam(text, onDone)` → `trySend` to channel (non-blocking)
- During playback: samples 100 ms RMS window from AudioTrack PCM via `playbackHeadPosition` → emits `ttsAmplitude: StateFlow<Float>` for aurora/orb animation
- `stop()` kills AudioTrack + drains queue + fires all `onDone` callbacks
- Android TTS used for hazard alerts (`QUEUE_FLUSH` for CRITICAL) — works without network

## Voice Recording

| Type | Description |
|------|-------------|
| `VoiceAmplitudeMonitor` | `AudioRecord` at 16 kHz 16-bit mono; emits `amplitude: StateFlow<Float>` (normalised RMS); `stopAndGetWav()` returns WAV bytes; `stopSilently()` discards buffer |

## Security

| Type | Description |
|------|-------------|
| `KeystoreManager` | Android Keystore AES-256 key management for at-rest encryption |

## Foreground Service

| Type | Description |
|------|-------------|
| `VigiaForegroundService` | Foreground service keeping BLE + GPS alive; emits `ServiceState` (`Idle · Connecting · AwaitingPresence · Connected · Error`) |
| `CdmPresenceService` | CDM (Companion Device Manager) presence observer |
| `CdmPresenceRepository` | Interface: watches for Blackbox device presence via CDM API |
| `CdmPresenceRepositoryImpl` | Impl; triggers `BleRepository.connect()` on device appearance |

## Dependents
[[feature-copilot]] (CopilotViewModel injects TtsManager, VoiceAmplitudeMonitor, ContextAggregator, CdmPresenceRepository) · [[feature-maps]] (MapsViewModel injects ContextAggregator, BleRepository) · [[app]] (starts VigiaForegroundService)
