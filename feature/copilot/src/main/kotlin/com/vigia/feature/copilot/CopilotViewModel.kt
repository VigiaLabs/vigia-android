package com.vigia.feature.copilot

import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vigia.core.data.ChatRepository
import com.vigia.core.model.ChatMessage
import com.vigia.core.model.ChatSession
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
import com.vigia.core.sensor.voice.VoiceAmplitudeMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

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
) : ViewModel() {

    private companion object { const val TAG = "VigiaCopilot" }

    private val _uiState = MutableStateFlow<CopilotUiState>(CopilotUiState.Loading)
    val uiState: StateFlow<CopilotUiState> = _uiState.asStateFlow()

    private val _latestContext = MutableStateFlow<VigiaSearchContext?>(null)

    // ── Session state — exposed to the Route as stable StateFlows ─────────────

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
            walletUiState    = WalletUiState(
                publicKey      = "7PTUbMJMWRwAixmkez2yBpsjovyAECtcXQHVYzAi8jf1",
                balanceVga     = 7241.500000,
                pendingRewards = listOf(
                    PendingReward(
                        detectionId = "det-001",
                        amountVga   = 2.0,
                        label       = "Medium hazard · confirming…",
                        timestampMs = System.currentTimeMillis() - 8_000L,
                    ),
                ),
                recentActivity = listOf(
                    WalletActivity(
                        txSignature = "4xHrK9mWz3Qp8NvLdY2sJfRtBcXeA7uMnG5oZiT1wPkCq6hVbE0yS",
                        type        = WalletActivity.Type.MINT,
                        amountVga   = 8.0,
                        label       = "Critical detection · first in area bonus",
                        timestampMs = System.currentTimeMillis() - 7_200_000L,
                    ),
                    WalletActivity(
                        txSignature = "9mKpL2WzN5vR8cXdQ4sTbJeYf6uAoGi3nHjMk1wPzCq7hBvE0yS",
                        type        = WalletActivity.Type.BURN,
                        amountVga   = 1.0,
                        label       = "AI Co-pilot session",
                        timestampMs = System.currentTimeMillis() - 86_400_000L,
                    ),
                    WalletActivity(
                        txSignature = "3rTsU7WxM4nO9bYcP6vQkZeAd2fLgJi5mNhKj8wRzDp1hCvF0yT",
                        type        = WalletActivity.Type.MINT,
                        amountVga   = 1.5,
                        label       = "Medium hazard · ×1.5 streak bonus",
                        timestampMs = System.currentTimeMillis() - 172_800_000L,
                    ),
                ),
            ),
        )

        observeSensorContext()
        observeAlerts()
        observeTtsAmplitude()
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Primary entry point for user messages. Creates a session on first send,
     * persists the USER message to Room, then kicks off the SSE search.
     *
     * Ordering is intentional: _activeSessionId is set AFTER the USER message
     * is already in Room so that when the composable switches to session mode,
     * sessionMessages immediately emits [USER] — no blank-thread window.
     */
    fun sendMessage(text: String) {
        if (text.isBlank()) return
        val baseContext = _latestContext.value
        if (baseContext == null) {
            Log.w(TAG, "sendMessage('${text.take(20)}') DROPPED — _latestContext is null (sensor context not ready)")
            // If in a voice session, reopen the mic so the overlay doesn't get stuck.
            val active = _uiState.value as? CopilotUiState.Active
            if (active?.isVoiceOverlayVisible == true) reopenMic()
            return
        }

        viewModelScope.launch {
            val trimmed = text.trim()
            val sessionId = getOrCreateSessionId(trimmed)

            // Persist USER message before switching the active session so the
            // Room Flow already has [USER] when the composable first observes it.
            chatRepository.insertMessage(
                ChatMessage(
                    id        = UUID.randomUUID().toString(),
                    sessionId = sessionId,
                    role      = MessageRole.USER,
                    body      = trimmed,
                    createdAt = System.currentTimeMillis(),
                )
            )

            // Switch to session mode now — Room already has the USER row.
            _activeSessionId.value = sessionId

            val context = baseContext.copy(
                queryText   = trimmed,
                timestampMs = System.currentTimeMillis(),
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

    /** Load a past session — clears any in-flight stream and switches the message feed. */
    fun loadSession(id: String) {
        searchJob?.cancel()
        _activeSessionId.value = id
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

    /** "New chat" — resets to the greeting home state with no active session. */
    fun newSession() {
        searchJob?.cancel()
        _activeSessionId.value = null
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

    // ── Voice mode ────────────────────────────────────────────────────────────

    /**
     * Opens the voice overlay and starts microphone capture.
     * Only starts if we are not already recording (guards against the auto-loop
     * firing while the session was dismissed mid-flight).
     * RECORD_AUDIO permission must be granted before calling — [CopilotRoute] owns that gate.
     */
    fun startVoiceMode() {
        val current = _uiState.value as? CopilotUiState.Active ?: return
        // Don't restart recording if already Listening or Processing.
        // Paused is allowed — resumeVoiceMode routes through here.
        if (current.voiceListeningState == VoiceListeningState.Listening ||
            current.voiceListeningState == VoiceListeningState.Processing) return

        voiceAmplitudeMonitor.startRecording()
        updateActive {
            copy(
                isVoiceOverlayVisible = true,
                voiceListeningState   = VoiceListeningState.Listening,
                orbState              = OrbState.Listening,
            )
        }
        // Collect live amplitude and push it into UI so AuroraMist responds in real-time.
        viewModelScope.launch {
            voiceAmplitudeMonitor.amplitude.collect { amp ->
                updateActive { copy(voiceAmplitude = amp) }
            }
        }
    }

    /**
     * Stops recording, transcribes via Sarvam STT, then runs the search pipeline.
     * The aurora mist stays visible and transitions to Processing state while STT runs.
     * On blank transcript or STT error the mic reopens for another attempt instead of
     * closing the overlay — the user stays in the conversational session.
     */
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
                if (transcript.isNotBlank() && _latestContext.value != null) {
                    sendMessage(transcript)  // triggers search → Sarvam TTS on Done
                } else {
                    // Blank transcript or sensor context not ready — reopen mic.
                    Log.w(TAG, "Voice: blank transcript or no context, reopening mic")
                    reopenMic()
                }
            } catch (e: Exception) {
                Log.e(TAG, "STT error — reopening mic", e)
                reopenMic()
            }
        }
    }

    /**
     * Resets voice state to Idle then immediately reopens the mic so the user
     * can speak again without leaving the voice session.
     * Called on blank STT result or network errors so the overlay never closes
     * unexpectedly mid-conversation.
     */
    private fun reopenMic() {
        // Reset to Idle first so startVoiceMode's guard passes.
        updateActive { copy(voiceListeningState = VoiceListeningState.Idle) }
        startVoiceMode()
    }

    /** Cancels recording, stops any queued/active TTS, and hides the overlay. */
    fun dismissVoiceOverlay() {
        voiceAmplitudeMonitor.stopSilently()
        ttsManager.stop()
        updateActive {
            copy(
                isVoiceOverlayVisible = false,
                voiceAmplitude        = 0f,
                voiceListeningState   = VoiceListeningState.Idle,
                orbState              = OrbState.Active,
            )
        }
    }

    /** Mutes the mic and pauses TTS — overlay stays open, session is preserved. */
    fun holdVoiceMode() {
        val current = _uiState.value as? CopilotUiState.Active ?: return
        if (current.voiceListeningState != VoiceListeningState.Listening &&
            current.voiceListeningState != VoiceListeningState.Speaking) return
        voiceAmplitudeMonitor.stopSilently()
        ttsManager.stop()
        updateActive {
            copy(
                voiceListeningState = VoiceListeningState.Paused,
                voiceAmplitude      = 0f,
            )
        }
    }

    /** Resumes recording after a hold — re-opens the mic for the next user turn. */
    fun resumeVoiceMode() {
        val current = _uiState.value as? CopilotUiState.Active ?: return
        if (current.voiceListeningState != VoiceListeningState.Paused) return
        updateActive { copy(voiceListeningState = VoiceListeningState.Idle) }
        startVoiceMode()
    }

    // ── Private observers ─────────────────────────────────────────────────────

    // Feeds TTS playback amplitude into voiceAmplitude during Speaking state so the
    // orb and aurora animate to the AI's voice exactly like they do to the user's.
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
                            // During voice sessions narrate each reasoning step so
                            // the user hears the agent thinking before the answer plays.
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
                                    spatialMarkers = event.spatialMarkers,
                                    totalLatencyMs = event.totalLatencyMs,
                                )
                            }

                        SearchEvent.Done -> {
                            // Capture final state before clearing streaming flag so we
                            // persist exactly what the user saw rendered.
                            val finalState = _uiState.value as? CopilotUiState.Active
                            val wasVoiceActive = finalState?.isVoiceOverlayVisible == true

                            if (finalState != null) {
                                chatRepository.insertMessage(
                                    ChatMessage(
                                        id             = UUID.randomUUID().toString(),
                                        sessionId      = sessionId,
                                        role           = MessageRole.ASSISTANT,
                                        body           = finalState.searchAnswer,
                                        sources        = finalState.searchSources.toMessageSources(),
                                        reasoningSteps = finalState.completedSteps,
                                        latencyMs      = finalState.totalLatencyMs,
                                        status         = MessageStatus.Complete,
                                        createdAt      = System.currentTimeMillis(),
                                    )
                                )
                                chatRepository.bumpSession(sessionId)
                            }

                            // Clear streaming fields — the message now lives in Room.
                            // Keep the voice overlay visible if it was open; TTS will play
                            // inside it and the onDone callback re-opens the mic for the
                            // next conversational turn.
                            updateActive {
                                copy(
                                    orbState          = OrbState.Active,
                                    isSearchStreaming = false,
                                    // Voice overlay stays open — transitions to Speaking state.
                                    voiceListeningState = if (wasVoiceActive)
                                        VoiceListeningState.Speaking else VoiceListeningState.Idle,
                                    voiceAmplitude   = 0f,
                                    searchStep       = "",
                                    searchAnswer     = "",
                                    searchSources    = emptyList(),
                                    completedSteps   = emptyList(),
                                    totalLatencyMs   = 0L,
                                )
                            }

                            // Speak the answer. onDone fires on IO thread after AudioTrack
                            // drains — jump back to Main and restart the mic for next turn.
                            val answer = finalState?.searchAnswer.orEmpty()
                            if (answer.isNotBlank()) {
                                ttsManager.speakSarvam(answer) {
                                    viewModelScope.launch {
                                        if (wasVoiceActive &&
                                            (_uiState.value as? CopilotUiState.Active)
                                                ?.isVoiceOverlayVisible == true) {
                                            // Auto-loop: reopen mic for next user turn.
                                            startVoiceMode()
                                        }
                                    }
                                }
                            } else if (wasVoiceActive) {
                                // Empty answer — just reopen the mic.
                                startVoiceMode()
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                // User-initiated cancel — do NOT flush partial tokens.
                throw e
            } catch (e: Exception) {
                // Network loss or unexpected error mid-stream.
                // Flush whatever partial tokens arrived so the session isn't empty.
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
                // Safety net: if the stream ever completes without delivering a
                // Done event (early server close, dropped terminal event), make
                // sure the "Generating response" indicator is never left stuck on.
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

    /** Returns the current active session ID, or creates a new session and returns its ID.
     *  Does NOT update _activeSessionId — the caller does that after persisting the USER message. */
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
