---
title: "AuthViewModel"
type: viewmodel
tags: [viewmodel, auth]
source: "feature/copilot/src/main/kotlin/com/vigia/feature/copilot/auth/AuthViewModel.kt"
related: ["[[auth-screen]]", "[[auth-repository]]", "[[aws-cognito-amplify]]"]
updated: 2026-06-20
---

# AuthViewModel

`@HiltViewModel`. Drives Cognito auth flows via `AuthRepository`. Converts domain `AuthState` into `AuthUiState` for the composable.

## Exposed State

- `authState: StateFlow<AuthState>` — Loading / SignedOut / SignedIn
- `ui: StateFlow<AuthUiState>` — form field values, error messages, loading flag, current `AuthStep`

## Methods

`onEmail()`, `onPassword()`, `onName()`, `onCode()`, `goTo(step)`, `signIn()`, `signUp()`, `confirm()`, `resendCode()`, `signOut()`, `signInWithGoogle()`.

## Links

[[auth-screen]] [[auth-repository]] [[aws-cognito-amplify]] [[app-root-screen]]
