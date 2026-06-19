---
title: "ADR: Sarvam calls via backend proxy"
type: decision
tags: [decision, security]
source: "core/network/src/main/kotlin/com/vigia/core/network/sarvam/SarvamSttClientImpl.kt"
related: ["[[sarvam-stt-client]]", "[[sarvam-tts-client]]", "[[aws-backend]]"]
updated: 2026-06-20
---

# ADR: Sarvam Calls Route Through Backend Proxy

## Decision

`SarvamSttClientImpl` and `SarvamTtsClientImpl` call the VIGIA backend at `/sarvam-proxy/stt` and `/sarvam-proxy/tts` respectively. They do not call Sarvam's API directly.

## Rationale

Including the `API-Subscription-Key` in the APK would expose the credential to anyone who decompiles it (ProGuard strings are reversible). Secrets Manager in AWS holds the key; the backend proxy forwards authenticated requests to Sarvam. This is consistent with the general principle that no API credentials appear in the APK.

## Consequence

All Sarvam calls add ~1 network hop through the VIGIA API Gateway. Read timeout is set to 120 s to accommodate STT on long recordings and TTS on long answers.

## Links

[[sarvam-stt-client]] [[sarvam-tts-client]] [[sarvam-ai]] [[aws-backend]] [[di-network-module]]
