package com.vigia.feature.copilot.voice

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vigia.feature.copilot.OrbState
import com.vigia.feature.copilot.VoiceListeningState
import com.vigia.feature.copilot.orb.AiOrb
import com.vigia.feature.copilot.theme.pressScale
import com.vigia.feature.copilot.theme.vigiaColors
import kotlin.math.PI
import kotlin.math.sin

/**
 * Live conversational AI overlay — Gemini Live–style full-screen voice session.
 *
 * Flow:
 *   [Listening] → user speaks, aurora reacts to real mic amplitude
 *   → tap orb → [Processing] (STT + AI search)
 *   → Sarvam TTS speaks answer inside the aurora → [Speaking]
 *   → mic auto-reopens → [Listening] again (conversational loop)
 *   → tap X to end the session entirely
 *
 * The centre orb is the primary interaction: it shows a "Tap to send" ring when
 * in [VoiceListeningState.Listening] and becomes non-interactive in other states.
 * The End button is the only other control — no Hold/Pause; drivers need simplicity.
 *
 * Aurora amplitude mapping:
 *   Listening  → 0.30 + voiceAmplitude × 0.70  (real mic RMS)
 *   Processing → 0.38  (dim breathe — thinking)
 *   Speaking   → 0.60  (mid glow — AI speaking)
 *   Idle       → 0.18  (very dim fallback)
 *
 * Render-thread guarantee: blob phases and amplitude are read INSIDE Canvas/graphicsLayer
 * lambdas — animating on draw phase only, zero recomposition cost.
 */
@Composable
internal fun VoiceCallOverlay(
    voiceAmplitude: Float,
    listeningState: VoiceListeningState,
    onSend: () -> Unit,      // tap orb while Listening → stop recording + transcribe
    onEnd: () -> Unit,       // tap X → dismiss entire voice session
    modifier: Modifier = Modifier,
) {
    val targetActivity = when (listeningState) {
        VoiceListeningState.Listening  -> 0.38f + voiceAmplitude * 0.62f
        VoiceListeningState.Processing -> 0.52f
        VoiceListeningState.Speaking   -> 0.78f
        VoiceListeningState.Idle       -> 0.28f
    }
    val activity = animateFloatAsState(
        targetValue   = targetActivity,
        animationSpec = tween(55, easing = FastOutSlowInEasing),
        label         = "voice_activity",
    )

    val bg = MaterialTheme.colorScheme.background
    val isDark = bg.luminance() < 0.5f

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bg),
    ) {
        AuroraMist(activity = activity, isDark = isDark, modifier = Modifier.fillMaxSize())

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Spacer(Modifier.height(40.dp))

            Text(
                text  = "VIGIA Voice",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(6.dp))

            // State label — crossfade between states so text changes feel intentional.
            AnimatedContent(
                targetState = listeningState,
                transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(180)) },
                label = "voice_state_label",
            ) { state ->
                Text(
                    text = when (state) {
                        VoiceListeningState.Listening  -> "Listening…"
                        VoiceListeningState.Processing -> "Processing…"
                        VoiceListeningState.Speaking   -> "Speaking…"
                        VoiceListeningState.Idle       -> ""
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
                )
            }

            Spacer(Modifier.weight(1f))

            // ── Centre orb — the primary send action ──────────────────────────
            OrbSendButton(
                voiceAmplitude = voiceAmplitude,
                listeningState = listeningState,
                onSend         = onSend,
                size           = 128.dp,
            )

            Spacer(Modifier.height(12.dp))

            // Hint text under the orb.
            AnimatedContent(
                targetState = listeningState,
                transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(180)) },
                label = "voice_hint_label",
            ) { state ->
                Text(
                    text      = when (state) {
                        VoiceListeningState.Listening  -> "Tap to send"
                        VoiceListeningState.Processing -> "Thinking…"
                        VoiceListeningState.Speaking   -> "Tap X to end"
                        VoiceListeningState.Idle       -> ""
                    },
                    style     = MaterialTheme.typography.labelMedium,
                    color     = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.weight(1f))

            // ── Single end-call button ─────────────────────────────────────────
            val endInteraction = remember { MutableInteractionSource() }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(64.dp)
                    .pressScale(endInteraction, pressedScale = 0.88f)
                    .clip(CircleShape)
                    .background(Color(0xFFFF5C3A))
                    .clickable(
                        interactionSource = endInteraction,
                        indication        = null,
                        onClick           = onEnd,
                    )
                    .semantics { contentDescription = "End voice session" },
            ) {
                Icon(
                    imageVector        = Icons.Filled.Close,
                    contentDescription = null,
                    tint               = Color.White,
                    modifier           = Modifier.size(26.dp),
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ── Orb send button ───────────────────────────────────────────────────────────

/**
 * The reactive AI orb — the primary conversational control.
 *
 * While [VoiceListeningState.Listening]: tappable, shows a pulsing white ring to
 * signal interactivity. The orb scale reacts to real mic amplitude.
 * In other states: non-interactive, organic oscillator runs instead.
 */
@Composable
private fun OrbSendButton(
    voiceAmplitude: Float,
    listeningState: VoiceListeningState,
    onSend: () -> Unit,
    size: Dp,
) {
    val isListening = listeningState == VoiceListeningState.Listening

    // Simulated oscillators keep the orb alive when mic amplitude is low.
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

    // Pulsing ring alpha — visible only in Listening state.
    val ringPulse by transition.animateFloat(
        initialValue = 0.25f, targetValue = 0.60f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "ring_pulse",
    )

    val interaction = remember { MutableInteractionSource() }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size + 24.dp)  // extra space for the tap ring
            .then(
                if (isListening) Modifier
                    .pressScale(interaction, pressedScale = 0.93f)
                    .clickable(
                        interactionSource = interaction,
                        indication        = null,
                        onClick           = onSend,
                    )
                else Modifier
            )
            .semantics {
                contentDescription = if (isListening) "Send voice message" else "VIGIA orb"
            },
    ) {
        // Pulsing affordance ring (Listening only) — rendered behind the orb.
        if (isListening) {
            val ringPx = with(LocalDensity.current) { (size / 2 + 14.dp).toPx() }
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color  = Color.White.copy(alpha = ringPulse),
                    radius = ringPx,
                    style  = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
                )
            }
        }

        // Orb — scaled by real amplitude when listening, oscillators otherwise.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    val simulated = 0.55f * a + 0.45f * b
                    val amp = when (listeningState) {
                        VoiceListeningState.Listening ->
                            voiceAmplitude * 0.72f + simulated * (1f - voiceAmplitude) * 0.28f
                        VoiceListeningState.Speaking  -> simulated * 0.50f   // gentle breathing
                        else                          -> simulated * 0.20f   // minimal motion
                    }
                    val s = 1f + amp * 0.24f
                    scaleX = s; scaleY = s
                },
        ) {
            val orbState = when (listeningState) {
                VoiceListeningState.Listening  -> OrbState.Listening
                VoiceListeningState.Processing -> OrbState.Searching
                VoiceListeningState.Speaking   -> OrbState.Active
                VoiceListeningState.Idle       -> OrbState.Idle
            }
            AiOrb(state = orbState, size = size)
        }
    }
}

// ── Aurora mist ───────────────────────────────────────────────────────────────

private class AuroraBlob(
    val xBase: Float, val yBase: Float, val rBase: Float,
    val xAmp: Float,  val yAmp: Float,  val rAmp: Float,
    val xSpeed: Float, val ySpeed: Float, val rSpeed: Float,
    val seed: Float, val colorIndex: Int,
)

/**
 * Volumetric aurora: large soft radial light blobs in the orb palette, drawn
 * with [BlendMode.Plus] (additive) so overlaps glow, then blurred so the whole
 * field reads as soft luminous mist. A slow breath animation makes it feel alive
 * independent of mic activity — activity just scales overall brightness.
 */
@Composable
private fun AuroraMist(
    activity: State<Float>,
    isDark: Boolean,
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

    val palette = remember(ext) { listOf(ext.orbRimPink, ext.orbRimViolet, ext.orbRimBlue) }
    val icy     = remember { Color(0xFFBFE3FF) }

    val blobs = remember {
        listOf(
            //          xBase yBase rBase xAmp yAmp rAmp  xSpd  ySpd rSpd seed col
            AuroraBlob(0.50f, 0.98f, 0.50f, 0.04f, 0.03f, 0.08f,  0.7f,  0.6f, 0.6f, 1.0f, 2),
            AuroraBlob(0.30f, 0.86f, 0.36f, 0.08f, 0.05f, 0.12f, -0.9f,  1.0f, 0.7f, 1.3f, 1),
            AuroraBlob(0.70f, 0.86f, 0.36f, 0.08f, 0.05f, 0.12f,  0.9f, -1.0f, 0.8f, 2.1f, 2),
            AuroraBlob(0.16f, 0.92f, 0.30f, 0.07f, 0.05f, 0.13f,  1.0f,  0.7f, 1.0f, 3.4f, 2),
            AuroraBlob(0.84f, 0.92f, 0.30f, 0.07f, 0.05f, 0.13f, -1.0f,  0.9f, 0.9f, 0.7f, 1),
            AuroraBlob(0.50f, 0.78f, 0.28f, 0.10f, 0.06f, 0.15f, -0.8f,  1.0f, 1.1f, 4.2f, 1),
            AuroraBlob(0.42f, 0.96f, 0.24f, 0.09f, 0.05f, 0.12f,  1.0f, -0.8f, 0.8f, 2.9f, 0),
        )
    }

    val blurPx      = with(LocalDensity.current) { 72.dp.toPx() }
    val bgColor     = MaterialTheme.colorScheme.background
    val floorScrim  = remember(bgColor) {
        Brush.verticalGradient(0.72f to Color.Transparent, 1.0f to bgColor)
    }

    // Dark mode: additive Plus blend for glow-on-black. Light mode: SrcOver for pastel wash.
    val blobBlend   = if (isDark) BlendMode.Plus else BlendMode.SrcOver
    val blobAlpha   = if (isDark) 0.54f else 0.22f
    val coreAlpha   = if (isDark) 0.72f else 0.28f
    val hotAlpha    = if (isDark) 0.56f else 0.20f

    Box(modifier) {
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
            val dim = activity.value
            val sw  = breath

            blobs.forEach { b ->
                val color = palette[b.colorIndex]
                val cx = (b.xBase + b.xAmp * sin(phaseA * b.xSpeed + b.seed)) * w
                val cy = (b.yBase + b.yAmp * sin(phaseB * b.ySpeed + b.seed * 1.7f)) * h
                val r  = (b.rBase + b.rAmp * sin(phaseA * b.rSpeed + b.seed)) * w * sw
                if (r <= 0f) return@forEach
                val c = Offset(cx, cy)
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(color.copy(alpha = blobAlpha * dim), Color.Transparent),
                        center = c, radius = r,
                    ),
                    radius = r, center = c, blendMode = blobBlend,
                )
            }

            val coreX = w * (0.5f + 0.04f * sin(phaseA * 0.6f))
            val coreC = Offset(coreX, h * 0.91f)
            val coreR = w * 0.80f * sw
            scale(scaleX = 1.45f, scaleY = 0.42f, pivot = coreC) {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(
                            icy.copy(alpha = coreAlpha * dim),
                            palette[2].copy(alpha = blobAlpha * dim),
                            palette[1].copy(alpha = (blobAlpha * 0.5f) * dim),
                            Color.Transparent,
                        ),
                        center = coreC, radius = coreR,
                    ),
                    radius = coreR, center = coreC, blendMode = blobBlend,
                )
            }
            val hotR = w * 0.42f * sw
            scale(scaleX = 1.3f, scaleY = 0.40f, pivot = coreC) {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(icy.copy(alpha = hotAlpha * dim), Color.Transparent),
                        center = coreC, radius = hotR,
                    ),
                    radius = hotR, center = coreC, blendMode = blobBlend,
                )
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(brush = floorScrim)
        }
    }
}
