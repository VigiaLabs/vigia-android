---
title: "SarvamSttClient"
type: client
tags: [client, voice]
source: "core/network/src/main/kotlin/com/vigia/core/network/sarvam/SarvamSttClient.kt"
related: ["[[sarvam-tts-client]]", "[[voice-amplitude-monitor]]", "[[copilot-viewmodel]]", "[[sarvam-ai]]"]
updated: 2026-06-20
---

# SarvamSttClient

Interface + `SarvamSttClientImpl`. Transcribes 16 kHz 16-bit mono WAV audio via the VIGIA backend proxy (`POST /sarvam-proxy/stt`) which calls Sarvam's `saarika:v2` model.

## Interface

```kotlin
suspend fun transcribe(
    wavBytes: ByteArray,
    languageCode: String = "unknown",  // "unknown" = auto-detection
): String
```

Supports Indian English and 10+ Indian languages with auto-detection. Throws on network or API error.

## Implementation Note

The Sarvam `API-Subscription-Key` never appears in the APK. The backend proxy holds it in AWS Secrets Manager. See [[adr-sarvam-proxy]].

## Links

[[sarvam-tts-client]] [[voice-amplitude-monitor]] [[copilot-viewmodel]] [[sarvam-ai]]
[[di-network-module]] [[flow-voice-copilot]] [[adr-sarvam-proxy]]
