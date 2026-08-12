import java.util.Properties

plugins {
    alias(libs.plugins.vigia.android.application)
    alias(libs.plugins.vigia.android.application.compose)
    alias(libs.plugins.vigia.android.hilt)
    // alias(libs.plugins.google.services)  // enable in Phase 3 after google-services.json is provisioned
}

// A production release must never be assembled with placeholder endpoints or a
// dummy device identity. Debug variants intentionally remain buildable for local
// UI work; release automation should provide these values via CI environment vars
// (or a developer-only secrets.properties file).
tasks.register("validateProdConfiguration") {
    doLast {
        val secretsFile = rootProject.file("secrets.properties")
        val secrets = Properties().also { props ->
            if (secretsFile.exists()) secretsFile.inputStream().use(props::load)
        }
        fun configured(name: String): String {
            val envValue = System.getenv(name)
            if (!envValue.isNullOrBlank()) return envValue
            return secrets.getProperty(name).orEmpty()
        }

        val invalid = buildList {
            if (!configured("VIGIA_API_BASE_URL").startsWith("https://")) add("VIGIA_API_BASE_URL (HTTPS)")
            if (!configured("MQTT_BROKER_URI").startsWith("ssl://")) add("MQTT_BROKER_URI (TLS)")
            val mac = configured("BLACKBOX_MAC")
            if (!mac.matches(Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$")) ||
                mac == "00:00:00:00:00:00") add("BLACKBOX_MAC")
        }
        check(invalid.isEmpty()) {
            "Production configuration is incomplete: ${invalid.joinToString()}. " +
                "Provide CI environment variables before assembling prodRelease."
        }
    }
}

tasks.matching { it.name == "bundleProdRelease" || it.name == "assembleProdRelease" }.configureEach {
    dependsOn("validateProdConfiguration")
}

android {
    namespace = "com.vigia.copilot"

    defaultConfig {
        versionCode = 7
        versionName = "2.0-finale-r7"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
}

dependencies {
    implementation(project(":feature:copilot"))
    implementation(project(":feature:pairing"))
    implementation(project(":core:wallet"))
    implementation(project(":feature:maps"))
    implementation(libs.osmdroid.android)
    implementation(project(":core:sensor"))
    implementation(project(":core:network"))
    implementation(project(":core:model"))
    implementation(project(":core:auth"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose.ui)
    implementation(libs.bundles.lifecycle)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
