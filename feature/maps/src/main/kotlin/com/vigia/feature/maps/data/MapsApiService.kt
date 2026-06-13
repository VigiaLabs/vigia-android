package com.vigia.feature.maps.data

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

// ── Session API DTOs ──────────────────────────────────────────────────────────

data class GeohashResolveRequest(val lat: Double, val lng: Double, val precision: Int = 6)

data class GeohashResolveResponse(
    val hash: String,
    val neighbours: List<GeohashCellDto>,
)

data class GeohashCellDto(
    val hash: String,
    val latMin: Double,
    val latMax: Double,
    val lngMin: Double,
    val lngMax: Double,
    val hazardDensity: Float = 0f,
)

data class PlacesSearchRequest(val query: String, val lat: Double?, val lng: Double?, val limit: Int = 10)

data class PlacesSearchResponse(val places: List<PlaceDto>)

data class PlaceDto(
    val id: String,
    val name: String,
    val address: String,
    val lat: Double,
    val lng: Double,
    val geohash: String,
)

// ── Session API ───────────────────────────────────────────────────────────────

interface SessionApiService {

    @POST("geohash/resolve")
    suspend fun resolveGeohash(@Body request: GeohashResolveRequest): GeohashResolveResponse

    @POST("places/search")
    suspend fun searchPlaces(@Body request: PlacesSearchRequest): PlacesSearchResponse
}

// ── Innovation API DTOs ───────────────────────────────────────────────────────

data class MaintenanceQueueResponse(val items: List<MaintenanceItemDto>)

data class MaintenanceItemDto(
    val id: String,
    val lat: Double,
    val lng: Double,
    val accuracyMeters: Float = 10f,
    val bearingDeg: Float = 0f,
    val velocityMs: Float = 0f,
    val timestampMs: Long,
    val priority: String,
    val category: String,
    val reportedMs: Long,
)

data class EconomicMetricsResponse(
    val geohash: String,
    val zoneType: String,
    val investmentScore: Float,
    val complianceStatus: String,
    val populationDensity: Int,
)

data class RouteRequest(
    val originLat: Double,
    val originLng: Double,
    val destLat: Double,
    val destLng: Double,
    val avoidHazards: Boolean = true,
)

data class RouteResponse(
    val waypoints: List<WaypointDto>,
    val estimatedSeconds: Int,
    val hazardsAvoided: Int,
    val confidenceScore: Float,
)

data class WaypointDto(val lat: Double, val lng: Double)

// ── Innovation API ────────────────────────────────────────────────────────────

interface InnovationApiService {

    @GET("maintenance/queue")
    suspend fun getMaintenanceQueue(): MaintenanceQueueResponse

    @GET("economic/metrics")
    suspend fun getEconomicMetrics(@Query("geohash") geohash: String): EconomicMetricsResponse

    @POST("routing-agent/branch")
    suspend fun generateRoute(@Body request: RouteRequest): RouteResponse
}

// ── Ingestion API DTOs ────────────────────────────────────────────────────────

data class HazardsResponse(val hazards: List<HazardDto>)

data class HazardDto(
    val id: String,
    val severity: String,
    val messageText: String,
    val timestampMs: Long,
    val lat: Double?,
    val lng: Double?,
    val accuracyMeters: Float = 10f,
    val bearingDeg: Float = 0f,
    val velocityMs: Float = 0f,
)

data class TracesResponse(val frames: List<TraceFrameDto>)

data class TraceFrameDto(
    val lat: Double,
    val lng: Double,
    val accuracyMeters: Float = 10f,
    val bearingDeg: Float = 0f,
    val velocityMs: Float = 0f,
    val timestampMs: Long,
    val rriScore: Float,
)

interface IngestionApiService {

    @GET("hazards")
    suspend fun getHazards(): HazardsResponse

    @GET("traces")
    suspend fun getTraces(): TracesResponse
}
