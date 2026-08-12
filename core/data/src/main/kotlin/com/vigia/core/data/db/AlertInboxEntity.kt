package com.vigia.core.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.vigia.core.model.HazardAlert
import com.vigia.core.model.LocationSnapshot

/** Durable alert inbox row. An alert is stored before it is emitted to UI code. */
@Entity(
    tableName = "alert_inbox",
    indices = [Index("timestampMs"), Index("acknowledged")],
)
data class AlertInboxEntity(
    @PrimaryKey val id: String,
    val severity: String,
    val messageText: String,
    val timestampMs: Long,
    val latitudeDeg: Double?,
    val longitudeDeg: Double?,
    val accuracyMeters: Float?,
    val bearingDeg: Float?,
    val velocityMs: Float?,
    val locationTimestampMs: Long?,
    val receivedAtMs: Long,
    val acknowledged: Boolean = false,
)

fun HazardAlert.toInboxEntity(receivedAtMs: Long = System.currentTimeMillis()) =
    AlertInboxEntity(
        id = id,
        severity = severity.name,
        messageText = messageText,
        timestampMs = timestampMs,
        latitudeDeg = locationSnapshot?.latitudeDeg,
        longitudeDeg = locationSnapshot?.longitudeDeg,
        accuracyMeters = locationSnapshot?.accuracyMeters,
        bearingDeg = locationSnapshot?.bearingDeg,
        velocityMs = locationSnapshot?.velocityMs,
        locationTimestampMs = locationSnapshot?.timestampMs,
        receivedAtMs = receivedAtMs,
    )

fun AlertInboxEntity.toHazardAlert(): HazardAlert {
    val location = if (latitudeDeg != null && longitudeDeg != null) {
        LocationSnapshot(
            latitudeDeg = latitudeDeg,
            longitudeDeg = longitudeDeg,
            accuracyMeters = accuracyMeters ?: 0f,
            bearingDeg = bearingDeg ?: 0f,
            velocityMs = velocityMs ?: 0f,
            timestampMs = locationTimestampMs ?: timestampMs,
        )
    } else null
    return HazardAlert(
        id = id,
        severity = runCatching { HazardAlert.Severity.valueOf(severity) }
            .getOrDefault(HazardAlert.Severity.MEDIUM),
        messageText = messageText,
        timestampMs = timestampMs,
        locationSnapshot = location,
    )
}
