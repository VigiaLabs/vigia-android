package com.vigia.core.sensor.adas

import android.util.Log
import com.vigia.core.data.db.HarshEventDao
import com.vigia.core.data.db.HarshEventEntity
import com.vigia.core.data.db.HarshEventType
import com.vigia.core.model.DriverProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes IMU-derived harsh events to SQLite during a trip, zero network calls.
 *
 * Profile-scaled thresholds (base values in m/s²):
 *   HARSH_BRAKE/ACCEL threshold  = BASE_LONG_G_MS2 / sProfile  (EXPERT more tolerant)
 *   ROAD_IMPACT threshold         = BASE_VERT_G_MS2 / sProfile
 *
 * Engine-off is signalled by calling [endTrip], which compiles the debrief summary string
 * for the companion to speak via TTS.
 */
@Singleton
class HarshEventLogger @Inject constructor(
    private val dao: HarshEventDao,
    private val scope: CoroutineScope,
) {
    private var currentTripId: String? = null
    private var currentProfile: DriverProfile = DriverProfile.NEW

    fun startTrip(tripId: String, profile: DriverProfile) {
        currentTripId = tripId
        currentProfile = profile
        Log.d(TAG, "Trip started: $tripId [${profile.name}]")
    }

    /** Called from BLE-data / IMU pipeline when a harsh event is detected at the edge. */
    fun record(
        type: HarshEventType,
        magnitude: Float,
        lat: Double,
        lon: Double,
    ) {
        val tripId = currentTripId ?: return
        scope.launch(Dispatchers.IO) {
            dao.insert(
                HarshEventEntity(
                    tripId      = tripId,
                    type        = type.name,
                    magnitude   = magnitude,
                    lat         = lat,
                    lon         = lon,
                    timestampMs = System.currentTimeMillis(),
                )
            )
        }
    }

    /**
     * Called on BLE-disconnect (engine-off signal). Returns a spoken debrief string
     * for [TtsManager] to play and null if the trip had no events.
     */
    suspend fun endTrip(earningsRupees: Double = 0.0): String? {
        val tripId = currentTripId ?: return null
        currentTripId = null

        val events = dao.getForTrip(tripId)
        if (events.isEmpty() && earningsRupees == 0.0) return null

        val harshBrakes  = events.count { it.type == HarshEventType.HARSH_BRAKE.name }
        val harshAccels  = events.count { it.type == HarshEventType.HARSH_ACCEL.name }
        val sharpTurns   = events.count { it.type == HarshEventType.SHARP_TURN.name }
        val roadImpacts  = events.count { it.type == HarshEventType.ROAD_IMPACT.name }
        val hazardCount  = roadImpacts  // road impacts are verified hazard observations

        val parts = mutableListOf<String>()

        if (harshBrakes > 0) {
            val advice = when {
                harshBrakes >= 4 -> "Your following distance may be short — try to brake earlier."
                harshBrakes >= 2 -> "A couple of hard braking events — keep a longer following gap."
                else              -> "One hard braking event detected."
            }
            parts += "$harshBrakes harsh braking ${if (harshBrakes == 1) "event" else "events"}. $advice"
        }
        if (harshAccels > 0) {
            parts += "$harshAccels harsh acceleration ${if (harshAccels == 1) "event" else "events"} — smooth acceleration saves fuel."
        }
        if (sharpTurns > 0) {
            parts += "$sharpTurns sharp ${if (sharpTurns == 1) "turn" else "turns"} — reduce speed before corners."
        }

        val earningsLine = if (earningsRupees > 0) {
            val formatted = "₹%.2f".format(earningsRupees)
            "You logged $hazardCount verified hazard${if (hazardCount != 1) "s" else ""} — that's $formatted added to your wallet."
        } else if (hazardCount > 0) {
            "You contributed $hazardCount hazard ${if (hazardCount != 1) "observations" else "observation"} to the network."
        } else ""

        val summary = buildString {
            append("Trip complete. ")
            if (parts.isEmpty()) {
                append("Clean drive — no harsh events detected. ")
            } else {
                parts.forEach { append(it).append(" ") }
            }
            if (earningsLine.isNotBlank()) append(earningsLine)
        }.trim()

        // Purge events older than 30 days to keep SQLite lean.
        dao.purgeOlderThan(System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000)
        return summary
    }

    companion object {
        private const val TAG = "HarshEventLogger"

        // Profile-independent base thresholds; divide by sProfile to get profile-scaled trigger.
        const val BASE_LONG_G_MS2 = 5.0f   // m/s² longitudinal (brake/accel)
        const val BASE_VERT_G_MS2 = 8.0f   // m/s² vertical (road impact)
    }
}
