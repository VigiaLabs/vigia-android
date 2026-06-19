---
title: "MapsRepository"
type: repository
tags: [repository, maps]
source: "feature/maps/src/main/kotlin/com/vigia/feature/maps/data/MapsRepository.kt"
related: ["[[maps-viewmodel]]", "[[di-maps-module]]", "[[aws-backend]]"]
updated: 2026-06-20
---

# MapsRepository

Interface + `MapsRepositoryImpl`. Retrofit-backed access to all map-related backend endpoints.

## Interface Methods

```kotlin
fun hazardsFlow(): Flow<List<HazardAlert>>
suspend fun resolveGeohash(lat: Double, lng: Double): List<GeohashCell>
suspend fun generateRoute(originLat, originLng, destLat, destLng): BezierRoute
suspend fun searchPlaces(query: String, lat: Double?, lng: Double?): List<SearchPlace>
suspend fun getMaintenanceQueue(): List<MaintenancePoi>
suspend fun getEconomicMetrics(geohash: String): EconomicZone
suspend fun getTraces(): List<TraceFrame>
```

## toDomain()

`MapsRepositoryImpl.toDomain()` maps API DTOs to domain types. Fan-in=7 (called from 7 methods).

## Links

[[maps-viewmodel]] [[di-maps-module]] [[aws-backend]]
[[hazard-alert-model]] [[bezier-route-model]] [[geohash-cell-model]]
[[economic-zone-model]] [[maintenance-poi-model]] [[search-place-model]] [[trace-frame-model]]
