---
title: "core:sensor"
type: module
tags: [module, ble]
source: "core/sensor/build.gradle.kts"
related: ["[[core-model]]", "[[di-sensor-module]]", "[[feature-copilot]]", "[[feature-pairing]]"]
updated: 2026-06-20
---

# core:sensor

Android library module. Owns the BLE GATT client, P-256 Keystore identity key, CDM companion presence, sensor context fusion, TTS playback, and voice amplitude capture.

## Key Files

| Path | Role |
|---|---|
| `ble/BleLinkManager.kt` | Full GATT connection pipeline: scan → connect → MTU → bond → ECDH → telemetry |
| `ble/BleDataStreamer.kt` | Interface: `telemetryFrames: SharedFlow<TelemetryFrame>` |
| `ble/BleDataStreamerImpl.kt` | Decodes raw GATT notification bytes into `TelemetryFrame` |
| `ble/BleRepository.kt` | BLE device config persistence interface |
| `ble/BleRepositoryImpl.kt` | DataStore-backed implementation |
| `ble/EcdhHandshake.kt` | HKDF-SHA256, HMAC-SHA256, ECDSA-P256 verify utilities |
| `ble/GattConstants.kt` | All GATT UUIDs (service, handshake, telemetry, control, attest, response, CCCD) |
| `keystore/KeystoreManager.kt` | EC P-256 key in Android Keystore; ECDH + ECDSA; StrongBox-preferred |
| `cdm/CdmPresenceRepository.kt` | Interface: `presenceState: StateFlow<DevicePresenceState>` |
| `cdm/CdmPresenceRepositoryImpl.kt` | Android CDM Presence API (minSdk=34) |
| `cdm/CdmPresenceService.kt` | `CompanionDeviceService` subclass |
| `context/ContextAggregator.kt` | `combine(locationFlow, bleDataStreamer.telemetryFrames)` → `VigiaSearchContext` |
| `pairing/PairingRepository.kt` | `PairedConfig` DataStore persistence interface |
| `pairing/PairingRepositoryImpl.kt` | DataStore implementation |
| `pairing/ClaimDeviceRepository.kt` | Backend `/claim-device` POST interface |
| `pairing/ClaimDeviceRepositoryImpl.kt` | Retrofit + Hilt implementation |
| `service/VigiaForegroundService.kt` | Foreground service keeping BLE and MQTT alive |
| `tts/TtsManager.kt` | Android TTS + Sarvam TTS; `speakSarvam()` uses backend proxy |
| `voice/VoiceAmplitudeMonitor.kt` | `MediaRecorder` amplitude poll + WAV export for STT |
| `BlackboxConfig.kt` | `BlackboxConfig(deviceAddress, piPublicKeyBytes?)` data class |

## BLE Pipeline States

`Idle → Scanning → Connecting(address) → Pairing → Handshaking → Bound`

MTU target: 517 bytes (handles 512-D frame via ATT fragmentation). PHY: LE 2M preferred.

## Hilt DI

See [[di-sensor-module]].

## Links

[[ble-link-manager]] [[ble-data-streamer]] [[keystore-manager]] [[ecdh-handshake]]
[[cdm-presence-repository]] [[pairing-repository]] [[claim-device-repository]]
[[tts-manager]] [[voice-amplitude-monitor]] [[di-sensor-module]]
[[core-model]] [[feature-copilot]] [[feature-pairing]] [[flow-ble-pairing]]
