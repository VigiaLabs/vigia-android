---
title: "ADR: OSMDroid over Google Maps"
type: decision
tags: [decision, maps]
source: "feature/maps/build.gradle.kts"
related: ["[[feature-maps]]", "[[osmdroid]]"]
updated: 2026-06-20
---

# ADR: OSMDroid over Google Maps

## Decision

`feature:maps` uses OSMDroid 6.1.20 with OpenStreetMap tiles instead of the Google Maps Android SDK.

## Rationale

- OSMDroid requires no API key and has no usage-based billing — suitable for a product where map loads are proportional to telemetry uploads which may be high-frequency.
- OpenStreetMap data is openly licensed and can be cached on-device for offline use.
- Custom canvas layers (`HazardLayer`, `GeohashLayer`, etc.) are easier to implement against OSMDroid's `MapEventsOverlay` API than against Google Maps' overlay system.

## Consequence

No Google Maps billing. Tile quality may be lower in rural areas compared to Google Maps. Satellite imagery not available without a third-party tile provider.

## Links

[[feature-maps]] [[osmdroid]] [[maps-viewmodel]]
