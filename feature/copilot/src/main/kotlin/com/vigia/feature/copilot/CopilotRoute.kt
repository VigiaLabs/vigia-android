package com.vigia.feature.copilot

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Entry point composable for the Copilot feature.
 *
 * Collects three independent StateFlows at the narrowest scope that needs each one,
 * then passes stable value types to the stateless [CopilotScreen].
 * [collectAsStateWithLifecycle] pauses collection when the lifecycle is stopped.
 */
@Composable
fun CopilotRoute(
    onSignOut: () -> Unit = {},
    accountName: String? = null,
    accountEmail: String? = null,
    viewModel: CopilotViewModel = hiltViewModel(),
) {
    val uiState        by viewModel.uiState.collectAsStateWithLifecycle()
    val sessions       by viewModel.sessions.collectAsStateWithLifecycle()
    val sessionMessages by viewModel.sessionMessages.collectAsStateWithLifecycle()
    val activeSessionId by viewModel.activeSessionId.collectAsStateWithLifecycle()

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
        accountName     = accountName,
        accountEmail    = accountEmail,
    )
}
