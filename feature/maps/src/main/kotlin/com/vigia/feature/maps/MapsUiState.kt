package com.vigia.feature.maps

import androidx.compose.runtime.Immutable
import com.vigia.core.model.BezierRoute
import com.vigia.core.model.EconomicZone
import com.vigia.core.model.GeohashCell
import com.vigia.core.model.HazardAlert
import com.vigia.core.model.LatLng
import com.vigia.core.model.LocationSnapshot
import com.vigia.core.model.MaintenancePoi
import com.vigia.core.model.MapLayer
import com.vigia.core.model.SearchPlace
import com.vigia.core.model.TraceFrame

@Immutable
data class MapsUiState(
    val userLocation: LocationSnapshot? = null,
    val activeLayers: Set<MapLayer> = setOf(MapLayer.HAZARDS, MapLayer.ROUTE),
    val hazards: List<HazardAlert> = emptyList(),
    val geohashCells: List<GeohashCell> = emptyList(),
    val activeRoute: BezierRoute? = null,
    val maintenancePois: List<MaintenancePoi> = emptyList(),
    val economicZones: List<EconomicZone> = emptyList(),
    val traceFrames: List<TraceFrame> = emptyList(),
    val tracePlaybackIndex: Int = 0,
    val isTracePlaybackActive: Boolean = false,
    val sensorStrip: SensorStripState = SensorStripState(),
    val searchQuery: String = "",
    val searchResults: List<SearchPlace> = emptyList(),
    val isSearchActive: Boolean = false,
    val selectedHazard: HazardAlert? = null,
    val activeSheetTab: SheetTab = SheetTab.ALERTS,
    val routeLoading: Boolean = false,
    val mapCenter: LatLng = LatLng(0.0, 0.0),
    val mapZoom: Double = 15.0,
    val error: String? = null,
)

@Immutable
data class SensorStripState(
    val bleConnected: Boolean = false,
    val rriScore: Float = 0f,
    val confidenceLabel: String = "Connecting…",
    val velocityMs: Float = 0f,
    val accuracyMeters: Float = 0f,
)

enum class SheetTab { ALERTS, ROUTE, ZONES, TRACES }
