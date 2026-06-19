---
title: "VigiaSearchClient"
type: client
tags: [client, search]
source: "core/network/src/main/kotlin/com/vigia/core/network/search/VigiaSearchClient.kt"
related: ["[[okhttp-sse-search-client]]", "[[copilot-viewmodel]]", "[[vigia-search-context-model]]", "[[search-event-model]]"]
updated: 2026-06-20
---

# VigiaSearchClient

Transport-agnostic interface for the VIGIASearch Fargate backend SSE endpoint.

## Interface

```kotlin
fun search(context: VigiaSearchContext): Flow<SearchEvent>
```

The `Flow` emits `SearchEvent.Step`, `SearchEvent.TextDelta`, `SearchEvent.Metadata` (with sources + spatial markers + latency), then `SearchEvent.Done`. Cancelling the collecting coroutine cancels the HTTP call.

## Binding

`NetworkModule` binds `OkHttpSseSearchClient` as the implementation. Swap at one point to change backend.

## Links

[[okhttp-sse-search-client]] [[copilot-viewmodel]] [[vigia-search-context-model]]
[[search-event-model]] [[di-network-module]] [[flow-voice-copilot]]
