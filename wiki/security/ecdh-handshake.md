---
title: "EcdhHandshake"
type: security
tags: [security, ble]
source: "core/sensor/src/main/kotlin/com/vigia/core/sensor/ble/EcdhHandshake.kt"
related: ["[[ble-link-manager]]", "[[keystore-manager]]", "[[adr-ecdh-p256]]"]
updated: 2026-06-20
---

# EcdhHandshake

Utility object containing pure cryptographic helper functions for the BLE mutual authentication protocol. No Android Keystore calls — these are JVM-side computations.

## Functions

```kotlin
fun hkdfSha256(inputKeyMaterial: ByteArray, salt: ByteArray, info: String = "vigia-ble-v1"): ByteArray
    // Derives 32-byte session key from ECDH shared secret

fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray
    // Used to verify CONFIRM frame: HMAC(session_key, CONFIRM_LABEL + nonce_pi + nonce_phone)

fun verifyEcdsaP256(pubKey65: ByteArray, signedData: ByteArray, sig: ByteArray): Boolean
    // Verifies Pi's CHALLENGE signature using the pinned Pi public key

val CONFIRM_LABEL: ByteArray  // = "VIGIA-CONFIRM".toByteArray()
```

## Session Key Derivation

`HKDF-SHA256(ECDH(Pi_priv, Phone_pub), salt = nonce_pi || nonce_phone, info = "vigia-ble-v1")` → 32-byte session key. Both sides derive the same key independently; the CONFIRM frame's HMAC proves derivation succeeded.

## Links

[[ble-link-manager]] [[keystore-manager]] [[adr-ecdh-p256]] [[flow-ble-pairing]]
