package com.vigia.core.auth

import android.content.Context
import android.util.Log
import com.amplifyframework.auth.cognito.AWSCognitoAuthPlugin
import com.amplifyframework.core.Amplify

/**
 * Configures Amplify Auth once at app startup. Call from `Application.onCreate()`.
 *
 * If `res/raw/amplifyconfiguration.json` is absent (no Cognito pool provisioned
 * yet), configuration fails gracefully and [isConfigured] stays false — the app
 * then binds [DemoAuthRepository], exactly like the FCM/MQTT demo fallbacks.
 */
object AmplifyInitializer {

    @Volatile
    var isConfigured = false
        private set

    fun initialize(context: Context) {
        if (isConfigured) return
        try {
            Amplify.addPlugin(AWSCognitoAuthPlugin())
            Amplify.configure(context.applicationContext)
            isConfigured = true
            Log.i(TAG, "Amplify Auth configured — using Cognito backend.")
        } catch (e: Exception) {
            isConfigured = false
            Log.w(TAG, "Amplify not configured (no amplifyconfiguration.json) — using demo auth. ${e.message}")
        }
    }

    private const val TAG = "VigiaAuth"
}
