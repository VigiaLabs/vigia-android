---
title: "Sarvam AI"
type: external
tags: [external, voice]
source: "core/network/src/main/kotlin/com/vigia/core/network/sarvam/SarvamSttClient.kt"
related: ["[[sarvam-stt-client]]", "[[sarvam-tts-client]]", "[[adr-sarvam-proxy]]"]
updated: 2026-06-20
---

# Sarvam AI

Indian-language AI models for STT and TTS. API calls routed through the VIGIA backend proxy; no Sarvam credentials in the APK.

| Model | Type | Languages |
|---|---|---|
| `saarika:v2` | STT | Indian English + 10 Indian languages, auto-detect |
| `bulbul:v1` | TTS | Indian English (`en-IN`), Hindi (`hi-IN`), etc. Default voice: "meera" |

Input for STT: 16 kHz 16-bit mono WAV. Output for TTS: raw WAV bytes.

## Links

[[sarvam-stt-client]] [[sarvam-tts-client]] [[adr-sarvam-proxy]] [[aws-backend]]
[[flow-voice-copilot]]
