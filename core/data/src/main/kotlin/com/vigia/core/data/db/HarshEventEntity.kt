package com.vigia.core.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "harsh_events",
    indices = [Index("tripId"), Index("timestampMs")],
)
data class HarshEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: String,
    val type: String,           // HarshEventType name
    val magnitude: Float,       // g-force or bearing delta
    val lat: Double,
    val lon: Double,
    val timestampMs: Long,
    val uploadedAt: Long? = null,
)

enum class HarshEventType {
    HARSH_BRAKE,
    HARSH_ACCEL,
    SHARP_TURN,
    ROAD_IMPACT,
}
