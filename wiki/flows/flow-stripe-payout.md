---
title: "Flow: Stripe Payout"
type: flow
tags: [flow, payments]
source: "feature/copilot/src/main/kotlin/com/vigia/feature/copilot/CopilotViewModel.kt"
related: ["[[copilot-viewmodel]]", "[[stripe-pay-repository]]", "[[wallet-repository]]", "[[stripe-sdk]]"]
updated: 2026-06-20
---

# Flow: Stripe Payout

How the user cashes out earned VGA tokens via Stripe Connect.

## Prerequisites

Stripe Connect onboarding must be complete. `CopilotViewModel.startStripeOnboarding()` launches onboarding:
1. Signs `"VIGIA-BALANCE:<wallet>:<ts>"` with `walletRepository.signRaw()`
2. Calls `StripePayRepositoryImpl.setWalletProof(address, timestamp, signature)`
3. `stripePayRepository.startConnectOnboarding()` → `POST /stripe/connect-onboarding`

## Payout Flow

1. User taps "Cash Out" in wallet panel → `CopilotViewModel.requestPayout()`
2. Guard: `ws.isProvisioned && ws.pendingBalanceMicroVigia > 0`
3. Signs `"VIGIA-BALANCE:<wallet>:<ts>"` → `setWalletProof(...)`
4. `amountCents = pendingBalanceMicroVigia / 10_000L` (1 VGA = $1 = 100 cents)
5. `stripePayRepository.initiatePayment(amountCents, "usd")` → `POST /stripe/create-payment-intent`
6. Backend verifies wallet proof, creates Stripe PaymentIntent with `transfer_data.destination = connect_account_id`
7. `StripePaySheet` launches Stripe's pre-built PaymentSheet with the `clientSecret`

## Links

[[copilot-viewmodel]] [[stripe-pay-repository]] [[wallet-repository]] [[stripe-pay-sheet]]
[[stripe-sdk]] [[aws-backend]]
