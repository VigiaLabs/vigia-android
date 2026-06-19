---
title: "WalletRepository"
type: repository
tags: [repository, security]
source: "core/wallet/src/main/kotlin/com/vigia/core/wallet/WalletRepository.kt"
related: ["[[ed25519-keystore]]", "[[copilot-viewmodel]]", "[[di-wallet-module]]", "[[flow-wallet-balance-refresh]]"]
updated: 2026-06-20
---

# WalletRepository

Interface + `WalletRepositoryImpl` (`@Singleton`). Manages the Ed25519 device identity, DePIN balance, and all signing operations.

## Interface

```kotlin
val state: StateFlow<WalletState>
suspend fun ensureProvisioned()
suspend fun refreshBalance()
fun signTelemetry(hazardType, lat, lon, timestamp, confidence, frameSha256?): TelemetrySignature
fun signRaw(bytes: ByteArray): String
```

## Impl: ensureProvisioned()

1. `keyStore.provision()` — generates Ed25519 keypair if absent; wraps private key with AES-GCM in Keystore
2. Signs `"VIGIA-REGISTER:<pubkey>"` → `POST /register-device` (proof-of-possession)
3. Calls `refreshBalance()`

## Impl: refreshBalance()

Signs `"VIGIA-BALANCE:<pubkey>:<ts>"` → `GET /rewards-balance` with `X-Wallet-Timestamp` + `X-Wallet-Signature` headers. Updates `_state` with `pendingBalanceMicroVigia`, `totalEarnedMicroVigia`, `totalClaimedMicroVigia`.

## Links

[[ed25519-keystore]] [[di-wallet-module]] [[copilot-viewmodel]] [[wallet-state-model]]
[[flow-wallet-balance-refresh]] [[flow-telemetry-sign-upload]] [[flow-stripe-payout]]
[[core-wallet]]
