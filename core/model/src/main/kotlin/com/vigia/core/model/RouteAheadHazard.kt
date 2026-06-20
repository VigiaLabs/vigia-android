package com.vigia.core.model

data class RouteAheadHazard(
    val geohash: String,
    val distanceMeters: Float,
    val hazardType: String,
    val severity: HazardAlert.Severity,
    val avgRriScore: Float,
    val reportCount: Int,
    val lastSeenMs: Long,
    val etaSeconds: Float,
)
