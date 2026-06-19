---
title: "core:auth"
type: module
tags: [module, auth]
source: "core/auth/build.gradle.kts"
related: ["[[di-auth-module]]", "[[auth-viewmodel]]", "[[auth-screen]]", "[[aws-cognito-amplify]]"]
updated: 2026-06-20
---

# core:auth

Android library module. Cognito/Amplify authentication, Google federation via Credential Manager, and a `DemoAuthRepository` for offline/CI development.

## Key Files

| Path | Role |
|---|---|
| `AuthRepository.kt` | Interface: `authState: Flow<AuthState>`, `signIn()`, `signUp()`, `confirm()`, `signOut()`, `signInWithGoogle()`, `resendCode()` |
| `AuthModels.kt` | `AuthState` sealed class (Loading, SignedOut, SignedIn), `AuthUiState`, `AuthStep` |
| `AmplifyAuthRepository.kt` | Amplify v2 implementation; uses `Amplify.Auth` coroutine extensions |
| `DemoAuthRepository.kt` | Stub that always returns `AuthState.SignedIn` — used in `demo` product flavor |
| `AmplifyInitializer.kt` | `ContentProvider` that initialises Amplify before `Application.onCreate` |
| `di/AuthModule.kt` | `@Binds AuthRepository` to Amplify or Demo impl based on `BuildConfig` |

## Flavors

`demo` flavor binds `DemoAuthRepository`; `prod` binds `AmplifyAuthRepository`.

## Links

[[di-auth-module]] [[auth-viewmodel]] [[auth-screen]] [[aws-cognito-amplify]]
[[app-root-screen]] [[copilot-viewmodel]] [[auth-repository]]
