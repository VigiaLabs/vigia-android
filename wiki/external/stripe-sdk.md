---
title: "Stripe Android SDK"
type: external
tags: [external, payments]
source: "core/network/src/main/kotlin/com/vigia/core/network/stripe/StripePayRepositoryImpl.kt"
related: ["[[stripe-pay-repository]]", "[[stripe-pay-sheet]]", "[[flow-stripe-payout]]"]
updated: 2026-06-20
---

# Stripe Android SDK

`stripe-android 21.5.0` + `financial-connections 21.5.0`.

Used for: PaymentSheet (pre-built payout UI), Connect onboarding (Stripe-hosted), and Financial Connections (bank account linking).

STRIPE\_PUBLISHABLE\_KEY is set as a `BuildConfig` field from `secrets.properties` — never hard-coded. Secret key lives in AWS Secrets Manager (backend-only).

## Links

[[stripe-pay-repository]] [[stripe-pay-sheet]] [[flow-stripe-payout]] [[core-network]]
