---
title: "ClaimDeviceRepository"
type: repository
tags: [repository, network]
source: "core/sensor/src/main/kotlin/com/vigia/core/sensor/pairing/ClaimDeviceRepository.kt"
related: ["[[pairing-viewmodel]]", "[[aws-backend]]", "[[core-sensor]]"]
updated: 2026-06-20
---

# ClaimDeviceRepository

Interface + `ClaimDeviceRepositoryImpl`. POSTs the scanned QR device MAC to the VIGIA backend's `/claim-device` endpoint to reserve the device for the current user's wallet.

## Interface Methods

```kotlin
suspend fun claimDevice(deviceId: String, walletAddress: String): ClaimResult
```

## ClaimResult

Sealed class: `Success`, `AlreadyClaimed(reason: String, deviceId: String)`, `Error(message: String)`.

## Links

[[pairing-viewmodel]] [[aws-backend]] [[core-sensor]] [[di-sensor-module]]
[[flow-ble-pairing]]
