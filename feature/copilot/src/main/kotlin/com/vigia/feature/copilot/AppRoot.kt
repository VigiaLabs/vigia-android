package com.vigia.feature.copilot

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vigia.core.auth.AuthState
import com.vigia.feature.copilot.auth.AuthScreen
import com.vigia.feature.copilot.auth.AuthViewModel
import com.vigia.feature.copilot.orb.AiOrb
import com.vigia.feature.copilot.theme.VigiaTheme
import com.vigia.feature.pairing.PairingScreen

/**
 * App entry gate. Hardware pairing is optional and never blocks the copilot:
 *   Loading   → branded splash
 *   SignedOut → [AuthScreen]
 *   SignedIn  → [CopilotRoute]
 */
@Composable
fun AppRoot(
    bypassSignIn: Boolean = false,
    configurationError: String? = null,
) {
    if (configurationError != null) {
        ConfigurationErrorGate(configurationError)
        return
    }

    val authViewModel: AuthViewModel = hiltViewModel()
    val authState by authViewModel.authState.collectAsStateWithLifecycle()
    val ui        by authViewModel.ui.collectAsStateWithLifecycle()

    if (bypassSignIn) {
        AuthenticatedApp(
            accountName = "RoadWatch Demo",
            accountEmail = "Demo build · sign-in bypassed",
            onSignOut = {},
        )
        return
    }

    when (val state = authState) {
        AuthState.Loading -> SplashGate()

        is AuthState.ConfigurationError -> ConfigurationErrorGate(state.message)

        AuthState.SignedOut -> AuthScreen(
            ui         = ui,
            onEmail    = authViewModel::onEmail,
            onPassword = authViewModel::onPassword,
            onName     = authViewModel::onName,
            onCode     = authViewModel::onCode,
            onGoTo     = authViewModel::goTo,
            onSignIn   = authViewModel::signIn,
            onSignUp   = authViewModel::signUp,
            onConfirm  = authViewModel::confirm,
            onResend   = authViewModel::resendCode,
            onGoogle   = authViewModel::signInWithGoogle,
        )

        is AuthState.SignedIn -> {
            AuthenticatedApp(
                accountName = state.user.displayName,
                accountEmail = state.user.email,
                onSignOut = authViewModel::signOut,
            )
        }
    }
}

@Composable
private fun AuthenticatedApp(
    accountName: String?,
    accountEmail: String,
    onSignOut: () -> Unit,
) {
    var showPairing by rememberSaveable { mutableStateOf(false) }

    if (showPairing) {
        BackHandler { showPairing = false }
        PairingScreen(onPairingComplete = { showPairing = false })
    } else {
        CopilotRoute(
            onSignOut = onSignOut,
            onPairHardware = { showPairing = true },
            accountName = accountName,
            accountEmail = accountEmail,
        )
    }
}

@Composable
private fun SplashGate() {
    VigiaTheme {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        ) {
            AiOrb(state = OrbState.Searching, size = 120.dp)
        }
    }
}

@Composable
private fun ConfigurationErrorGate(message: String) {
    VigiaTheme {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp),
            ) {
                AiOrb(state = OrbState.Offline, size = 120.dp)
                Spacer(Modifier.height(24.dp))
                androidx.compose.material3.Text(
                    text = "VIGIA is unavailable",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
