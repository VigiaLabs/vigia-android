---
title: "Ed25519KeyStore"
type: security
tags: [security, wallet]
source: "core/wallet/src/main/kotlin/com/vigia/core/wallet/Ed25519KeyStore.kt"
related: ["[[wallet-repository]]", "[[keystore-manager]]", "[[core-wallet]]"]
updated: 2026-06-20
---

# Ed25519KeyStore

Manages the DePIN wallet Ed25519 keypair. Private key is AES-256-GCM encrypted and stored in SharedPreferences (`vigia_wallet_v1`); the AES wrapping key (`vigia_wallet_aes_v1`) never leaves the Android Keystore TEE.

## Key Operations

```kotlin
fun provision()                          // generate keypair + encrypt; idempotent
val isProvisioned: Boolean
val publicKeyBase58: String              // 32-byte raw pubkey, Bitcoin alphabet Base58
fun sign(message: ByteArray): String     // returns Base58-encoded 64-byte Ed25519 signature
```

## Key Generation Detail

`KeyPairGenerator("Ed25519")` on the JVM. Raw 32-byte public key extracted by dropping the 12-byte SubjectPublicKeyInfo header from the X.509 encoding (`keyPair.public.encoded.drop(12)`). Private key (PKCS8, 48 bytes) encrypted with `AES/GCM/NoPadding`, GCM tag 128 bits. IV stored separately in SharedPreferences.

## Security Properties

- Wrapping key PURPOSE: ENCRYPT | DECRYPT; BLOCK_MODE_GCM; keySize=256
- `setUserAuthenticationRequired(false)` — background operations don't require biometric
- The Ed25519 private key material itself is never stored in cleartext

## Links

[[wallet-repository]] [[keystore-manager]] [[core-wallet]] [[di-wallet-module]]
[[flow-wallet-balance-refresh]] [[flow-telemetry-sign-upload]]
