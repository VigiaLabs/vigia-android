---
title: "TtsManager"
type: client
tags: [client, voice]
source: "core/sensor/src/main/kotlin/com/vigia/core/sensor/tts/TtsManager.kt"
related: ["[[sarvam-tts-client]]", "[[copilot-viewmodel]]", "[[voice-call-overlay]]"]
updated: 2026-06-20
---

# TtsManager

`@Singleton`. Coordinates Android's built-in `TextToSpeech` engine (for hazard alerts) and Sarvam `bulbul:v1` TTS (for copilot answers and reasoning steps).

## Key Methods

```kotlin
fun speak(text: String, queueMode: Int = TextToSpeech.QUEUE_ADD)   // Android TTS
suspend fun speakSarvam(text: String, onDone: (() -> Unit)? = null) // Sarvam TTS
fun stop()
```

## TTS Amplitude

`val ttsAmplitude: Flow<Float>` — polls AudioTrack playback amplitude during Sarvam TTS; `CopilotViewModel` collects this to animate the orb and aurora during `VoiceListeningState.Speaking`.

## Links

[[sarvam-tts-client]] [[copilot-viewmodel]] [[voice-call-overlay]]
[[di-sensor-module]] [[flow-voice-copilot]] [[flow-mqtt-hazard-alert]]
