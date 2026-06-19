---
title: "ADR: ECDH P-256 replaced HMAC for BLE auth"
type: decision
tags: [decision, security]
source: "core/sensor/src/main/kotlin/com/vigia/core/sensor/ble/BleLinkManager.kt"
related: ["[[ble-link-manager]]", "[[keystore-manager]]", "[[ecdh-handshake]]"]
updated: 2026-06-20
---

# ADR: ECDH P-256 Replaced HMAC for BLE Auth

## Decision

BLE mutual authentication uses ECDH P-256 key agreement with ECDSA challenge-response. The original HMAC-based approach was abandoned.

## Rationale

Android StrongBox keys with `PURPOSE_SIGN` are non-exportable by design — their raw key material cannot be read out of the TEE. A symmetric HMAC scheme requires both sides to share the same key, but the phone's private key cannot be shared with the Pi. Therefore HMAC over the shared key is cryptographically impossible without key export, which defeats the purpose of the TEE.

ECDH solves this: the shared secret is derived independently on both sides from each party's public key. The phone's private key never leaves the Keystore hardware boundary.

## Protocol

```
Phone → Pi: HELLO
Pi → Phone: CHALLENGE [0x02 | nonce_pi(32) | Pi_pub(65) | ECDSA_sig]
Phone → Pi: RESPONSE  [0x03 | nonce_phone(32) | Phone_pub(65) | ECDSA_sig]
Pi → Phone: CONFIRM   [0x04 | HMAC-SHA256(session_key, "VIGIA-CONFIRM"||nonce_pi||nonce_phone)]
```

Session key: `HKDF-SHA256(ECDH(Pi_priv, Phone_pub), salt = nonce_pi || nonce_phone, info = "vigia-ble-v1")`

## Links

[[ble-link-manager]] [[keystore-manager]] [[ecdh-handshake]] [[flow-ble-pairing]]
