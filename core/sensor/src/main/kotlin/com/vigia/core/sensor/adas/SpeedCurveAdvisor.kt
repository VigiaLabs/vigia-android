package com.vigia.core.sensor.adas

import android.util.Log
import com.vigia.core.model.DriverProfile
import com.vigia.core.model.LocationSnapshot
import com.vigia.core.model.RoadGeometryAdvisory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Emits proactive TTS advisories from OSM-derived road geometry (speed limits + curves).
 *
 * Geometry is sourced from the /v1/road-ahead response `road_geometry` array.
 * Each advisory fires once, at a profile-scaled lead distance from the feature.
 *
 *   EXPERT  (0.5×) — warns when 50% of base distance remains
 *   NEW     (1.5×) — warns when 150% of base distance remains (earlier)
 *   ELDERLY (3.0×) — warns when 300% of base distance remains (much earlier)
 *
 * Base trigger distance = advisory.distanceMeters (i.e., fire when within that distance).
 * With sProfile scaling: fire when distance < advisory.distanceMeters × sProfile.
 *
 * Suppresses announcements for the same advisory key (type+distance bucket) within 2 min.
 */
@Singleton
class SpeedCurveAdvisor @Inject constructor() {

    data class GeometryEvent(val message: String, val advisory: RoadGeometryAdvisory)

    private val _events = MutableSharedFlow<GeometryEvent>(extraBufferCapacity = 16)
    val events: Flow<GeometryEvent> = _events.asSharedFlow()

    private var profile: DriverProfile = DriverProfile.NEW
    private var advisoryScope: CoroutineScope? = null

    // Key = "${type}_${distanceBucket}"; value = last announced ms
    private val announced = LinkedHashMap<String, Long>()

    fun setProfile(p: DriverProfile) { profile = p }

    /**
     * Feed new road_geometry from /v1/road-ahead response whenever RouteAheadMonitor fires.
     * [currentDistanceMs] is the current distance to each advisory's origin (passed from caller).
     */
    fun onGeometryUpdate(
        advisories: List<RoadGeometryAdvisory>,
        currentVelocityMs: Float,
    ) {
        val now = System.currentTimeMillis()
        // Evict stale announcement records (> 2 min)
        announced.entries.removeIf { now - it.value > SUPPRESS_WINDOW_MS }

        for (adv in advisories) {
            val triggerDistance = adv.distanceMeters * profile.sProfile
            // Only announce if the feature is within the profile-scaled trigger distance
            if (adv.distanceMeters > triggerDistance) continue

            val key = "${adv.type.name}_${(adv.distanceMeters / 100).toInt()}"
            if (announced.containsKey(key)) continue
            announced[key] = now

            val msg = buildMessage(adv, currentVelocityMs)
            Log.d(TAG, "Geometry advisory: $msg")
            _events.tryEmit(GeometryEvent(message = msg, advisory = adv))
        }
    }

    private fun buildMessage(adv: RoadGeometryAdvisory, currentVelocityMs: Float): String {
        val distLabel = when {
            adv.distanceMeters < 150f -> "just ahead"
            adv.distanceMeters < 500f -> "in ${adv.distanceMeters.toInt()} meters"
            else -> "in ${(adv.distanceMeters / 1000f).let { "%.1f".format(it) }} kilometers"
        }

        return when (adv.type) {
            RoadGeometryAdvisory.Type.SPEED_LIMIT -> {
                val currentKmh = (currentVelocityMs * 3.6f).toInt()
                val limitKmh   = adv.valuKmh.toInt()
                if (currentKmh > limitKmh) {
                    "Speed limit drops to $limitKmh $distLabel — ease off to ${limitKmh} km/h."
                } else {
                    "Speed limit is $limitKmh km/h $distLabel."
                }
            }
            RoadGeometryAdvisory.Type.CURVE -> {
                val dirLabel = if (adv.directionLabel.isNotBlank()) " ${adv.directionLabel}" else ""
                val currentKmh = (currentVelocityMs * 3.6f).toInt()
                val advisedKmh = adv.valuKmh.toInt()
                if (currentKmh > advisedKmh + 5) {
                    "Sharp$dirLabel curve $distLabel — reduce to $advisedKmh km/h now."
                } else {
                    "Curve$dirLabel ahead $distLabel — advised speed $advisedKmh km/h."
                }
            }
        }
    }

    companion object {
        private const val TAG = "SpeedCurveAdvisor"
        private const val SUPPRESS_WINDOW_MS = 2 * 60 * 1_000L
    }
}
