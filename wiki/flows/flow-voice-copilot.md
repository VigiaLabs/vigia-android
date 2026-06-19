---
title: "Flow: Voice Copilot (mic → STT → search → TTS)"
type: flow
tags: [flow, voice]
source: "feature/copilot/src/main/kotlin/com/vigia/feature/copilot/CopilotViewModel.kt"
related: ["[[copilot-viewmodel]]", "[[voice-amplitude-monitor]]", "[[sarvam-stt-client]]", "[[vigia-search-client]]", "[[tts-manager]]"]
updated: 2026-06-20
---

# Flow: Voice Copilot

End-to-end conversational loop for hands-free voice interaction.

## Steps

1. User taps mic FAB → `CopilotViewModel.startVoiceMode()`
2. `VoiceAmplitudeMonitor.startRecording()` — MediaRecorder opens mic
3. Amplitude collected → `voiceAmplitude` in `CopilotUiState.Active` → `VoiceCallOverlay` aurora animation
4. User releases mic button → `CopilotViewModel.endVoiceRecording()`
5. `VoiceAmplitudeMonitor.stopAndGetWav()` → 16 kHz 16-bit mono WAV bytes
6. `SarvamSttClient.transcribe(wav)` → transcript string (via backend `/sarvam-proxy/stt`)
7. If transcript blank → `reopenMic()` (no overlay close); else `sendMessage(transcript)`
8. `CopilotViewModel.startSearch(VigiaSearchContext)` — SSE stream begins
9. `SearchEvent.Step` events → narrated via `TtsManager.speakSarvam(step)` during voice mode
10. `SearchEvent.TextDelta` events → `searchAnswer` accumulates (displayed in UI)
11. `SearchEvent.Done` → persist ASSISTANT message to Room
12. `TtsManager.speakSarvam(finalAnswer, onDone = { startVoiceMode() })` — TTS plays answer
13. `onDone` fires → `startVoiceMode()` auto-reopens mic for next turn

## Error Handling

STT error or blank transcript → `reopenMic()` (no session close, no error message). Network error mid-SSE → partial tokens persisted with `MessageStatus.Partial`.

## Links

[[copilot-viewmodel]] [[voice-call-overlay]] [[voice-amplitude-monitor]] [[sarvam-stt-client]]
[[vigia-search-client]] [[tts-manager]] [[chat-repository]] [[search-event-model]]
