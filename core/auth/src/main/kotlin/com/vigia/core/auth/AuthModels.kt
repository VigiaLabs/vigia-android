package com.vigia.core.auth

/**
 * The signed-in identity. [roles] carries the user's Cognito groups so feature
 * gating (e.g. a future `hardware` role for Blackbox pairing) becomes a config
 * change, not a refactor. Empty today — the product runs "authenticated-only".
 */
data class AuthUser(
    val userId: String,
    val email: String,
    val displayName: String?,
    val roles: Set<String> = emptySet(),
) {
    fun hasRole(role: String): Boolean = role in roles
}

/** Top-level session state observed by the app gate. */
sealed interface AuthState {
    /** Session is being restored at startup — show a splash, not the auth screen. */
    data object Loading : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val user: AuthUser) : AuthState
}

/** Result of an auth action. Cognito sign-up returns [ConfirmationRequired] first. */
sealed interface AuthOutcome {
    /** Signed in — [AuthState] will transition to SignedIn. */
    data object Success : AuthOutcome

    /** Account created but a verification code was emailed; go to the confirm step. */
    data object ConfirmationRequired : AuthOutcome

    /** Recoverable failure with a user-facing message. */
    data class Failure(val message: String) : AuthOutcome
}
