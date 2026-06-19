---
title: "SearchEvent"
type: model
tags: [model, search]
source: "core/network/src/main/kotlin/com/vigia/core/network/search/SearchEvent.kt"
related: ["[[vigia-search-client]]", "[[copilot-viewmodel]]"]
updated: 2026-06-20
---

# SearchEvent

Sealed class for SSE events from the VIGIASearch Fargate backend:

```kotlin
sealed class SearchEvent {
    data class Step(val message: String) : SearchEvent()         // reasoning step narrated in voice mode
    data class TextDelta(val delta: String) : SearchEvent()      // incremental answer token
    data class Metadata(
        val sources: List<Source>,
        val spatialMarkers: List<SpatialMarker>,
        val totalLatencyMs: Long,
    ) : SearchEvent()
    object Done : SearchEvent()
}
```

## Links

[[vigia-search-client]] [[copilot-viewmodel]] [[okhttp-sse-search-client]] [[core-network]]
