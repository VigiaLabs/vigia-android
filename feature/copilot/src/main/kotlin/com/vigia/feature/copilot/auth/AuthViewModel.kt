package com.vigia.feature.copilot.auth

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vigia.core.auth.AuthOutcome
import com.vigia.core.auth.AuthRepository
import com.vigia.core.auth.AuthState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AuthStep { Welcome, SignIn, SignUp, Confirm }

data class AuthUiState(
    val step: AuthStep = AuthStep.Welcome,
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val code: String = "",
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val info: String? = null,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    /** Drives the app gate. */
    val authState: StateFlow<AuthState> = authRepository.authState
        .stateIn(viewModelScope, SharingStarted.Eagerly, AuthState.Loading)

    private val _ui = MutableStateFlow(AuthUiState())
    val ui: StateFlow<AuthUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch { authRepository.restoreSession() }
    }

    // ── form field updates ──────────────────────────────────────────────────
    fun onName(v: String)     = _ui.update { it.copy(name = v, error = null) }
    fun onEmail(v: String)    = _ui.update { it.copy(email = v, error = null) }
    fun onPassword(v: String) = _ui.update { it.copy(password = v, error = null) }
    fun onCode(v: String)     = _ui.update { it.copy(code = v.filter(Char::isDigit).take(6), error = null) }

    fun goTo(step: AuthStep) = _ui.update {
        it.copy(step = step, error = null, info = null, password = "", code = "")
    }

    // ── actions ──────────────────────────────────────────────────────────────
    fun signIn() = submit { authRepository.signIn(_ui.value.email, _ui.value.password) }

    fun signUp() = submit {
        authRepository.signUp(_ui.value.email, _ui.value.password, _ui.value.name)
    }

    fun confirm() = submit { authRepository.confirmSignUp(_ui.value.email, _ui.value.code) }

    fun signInWithGoogle(activity: Activity) = submit { authRepository.signInWithGoogle(activity) }

    fun resendCode() {
        viewModelScope.launch {
            authRepository.resendCode(_ui.value.email)
            _ui.update { it.copy(info = "Code re-sent to ${it.email}", error = null) }
        }
    }

    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }

    /**
     * Runs an auth action with shared submitting/error handling. On Success the
     * global [authState] flips and the gate swaps screens; on ConfirmationRequired
     * we advance to the Confirm step.
     */
    private fun submit(action: suspend () -> AuthOutcome) {
        viewModelScope.launch {
            _ui.update { it.copy(isSubmitting = true, error = null, info = null) }
            when (val outcome = action()) {
                AuthOutcome.Success ->
                    _ui.update { it.copy(isSubmitting = false) }
                AuthOutcome.ConfirmationRequired ->
                    _ui.update { it.copy(isSubmitting = false, step = AuthStep.Confirm, info = "We emailed a 6-digit code to ${it.email}") }
                is AuthOutcome.Failure ->
                    _ui.update { it.copy(isSubmitting = false, error = outcome.message) }
            }
        }
    }
}
