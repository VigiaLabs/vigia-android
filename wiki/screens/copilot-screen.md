---
title: "CopilotScreen"
type: screen
tags: [screen, copilot]
source: "feature/copilot/src/main/kotlin/com/vigia/feature/copilot/CopilotScreen.kt"
related: ["[[copilot-viewmodel]]", "[[voice-call-overlay]]", "[[stripe-pay-sheet]]"]
updated: 2026-06-20
---

# CopilotScreen

Main copilot chat UI. Renders the AI orb, greeting, streaming search answer, chat history drawer, session list, wallet panel, and alert banner. Hosts the `VoiceCallOverlay` as a full-screen modal overlay.

## UI Regions

- **AI Orb** — `AiOrb` composable; state driven by `CopilotUiState.Active.orbState`
- **Session drawer** — slides in from left; lists all `ChatSession` items
- **Chat pane** — `LazyColumn` of `ChatMessage` items with markdown rendering; switches to session view on `_activeSessionId != null`
- **Streaming answer** — live text from `searchAnswer` field as `TextDelta` events arrive
- **Wallet panel** — shows `WalletUiState.balanceVga`, public key, `requestPayout()` / `startStripeOnboarding()` buttons
- **Alert banner** — top-of-screen banner for pending `HazardAlert` items from `pendingAlerts` list

## Voice Entry Point

Mic FAB → `CopilotViewModel.startVoiceMode()` → `VoiceCallOverlay` becomes visible.

## Links

[[copilot-viewmodel]] [[voice-call-overlay]] [[stripe-pay-sheet]] [[feature-copilot]]
[[copilot-screen]] [[wallet-repository]] [[mqtt-alert-repository]] [[vigia-search-client]]
