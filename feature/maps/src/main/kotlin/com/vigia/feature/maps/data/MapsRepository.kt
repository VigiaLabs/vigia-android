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
import kotlinx.coroutines.flow.Flow

interface MapsRepository {
    fun hazardsFlow(): Flow<List<HazardAlert>>
    suspend fun resolveGeohash(lat: Double, lng: Double): List<GeohashCell>
    suspend fun searchPlaces(query: String, lat: Double?, lng: Double?): List<SearchPlace>
    suspend fun generateRoute(originLat: Double, originLng: Double, destLat: Double, destLng: Double): BezierRoute
    suspend fun getMaintenanceQueue(): List<MaintenancePoi>
    suspend fun getEconomicMetrics(geohash: String): EconomicZone
    suspend fun getTraces(): List<TraceFrame>
}
