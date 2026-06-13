package com.vigia.core.model

data class GeohashCell(
    val hash: String,
    val latMin: Double,
    val latMax: Double,
    val lngMin: Double,
    val lngMax: Double,
    val hazardDensity: Float,
)
