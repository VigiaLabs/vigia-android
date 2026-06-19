---
title: "VigiaSearchContext"
type: model
tags: [model, search]
source: "core/model/src/main/kotlin/com/vigia/core/model/VigiaSearchContext.kt"
related: ["[[vigia-search-client]]", "[[copilot-viewmodel]]", "[[ble-data-streamer]]"]
updated: 2026-06-20
---

# VigiaSearchContext

The enriched payload sent to VIGIASearch. Built by `ContextAggregator` from GPS + BLE telemetry; `CopilotViewModel.sendMessage()` copies it with `copy(queryText = userInput)`.

```kotlin
data class VigiaSearchContext(
    val queryText: String,
    val timestampMs: Long,
    val location: LocationSnapshot,
    val velocityMs: Float,
    val rriScore: RriScore,
    val spatialLatentVector: SpatialLatentVector,
)
```

## Links

[[vigia-search-client]] [[copilot-viewmodel]] [[ble-data-streamer]] [[location-snapshot-model]]
[[rri-score-model]] [[spatial-latent-vector-model]] [[core-model]]
