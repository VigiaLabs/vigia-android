---
title: "AppRootViewModel"
type: viewmodel
tags: [viewmodel, auth]
source: "feature/copilot/src/main/kotlin/com/vigia/feature/copilot/AppRootViewModel.kt"
related: ["[[app-root-screen]]", "[[pairing-screen]]", "[[pairing-repository]]"]
updated: 2026-06-20
---

# AppRootViewModel

`@HiltViewModel`. Reads the `isPaired` boolean from DataStore and writes it on successful pairing.

## Exposed State

- `isPaired: StateFlow<Boolean?>` — `null` while DataStore is loading; `false` → show pairing screen; `true` → show copilot

## Key Method

- `onPairingComplete()` — writes `isPaired = true` to DataStore; triggers `AppRoot` recomposition to `CopilotRoute`

## Links

[[app-root-screen]] [[pairing-repository]] [[pairing-screen]] [[feature-copilot]]
