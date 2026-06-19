---
title: "ADR: Room for offline-first chat history"
type: decision
tags: [decision, database]
source: "core/data/build.gradle.kts"
related: ["[[core-data]]", "[[chat-repository]]", "[[copilot-viewmodel]]"]
updated: 2026-06-20
---

# ADR: Room for Offline-First Chat History

## Decision

All copilot sessions and messages are persisted in a local Room database (`VigiaDatabase`).

## Rationale

- Users should be able to review past conversations without network access.
- SSE streams can be interrupted mid-response; `MessageStatus.Partial` lets the app preserve and display whatever tokens arrived before the failure.
- Room's `Flow`-returning DAOs integrate cleanly with `flatMapLatest` in `CopilotViewModel` for reactive session switching.
- No server-side chat storage is needed — the backend is stateless with respect to chat history.

## Consequence

Chat history is device-local only. Moving to a new device loses all sessions unless a cloud sync layer is added later.

## Links

[[core-data]] [[chat-repository]] [[copilot-viewmodel]] [[chat-message-model]] [[chat-session-model]]
