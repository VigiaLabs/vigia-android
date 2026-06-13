plugins {
    alias(libs.plugins.vigia.android.feature)
}

android {
    namespace = "com.vigia.feature.copilot"
}

dependencies {
    implementation(project(":feature:maps"))

    // Feature modules access core layers only through interfaces
    implementation(project(":core:network"))
    implementation(project(":core:sensor"))
    implementation(project(":core:data"))
    implementation(project(":core:auth"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose.ui)
    implementation(libs.androidx.compose.ui.text.google.fonts)
    implementation(libs.androidx.compose.material.icons.extended)

    // Real backdrop blur for the liquid-glass dock (minSdk 34 → full RenderEffect path)
    implementation(libs.haze)

    // Stripe Financial Connections — boundary exception: StripePaySheet.kt is the sole
    // file in :feature:copilot that imports Stripe types. All financial data flows through
    // :core:network; the feature receives only opaque PayoutStatus sealed states.
    implementation(libs.stripe.financial.connections)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
