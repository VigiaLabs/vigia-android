---
title: "LocationSnapshot"
type: model
tags: [model, sensor]
source: "core/model/src/main/kotlin/com/vigia/core/model/LocationSnapshot.kt"
related: ["[[vigia-search-context-model]]", "[[hazard-alert-model]]", "[[maps-viewmodel]]"]
updated: 2026-06-20
---

# LocationSnapshot

```kotlin
data class LocationSnapshot(
    val latitudeDeg: Double,
    val longitudeDeg: Double,
    val accuracyMeters: Float,
    val bearingDeg: Float,
    val velocityMs: Float,
    val timestampMs: Long,
)
```

Produced from `android.location.Location` by `ContextAggregator.toSnapshot()`. Used in `VigiaSearchContext`, `HazardAlert`, and `MapsUiState`.

## Links

[[vigia-search-context-model]] [[hazard-alert-model]] [[maps-viewmodel]] [[core-model]]
