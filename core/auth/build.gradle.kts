plugins {
    alias(libs.plugins.vigia.android.library)
    alias(libs.plugins.vigia.android.hilt)
}

android {
    namespace = "com.vigia.core.auth"
}

dependencies {
    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Session persistence for the demo auth backend
    implementation(libs.androidx.datastore.prefs)

    // Amplify Auth (Cognito User Pools + Hosted UI Google federation).
    // The real backend; bound only in builds where amplifyconfiguration is provisioned.
    implementation(libs.amplify.auth.cognito)

    // Credential Manager — native "Sign in with Google" account picker.
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.google.identity.googleid)

    testImplementation(libs.junit)
}
