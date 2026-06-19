---
title: "feature:maps"
type: module
tags: [module, ui]
source: "feature/maps/build.gradle.kts"
related: ["[[maps-screen]]", "[[maps-viewmodel]]", "[[maps-repository]]", "[[osmdroid]]"]
updated: 2026-06-20
---

# feature:maps

Android feature module. OSMDroid map with 5 custom canvas layers, a bottom sheet with tabs, search bar, and sensor status strip.

## Key Files

| Path | Role |
|---|---|
| `MapsRoute.kt` | Navigation entry point composable |
| `MapsScreen.kt` | Full-screen map + overlaid UI chrome |
| `MapsViewModel.kt` | Layer toggles, route requests, trace playback, hazard selection |
| `MapsUiState.kt` | Data class: activeLayers, hazards, geohashCells, activeRoute, traceFrames, sensorStrip, selectedHazard |
| `components/VigiaMapView.kt` | `AndroidView` wrapping `MapView`; applies active layers |
| `components/MapsBottomSheet.kt` | Bottom sheet: Alerts tab, Route tab, Layers tab |
| `components/LayerToggleColumn.kt` | Toggle buttons for 5 `MapLayer` values |
| `components/MapsSearchBar.kt` | Place search UI; calls `MapsViewModel.onSearchQueryChange()` |
| `components/SensorStatusStrip.kt` | BLE link indicator, RRI score, velocity, accuracy label |
| `layers/HazardLayer.kt` | Canvas: draws hazard pins; `latLngToScreen` (fan-in=5) |
| `layers/GeohashLayer.kt` | Canvas: draws geohash grid cells |
| `layers/MaintenanceLayer.kt` | Canvas: draws maintenance POI markers |
| `layers/RouteLayer.kt` | Canvas: draws Bézier route polyline |
| `layers/TracePlaybackLayer.kt` | Canvas: animates trace frame history |
| `data/MapsApiService.kt` | Retrofit interface for maps backend endpoints |
| `data/MapsRepository.kt` | Interface: `hazardsFlow()`, `resolveGeohash()`, `generateRoute()`, `searchPlaces()`, `getMaintenanceQueue()`, `getEconomicMetrics()`, `getTraces()` |
| `data/MapsRepositoryImpl.kt` | Retrofit impl; `toDomain()` mapper (fan-in=7) |
| `di/MapsModule.kt` | `@Provides MapsApiService` via Retrofit; `@Binds MapsRepository` |

## Links

[[maps-screen]] [[maps-viewmodel]] [[maps-repository]] [[di-maps-module]]
[[hazard-alert-model]] [[bezier-route-model]] [[geohash-cell-model]] [[economic-zone-model]]
[[maintenance-poi-model]] [[search-place-model]] [[osmdroid]] [[core-model]] [[core-sensor]]
