---
title: "CopilotViewModel"
type: viewmodel
tags: [viewmodel, copilot]
source: "feature/copilot/src/main/kotlin/com/vigia/feature/copilot/CopilotViewModel.kt"
related: ["[[copilot-screen]]", "[[vigia-search-client]]", "[[mqtt-alert-repository]]", "[[wallet-repository]]", "[[sarvam-stt-client]]", "[[tts-manager]]", "[[chat-repository]]"]
updated: 2026-06-20
---

# CopilotViewModel

`@HiltViewModel`. The central orchestrator for the copilot feature: SSE search streaming, voice STT→search→TTS loop, wallet balance polling, MQTT alert handling, and Stripe payout.

## Injected Dependencies

`VigiaSearchClient`, `MqttAlertRepository`, `ContextAggregator`, `TtsManager`, `CdmPresenceRepository`, `ChatRepository`, `SarvamSttClient`, `VoiceAmplitudeMonitor`, `WalletRepository`, `StripePayRepository`.

## Exposed StateFlows

| Flow | Type | Description |
|---|---|---|
| `uiState` | `StateFlow<CopilotUiState>` | Loading or Active (with orb, voice, wallet state) |
| `activeSessionId` | `StateFlow<String?>` | Null = home state; non-null = active session |
| `sessions` | `StateFlow<List<ChatSession>>` | All sessions from Room |
| `sessionMessages` | `StateFlow<List<ChatMessage>>` | Messages for active session |
| `payoutStatus` | `StateFlow<PayoutStatus>` | Delegated from `StripePayRepository` |

## Key Methods

- `sendMessage(text)` — creates/continues session, persists USER message to Room, starts SSE search
- `startVoiceMode()` / `endVoiceRecording()` / `dismissVoiceOverlay()` / `holdVoiceMode()` / `resumeVoiceMode()` — voice lifecycle
- `requestPayout()` / `startStripeOnboarding()` — wallet proof + Stripe calls
- `loadSession(id)` / `newSession()` / `deleteSession(id)` — session management

## Init Observers

On `init`: `observeSensorContext()`, `observeAlerts()`, `observeTtsAmplitude()`, `observeWalletState()`, `startWalletPolling()` (60 s interval).

## Alert Handling

CRITICAL alerts: `QUEUE_FLUSH` TTS + `OrbState.Alert`. HIGH: `QUEUE_ADD` TTS + `OrbState.Alert`.

## Links

[[copilot-screen]] [[voice-call-overlay]] [[vigia-search-client]] [[mqtt-alert-repository]]
[[wallet-repository]] [[sarvam-stt-client]] [[tts-manager]] [[chat-repository]]
[[stripe-pay-repository]] [[copilot-viewmodel]] [[flow-voice-copilot]] [[flow-mqtt-hazard-alert]]
[[flow-wallet-balance-refresh]] [[flow-stripe-payout]]
