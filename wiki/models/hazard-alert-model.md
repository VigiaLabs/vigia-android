---
title: "HazardAlert"
type: model
tags: [model, alerts]
source: "core/model/src/main/kotlin/com/vigia/core/model/HazardAlert.kt"
related: ["[[mqtt-alert-repository]]", "[[copilot-viewmodel]]", "[[maps-viewmodel]]"]
updated: 2026-06-20
---

# HazardAlert

```kotlin
data class HazardAlert(
    val id: String,
    val severity: Severity,       // LOW, MEDIUM, HIGH, CRITICAL
    val messageText: String,
    val timestampMs: Long,
    val locationSnapshot: LocationSnapshot?,
)
enum class Severity { LOW, MEDIUM, HIGH, CRITICAL }
```

Received from MQTT (`vigia/alerts/{userId}`) or FCM fallback. CRITICAL: `QUEUE_FLUSH` TTS; HIGH: `OrbState.Alert`. Maps layer also renders hazard pins.

## Links

[[mqtt-alert-repository]] [[copilot-viewmodel]] [[maps-viewmodel]] [[location-snapshot-model]]
[[flow-mqtt-hazard-alert]] [[core-model]]
