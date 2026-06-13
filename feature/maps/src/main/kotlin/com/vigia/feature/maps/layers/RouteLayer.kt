package com.vigia.feature.maps.layers

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import com.vigia.core.model.BezierRoute

@Composable
fun RouteLayer(
    route: BezierRoute,
    mapCenter: Pair<Double, Double>,
    mapZoom: Double,
    canvasWidthPx: Float,
    canvasHeightPx: Float,
    modifier: Modifier = Modifier,
) {
    val routeColor = MaterialTheme.colorScheme.primary

    val inf      = rememberInfiniteTransition(label = "route_dash")
    val dashPhase by inf.animateFloat(
        initialValue = 0f, targetValue = 48f,
        animationSpec = infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Restart),
        label = "dash_phase",
    )

    val points = remember(route, mapCenter, mapZoom, canvasWidthPx, canvasHeightPx) {
        route.waypoints.map { wp ->
            latLngToScreen(wp.lat, wp.lng, mapCenter, mapZoom, canvasWidthPx, canvasHeightPx)
        }
    }

    if (points.size < 2) return

    Canvas(modifier = modifier.fillMaxSize()) {
        val path = Path().apply {
            moveTo(points.first().x, points.first().y)
            if (points.size == 2) {
                lineTo(points.last().x, points.last().y)
            } else {
                for (i in 1 until points.size - 1) {
                    val mid = Offset((points[i].x + points[i + 1].x) / 2f, (points[i].y + points[i + 1].y) / 2f)
                    quadraticTo(points[i].x, points[i].y, mid.x, mid.y)
                }
                lineTo(points.last().x, points.last().y)
            }
        }

        // Glow pass
        drawPath(
            path  = path,
            color = routeColor.copy(alpha = 0.15f),
            style = Stroke(width = 20f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        // Main stroke
        drawPath(
            path  = path,
            color = routeColor.copy(alpha = 0.90f),
            style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        // Animated dash overlay (flowing motion effect)
        drawPath(
            path  = path,
            color = Color.White.copy(alpha = 0.35f),
            style = Stroke(
                width  = 3f,
                cap    = StrokeCap.Round,
                join   = StrokeJoin.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 32f), dashPhase),
            ),
        )

        // Origin dot
        drawCircle(color = routeColor, radius = 10f, center = points.first())
        drawCircle(color = Color.White, radius = 5f,  center = points.first())

        // Destination dot
        drawCircle(color = routeColor, radius = 12f, center = points.last())
        drawCircle(color = Color.White, radius = 6f,  center = points.last())
    }
}
