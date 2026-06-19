---
title: "RriScore"
type: model
tags: [model, sensor]
source: "core/model/src/main/kotlin/com/vigia/core/model/RriScore.kt"
related: ["[[ble-data-streamer]]", "[[vigia-search-context-model]]", "[[maps-viewmodel]]"]
updated: 2026-06-20
---

# RriScore

```kotlin
data class RriScore(val value: Float)  // 0.0–1.0 road roughness index confidence
```

Used in `MapsViewModel` to label confidence: `>0.8` = High, `>0.5` = Medium, else Low.

## Links

[[ble-data-streamer]] [[vigia-search-context-model]] [[maps-viewmodel]] [[core-model]]
