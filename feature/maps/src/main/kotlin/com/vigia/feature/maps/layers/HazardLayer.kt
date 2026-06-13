package com.vigia.feature.maps.layers

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.vigia.core.model.HazardAlert
import com.vigia.core.model.LocationSnapshot

/**
 * Canvas overlay rendering hazard pins over the map.
 *
 * Coordinate mapping is performed relative to [mapCenter] and [mapZoom].
 * Pin sizes and pulse animations are render-thread driven via [graphicsLayer] and Canvas draws.
 */
@Composable
fun HazardLayer(
    hazards: List<HazardAlert>,
    mapCenter: Pair<Double, Double>,
    mapZoom: Double,
    canvasWidthPx: Float,
    canvasHeightPx: Float,
    onHazardTap: (HazardAlert) -> Unit,
    modifier: Modifier = Modifier,
) {
    val errorColor   = MaterialTheme.colorScheme.error
    val density      = LocalDensity.current
    val tapRadiusPx  = with(density) { 28.dp.toPx() }

    val inf          = rememberInfiniteTransition(label = "hazard_pulse")
    val pulseAlpha   by inf.animateFloat(
        initialValue = 0.7f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1_600, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "pulse_alpha",
    )
    val pulseScale   by inf.animateFloat(
        initialValue = 1f, targetValue = 2.2f,
        animationSpec = infiniteRepeatable(tween(1_600, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "pulse_scale",
    )

    val pinData = remember(hazards, mapCenter, mapZoom, canvasWidthPx, canvasHeightPx) {
        hazards.mapNotNull { hazard ->
            val loc = hazard.locationSnapshot ?: return@mapNotNull null
            val screen = latLngToScreen(loc.latitudeDeg, loc.longitudeDeg, mapCenter, mapZoom, canvasWidthPx, canvasHeightPx)
            Triple(hazard, screen, severityColor(hazard.severity, errorColor))
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(pinData) {
                detectTapGestures { tap ->
                    pinData.forEach { (hazard, screen, _) ->
                        val dx = tap.x - screen.x
                        val dy = tap.y - screen.y
                        if (dx * dx + dy * dy <= tapRadiusPx * tapRadiusPx) {
                            onHazardTap(hazard)
                            return@detectTapGestures
                        }
                    }
                }
            },
    ) {
        pinData.forEach { (_, screen, color) ->
            val baseRadius = 18f

            // Outer pulse ring
            drawCircle(
                color  = color.copy(alpha = pulseAlpha),
                center = screen,
                radius = baseRadius * pulseScale,
                style  = Stroke(width = 2f),
            )

            // Glow halo
            drawCircle(
                color  = color.copy(alpha = 0.12f),
                center = screen,
                radius = baseRadius * 1.6f,
            )

            // Solid pin circle
            drawCircle(
                color  = color.copy(alpha = 0.18f),
                center = screen,
                radius = baseRadius,
            )
            drawCircle(
                color  = color,
                center = screen,
                radius = baseRadius,
                style  = Stroke(width = 2.5f),
            )

            // Centre dot
            drawCircle(
                color  = color,
                center = screen,
                radius = 4f,
            )
        }
    }
}

private fun severityColor(severity: HazardAlert.Severity, fallback: Color): Color = when (severity) {
    HazardAlert.Severity.CRITICAL -> Color(0xFFEF4444)
    HazardAlert.Severity.HIGH     -> Color(0xFFF97316)
    HazardAlert.Severity.MEDIUM   -> Color(0xFFEAB308)
    HazardAlert.Severity.LOW      -> Color(0xFF22C55E)
}

/** Mercator-ish screen projection — good enough for ~5km radius around center. */
fun latLngToScreen(
    lat: Double, lng: Double,
    center: Pair<Double, Double>,
    zoom: Double,
    canvasW: Float,
    canvasH: Float,
): Offset {
    val scale      = 256.0 * Math.pow(2.0, zoom)
    val dLng       = lng - center.second
    val dLat       = lat - center.first
    val pxPerDeg   = scale / 360.0
    val x          = canvasW / 2f + (dLng * pxPerDeg).toFloat()
    val y          = canvasH / 2f - (dLat * pxPerDeg).toFloat()
    return Offset(x, y)
}
