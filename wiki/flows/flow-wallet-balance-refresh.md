---
title: "Flow: Wallet Balance Refresh"
type: flow
tags: [flow, wallet]
source: "core/wallet/src/main/kotlin/com/vigia/core/wallet/WalletRepositoryImpl.kt"
related: ["[[wallet-repository]]", "[[ed25519-keystore]]", "[[copilot-viewmodel]]"]
updated: 2026-06-20
---

# Flow: Wallet Balance Refresh

Periodic ownership-proof fetch of the DePIN rewards balance. Runs every 60 seconds from `CopilotViewModel.startWalletPolling()`.

## Steps

1. `CopilotViewModel.startWalletPolling()` → `while(true) { refreshBalance(); delay(60_000) }`
2. `WalletRepository.refreshBalance()`:
   - `tsMs = System.currentTimeMillis()`
   - Signs `"VIGIA-BALANCE:<pubkey>:<tsMs>"` with `Ed25519KeyStore.sign()`
   - `GET /rewards-balance?wallet_address=<pubkey>` with headers `X-Wallet-Timestamp: <tsMs>` and `X-Wallet-Signature: <sig>`
3. Backend verifies Ed25519 sig over `VIGIA-BALANCE:<pubkey>:<ts>` — proves device holds private key
4. Response JSON: `{pending_balance, total_earned, total_claimed}` (micro-VGA strings)
5. `_state.update { it.copy(pendingBalanceMicroVigia=..., ...) }`
6. `CopilotViewModel.observeWalletState()` collects → updates `WalletUiState.balanceVga`

## Links

[[wallet-repository]] [[ed25519-keystore]] [[copilot-viewmodel]] [[wallet-state-model]]
[[aws-backend]] [[core-wallet]]
