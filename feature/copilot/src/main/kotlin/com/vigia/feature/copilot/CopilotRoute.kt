package com.vigia.feature.copilot

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vigia.feature.copilot.voice.VoiceCallOverlay

/**
 * Entry point composable for the Copilot feature.
 *
 * Handles the RECORD_AUDIO runtime permission before delegating to [CopilotScreen].
 * On Android 6+ the permission dialog fires the first time the mic button is tapped.
 * If denied, a toast explains why and voice mode is not entered. If granted,
 * [CopilotViewModel.startVoiceMode] is called and the overlay appears.
 *
 * The voice overlay renders over [CopilotScreen] in a [Box] so the back stack and
 * system bars remain undisturbed during a voice session.
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

    val context = LocalContext.current

    // Request RECORD_AUDIO at the point the user taps the mic button, not at app launch.
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.startVoiceMode()
        } else {
            Toast.makeText(
                context,
                "Microphone permission is required for voice mode",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    // Wrap startVoiceMode so the Route owns the permission gate — ViewModel never
    // touches Android permission APIs.
    val onStartVoiceWithPermission = remember(micPermissionLauncher) {
        { micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
    }

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
            onStartVoice    = onStartVoiceWithPermission,
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
