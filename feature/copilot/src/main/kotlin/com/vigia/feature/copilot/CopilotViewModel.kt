package com.vigia.feature.copilot

import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vigia.core.data.ChatRepository
import com.vigia.core.network.stripe.StripePayRepository
import com.vigia.core.network.stripe.StripePayRepositoryImpl
import com.vigia.core.model.ChatMessage
import com.vigia.core.model.ChatSession
import com.vigia.core.model.ConversationTurn
import com.vigia.core.model.HazardAlert
import com.vigia.core.model.MessageRole
import com.vigia.core.model.MessageSource
import com.vigia.core.model.MessageStatus
import com.vigia.core.model.VigiaSearchContext
import com.vigia.core.network.mqtt.MqttAlertRepository
import com.vigia.core.network.sarvam.SarvamSttClient
import com.vigia.core.network.search.SearchEvent
import com.vigia.core.network.search.VigiaSearchClient
import com.vigia.core.sensor.cdm.CdmPresenceRepository
import com.vigia.core.sensor.context.ContextAggregator
import com.vigia.core.sensor.tts.TtsManager
import com.vigia.core.model.BleLinkState
import com.vigia.core.model.DriverProfile
import com.vigia.core.sensor.adas.FatigueProxyScorer
import com.vigia.core.sensor.adas.HarshEventLogger
import com.vigia.core.sensor.adas.SpeedCurveAdvisor
import com.vigia.core.sensor.ble.BleDataStreamer
import com.vigia.core.sensor.ble.BleRepository
import com.vigia.core.sensor.profile.DriverProfileRepository
import com.vigia.core.sensor.voice.BargeInController
import com.vigia.core.sensor.voice.ConversationContextManager
import com.vigia.core.sensor.voice.LaneDriftDetector
import com.vigia.core.sensor.voice.LiveVadEngine
import com.vigia.core.sensor.voice.RouteAheadMonitor
import com.vigia.core.sensor.voice.VoiceAmplitudeMonitor
import com.vigia.core.wallet.WalletRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

internal fun isVoiceSessionStopCommand(transcript: String): Boolean {
    val normalized = transcript
        .lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9' ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
    val exactCommands = setOf(
        "stop",
        "stop speaking",
        "stop listening",
        "stop conversation",
        "end conversation",
        "end the conversation",
        "thank you",
        "thanks",
        "ok thank you",
        "okay thank you",
        "that's all",
        "that is all",
        "goodbye",
        "bye",
    )
    return normalized in exactCommands ||
        normalized.matches(Regex("^(please |ok |okay )?stop( please)?$")) ||
        normalized.matches(Regex("^(ok |okay )?(thank you|thanks)( very much)?( vigia)?$"))
}

@HiltViewModel
class CopilotViewModel @Inject constructor(
    private val searchClient: VigiaSearchClient,
    private val mqttAlertRepository: MqttAlertRepository,
    private val contextAggregator: ContextAggregator,
    private val ttsManager: TtsManager,
    private val cdmRepository: CdmPresenceRepository,
    private val chatRepository: ChatRepository,
    private val sarvamSttClient: SarvamSttClient,
    private val voiceAmplitudeMonitor: VoiceAmplitudeMonitor,
    private val liveVadEngine: LiveVadEngine,
    private val bargeInController: BargeInController,
    private val laneDriftDetector: LaneDriftDetector,
    private val routeAheadMonitor: RouteAheadMonitor,
    private val conversationContextManager: ConversationContextManager,
    private val walletRepository: WalletRepository,
    private val stripePayRepository: StripePayRepository,
    private val driverProfileRepository: DriverProfileRepository,
    private val harshEventLogger: HarshEventLogger,
    private val bleRepository: BleRepository,
    private val bleDataStreamer: BleDataStreamer,
    private val fatigueProxyScorer: FatigueProxyScorer,
    private val speedCurveAdvisor: SpeedCurveAdvisor,
) : ViewModel() {

    val payoutStatus = stripePayRepository.payoutStatus

    private companion object {
        const val TAG = "VigiaCopilot"
        const val WALLET_POLL_INTERVAL_MS = 60_000L
        const val PROACTIVE_LABEL_CLEAR_MS = 4_000L
        const val BASE_TTC_S = 3.0f  // §3.2: EXPERT=1.5s, NEW=4.5s, ELDERLY=9.0s via S_profile
    }

    private val _uiState = MutableStateFlow<CopilotUiState>(CopilotUiState.Loading)
    val uiState: StateFlow<CopilotUiState> = _uiState.asStateFlow()

    private val _latestContext = MutableStateFlow<VigiaSearchContext?>(null)

    // ── Session state ─────────────────────────────────────────────────────────

    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()

    val sessions: StateFlow<List<ChatSession>> = chatRepository.allSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    val sessionMessages: StateFlow<List<ChatMessage>> = _activeSessionId
        .flatMapLatest { id ->
            if (id != null) chatRepository.messagesForSession(id) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    private var searchJob: Job? = null

    init {
        _uiState.value = CopilotUiState.Active(
            orbState         = OrbState.Idle,
            devicePresent    = false,
            rriScore         = com.vigia.core.model.RriScore(0f),
            velocityMs       = 0f,
            locationSnapshot = null,
            pendingAlerts    = emptyList(),
            walletUiState    = WalletUiState(isSyncing = true),
        )

        observeSensorContext()
        observeAlerts()
        observeTtsAmplitude()
        observeWalletState()
        observeRouteAhead()
        observeDriverProfile()
        observeBleLifecycle()
        startWalletPolling()
        startDrivingAssistance()
    }

    // ── Public API ────────────────────────────────────────────────────────────

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        val baseContext = _latestContext.value
        if (baseContext == null) {
            Log.w(TAG, "sendMessage DROPPED — sensor context not ready")
            val active = _uiState.value as? CopilotUiState.Active
            if (active?.isVoiceOverlayVisible == true) reopenAutoMic()
            return
        }

        viewModelScope.launch {
            val trimmed = text.trim()
            conversationContextManager.addUserTurn(trimmed)
            val sessionId = getOrCreateSessionId(trimmed)

            chatRepository.insertMessage(
                ChatMessage(
                    id        = UUID.randomUUID().toString(),
                    sessionId = sessionId,
                    role      = MessageRole.USER,
                    body      = trimmed,
                    createdAt = System.currentTimeMillis(),
                )
            )
            _activeSessionId.value = sessionId

            val context = baseContext.copy(
                queryText            = trimmed,
                timestampMs          = System.currentTimeMillis(),
                conversationHistory  = conversationContextManager.history().dropLast(1), // exclude current turn
                routeAheadHazards    = routeAheadMonitor.routeAheadHazards.value,
            )
            startSearch(context, sessionId)
        }
    }

    fun cancelSearch() {
        searchJob?.cancel()
        updateActive {
            copy(
                orbState          = OrbState.Active,
                isSearchStreaming = false,
                searchStep        = "",
                searchAnswer      = "",
            )
        }
    }

    fun loadSession(id: String) {
        searchJob?.cancel()
        _activeSessionId.value = id
        conversationContextManager.clear()
        updateActive {
            copy(
                orbState          = OrbState.Idle,
                isSearchStreaming = false,
                searchStep        = "",
                searchAnswer      = "",
                completedSteps    = emptyList(),
                totalLatencyMs    = 0L,
                searchSources     = emptyList(),
                spatialMarkers    = emptyList(),
            )
        }
    }

    fun newSession() {
        searchJob?.cancel()
        _activeSessionId.value = null
        conversationContextManager.clear()
        updateActive {
            copy(
                orbState          = OrbState.Active,
                isSearchStreaming = false,
                searchStep        = "",
                searchAnswer      = "",
                completedSteps    = emptyList(),
                totalLatencyMs    = 0L,
                searchSources     = emptyList(),
                spatialMarkers    = emptyList(),
            )
        }
    }

    fun deleteSession(id: String) {
        viewModelScope.launch {
            if (_activeSessionId.value == id) newSession()
            chatRepository.deleteSession(id)
        }
    }

    // ── Voice mode — manual (legacy tap-to-send) ──────────────────────────────

    fun startVoiceMode() {
        val current = _uiState.value as? CopilotUiState.Active ?: return
        if (current.voiceListeningState == VoiceListeningState.Listening ||
            current.voiceListeningState == VoiceListeningState.AutoListening ||
            current.voiceListeningState == VoiceListeningState.Processing) return

        voiceAmplitudeMonitor.startRecording()
        updateActive {
            copy(
                isVoiceOverlayVisible = true,
                voiceListeningState   = VoiceListeningState.Listening,
                orbState              = OrbState.Listening,
                isAutoVadActive       = false,
            )
        }
        viewModelScope.launch {
            voiceAmplitudeMonitor.amplitude.collect { amp ->
                updateActive { copy(voiceAmplitude = amp) }
            }
        }
    }

    /**
     * Gemini Live-style hands-free mode. Starts the VAD engine and monitors for
     * barge-in while AI is speaking. The user never needs to tap — just speak
     * naturally and the engine auto-detects the end of each utterance.
     */
    fun startAutoVoiceMode() {
        val current = _uiState.value as? CopilotUiState.Active ?: return
        if (current.voiceListeningState == VoiceListeningState.AutoListening ||
            current.voiceListeningState == VoiceListeningState.Processing) return

        liveVadEngine.start()
        updateActive {
            copy(
                isVoiceOverlayVisible = true,
                voiceListeningState   = VoiceListeningState.AutoListening,
                orbState              = OrbState.Listening,
                isAutoVadActive       = true,
            )
        }
        observeVadEvents()
    }

    fun endVoiceRecording() {
        val current = _uiState.value as? CopilotUiState.Active ?: return
        if (current.voiceListeningState != VoiceListeningState.Listening) return
        val wav = voiceAmplitudeMonitor.stopAndGetWav()
        updateActive {
            copy(
                voiceListeningState = VoiceListeningState.Processing,
                orbState            = OrbState.Searching,
                voiceAmplitude      = 0f,
            )
        }
        viewModelScope.launch {
            try {
                val transcript = sarvamSttClient.transcribe(wav)
                if (isVoiceSessionStopCommand(transcript)) {
                    dismissVoiceOverlay()
                } else if (transcript.isNotBlank() && _latestContext.value != null) {
                    sendMessage(transcript)
                } else {
                    reopenMic()
                }
            } catch (e: Exception) {
                Log.e(TAG, "STT error — reopening mic", e)
                reopenMic()
            }
        }
    }

    private fun reopenMic() {
        updateActive { copy(voiceListeningState = VoiceListeningState.Idle) }
        startVoiceMode()
    }

    private fun reopenAutoMic() {
        liveVadEngine.start()
        updateActive {
            copy(
                voiceListeningState = VoiceListeningState.AutoListening,
                orbState            = OrbState.Listening,
                voiceAmplitude      = 0f,
            )
        }
    }

    fun dismissVoiceOverlay() {
        voiceAmplitudeMonitor.stopSilently()
        liveVadEngine.stop()
        bargeInController.stopMonitoring()
        ttsManager.stop()
        updateActive {
            copy(
                isVoiceOverlayVisible = false,
                voiceAmplitude        = 0f,
                voiceListeningState   = VoiceListeningState.Idle,
                orbState              = OrbState.Active,
                isAutoVadActive       = false,
            )
        }
    }

    fun holdVoiceMode() {
        val current = _uiState.value as? CopilotUiState.Active ?: return
        if (current.voiceListeningState != VoiceListeningState.Listening &&
            current.voiceListeningState != VoiceListeningState.AutoListening &&
            current.voiceListeningState != VoiceListeningState.Speaking) return
        voiceAmplitudeMonitor.stopSilently()
        liveVadEngine.stop()
        bargeInController.stopMonitoring()
        ttsManager.stop()
        updateActive {
            copy(
                voiceListeningState = VoiceListeningState.Paused,
                voiceAmplitude      = 0f,
            )
        }
    }

    fun resumeVoiceMode() {
        val current = _uiState.value as? CopilotUiState.Active ?: return
        if (current.voiceListeningState != VoiceListeningState.Paused) return
        updateActive { copy(voiceListeningState = VoiceListeningState.Idle) }
        if (current.isAutoVadActive) startAutoVoiceMode() else startVoiceMode()
    }

    // ── VAD event wiring ──────────────────────────────────────────────────────

    private var vadObserveJob: Job? = null

    private fun observeVadEvents() {
        vadObserveJob?.cancel()
        vadObserveJob = viewModelScope.launch {
            liveVadEngine.events.collect { event ->
                when (event) {
                    is LiveVadEngine.VadEvent.Ready ->
                        ttsManager.playListeningCue()

                    is LiveVadEngine.VadEvent.AmplitudeUpdate ->
                        updateActive { copy(voiceAmplitude = event.rms) }

                    is LiveVadEngine.VadEvent.SpeechStart ->
                        updateActive { copy(orbState = OrbState.Listening) }

                    is LiveVadEngine.VadEvent.UtteranceComplete -> {
                        liveVadEngine.stop()  // stop VAD; search pipeline takes over
                        updateActive {
                            copy(
                                voiceListeningState = VoiceListeningState.Processing,
                                orbState            = OrbState.Searching,
                                voiceAmplitude      = 0f,
                            )
                        }
                        transcribeAndSearch(event.wav)
                    }
                }
            }
        }
    }

    private fun transcribeAndSearch(wav: ByteArray) {
        viewModelScope.launch {
            try {
                val transcript = sarvamSttClient.transcribe(wav)
                if (isVoiceSessionStopCommand(transcript)) {
                    dismissVoiceOverlay()
                } else if (transcript.isNotBlank() && _latestContext.value != null) {
                    sendMessage(transcript)
                } else {
                    reopenAutoMic()
                }
            } catch (e: Exception) {
                Log.e(TAG, "VAD STT error — reopening mic", e)
                reopenAutoMic()
            }
        }
    }

    private var bargeObserveJob: Job? = null

    private fun observeBargeIn() {
        bargeObserveJob?.cancel()
        bargeObserveJob = viewModelScope.launch {
            bargeInController.events.collectLatest { event ->
                when (event) {
                    is BargeInController.BargeInEvent.SpeechStart -> {
                        val active = _uiState.value as? CopilotUiState.Active ?: return@collectLatest
                        if (active.voiceListeningState != VoiceListeningState.Speaking) return@collectLatest
                        Log.d(TAG, "Barge-in detected — interrupting TTS")
                        updateActive {
                            copy(
                                voiceListeningState = VoiceListeningState.BargeIn,
                                orbState            = OrbState.Listening,
                                voiceAmplitude      = 0f,
                            )
                        }
                        ttsManager.stop()
                        ttsManager.playListeningCue()
                    }

                    is BargeInController.BargeInEvent.UtteranceComplete -> {
                        val active = _uiState.value as? CopilotUiState.Active ?: return@collectLatest
                        if (active.voiceListeningState != VoiceListeningState.BargeIn) return@collectLatest
                        bargeInController.stopMonitoring()
                        updateActive {
                            copy(
                                voiceListeningState = VoiceListeningState.Processing,
                                orbState            = OrbState.Searching,
                                voiceAmplitude      = 0f,
                            )
                        }
                        transcribeAndSearch(event.wav)
                    }
                }
            }
        }
    }

    // ── Driving assistance observers ──────────────────────────────────────────

    private fun startDrivingAssistance() {
        // Lane drift detection — feeds from GPS location flow.
        val locationFlow = contextAggregator.searchContext.map { it.location }
        laneDriftDetector.start(locationFlow)
        viewModelScope.launch {
            laneDriftDetector.events.collect { event ->
                when (event) {
                    is LaneDriftDetector.DriftEvent.DriftDetected -> {
                        Log.d(TAG, "Lane drift: ${event.message}")
                        ttsManager.speak(event.message, TextToSpeech.QUEUE_ADD)
                        updateActive {
                            copy(
                                proactiveLabel = "Lane drift detected",
                                orbState = if (orbState != OrbState.Alert) OrbState.Alert else orbState,
                            )
                        }
                        delay(PROACTIVE_LABEL_CLEAR_MS)
                        updateActive { copy(proactiveLabel = "") }
                    }
                }
            }
        }

        // Route-ahead monitor — start after first valid location arrives.
        routeAheadMonitor.start(locationFlow)

        // Fatigue proxy scorer — starts on first BLE connection, uses same location flow.
        fatigueProxyScorer.start(locationFlow, laneDriftDetector, viewModelScope)
        viewModelScope.launch {
            fatigueProxyScorer.events.collect { event ->
                val message = when (event) {
                    is FatigueProxyScorer.FatigueEvent.NudgeAlert    -> event.message
                    is FatigueProxyScorer.FatigueEvent.EscalateAlert -> event.message
                }
                val queueMode = if (event is FatigueProxyScorer.FatigueEvent.EscalateAlert)
                    TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                Log.d(TAG, "Fatigue alert (F=${(event as? FatigueProxyScorer.FatigueEvent.NudgeAlert)?.score ?: (event as FatigueProxyScorer.FatigueEvent.EscalateAlert).score}): $message")
                ttsManager.speak(message, queueMode)
                updateActive { copy(proactiveLabel = "Fatigue advisory") }
                delay(PROACTIVE_LABEL_CLEAR_MS)
                updateActive { copy(proactiveLabel = "") }
            }
        }

        // Speed/Curve advisor — fires on OSM geometry from RouteAheadMonitor
        viewModelScope.launch {
            speedCurveAdvisor.events.collect { event ->
                Log.d(TAG, "Geometry advisory: ${event.message}")
                ttsManager.speak(event.message, TextToSpeech.QUEUE_ADD)
                updateActive { copy(proactiveLabel = event.message.take(60)) }
                delay(PROACTIVE_LABEL_CLEAR_MS)
                updateActive { copy(proactiveLabel = "") }
            }
        }

        // Forward Collision Warning — ALERT_CHAR notifications from Pi edge node (M11).
        observeFcwEvents()
    }

    private fun observeFcwEvents() {
        viewModelScope.launch {
            bleDataStreamer.fcwEvents.collect { event ->
                val ttcFormatted = "%.1f".format(event.ttcSeconds)
                val message = "Brake — ${event.targetLabel} stopping ahead. T T C $ttcFormatted seconds."
                Log.w(TAG, "FCW: ${event.targetLabel} TTC=${event.ttcSeconds}s classId=${event.classId}")
                ttsManager.speak(message, TextToSpeech.QUEUE_FLUSH)
                updateActive {
                    copy(
                        proactiveLabel = "Collision Warning",
                        orbState = OrbState.Alert,
                    )
                }
                delay(PROACTIVE_LABEL_CLEAR_MS)
                updateActive { copy(proactiveLabel = "") }
            }
        }
    }

    private fun observeRouteAhead() {
        // Push route-ahead events as proactive TTS announcements.
        viewModelScope.launch {
            routeAheadMonitor.proactiveEvents.collect { event ->
                val message = when (event) {
                    is RouteAheadMonitor.ProactiveEvent.HazardAhead ->
                        event.message
                    is RouteAheadMonitor.ProactiveEvent.RoadQualityWarning ->
                        event.message
                }
                Log.d(TAG, "Proactive route alert: $message")
                ttsManager.speak(message, TextToSpeech.QUEUE_ADD)
                updateActive { copy(proactiveLabel = message.take(60)) }
                delay(PROACTIVE_LABEL_CLEAR_MS)
                updateActive { copy(proactiveLabel = "") }
            }
        }

        // Sync routeAheadHazards into UI for the LLM context display.
        viewModelScope.launch {
            routeAheadMonitor.routeAheadHazards.collect { hazards ->
                updateActive { copy(routeAheadHazards = hazards) }
            }
        }
    }

    // ── Driver profile & trip lifecycle ──────────────────────────────────────

    private fun observeDriverProfile() {
        viewModelScope.launch {
            driverProfileRepository.profile.collect { profile ->
                routeAheadMonitor.setProfile(profile)
                laneDriftDetector.setProfile(profile)
                ttsManager.setProfile(profile)
                // Push profile-scaled TTC threshold to edge (BaseTtc=3.0s × S_profile).
                val ttcThreshold = BASE_TTC_S * profile.sProfile
                runCatching { bleRepository.sendTtcThreshold(ttcThreshold) }
                Log.d(TAG, "Driver profile updated: ${profile.name} sProfile=${profile.sProfile} ttcThreshold=${ttcThreshold}s")
            }
        }
    }

    /** Called from UI when user selects a profile in onboarding or settings. */
    fun setDriverProfile(profile: DriverProfile) {
        viewModelScope.launch { driverProfileRepository.setProfile(profile) }
    }

    private var currentTripId: String? = null

    private fun observeBleLifecycle() {
        viewModelScope.launch {
            var wasBound = false
            bleRepository.linkState.collect { state ->
                val isBound = state is BleLinkState.Bound
                if (isBound && !wasBound) onBleConnected()
                if (!isBound && wasBound && state is BleLinkState.Idle) onBleDisconnected()
                wasBound = isBound
            }
        }
    }

    /** Called when BLE blackbox connects — starts a new trip. */
    fun onBleConnected() {
        val tripId = UUID.randomUUID().toString()
        currentTripId = tripId
        viewModelScope.launch {
            val profile = driverProfileRepository.profile.stateIn(viewModelScope).value
            harshEventLogger.startTrip(tripId, profile)
        }
    }

    /** Called when BLE blackbox disconnects (engine-off proxy) — ends trip and speaks debrief. */
    fun onBleDisconnected() {
        viewModelScope.launch {
            // pendingBalanceVigia in VIGIA tokens → rough INR for debrief text only (not financial)
            val balance = walletRepository.state.value.pendingBalanceVigia * 0.01
            val debrief = harshEventLogger.endTrip(earningsRupees = balance)
            currentTripId = null
            if (debrief != null) {
                Log.d(TAG, "Trip debrief: $debrief")
                // Flush any ongoing response before the debrief plays.
                ttsManager.speak(debrief, TextToSpeech.QUEUE_FLUSH)
                updateActive { copy(proactiveLabel = "Trip summary") }
                delay(PROACTIVE_LABEL_CLEAR_MS)
                updateActive { copy(proactiveLabel = "") }
            }
        }
    }

    // ── Private observers ─────────────────────────────────────────────────────

    private fun observeTtsAmplitude() {
        viewModelScope.launch {
            ttsManager.ttsAmplitude.collect { amp ->
                updateActive {
                    if (voiceListeningState == VoiceListeningState.Speaking) {
                        copy(voiceAmplitude = amp)
                    } else this
                }
            }
        }
    }

    private fun observeSensorContext() {
        viewModelScope.launch {
            contextAggregator.searchContext.collect { ctx ->
                _latestContext.value = ctx
                updateActive {
                    copy(
                        rriScore         = ctx.rriScore,
                        velocityMs       = ctx.velocityMs,
                        locationSnapshot = ctx.location,
                    )
                }
            }
        }
    }

    private fun observeAlerts() {
        viewModelScope.launch {
            mqttAlertRepository.alerts.collectLatest { alert ->
                updateActive { copy(pendingAlerts = (listOf(alert) + pendingAlerts).take(10)) }
                val queueMode = if (alert.severity == HazardAlert.Severity.CRITICAL)
                    TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                ttsManager.speak(alert.messageText, queueMode)
                if (alert.severity >= HazardAlert.Severity.HIGH) {
                    updateActive { copy(orbState = OrbState.Alert) }
                }
            }
        }
    }

    // ── Wallet ────────────────────────────────────────────────────────────────

    private fun observeWalletState() {
        viewModelScope.launch {
            walletRepository.state.collect { ws ->
                updateActive {
                    copy(
                        walletUiState = walletUiState.copy(
                            publicKey  = ws.publicKey,
                            balanceVga = ws.pendingBalanceVigia,
                            isSyncing  = ws.isSyncing,
                        )
                    )
                }
            }
        }
    }

    fun requestPayout() {
        val ws = walletRepository.state.value
        if (!ws.isProvisioned || ws.pendingBalanceMicroVigia <= 0) return
        val tsMs = System.currentTimeMillis()
        val sig  = walletRepository.signRaw(
            "VIGIA-PAYOUT:${ws.publicKey}:$tsMs".toByteArray(Charsets.UTF_8)
        )
        (stripePayRepository as? StripePayRepositoryImpl)?.setWalletProof(
            address   = ws.publicKey,
            timestamp = tsMs.toString(),
            signature = sig,
        )
        viewModelScope.launch {
            stripePayRepository.initiatePayment(
                amountCents = ws.pendingBalanceMicroVigia / 10_000L,
                currency    = "usd",
            )
        }
    }

    fun startStripeOnboarding() {
        viewModelScope.launch {
            val ws   = walletRepository.state.value
            val tsMs = System.currentTimeMillis()
            val sig  = walletRepository.signRaw(
                "VIGIA-PAYOUT:${ws.publicKey}:$tsMs".toByteArray(Charsets.UTF_8)
            )
            (stripePayRepository as? StripePayRepositoryImpl)?.setWalletProof(
                address   = ws.publicKey,
                timestamp = tsMs.toString(),
                signature = sig,
            )
            stripePayRepository.startConnectOnboarding()
        }
    }

    private fun startWalletPolling() {
        viewModelScope.launch {
            while (true) {
                walletRepository.refreshBalance()
                delay(WALLET_POLL_INTERVAL_MS)
            }
        }
    }

    // ── Search stream ─────────────────────────────────────────────────────────

    private fun startSearch(context: VigiaSearchContext, sessionId: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            updateActive {
                copy(
                    orbState          = OrbState.Searching,
                    isSearchStreaming = true,
                    searchAnswer      = "",
                    searchStep        = "",
                    completedSteps    = emptyList(),
                    totalLatencyMs    = 0L,
                    searchSources     = emptyList(),
                    evidenceClaims    = emptyList(),
                    offlineEvidence   = null,
                    spatialMarkers    = emptyList(),
                )
            }

            try {
                searchClient.search(context).collect { event ->
                    when (event) {
                        is SearchEvent.Step -> {
                            val isVoice = (_uiState.value as? CopilotUiState.Active)
                                ?.isVoiceOverlayVisible == true
                            updateActive {
                                copy(
                                    searchStep     = event.message,
                                    completedSteps = completedSteps + event.message,
                                )
                            }
                            if (isVoice && event.message.isNotBlank()) {
                                ttsManager.speakSarvam(event.message)
                            }
                        }

                        is SearchEvent.TextDelta ->
                            updateActive { copy(searchAnswer = searchAnswer + event.delta) }

                        is SearchEvent.Metadata ->
                            updateActive {
                                copy(
                                    searchSources  = event.sources,
                                    evidenceClaims = event.claims,
                                    offlineEvidence = event.offline,
                                    spatialMarkers = event.spatialMarkers,
                                    totalLatencyMs = event.totalLatencyMs,
                                )
                            }

                        SearchEvent.Done -> {
                            val finalState = _uiState.value as? CopilotUiState.Active
                            val wasVoiceActive  = finalState?.isVoiceOverlayVisible == true
                            val wasAutoVad      = finalState?.isAutoVadActive == true

                            if (finalState != null) {
                                val answerText = finalState.searchAnswer
                                conversationContextManager.addAssistantTurn(answerText)
                                chatRepository.insertMessage(
                                    ChatMessage(
                                        id             = UUID.randomUUID().toString(),
                                        sessionId      = sessionId,
                                        role           = MessageRole.ASSISTANT,
                                        body           = answerText,
                                        sources        = finalState.searchSources.toMessageSources(),
                                        reasoningSteps = finalState.completedSteps,
                                        latencyMs      = finalState.totalLatencyMs,
                                        status         = MessageStatus.Complete,
                                        createdAt      = System.currentTimeMillis(),
                                    )
                                )
                                chatRepository.bumpSession(sessionId)
                            }

                            updateActive {
                                copy(
                                    orbState            = OrbState.Active,
                                    isSearchStreaming   = false,
                                    voiceListeningState = if (wasVoiceActive)
                                        VoiceListeningState.Speaking else VoiceListeningState.Idle,
                                    voiceAmplitude      = 0f,
                                    searchStep          = "",
                                    searchAnswer        = "",
                                    searchSources       = emptyList(),
                                    completedSteps      = emptyList(),
                                    totalLatencyMs      = 0L,
                                )
                            }

                            val answer = finalState?.searchAnswer.orEmpty()
                            if (answer.isNotBlank()) {
                                // Start barge-in monitor BEFORE TTS starts so even the first
                                // syllable of the answer can be interrupted.
                                if (wasVoiceActive) bargeInController.startMonitoring()
                                observeBargeIn()

                                ttsManager.speakSarvam(answer) {
                                    viewModelScope.launch {
                                        bargeInController.stopMonitoring()
                                        val currentVoiceState = (_uiState.value as? CopilotUiState.Active)
                                            ?.voiceListeningState
                                        if (currentVoiceState == VoiceListeningState.Speaking) {
                                            if (wasAutoVad) reopenAutoMic() else reopenMic()
                                        }
                                    }
                                }
                            } else if (wasVoiceActive) {
                                if (wasAutoVad) reopenAutoMic() else reopenMic()
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val partial = (_uiState.value as? CopilotUiState.Active)?.searchAnswer.orEmpty()
                if (partial.isNotBlank()) {
                    val steps = (_uiState.value as? CopilotUiState.Active)?.completedSteps
                        ?: emptyList()
                    chatRepository.insertMessage(
                        ChatMessage(
                            id             = UUID.randomUUID().toString(),
                            sessionId      = sessionId,
                            role           = MessageRole.ASSISTANT,
                            body           = partial,
                            sources        = emptyList(),
                            reasoningSteps = steps,
                            latencyMs      = 0L,
                            status         = MessageStatus.Partial,
                            createdAt      = System.currentTimeMillis(),
                        )
                    )
                    chatRepository.bumpSession(sessionId)
                }
                updateActive {
                    copy(
                        orbState          = OrbState.Active,
                        isSearchStreaming = false,
                        searchStep        = "",
                        searchAnswer      = "",
                    )
                }
            } finally {
                if ((_uiState.value as? CopilotUiState.Active)?.isSearchStreaming == true) {
                    updateActive {
                        copy(
                            orbState          = OrbState.Active,
                            isSearchStreaming = false,
                            searchStep        = "",
                        )
                    }
                }
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private suspend fun getOrCreateSessionId(firstMessage: String): String {
        val existing = _activeSessionId.value
        if (existing != null) return existing
        val id  = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        chatRepository.createSession(
            ChatSession(
                id        = id,
                title     = firstMessage.take(60).trimEnd(),
                createdAt = now,
                updatedAt = now,
            )
        )
        return id
    }

    private fun List<SearchEvent.Source>.toMessageSources(): List<MessageSource> =
        map { MessageSource(id = it.id, url = it.url, label = it.label, trustLevel = it.trustLevel) }

    private fun updateActive(block: CopilotUiState.Active.() -> CopilotUiState.Active) {
        _uiState.update { if (it is CopilotUiState.Active) it.block() else it }
    }
}
