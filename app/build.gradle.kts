plugins {
    alias(libs.plugins.vigia.android.application)
    alias(libs.plugins.vigia.android.application.compose)
    alias(libs.plugins.vigia.android.hilt)
    // alias(libs.plugins.google.services)  // enable in Phase 3 after google-services.json is provisioned
}

android {
    namespace = "com.vigia.copilot"

    defaultConfig {
        versionCode = 1
        versionName = "1.0"
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