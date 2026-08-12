package com.vigia.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertInboxDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(alert: AlertInboxEntity)

    @Query("SELECT * FROM alert_inbox WHERE acknowledged = 0 ORDER BY timestampMs DESC LIMIT 50")
    fun observePending(): Flow<List<AlertInboxEntity>>

    @Query("UPDATE alert_inbox SET acknowledged = 1 WHERE id = :id")
    suspend fun acknowledge(id: String)

    @Query("DELETE FROM alert_inbox WHERE receivedAtMs < :cutoffMs")
    suspend fun deleteOlderThan(cutoffMs: Long)
}
