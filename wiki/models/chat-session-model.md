---
title: "ChatSession"
type: model
tags: [model, chat]
source: "core/model/src/main/kotlin/com/vigia/core/model/ChatSession.kt"
related: ["[[chat-repository]]", "[[copilot-viewmodel]]"]
updated: 2026-06-20
---

# ChatSession

```kotlin
data class ChatSession(
    val id: String,
    val title: String,       // first 60 chars of first USER message
    val createdAt: Long,
    val updatedAt: Long,
)
```

`bumpSession(id)` updates `updatedAt` to `System.currentTimeMillis()` so sessions sort newest-first.

## Links

[[chat-repository]] [[copilot-viewmodel]] [[chat-message-model]] [[core-data]]
