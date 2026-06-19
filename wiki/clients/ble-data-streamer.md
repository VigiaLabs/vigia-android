---
title: "BleDataStreamer"
type: client
tags: [client, ble]
source: "core/sensor/src/main/kotlin/com/vigia/core/sensor/ble/BleDataStreamer.kt"
related: ["[[ble-link-manager]]", "[[core-sensor]]", "[[vigia-search-context-model]]"]
updated: 2026-06-20
---

# BleDataStreamer

Interface + `BleDataStreamerImpl`. Decodes raw GATT notification bytes from `BleLinkManager.incomingFrames` into structured `TelemetryFrame` objects.

## Interface

```kotlin
val telemetryFrames: SharedFlow<TelemetryFrame>

data class TelemetryFrame(
    val rriScore: RriScore,
    val spatialLatentVector: SpatialLatentVector,
)
```

## Usage

`ContextAggregator.searchContext` combines `telemetryFrames` with GPS location to build `VigiaSearchContext` for the copilot query.

## Links

[[ble-link-manager]] [[core-sensor]] [[vigia-search-context-model]] [[rri-score-model]]
[[spatial-latent-vector-model]] [[di-sensor-module]]
