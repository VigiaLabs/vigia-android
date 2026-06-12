// Pure Kotlin module — no Android framework deps allowed.
// Enforced by using the kotlin("jvm") plugin, not android library.
plugins {
    kotlin("jvm")
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    testImplementation(libs.junit)
}
