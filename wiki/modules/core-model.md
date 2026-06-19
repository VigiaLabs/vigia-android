---
title: "core:model"
type: module
tags: [module, core]
source: "core/model/build.gradle.kts"
related: ["[[core-network]]", "[[core-sensor]]", "[[core-data]]", "[[core-wallet]]"]
updated: 2026-06-20
---

# core:model

Pure Kotlin library module — zero Android deps. Contains every shared domain data class used across all other modules. No ViewModels, no DI, no Android SDK imports.

## Key Classes

- `HazardAlert` (data class) — `id`, `severity` (LOW/MEDIUM/HIGH/CRITICAL), `messageText`, `timestampMs`, `locationSnapshot`. Source: `core/model/src/main/kotlin/com/vigia/core/model/HazardAlert.kt`
- `ChatMessage` — message body, role (USER/ASSISTANT), sources list, reasoning steps, latencyMs, `MessageStatus` (Complete/Partial)
- `ChatSession` — id, title, createdAt, updatedAt
- `BleLinkState` — sealed class: Idle, Scanning, Connecting, Pairing, Handshaking, Bound, Error
- `TraceFrame` — GPS + RRI + spatial vector timestamp
- `VigiaSearchContext` — queryText, location, velocityMs, rriScore, spatialLatentVector, timestampMs
- `RriScore` — Float wrapper for road roughness index
- `SpatialLatentVector` — dimensions (256 or 512), Float array data, originTimestampMs
- `LocationSnapshot` — lat, lon, accuracy, bearing, velocity, timestamp
- `BezierRoute`, `GeohashCell`, `EconomicZone`, `MaintenancePoi`, `SearchPlace` — map domain types
- `DevicePresenceState` — CDM presence state
- `MapLayer` — enum: HAZARD, GEOHASH, MAINTENANCE, ECONOMIC, TRACES

## Module Dependencies

No external dependencies — plain Kotlin stdlib only.

## Links

[[hazard-alert-model]] [[chat-message-model]] [[chat-session-model]] [[ble-link-state-model]]
[[vigia-search-context-model]] [[rri-score-model]] [[spatial-latent-vector-model]]
[[location-snapshot-model]] [[trace-frame-model]] [[bezier-route-model]] [[geohash-cell-model]]
[[economic-zone-model]] [[maintenance-poi-model]] [[search-place-model]]
[[core-network]] [[core-sensor]] [[core-data]] [[core-wallet]]
