package com.vigia.core.sensor.ble

import java.util.UUID

/**
 * GATT service and characteristic UUIDs for the Pi 5 Blackbox.
 *
 * These are the FINAL random 128-bit UUIDs (generated 2026-06, decision D6 in
 * docs/app_dashcam_integration.md). They MUST match the Pi-side values in
 * vigia-raspi: vigia_edge_node/include/vigia_edge_node/ble_gatt_constants.hpp.
 */
internal object GattConstants {

    val VIGIA_SERVICE_UUID: UUID  = UUID.fromString("5e355f98-eabf-4ae0-8417-919e926d411e")
    val HANDSHAKE_CHAR_UUID: UUID = UUID.fromString("eb4b161b-3be6-4719-aa0f-8ef40bd44a36")
    val TELEMETRY_CHAR_UUID: UUID = UUID.fromString("4d231514-5514-4847-bb6d-64e3aa7a3ffb")
    val CONTROL_CHAR_UUID: UUID   = UUID.fromString("0bb821dd-6b24-4185-ad69-662510769d19")
    val ATTEST_CHAR_UUID: UUID    = UUID.fromString("580c5fb6-5283-4194-84c8-5d6aec75b88a")
    // M11: ALERT_CHAR — Pi→phone critical alerts (FCW + ADAS). Matches kAlertUuid in ble_gatt_constants.hpp.
    val ALERT_CHAR_UUID: UUID     = UUID.fromString("c3a7d812-4f9e-4b3a-a5d2-7e1f8c0b6e94")
    // ACK/NACK/PONG replies from Pi on CONTROL_CHAR writes. Matches kResponseUuid in
    // vigia-raspi: include/vigia_edge_node/ble_gatt_constants.hpp.
    val RESPONSE_CHAR_UUID: UUID  = UUID.fromString("a3f1c2d7-88e4-4b9a-b3c1-5d7e9f012345")
    val CCCD_UUID: UUID           = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

    /** Application-layer handshake protocol bytes. Must match ble_gatt_node.cpp. */
    object Protocol {
        const val HELLO: Byte    = 0x01
        const val CHALLENGE: Byte = 0x02
        const val RESPONSE: Byte = 0x03
        const val BOUND: Byte    = 0x04
        const val ERR: Byte      = 0xFF.toByte()

        const val NONCE_BYTES      = 32
        const val P256_PUB_BYTES   = 65   // uncompressed point: 0x04 || X(32) || Y(32)

        // CHALLENGE layout: [0x02 | nonce(32) | Pi_pub(65) | ECDSA_sig(variable DER ~70 bytes)]
        const val CHALLENGE_MIN_BYTES = 1 + NONCE_BYTES + P256_PUB_BYTES + 1

        // RESPONSE layout: [0x03 | nonce_phone(32) | Phone_pub(65) | ECDSA_sig(variable DER)]
        const val RESPONSE_FIXED_PREFIX = 1 + NONCE_BYTES + P256_PUB_BYTES

        // CONFIRM layout: [0x04 | HMAC-SHA256(32)]
        const val CONFIRM_HMAC_BYTES = 32
    }

    /** CONTROL_CHAR opcodes (phone→Pi stream-mode commands). Matches kRequest* in ble_gatt_node.cpp. */
    object Control {
        const val REQUEST_256D: Byte = 0x00
        const val REQUEST_512D: Byte = 0x01
        const val REQUEST_RRI_ONLY: Byte = 0xFF.toByte()
        // M11: profile-scaled TTC threshold push. Payload: [0xA0 | ttc_f32_le(4)] = 5 bytes.
        const val SET_TTC_THRESHOLD: Byte = 0xA0.toByte()
    }

    /** Target ATT MTU for BLE link (design spec §7). */
    const val TARGET_MTU = 517
}
