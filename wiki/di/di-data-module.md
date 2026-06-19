---
title: "DataModule (Hilt)"
type: di
tags: [di, database]
source: "core/data/src/main/kotlin/com/vigia/core/data/di/DataModule.kt"
related: ["[[core-data]]", "[[chat-repository]]"]
updated: 2026-06-20
---

# DataModule (Hilt)

`@Module @InstallIn(SingletonComponent)`. Provides `VigiaDatabase` and binds `ChatRepository`.

## Provides

- `VigiaDatabase` — `Room.databaseBuilder(context, VigiaDatabase::class.java, "vigia_db")`
- `ChatMessageDao`, `ChatSessionDao` — extracted from database instance

## Binds

`ChatRepository` → `ChatRepositoryImpl`

## Links

[[core-data]] [[chat-repository]]
