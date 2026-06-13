package com.vigia.feature.maps.layers

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.vigia.core.model.MaintenancePoi

private val HighColor   = Color(0xFFF97316)
private val MedColor    = Color(0xFFEAB308)
private val LowColor    = Color(0xFF6B7280)

@Composable
fun MaintenanceLayer(
    pois: List<MaintenancePoi>,
    mapCenter: Pair<Double, Double>,
    mapZoom: Double,
    canvasWidthPx: Float,
    canvasHeightPx: Float,
    onPoiTap: (MaintenancePoi) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pinData = remember(pois, mapCenter, mapZoom, canvasWidthPx, canvasHeightPx) {
        pois.map { poi ->
            val screen = latLngToScreen(
                poi.location.latitudeDeg, poi.location.longitudeDeg,
                mapCenter, mapZoom, canvasWidthPx, canvasHeightPx,
            )
            Pair(poi, screen)
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(pinData) {
                detectTapGestures { tap ->
                    pinData.forEach { (poi, screen) ->
                        val dx = tap.x - screen.x
                        val dy = tap.y - screen.y
                        if (dx * dx + dy * dy <= 900f) { onPoiTap(poi); return@detectTapGestures }
                    }
                }
            },
    ) {
        pinData.forEach { (poi, screen) ->
            val color = when (poi.priority) {
                MaintenancePoi.Priority.HIGH   -> HighColor
                MaintenancePoi.Priority.MEDIUM -> MedColor
                MaintenancePoi.Priority.LOW    -> LowColor
            }
            drawCircle(color = color.copy(alpha = 0.15f), radius = 16f, center = screen)
            drawCircle(color = color, radius = 14f, center = screen, style = Stroke(width = 2f))
            // Wrench symbol approximated as a cross
            drawLine(color, screen + Offset(-5f, -5f), screen + Offset(5f, 5f), 2.5f)
            drawLine(color, screen + Offset(5f, -5f),  screen + Offset(-5f, 5f), 2.5f)
        }
    }
}
