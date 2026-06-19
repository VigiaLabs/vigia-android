# core:model

**Layer:** Core — leaf (no outbound module dependencies)  
**Package:** `com.vigia.core.model`  
**Path:** `core/model/`

Pure Kotlin data classes and sealed types. Zero Android or third-party dependencies — everything else depends on this module, it depends on nothing.

## Domain Types

### BLE / Device presence
| Type | Description |
|------|-------------|
| `BleLinkState` | Sealed: `Idle · Scanning · Connecting · Pairing · Handshaking · Bound · Error` |
| `BleLinkError` | Typed BLE failure reasons |
| `DevicePresenceState` | `Present` / `Absent` / `Unknown` |

### Location & context
| Type | Description |
|------|-------------|
| `LocationSnapshot` | Lat/lng, accuracy, bearing, velocity, timestamp |
| `VigiaSearchContext` | Query text + location + RRI score + velocity — passed to the LangGraph pipeline |
| `RriScore` | Road-Risk Index `0..1 Float` |
| `BezierRoute` | List of `LatLng` waypoints for smooth path rendering |
| `SpatialLatentVector` | Embedding for geospatial ML inference |

### Map data
| Type | Description |
|------|-------------|
| `GeohashCell` | Geohash string + bounding box |
| `EconomicZone` | Named economic corridor overlay |
| `MapLayer` | Enum of toggleable map layers |
| `MaintenancePoi` | Road maintenance point of interest with `Priority` |
| `TraceFrame` | Historical GPS trace snapshot |
| `SearchPlace` | Place result from VIGIASearch |

### Chat / AI
| Type | Description |
|------|-------------|
| `ChatSession` | Conversation container (id, title, timestamps) |
| `ChatMessage` | Single message; `role: MessageRole`, `status: MessageStatus`, `reasoningSteps`, `sources` |
| `MessageRole` | `USER` / `ASSISTANT` |
| `MessageStatus` | `Pending · Complete · Error` |
| `MessageSource` | URL + label + trust level surfaced by LangGraph |

### Alerts
| Type | Description |
|------|-------------|
| `HazardAlert` | `id · severity · messageText · timestampMs · locationSnapshot` |
| `HazardAlert.Severity` | `LOW · MEDIUM · HIGH · CRITICAL` |

## Dependents
All modules depend on `core:model`:
[[core-network]] · [[core-sensor]] · [[core-data]] · [[feature-copilot]] · [[feature-maps]] · [[app]]
