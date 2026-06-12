package com.vigia.copilot

import android.app.Application
import com.vigia.core.auth.AmplifyInitializer
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class VigiaApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Configures Amplify Auth if amplifyconfiguration.json is provisioned;
        // otherwise no-ops and the app falls back to the demo auth backend.
        AmplifyInitializer.initialize(this)
    }
}
