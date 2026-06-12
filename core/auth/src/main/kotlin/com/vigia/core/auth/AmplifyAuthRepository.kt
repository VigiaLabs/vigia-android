package com.vigia.core.auth

import android.app.Activity
import com.amplifyframework.auth.AuthProvider
import com.amplifyframework.auth.AuthUserAttribute
import com.amplifyframework.auth.AuthUserAttributeKey
import com.amplifyframework.auth.AuthException
import com.amplifyframework.auth.options.AuthSignUpOptions
import com.amplifyframework.auth.result.AuthSignInResult
import com.amplifyframework.auth.result.AuthSignUpResult
import com.amplifyframework.auth.result.step.AuthSignInStep
import com.amplifyframework.auth.result.step.AuthSignUpStep
import com.amplifyframework.core.Amplify
import com.amplifyframework.core.Consumer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Real Cognito User Pools backend via Amplify Auth. Bound only when Amplify has
 * been configured at startup ([AmplifyInitializer]); otherwise the app binds
 * [DemoAuthRepository].
 *
 * Email/password and Hosted-UI Google federation are both routed here. Cognito
 * groups (for future role gating) can be read from the access token's
 * `cognito:groups` claim — left empty under the current authenticated-only model.
 */
@Singleton
class AmplifyAuthRepository @Inject constructor() : AuthRepository {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    override suspend fun restoreSession() {
        _authState.value = try {
            val session = awaitAmplify<com.amplifyframework.auth.AuthSession> { ok, err ->
                Amplify.Auth.fetchAuthSession(ok, err)
            }
            if (session.isSignedIn) AuthState.SignedIn(loadCurrentUser()) else AuthState.SignedOut
        } catch (e: Exception) {
            AuthState.SignedOut
        }
    }

    override suspend fun signUp(email: String, password: String, displayName: String): AuthOutcome =
        runCatching {
            val options = AuthSignUpOptions.builder()
                .userAttributes(
                    listOf(
                        AuthUserAttribute(AuthUserAttributeKey.email(), email.trim()),
                        AuthUserAttribute(AuthUserAttributeKey.name(), displayName.trim()),
                    ),
                )
                .build()
            val result = awaitAmplify<AuthSignUpResult> { ok, err ->
                Amplify.Auth.signUp(email.trim(), password, options, ok, err)
            }
            if (result.isSignUpComplete) AuthOutcome.Success else AuthOutcome.ConfirmationRequired
        }.getOrElse { AuthOutcome.Failure(it.userMessage()) }

    override suspend fun confirmSignUp(email: String, code: String): AuthOutcome =
        runCatching {
            val result = awaitAmplify<AuthSignUpResult> { ok, err ->
                Amplify.Auth.confirmSignUp(email.trim(), code.trim(), ok, err)
            }
            if (result.nextStep.signUpStep == AuthSignUpStep.DONE) AuthOutcome.Success
            else AuthOutcome.Failure("Confirmation incomplete. Try again.")
        }.getOrElse { AuthOutcome.Failure(it.userMessage()) }

    override suspend fun resendCode(email: String): AuthOutcome =
        runCatching {
            awaitAmplify<Any> { ok, err ->
                Amplify.Auth.resendSignUpCode(email.trim(), { ok.accept(it) }, err)
            }
            AuthOutcome.Success
        }.getOrElse { AuthOutcome.Failure(it.userMessage()) }

    override suspend fun signIn(email: String, password: String): AuthOutcome =
        runCatching {
            val result = awaitAmplify<AuthSignInResult> { ok, err ->
                Amplify.Auth.signIn(email.trim(), password, ok, err)
            }
            finishSignIn(result)
        }.getOrElse { AuthOutcome.Failure(it.userMessage()) }

    override suspend fun signInWithGoogle(activity: Activity): AuthOutcome =
        runCatching {
            val result = awaitAmplify<AuthSignInResult> { ok, err ->
                Amplify.Auth.signInWithSocialWebUI(AuthProvider.google(), activity, ok, err)
            }
            finishSignIn(result)
        }.getOrElse { AuthOutcome.Failure(it.userMessage()) }

    override suspend fun signOut() {
        suspendCancellableCoroutine<Unit> { cont ->
            Amplify.Auth.signOut { cont.resume(Unit) }
        }
        _authState.value = AuthState.SignedOut
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private suspend fun finishSignIn(result: AuthSignInResult): AuthOutcome =
        if (result.isSignedIn || result.nextStep.signInStep == AuthSignInStep.DONE) {
            _authState.value = AuthState.SignedIn(loadCurrentUser())
            AuthOutcome.Success
        } else {
            AuthOutcome.ConfirmationRequired
        }

    private suspend fun loadCurrentUser(): AuthUser {
        val user = awaitAmplify<com.amplifyframework.auth.AuthUser> { ok, err ->
            Amplify.Auth.getCurrentUser(ok, err)
        }
        val attrs = runCatching {
            awaitAmplify<List<AuthUserAttribute>> { ok, err ->
                Amplify.Auth.fetchUserAttributes(ok, err)
            }
        }.getOrDefault(emptyList())
        val email = attrs.firstOrNull { it.key == AuthUserAttributeKey.email() }?.value ?: user.username
        val name  = attrs.firstOrNull { it.key == AuthUserAttributeKey.name() }?.value
        return AuthUser(userId = user.userId, email = email, displayName = name)
    }

    private fun Throwable.userMessage(): String =
        (this as? AuthException)?.recoverySuggestion?.takeIf { it.isNotBlank() }
            ?: this.message
            ?: "Something went wrong. Please try again."

    private suspend inline fun <T> awaitAmplify(
        crossinline block: (Consumer<T>, Consumer<AuthException>) -> Unit,
    ): T = suspendCancellableCoroutine { cont ->
        block(
            Consumer { value -> if (cont.isActive) cont.resume(value) },
            Consumer { error -> if (cont.isActive) cont.resumeWithException(error) },
        )
    }
}
