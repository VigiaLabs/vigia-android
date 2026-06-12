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
    ) : CopilotUiState

    data class Error(val message: String) : CopilotUiState
}

enum class OrbState { Idle, Active, Alert, Offline, Searching }
