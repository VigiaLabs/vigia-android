---
title: "SarvamTtsClient"
type: client
tags: [client, voice]
source: "core/network/src/main/kotlin/com/vigia/core/network/sarvam/SarvamTtsClient.kt"
related: ["[[sarvam-stt-client]]", "[[tts-manager]]", "[[sarvam-ai]]"]
updated: 2026-06-20
---

# SarvamTtsClient

Interface + `SarvamTtsClientImpl`. Synthesizes text to raw WAV bytes via `/sarvam-proxy/tts` → Sarvam `bulbul:v1`.

## Interface

```kotlin
suspend fun synthesize(
    text: String,
    languageCode: String = "en-IN",
    speaker: String = "meera",         // female, Indian English
): ByteArray  // raw WAV bytes for AudioTrack / MediaPlayer playback
```

## Links

[[sarvam-stt-client]] [[tts-manager]] [[sarvam-ai]] [[di-network-module]]
[[flow-voice-copilot]] [[adr-sarvam-proxy]]
