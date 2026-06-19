---
title: "MapsViewModel"
type: viewmodel
tags: [viewmodel, maps]
source: "feature/maps/src/main/kotlin/com/vigia/feature/maps/MapsViewModel.kt"
related: ["[[maps-screen]]", "[[maps-repository]]", "[[ble-data-streamer]]"]
updated: 2026-06-20
---

# MapsViewModel

`@HiltViewModel`. Manages `MapsUiState`: layer visibility, hazard list, geohash cells, route, maintenance POIs, economic zones, trace frames, and playback position.

## Injected Dependencies

`MapsRepository`, `ContextAggregator`.

## Key Methods

- `toggleLayer(MapLayer)` — enables/disables a layer and triggers lazy data load
- `requestRoute(LatLng)` — calls `repository.generateRoute()`; sets `activeRoute` + switches to ROUTE tab
- `selectHazard(HazardAlert)` / `dismissHazard()` — detail selection
- `onSearchQueryChange(query)` / `onSearchResultSelected()` / `dismissSearch()` — place search
- `startTracePlayback()` / `stopTracePlayback()` / `scrubTrace(index)` — trace animation
- `setSheetTab(SheetTab)` — Alerts / Route / Layers tab
- `clearError()` — error snackbar dismissal

## Init

`observeLocation()` — collects `ContextAggregator.searchContext`; updates `mapCenter`, `sensorStrip`, and triggers `resolveGeohash()`.
`observeHazards()` — collects `repository.hazardsFlow()`.

## Links

[[maps-screen]] [[maps-repository]] [[ble-data-streamer]] [[feature-maps]]
[[hazard-alert-model]] [[bezier-route-model]] [[geohash-cell-model]]
