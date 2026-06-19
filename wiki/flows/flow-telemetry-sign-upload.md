---
title: "Flow: Telemetry Sign + Upload"
type: flow
tags: [flow, security]
source: "core/wallet/src/main/kotlin/com/vigia/core/wallet/WalletRepositoryImpl.kt"
related: ["[[wallet-repository]]", "[[ed25519-keystore]]", "[[aws-backend]]"]
updated: 2026-06-20
---

# Flow: Telemetry Sign + Upload

How a road-hazard detection event is signed with the device Ed25519 key and submitted to the backend validator.

## Steps

1. Pi 5 detects hazard → publishes event over MQTT or BLE
2. App (or edge node) builds telemetry payload fields: `hazardType`, `lat`, `lon`, `timestamp`, `confidence`, optional `frameSha256`
3. `WalletRepository.signTelemetry(...)` called:
   - Constructs canonical payload string: `VIGIA:<type>:<lat>:<lon>:<ts>:<conf>[:<sha256>]`
   - `Ed25519KeyStore.sign(payload.toByteArray())` — decrypts private key from Keystore AES-GCM blob, signs, re-encrypts
   - Returns `TelemetrySignature(publicKey, signature)`
4. Caller POSTs to `/submit-telemetry` with body + `publicKey` + `signature`
5. Backend Lambda (`validator/index.ts`) reconstructs canonical payload string, calls `ed25519.verify(sig, payload, pubKey)`, accepts or rejects

## Frame Binding

When a JPEG frame is included: `frameSha256 = SHA-256(jpegBytes).hex`. The hash is appended to the signed payload, binding the specific frame bytes to the signature (prevents frame substitution at the validator).

## Links

[[wallet-repository]] [[ed25519-keystore]] [[aws-backend]] [[wallet-state-model]]
[[core-wallet]]
