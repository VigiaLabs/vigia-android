---
title: "AuthModule (Hilt)"
type: di
tags: [di, auth]
source: "core/auth/src/main/kotlin/com/vigia/core/auth/di/AuthModule.kt"
related: ["[[core-auth]]", "[[auth-repository]]"]
updated: 2026-06-20
---

# AuthModule (Hilt)

`@Module @InstallIn(SingletonComponent)`. Binds `AuthRepository` to either `AmplifyAuthRepository` (prod) or `DemoAuthRepository` (demo flavor) based on `BuildConfig.FLAVOR`.

## Links

[[core-auth]] [[auth-repository]]
