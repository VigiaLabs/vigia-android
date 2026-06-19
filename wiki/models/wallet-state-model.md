---
title: "WalletState + TelemetrySignature"
type: model
tags: [model, wallet]
source: "core/wallet/src/main/kotlin/com/vigia/core/wallet/WalletRepository.kt"
related: ["[[wallet-repository]]", "[[copilot-viewmodel]]"]
updated: 2026-06-20
---

# WalletState + TelemetrySignature

```kotlin
data class WalletState(
    val publicKey: String = "",
    val pendingBalanceMicroVigia: Long = 0L,
    val totalEarnedMicroVigia: Long = 0L,
    val totalClaimedMicroVigia: Long = 0L,
    val isSyncing: Boolean = false,
    val isProvisioned: Boolean = false,
) {
    val pendingBalanceVigia: Double get() = pendingBalanceMicroVigia / 1_000_000.0
    val totalEarnedVigia: Double get() = totalEarnedMicroVigia / 1_000_000.0
}

data class TelemetrySignature(
    val publicKey: String,
    val signature: String,   // Base58-encoded Ed25519 sig
)
```

## Links

[[wallet-repository]] [[copilot-viewmodel]] [[ed25519-keystore]] [[core-wallet]]
