---
title: "VoiceAmplitudeMonitor"
type: client
tags: [client, voice]
source: "core/sensor/src/main/kotlin/com/vigia/core/sensor/voice/VoiceAmplitudeMonitor.kt"
related: ["[[copilot-viewmodel]]", "[[sarvam-stt-client]]", "[[tts-manager]]"]
updated: 2026-06-20
---

# VoiceAmplitudeMonitor

`@Singleton`. Captures microphone audio, emits real-time amplitude for UI animation, and exports WAV for STT.

## Key API

```kotlin
val amplitude: Flow<Float>   // live dB amplitude (0–1 normalized) during recording
fun startRecording()         // opens MediaRecorder, starts amplitude polling
fun stopAndGetWav(): ByteArray   // stops recording, returns 16kHz 16-bit mono WAV bytes
fun stopSilently()           // stops without returning bytes (hold/dismiss paths)
```

## Usage in CopilotViewModel

`startRecording()` on `startVoiceMode()`. Amplitude collected → `voiceAmplitude` in `CopilotUiState.Active` → feeds aurora animation. `stopAndGetWav()` called on `endVoiceRecording()` → passed to `SarvamSttClient.transcribe()`.

## Links

[[copilot-viewmodel]] [[sarvam-stt-client]] [[tts-manager]] [[di-sensor-module]]
[[flow-voice-copilot]]
