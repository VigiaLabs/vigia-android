plugins {
    alias(libs.plugins.vigia.android.library)
    alias(libs.plugins.vigia.android.hilt)
}

android {
    namespace = "com.vigia.core.sensor"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:network"))
    implementation(project(":core:wallet"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.datastore.prefs)
    implementation(libs.okhttp) // pairing/claim-device HTTP repositories

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.core)
}
