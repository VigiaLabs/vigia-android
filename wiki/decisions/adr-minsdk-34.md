---
title: "ADR: minSdk = 34"
type: decision
tags: [decision, android]
source: "build-logic/convention/src/main/kotlin/KotlinAndroid.kt"
related: ["[[cdm-presence-repository]]", "[[build-logic]]"]
updated: 2026-06-20
---

# ADR: Why minSdk = 34

## Decision

`minSdk` is set globally to 34 (Android 14) in `KotlinAndroid.kt:9`.

## Rationale

The `CompanionDeviceManager` Presence API (`DevicePresenceEvent`, `registerDevicePresenceListenerService`) was introduced in API 34. This API is used by `CdmPresenceRepository` to detect when the paired Pi Blackbox is within BLE proximity — a core feature that cannot be polyfilled on older Android versions.

Setting the floor globally removes all SDK-check boilerplate from `CdmPresenceRepositoryImpl` and guarantees `BleLinkManager` can use `BluetoothDevice.PHY_LE_2M_MASK` (API 26, already covered) and other BLE APIs without guards.

## Consequence

Approximately 12% of active Android devices (as of 2026) run below API 34 and cannot install VIGIA Mobile.

## Links

[[cdm-presence-repository]] [[build-logic]] [[ble-link-manager]]
