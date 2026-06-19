---
title: "PairingViewModel"
type: viewmodel
tags: [viewmodel, ble]
source: "feature/pairing/src/main/kotlin/com/vigia/feature/pairing/PairingViewModel.kt"
related: ["[[pairing-screen]]", "[[claim-device-repository]]", "[[pairing-repository]]", "[[ble-link-manager]]"]
updated: 2026-06-20
---

# PairingViewModel

`@HiltViewModel`. Drives the QR scan → CDM association → backend claim pairing state machine.

## State Machine

`PairingState`: `Scanning → AwaitingCdmLaunch → Success`; error branches: `PairingRejected`, `Error`, `DeviceAlreadyClaimed`.

## Key Methods

- `onQrDetected(rawValue)` — parses `vigia://<mac>/<pub_key_hex>`, calls `ClaimDeviceRepository`, creates CDM association request
- `onCdmResultReceived(associationId)` — CDM association success; calls `PairingRepository.savePairedConfig()`; transitions to `Success`
- `onCdmResultCancelled()` — transitions to `PairingRejected`
- `retryScanning()` — resets to `Scanning`

## Links

[[pairing-screen]] [[claim-device-repository]] [[pairing-repository]] [[ble-link-manager]]
[[app-root-viewmodel]] [[flow-ble-pairing]]
