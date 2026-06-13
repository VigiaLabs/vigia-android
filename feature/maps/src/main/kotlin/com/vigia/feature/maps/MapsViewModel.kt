package com.vigia.feature.maps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vigia.core.model.HazardAlert
import com.vigia.core.model.LatLng
import com.vigia.core.model.MapLayer
import com.vigia.core.sensor.context.ContextAggregator
import com.vigia.feature.maps.data.MapsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MapsViewModel @Inject constructor(
    private val repository: MapsRepository,
    private val contextAggregator: ContextAggregator,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapsUiState())
    val uiState: StateFlow<MapsUiState> = _uiState.asStateFlow()

    private var tracePlaybackJob: Job? = null

    init {
        observeLocation()
        observeHazards()
    }

    // ── Location + sensor ─────────────────────────────────────────────────────

    private fun observeLocation() {
        viewModelScope.launch {
            contextAggregator.searchContext.collect { ctx ->
                val snap  = ctx.location
                val rri   = ctx.rriScore.value
                val label = when {
                    rri > 0.8f -> "High Confidence"
                    rri > 0.5f -> "Medium Confidence"
                    else       -> "Low Confidence"
                }
                _uiState.update { s ->
                    s.copy(
                        userLocation = snap,
                        mapCenter    = LatLng(snap.latitudeDeg, snap.longitudeDeg),
                        sensorStrip  = s.sensorStrip.copy(
                            bleConnected    = true,
                            rriScore        = rri,
                            velocityMs      = snap.velocityMs,
                            accuracyMeters  = snap.accuracyMeters,
                            confidenceLabel = label,
                        ),
                    )
                }
                resolveGeohash(snap.latitudeDeg, snap.longitudeDeg)
            }
        }
    }

    // ── Hazards ───────────────────────────────────────────────────────────────

    private fun observeHazards() {
        viewModelScope.launch {
            repository.hazardsFlow().collect { hazards ->
                _uiState.update { it.copy(hazards = hazards) }
            }
        }
    }

    // ── Geohash ───────────────────────────────────────────────────────────────

    private fun resolveGeohash(lat: Double, lng: Double) {
        viewModelScope.launch {
            val cells = repository.resolveGeohash(lat, lng)
            _uiState.update { it.copy(geohashCells = cells) }
        }
    }

    // ── Route ─────────────────────────────────────────────────────────────────

    fun requestRoute(destination: LatLng) {
        val origin = _uiState.value.userLocation ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(routeLoading = true) }
            val route = repository.generateRoute(
                originLat = origin.latitudeDeg, originLng = origin.longitudeDeg,
                destLat   = destination.lat,    destLng   = destination.lng,
            )
            _uiState.update { it.copy(activeRoute = route, routeLoading = false, activeSheetTab = SheetTab.ROUTE) }
        }
    }

    // ── Layers ────────────────────────────────────────────────────────────────

    fun toggleLayer(layer: MapLayer) {
        _uiState.update { s ->
            val next = if (layer in s.activeLayers) s.activeLayers - layer else s.activeLayers + layer
            s.copy(activeLayers = next)
        }
        when (layer) {
            MapLayer.MAINTENANCE -> if (MapLayer.MAINTENANCE in _uiState.value.activeLayers) loadMaintenancePois()
            MapLayer.ECONOMIC    -> if (MapLayer.ECONOMIC in _uiState.value.activeLayers) loadEconomicMetrics()
            MapLayer.TRACES      -> if (MapLayer.TRACES in _uiState.value.activeLayers) loadTraces()
            else                 -> Unit
        }
    }

    private fun loadMaintenancePois() {
        viewModelScope.launch {
            val pois = repository.getMaintenanceQueue()
            _uiState.update { it.copy(maintenancePois = pois) }
        }
    }

    private fun loadEconomicMetrics() {
        viewModelScope.launch {
            val geohash = _uiState.value.geohashCells.firstOrNull()?.hash ?: return@launch
            val zone = repository.getEconomicMetrics(geohash)
            _uiState.update { it.copy(economicZones = listOf(zone)) }
        }
    }

    private fun loadTraces() {
        viewModelScope.launch {
            val frames = repository.getTraces()
            _uiState.update { it.copy(traceFrames = frames, tracePlaybackIndex = 0) }
        }
    }

    // ── Trace playback ────────────────────────────────────────────────────────

    fun startTracePlayback() {
        _uiState.update { it.copy(isTracePlaybackActive = true) }
        tracePlaybackJob = viewModelScope.launch {
            val total = _uiState.value.traceFrames.size
            while (_uiState.value.isTracePlaybackActive && _uiState.value.tracePlaybackIndex < total - 1) {
                delay(200)
                _uiState.update { it.copy(tracePlaybackIndex = it.tracePlaybackIndex + 1) }
            }
            _uiState.update { it.copy(isTracePlaybackActive = false) }
        }
    }

    fun stopTracePlayback() {
        tracePlaybackJob?.cancel()
        _uiState.update { it.copy(isTracePlaybackActive = false) }
    }

    fun scrubTrace(index: Int) {
        _uiState.update { it.copy(tracePlaybackIndex = index.coerceIn(0, it.traceFrames.size - 1)) }
    }

    // ── Hazard selection ──────────────────────────────────────────────────────

    fun selectHazard(hazard: HazardAlert) {
        _uiState.update { it.copy(selectedHazard = hazard, activeSheetTab = SheetTab.ALERTS) }
    }

    fun dismissHazard() {
        _uiState.update { it.copy(selectedHazard = null) }
    }

    // ── Search ────────────────────────────────────────────────────────────────

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query, isSearchActive = query.isNotEmpty()) }
        if (query.length >= 2) {
            viewModelScope.launch {
                val loc     = _uiState.value.userLocation
                val results = repository.searchPlaces(query, loc?.latitudeDeg, loc?.longitudeDeg)
                _uiState.update { it.copy(searchResults = results) }
            }
        } else {
            _uiState.update { it.copy(searchResults = emptyList()) }
        }
    }

    fun onSearchResultSelected(lat: Double, lng: Double) {
        _uiState.update { it.copy(isSearchActive = false, searchQuery = "", searchResults = emptyList()) }
        requestRoute(LatLng(lat, lng))
    }

    fun dismissSearch() {
        _uiState.update { it.copy(isSearchActive = false, searchQuery = "", searchResults = emptyList()) }
    }

    // ── Sheet ─────────────────────────────────────────────────────────────────

    fun setSheetTab(tab: SheetTab) {
        _uiState.update { it.copy(activeSheetTab = tab) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
