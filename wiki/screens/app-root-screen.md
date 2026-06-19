---
title: "AppRoot Screen"
type: screen
tags: [screen, auth]
source: "feature/copilot/src/main/kotlin/com/vigia/feature/copilot/AppRoot.kt"
related: ["[[app-root-viewmodel]]", "[[auth-screen]]", "[[pairing-screen]]", "[[copilot-screen]]"]
updated: 2026-06-20
---

# AppRoot Screen

Entry gate composable. Routes based on `AuthState` from `AuthViewModel` and `isPaired` from `AppRootViewModel`.

## Routing Logic

```
AuthState.Loading          → SplashGate() (animated AI orb)
AuthState.SignedOut        → AuthScreen
AuthState.SignedIn + isPaired == null → SplashGate()
AuthState.SignedIn + isPaired == false → PairingScreen
AuthState.SignedIn + isPaired == true  → CopilotRoute
```

Source: `AppRoot.kt:29–68`

## SplashGate

Renders `AiOrb(state = OrbState.Searching, size = 120.dp)` centered on a `MaterialTheme.colorScheme.background` fill. Shown during auth loading and while DataStore pairing state resolves.

## Links

[[app-root-viewmodel]] [[auth-viewmodel]] [[auth-screen]] [[pairing-screen]] [[copilot-screen]]
[[feature-copilot]]
