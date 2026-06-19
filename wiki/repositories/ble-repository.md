---
title: "BleRepository"
type: repository
tags: [repository, ble]
source: "core/sensor/src/main/kotlin/com/vigia/core/sensor/ble/BleRepository.kt"
related: ["[[ble-link-manager]]", "[[core-sensor]]", "[[di-sensor-module]]"]
updated: 2026-06-20
---

# BleRepository

Interface + `BleRepositoryImpl`. Persists BLE device config (MAC address, Pi public key) so the app can auto-reconnect after process restart.

## Interface Methods

```kotlin
suspend fun saveConfig(config: BlackboxConfig)
suspend fun loadConfig(): BlackboxConfig?
```

## DataStore

`BlackboxConfig(deviceAddress: String, piPublicKeyBytes: ByteArray?)` stored via DataStore Preferences.

## Links

[[ble-link-manager]] [[core-sensor]] [[di-sensor-module]] [[pairing-repository]]
