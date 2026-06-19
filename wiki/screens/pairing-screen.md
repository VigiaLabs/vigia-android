---
title: "PairingScreen"
type: screen
tags: [screen, ble]
source: "feature/pairing/src/main/kotlin/com/vigia/feature/pairing/PairingScreen.kt"
related: ["[[pairing-viewmodel]]", "[[ble-link-manager]]", "[[claim-device-repository]]"]
updated: 2026-06-20
---

# PairingScreen

One-time QR scan and CompanionDeviceManager association screen. Only shown when `isPaired == false`. On success, `onPairingComplete` callback → `AppRootViewModel.onPairingComplete()` → DataStore write.

## States

- `Scanning` / `AwaitingCdmLaunch` — live CameraX preview + `QrAnalyzer`
- `PairingRejected` — "Pairing cancelled" with retry action
- `DeviceAlreadyClaimed` — error if device or wallet already linked to different account
- `Error` — generic error with retry
- `Success` — calls `onPairingComplete`

## CameraX Pipeline

`ProcessCameraProvider` binds `Preview` + `ImageAnalysis` (STRATEGY_KEEP_ONLY_LATEST) to `QrAnalyzer`. `QrAnalyzer` uses ML Kit `BarcodeScanning` to detect `vigia://` scheme QR codes.

## CDM Association

`CompanionDeviceManager` association request launched via `ActivityResultContracts.StartIntentSenderForResult`. Association ID returned to `PairingViewModel.onCdmResultReceived()`.

## Links

[[pairing-viewmodel]] [[claim-device-repository]] [[pairing-repository]] [[ble-link-manager]]
[[app-root-viewmodel]] [[feature-pairing]] [[flow-ble-pairing]]
