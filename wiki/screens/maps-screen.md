---
title: "MapsScreen"
type: screen
tags: [screen, maps]
source: "feature/maps/src/main/kotlin/com/vigia/feature/maps/MapsScreen.kt"
related: ["[[maps-viewmodel]]", "[[maps-repository]]", "[[osmdroid]]"]
updated: 2026-06-20
---

# MapsScreen

Full-screen OSMDroid map composable with 5 overlay layers, search bar, sensor status strip, and a bottom sheet with Alerts / Route / Layers tabs.

## Components

- `VigiaMapView` — `AndroidView` wrapping OSMDroid `MapView`; applies active `MapLayer` set from `MapsUiState.activeLayers`
- `MapsSearchBar` — floating search bar; shows `SearchPlace` results; on selection calls `MapsViewModel.onSearchResultSelected()` → route request
- `SensorStatusStrip` — BLE link indicator, RRI score badge, velocity, accuracy confidence label
- `MapsBottomSheet` — modal bottom sheet with 3 tabs; `EmptyTabPlaceholder` shown when no data (fan-in=4)
- `LayerToggleColumn` — toggle buttons for HAZARD, GEOHASH, MAINTENANCE, ECONOMIC, TRACES layers
- 5 canvas layers: `HazardLayer`, `GeohashLayer`, `MaintenanceLayer`, `RouteLayer`, `TracePlaybackLayer`

## Trace Playback

`MapsViewModel.startTracePlayback()` iterates `traceFrames` with 200 ms delay per frame, updating `tracePlaybackIndex`. `TracePlaybackLayer` reads the current frame index.

## Links

[[maps-viewmodel]] [[maps-repository]] [[feature-maps]] [[osmdroid]]
[[hazard-alert-model]] [[bezier-route-model]] [[geohash-cell-model]]
