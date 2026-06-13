package com.vigia.core.model

data class SearchPlace(
    val id: String,
    val name: String,
    val address: String,
    val lat: Double,
    val lng: Double,
    val geohash: String,
)
