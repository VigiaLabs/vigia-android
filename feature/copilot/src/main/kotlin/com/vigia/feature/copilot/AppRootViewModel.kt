package com.vigia.feature.copilot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vigia.core.sensor.pairing.PairingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Drives the pairing gate in [AppRoot].
 *
 * [isPaired] emits:
 *   null  → DataStore not yet loaded (show splash)
 *   false → No pairing record → show PairingScreen
 *   true  → Paired → show CopilotRoute
 */
@HiltViewModel
class AppRootViewModel @Inject constructor(
    pairingRepository: PairingRepository,
) : ViewModel() {

    val isPaired: StateFlow<Boolean?> = pairingRepository.pairedConfig
        .map { config -> config != null }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    fun onPairingComplete() {
        // isPaired will automatically flip to true via the PairingRepository flow.
        // AppRoot recomposes and navigates to CopilotRoute without explicit action.
    }
}
