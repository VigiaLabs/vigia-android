---
title: "KeystoreManager"
type: security
tags: [security, ble]
source: "core/sensor/src/main/kotlin/com/vigia/core/sensor/keystore/KeystoreManager.kt"
related: ["[[ble-link-manager]]", "[[ecdh-handshake]]", "[[ed25519-keystore]]", "[[adr-ecdh-p256]]"]
updated: 2026-06-20
---

# KeystoreManager

`@Singleton`. Manages the device's BLE identity EC P-256 key inside the Android Keystore. Separate from the wallet `Ed25519KeyStore` — this key is for BLE mutual auth only.

## Key Properties

- Algorithm: EC secp256r1 (P-256)
- PURPOSE: `PURPOSE_AGREE_KEY | PURPOSE_SIGN`
- StrongBox preferred (`setIsStrongBoxBacked(true)` on API 28+); falls back silently to regular Keystore TEE
- Private key never leaves the TEE hardware boundary

## Key Operations

```kotlin
fun provisionIfAbsent()                          // idempotent
fun getPublicKeyUncompressed(): ByteArray         // 65 bytes: 0x04 || X(32) || Y(32)
fun computeSharedSecret(peerPub65: ByteArray): ByteArray  // ECDH; returns 32-byte raw secret
fun signEcdsa(data: ByteArray): ByteArray         // SHA256withECDSA; DER ~70 bytes
```

## Usage in BLE Handshake

`BleLinkManager.performHandshake()`:
1. `keystoreManager.computeSharedSecret(piPub65)` → raw secret → HKDF-SHA256 → session key
2. `keystoreManager.getPublicKeyUncompressed()` → included in RESPONSE frame
3. `keystoreManager.signEcdsa(noncePhone + noncePi + phonePub65)` → ECDSA sig in RESPONSE

## Links

[[ble-link-manager]] [[ecdh-handshake]] [[ed25519-keystore]] [[adr-ecdh-p256]]
[[di-sensor-module]] [[core-sensor]] [[flow-ble-pairing]]
