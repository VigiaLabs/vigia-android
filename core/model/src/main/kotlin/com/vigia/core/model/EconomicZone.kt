package com.vigia.core.model

data class EconomicZone(
    val geohash: String,
    val zoneType: String,
    val investmentScore: Float,
    val complianceStatus: String,
    val populationDensity: Int,
)
