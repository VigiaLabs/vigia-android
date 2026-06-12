// Root build file. All plugin versions live in gradle/libs.versions.toml.
// No dependencies or configuration belong here — use convention plugins in build-logic/.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library)     apply false
    alias(libs.plugins.kotlin.android)      apply false
    alias(libs.plugins.kotlin.compose)      apply false
    alias(libs.plugins.hilt.android)        apply false
    alias(libs.plugins.ksp)                 apply false
    alias(libs.plugins.google.services)     apply false
}