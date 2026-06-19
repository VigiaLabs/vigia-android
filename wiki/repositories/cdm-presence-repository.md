---
title: "CdmPresenceRepository"
type: repository
tags: [repository, ble]
source: "core/sensor/src/main/kotlin/com/vigia/core/sensor/cdm/CdmPresenceRepository.kt"
related: ["[[core-sensor]]", "[[copilot-viewmodel]]", "[[adr-minsdk-34]]"]
updated: 2026-06-20
---

# CdmPresenceRepository

Interface + `CdmPresenceRepositoryImpl`. Uses the Android CompanionDeviceManager Presence API (API 34+) to detect when the paired Pi Blackbox is nearby (BLE proximity).

## Interface Methods

```kotlin
val presenceState: StateFlow<DevicePresenceState>
suspend fun registerPresenceObserver(associationId: Int)
suspend fun unregisterPresenceObserver(associationId: Int)
```

## Why minSdk=34

The CDM Presence API (`DevicePresenceEvent`, `registerDevicePresenceListenerService`) was introduced in Android 14 (API 34). This is the primary reason `minSdk` is set to 34. See [[adr-minsdk-34]].

## Links

[[core-sensor]] [[copilot-viewmodel]] [[adr-minsdk-34]] [[ble-link-state-model]] [[di-sensor-module]]
