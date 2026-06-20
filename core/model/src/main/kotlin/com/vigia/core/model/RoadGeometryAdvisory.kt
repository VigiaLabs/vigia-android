package com.vigia.core.model

/**
 * OSM-derived road geometry advisory for a segment ahead.
 * Sourced from Overpass API (server-side cached, 24h TTL) and included in /v1/road-ahead response.
 */
data class RoadGeometryAdvisory(
    val type: Type,
    val distanceMeters: Float,
    val valuKmh: Float,          // speed_limit value or curve advised_kmh
    val directionLabel: String,  // "left" / "right" / "" for speed limits
) {
    enum class Type { SPEED_LIMIT, CURVE }
}
