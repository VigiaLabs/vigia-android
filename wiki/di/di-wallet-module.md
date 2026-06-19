---
title: "WalletModule (Hilt)"
type: di
tags: [di, security]
source: "core/wallet/src/main/kotlin/com/vigia/core/wallet/di/WalletModule.kt"
related: ["[[core-wallet]]", "[[ed25519-keystore]]", "[[wallet-repository]]"]
updated: 2026-06-20
---

# WalletModule (Hilt)

`@Module @InstallIn(SingletonComponent)`. Provides `Ed25519KeyStore`, the wallet OkHttp client, and binds `WalletRepository`.

## Provides

- `Ed25519KeyStore(context)` — `@Singleton`
- `@Named("WalletOkHttpClient") OkHttpClient` — plain (no auth interceptor); separate from the Sarvam and Vigia clients
- `@Named("VigiaApiBaseUrl") String` — delegated from `AppModule`

## Binds

`WalletRepository` → `WalletRepositoryImpl`

## Links

[[core-wallet]] [[ed25519-keystore]] [[wallet-repository]] [[di-app-module]]
