---
title: "OSMDroid"
type: external
tags: [external, maps]
source: "feature/maps/src/main/kotlin/com/vigia/feature/maps/components/VigiaMapView.kt"
related: ["[[feature-maps]]", "[[maps-screen]]", "[[adr-osmdroid]]"]
updated: 2026-06-20
---

# OSMDroid

`osmdroid-android 6.1.20`. OpenStreetMap tile engine for Android. No API key required.

Wrapped in `VigiaMapView` as an `AndroidView`. Custom layers extend `Overlay` and paint directly to the `Canvas`: `HazardLayer`, `GeohashLayer`, `MaintenanceLayer`, `RouteLayer`, `TracePlaybackLayer`.

OSMDroid tile cache is configured at app startup in `VigiaApplication`.

## Links

[[feature-maps]] [[maps-screen]] [[maps-viewmodel]] [[adr-osmdroid]]
