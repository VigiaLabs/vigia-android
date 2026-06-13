package com.vigia.copilot

import android.app.Application
import com.vigia.core.auth.AmplifyInitializer
import dagger.hilt.android.HiltAndroidApp
import org.osmdroid.config.Configuration
import java.io.File

@HiltAndroidApp
class VigiaApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        AmplifyInitializer.initialize(this)
        // OSMDroid requires this before any map view is inflated.
        Configuration.getInstance().apply {
            load(this@VigiaApplication, getSharedPreferences("osmdroid", MODE_PRIVATE))
            osmdroidTileCache = File(cacheDir, "osmdroid")
            userAgentValue = packageName
        }
    }
}
