package com.vigia.feature.copilot.voice

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vigia.feature.copilot.OrbState
import com.vigia.feature.copilot.orb.AiOrb
import com.vigia.feature.copilot.theme.pressScale
import com.vigia.feature.copilot.theme.vigiaColors
import kotlin.math.PI
import kotlin.math.sin

/**
 * Gemini Live–style voice surface: a near-black canvas with volumetric aurora
 * light pooling along the bottom. Hands-free register for drivers.
 *
 * The aurora is NOT drawn as wave outlines (that looks cartoonish). It is a
 * field of large, soft radial light blobs — pink / violet / blue from the orb
 * palette — drifting independently and composited with ADDITIVE blending, then
 * run through a heavy GPU blur. Overlaps brighten like real light and every
 * hard edge dissolves, giving the diffuse curtains of Gemini's aurora.
 *
 * Bottom controls: Hold · reactive liquid AI orb · End.
 *
 * Render-thread guarantee: blob phases are read INSIDE the Canvas draw lambda
 * and the blur lives in [graphicsLayer] — the field animates on the draw/render
 * phases only, never recomposition.
 *
 * NOTE: amplitude is simulated for now. When the multilingual transcription
 * stream lands, feed its live amplitude into [AuroraMist] (activity) and
 * [ReactiveVoiceOrb] — they are the two integration points.
 */
@Composable
internal fun VoiceCallOverlay(
    onEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var paused by remember { mutableStateOf(false) }

    // The aurora calms to a dim drift on hold instead of freezing dead.
    val activity = animateFloatAsState(
        targetValue   = if (paused) 0.22f else 1f,
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label         = "voice_activity",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF07060A)),
    ) {
        AuroraMist(
            activity = activity,
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Spacer(Modifier.height(48.dp))
            Text(
                text  = "VIGIA Voice",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text  = if (paused) "On hold" else "Listening…",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.65f),
            )

            Spacer(Modifier.weight(1f))

            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(32.dp),
            ) {
                VoiceControlButton(
                    icon      = if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    label     = if (paused) "Resume" else "Hold",
                    container = Color.White.copy(alpha = 0.14f),
                    content   = Color.White,
                    onClick   = { paused = !paused },
                )
                ReactiveVoiceOrb(active = !paused, size = 96.dp)
                VoiceControlButton(
                    icon      = Icons.Filled.Close,
                    label     = "End",
                    container = Color(0xFFFF7A59),
                    content   = Color.White,
                    onClick   = onEnd,
                )
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}

// ── Aurora mist ───────────────────────────────────────────────────────────────

/** One soft light blob: drifts in x/y and pulses in radius, each on its own clock. */
private class AuroraBlob(
    val xBase: Float, val yBase: Float, val rBase: Float,
    val xAmp: Float,  val yAmp: Float,  val rAmp: Float,
    val xSpeed: Float, val ySpeed: Float, val rSpeed: Float,
    val seed: Float, val colorIndex: Int,
)

/**
 * Volumetric aurora: ~11 large radial light blobs in the orb palette, drifting
 * independently, drawn with [BlendMode.Plus] (additive) so overlaps glow, then
 * blurred heavily so the whole field reads as soft luminous mist rather than
 * stacked bands. A slow breath swells it; a floor scrim melts it into the bezel.
 */
@Composable
private fun AuroraMist(
    activity: State<Float>,
    modifier: Modifier = Modifier,
) {
    val ext        = MaterialTheme.vigiaColors
    val transition = rememberInfiniteTransition(label = "aurora")

    val phaseA by transition.animateFloat(
        initialValue  = 0f,
        targetValue   = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(11_000, easing = LinearEasing), RepeatMode.Restart),
        label = "phase_a",
    )
    val phaseB by transition.animateFloat(
        initialValue  = 0f,
        targetValue   = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(17_000, easing = LinearEasing), RepeatMode.Restart),
        label = "phase_b",
    )
    val breath by transition.animateFloat(
        initialValue  = 0.86f,
        targetValue   = 1.14f,
        animationSpec = infiniteRepeatable(tween(4_200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath",
    )

    // 0 = pink, 1 = violet, 2 = blue. Cool-weighted toward Google's aurora;
    // `icy` is the near-white highlight that makes overlaps read as real light.
    val palette = remember(ext) { listOf(ext.orbRimPink, ext.orbRimViolet, ext.orbRimBlue) }
    val icy     = remember { Color(0xFFBFE3FF) }

    // Low, cool, sparse — the bright horizontal core band carries the glow; these
    // just add slow colour variation drifting above it.
    val blobs = remember {
        listOf(
            //          xBase yBase rBase xAmp yAmp rAmp  xSpd  ySpd rSpd seed col
            AuroraBlob(0.50f, 0.98f, 0.50f, 0.04f, 0.03f, 0.08f,  0.7f,  0.6f, 0.6f, 1.0f, 2), // broad blue base
            AuroraBlob(0.30f, 0.86f, 0.36f, 0.08f, 0.05f, 0.12f, -0.9f,  1.0f, 0.7f, 1.3f, 1), // violet left
            AuroraBlob(0.70f, 0.86f, 0.36f, 0.08f, 0.05f, 0.12f,  0.9f, -1.0f, 0.8f, 2.1f, 2), // blue right
            AuroraBlob(0.16f, 0.92f, 0.30f, 0.07f, 0.05f, 0.13f,  1.0f,  0.7f, 1.0f, 3.4f, 2), // blue far left
            AuroraBlob(0.84f, 0.92f, 0.30f, 0.07f, 0.05f, 0.13f, -1.0f,  0.9f, 0.9f, 0.7f, 1), // violet far right
            AuroraBlob(0.50f, 0.78f, 0.28f, 0.10f, 0.06f, 0.15f, -0.8f,  1.0f, 1.1f, 4.2f, 1), // violet upper centre
            AuroraBlob(0.42f, 0.96f, 0.24f, 0.09f, 0.05f, 0.12f,  1.0f, -0.8f, 0.8f, 2.9f, 0), // pink accent
        )
    }

    val blurPx     = with(LocalDensity.current) { 64.dp.toPx() }
    val floorScrim = remember {
        Brush.verticalGradient(0.78f to Color.Transparent, 1.0f to Color(0xFF07060A))
    }

    Box(modifier) {
        // Additive, heavily-blurred light field.
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    renderEffect = BlurEffect(blurPx, blurPx, TileMode.Decal)
                    clip = false
                }
                .semantics { contentDescription = "Voice activity" },
        ) {
            val w   = size.width
            val h   = size.height
            val dim = activity.value          // hold → dim
            val sw  = breath                  // swell

            // Drifting colour blobs (behind the core band).
            blobs.forEach { b ->
                val color = palette[b.colorIndex]
                val cx = (b.xBase + b.xAmp * sin(phaseA * b.xSpeed + b.seed)) * w
                val cy = (b.yBase + b.yAmp * sin(phaseB * b.ySpeed + b.seed * 1.7f)) * h
                val r  = (b.rBase + b.rAmp * sin(phaseA * b.rSpeed + b.seed)) * w * sw
                if (r <= 0f) return@forEach
                val c = Offset(cx, cy)
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(color.copy(alpha = 0.26f * dim), Color.Transparent),
                        center = c, radius = r,
                    ),
                    radius = r, center = c, blendMode = BlendMode.Plus,
                )
            }

            // Bright horizontal core band — the heart of the aurora. A radial
            // glow flattened into a wide ellipse, drifting gently side to side.
            val coreX = w * (0.5f + 0.04f * sin(phaseA * 0.6f))
            val coreC = Offset(coreX, h * 0.91f)
            val coreR = w * 0.80f * sw
            scale(scaleX = 1.45f, scaleY = 0.42f, pivot = coreC) {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(
                            icy.copy(alpha = 0.42f * dim),
                            palette[2].copy(alpha = 0.26f * dim),
                            palette[1].copy(alpha = 0.12f * dim),
                            Color.Transparent,
                        ),
                        center = coreC, radius = coreR,
                    ),
                    radius = coreR, center = coreC, blendMode = BlendMode.Plus,
                )
            }
            // Concentrated inner hot-spot for a luminous centre.
            val hotR = w * 0.42f * sw
            scale(scaleX = 1.3f, scaleY = 0.40f, pivot = coreC) {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(icy.copy(alpha = 0.30f * dim), Color.Transparent),
                        center = coreC, radius = hotR,
                    ),
                    radius = hotR, center = coreC, blendMode = BlendMode.Plus,
                )
            }
        }

        // Floor scrim (unblurred) melts the mist into the bottom edge.
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(brush = floorScrim)
        }
    }
}

// ── Reactive orb & controls ───────────────────────────────────────────────────

/**
 * The AI orb, scaled by a simulated amplitude so it visibly "breathes" with the
 * voice. Two out-of-phase oscillators are summed inside [graphicsLayer] (render
 * thread) for an organic, non-mechanical pulse.
 */
@Composable
private fun ReactiveVoiceOrb(active: Boolean, size: Dp) {
    val transition = rememberInfiniteTransition(label = "voice_orb")
    val a by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(430, easing = LinearEasing), RepeatMode.Reverse),
        label = "amp_a",
    )
    val b by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(670, easing = LinearEasing), RepeatMode.Reverse),
        label = "amp_b",
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                val amp   = if (active) (0.55f * a + 0.45f * b) else 0f
                val scale = 1f + amp * 0.16f
                scaleX = scale
                scaleY = scale
            },
    ) {
        AiOrb(state = OrbState.Searching, size = size)
    }
}

@Composable
private fun VoiceControlButton(
    icon: ImageVector,
    label: String,
    container: Color,
    content: Color,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(64.dp)
                .pressScale(interaction, pressedScale = 0.90f)
                .clip(CircleShape)
                .background(container)
                .clickable(interactionSource = interaction, indication = null) { onClick() }
                .semantics { contentDescription = label },
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                tint               = content,
                modifier           = Modifier.size(26.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text  = label,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.85f),
        )
    }
}
