---
title: "feature:copilot"
type: module
tags: [module, ui]
source: "feature/copilot/build.gradle.kts"
related: ["[[copilot-screen]]", "[[copilot-viewmodel]]", "[[auth-screen]]", "[[voice-call-overlay]]"]
updated: 2026-06-20
---

# feature:copilot

Android feature module. The main UI: auth gate, copilot chat, wallet panel, voice overlay, AI orb animation, Stripe sheet, and the Vigia theme system.

## Key Files

| Path | Role |
|---|---|
| `AppRoot.kt` | Root composable: routes Loading/SignedOut/SignedIn+paired |
| `AppRootViewModel.kt` | DataStore `isPaired` StateFlow; `onPairingComplete()` |
| `CopilotRoute.kt` | `NavHost` with bottom nav: Copilot tab, Maps tab |
| `CopilotScreen.kt` | Chat list, greeting, search answer stream, wallet pager |
| `CopilotViewModel.kt` | Orchestrator: search, voice, wallet, MQTT alerts, Stripe |
| `CopilotUiState.kt` | `sealed class CopilotUiState`: Loading, Active (with OrbState, VoiceListeningState, WalletUiState) |
| `auth/AuthScreen.kt` | Sign-in / sign-up / confirm / Google sign-in composable |
| `auth/AuthViewModel.kt` | Drives AuthRepository flows |
| `orb/AiOrbComponent.kt` | Animated AI orb: Idle, Active, Listening, Searching, Alert states |
| `stripe/StripePaySheet.kt` | Stripe PaymentSheet + onboarding launcher |
| `voice/VoiceCallOverlay.kt` | Full-screen voice session; AuroraMist animation responds to amplitude |
| `theme/VigiaTheme.kt` | Material 3 custom theme wrapper |
| `theme/VigiaColors.kt` | Color palette (dark glass surfaces, accent cyan/amber) |
| `theme/VigiaMotion.kt` | `pressScale` spring animation (fan-in=12) |
| `theme/VigiaTypography.kt` | Type scale |
| `theme/VigiaShapes.kt` | Corner radii |

## Hilt Entry Points

`@HiltViewModel` on `CopilotViewModel`, `AuthViewModel`, `AppRootViewModel`.
`@AndroidEntryPoint` on `MainActivity`.

## Links

[[app-root-screen]] [[copilot-screen]] [[copilot-viewmodel]] [[auth-screen]] [[auth-viewmodel]]
[[app-root-viewmodel]] [[voice-call-overlay]] [[stripe-pay-sheet]]
[[feature-maps]] [[feature-pairing]] [[core-network]] [[core-sensor]] [[core-data]] [[core-auth]] [[core-wallet]]
