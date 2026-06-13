package com.vigia.feature.maps.layers

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import com.vigia.core.model.TraceFrame

private val TraceStart = Color(0xFF6366F1)
private val TraceEnd   = Color(0xFF22C55E)

@Composable
fun TracePlaybackLayer(
    frames: List<TraceFrame>,
    playbackIndex: Int,
    mapCenter: Pair<Double, Double>,
    mapZoom: Double,
    canvasWidthPx: Float,
    canvasHeightPx: Float,
    modifier: Modifier = Modifier,
) {
    val visibleFrames = remember(frames, playbackIndex) {
        frames.take((playbackIndex + 1).coerceAtMost(frames.size))
    }

    if (visibleFrames.size < 2) return

    val points = remember(visibleFrames, mapCenter, mapZoom, canvasWidthPx, canvasHeightPx) {
        visibleFrames.map { f ->
            latLngToScreen(
                f.location.latitudeDeg,
                f.location.longitudeDeg,
                mapCenter,
                mapZoom,
                canvasWidthPx,
                canvasHeightPx,
            )
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val path = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }

        // Subtle glow
        drawPath(
            path  = path,
            color = TraceStart.copy(alpha = 0.12f),
            style = Stroke(width = 12f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        // Coloured trace line (gradient effect via per-segment draws)
        val n = points.size
        for (i in 0 until n - 1) {
            val t = i.toFloat() / (n - 1).coerceAtLeast(1)
            drawLine(
                color       = lerp(TraceStart, TraceEnd, t).copy(alpha = 0.80f),
                start       = points[i],
                end         = points[i + 1],
                strokeWidth = 4f,
                cap         = StrokeCap.Round,
            )
        }

        // Current position dot
        drawCircle(
            color  = TraceEnd,
            radius = 8f,
            center = points.last(),
        )
        drawCircle(
            color  = Color.White,
            radius = 4f,
            center = points.last(),
        )
    }
}
