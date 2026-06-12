package com.vigia.core.network.mqtt

import com.vigia.core.model.HazardAlert
import kotlinx.coroutines.flow.SharedFlow

interface MqttAlertRepository {
    val alerts: SharedFlow<HazardAlert>
    fun connect(userId: String)
    fun reconnect()
    fun disconnect()
    /** Direct injection path used by [VigiaFcmReceiver] when FCM delivers alert payload while MQTT is down. */
    suspend fun injectAlert(alert: HazardAlert)
}
