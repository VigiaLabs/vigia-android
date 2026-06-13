plugins {
    alias(libs.plugins.vigia.android.library)
    alias(libs.plugins.vigia.android.hilt)
}

android {
    namespace = "com.vigia.core.network"
    buildFeatures { buildConfig = true }
}

dependencies {
    implementation(project(":core:model"))

    // Network stack
    implementation(libs.bundles.networking)

    // MQTT — Eclipse Paho v3 (battle-tested on Android)
    implementation(libs.paho.mqtt.client)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Firebase FCM (wakeup path for Doze)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging.ktx)

    // Stripe — implementation detail only; never leaks to :feature:copilot
    implementation(libs.stripe.android)
    implementation(libs.stripe.financial.connections)

    // AWS
    implementation(libs.aws.sdk.core)

    // DataStore for encrypted rate-limit counters
    implementation(libs.androidx.datastore.prefs)

    testImplementation(libs.junit)
}
