---
title: "BleLinkManager"
type: client
tags: [client, ble]
source: "core/sensor/src/main/kotlin/com/vigia/core/sensor/ble/BleLinkManager.kt"
related: ["[[keystore-manager]]", "[[ecdh-handshake]]", "[[ble-data-streamer]]", "[[core-sensor]]"]
updated: 2026-06-20
---

# BleLinkManager

`@Singleton`. Full BLE GATT connection pipeline for the Pi 5 Blackbox. Source: `BleLinkManager.kt`.

## Connection Steps

1. **Scan** — LE scan filtered by MAC address (`ScanSettings.SCAN_MODE_LOW_LATENCY`)
2. **GATT connect** — `TRANSPORT_LE`; 15 s timeout
3. **MTU negotiation** — requests 517 bytes; LE 2M PHY set (best-effort)
4. **LE SC Bond** — `createBond()` if not already bonded; `ACTION_BOND_STATE_CHANGED` broadcast
5. **Service discovery** — 10 s timeout
6. **ECDH P-256 handshake** — HELLO → CHALLENGE → RESPONSE → CONFIRM (HMAC-SHA256 verification)
7. **Confirm dims** — writes `REQUEST_256D` to CONTROL\_CHAR
8. **Enable notifications** — RESPONSE\_CHAR and TELEMETRY\_CHAR CCCDs enabled

## GATT UUIDs (from GattConstants.kt)

| Characteristic | UUID |
|---|---|
| Service | `5e355f98-eabf-4ae0-8417-919e926d411e` |
| Handshake | `eb4b161b-3be6-4719-aa0f-8ef40bd44a36` |
| Telemetry | `4d231514-5514-4847-bb6d-64e3aa7a3ffb` |
| Control | `0bb821dd-6b24-4185-ad69-662510769d19` |
| Attest | `580c5fb6-5283-4194-84c8-5d6aec75b88a` |
| Response | `a3f1c2d7-88e4-4b9a-b3c1-5d7e9f012345` |

## Exposed Flows

- `linkState: StateFlow<BleLinkState>` — pipeline progress
- `incomingFrames: SharedFlow<ByteArray>` — raw GATT notification bytes (extraBufferCapacity=64)

## Links

[[keystore-manager]] [[ecdh-handshake]] [[ble-data-streamer]] [[core-sensor]]
[[ble-link-state-model]] [[flow-ble-pairing]] [[adr-ecdh-p256]]
