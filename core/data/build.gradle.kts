plugins {
    alias(libs.plugins.vigia.android.library)
    alias(libs.plugins.vigia.android.hilt)
}

android {
    namespace = "com.vigia.core.data"
}

dependencies {
    implementation(project(":core:model"))

    // Room — local chat history persistence
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.kotlinx.coroutines.android)
}
