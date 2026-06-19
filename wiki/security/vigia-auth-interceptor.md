---
title: "VigiaAuthInterceptor"
type: security
tags: [security, network]
source: "core/network/src/main/kotlin/com/vigia/core/network/auth/VigiaAuthInterceptor.kt"
related: ["[[di-network-module]]", "[[auth-repository]]", "[[aws-cognito-amplify]]"]
updated: 2026-06-20
---

# VigiaAuthInterceptor

OkHttp `Interceptor`. Appends `Authorization: Bearer <cognito_id_token>` to all requests made through `@Named("VigiaOkHttpClient")`.

## Behavior

- Calls `ApiTokenProvider.getIdToken()` synchronously (blocking, OkHttp dispatch thread)
- If token is null (demo mode or signed out): omits the header (requests to public routes succeed; protected routes fail with 403)
- Applied before the logging interceptor so the token appears in debug logs

## ApiTokenProvider

Bridges the `AuthRepository.getIdToken(): suspend String?` into a synchronous interface using `runBlocking { authRepository.getIdToken() }`. This is the only place `runBlocking` is used in the codebase.

## Links

[[di-network-module]] [[auth-repository]] [[aws-cognito-amplify]] [[di-app-module]]
