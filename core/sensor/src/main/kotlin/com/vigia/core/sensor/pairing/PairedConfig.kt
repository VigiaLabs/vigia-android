package com.vigia.core.sensor.pairing

/**
 * Persisted pairing state after a successful QR scan + CDM.associate() flow (design spec §5).
 *
 * Stored in DataStore Preferences. Null means the device has never been paired.
 */
data class PairedConfig(
    /** Bluetooth MAC address parsed from the QR code. */
    val mac: String,
    /** Pi's P-256 identity public key, uncompressed 65-byte point (from QR field `pk`). */
    val piPublicKeyBytes: ByteArray,
    /** CDM association ID returned by CompanionDeviceManager.associate(). Enables auto-connect. */
    val associationId: Int,
    /** Device ID string from the QR code (e.g. "vigia-001"). For display only. */
    val deviceId: String = "",
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PairedConfig) return false
        return mac == other.mac &&
            piPublicKeyBytes.contentEquals(other.piPublicKeyBytes) &&
            associationId == other.associationId &&
            deviceId == other.deviceId
    }

    override fun hashCode(): Int {
        var result = mac.hashCode()
        result = 31 * result + piPublicKeyBytes.contentHashCode()
        result = 31 * result + associationId
        result = 31 * result + deviceId.hashCode()
        return result
    }
}
