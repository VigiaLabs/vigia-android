package com.vigia.core.auth

import android.app.Activity
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.authDataStore by preferencesDataStore(name = "vigia_demo_auth")

/**
 * In-memory / DataStore auth backend used until a Cognito pool is provisioned —
 * the same "runnable demo" pattern the app uses for FCM, MQTT and Stripe.
 *
 * Behaviour mirrors a real Cognito flow so the UI is wired correctly:
 *   sign-up → ConfirmationRequired → confirm (any 6-digit code) → signed in.
 *   sign-in accepts any email + 8-char password; Google is simulated instantly.
 * The signed-in session is persisted to DataStore so the gate survives restarts.
 *
 * NOTE: this is intentionally permissive and stores no real credentials. It is
 * NOT a security boundary — production builds bind [AmplifyAuthRepository].
 */
@Singleton
class DemoAuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : AuthRepository {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    // email → display name, for accounts created this process (pre-confirmation).
    private val pendingSignUps = mutableMapOf<String, String>()

    override suspend fun restoreSession() {
        val prefs = context.authDataStore.data.first()
        val email = prefs[KEY_EMAIL]
        _authState.value = if (email != null) {
            AuthState.SignedIn(
                AuthUser(
                    userId      = prefs[KEY_USER_ID] ?: UUID.randomUUID().toString(),
                    email       = email,
                    displayName = prefs[KEY_NAME],
                ),
            )
        } else {
            AuthState.SignedOut
        }
    }

    override suspend fun signUp(email: String, password: String, displayName: String): AuthOutcome {
        validate(email, password)?.let { return AuthOutcome.Failure(it) }
        if (displayName.isBlank()) return AuthOutcome.Failure("Enter your name.")
        pendingSignUps[email.trim().lowercase()] = displayName.trim()
        return AuthOutcome.ConfirmationRequired
    }

    override suspend fun confirmSignUp(email: String, code: String): AuthOutcome {
        if (code.trim().length != 6 || code.any { !it.isDigit() }) {
            return AuthOutcome.Failure("Enter the 6-digit code.")
        }
        val key  = email.trim().lowercase()
        val name = pendingSignUps.remove(key) ?: key.substringBefore('@')
        persistSession(email = key, name = name)
        return AuthOutcome.Success
    }

    override suspend fun resendCode(email: String): AuthOutcome = AuthOutcome.Success

    override suspend fun signIn(email: String, password: String): AuthOutcome {
        validate(email, password)?.let { return AuthOutcome.Failure(it) }
        val key = email.trim().lowercase()
        persistSession(email = key, name = key.substringBefore('@'))
        return AuthOutcome.Success
    }

    override suspend fun signInWithGoogle(activity: Activity): AuthOutcome {
        persistSession(email = "rider@gmail.com", name = "VIGIA Rider")
        return AuthOutcome.Success
    }

    override suspend fun signOut() {
        context.authDataStore.edit { it.clear() }
        _authState.value = AuthState.SignedOut
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private suspend fun persistSession(email: String, name: String) {
        val userId = UUID.randomUUID().toString()
        context.authDataStore.edit {
            it[KEY_USER_ID] = userId
            it[KEY_EMAIL]   = email
            it[KEY_NAME]    = name
        }
        _authState.value = AuthState.SignedIn(AuthUser(userId, email, name))
    }

    private fun validate(email: String, password: String): String? = when {
        !email.contains('@') || !email.contains('.') -> "Enter a valid email address."
        password.length < 8                          -> "Password must be at least 8 characters."
        else                                         -> null
    }

    private companion object {
        val KEY_USER_ID = stringPreferencesKey("user_id")
        val KEY_EMAIL   = stringPreferencesKey("email")
        val KEY_NAME    = stringPreferencesKey("name")
    }
}
