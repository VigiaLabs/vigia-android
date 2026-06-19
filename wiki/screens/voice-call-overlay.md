---
title: "VoiceCallOverlay"
type: screen
tags: [screen, voice]
source: "feature/copilot/src/main/kotlin/com/vigia/feature/copilot/voice/VoiceCallOverlay.kt"
related: ["[[copilot-viewmodel]]", "[[tts-manager]]", "[[voice-amplitude-monitor]]", "[[sarvam-stt-client]]"]
updated: 2026-06-20
---

# VoiceCallOverlay

Full-screen voice session composable, visible when `CopilotUiState.Active.isVoiceOverlayVisible == true`. Replaces the text input UI with an aurora-mist animation that responds to `voiceAmplitude` in real time.

## States Rendered

| `VoiceListeningState` | Visual |
|---|---|
| `Listening` | Aurora pulsing to mic amplitude |
| `Processing` | Spinner (STT in progress) |
| `Speaking` | Aurora pulsing to TTS playback amplitude |
| `Paused` | Dim static aurora; hold/resume controls |

## Controls

- **End recording** button → `CopilotViewModel.endVoiceRecording()`
- **Hold** button → `CopilotViewModel.holdVoiceMode()`
- **Resume** button → `CopilotViewModel.resumeVoiceMode()`
- **Dismiss** (back / X) → `CopilotViewModel.dismissVoiceOverlay()`

## Auto-loop

After TTS playback completes (`TtsManager.speakSarvam` `onDone` callback), `CopilotViewModel` calls `startVoiceMode()` again — the mic re-opens for the next conversational turn without any user action.

## Links

[[copilot-viewmodel]] [[tts-manager]] [[voice-amplitude-monitor]] [[sarvam-stt-client]]
[[flow-voice-copilot]] [[copilot-screen]]
