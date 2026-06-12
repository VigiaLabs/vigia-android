package com.vigia.feature.copilot.orb

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vigia.feature.copilot.OrbState
import com.vigia.feature.copilot.theme.vigiaColors

/**
 * Siri-style orb — a dark glossy sphere with neon rim lights that flow
 * continuously around the edge (pink → violet → blue), counter-rotating
 * at two speeds for the liquid shimmer of the reference.
 *
 * The sphere is self-lit and identical in both themes; only the halo and
 * hairline adapt. Alert state swaps the rim palette to amber/red fire;
 * Offline desaturates and dims.
 *
 * Render-thread guarantee (Compose Expert rule):
 *   - Scale / alpha / pulse: State read inside [graphicsLayer] — render thread.
 *   - Rim rotation: State read inside the Canvas draw lambda — draw-phase
 *     invalidation only. The composition is never re-executed per frame.
 *
 * UI/UX Pro Max: semantic contentDescription per state for TalkBack.
 */
@Composable
fun AiOrb(
    state: OrbState,
    modifier: Modifier = Modifier,
    size: Dp = 150.dp,
) {
    // ── Animation specs ───────────────────────────────────────────────────────

    val infiniteTransition = rememberInfiniteTransition(label = "orb")

    val breatheScale = infiniteTransition.animateFloat(
        initialValue = 0.97f,
        targetValue  = 1.03f,
        animationSpec = infiniteRepeatable(
            animation  = tween(2_400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathe_scale",
    )

    val alertPulse = infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue  = 1.08f,
        animationSpec = infiniteRepeatable(
            animation  = tween(380, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "alert_pulse",
    )

    // Two flow speeds — the bands drift against each other like liquid light.
    val flowFast = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue  = 360f,
        animationSpec = infiniteRepeatable(
            animation  = tween(6_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "flow_fast",
    )
    val flowSlow = infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue  = 0f,
        animationSpec = infiniteRepeatable(
            animation  = tween(11_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "flow_slow",
    )

    val targetScale = when (state) {
        OrbState.Idle      -> 1.0f
        OrbState.Active    -> 1.04f
        OrbState.Alert     -> 1.12f
        OrbState.Offline   -> 0.84f
        OrbState.Searching -> 1.06f
    }
    val outerScale = animateFloatAsState(
        targetValue   = targetScale,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label         = "outer_scale",
    )

    val targetAlpha = if (state == OrbState.Offline) 0.45f else 1.0f
    val orbAlpha = animateFloatAsState(
        targetValue   = targetAlpha,
        animationSpec = tween(400),
        label         = "orb_alpha",
    )

    // ── Theme-driven colours (resolved once per state/theme change) ───────────

    val ext = MaterialTheme.vigiaColors

    // Rim palette per state: aurora (default) / fire (alert) / ash (offline).
    val rimA: Color
    val rimB: Color
    val rimC: Color
    when (state) {
        OrbState.Alert -> {
            rimA = ext.orbAlertRim
            rimB = MaterialTheme.colorScheme.error
            rimC = ext.orbAlertRim
        }
        OrbState.Offline -> {
            rimA = ext.orbOffline
            rimB = ext.orbOffline
            rimC = ext.orbOffline
        }
        else -> {
            rimA = ext.orbRimPink
            rimB = ext.orbRimViolet
            rimC = ext.orbRimBlue
        }
    }

    // Sweep brushes: arcs of light separated by transparency, glow = wider faint pass.
    val bandBright = remember(rimA, rimB, rimC) {
        Brush.sweepGradient(
            0.00f to Color.Transparent,
            0.10f to rimA,
            0.22f to Color.Transparent,
            0.42f to rimB,
            0.58f to Color.Transparent,
            0.78f to rimC,
            0.92f to Color.Transparent,
            1.00f to Color.Transparent,
        )
    }
    val bandGlow = remember(rimA, rimB, rimC) {
        Brush.sweepGradient(
            0.00f to Color.Transparent,
            0.10f to rimA.copy(alpha = 0.35f),
            0.22f to Color.Transparent,
            0.42f to rimB.copy(alpha = 0.35f),
            0.58f to Color.Transparent,
            0.78f to rimC.copy(alpha = 0.35f),
            0.92f to Color.Transparent,
            1.00f to Color.Transparent,
        )
    }
    val bandCounter = remember(rimB, rimC) {
        Brush.sweepGradient(
            0.00f to Color.Transparent,
            0.18f to rimC.copy(alpha = 0.85f),
            0.34f to Color.Transparent,
            0.66f to rimB.copy(alpha = 0.85f),
            0.82f to Color.Transparent,
            1.00f to Color.Transparent,
        )
    }

    val sphereCore = ext.orbSphereCore
    val sphereEdge = ext.orbSphereEdge
    val haloTint   = remember(rimB) { rimB.copy(alpha = 0.30f) }

    val semanticLabel = when (state) {
        OrbState.Idle      -> "Copilot idle"
        OrbState.Active    -> "Copilot active, device linked"
        OrbState.Alert     -> "Hazard alert active"
        OrbState.Offline   -> "Device offline"
        OrbState.Searching -> "Searching…"
    }

    // ── Layout ────────────────────────────────────────────────────────────────

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .semantics { contentDescription = semanticLabel }
            // Render thread: scale / alpha / pulse.
            .graphicsLayer {
                val breatheFactor = if (state == OrbState.Alert) alertPulse.value
                                    else breatheScale.value
                val combinedScale = outerScale.value * breatheFactor
                scaleX            = combinedScale
                scaleY            = combinedScale
                alpha             = orbAlpha.value
            },
    ) {
        // Draw phase: flowFast/flowSlow reads invalidate DRAW only — no recomposition.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val rOuter = this.size.minDimension / 2f
            val rSphere = rOuter * 0.62f

            // Halo — lets the sphere float on the warm canvas
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(haloTint, Color.Transparent),
                    center = center,
                    radius = rOuter,
                ),
                center = center,
                radius = rOuter,
            )

            // Dark glossy sphere, lit from upper-left
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(sphereCore, sphereCore, sphereEdge),
                    center = Offset(center.x - rSphere * 0.30f, center.y - rSphere * 0.38f),
                    radius = rSphere * 1.9f,
                ),
                center = center,
                radius = rSphere,
            )

            // Specular sheen pool
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.22f), Color.Transparent),
                    center = Offset(center.x - rSphere * 0.34f, center.y - rSphere * 0.44f),
                    radius = rSphere * 0.85f,
                ),
                center = center,
                radius = rSphere,
            )

            // Rim lights — wide glow pass, bright core pass, counter-rotating band
            rotate(flowFast.value, pivot = center) {
                drawCircle(
                    brush  = bandGlow,
                    center = center,
                    radius = rSphere,
                    style  = Stroke(width = rSphere * 0.30f),
                )
                drawCircle(
                    brush  = bandBright,
                    center = center,
                    radius = rSphere,
                    style  = Stroke(width = rSphere * 0.10f),
                )
            }
            rotate(flowSlow.value, pivot = center) {
                drawCircle(
                    brush  = bandCounter,
                    center = center,
                    radius = rSphere * 0.94f,
                    style  = Stroke(width = rSphere * 0.07f),
                )
            }
        }
    }
}
