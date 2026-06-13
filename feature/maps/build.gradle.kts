plugins {
    alias(libs.plugins.vigia.android.feature)
}

android {
    namespace = "com.vigia.feature.maps"
}

dependencies {
    implementation(project(":core:network"))
    implementation(project(":core:sensor"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose.ui)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.haze)

    // OSMDroid — OpenStreetMap tile engine, no API key required
    implementation(libs.osmdroid.android)

    // Networking (reuse shared OkHttpClient from :core:network via Hilt)
    implementation(libs.bundles.networking)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
