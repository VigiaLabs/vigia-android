package com.vigia.core.network.live

import android.util.Log
import com.vigia.core.model.HazardAlert
import com.vigia.core.model.RoadGeometryAdvisory
import com.vigia.core.model.RouteAheadHazard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Queries the VIGIA cloud for hazards along the user's route ahead.
 *
 * POST /v1/road-ahead
 * Body: { lat, lon, bearing_deg, velocity_ms, look_ahead: [{lat, lon}] }
 * Response: { hazards: [{ geohash, distance_m, hazard_type, severity,
 *                         avg_rri, report_count, last_seen_ms, eta_s }] }
 *
 * The backend Lambda queries HazardsTable by geohash prefix for each
 * look-ahead point, merges nearby cells, and returns the deduped list
 * sorted by distance ascending. Only active (non-expired) hazards are returned.
 */
@Singleton
class RoadAheadClient @Inject constructor(
    @Named("VigiaOkHttpClient") private val okHttpClient: OkHttpClient,
    @Named("VigiaApiBaseUrl")   private val baseUrl: String,
) {
    data class RoadAheadResult(
        val hazards: List<RouteAheadHazard>,
        val roadGeometry: List<RoadGeometryAdvisory>,
    )

    suspend fun query(
        lat: Double,
        lon: Double,
        bearingDeg: Double,
        velocityMs: Float,
        lookAheadPoints: List<Pair<Double, Double>>,
    ): RoadAheadResult = withContext(Dispatchers.IO) {
        val lookAheadArr = JSONArray().also { arr ->
            lookAheadPoints.forEach { (ptLat, ptLon) ->
                arr.put(JSONObject().apply { put("lat", ptLat); put("lon", ptLon) })
            }
        }
        val body = JSONObject().apply {
            put("lat",          lat)
            put("lon",          lon)
            put("bearing_deg",  bearingDeg)
            put("velocity_ms",  velocityMs)
            put("look_ahead",   lookAheadArr)
        }.toString().toRequestBody(JSON_MEDIA)

        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/v1/road-ahead")
            .post(body)
            .build()

        runCatching {
            okHttpClient.newCall(request).execute().use { response ->
                val empty = RoadAheadResult(emptyList(), emptyList())
                if (!response.isSuccessful) return@runCatching empty
                val raw = response.body?.string() ?: return@runCatching empty
                val root = JSONObject(raw)
                RoadAheadResult(
                    hazards      = parseHazards(root.optJSONArray("hazards")),
                    roadGeometry = parseGeometry(root.optJSONArray("road_geometry")),
                )
            }
        }.onFailure { Log.w(TAG, "road-ahead query failed: ${it.message}") }
            .getOrDefault(RoadAheadResult(emptyList(), emptyList()))
    }

    private fun parseHazards(arr: JSONArray?): List<RouteAheadHazard> {
        if (arr == null) return emptyList()
        return parseHazardsArray(arr)
    }

    private fun parseHazardsArray(arr: JSONArray): List<RouteAheadHazard> =
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            RouteAheadHazard(
                geohash       = o.optString("geohash"),
                distanceMeters = o.optDouble("distance_m", 9999.0).toFloat(),
                hazardType    = o.optString("hazard_type", "unknown"),
                severity      = parseSeverity(o.optString("severity", "LOW")),
                avgRriScore   = o.optDouble("avg_rri", 0.5).toFloat(),
                reportCount   = o.optInt("report_count", 1),
                lastSeenMs    = o.optLong("last_seen_ms"),
                etaSeconds    = o.optDouble("eta_s", 999.0).toFloat(),
            )
        }

    private fun parseGeometry(arr: JSONArray?): List<RoadGeometryAdvisory> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val type = when (o.optString("type")) {
                "speed_limit" -> RoadGeometryAdvisory.Type.SPEED_LIMIT
                "curve"       -> RoadGeometryAdvisory.Type.CURVE
                else          -> return@mapNotNull null
            }
            RoadGeometryAdvisory(
                type            = type,
                distanceMeters  = o.optDouble("distance_m", 9999.0).toFloat(),
                valuKmh         = o.optDouble("value_kmh", o.optDouble("advised_kmh", 0.0)).toFloat(),
                directionLabel  = o.optString("direction", ""),
            )
        }.sortedBy { it.distanceMeters }
    }

    private fun parseSeverity(s: String): HazardAlert.Severity =
        runCatching { HazardAlert.Severity.valueOf(s.uppercase()) }
            .getOrDefault(HazardAlert.Severity.LOW)

    companion object {
        private const val TAG = "RoadAheadClient"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
