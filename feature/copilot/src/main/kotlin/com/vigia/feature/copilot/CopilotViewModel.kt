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
import com.vigia.core.network.search.SearchEvent
import com.vigia.core.network.search.VigiaSearchClient
import com.vigia.core.network.stripe.PayoutStatus
import com.vigia.core.sensor.cdm.CdmPresenceRepository
import com.vigia.core.sensor.context.ContextAggregator
import com.vigia.core.sensor.tts.TtsManager
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
            payoutStatus     = PayoutStatus.Idle,
        )

        observeSensorContext()
        observeAlerts()
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

    // ── Private observers ─────────────────────────────────────────────────────

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
                        is SearchEvent.Step ->
                            updateActive {
                                copy(
                                    searchStep     = event.message,
                                    completedSteps = completedSteps + event.message,
                                )
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
                            // Clear streaming fields — the message now lives in Room
                            // and will appear in sessionMessages via Flow.
                            updateActive {
                                copy(
                                    orbState          = OrbState.Active,
                                    isSearchStreaming = false,
                                    searchStep        = "",
                                    searchAnswer      = "",
                                    searchSources     = emptyList(),
                                    completedSteps    = emptyList(),
                                    totalLatencyMs    = 0L,
                                )
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
