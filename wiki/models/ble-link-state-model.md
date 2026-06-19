---
title: "BleLinkState"
type: model
tags: [model, ble]
source: "core/model/src/main/kotlin/com/vigia/core/model/BleLinkState.kt"
related: ["[[ble-link-manager]]", "[[copilot-viewmodel]]"]
updated: 2026-06-20
---

# BleLinkState

Sealed class representing the BLE GATT connection pipeline state:

```
Idle → Scanning → Connecting(deviceAddress) → Pairing → Handshaking → Bound
                                                                      ↓ (on error)
                                                                   Error(BleLinkError)
```

`BleLinkError` enum: `SCAN_FAILED`, `CONNECTION_TIMEOUT`, `PAIRING_FAILED`, `GATT_ERROR`, `HANDSHAKE_FAILED`.

## Links

[[ble-link-manager]] [[copilot-viewmodel]] [[core-model]]
