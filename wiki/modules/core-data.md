---
title: "core:data"
type: module
tags: [module, database]
source: "core/data/build.gradle.kts"
related: ["[[core-model]]", "[[di-data-module]]", "[[copilot-viewmodel]]"]
updated: 2026-06-20
---

# core:data

Android library module. Room database, DAOs, and the `ChatRepository` interface + implementation.

## Key Files

| Path | Role |
|---|---|
| `db/VigiaDatabase.kt` | `@Database(entities=[ChatMessageEntity, ChatSessionEntity])` |
| `db/ChatMessageDao.kt` | CRUD for `ChatMessageEntity`; `Flow<List>` for reactive UI |
| `db/ChatSessionDao.kt` | CRUD for `ChatSessionEntity`; `Flow<List>` sorted by `updatedAt` |
| `db/ChatMessageEntity.kt` | Room entity mirroring `ChatMessage` domain type |
| `db/ChatSessionEntity.kt` | Room entity mirroring `ChatSession` domain type |
| `ChatRepository.kt` | Interface: `allSessions()`, `messagesForSession()`, `insertMessage()`, `createSession()`, `deleteSession()`, `bumpSession()` |
| `ChatRepositoryImpl.kt` | `@Singleton` — wraps both DAOs; maps entity ↔ domain |
| `di/DataModule.kt` | `@Provides VigiaDatabase`; `@Binds ChatRepository` |

## Room Version

Room 2.7.1 (KSP-processed).

## Links

[[di-data-module]] [[chat-repository]] [[chat-message-model]] [[chat-session-model]]
[[copilot-viewmodel]] [[core-model]]
