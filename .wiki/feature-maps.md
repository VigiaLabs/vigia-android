# feature:maps

**Layer:** Feature — entry  
**Package:** `com.vigia.feature.maps`  
**Path:** `feature/maps/`  
**Depends on:** [[core-network]] · [[core-sensor]]

OSM-based map feature. Renders hazard overlays, geohash grids, economic zones, maintenance POIs, route traces, and a BLE sensor status strip. Embedded inside [[feature-copilot]] via a Compose `NavHost` sub-graph.

## Architecture

```
MapsRoute  (Hilt entry)
  └── MapsScreen  (Compose, OSM MapView via AndroidView)
        ├── MapsSearchBar
        ├── MapsBottomSheet  (SheetTab: Hazards · Places · Traces · Economic)
        ├── SensorStatusStrip  (BLE link state + RRI score)
        ├── LayerToggleColumn  (MapLayer enum buttons)
        └── Map Layers:
              GeohashLayer · MaintenanceLayer · TracePlaybackLayer

MapsViewModel  (@HiltViewModel)
  ├── MapsRepository         (local interface)
  ├── ContextAggregator  → [[core-sensor]]
  └── BleRepository      → [[core-sensor]]
```

## Key Types

### ViewModel & State
| Type | Description |
|------|-------------|
| `MapsViewModel` | Loads hazards, geohash cells, routes, places; exposes `MapsUiState` |
| `MapsUiState` | All map UI state: hazards, cells, traces, POIs, economic zones, active layers, sensor strip |
| `SensorStripState` | RRI score, BLE link state, GPS accuracy — shown in the status strip |
| `SheetTab` | `Hazards · Places · Traces · Economic` — bottom sheet tabs |

### Repository
| Type | Description |
|------|-------------|
| `MapsRepository` | Interface: hazards, geohash resolve, places search, route, traces, economic metrics, maintenance queue |
| `MapsRepositoryImpl` | Calls `SessionApiService` + `InnovationApiService`; maps DTOs → [[core-model]] types |

### API Services (Retrofit)
| Service | Base URL | Endpoints |
|---------|----------|-----------|
| `SessionApiService` | `eepqy4yku7…/prod/` | `GET /hazards` · `POST /geohash/resolve` · `POST /places/search` · `POST /route` · `GET /device/traces` |
| `InnovationApiService` | `p4qc9upgsf…/prod/` | `GET /economic/metrics` · `GET /maintenance/queue` |
| `IngestionApiService` | `eepqy4yku7…/prod/` | Hazard ingestion (POST) |

Both Retrofit instances use `@Named("VigiaOkHttpClient")` — carries Cognito JWT via `VigiaAuthInterceptor` (from [[core-network]]).

### DTOs (internal to module)
`HazardDto · HazardsResponse · GeohashCellDto · GeohashResolveRequest/Response · PlaceDto · PlacesSearchRequest/Response · RouteRequest/Response · TraceFrameDto · TracesResponse · MaintenanceItemDto · MaintenanceQueueResponse · EconomicMetricsResponse · WaypointDto`

## Map Layers

| Layer | Data source | Renders |
|-------|-------------|---------|
| `GeohashLayer` | `MapsRepository.resolveGeohash()` | Coloured geohash grid cells |
| `MaintenanceLayer` | `MapsRepository.maintenanceQueue()` | Priority-coloured POI markers |
| `TracePlaybackLayer` | `MapsRepository.traces()` | Animated GPS trace replay |

## Backend Notes
- `GET /prod/hazards` requires Cognito `Authorization: Bearer` — returns 403 without it.
- `POST /prod/geohash/resolve` — Lambda was returning 502 (server-side crash); check CloudWatch.
- Both issues are AWS-side; Android auth interceptor is already wired correctly.

## Dependents
[[feature-copilot]] (embeds MapsRoute inside CopilotScreen bottom tab) · [[app]] (declares osmdroid dependency)
