---
title: "AuthScreen"
type: screen
tags: [screen, auth]
source: "feature/copilot/src/main/kotlin/com/vigia/feature/copilot/auth/AuthScreen.kt"
related: ["[[auth-viewmodel]]", "[[auth-repository]]", "[[aws-cognito-amplify]]"]
updated: 2026-06-20
---

# AuthScreen

Cognito sign-in / sign-up / email confirm / Google sign-in flow. Stateless composable: all state from `AuthUiState` passed as parameters.

## Auth Steps

`AuthStep` enum drives which sub-form is rendered: `SIGN_IN`, `SIGN_UP`, `CONFIRM_EMAIL`. Navigation between steps driven by `onGoTo` callback → `AuthViewModel.goTo()`.

## Reusable Components (private)

- `PrimaryButton` — themed CTA button (fan-in=4)
- `LinkRow` — text link for "Don't have an account? Sign up" navigation (fan-in=4)
- `ErrorText` — red error message (fan-in=4)

## Google Sign-In

Uses `Credential Manager` API (`GetGoogleIdOption`, `CredentialManager`). Result passed to `onGoogle` callback → `AuthViewModel.signInWithGoogle()`.

## Links

[[auth-viewmodel]] [[auth-repository]] [[aws-cognito-amplify]] [[app-root-screen]]
