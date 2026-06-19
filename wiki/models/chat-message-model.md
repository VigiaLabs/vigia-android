---
title: "ChatMessage"
type: model
tags: [model, chat]
source: "core/model/src/main/kotlin/com/vigia/core/model/ChatMessage.kt"
related: ["[[chat-repository]]", "[[copilot-viewmodel]]", "[[core-data]]"]
updated: 2026-06-20
---

# ChatMessage

```kotlin
data class ChatMessage(
    val id: String,
    val sessionId: String,
    val role: MessageRole,           // USER, ASSISTANT
    val body: String,
    val sources: List<MessageSource> = emptyList(),
    val reasoningSteps: List<String> = emptyList(),
    val latencyMs: Long = 0L,
    val status: MessageStatus,       // Complete, Partial
    val createdAt: Long,
)
```

`MessageStatus.Partial` is written on network error mid-stream to preserve partial tokens. `sources` and `reasoningSteps` are only non-empty for ASSISTANT messages.

## Links

[[chat-repository]] [[copilot-viewmodel]] [[core-data]] [[chat-session-model]]
