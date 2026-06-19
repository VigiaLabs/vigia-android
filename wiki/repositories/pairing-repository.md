---
title: "PairingRepository"
type: repository
tags: [repository, ble]
source: "core/sensor/src/main/kotlin/com/vigia/core/sensor/pairing/PairingRepository.kt"
related: ["[[pairing-viewmodel]]", "[[app-root-viewmodel]]", "[[core-sensor]]"]
updated: 2026-06-20
---

# PairingRepository

Interface + `PairingRepositoryImpl`. DataStore-backed persistence for `PairedConfig` (MAC, Pi public key, CDM association ID).

## Interface Methods

```kotlin
suspend fun savePairedConfig(config: PairedConfig)
fun isPairedFlow(): Flow<Boolean>
suspend fun clearPairedConfig()
```

## PairedConfig

Data class: `deviceAddress: String`, `piPublicKeyHex: String`, `cdmAssociationId: Int`.

## Links

[[pairing-viewmodel]] [[app-root-viewmodel]] [[core-sensor]] [[di-sensor-module]]
