---
title: "OkHttpSseSearchClient"
type: client
tags: [client, search]
source: "core/network/src/main/kotlin/com/vigia/core/network/search/OkHttpSseSearchClient.kt"
related: ["[[vigia-search-client]]", "[[search-event-model]]", "[[di-network-module]]"]
updated: 2026-06-20
---

# OkHttpSseSearchClient

`@Singleton`. Implements `VigiaSearchClient` using a raw OkHttp `EventSource` over `POST /v1/search` with `Accept: text/event-stream`.

## Flow Contract

Emits `SearchEvent` items as SSE lines arrive, then completes on `SearchEvent.Done`. If the collecting coroutine is cancelled, `eventSource.cancel()` is called immediately (no dangling HTTP connection).

## VigiaSearchContext Serialization

`VigiaSearchContext` is serialized to JSON (Gson) and sent as the POST body. Includes `queryText`, GPS location, `velocityMs`, `rriScore`, and the `spatialLatentVector` float array.

## Links

[[vigia-search-client]] [[search-event-model]] [[vigia-search-context-model]]
[[di-network-module]] [[copilot-viewmodel]]
