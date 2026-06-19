# core:data

**Layer:** Core  
**Package:** `com.vigia.core.data`  
**Path:** `core/data/`  
**Depends on:** [[core-model]]

Room-backed persistence layer for chat sessions and messages. Exposes a single `ChatRepository` interface consumed by [[feature-copilot]].

## Key Types

### Repository
| Type | Description |
|------|-------------|
| `ChatRepository` | Interface: `insertMessage · allSessions · messagesForSession · bumpSession · deleteSession` |
| `ChatRepositoryImpl` | `@Singleton` impl; wraps Room DAOs, maps entities ↔ [[core-model]] types |

### Room Database
| Type | Description |
|------|-------------|
| `VigiaDatabase` | `@Database` — single Room database, version-managed |
| `ChatMessageDao` | CRUD + Flow queries for `ChatMessageEntity` |
| `ChatSessionDao` | CRUD + Flow queries for `ChatSessionEntity` |
| `ChatMessageEntity` | DB row: maps to/from `ChatMessage` in [[core-model]] |
| `ChatSessionEntity` | DB row: maps to/from `ChatSession` in [[core-model]] |

### DI
| Type | Description |
|------|-------------|
| `DatabaseModule` | Hilt `@Provides` for `VigiaDatabase` and DAOs |
| `RepositoryModule` | Hilt `@Binds` `ChatRepositoryImpl → ChatRepository` |

## Data Schema

```
ChatSessionEntity
  id TEXT PK · title TEXT · createdAt INTEGER · updatedAt INTEGER

ChatMessageEntity
  id TEXT PK · sessionId TEXT FK → ChatSessionEntity
  role TEXT · body TEXT · sources TEXT (JSON)
  reasoningSteps TEXT (JSON) · latencyMs INTEGER
  status TEXT · createdAt INTEGER
```

## Dependents
[[feature-copilot]] (CopilotViewModel reads/writes chat history) · [[app]] (Room DB lifecycle)
