# PHASE 4: Cyber-Minimalist Compose Presentation & Stripe Connected Pay
**Status:** Pending  
**Prerequisite:** Phase 3 — MQTT subscriber live; FCM wakeup verified; TTS pre-warmed  
**Primary Module:** `:feature:copilot`  
**Exit Criteria:** AI Orb renders at 60/120fps with 0 recompositions on animation frames (verified via Layout Inspector); Stripe Financial Connections sheet opens and completes sandbox onboarding flow

---

## 1. Deliverables

| Artifact | Path |
|---|---|
| `VigiaTheme` | `feature/copilot/src/.../theme/VigiaTheme.kt` |
| `VigiaColors` | `feature/copilot/src/.../theme/VigiaColors.kt` |
| `VigiaTypography` | `feature/copilot/src/.../theme/VigiaTypography.kt` |
| `VigiaShapes` | `feature/copilot/src/.../theme/VigiaShapes.kt` |
| `AiOrbComponent` | `feature/copilot/src/.../orb/AiOrbComponent.kt` |
| `CopilotRoute` | `feature/copilot/src/.../CopilotRoute.kt` |
| `CopilotScreen` | `feature/copilot/src/.../CopilotScreen.kt` |
| `CopilotViewModel` | `feature/copilot/src/.../CopilotViewModel.kt` |
| `CopilotUiState` | `feature/copilot/src/.../CopilotUiState.kt` |
| `StripePaySheet` | `feature/copilot/src/.../stripe/StripePaySheet.kt` |
| `CopilotModule` (Hilt) | `feature/copilot/src/.../di/CopilotModule.kt` |

---

## 2. Theme — Cyber-Minimalist Dark

### 2.1 Color Tokens

```kotlin
// VigiaColors.kt
object VigiaColors {
    val Background   = Color(0xFF09090B)   // Root scaffold background
    val Surface      = Color(0xFF18181B)   // Cards, sheets, dialogs
    val Outline      = Color(0xFF27272A)   // Borders, dividers
    val Primary      = Color(0xFFA78BFA)   // Violet accent
    val OnPrimary    = Color(0xFF09090B)
    val Secondary    = Color(0xFF38BDF8)   // Sky accent
    val Error        = Color(0xFFF87171)
    val OnBackground = Color(0xFFFAFAFA)   // Primary body text
    val OnSurface    = Color(0xFFA1A1AA)   // Secondary / caption text
}

private val VigiaDarkColorScheme = darkColorScheme(
    background       = VigiaColors.Background,
    surface          = VigiaColors.Surface,
    outline          = VigiaColors.Outline,
    primary          = VigiaColors.Primary,
    onPrimary        = VigiaColors.OnPrimary,
    secondary        = VigiaColors.Secondary,
    error            = VigiaColors.Error,
    onBackground     = VigiaColors.OnBackground,
    onSurface        = VigiaColors.OnSurface,
)
```

### 2.2 Typography

```kotlin
// VigiaTypography.kt  
// Primary typeface: Inter (downloadable font via GMS)
// Monospaced (telemetry readouts): JetBrains Mono

private val InterFamily = FontFamily(
    Font(R.font.inter_regular,  FontWeight.Normal),
    Font(R.font.inter_medium,   FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)
private val MonoFamily = FontFamily(Font(R.font.jetbrainsmono_regular, FontWeight.Normal))

val VigiaTypography = Typography(
    headlineLarge  = TextStyle(fontFamily = InterFamily, fontSize = 28.sp, fontWeight = FontWeight.SemiBold),
    headlineMedium = TextStyle(fontFamily = InterFamily, fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge      = TextStyle(fontFamily = InterFamily, fontSize = 16.sp, fontWeight = FontWeight.Normal),
    bodyMedium     = TextStyle(fontFamily = InterFamily, fontSize = 14.sp, fontWeight = FontWeight.Normal),
    labelSmall     = TextStyle(
        fontFamily   = InterFamily,
        fontSize     = 11.sp,
        fontWeight   = FontWeight.Medium,
        letterSpacing = 0.08.em,
    ),
    // Telemetry display — RRI score, lat/lon, velocity
    displayLarge   = TextStyle(fontFamily = MonoFamily, fontSize = 32.sp, fontWeight = FontWeight.Normal),
)
```

### 2.3 `VigiaTheme` Composable

```kotlin
@Composable
fun VigiaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = VigiaDarkColorScheme,
        typography  = VigiaTypography,
        shapes      = VigiaShapes,
        content     = content,
    )
}
```

---

## 3. AI Orb Component — Zero-Recomposition Architecture

### 3.1 Design Intent

The Orb is the ambient status indicator: it breathes slowly during normal operation, pulses rapidly during active hazard alerts, and dims when the hardware link is absent. All animation value reads occur **inside `graphicsLayer {}` lambdas** — the render thread mutates only the layer transform, never triggering Compose recomposition.

### 3.2 Full Implementation

```kotlin
// AiOrbComponent.kt
@Composable
fun AiOrb(
    modifier: Modifier = Modifier,
    state: OrbState = OrbState.Idle,
) {
    val targetScale = when (state) {
        OrbState.Idle    -> 1.0f
        OrbState.Active  -> 1.12f
        OrbState.Alert   -> 1.25f
        OrbState.Offline -> 0.80f
    }
    val targetAlpha = when (state) {
        OrbState.Offline -> 0.35f
        else             -> 1.0f
    }
    val pulseDuration = when (state) {
        OrbState.Alert -> 600
        OrbState.Active -> 1200
        else -> 1800
    }

    val infiniteTransition = rememberInfiniteTransition(label = "orb")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = targetScale * 0.92f,
        targetValue  = targetScale * 1.08f,
        animationSpec = infiniteRepeatable(
            animation  = tween(pulseDuration, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "orbScale",
    )

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.55f * targetAlpha,
        targetValue  = 0.95f * targetAlpha,
        animationSpec = infiniteRepeatable(
            animation  = tween(pulseDuration, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "orbAlpha",
    )

    // CRITICAL: graphicsLayer lambda — reads pulseScale and glowAlpha on the render thread.
    // The Canvas below is composed ONCE and never recomposed by animation value changes.
    Box(
        modifier = modifier
            .size(180.dp)
            .graphicsLayer {
                scaleX = pulseScale
                scaleY = pulseScale
                alpha  = glowAlpha
            },
    ) {
        // Outer glow ring — blurred, large radius
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Additional blur pass for glow effect — render thread only
                    renderEffect = BlurMaskFilter(40f, BlurMaskFilter.Blur.NORMAL).toRenderEffect()
                },
        ) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x80A78BFA), Color(0x4038BDF8), Color.Transparent),
                    radius = size.minDimension / 1.5f,
                ),
            )
        }

        // Core orb — crisp, smaller radius
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFA78BFA), Color(0xFF38BDF8), Color.Transparent),
                    radius = size.minDimension / 2.8f,
                ),
            )
        }
    }
}

enum class OrbState { Idle, Active, Alert, Offline }
```

### 3.3 Recomposition Verification

During development, wrap with `RecompositionHighlighter` (custom modifier logging recomposition count via `SideEffect`). Target: **0 recompositions per animation cycle** after initial composition. Verify using Android Studio Layout Inspector → "Show recomposition counts".

---

## 4. Screen Architecture

### 4.1 `CopilotUiState`

```kotlin
sealed interface CopilotUiState {
    data object Loading : CopilotUiState
    data class Active(
        val orbState: OrbState,
        val devicePresent: Boolean,
        val rriScore: RriScore,
        val velocityMs: Float,
        val locationSnapshot: LocationSnapshot?,
        val pendingAlerts: List<HazardAlert>,
    ) : CopilotUiState
    data class Error(val message: String) : CopilotUiState
}
```

### 4.2 `CopilotViewModel`

```kotlin
@HiltViewModel
class CopilotViewModel @Inject constructor(
    private val cdmRepository: CdmPresenceRepository,
    private val bleRepository: BleRepository,
    private val mqttAlertRepository: MqttAlertRepository,
    private val contextAggregator: ContextAggregator,
    private val vigiaSearchRepository: VigiaSearchRepository,
) : ViewModel() {

    val uiState: StateFlow<CopilotUiState> = combine(
        cdmRepository.presenceState,
        bleRepository.linkState,
        mqttAlertRepository.alerts,
    ) { presence, link, alert ->
        CopilotUiState.Active(
            orbState      = link.toOrbState(),
            devicePresent = presence == DevicePresenceState.Present,
            rriScore      = RriScore(0f),   // will be sourced from bleRepository frames
            velocityMs    = 0f,
            locationSnapshot = null,
            pendingAlerts = listOfNotNull(alert),
        )
    }
    .catch { emit(CopilotUiState.Error(it.message ?: "Unknown error")) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CopilotUiState.Loading)

    fun onSearch(query: String) {
        viewModelScope.launch {
            contextAggregator.contextFor(query).collect { ctx ->
                vigiaSearchRepository.search(ctx)
            }
        }
    }
}

private fun BleLinkState.toOrbState() = when (this) {
    is BleLinkState.Bound       -> OrbState.Active
    is BleLinkState.Error       -> OrbState.Offline
    is BleLinkState.Idle        -> OrbState.Offline
    else                        -> OrbState.Idle
}
```

### 4.3 `CopilotScreen` Layout

```kotlin
@Composable
internal fun CopilotScreen(
    uiState: CopilotUiState,
    onSearch: (String) -> Unit,
    onPayTap: () -> Unit,
) {
    VigiaTheme {
        Scaffold(
            containerColor = VigiaColors.Background,
        ) { padding ->
            when (uiState) {
                CopilotUiState.Loading -> CircularProgressIndicator(color = VigiaColors.Primary)

                is CopilotUiState.Active -> Column(
                    modifier = Modifier.padding(padding).fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(48.dp))
                    AiOrb(state = uiState.orbState)
                    Spacer(Modifier.height(32.dp))
                    TelemetryRow(rriScore = uiState.rriScore, velocity = uiState.velocityMs)
                    Spacer(Modifier.height(24.dp))
                    SearchBar(onSearch = onSearch)
                    Spacer(Modifier.weight(1f))
                    PayButton(onClick = onPayTap)
                    Spacer(Modifier.height(24.dp))
                }

                is CopilotUiState.Error -> Text(
                    text  = uiState.message,
                    color = VigiaColors.Error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
```

---

## 5. Stripe Financial Connections Integration

### 5.1 Architecture

```
CopilotViewModel.onPayTap()
        │
        ▼
StripePayRepository.createFinancialConnectionsSession()
  (calls VIGIA backend → Stripe API → returns client_secret)
        │
        ▼
StripePaySheet (Composable)
  collectBankAccountLauncher.launch(params)
        │
        ▼
Stripe SDK renders Financial Connections sheet
  (native sheet — not a WebView)
        │
        ▼
Result: bank account token → StripePayRepository.confirmPayment()
```

### 5.2 `StripePaySheet` Composable

```kotlin
@Composable
internal fun StripePaySheet(
    clientSecret: String,
    billingDetails: PaymentSheet.BillingDetails,
    onResult: (FinancialConnectionsSheetResult) -> Unit,
) {
    val launcher = rememberFinancialConnectionsSheetLauncher(onResult)

    LaunchedEffect(clientSecret) {
        launcher.present(
            configuration = FinancialConnectionsSheet.Configuration(
                financialConnectionsSessionClientSecret = clientSecret,
                merchantDisplayName = "VIGIA Copilot",
            )
        )
    }
}
```

### 5.3 Security Constraints

- `clientSecret` sourced exclusively from the VIGIA backend — never from local config.
- Financial data flows only through `:core:network`; `:feature:copilot` receives opaque sealed states (`StripeFlowState.Success`, `StripeFlowState.Error`) only.
- Network Security Config (`res/xml/network_security_config.xml`) disallows cleartext — Stripe endpoints use TLS 1.2+.

---

## 6. Container Design Tokens

```kotlin
// Fine-line card style — applied to all surface containers
val VigiaCardModifier = Modifier
    .background(VigiaColors.Surface, shape = RoundedCornerShape(12.dp))
    .border(1.dp, VigiaColors.Outline, shape = RoundedCornerShape(12.dp))
    .padding(horizontal = 16.dp, vertical = 12.dp)

// Touch target compliance — minimum 48dp
val MinTouchTarget = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
```

---

## 7. Compose Expert & UI/UX Pro Max Validation Gates

Before marking Phase 4 complete:

1. **Compose Expert** rules applied:
   - All `State` reads scoped to the narrowest composable
   - `remember` wraps all object allocations inside composables
   - `key()` used in `LazyColumn` items with stable IDs
   - No lambdas that capture unstable types passed to composables without `remember`

2. **UI/UX Pro Max** validation:
   - All interactive elements ≥ 48×48dp touch target
   - Color contrast ratio ≥ 4.5:1 for body text on `#09090B` background
   - Edge-to-edge enforced (`WindowCompat.setDecorFitsSystemWindows(window, false)`)
   - Responsive layout tested at Compact / Medium / Expanded window size classes
   - Semantic content descriptions on all icon-only buttons

---

## 8. Verification Checklist

- [ ] Layout Inspector shows 0 recomposition count increments during orb pulse animation
- [ ] `VigiaTheme` applied globally — no hardcoded colors anywhere in `:feature:copilot`
- [ ] `CopilotScreen` renders correctly on Pixel 6 (compact), Pixel Fold (medium), and tablet (expanded)
- [ ] Stripe Financial Connections sheet opens in sandbox mode and returns `FinancialConnectionsSheetResult.Completed`
- [ ] All touch targets pass 48dp minimum (verified via Accessibility Scanner)
- [ ] `./gradlew :feature:copilot:assembleDebug` exits 0
- [ ] `./gradlew assembleDebug` (full project) exits 0
- [ ] R8 release build: `./gradlew assembleRelease` exits 0; no `ClassNotFoundException` at runtime
