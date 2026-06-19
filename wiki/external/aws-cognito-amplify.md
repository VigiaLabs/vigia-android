---
title: "AWS Cognito / Amplify"
type: external
tags: [external, auth]
source: "core/auth/src/main/kotlin/com/vigia/core/auth/AmplifyAuthRepository.kt"
related: ["[[auth-repository]]", "[[vigia-auth-interceptor]]", "[[core-auth]]"]
updated: 2026-06-20
---

# AWS Cognito / Amplify

Amplify v2 (`aws-auth-cognito 2.19.1`) provides Cognito User Pools authentication with Google Hosted UI federation.

## Initialisation

`AmplifyInitializer` (`ContentProvider`) calls `Amplify.addPlugin(AWSCognitoAuthPlugin())` and `Amplify.configure(context)` before `Application.onCreate`. `amplifyconfiguration.json` must be in `app/src/main/res/raw/` (gitignored).

## Google Sign-In

`Amplify.Auth.signInWithWebUI(AuthProvider.google(), context)` → Cognito Hosted UI → `HostedUIRedirectActivity` receives callback on `vigia://callback/` scheme.

## ID Token

`AmplifyAuthRepository.getIdToken()` fetches the current Cognito ID token from `Amplify.Auth.fetchAuthSession()`. Used by `VigiaAuthInterceptor` to add `Authorization: Bearer` header.

## Links

[[auth-repository]] [[vigia-auth-interceptor]] [[core-auth]] [[di-auth-module]] [[aws-backend]]
