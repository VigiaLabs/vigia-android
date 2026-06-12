package com.vigia.core.sensor.cdm

import android.companion.CompanionDeviceManager
import android.companion.ObservingDevicePresenceRequest
import android.content.Context
import com.vigia.core.model.DevicePresenceState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges CompanionDeviceManager presence events into a reactive [StateFlow].
 *
 * CDM delivers presence changes in two ways:
 *  1. [CdmPresenceService.onDeviceAppeared] / [onDeviceDisappeared] — the primary path.
 *     The system binds to [CdmPresenceService] and calls these methods when BLE proximity changes.
 *  2. Explicit [registerPresenceObserver] — instructs CDM to actively scan for the given
 *     association, enabling the callbacks above even when the app is backgrounded.
 *
 * minSdk = 34 guarantee: [CompanionDeviceManager.startObservingDevicePresence] and
 * [CompanionDeviceManager.stopObservingDevicePresence] are API 34 — no version guard needed.
 */
@Singleton
class CdmPresenceRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : CdmPresenceRepository {

    private val _presenceState = MutableStateFlow<DevicePresenceState>(DevicePresenceState.Unknown)
    override val presenceState: StateFlow<DevicePresenceState> = _presenceState.asStateFlow()

    private val companionDeviceManager: CompanionDeviceManager by lazy {
        context.getSystemService(CompanionDeviceManager::class.java)
    }

    /**
     * Asks CDM to actively monitor the given [associationId].
     * Results are delivered via [CdmPresenceService].
     */
    // CDM system calls are fast binder calls — no IO dispatcher needed.
    override suspend fun registerPresenceObserver(associationId: Int) {
        if (associationId <= 0) return
        val request = ObservingDevicePresenceRequest.Builder()
            .setAssociationId(associationId)
            .build()
        companionDeviceManager.startObservingDevicePresence(request)
    }

    override suspend fun unregisterPresenceObserver(associationId: Int) {
        if (associationId <= 0) return
        val request = ObservingDevicePresenceRequest.Builder()
            .setAssociationId(associationId)
            .build()
        try {
            companionDeviceManager.stopObservingDevicePresence(request)
        } catch (_: Exception) { /* ignore if not currently observing */ }
    }

    /**
     * Called by [CdmPresenceService] on the system's binder thread.
     * [MutableStateFlow.value] is thread-safe, so no dispatcher switch is needed.
     */
    internal fun onPresenceChanged(isPresent: Boolean) {
        _presenceState.value = if (isPresent) DevicePresenceState.Present else DevicePresenceState.Absent
    }
}
