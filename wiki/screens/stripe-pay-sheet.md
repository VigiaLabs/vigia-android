---
title: "StripePaySheet"
type: screen
tags: [screen, payments]
source: "feature/copilot/src/main/kotlin/com/vigia/feature/copilot/stripe/StripePaySheet.kt"
related: ["[[copilot-viewmodel]]", "[[stripe-pay-repository]]", "[[stripe-sdk]]"]
updated: 2026-06-20
---

# StripePaySheet

Bottom sheet composable for Stripe payout and Connect onboarding. Launches Stripe's pre-built PaymentSheet and Connect onboarding flow.

## Entry Points

- `CopilotViewModel.requestPayout()` — computes wallet ownership proof, calls `stripePayRepository.initiatePayment(amountCents, "usd")`
- `CopilotViewModel.startStripeOnboarding()` — computes wallet proof, calls `stripePayRepository.startConnectOnboarding()`

## Wallet Proof Injection

Before any Stripe call, the ViewModel signs `"VIGIA-BALANCE:<wallet>:<ts>"` with `walletRepository.signRaw()` and passes `address`, `timestamp`, `signature` to `StripePayRepositoryImpl.setWalletProof()`. The backend uses this to verify the payout request without a separate JWT.

## Links

[[copilot-viewmodel]] [[stripe-pay-repository]] [[wallet-repository]] [[stripe-sdk]]
[[flow-stripe-payout]]
