package com.vigia.feature.copilot.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/**
 * Application-wide theme wrapper — "Warm Horizon".
 *
 * Follows the system theme: Porcelain (warm light) by day, Ember (warm dark) by night.
 * Extended tokens (orb gradient, warning tier, atmosphere washes) ride along via
 * [LocalVigiaColors]; read them with [MaterialTheme.vigiaColors].
 *
 * Compose Expert gates:
 *  - Scheme objects are top-level singletons — never re-allocated, never invalidate.
 *  - All UI colours read via MaterialTheme.colorScheme.* / MaterialTheme.vigiaColors.
 *
 * UI/UX Pro Max gates:
 *  - Contrast pairs verified per scheme in VigiaColors.kt.
 *  - Light and dark designed together (token-driven, not inverted).
 */
@Composable
fun VigiaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) VigiaDarkColorScheme else VigiaLightColorScheme
    val extended    = if (darkTheme) VigiaExtendedDark else VigiaExtendedLight

    CompositionLocalProvider(LocalVigiaColors provides extended) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography  = VigiaTypography,
            shapes      = VigiaShapes,
            content     = content,
        )
    }
}

/** Accessor for the extended (non-Material-slot) tokens: `MaterialTheme.vigiaColors.orbCore`. */
val MaterialTheme.vigiaColors: VigiaExtendedColors
    @Composable @ReadOnlyComposable get() = LocalVigiaColors.current
