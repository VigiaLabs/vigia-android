package com.vigia.feature.maps

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vigia.core.model.MapLayer
import com.vigia.feature.maps.components.LayerToggleColumn
import com.vigia.feature.maps.components.MapsBottomSheet
import com.vigia.feature.maps.components.MapsSearchBar
import com.vigia.feature.maps.components.SensorStatusStrip
import com.vigia.feature.maps.components.VigiaMapView
import com.vigia.feature.maps.layers.GeohashLayer
import com.vigia.feature.maps.layers.HazardLayer
import com.vigia.feature.maps.layers.MaintenanceLayer
import com.vigia.feature.maps.layers.RouteLayer
import com.vigia.feature.maps.layers.TracePlaybackLayer

private val PeekHeight  = 120.dp
private val OrbSize     = 52.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapsScreen(
    viewModel: MapsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarState  = remember { SnackbarHostState() }
    val sheetScaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(initialValue = SheetValue.PartiallyExpanded),
    )

    LaunchedEffect(uiState.error) {
        uiState.error?.let { msg ->
            snackbarState.showSnackbar(msg)
            viewModel.clearError()
        }
    }

    BottomSheetScaffold(
        scaffoldState = sheetScaffoldState,
        sheetPeekHeight = PeekHeight,
        containerColor  = Color(0xFF0A0A0F),
        sheetContainerColor = Color(0xEA0A0A0F),
        sheetDragHandle = null,
        snackbarHost = {
            SnackbarHost(snackbarState) { data ->
                Snackbar(
                    action = { TextButton(onClick = { data.dismiss() }) { Text("OK") } },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ) { Text(data.visuals.message) }
            }
        },
        sheetContent = {
            MapsBottomSheet(
                state          = uiState,
                onTabSelect    = viewModel::setSheetTab,
                onHazardSelect = viewModel::selectHazard,
                onRequestRoute = { lat, lng ->
                    viewModel.requestRoute(com.vigia.core.model.LatLng(lat, lng))
                },
                onStartTrace   = viewModel::startTracePlayback,
                onStopTrace    = viewModel::stopTracePlayback,
                onScrubTrace   = viewModel::scrubTrace,
            )
        },
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            val density    = LocalDensity.current
            val canvasW    = with(density) { maxWidth.toPx() }
            val canvasH    = with(density) { maxHeight.toPx() }
            val mapCenter  = uiState.mapCenter.let { it.lat to it.lng }

            // ── Full-bleed map ────────────────────────────────────────────────
            VigiaMapView(
                center       = uiState.mapCenter,
                zoom         = uiState.mapZoom,
                userLocation = uiState.userLocation,
                modifier     = Modifier.fillMaxSize(),
            )

            // ── Intelligence overlays (all Canvas, zero recomposition on pan) ─

            if (MapLayer.GEOHASH in uiState.activeLayers && uiState.geohashCells.isNotEmpty()) {
                GeohashLayer(
                    cells          = uiState.geohashCells,
                    mapCenter      = mapCenter,
                    mapZoom        = uiState.mapZoom,
                    canvasWidthPx  = canvasW,
                    canvasHeightPx = canvasH,
                    modifier       = Modifier.fillMaxSize(),
                )
            }

            if (MapLayer.HAZARDS in uiState.activeLayers && uiState.hazards.isNotEmpty()) {
                HazardLayer(
                    hazards        = uiState.hazards,
                    mapCenter      = mapCenter,
                    mapZoom        = uiState.mapZoom,
                    canvasWidthPx  = canvasW,
                    canvasHeightPx = canvasH,
                    onHazardTap    = viewModel::selectHazard,
                    modifier       = Modifier.fillMaxSize(),
                )
            }

            if (MapLayer.ROUTE in uiState.activeLayers && uiState.activeRoute != null) {
                RouteLayer(
                    route          = uiState.activeRoute!!,
                    mapCenter      = mapCenter,
                    mapZoom        = uiState.mapZoom,
                    canvasWidthPx  = canvasW,
                    canvasHeightPx = canvasH,
                    modifier       = Modifier.fillMaxSize(),
                )
            }

            if (MapLayer.MAINTENANCE in uiState.activeLayers && uiState.maintenancePois.isNotEmpty()) {
                MaintenanceLayer(
                    pois           = uiState.maintenancePois,
                    mapCenter      = mapCenter,
                    mapZoom        = uiState.mapZoom,
                    canvasWidthPx  = canvasW,
                    canvasHeightPx = canvasH,
                    onPoiTap       = { /* future: show maintenance detail sheet */ },
                    modifier       = Modifier.fillMaxSize(),
                )
            }

            if (MapLayer.TRACES in uiState.activeLayers && uiState.traceFrames.size > 1) {
                TracePlaybackLayer(
                    frames         = uiState.traceFrames,
                    playbackIndex  = uiState.tracePlaybackIndex,
                    mapCenter      = mapCenter,
                    mapZoom        = uiState.mapZoom,
                    canvasWidthPx  = canvasW,
                    canvasHeightPx = canvasH,
                    modifier       = Modifier.fillMaxSize(),
                )
            }

            // ── Sensor strip ──────────────────────────────────────────────────
            SensorStatusStrip(
                state    = uiState.sensorStrip,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding(),
            )

            // ── Search bar ────────────────────────────────────────────────────
            MapsSearchBar(
                query            = uiState.searchQuery,
                results          = uiState.searchResults,
                isActive         = uiState.isSearchActive,
                onQueryChange    = viewModel::onSearchQueryChange,
                onResultSelected = { place -> viewModel.onSearchResultSelected(place.lat, place.lng) },
                onDismiss        = viewModel::dismissSearch,
                modifier         = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 16.dp, end = 64.dp, top = 56.dp)
                    .statusBarsPadding(),
            )

            // ── Layer toggles ─────────────────────────────────────────────────
            AnimatedVisibility(
                visible = !uiState.isSearchActive,
                enter   = fadeIn(),
                exit    = fadeOut(),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp),
            ) {
                LayerToggleColumn(
                    activeLayers = uiState.activeLayers,
                    onToggle     = viewModel::toggleLayer,
                )
            }

            // ── AI Orb (Maps-local floating action button) ────────────────────
            MapsOrbFab(
                hasCritical  = uiState.hazards.any { it.severity == com.vigia.core.model.HazardAlert.Severity.CRITICAL },
                bleConnected = uiState.sensorStrip.bleConnected,
                isLoading    = uiState.routeLoading,
                modifier     = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 16.dp, bottom = PeekHeight + 16.dp),
            )
        }
    }
}

// ── Maps-local AI Orb FAB ─────────────────────────────────────────────────────
// Matches the AiOrb visual in feature:copilot without creating a circular module dep.

@Composable
private fun MapsOrbFab(
    hasCritical: Boolean,
    bleConnected: Boolean,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    val inf = rememberInfiniteTransition(label = "maps_orb")

    val flowFast by inf.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(6_000, easing = androidx.compose.animation.core.LinearEasing), RepeatMode.Restart),
        label = "flow_fast",
    )
    val breathe by inf.animateFloat(
        initialValue = 0.95f, targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(2_400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathe",
    )

    val rimA = when {
        hasCritical   -> Color(0xFFEF4444)
        !bleConnected -> Color(0xFF6B7280)
        isLoading     -> Color(0xFF8F6CF6)
        else          -> Color(0xFFFF3D8F)
    }
    val rimB = when {
        hasCritical   -> Color(0xFFF97316)
        !bleConnected -> Color(0xFF6B7280)
        else          -> Color(0xFF8F6CF6)
    }
    val rimC = when {
        hasCritical   -> Color(0xFFEF4444)
        !bleConnected -> Color(0xFF6B7280)
        else          -> Color(0xFF4CC2FF)
    }

    Canvas(
        modifier = modifier
            .size(OrbSize)
            .graphicsLayer { scaleX = breathe; scaleY = breathe },
    ) {
        val center  = Offset(size.width / 2f, size.height / 2f)
        val rOuter  = size.minDimension / 2f
        val rSphere = rOuter * 0.68f

        // Halo
        drawCircle(
            brush  = Brush.radialGradient(
                listOf(rimB.copy(alpha = 0.28f), Color.Transparent),
                center = center, radius = rOuter,
            ),
            center = center, radius = rOuter,
        )

        // Sphere body
        drawCircle(
            brush  = Brush.radialGradient(
                listOf(Color(0xFF332960), Color(0xFF332960), Color(0xFF07050F)),
                center = Offset(center.x - rSphere * 0.30f, center.y - rSphere * 0.38f),
                radius = rSphere * 1.9f,
            ),
            center = center, radius = rSphere,
        )

        // Specular
        drawCircle(
            brush  = Brush.radialGradient(
                listOf(Color.White.copy(alpha = 0.20f), Color.Transparent),
                center = Offset(center.x - rSphere * 0.34f, center.y - rSphere * 0.44f),
                radius = rSphere * 0.85f,
            ),
            center = center, radius = rSphere,
        )

        // Rim lights
        rotate(flowFast, pivot = center) {
            drawCircle(
                brush  = Brush.sweepGradient(
                    0.0f to Color.Transparent,
                    0.2f to rimA.copy(alpha = 0.9f),
                    0.5f to rimB.copy(alpha = 0.9f),
                    0.8f to rimC.copy(alpha = 0.9f),
                    1.0f to Color.Transparent,
                ),
                center = center, radius = rSphere,
                style  = Stroke(width = rSphere * 0.12f),
            )
        }
    }
}
