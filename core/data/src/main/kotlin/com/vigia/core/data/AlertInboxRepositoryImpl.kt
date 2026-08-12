package com.vigia.core.data

import com.vigia.core.data.db.AlertInboxDao
import com.vigia.core.data.db.toHazardAlert
import com.vigia.core.data.db.toInboxEntity
import com.vigia.core.model.HazardAlert
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlertInboxRepositoryImpl @Inject constructor(
    private val dao: AlertInboxDao,
) : AlertInboxRepository {
    override val pendingAlerts: Flow<List<HazardAlert>> =
        dao.observePending().map { rows -> rows.map { it.toHazardAlert() } }

    override suspend fun store(alert: HazardAlert) {
        dao.upsert(alert.toInboxEntity())
        // Keep the local inbox bounded even if a device remains offline for weeks.
        dao.deleteOlderThan(System.currentTimeMillis() - RETENTION_MS)
    }

    override suspend fun acknowledge(alertId: String) = dao.acknowledge(alertId)

    private companion object {
        const val RETENTION_MS = 30L * 24L * 60L * 60L * 1_000L
    }
}
