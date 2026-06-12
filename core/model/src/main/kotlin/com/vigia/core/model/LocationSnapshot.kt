package com.vigia.core.model

data class LocationSnapshot(
    val latitudeDeg: Double,
    val longitudeDeg: Double,
    val accuracyMeters: Float,
    val bearingDeg: Float,
    val velocityMs: Float,
    val timestampMs: Long,
)
