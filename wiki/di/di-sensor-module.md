---
title: "SensorModule (Hilt)"
type: di
tags: [di, ble]
source: "core/sensor/src/main/kotlin/com/vigia/core/sensor/di/SensorModule.kt"
related: ["[[core-sensor]]", "[[ble-link-manager]]", "[[keystore-manager]]", "[[tts-manager]]"]
updated: 2026-06-20
---

# SensorModule (Hilt)

`@Module @InstallIn(SingletonComponent)`. Provides BLE, CDM, pairing, Keystore, TTS, and voice components.

## Bindings

| Interface | Implementation |
|---|---|
| `BleDataStreamer` | `BleDataStreamerImpl` |
| `BleRepository` | `BleRepositoryImpl` |
| `CdmPresenceRepository` | `CdmPresenceRepositoryImpl` |
| `PairingRepository` | `PairingRepositoryImpl` |
| `ClaimDeviceRepository` | `ClaimDeviceRepositoryImpl` |

## Singletons Provided

`BleLinkManager`, `KeystoreManager`, `ContextAggregator`, `TtsManager`, `VoiceAmplitudeMonitor`.

## Links

[[core-sensor]] [[ble-link-manager]] [[ble-data-streamer]] [[ble-repository]]
[[cdm-presence-repository]] [[pairing-repository]] [[claim-device-repository]]
[[keystore-manager]] [[tts-manager]] [[voice-amplitude-monitor]]
