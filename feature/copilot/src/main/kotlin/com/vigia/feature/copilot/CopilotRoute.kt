package com.vigia.feature.copilot

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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

    // Auto (Gemini Live-style) voice mode launcher — same permission gate.
    val autoMicLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.startAutoVoiceMode()
        } else {
            Toast.makeText(
                context,
                "Microphone permission is required for voice mode",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
    val onStartAutoVoiceWithPermission = remember(autoMicLauncher) {
        { autoMicLauncher.launch(Manifest.permission.RECORD_AUDIO) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CopilotScreen(
            uiState             = uiState,
            sessions            = sessions,
            sessionMessages     = sessionMessages,
            activeSessionId     = activeSessionId,
            onSendMessage       = viewModel::sendMessage,
            onCancelSearch      = viewModel::cancelSearch,
            onNewChat           = viewModel::newSession,
            onLoadSession       = viewModel::loadSession,
            onDeleteSession     = viewModel::deleteSession,
            onSignOut           = onSignOut,
            onStartVoice        = onStartVoiceWithPermission,
            onStartAutoVoice    = onStartAutoVoiceWithPermission,
            onEndVoice          = viewModel::endVoiceRecording,
            onCashOut           = viewModel::requestPayout,
            onOnboard           = viewModel::startStripeOnboarding,
            accountName         = accountName,
            accountEmail        = accountEmail,
        )

        val active = uiState as? CopilotUiState.Active
        AnimatedVisibility(
            visible = active?.isVoiceOverlayVisible == true,
            enter   = fadeIn(tween(320)) +
                      scaleIn(tween(380, easing = FastOutSlowInEasing), initialScale = 0.93f),
            exit    = fadeOut(tween(220)) +
                      scaleOut(tween(260, easing = FastOutSlowInEasing), targetScale = 0.95f),
        ) {
            VoiceCallOverlay(
                voiceAmplitude  = active?.voiceAmplitude ?: 0f,
                listeningState  = active?.voiceListeningState ?: VoiceListeningState.Idle,
                isAutoVad       = active?.isAutoVadActive == true,
                proactiveLabel  = active?.proactiveLabel ?: "",
                onSend          = viewModel::endVoiceRecording,
                onHold          = viewModel::holdVoiceMode,
                onResume        = viewModel::resumeVoiceMode,
                onEnd           = viewModel::dismissVoiceOverlay,
                onSwitchToLive  = onStartAutoVoiceWithPermission,
                modifier        = Modifier.fillMaxSize(),
            )
        }
    }
}
