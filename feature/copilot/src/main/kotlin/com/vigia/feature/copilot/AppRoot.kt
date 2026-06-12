package com.vigia.feature.copilot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

/**
 * App entry gate. Routes on the auth session:
 *   Loading  → branded splash while the cached session is restored
 *   SignedOut → [AuthScreen]
 *   SignedIn  → [CopilotRoute] (with sign-out + account wired into the drawer)
 *
 * Hardware/AI features live entirely behind this gate — an unauthenticated user
 * never reaches the copilot.
 */
@Composable
fun AppRoot() {
    val authViewModel: AuthViewModel = hiltViewModel()
    val authState by authViewModel.authState.collectAsStateWithLifecycle()
    val ui by authViewModel.ui.collectAsStateWithLifecycle()

    when (val state = authState) {
        AuthState.Loading -> SplashGate()

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

        is AuthState.SignedIn -> CopilotRoute(
            onSignOut    = authViewModel::signOut,
            accountName  = state.user.displayName,
            accountEmail = state.user.email,
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
