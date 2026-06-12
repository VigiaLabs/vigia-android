package com.vigia.core.sensor.ble

import java.util.UUID

/**
 * GATT service and characteristic UUIDs for the Pi 5 Blackbox.
 *
 * ⚠ PLACEHOLDER VALUES — replace with the real UUIDs supplied by the hardware team
 *   before any physical device testing. See design/phase2_hardware_connectivity_spec.md §3.
 */
internal object GattConstants {

    val VIGIA_SERVICE_UUID: UUID      = UUID.fromString("0000CAFE-0000-1000-8000-00805F9B34FB")
    val HANDSHAKE_CHAR_UUID: UUID     = UUID.fromString("0000CAF1-0000-1000-8000-00805F9B34FB")
    val TELEMETRY_CHAR_UUID: UUID     = UUID.fromString("0000CAF2-0000-1000-8000-00805F9B34FB")

    /** Client Characteristic Configuration Descriptor — standard BLE UUID. */
    val CCCD_UUID: UUID               = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

    /** Application-layer handshake protocol bytes. */
    object Protocol {
        const val HELLO: Byte    = 0x01
        const val RESPONSE: Byte = 0x03
        const val BOUND: Byte    = 0x04
        const val ERR: Byte      = 0xFF.toByte()

        /** First byte of a CHALLENGE notification from the Pi 5. */
        const val CHALLENGE: Byte = 0x02

        /** Expected nonce size in bytes following the CHALLENGE prefix byte. */
        const val NONCE_BYTES = 32
    }
}
