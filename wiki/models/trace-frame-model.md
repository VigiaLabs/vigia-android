---
title: "TraceFrame"
type: model
tags: [model, sensor]
source: "core/model/src/main/kotlin/com/vigia/core/model/TraceFrame.kt"
related: ["[[maps-viewmodel]]", "[[maps-repository]]"]
updated: 2026-06-20
---

# TraceFrame

Domain type for historical telemetry frames retrieved from the backend (`MapsRepository.getTraces()`). Used by `TracePlaybackLayer` to animate the vehicle's historical path on the map.

Fields: GPS coordinates, RRI score, timestamp. Playback advances at 200 ms per frame in `MapsViewModel.startTracePlayback()`.

## Links

[[maps-viewmodel]] [[maps-repository]] [[core-model]]
