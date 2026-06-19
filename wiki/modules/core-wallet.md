---
title: "core:wallet"
type: module
tags: [module, security]
source: "core/wallet/build.gradle.kts"
related: ["[[ed25519-keystore]]", "[[wallet-repository]]", "[[di-wallet-module]]", "[[copilot-viewmodel]]"]
updated: 2026-06-20
---

# core:wallet

Android library module. Ed25519 device identity keypair (TEE-backed), DePIN rewards balance, telemetry signing, and ownership proof helpers.

## Key Files

| Path | Role |
|---|---|
| `Ed25519KeyStore.kt` | Generates Ed25519 keypair; AES-GCM wraps private key in Android Keystore; `sign(ByteArray): String` |
| `WalletRepository.kt` | Interface: `state: StateFlow<WalletState>`, `ensureProvisioned()`, `refreshBalance()`, `signTelemetry(...)`, `signRaw(ByteArray): String` |
| `WalletRepositoryImpl.kt` | `@Singleton`; calls `/register-device` and `/rewards-balance` with proof-of-possession headers |
| `Base58.kt` | Bitcoin-alphabet Base58 encoder/decoder (public key + signature serialization) |
| `di/WalletModule.kt` | `@Provides Ed25519KeyStore`; `@Named("WalletOkHttpClient")` plain OkHttp; `@Named("VigiaApiBaseUrl")` |

## WalletState Fields

`publicKey`, `pendingBalanceMicroVigia`, `totalEarnedMicroVigia`, `totalClaimedMicroVigia`, `isSyncing`, `isProvisioned`. Convenience properties: `pendingBalanceVigia = micro / 1_000_000`.

## Telemetry Signing Payload Format

```
VIGIA:<type>:<lat>:<lon>:<ts>:<conf>           (no frame)
VIGIA:<type>:<lat>:<lon>:<ts>:<conf>:<sha256>  (with JPEG frame SHA-256)
```

Must match `validator/index.ts` on the backend.

## Links

[[ed25519-keystore]] [[wallet-repository]] [[wallet-state-model]] [[di-wallet-module]]
[[copilot-viewmodel]] [[flow-wallet-balance-refresh]] [[flow-telemetry-sign-upload]]
[[flow-stripe-payout]] [[core-network]]
