package com.vigia.feature.copilot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vigia.feature.copilot.voice.VoiceCallOverlay

/**
 * Entry point composable for the Copilot feature.
 *
 * Collects three independent StateFlows at the narrowest scope that needs each one,
 * then passes stable value types to the stateless [CopilotScreen].
 * [collectAsStateWithLifecycle] pauses collection when the lifecycle is stopped.
 *
 * When the voice overlay is active it renders over [CopilotScreen] in a [Box] so
 * the back stack and system bars remain undisturbed.
 */
@Composable
fun CopilotRoute(
    onSignOut: () -> Unit = {},
    accountName: String? = null,
    accountEmail: String? = null,
    viewModel: CopilotViewModel = hiltViewModel(),
) {
    val uiState         by viewModel.uiState.collectAsStateWithLifecycle()
    val sessions        by viewModel.sessions.collectAsStateWithLifecycle()
    val sessionMessages by viewModel.sessionMessages.collectAsStateWithLifecycle()
    val activeSessionId by viewModel.activeSessionId.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        CopilotScreen(
            uiState         = uiState,
            sessions        = sessions,
            sessionMessages = sessionMessages,
            activeSessionId = activeSessionId,
            onSendMessage   = viewModel::sendMessage,
            onCancelSearch  = viewModel::cancelSearch,
            onNewChat       = viewModel::newSession,
            onLoadSession   = viewModel::loadSession,
            onDeleteSession = viewModel::deleteSession,
            onSignOut       = onSignOut,
            onStartVoice    = viewModel::startVoiceMode,
            onEndVoice      = viewModel::endVoiceRecording,
            accountName     = accountName,
            accountEmail    = accountEmail,
        )

        val active = uiState as? CopilotUiState.Active
        if (active?.isVoiceOverlayVisible == true) {
            VoiceCallOverlay(
                voiceAmplitude = active.voiceAmplitude,
                listeningState = active.voiceListeningState,
                onSend         = viewModel::endVoiceRecording,
                onEnd          = viewModel::dismissVoiceOverlay,
                modifier       = Modifier.fillMaxSize(),
            )
        }
    }
}
