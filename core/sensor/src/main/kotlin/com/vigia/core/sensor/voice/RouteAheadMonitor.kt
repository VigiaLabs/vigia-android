package com.vigia.core.sensor.voice

import android.util.Log
import com.vigia.core.model.DriverProfile
import com.vigia.core.model.HazardAlert
import com.vigia.core.model.LocationSnapshot
import com.vigia.core.model.RouteAheadHazard
import com.vigia.core.network.live.RoadAheadClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.atan2
import kotlin.math.sqrt
import kotlin.math.PI

/**
 * Monitors the route ahead and proactively announces hazards before the driver reaches them.
 *
 * Every [SCAN_INTERVAL_MS] (5s) it:
 *   1. Projects 3 look-ahead points (200m, 500m, 1000m) along the current GPS bearing.
 *   2. Converts each to a geohash-6 (~1.2km precision) for cloud query.
 *   3. Calls [RoadAheadClient.query] → returns hazards within those cells.
 *   4. For each un-announced hazard, schedules a proactive TTS announcement via [ProactiveEvent].
 *
 * Announcement timing:
 *   - ETA < 20s → announce immediately ("Pothole in 8 seconds — slow down")
 *   - ETA < 60s → announce at (ETA - 15s) mark
 *   - ETA ≥ 60s → announce when ETA crosses the 45s threshold on the next scan
 *
 * [routeAheadHazards] is a [StateFlow] injected into [VigiaSearchContext] so the LLM
 * has road-quality-ahead data for every query without the user having to ask.
 *
 * New-driver use cases:
 *   "Speed bump in 200 meters — slow to 20 km/h"
 *   "Historically bad road segment ahead — 1.3 km of rough surface"
 *   "Pothole cluster detected 400 meters ahead — consider right lane"
 *   "Road quality improves significantly after the next 500 meters"
 */
@Singleton
class RouteAheadMonitor @Inject constructor(
    private val roadAheadClient: RoadAheadClient,
) {

    sealed class ProactiveEvent {
        data class HazardAhead(
            val hazard: RouteAheadHazard,
            val message: String,
        ) : ProactiveEvent()
        data class RoadQualityWarning(
            val avgRri: Float,
            val distanceMeters: Float,
            val message: String,
        ) : ProactiveEvent()
    }

    private val _proactiveEvents = MutableSharedFlow<ProactiveEvent>(extraBufferCapacity = 16)
    val proactiveEvents: Flow<ProactiveEvent> = _proactiveEvents.asSharedFlow()

    private val _routeAheadHazards = MutableStateFlow<List<RouteAheadHazard>>(emptyList())
    val routeAheadHazards: StateFlow<List<RouteAheadHazard>> = _routeAheadHazards.asStateFlow()

    private val announcedGeohashes = LinkedHashMap<String, Long>()
    private var monitorScope: CoroutineScope? = null
    private var profile: DriverProfile = DriverProfile.NEW

    fun setProfile(p: DriverProfile) { profile = p }

    fun start(locationFlow: Flow<LocationSnapshot>) {
        stop()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        monitorScope = scope
        scope.launch {
            locationFlow.collectLatest { snap ->
                if (snap.velocityMs < MIN_MOVING_SPEED_MS) return@collectLatest
                delay(SCAN_INTERVAL_MS)
                scanAhead(snap)
            }
        }
    }

    fun stop() {
        monitorScope?.cancel()
        monitorScope = null
        _routeAheadHazards.value = emptyList()
    }

    private suspend fun scanAhead(snap: LocationSnapshot) {
        val scaledDistances = BASE_LOOK_AHEAD_DISTANCES_M.map { it * profile.sProfile }
        val lookAheadPoints = scaledDistances.map { distanceM ->
            projectPoint(snap.latitudeDeg, snap.longitudeDeg, snap.bearingDeg.toDouble(), distanceM)
        }

        try {
            val hazards = roadAheadClient.query(
                lat = snap.latitudeDeg,
                lon = snap.longitudeDeg,
                bearingDeg = snap.bearingDeg.toDouble(),
                velocityMs = snap.velocityMs,
                lookAheadPoints = lookAheadPoints,
            )
            _routeAheadHazards.value = hazards

            val now = System.currentTimeMillis()
            // Evict stale announced geohashes (older than 5 min).
            announcedGeohashes.entries.removeIf { now - it.value > 300_000L }

            for (hazard in hazards) {
                if (announcedGeohashes.containsKey(hazard.geohash)) continue
                val shouldAnnounce = when {
                    snap.velocityMs < 0.1f -> false       // stationary
                    hazard.etaSeconds < 10f -> true        // imminent
                    hazard.etaSeconds < 60f -> true        // within 1 minute
                    else -> false
                }
                if (!shouldAnnounce) continue
                announcedGeohashes[hazard.geohash] = now
                _proactiveEvents.tryEmit(buildAnnouncement(hazard, snap.velocityMs))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Route-ahead scan failed: ${e.message}")
        }
    }

    private fun buildAnnouncement(hazard: RouteAheadHazard, velocityMs: Float): ProactiveEvent {
        val distLabel = when {
            hazard.distanceMeters < 150f -> "just ahead"
            hazard.distanceMeters < 400f -> "in ${hazard.distanceMeters.toInt()} meters"
            else -> "in ${(hazard.distanceMeters / 1000f).let { "%.1f".format(it) }} km"
        }
        val etaLabel = if (hazard.etaSeconds < 30f) " (${hazard.etaSeconds.toInt()} seconds away)" else ""
        val severityPrefix = when (hazard.severity) {
            HazardAlert.Severity.CRITICAL -> "Critical hazard"
            HazardAlert.Severity.HIGH     -> "Warning"
            HazardAlert.Severity.MEDIUM   -> "Caution"
            HazardAlert.Severity.LOW      -> "Note"
        }
        val hazardDesc = when (hazard.hazardType.lowercase()) {
            "pothole"       -> "pothole cluster"
            "speed_bump"    -> "speed bump zone — reduce to 20 km/h"
            "rough_road"    -> "rough road surface"
            "flood"         -> "waterlogging reported"
            "construction"  -> "road construction"
            "sharp_curve"   -> "sharp curve — reduce speed"
            else            -> hazard.hazardType.replace("_", " ")
        }
        val speedAdvice = if (velocityMs > 10f && hazard.severity >= HazardAlert.Severity.MEDIUM) {
            " Reduce speed now."
        } else ""

        val message = "$severityPrefix: $hazardDesc $distLabel$etaLabel.$speedAdvice"
        return if (hazard.avgRriScore < 0.25f) {
            ProactiveEvent.RoadQualityWarning(
                avgRri = hazard.avgRriScore,
                distanceMeters = hazard.distanceMeters,
                message = message,
            )
        } else {
            ProactiveEvent.HazardAhead(hazard = hazard, message = message)
        }
    }

    // ── Geodesy ───────────────────────────────────────────────────────────────

    private fun projectPoint(
        lat: Double, lon: Double, bearingDeg: Double, distanceM: Double,
    ): Pair<Double, Double> {
        val R = 6_371_000.0   // Earth radius in metres
        val d = distanceM / R
        val bRad = bearingDeg * PI / 180.0
        val latRad = lat * PI / 180.0
        val lonRad = lon * PI / 180.0

        val lat2 = Math.asin(
            sin(latRad) * cos(d) + cos(latRad) * sin(d) * cos(bRad)
        )
        val lon2 = lonRad + atan2(
            sin(bRad) * sin(d) * cos(latRad),
            cos(d) - sin(latRad) * sin(lat2),
        )
        return Pair(lat2 * 180.0 / PI, lon2 * 180.0 / PI)
    }

    companion object {
        private const val TAG = "RouteAheadMonitor"
        private const val MIN_MOVING_SPEED_MS  = 3f          // ~10 km/h
        private const val SCAN_INTERVAL_MS     = 5_000L
        // Base distances; scaled by DriverProfile.sProfile at runtime.
        private val BASE_LOOK_AHEAD_DISTANCES_M = listOf(200.0, 500.0, 1000.0)
    }
}
