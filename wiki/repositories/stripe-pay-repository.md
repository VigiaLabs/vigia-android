---
title: "StripePayRepository"
type: repository
tags: [repository, payments]
source: "core/network/src/main/kotlin/com/vigia/core/network/stripe/StripePayRepository.kt"
related: ["[[copilot-viewmodel]]", "[[stripe-pay-sheet]]", "[[stripe-sdk]]", "[[flow-stripe-payout]]"]
updated: 2026-06-20
---

# StripePayRepository

Interface + `StripePayRepositoryImpl` (`@Singleton`). Stripe Connect onboarding, PaymentIntent creation, and Financial Connections.

## Interface

```kotlin
val payoutStatus: StateFlow<PayoutStatus>
suspend fun startConnectOnboarding()
suspend fun initiatePayment(amountCents: Long, currency: String)
suspend fun startFinancialConnectionsSession(): String  // returns client_secret
```

## Wallet Proof

`StripePayRepositoryImpl.setWalletProof(address, timestamp, signature)` stores the Ed25519 ownership proof. All backend calls include it as HTTP headers so the Lambda can verify the payout request is from the legitimate wallet owner.

## Backend Endpoints

`POST /stripe/connect-onboarding`, `POST /stripe/create-payment-intent`, `POST /stripe/financial-connections-session` — all via `@Named("VigiaOkHttpClient")`.

## Links

[[copilot-viewmodel]] [[stripe-pay-sheet]] [[stripe-sdk]] [[wallet-repository]]
[[flow-stripe-payout]] [[di-network-module]] [[aws-backend]]
