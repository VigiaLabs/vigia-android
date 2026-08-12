package com.vigia.copilot

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.vigia.feature.copilot.AppRoot
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* ContextAggregator re-checks on next collect; no action needed here */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // UI/UX Pro Max: edge-to-edge enforced here; CopilotScreen handles inset padding.
        enableEdgeToEdge()

        locationPermissionRequest.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
            )
        )

        setContent {
            AppRoot(
                bypassSignIn = BuildConfig.DEMO_BYPASS_AUTH,
                configurationError = productionConfigurationError(),
            )
        }
    }

    private fun productionConfigurationError(): String? {
        if (BuildConfig.DEMO_BYPASS_AUTH) return null
        val missing = buildList {
            if (!BuildConfig.VIGIA_API_BASE_URL.startsWith("https://")) add("VIGIA_API_BASE_URL (HTTPS)")
            if (!BuildConfig.MQTT_BROKER_URI.startsWith("ssl://")) add("MQTT_BROKER_URI (TLS)")
            if (!BuildConfig.BLACKBOX_MAC.matches(Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$")) ||
                BuildConfig.BLACKBOX_MAC == "00:00:00:00:00:00") add("BLACKBOX_MAC")
        }
        return missing.takeIf { it.isNotEmpty() }?.joinToString(
            prefix = "Production configuration is incomplete: ",
            separator = ", ",
        )
    }
}
