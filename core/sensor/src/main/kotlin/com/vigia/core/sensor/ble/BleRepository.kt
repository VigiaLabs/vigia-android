package com.vigia.core.sensor.ble

import com.vigia.core.model.BleLinkState
import kotlinx.coroutines.flow.StateFlow

interface BleRepository {
    val linkState: StateFlow<BleLinkState>

    /**
     * @param piPublicKeyBytes Pi's pinned P-256 public key (65 bytes, from pairing QR).
     *   Null in demo/dev mode — identity pinning is skipped but ECDH still runs.
     */
    suspend fun startScan(targetDeviceAddress: String, piPublicKeyBytes: ByteArray? = null)
    suspend fun disconnect()

    /** Writes a dims-mode opcode to CONTROL_CHAR (see [GattConstants.Control]). */
    suspend fun requestDims(dimsCode: Byte)

    /** Pushes profile-scaled TTC threshold (BaseTtc × S_profile) to the edge node (M11 §3.3). */
    suspend fun sendTtcThreshold(ttcS: Float)
}
