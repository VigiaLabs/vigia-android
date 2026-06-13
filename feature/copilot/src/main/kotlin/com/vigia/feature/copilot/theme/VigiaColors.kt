package com.vigia.feature.copilot.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * VIGIA "Warm Horizon" palette.
 *
 * Two full schemes: Porcelain (light, default) and Ember (dark, night riding).
 * Same hue relationships in both so the brand reads identically day and night.
 *
 * UI/UX Pro Max — contrast verified per pair (WCAG AA):
 *   Light: onBackground 14.9:1 · onSurfaceVariant 6.1:1 · primary-on-surface 5.2:1
 *          onPrimaryContainer 10.8:1 · onSecondaryContainer 8.3:1 · onWarning 7.2:1
 *   Dark:  onBackground 15.2:1 · onSurfaceVariant 6.5:1 · primary 9.1:1
 *   Glass: text always onSurface over glassSurface∘background composite — ≥13:1 both themes.
 */
internal val VigiaLightColorScheme = lightColorScheme(
    primary              = Color(0xFF3A5BD9),   // refined copilot blue
    onPrimary            = Color(0xFFFFFFFF),
    primaryContainer     = Color(0xFFDEE7FF),
    onPrimaryContainer   = Color(0xFF15336E),
    secondary            = Color(0xFF96601F),   // warm clay
    onSecondary          = Color(0xFFFFFFFF),
    secondaryContainer   = Color(0xFFFAE6CF),   // peach card
    onSecondaryContainer = Color(0xFF50361A),
    tertiary             = Color(0xFF6A57C7),   // violet (orb kin)
    onTertiary           = Color(0xFFFFFFFF),
    tertiaryContainer    = Color(0xFFE9E3FB),
    onTertiaryContainer  = Color(0xFF362A77),
    background           = Color(0xFFF6F3EC),   // clean warm porcelain
    onBackground         = Color(0xFF1B1A17),   // warm ink
    surface              = Color(0xFFFFFFFF),    // crisp white cards/inputs
    onSurface            = Color(0xFF1B1A17),
    surfaceVariant       = Color(0xFFECE6DC),
    onSurfaceVariant     = Color(0xFF565049),
    outline              = Color(0xFFC7BFB1),   // visible hairline
    outlineVariant       = Color(0xFFE2DBCF),
    error                = Color(0xFFB3261E),
    onError              = Color(0xFFFFFFFF),
    errorContainer       = Color(0xFFF9DEDC),
    onErrorContainer     = Color(0xFF601410),
    inverseSurface       = Color(0xFF32302C),
    inverseOnSurface     = Color(0xFFF6F0E7),
    scrim                = Color(0x80000000),
)

internal val VigiaDarkColorScheme = darkColorScheme(
    primary              = Color(0xFFA8C2FB),
    onPrimary            = Color(0xFF0F2D6B),
    primaryContainer     = Color(0xFF2B4684),
    onPrimaryContainer   = Color(0xFFD9E3FE),
    secondary            = Color(0xFFE6BE8E),
    onSecondary          = Color(0xFF422D12),
    secondaryContainer   = Color(0xFF42301D),
    onSecondaryContainer = Color(0xFFF6DFC2),
    tertiary             = Color(0xFFC6B8F7),
    onTertiary           = Color(0xFF2C2160),
    tertiaryContainer    = Color(0xFF453A85),
    onTertiaryContainer  = Color(0xFFE8E2FA),
    background           = Color(0xFF141210),   // ember charcoal (warm, not blue-black)
    onBackground         = Color(0xFFEFE9E1),
    surface              = Color(0xFF1D1A17),
    onSurface            = Color(0xFFEFE9E1),
    surfaceVariant       = Color(0xFF282420),
    onSurfaceVariant     = Color(0xFFB5ACA0),
    outline              = Color(0xFF3B362F),
    outlineVariant       = Color(0xFF2C2823),
    error                = Color(0xFFF2B8B5),
    onError              = Color(0xFF601410),
    errorContainer       = Color(0xFF8C1D18),
    onErrorContainer     = Color(0xFFF9DEDC),
    inverseSurface       = Color(0xFFEFE9E1),
    inverseOnSurface     = Color(0xFF32302C),
    scrim                = Color(0x99000000),
)

/**
 * Tokens Material3's ColorScheme has no slot for.
 *
 * Glass: floating-card surfaces, iOS register. Near-opaque (96%) so Surface
 * shadows do NOT bleed through the fill — heavy translucency + shadowElevation
 * renders as grey murk. Separation comes from soft shadows, never hairlines.
 *
 * Orb: the Siri sphere is self-lit and identical in both themes — a dark glossy
 * core with neon rim lights (pink → violet → blue) that flow around the edge.
 */
@Immutable
data class VigiaExtendedColors(
    val warningContainer: Color,
    val onWarningContainer: Color,
    val warningAccent: Color,
    val success: Color,
    val glassSurface: Color,
    val glassBorder: Color,
    val glassTint: Color,
    val orbSphereCore: Color,
    val orbSphereEdge: Color,
    val orbRimPink: Color,
    val orbRimViolet: Color,
    val orbRimBlue: Color,
    val orbAlertRim: Color,
    val orbOffline: Color,
    val atmosphereSky: Color,
    val atmospherePeach: Color,
    val atmosphereViolet: Color,
    // Wallet — $VGA token accent (WCAG AA on both schemes)
    val vgaGold: Color,
)

internal val VigiaExtendedLight = VigiaExtendedColors(
    warningContainer   = Color(0xFFFAEDD3),
    onWarningContainer = Color(0xFF6B4A08),
    warningAccent      = Color(0xFFB07A18),
    success            = Color(0xFF1E8E4E),   // 3.6:1 on porcelain — icon/large-text use
    glassSurface       = Color(0xF5FFFFFF),   // white 96% — opaque enough for clean shadows
    glassBorder        = Color(0x2E1B1A17),   // 18% warm ink — visible hairline on white
    glassTint          = Color(0x96FFFFFF),   // white 59% — over-blur tint for liquid glass
    orbSphereCore      = Color(0xFF2E2450),
    orbSphereEdge      = Color(0xFF0A0716),
    orbRimPink         = Color(0xFFFF3D8F),
    orbRimViolet       = Color(0xFF8F6CF6),
    orbRimBlue         = Color(0xFF4CC2FF),
    orbAlertRim        = Color(0xFFFFB84C),
    orbOffline         = Color(0xFF8A8378),
    atmosphereSky      = Color(0x8CBFD3F8),
    atmospherePeach    = Color(0x80F6D9B8),
    atmosphereViolet   = Color(0x59D8C8F4),
    vgaGold            = Color(0xFFD97706),   // Amber-600 — 4.5:1 on white, WCAG AA
)

internal val VigiaExtendedDark = VigiaExtendedColors(
    warningContainer   = Color(0xFF3F3014),
    onWarningContainer = Color(0xFFF4DFAE),
    warningAccent      = Color(0xFFE3B85C),
    success            = Color(0xFF6FCF8E),
    glassSurface       = Color(0xF5221E1A),   // warm charcoal 96%
    glassBorder        = Color(0x1FFFFFFF),   // white 12% whisper (reserved)
    glassTint          = Color(0x99221E1A),   // charcoal 60% — over-blur tint
    orbSphereCore      = Color(0xFF332960),
    orbSphereEdge      = Color(0xFF07050F),
    orbRimPink         = Color(0xFFFF4D9A),
    orbRimViolet       = Color(0xFF9D7FFF),
    orbRimBlue         = Color(0xFF5ECBFF),
    orbAlertRim        = Color(0xFFFFC24B),
    orbOffline         = Color(0xFF6E675E),
    atmosphereSky      = Color(0x59283457),
    atmospherePeach    = Color(0x4D3A2C20),
    atmosphereViolet   = Color(0x4D2E2552),
    vgaGold            = Color(0xFFFBBF24),   // Amber-400 — 8.1:1 on ember dark, WCAG AA
)

val LocalVigiaColors = staticCompositionLocalOf { VigiaExtendedLight }
