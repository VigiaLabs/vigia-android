package com.vigia.core.auth

import android.app.Activity
import kotlinx.coroutines.flow.StateFlow

/**
 * The auth boundary. The feature layer observes [authState] and invokes these
 * suspend actions; it never sees Cognito/Amplify types.
 *
 * Two implementations exist:
 *  - [AmplifyAuthRepository] — real Cognito User Pools + Hosted-UI Google (prod).
 *  - [DemoAuthRepository]    — in-memory/DataStore session so the flow runs on
 *    device before a Cognito pool is provisioned (demo flavor).
 */
interface AuthRepository {

    /** Drives the app gate. Starts [AuthState.Loading] until [restoreSession] resolves. */
    val authState: StateFlow<AuthState>

    /** Re-checks any cached session at startup and emits SignedIn / SignedOut. */
    suspend fun restoreSession()

    suspend fun signUp(email: String, password: String, displayName: String): AuthOutcome

    suspend fun confirmSignUp(email: String, code: String): AuthOutcome

    suspend fun resendCode(email: String): AuthOutcome

    suspend fun signIn(email: String, password: String): AuthOutcome

    /** Hosted-UI federated Google sign-in; needs the hosting [Activity]. */
    suspend fun signInWithGoogle(activity: Activity): AuthOutcome

    suspend fun signOut()
}
