package com.vigia.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface HarshEventDao {

    @Insert
    suspend fun insert(event: HarshEventEntity)

    @Query("SELECT * FROM harsh_events WHERE tripId = :tripId ORDER BY timestampMs ASC")
    suspend fun getForTrip(tripId: String): List<HarshEventEntity>

    @Query("SELECT COUNT(*) FROM harsh_events WHERE tripId = :tripId AND type = :type")
    suspend fun countByType(tripId: String, type: String): Int

    @Query("DELETE FROM harsh_events WHERE tripId = :tripId")
    suspend fun deleteTrip(tripId: String)

    @Query("DELETE FROM harsh_events WHERE timestampMs < :cutoffMs")
    suspend fun purgeOlderThan(cutoffMs: Long)
}
