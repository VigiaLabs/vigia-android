package com.vigia.feature.maps.data

import com.vigia.core.model.BezierRoute
import com.vigia.core.model.EconomicZone
import com.vigia.core.model.GeohashCell
import com.vigia.core.model.HazardAlert
import com.vigia.core.model.LatLng
import com.vigia.core.model.LocationSnapshot
import com.vigia.core.model.MaintenancePoi
import com.vigia.core.model.SearchPlace
import com.vigia.core.model.TraceFrame
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MapsRepositoryImpl @Inject constructor(
    private val sessionApi: SessionApiService,
    private val innovationApi: InnovationApiService,
    private val ingestionApi: IngestionApiService,
) : MapsRepository {

    // Poll every 30s; downstream collectors cancel/restart with the scope.
    override fun hazardsFlow(): Flow<List<HazardAlert>> = flow {
        while (true) {
            runCatching { ingestionApi.getHazards() }
                .onSuccess { resp -> emit(resp.hazards.map { it.toDomain() }) }
            delay(30_000)
        }
    }

    override suspend fun resolveGeohash(lat: Double, lng: Double): List<GeohashCell> =
        runCatching {
            val resp = sessionApi.resolveGeohash(GeohashResolveRequest(lat, lng))
            resp.neighbours.map { it.toDomain() }
        }.getOrDefault(emptyList())

    override suspend fun searchPlaces(query: String, lat: Double?, lng: Double?): List<SearchPlace> =
        runCatching {
            sessionApi.searchPlaces(PlacesSearchRequest(query, lat, lng)).places.map { it.toDomain() }
        }.getOrDefault(emptyList())

    override suspend fun generateRoute(
        originLat: Double, originLng: Double,
        destLat: Double, destLng: Double,
    ): BezierRoute =
        runCatching {
            innovationApi.generateRoute(RouteRequest(originLat, originLng, destLat, destLng)).toDomain()
        }.getOrElse {
            BezierRoute(
                waypoints        = listOf(LatLng(originLat, originLng), LatLng(destLat, destLng)),
                estimatedSeconds = 0,
                hazardsAvoided   = 0,
                confidenceScore  = 0f,
            )
        }

    override suspend fun getMaintenanceQueue(): List<MaintenancePoi> =
        runCatching {
            innovationApi.getMaintenanceQueue().items.map { it.toDomain() }
        }.getOrDefault(emptyList())

    override suspend fun getEconomicMetrics(geohash: String): EconomicZone =
        runCatching {
            innovationApi.getEconomicMetrics(geohash).toDomain()
        }.getOrElse {
            EconomicZone(geohash, "unknown", 0f, "unknown", 0)
        }

    override suspend fun getTraces(): List<TraceFrame> =
        runCatching {
            ingestionApi.getTraces().frames.map { it.toDomain() }
        }.getOrDefault(emptyList())
}

// ── DTO → Domain mappers ──────────────────────────────────────────────────────

private fun HazardDto.toDomain() = HazardAlert(
    id               = id,
    severity         = enumValueOfOrDefault(severity, HazardAlert.Severity.HIGH),
    messageText      = messageText,
    timestampMs      = timestampMs,
    locationSnapshot = if (lat != null && lng != null) LocationSnapshot(
        latitudeDeg    = lat,
        longitudeDeg   = lng,
        accuracyMeters = accuracyMeters,
        bearingDeg     = bearingDeg,
        velocityMs     = velocityMs,
        timestampMs    = timestampMs,
    ) else null,
)

private fun GeohashCellDto.toDomain() = GeohashCell(
    hash          = hash,
    latMin        = latMin,
    latMax        = latMax,
    lngMin        = lngMin,
    lngMax        = lngMax,
    hazardDensity = hazardDensity,
)

private fun PlaceDto.toDomain() = SearchPlace(
    id      = id,
    name    = name,
    address = address,
    lat     = lat,
    lng     = lng,
    geohash = geohash,
)

private fun RouteResponse.toDomain() = BezierRoute(
    waypoints       = waypoints.map { LatLng(it.lat, it.lng) },
    estimatedSeconds = estimatedSeconds,
    hazardsAvoided  = hazardsAvoided,
    confidenceScore = confidenceScore,
)

private fun MaintenanceItemDto.toDomain() = MaintenancePoi(
    id       = id,
    location = LocationSnapshot(
        latitudeDeg    = lat,
        longitudeDeg   = lng,
        accuracyMeters = accuracyMeters,
        bearingDeg     = bearingDeg,
        velocityMs     = velocityMs,
        timestampMs    = timestampMs,
    ),
    priority   = enumValueOfOrDefault(priority, MaintenancePoi.Priority.MEDIUM),
    category   = category,
    reportedMs = reportedMs,
)

private fun EconomicMetricsResponse.toDomain() = EconomicZone(
    geohash          = geohash,
    zoneType         = zoneType,
    investmentScore  = investmentScore,
    complianceStatus = complianceStatus,
    populationDensity = populationDensity,
)

private fun TraceFrameDto.toDomain() = TraceFrame(
    location = LocationSnapshot(
        latitudeDeg    = lat,
        longitudeDeg   = lng,
        accuracyMeters = accuracyMeters,
        bearingDeg     = bearingDeg,
        velocityMs     = velocityMs,
        timestampMs    = timestampMs,
    ),
    rriScore    = rriScore,
    timestampMs = timestampMs,
)

// Returns the matching enum constant, or [default] if the server sends an unrecognised value.
// Prevents a single unknown enum string from dropping the entire response list via valueOf().
private inline fun <reified T : Enum<T>> enumValueOfOrDefault(name: String, default: T): T =
    enumValues<T>().firstOrNull { it.name == name.uppercase() } ?: default
