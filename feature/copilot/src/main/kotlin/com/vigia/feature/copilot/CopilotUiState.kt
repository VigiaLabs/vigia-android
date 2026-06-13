package com.vigia.feature.copilot

import com.vigia.core.model.DevicePresenceState
import com.vigia.core.model.HazardAlert
import com.vigia.core.model.LocationSnapshot
import com.vigia.core.model.RriScore
import com.vigia.core.network.search.SearchEvent

sealed interface CopilotUiState {
    data object Loading : CopilotUiState

    data class Active(
        val orbState: OrbState,
        val devicePresent: Boolean,
        val rriScore: RriScore,
        val velocityMs: Float,
        val locationSnapshot: LocationSnapshot?,
        val pendingAlerts: List<HazardAlert>,
        val walletUiState: WalletUiState = WalletUiState(),
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

// ── Wallet UI state ───────────────────────────────────────────────────────────

data class WalletUiState(
    val publicKey: String = "",
    val balanceVga: Double = 0.0,
    val pendingRewards: List<PendingReward> = emptyList(),
    val recentActivity: List<WalletActivity> = emptyList(),
    val isSyncing: Boolean = false,
)

data class PendingReward(
    val detectionId: String,
    val amountVga: Double,
    val label: String,
    val timestampMs: Long,
)

data class WalletActivity(
    val txSignature: String,
    val type: Type,
    val amountVga: Double,
    val label: String,
    val timestampMs: Long,
) {
    enum class Type { MINT, BURN }
}
