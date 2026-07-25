# PHASE 5: Maps Page — Hazard-Intelligence Map Interface
**Status:** Pending  
**Prerequisite:** Phase 3 (MQTT + FCM + TTS) verified  
**New Module:** `:feature:maps`  
**Design Aesthetic:** Uber dark-map — full-bleed, minimal chrome, focus on the data  
**Exit Criteria:** Live hazard pins render from `IngestionHazardsGetter`, geohash grid resolves from `/geohash/resolve`, AI Bezier route animates from `GenerateBezierPath`, bottom sheet tabs switch without recomposition, BLE sensor strip updates at 1 Hz.

---

## 1. Deliverables

| Artifact | Path |
|---|---|
| `MapsScreen.kt` | `feature/maps/src/main/kotlin/com/vigia/feature/maps/MapsScreen.kt` |
| `MapsViewModel.kt` | `feature/maps/src/main/kotlin/com/vigia/feature/maps/MapsViewModel.kt` |
| `MapsUiState.kt` | `feature/maps/src/main/kotlin/com/vigia/feature/maps/MapsUiState.kt` |
| `MapsRoute.kt` | `feature/maps/src/main/kotlin/com/vigia/feature/maps/MapsRoute.kt` |
| `HazardLayer.kt` | `feature/maps/src/main/kotlin/com/vigia/feature/maps/layers/HazardLayer.kt` |
| `GeohashLayer.kt` | `feature/maps/src/main/kotlin/com/vigia/feature/maps/layers/GeohashLayer.kt` |
| `RouteLayer.kt` | `feature/maps/src/main/kotlin/com/vigia/feature/maps/layers/RouteLayer.kt` |
| `TracePlaybackLayer.kt` | `feature/maps/src/main/kotlin/com/vigia/feature/maps/layers/TracePlaybackLayer.kt` |
| `SensorStatusStrip.kt` | `feature/maps/src/main/kotlin/com/vigia/feature/maps/components/SensorStatusStrip.kt` |
| `MapsBottomSheet.kt` | `feature/maps/src/main/kotlin/com/vigia/feature/maps/components/MapsBottomSheet.kt` |
| `MapsRepository.kt` | `feature/maps/src/main/kotlin/com/vigia/feature/maps/data/MapsRepository.kt` |
| `MapsApiService.kt` | `feature/maps/src/main/kotlin/com/vigia/feature/maps/data/MapsApiService.kt` |
| `MapsModule.kt` (Hilt) | `feature/maps/src/main/kotlin/com/vigia/feature/maps/di/MapsModule.kt` |
| `build.gradle.kts` | `feature/maps/build.gradle.kts` |

---

## 2. Visual Design System

### 2.1 Aesthetic Principles (Uber-inspired)
- **Full-bleed map** — no persistent top app bar; UI elements float over the map as translucent cards
- **Backdrop blur** everywhere (`backdrop-filter: blur(16px)` equivalent via Compose `graphicsLayer`)
- **Dark map tiles** — custom map style with `#0d1117` land, `#0f1922` water, muted road colors
- **Glass surfaces** — `glassSurface` token from existing `VigiaColors` for all floating panels
- **One-thumb reach** — all interactive elements within bottom 60% of screen
- **Information density** — maximum data, minimal decoration

### 2.2 Color Semantic Layer
| Layer | Color | Token |
|---|---|---|
| CRITICAL hazard | `#ef4444` (red) | `colorError` |
| HIGH hazard | `#f97316` (orange) | custom `hazardHigh` |
| MEDIUM hazard | `#eab308` (yellow) | custom `hazardMedium` |
| LOW hazard | `#22c55e` (green) | custom `hazardLow` |
| AI Route | `#3b82f6` (blue) | `colorPrimary` |
| Geohash grid | `#6366f1` (indigo) | `colorSecondary` |
| BLE connected | `#4ade80` | custom `bleConnected` |
| BLE disconnected | `#6b7280` | custom `bleDisconnected` |

### 2.3 Typography
Inherit from `VigiaTypography`. Map-specific:
- Hazard callout: `labelLarge` bold, all-caps severity label
- Distance badge: `labelSmall` monospace
- Sheet tab: `labelMedium` 600 weight
- Coordinate/geohash: `labelSmall` monospace, 80% opacity

---

## 3. Screen Layout

```
┌─────────────────────────────────────────────┐
│ StatusBar (system, transparent)             │
├─────────────────────────────────────────────┤
│ SensorStatusStrip (48dp, translucent glass) │  ← always visible, 1Hz BLE update
├─────────────────────────────────────────────┤
│                                             │
│           Full-bleed MapView                │
│                                             │
│  [SearchBar - floating, 56dp tall]          │  ← top-start, 16dp margin
│                                             │
│  [LayerToggleColumn]  [ZoomControls]        │  ← end side, 80dp from top of map
│                                             │
│  [HazardLayer]                              │  ← rendered as Compose Canvas overlay
│  [GeohashLayer]                             │
│  [RouteLayer]                               │
│  [TracePlaybackLayer]                       │
│                                             │
│  [UserLocationDot + bearing cone]           │
│                                             │
│              [AiOrb - floating FAB]         │  ← end, above bottom sheet
│                                             │
├─────────────────────────────────────────────┤
│ MapsBottomSheet (BottomSheetScaffold)       │
│  peek: 120dp  |  half: 280dp  |  full: 80% │
│  Tabs: Alerts / Route / Zones / Traces      │
└─────────────────────────────────────────────┘
```

---

## 4. Core Models (additions to `:core:model`)

```kotlin
// MapLayer.kt
enum class MapLayer { HAZARDS, GEOHASH, ROUTE, MAINTENANCE, ECONOMIC, TRACES }

// GeohashCell.kt
data class GeohashCell(
    val hash: String,
    val latMin: Double, val latMax: Double,
    val lngMin: Double, val lngMax: Double,
    val hazardDensity: Float,   // 0..1 for heatmap tint
)

// BezierRoute.kt
data class BezierRoute(
    val waypoints: List<LatLng>,       // control points from GenerateBezierPath lambda
    val estimatedSeconds: Int,
    val hazardsAvoided: Int,
    val confidenceScore: Float,
)

// MaintenancePoi.kt
data class MaintenancePoi(
    val id: String,
    val location: LocationSnapshot,
    val priority: Priority,            // HIGH / MEDIUM / LOW
    val category: String,              // "road", "signage", "lighting"
    val reportedMs: Long,
) { enum class Priority { HIGH, MEDIUM, LOW } }

// SensorStripState.kt
data class SensorStripState(
    val bleConnected: Boolean,
    val rriScore: Float,               // 0..1
    val confidenceLabel: String,       // "High" / "Medium" / "Low"
    val velocityMs: Float,
    val accuracyMeters: Float,
)

// TraceFrame.kt
data class TraceFrame(
    val location: LocationSnapshot,
    val rriScore: Float,
    val timestampMs: Long,
)
```

---

## 5. `MapsUiState`

```kotlin
data class MapsUiState(
    val userLocation: LocationSnapshot? = null,
    val activeLayers: Set<MapLayer> = setOf(MapLayer.HAZARDS, MapLayer.ROUTE),
    val hazards: List<HazardAlert> = emptyList(),
    val geohashCells: List<GeohashCell> = emptyList(),
    val activeRoute: BezierRoute? = null,
    val maintenancePois: List<MaintenancePoi> = emptyList(),
    val traceFrames: List<TraceFrame> = emptyList(),
    val tracePlaybackIndex: Int = 0,
    val isTracePlaybackActive: Boolean = false,
    val sensorStrip: SensorStripState = SensorStripState(false, 0f, "Connecting…", 0f, 0f),
    val searchQuery: String = "",
    val selectedHazard: HazardAlert? = null,
    val activeSheetTab: SheetTab = SheetTab.ALERTS,
    val routeLoading: Boolean = false,
    val error: String? = null,
) {
    enum class SheetTab { ALERTS, ROUTE, ZONES, TRACES }
}
```

---

## 6. `MapsViewModel`

```kotlin
@HiltViewModel
class MapsViewModel @Inject constructor(
    private val mapsRepository: MapsRepository,
    private val contextAggregator: ContextAggregator,
    private val ttsManager: TtsManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapsUiState())
    val uiState: StateFlow<MapsUiState> = _uiState.asStateFlow()

    init {
        observeLocation()
        loadHazards()
    }

    private fun observeLocation() {
        viewModelScope.launch {
            contextAggregator.locationFlow.collect { snap ->
                _uiState.update { it.copy(
                    userLocation = snap,
                    sensorStrip  = it.sensorStrip.copy(
                        velocityMs     = snap.velocityMs,
                        accuracyMeters = snap.accuracyMeters,
                    )
                ) }
                // Auto-resolve geohash for current location
                resolveGeohash(snap.latitudeDeg, snap.longitudeDeg)
            }
        }
    }

    private fun loadHazards() {
        viewModelScope.launch {
            mapsRepository.getHazards().collect { hazards ->
                _uiState.update { it.copy(hazards = hazards) }
            }
        }
    }

    fun requestRoute(destination: LatLng) {
        viewModelScope.launch {
            _uiState.update { it.copy(routeLoading = true) }
            val origin = _uiState.value.userLocation ?: return@launch
            val route = mapsRepository.generateBezierRoute(
                originLat = origin.latitudeDeg, originLng = origin.longitudeDeg,
                destLat   = destination.lat,    destLng   = destination.lng,
            )
            _uiState.update { it.copy(activeRoute = route, routeLoading = false) }
        }
    }

    fun resolveGeohash(lat: Double, lng: Double) {
        viewModelScope.launch {
            val cells = mapsRepository.resolveGeohash(lat, lng)
            _uiState.update { it.copy(geohashCells = cells) }
        }
    }

    fun toggleLayer(layer: MapLayer) {
        _uiState.update { state ->
            val updated = if (layer in state.activeLayers)
                state.activeLayers - layer else state.activeLayers + layer
            state.copy(activeLayers = updated)
        }
    }

    fun selectHazard(hazard: HazardAlert) {
        _uiState.update { it.copy(selectedHazard = hazard) }
        ttsManager.speak(hazard.messageText, TtsManager.Priority.NORMAL)
    }

    fun startTracePlayback() { /* tick tracePlaybackIndex at 200ms intervals */ }
    fun stopTracePlayback()  { _uiState.update { it.copy(isTracePlaybackActive = false) } }

    fun loadMaintenancePois() {
        viewModelScope.launch {
            val pois = mapsRepository.getMaintenanceQueue()
            _uiState.update { it.copy(maintenancePois = pois) }
        }
    }
}
```

---

## 7. `MapsRepository`

```kotlin
interface MapsRepository {
    fun getHazards(): Flow<List<HazardAlert>>
    suspend fun resolveGeohash(lat: Double, lng: Double): List<GeohashCell>
    suspend fun searchPlaces(query: String): List<SearchPlace>
    suspend fun generateBezierRoute(originLat: Double, originLng: Double, destLat: Double, destLng: Double): BezierRoute
    suspend fun getMaintenanceQueue(): List<MaintenancePoi>
    suspend fun getEconomicMetrics(geohash: String): EconomicZone
    fun streamAgentTraces(): Flow<AgentTraceEvent>
}
```

### 7.1 API Wiring

| Method | AWS Endpoint | Lambda |
|---|---|---|
| `getHazards()` | Session API → Ingestion stack | `IngestionHazardsGetter` |
| `resolveGeohash()` | `POST /geohash/resolve` | `SessionGeohashResolverFunction` |
| `searchPlaces()` | `POST /places/search` | `SessionPlacesSearchFunction` |
| `generateBezierRoute()` | Innovation API via Orchestrator | `GenerateBezierPath` |
| `getMaintenanceQueue()` | `GET /maintenance/queue` | `MaintenanceQueueQueryFunction` |
| `getEconomicMetrics()` | `GET /economic/metrics` | `EconomicMetricsQueryFunction` |
| `streamAgentTraces()` | `GET /agent-traces/stream` (SSE) | `AgentTraceStreamerFunction` |

---

## 8. UI Components

### 8.1 `SensorStatusStrip`

Always-visible, sits below the system status bar. Translucent glass (`glassSurface`), 48dp tall.

```
┌─ BLE ● Pi5 ──────── RRI 0.87 ─── High Confidence ─── 14.2 km/h ─ ±3m ─┐
```

- Green dot pulses at 1 Hz when BLE connected; grey when disconnected
- Tapping opens a `ModalBottomSheet` with full sensor diagnostics
- RRI score drives a subtle gradient: green (>0.8) → yellow (0.5–0.8) → red (<0.5)

### 8.2 `SearchBar` (floating)

- 56dp tall, `shape = RoundedCornerShape(16.dp)`, `glassSurface` background
- Magnifier icon left, mic icon right (voice search via BedrockRouter)
- `AnimatedContent` transitions to a `SearchResultsList` on focus
- Results come from `/places/search`, tapping a result pins it and calls `requestRoute()`

### 8.3 `LayerToggleColumn`

Four icon buttons stacked vertically on the map end edge:

| Icon | Layer | Default |
|---|---|---|
| ⚠ | HAZARDS | ON |
| ⬡ | GEOHASH | OFF |
| 🔧 | MAINTENANCE | OFF |
| 📈 | ECONOMIC | OFF |

Each button uses `AnimatedContent` to show a filled/outlined variant. Active buttons have `colorPrimary` tint with a 12% alpha background.

### 8.4 `HazardLayer`

Drawn on a `Canvas` composable pinned over the map. For each `HazardAlert` with a non-null `locationSnapshot`:

- **Outer pulse ring**: `drawCircle` with `animateFloat(0f → 1.6f, infiniteRepeatable)` scale, alpha fades to 0
- **Inner severity ring**: always visible, stroke-only circle, severity-coded color
- **Icon**: `!` for CRITICAL, `▲` for HIGH, `~` for LOW
- **Tap target**: 48×48dp transparent Box wrapping the pin; calls `viewModel.selectHazard()`

### 8.5 `GeohashLayer`

Overlays a grid of `GeohashCell` rectangles on the map canvas:

- Cells colored by `hazardDensity`: low=transparent, high=`#ef444430`
- Cell border: `#6366f115` stroke, 1px
- Hash label rendered at `labelSmall` inside cell when zoom > threshold

### 8.6 `RouteLayer`

Animated Bezier path drawn as a `Canvas` `drawPath`:

1. **Glow pass**: wide stroke (20px), `colorPrimary` at 15% alpha
2. **Main stroke**: 4px, `colorPrimary` at 90% alpha
3. **Animated dash**: `PathEffect.dashPathEffect([16f, 8f], animatedPhase)` creates flowing motion
4. **Origin/Destination markers**: 12dp filled circles with white border

### 8.7 `TracePlaybackLayer`

- Renders `traceFrames[0..tracePlaybackIndex]` as a polyline in `colorSecondary`
- A scrubber `Slider` appears at the bottom of the map area when active
- Frame dots colored by RRI score at that frame
- Play/Pause FAB overlays the scrubber

### 8.8 `MapsBottomSheet` — Tab Content

#### Alerts Tab
`LazyColumn` of `AlertCard`:
- Severity color bar (left 4dp inset)
- Message text (`bodyMedium`)
- Distance from user + relative timestamp
- Swipe-right to dismiss (removes from local state, does not affect server)
- Tap calls `viewModel.selectHazard()` → TTS

#### Route Tab
- Current `BezierRoute` summary card: ETA, distance, hazards avoided
- Step-by-step turn list from RoutingAgent
- "Recalculate" button re-invokes `generateBezierRoute()`

#### Zones Tab
- `LazyVerticalGrid` of `EconomicZone` cards
- Zone type, investment score, `CheckZoneRegulation` compliance badge
- Tap expands inline with `AnimatedContent`

#### Traces Tab
- Timeline `LazyColumn` of recorded traces
- Each entry shows timestamp + RRI score sparkline
- "Play on Map" button sets `isTracePlaybackActive = true`

---

## 9. `AiOrb` Integration

Reuse existing `AiOrb` composable from `feature/copilot`. Position: `Alignment.BottomEnd`, offset 16dp from sheet peek. Tapping:

1. Opens an inline voice input overlay (existing copilot sheet)
2. Query prefilled with map context: `"Hazard-aware route to [destination] from [currentGeohash]"`
3. Response annotated on map via `AgentTraceStreamer` SSE stream — shows AI reasoning as animated text overlays on relevant map cells

---

## 10. Map Engine

Use **Mapbox Maps SDK for Android** (or **OSMDroid** as a zero-cost fallback):

- Custom `MapStyle` JSON: dark base matching `#0d1117` / `#0f1922` palette
- All domain overlays (hazards, routes, geohash) are drawn via **Compose Canvas** layered over the map view — not Mapbox layers — to keep animation logic in Kotlin/Compose

```kotlin
@Composable
fun MapsScreen(viewModel: MapsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        VigiaMapView(
            userLocation = uiState.userLocation,
            modifier     = Modifier.fillMaxSize(),
        )
        // Compose layers drawn on top of native map
        if (MapLayer.HAZARDS in uiState.activeLayers)
            HazardLayer(hazards = uiState.hazards, onSelect = viewModel::selectHazard)
        if (MapLayer.GEOHASH in uiState.activeLayers)
            GeohashLayer(cells = uiState.geohashCells)
        if (MapLayer.ROUTE in uiState.activeLayers && uiState.activeRoute != null)
            RouteLayer(route = uiState.activeRoute!!)

        SensorStatusStrip(state = uiState.sensorStrip, modifier = Modifier.align(Alignment.TopCenter))
        SearchBar(query = uiState.searchQuery, modifier = Modifier.align(Alignment.TopStart).padding(top=56.dp,start=16.dp,end=16.dp))
        LayerToggleColumn(active = uiState.activeLayers, onToggle = viewModel::toggleLayer, modifier = Modifier.align(Alignment.CenterEnd))

        AiOrb(modifier = Modifier.align(Alignment.BottomEnd).padding(end=16.dp, bottom=136.dp))

        MapsBottomSheet(state = uiState, onTabSelect = viewModel::setSheetTab, onHazardSelect = viewModel::selectHazard)
    }
}
```

---

## 11. Performance Constraints

| Constraint | Target |
|---|---|
| Map frame rate | 60fps (no Compose recomposition on pan/zoom) |
| Hazard pin recomposition | Only on `hazards` list change (stable keys = `HazardAlert.id`) |
| `SensorStatusStrip` update | 1Hz coroutine tick, `@Stable` wrapper |
| Route animation | `drawPath` on `Canvas` — zero recomposition |
| Bottom sheet tab switch | `AnimatedContent` with `ContentTransform` fade — no list rebuild |
| Geohash resolve latency | < 300ms (Lambda cold start budgeted; warm = ~40ms) |
| BLE strip → map update | < 100ms end-to-end from `ContextAggregator.locationFlow` |

---

## 12. Verification Checklist

- [ ] `HazardLayer` renders CRITICAL pin with pulsing ring at correct lat/lng
- [ ] `SensorStatusStrip` updates RRI and velocity at 1Hz when BLE connected
- [ ] `POST /geohash/resolve` returns neighbouring cells; `GeohashLayer` renders grid
- [ ] `generateBezierRoute()` returns smooth path; `RouteLayer` animates dash
- [ ] `MapsBottomSheet` Alerts tab shows hazards sorted by distance from user
- [ ] `TracePlaybackLayer` plays back frames in chronological order
- [ ] `AiOrb` tap opens copilot with map-context prefill
- [ ] Layer toggle buttons correctly show/hide each overlay without full recompose
- [ ] Dark map style loads; no white flash on screen entry
- [ ] `./gradlew :feature:maps:assembleDebug` exits 0
