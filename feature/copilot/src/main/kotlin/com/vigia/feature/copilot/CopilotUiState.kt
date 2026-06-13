package com.vigia.feature.copilot

import com.vigia.core.model.DevicePresenceState
import com.vigia.core.model.HazardAlert
import com.vigia.core.model.LocationSnapshot
import com.vigia.core.model.RriScore
import com.vigia.core.network.search.SearchEvent
import com.vigia.core.network.stripe.PayoutStatus

sealed interface CopilotUiState {
    data object Loading : CopilotUiState

    data class Active(
        val orbState: OrbState,
        val devicePresent: Boolean,
        val rriScore: RriScore,
        val velocityMs: Float,
        val locationSnapshot: LocationSnapshot?,
        val pendingAlerts: List<HazardAlert>,
        val payoutStatus: PayoutStatus,
        // VIGIASearch streaming state
        val searchStep: String = "",
        val completedSteps: List<String> = emptyList(),
        val totalLatencyMs: Long = 0L,
        val searchAnswer: String = "",
        val searchSources: List<SearchEvent.Source> = emptyList(),
        val spatialMarkers: List<SearchEvent.SpatialMarker> = emptyList(),
        val isSearchStreaming: Boolean = false,
        // Voice mode state
        val isVoiceOverlayVisible: Boolean = false,
        val voiceAmplitude: Float = 0f,        // 0..1 normalised RMS from mic
        val voiceListeningState: VoiceListeningState = VoiceListeningState.Idle,
    ) : CopilotUiState

    data class Error(val message: String) : CopilotUiState
}

enum class OrbState {
    Idle,
    Active,
    Alert,
    Offline,
    Searching,
    Listening,   // user is speaking — aurora mist reacts to voice amplitude
}

enum class VoiceListeningState {
    Idle,        // overlay not active
    Listening,   // mic open, recording
    Processing,  // STT + search in flight
    Speaking,    // Sarvam TTS playing back the answer
}
