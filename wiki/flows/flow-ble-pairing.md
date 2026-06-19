---
title: "Flow: BLE Pairing (QR → CDM → ECDH)"
type: flow
tags: [flow, ble]
source: "feature/pairing/src/main/kotlin/com/vigia/feature/pairing/PairingViewModel.kt"
related: ["[[pairing-viewmodel]]", "[[pairing-screen]]", "[[ble-link-manager]]", "[[keystore-manager]]", "[[ecdh-handshake]]"]
updated: 2026-06-20
---

# Flow: BLE Pairing

One-time setup that establishes the authenticated BLE link between the phone and the Pi 5 Blackbox.

## Steps

### Phase 1 — QR Scan + Backend Claim
1. `PairingScreen` mounts → camera permission requested
2. CameraX `ImageAnalysis` → `QrAnalyzer` (ML Kit) → detects `vigia://<mac>/<pi_pub_key_hex>`
3. `PairingViewModel.onQrDetected(rawValue)` → parse MAC + Pi public key bytes
4. `ClaimDeviceRepository.claimDevice(deviceId, walletAddress)` → `POST /claim-device`
5. On `AlreadyClaimed` → `PairingState.DeviceAlreadyClaimed`; on success → proceed

### Phase 2 — CDM Association
6. `CompanionDeviceManager.associate(request)` → system dialog
7. User approves → `associationId` returned via `ActivityResult`
8. `PairingViewModel.onCdmResultReceived(associationId)` → `PairingRepository.savePairedConfig()`

### Phase 3 — BLE GATT Connection + ECDH
9. `BleLinkManager.connect(mac, piPublicKeyBytes)` called (from `VigiaForegroundService`)
10. LE scan → GATT connect → MTU 517 → LE SC bond
11. ECDH P-256 handshake:
    - `KeystoreManager.provisionIfAbsent()` — generates phone P-256 TEE key
    - Send HELLO → receive CHALLENGE (nonce_pi, Pi_pub, ECDSA_sig)
    - Verify Pi identity (pinned pub key + ECDSA sig)
    - `KeystoreManager.computeSharedSecret(piPub65)` → HKDF-SHA256 → session key
    - Send RESPONSE (nonce_phone, phone_pub, ECDSA_sig)
    - Receive + verify CONFIRM (HMAC-SHA256 over session key)
12. `BleLinkState.Bound` — telemetry stream begins

## Links

[[pairing-viewmodel]] [[pairing-screen]] [[claim-device-repository]] [[pairing-repository]]
[[ble-link-manager]] [[keystore-manager]] [[ecdh-handshake]] [[app-root-viewmodel]]
[[adr-ecdh-p256]]
