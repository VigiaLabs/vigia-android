package com.vigia.feature.maps.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vigia.core.model.HazardAlert
import com.vigia.core.model.MaintenancePoi
import com.vigia.core.model.TraceFrame
import com.vigia.feature.maps.MapsUiState
import com.vigia.feature.maps.SheetTab
import java.util.concurrent.TimeUnit

private val SheetBackground = Color(0xEA0A0A0F)
private val TabActive       = Color(0xFF3B82F6)
private val TabInactive     = Color.White.copy(alpha = 0.35f)
private val DividerColor    = Color.White.copy(alpha = 0.06f)

@Composable
fun MapsBottomSheet(
    state: MapsUiState,
    onTabSelect: (SheetTab) -> Unit,
    onHazardSelect: (HazardAlert) -> Unit,
    onRequestRoute: (Double, Double) -> Unit,
    onStartTrace: () -> Unit,
    onStopTrace: () -> Unit,
    onScrubTrace: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SheetBackground),
    ) {
        // Handle
        Box(
            modifier = Modifier
                .padding(vertical = 10.dp)
                .size(width = 36.dp, height = 4.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.18f))
                .align(Alignment.CenterHorizontally),
        )

        // Tab row
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 10.dp),
        ) {
            SheetTab.entries.forEach { tab ->
                val isActive = tab == state.activeSheetTab
                Surface(
                    shape  = RoundedCornerShape(8.dp),
                    color  = if (isActive) TabActive.copy(alpha = 0.18f) else Color.Transparent,
                    modifier = Modifier.clickable { onTabSelect(tab) },
                ) {
                    Text(
                        text     = tab.name.lowercase().replaceFirstChar { it.uppercase() },
                        style    = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                        color    = if (isActive) TabActive else TabInactive,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    )
                }
            }
        }

        HorizontalDivider(color = DividerColor, thickness = 0.5.dp)

        // Tab content — AnimatedContent prevents list rebuild on tab switch
        AnimatedContent(
            targetState = state.activeSheetTab,
            transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(160)) },
            label = "sheet_tab_content",
        ) { tab ->
            when (tab) {
                SheetTab.ALERTS -> AlertsTab(
                    hazards  = state.hazards,
                    userLat  = state.userLocation?.latitudeDeg,
                    userLng  = state.userLocation?.longitudeDeg,
                    selected = state.selectedHazard,
                    onSelect = onHazardSelect,
                )
                SheetTab.ROUTE  -> RouteTab(
                    route        = state.activeRoute,
                    loading      = state.routeLoading,
                    destination  = state.activeRoute?.waypoints?.lastOrNull(),
                    onRecalculate = { dest -> onRequestRoute(dest.lat, dest.lng) },
                )
                SheetTab.ZONES  -> ZonesTab(zones = state.economicZones)
                SheetTab.TRACES -> TracesTab(
                    frames              = state.traceFrames,
                    playbackIndex       = state.tracePlaybackIndex,
                    isPlaying           = state.isTracePlaybackActive,
                    onPlay              = onStartTrace,
                    onStop              = onStopTrace,
                    onScrub             = onScrubTrace,
                )
            }
        }
    }
}

// ── Alerts tab ────────────────────────────────────────────────────────────────

@Composable
private fun AlertsTab(
    hazards: List<HazardAlert>,
    userLat: Double?,
    userLng: Double?,
    selected: HazardAlert?,
    onSelect: (HazardAlert) -> Unit,
) {
    if (hazards.isEmpty()) {
        EmptyTabPlaceholder("No active hazard alerts in this area")
        return
    }

    LazyColumn(
        contentPadding      = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        items(hazards, key = { it.id }) { hazard ->
            AlertRow(
                hazard    = hazard,
                userLat   = userLat,
                userLng   = userLng,
                isSelected = hazard.id == selected?.id,
                onClick   = { onSelect(hazard) },
            )
        }
    }
}

@Composable
private fun AlertRow(
    hazard: HazardAlert,
    userLat: Double?,
    userLng: Double?,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val severityColor = when (hazard.severity) {
        HazardAlert.Severity.CRITICAL -> Color(0xFFEF4444)
        HazardAlert.Severity.HIGH     -> Color(0xFFF97316)
        HazardAlert.Severity.MEDIUM   -> Color(0xFFEAB308)
        HazardAlert.Severity.LOW      -> Color(0xFF22C55E)
    }

    val hazardLoc = hazard.locationSnapshot
    val distanceText = if (userLat != null && hazardLoc != null) {
        val dlat = userLat - hazardLoc.latitudeDeg
        val dlng = (userLng ?: 0.0) - hazardLoc.longitudeDeg
        val distM = Math.sqrt(dlat * dlat + dlng * dlng) * 111_000
        if (distM < 1_000) "${distM.toInt()}m" else "${"%.1f".format(distM / 1_000)}km"
    } else ""

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) severityColor.copy(alpha = 0.07f) else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 11.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = 4.dp, height = 36.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(severityColor),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text  = hazard.severity.name,
                style = MaterialTheme.typography.labelSmall,
                color = severityColor,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text  = hazard.messageText,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.75f),
            )
        }
        if (distanceText.isNotEmpty()) {
            Text(distanceText, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.35f))
        }
    }
}

// ── Route tab ─────────────────────────────────────────────────────────────────

@Composable
private fun RouteTab(
    route: com.vigia.core.model.BezierRoute?,
    loading: Boolean,
    destination: com.vigia.core.model.LatLng?,
    onRecalculate: (com.vigia.core.model.LatLng) -> Unit,
) {
    Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
        when {
            loading -> Text("Calculating route…", color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.bodyMedium)
            route == null -> EmptyTabPlaceholder("Search a destination to generate a route")
            else -> {
                val eta = "${TimeUnit.SECONDS.toMinutes(route.estimatedSeconds.toLong())} min"
                Text(eta, style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("${route.hazardsAvoided} hazards avoided · ${(route.confidenceScore * 100).toInt()}% confidence",
                    style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.5f))
                Spacer(Modifier.height(12.dp))
                if (destination != null) {
                    Button(onClick = { onRecalculate(destination) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Recalculate")
                    }
                }
            }
        }
    }
}

// ── Zones tab ─────────────────────────────────────────────────────────────────

@Composable
private fun ZonesTab(zones: List<com.vigia.core.model.EconomicZone>) {
    if (zones.isEmpty()) {
        EmptyTabPlaceholder("Enable the Economic layer to load zone data")
        return
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(zones, key = { it.geohash }) { zone ->
            Surface(color = Color.White.copy(alpha = 0.04f), shape = RoundedCornerShape(10.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(zone.zoneType, style = MaterialTheme.typography.titleSmall, color = Color.White)
                    Text("Investment score: ${"%.0f".format(zone.investmentScore * 100)}% · ${zone.complianceStatus}",
                        style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.5f))
                }
            }
        }
    }
}

// ── Traces tab ────────────────────────────────────────────────────────────────

@Composable
private fun TracesTab(
    frames: List<TraceFrame>,
    playbackIndex: Int,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    onScrub: (Int) -> Unit,
) {
    if (frames.isEmpty()) {
        EmptyTabPlaceholder("Enable the Traces layer to load recorded sessions")
        return
    }
    Column(modifier = Modifier.padding(horizontal = 16.dp).padding(top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${playbackIndex + 1} / ${frames.size} frames",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.5f),
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = if (isPlaying) onStop else onPlay) {
                Icon(
                    imageVector        = if (isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Stop" else "Play",
                    tint               = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(4.dp))
                Text(if (isPlaying) "Stop" else "Play", color = MaterialTheme.colorScheme.primary)
            }
        }
        Slider(
            value         = playbackIndex.toFloat(),
            onValueChange = { onScrub(it.toInt()) },
            valueRange    = 0f..(frames.size - 1).coerceAtLeast(1).toFloat(),
            steps         = (frames.size - 2).coerceAtLeast(0),
            modifier      = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun EmptyTabPlaceholder(text: String) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxWidth().height(72.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.35f))
    }
}
