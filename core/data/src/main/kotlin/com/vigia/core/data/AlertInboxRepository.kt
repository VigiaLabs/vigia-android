package com.vigia.core.data

import com.vigia.core.model.HazardAlert
import kotlinx.coroutines.flow.Flow

/** Storage boundary for alerts received while the UI/process may be unavailable. */
interface AlertInboxRepository {
    val pendingAlerts: Flow<List<HazardAlert>>
    suspend fun store(alert: HazardAlert)
    suspend fun acknowledge(alertId: String)
}
