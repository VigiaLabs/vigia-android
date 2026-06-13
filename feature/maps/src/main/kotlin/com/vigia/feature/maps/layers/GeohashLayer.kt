package com.vigia.feature.maps.layers

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import com.vigia.core.model.GeohashCell

private val GridStroke  = Color(0x206366F1)
private val DensityBase = Color(0xFFEF4444)
private val GridFill    = Color(0x086366F1)

@Composable
fun GeohashLayer(
    cells: List<GeohashCell>,
    mapCenter: Pair<Double, Double>,
    mapZoom: Double,
    canvasWidthPx: Float,
    canvasHeightPx: Float,
    modifier: Modifier = Modifier,
) {
    val rects = remember(cells, mapCenter, mapZoom, canvasWidthPx, canvasHeightPx) {
        cells.map { cell ->
            val topLeft = latLngToScreen(cell.latMax, cell.lngMin, mapCenter, mapZoom, canvasWidthPx, canvasHeightPx)
            val botRight = latLngToScreen(cell.latMin, cell.lngMax, mapCenter, mapZoom, canvasWidthPx, canvasHeightPx)
            Triple(topLeft, botRight, cell.hazardDensity)
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        rects.forEach { (topLeft, botRight, density) ->
            val w = botRight.x - topLeft.x
            val h = botRight.y - topLeft.y
            if (w <= 0f || h <= 0f) return@forEach

            // Density-driven fill
            if (density > 0.05f) {
                drawRect(
                    color   = DensityBase.copy(alpha = (density * 0.25f).coerceIn(0f, 0.25f)),
                    topLeft = topLeft,
                    size    = Size(w, h),
                )
            } else {
                drawRect(
                    color   = GridFill,
                    topLeft = topLeft,
                    size    = Size(w, h),
                )
            }

            // Grid border
            drawRect(
                color   = GridStroke,
                topLeft = topLeft,
                size    = Size(w, h),
                style   = Stroke(width = 1f),
            )
        }
    }
}
