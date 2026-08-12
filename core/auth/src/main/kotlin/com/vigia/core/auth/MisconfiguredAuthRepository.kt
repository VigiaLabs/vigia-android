package com.vigia.core.auth

import android.app.Activity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production fail-closed auth implementation. It deliberately exposes no
 * local sign-in path when the identity provider is unavailable.
 */
@Singleton
class MisconfiguredAuthRepository @Inject constructor() : AuthRepository {

    private val _authState = MutableStateFlow<AuthState>(
        AuthState.ConfigurationError(
            "Authentication is unavailable. Please update the app or contact support.",
        ),
    )
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    override suspend fun restoreSession() {
        // Keep the blocking configuration state. There is no local session to trust.
        _authState.value = AuthState.ConfigurationError(
            "Authentication is unavailable. Please update the app or contact support.",
        )
    }

    override suspend fun signUp(email: String, password: String, displayName: String): AuthOutcome = unavailable()
    override suspend fun confirmSignUp(email: String, code: String): AuthOutcome = unavailable()
    override suspend fun resendCode(email: String): AuthOutcome = unavailable()
    override suspend fun signIn(email: String, password: String): AuthOutcome = unavailable()
    override suspend fun signInWithGoogle(activity: Activity): AuthOutcome = unavailable()

    override suspend fun signOut() {
        _authState.value = AuthState.ConfigurationError(
            "Authentication is unavailable. Please update the app or contact support.",
        )
    }

    override suspend fun getIdToken(): String? = null

    private fun unavailable() = AuthOutcome.Failure(
        "Authentication is unavailable. Please update the app or contact support.",
    )
}
