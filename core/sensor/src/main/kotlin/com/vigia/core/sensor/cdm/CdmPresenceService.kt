package com.vigia.core.sensor.cdm

import android.companion.AssociationInfo
import android.companion.CompanionDeviceService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * System-bound [CompanionDeviceService] that receives CDM presence callbacks.
 *
 * The system binds to this service when a registered CDM association is within
 * BLE proximity and calls [onDeviceAppeared] / [onDeviceDisappeared] accordingly.
 *
 * Manifest requirements (in core/sensor/AndroidManifest.xml):
 *   - android:permission="android.permission.BIND_COMPANION_DEVICE_SERVICE"
 *   - intent-filter action: "android.companion.CompanionDeviceService"
 *   - android:exported="true" (the system must be able to bind)
 */
@AndroidEntryPoint
class CdmPresenceService : CompanionDeviceService() {

    @Inject
    lateinit var repository: CdmPresenceRepositoryImpl

    override fun onDeviceAppeared(associationInfo: AssociationInfo) {
        repository.onPresenceChanged(isPresent = true)
    }

    override fun onDeviceDisappeared(associationInfo: AssociationInfo) {
        repository.onPresenceChanged(isPresent = false)
    }
}
