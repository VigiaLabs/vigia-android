package com.vigia.feature.copilot.theme

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Motion tokens — iOS register.
 *
 * One rhythm everywhere (ui-ux-pro-max `motion-consistency`): springs for state,
 * short tweens for fades, exits ~60% of enter duration (`exit-faster-than-enter`).
 * Springs are interruptible by construction (`interruptible`) — retargeting
 * mid-flight never snaps.
 */
object VigiaMotion {
    /** Primary state spring — UIKit `response ≈ 0.35, damping 0.85` equivalent. */
    val gentle: SpringSpec<Float> = spring(dampingRatio = 0.85f, stiffness = 320f)

    /** Press feedback — fast with a light bounce. */
    val snappy: SpringSpec<Float> = spring(dampingRatio = 0.60f, stiffness = 700f)

    const val ENTER_MS = 240
    const val EXIT_MS  = 140
}

/**
 * iOS press feedback: the element squishes to [pressedScale] while touched and
 * springs back on release — used WITH `indication = null` so no Material ripple.
 *
 * Compose Expert: the scale State is read only inside [graphicsLayer] — press
 * animation runs on the render thread with zero recompositions.
 */
@Composable
fun Modifier.pressScale(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.965f,
): Modifier {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale = animateFloatAsState(
        targetValue   = if (isPressed) pressedScale else 1f,
        animationSpec = VigiaMotion.snappy,
        label         = "press_scale",
    )
    return this.graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}
