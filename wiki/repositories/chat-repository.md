---
title: "ChatRepository"
type: repository
tags: [repository, database]
source: "core/data/src/main/kotlin/com/vigia/core/data/ChatRepository.kt"
related: ["[[core-data]]", "[[copilot-viewmodel]]", "[[di-data-module]]"]
updated: 2026-06-20
---

# ChatRepository

Interface + `ChatRepositoryImpl` (`@Singleton`). Reactive Room-backed chat history.

## Interface Methods

```kotlin
fun allSessions(): Flow<List<ChatSession>>
fun messagesForSession(sessionId: String): Flow<List<ChatMessage>>
suspend fun createSession(session: ChatSession)
suspend fun insertMessage(message: ChatMessage)
suspend fun deleteSession(id: String)
suspend fun bumpSession(id: String)  // updates updatedAt to now, re-sorts session list
```

## Usage in CopilotViewModel

- `allSessions()` → stateIn → `sessions` StateFlow
- `messagesForSession(activeSessionId)` → flatMapLatest → `sessionMessages` StateFlow
- `insertMessage()` called for USER message before search starts and ASSISTANT message on `SearchEvent.Done`
- On network error mid-stream: `insertMessage()` with `MessageStatus.Partial` to preserve partial tokens

## Links

[[core-data]] [[copilot-viewmodel]] [[di-data-module]] [[chat-message-model]] [[chat-session-model]]
