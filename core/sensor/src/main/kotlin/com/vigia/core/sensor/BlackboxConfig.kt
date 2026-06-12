package com.vigia.core.sensor

/**
 * Hardware identity parameters for the Pi 5 Blackbox.
 * Provided by the :app module via Hilt so :core:sensor never holds build-flavour secrets directly.
 */
data class BlackboxConfig(
    /** Bluetooth MAC address of the paired Pi 5 Blackbox. */
    val macAddress: String,
    /**
     * CDM association ID obtained after the first successful CompanionDeviceManager pairing.
     * 0 means no association has been completed yet.
     */
    val associationId: Int = 0,
)
