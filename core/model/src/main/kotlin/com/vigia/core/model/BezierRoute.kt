package com.vigia.core.model

data class BezierRoute(
    val waypoints: List<LatLng>,
    val estimatedSeconds: Int,
    val hazardsAvoided: Int,
    val confidenceScore: Float,
)

data class LatLng(val lat: Double, val lng: Double)
