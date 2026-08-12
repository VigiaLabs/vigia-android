package com.vigia.core.auth

import android.content.Context
import android.util.Log
import com.amplifyframework.auth.cognito.AWSCognitoAuthPlugin
import com.amplifyframework.core.Amplify

/**
 * Configures Amplify Auth once at app startup. Call from `Application.onCreate()`.
 *
 * Demo authentication is selected explicitly by the demo application flavour. A
 * production configuration failure is retained as an error and must never select
 * [DemoAuthRepository].
 */
object AmplifyInitializer {

    @Volatile
    var isConfigured = false
        private set

    @Volatile
    var isDemoBuild = false
        private set

    @Volatile
    var configurationError: String? = null
        private set

    fun initialize(context: Context, demoBuild: Boolean) {
        isDemoBuild = demoBuild
        if (demoBuild) {
            isConfigured = false
            configurationError = null
            Log.i(TAG, "Demo auth explicitly enabled by the demo application flavour.")
            return
        }
        if (isConfigured || configurationError != null) return
        try {
            Amplify.addPlugin(AWSCognitoAuthPlugin())
            Amplify.configure(context.applicationContext)
            isConfigured = true
            configurationError = null
            Log.i(TAG, "Amplify Auth configured — using Cognito backend.")
        } catch (e: Exception) {
            isConfigured = false
            configurationError = "Production authentication is unavailable. Check Cognito configuration."
            Log.e(TAG, "Amplify Auth configuration failed; production auth is blocked.", e)
        }
    }

    private const val TAG = "VigiaAuth"
}
