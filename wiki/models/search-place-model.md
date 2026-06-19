---
title: "SearchPlace"
type: model
tags: [model, maps]
source: "core/model/src/main/kotlin/com/vigia/core/model/SearchPlace.kt"
related: ["[[maps-repository]]", "[[maps-viewmodel]]"]
updated: 2026-06-20
---

# SearchPlace

Place search result from `MapsRepository.searchPlaces()`. Contains display name, lat/lng, and optional address. Selecting a result in `MapsSearchBar` calls `MapsViewModel.onSearchResultSelected(lat, lng)` → route generation.

## Links

[[maps-repository]] [[maps-viewmodel]] [[core-model]]
