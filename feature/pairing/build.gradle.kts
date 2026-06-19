plugins {
    alias(libs.plugins.vigia.android.feature)
    alias(libs.plugins.vigia.android.hilt)
}

android {
    namespace = "com.vigia.feature.pairing"
}

dependencies {
    implementation(project(":core:sensor"))
    implementation(project(":core:wallet")) // WalletRepository for post-pair provisioning

    // CameraX — live camera preview for QR scanning.
    implementation(libs.bundles.camerax)

    // ML Kit bundled barcode scanner — no Play Services dependency, works offline.
    implementation(libs.mlkit.barcode.scanning)

    implementation(libs.androidx.core.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose.ui)
    implementation(libs.bundles.lifecycle)
    implementation(libs.kotlinx.coroutines.android)
}
