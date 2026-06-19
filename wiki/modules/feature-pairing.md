---
title: "feature:pairing"
type: module
tags: [module, ui]
source: "feature/pairing/build.gradle.kts"
related: ["[[pairing-screen]]", "[[pairing-viewmodel]]", "[[ble-link-manager]]", "[[claim-device-repository]]"]
updated: 2026-06-20
---

# feature:pairing

Android feature module. One-time QR scan, CompanionDeviceManager association, and backend device-claim. Only shown once after sign-in; result persisted in DataStore.

## Key Files

| Path | Role |
|---|---|
| `PairingScreen.kt` | CameraX live preview + ML Kit QR analysis; CDM chooser intent launcher |
| `PairingViewModel.kt` | Drives pairing state machine: Scanning → AwaitingCdmLaunch → Success / error states |
| `PairingState.kt` | Sealed class: Scanning, AwaitingCdmLaunch, Success, PairingRejected, Error, DeviceAlreadyClaimed |
| `QrAnalyzer.kt` | `ImageAnalysis.Analyzer` using ML Kit `BarcodeScanning`; parses `vigia://` scheme QR codes |

## Pairing Flow

1. Camera permission requested on launch.
2. `QrAnalyzer` decodes `vigia://<mac>/<pi_pub_key_hex>` QR.
3. ViewModel calls `ClaimDeviceRepository` → backend validates and reserves the device.
4. On success: CDM `associationRequest` launched via `IntentSenderRequest`.
5. CDM result → `PairingState.Success` → `AppRootViewModel.onPairingComplete()` writes DataStore.

## Links

[[pairing-screen]] [[pairing-viewmodel]] [[claim-device-repository]] [[pairing-repository]]
[[ble-link-manager]] [[keystore-manager]] [[app-root-viewmodel]] [[flow-ble-pairing]]
[[core-sensor]] [[core-model]]
