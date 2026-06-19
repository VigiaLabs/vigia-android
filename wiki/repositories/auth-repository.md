---
title: "AuthRepository"
type: repository
tags: [repository, auth]
source: "core/auth/src/main/kotlin/com/vigia/core/auth/AuthRepository.kt"
related: ["[[auth-viewmodel]]", "[[core-auth]]", "[[aws-cognito-amplify]]", "[[di-auth-module]]"]
updated: 2026-06-20
---

# AuthRepository

Interface with two implementations: `AmplifyAuthRepository` (prod) and `DemoAuthRepository` (demo flavor).

## Interface Methods

```kotlin
val authState: Flow<AuthState>
suspend fun signIn(email: String, password: String)
suspend fun signUp(name: String, email: String, password: String)
suspend fun confirm(email: String, code: String)
suspend fun resendCode(email: String)
suspend fun signOut()
suspend fun signInWithGoogle(context: Context)
suspend fun getIdToken(): String?
```

## AmplifyAuthRepository

- `userMessage` (fan-in=5): maps Amplify exception codes to user-readable strings
- Uses `Amplify.Auth` Kotlin coroutine extensions
- Google sign-in via `Amplify.Auth.signInWithWebUI` → Cognito Hosted UI

## DemoAuthRepository

Immediately emits `AuthState.SignedIn(demoUser)` — used in `demo` flavor to bypass Cognito.

## Links

[[auth-viewmodel]] [[core-auth]] [[aws-cognito-amplify]] [[di-auth-module]] [[app-root-screen]]
