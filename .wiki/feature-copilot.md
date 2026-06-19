# feature:copilot

**Layer:** Feature — entry (only outbound calls)  
**Package:** `com.vigia.feature.copilot`  
**Path:** `feature/copilot/`  
**Depends on:** [[feature-maps]] · [[core-network]] · [[core-sensor]] · [[core-data]] · [[core-auth]]

The primary user-facing feature. Hosts the AI voice copilot overlay, chat thread UI, wallet dashboard, and the authentication flow.

## Architecture

```
CopilotRoute  (Hilt entry, permission gate)
  └── CopilotScreen  (stateless Compose UI)
  └── VoiceCallOverlay  (full-screen aurora overlay)
        └── AiOrbComponent  (reactive orb)
        └── AuroraMist  (Canvas aurora — BlendMode.Plus on dark bg)

CopilotViewModel  (@HiltViewModel)
  ├── VigiaSearchClient     → [[core-network]]
  ├── MqttAlertRepository   → [[core-network]]
  ├── SarvamSttClient       → [[core-network]]
  ├── TtsManager            → [[core-sensor]]
  ├── VoiceAmplitudeMonitor → [[core-sensor]]
  ├── ContextAggregator     → [[core-sensor]]
  ├── CdmPresenceRepository → [[core-sensor]]
  └── ChatRepository        → [[core-data]]
```

## Key Types

### State
| Type | Description |
|------|-------------|
| `CopilotUiState` | Sealed: `Loading · Active · Error` |
| `CopilotUiState.Active` | Full UI state snapshot — orb, voice, chat, wallet, alerts |
| `OrbState` | `Idle · Listening · Searching · Active · Alert` — drives orb visual |
| `VoiceListeningState` | `Idle · Listening · Paused · Processing · Speaking` |
| `WalletUiState` | Balance, pending rewards, recent activity |
| `WalletActivity` | `MINT` / `BURN` transaction with signature + label |
| `PendingReward` | In-flight detection reward awaiting confirmation |

### ViewModel
| Method | Description |
|--------|-------------|
| `sendMessage(text)` | Creates/reuses session → persists USER message → `startSearch()` |
| `startVoiceMode()` | Starts `AudioRecord`, opens overlay, collects mic amplitude |
| `endVoiceRecording()` | Stops mic → Sarvam STT → `sendMessage()` → TTS queue |
| `holdVoiceMode()` | Mutes mic + stops TTS; state → `Paused`; overlay stays open |
| `resumeVoiceMode()` | Restarts mic from `Paused` state |
| `dismissVoiceOverlay()` | Stops mic + TTS + drains queue; hides overlay |
| `observeTtsAmplitude()` | Feeds `ttsAmplitude` into `voiceAmplitude` during Speaking → aurora reacts to AI voice |

### Search pipeline (SSE)
```
SearchEvent.Step   → spoken via TtsManager.speakSarvam() if voice active
SearchEvent.TextDelta → accumulated into searchAnswer
SearchEvent.Metadata  → sources + spatialMarkers
SearchEvent.Done   → persist ChatMessage → TTS final answer → auto-reopen mic
```

## Voice Overlay — AuroraMist

- Background: always `Color(0xFF07060A)` — dark regardless of system theme
- Aurora blobs: 7 radial gradient circles, `BlendMode.Plus` (additive glow)
- Blob alpha: `0.54 × activity` (dark) — visible at all activity levels
- Activity formula per state:
  - Listening: `0.38 + voiceAmplitude × 0.62` (real mic RMS)
  - Processing: `0.52` (fixed breathe)
  - Speaking: `0.42 + voiceAmplitude × 0.58` (real TTS PCM RMS from [[core-sensor]])
  - Paused: `0.18`
- Tween: 55 ms `FastOutSlowInEasing` for tight amplitude tracking
- Open animation: `scaleIn(0.93→1.0, 380ms) + fadeIn(320ms)` via `AnimatedVisibility` in `CopilotRoute`

## Controls in VoiceCallOverlay

| Control | State | Action |
|---------|-------|--------|
| Centre orb | Listening | Tap → `endVoiceRecording()` |
| Hold button (64dp) | Listening / Speaking | Tap → `holdVoiceMode()`; green Mic icon when Paused → `resumeVoiceMode()` |
| Close button (64dp, red) | Any | `dismissVoiceOverlay()` — stops TTS immediately |

## Auth Flow
`AuthViewModel` → `AuthRepository` (from [[core-auth]]) → Cognito sign-in/sign-up/confirm screens in `auth/` sub-package.

## Theme
`VigiaExtendedColors` + `VigiaMotion` — custom Material3 extension accessed via `MaterialTheme.vigiaColors`.
