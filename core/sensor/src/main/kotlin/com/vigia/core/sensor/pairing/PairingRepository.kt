package com.vigia.core.sensor.pairing

import kotlinx.coroutines.flow.Flow

interface PairingRepository {
    /** Emits the current pairing state, or null if never paired. Updates after [savePairedConfig]. */
    val pairedConfig: Flow<PairedConfig?>

    /** Persists a completed pairing. Called by PairingViewModel after CDM.associate() succeeds. */
    suspend fun savePairedConfig(config: PairedConfig)

    /** Clears all pairing data (factory reset). */
    suspend fun clearPairedConfig()
}
