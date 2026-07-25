# PHASE 1: Project Scaffolding & Dependency Catalog
**Status:** Pending  
**Prerequisite:** Phase 0 sign-off on `01_copilot_architecture_v2.md`  
**Exit Criteria:** `./gradlew assembleDebug` exits 0; all modules resolve without error

---

## 1. Deliverables

| Artifact | Path | Notes |
|---|---|---|
| Version catalog | `gradle/libs.versions.toml` | Single source of truth for all dependency versions |
| Root build file | `build.gradle.kts` | Minimal — only `buildscript` block and convention plugin classpath |
| Settings file | `settings.gradle.kts` | `includeBuild("build-logic")` + all module includes |
| Convention plugins | `build-logic/convention/` | AGP, Compose, Hilt, Library, Feature plugins |
| App module | `app/` | Thin launcher, Hilt root, NavHost |
| `:core:model` | `core/model/` | Pure Kotlin — no Android deps |
| `:core:network` | `core/network/` | Stripe, MQTT, AWS clients — interfaces + DI bindings |
| `:core:sensor` | `core/sensor/` | CDM, BLE, ForegroundService — interfaces + DI bindings |
| `:feature:copilot` | `feature/copilot/` | Empty shell — Route + ViewModel + UiState stubs |

---

## 2. `gradle/libs.versions.toml` — Pinned Versions

```toml
[versions]
agp                     = "8.5.2"
kotlin                  = "2.0.21"
ksp                     = "2.0.21-1.0.28"
compose-bom             = "2024.09.03"
hilt                    = "2.52"
room                    = "2.7.0"
navigation              = "2.8.3"
coroutines              = "1.9.0"
lifecycle               = "2.8.6"
datastore               = "1.1.1"
okhttp                  = "4.12.0"
retrofit                = "2.11.0"
firebase-bom            = "33.5.1"
stripe                  = "20.53.0"
paho-mqtt               = "1.1.1"
aws-sdk                 = "1.3.76"
accompanist             = "0.36.0"
core-ktx                = "1.13.1"
activity-compose        = "1.9.3"
material3               = "1.3.0"

[libraries]
# Kotlin
kotlinx-coroutines-android  = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-coroutines-core     = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core",    version.ref = "coroutines" }

# AndroidX Core
androidx-core-ktx           = { group = "androidx.core",          name = "core-ktx",                version.ref = "core-ktx" }
androidx-activity-compose   = { group = "androidx.activity",      name = "activity-compose",         version.ref = "activity-compose" }

# Compose BOM
androidx-compose-bom        = { group = "androidx.compose",       name = "compose-bom",              version.ref = "compose-bom" }
androidx-compose-ui         = { group = "androidx.compose.ui",    name = "ui" }
androidx-compose-ui-tooling = { group = "androidx.compose.ui",    name = "ui-tooling" }
androidx-compose-material3  = { group = "androidx.compose.material3", name = "material3",            version.ref = "material3" }
androidx-compose-animation  = { group = "androidx.compose.animation", name = "animation" }

# Lifecycle
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-lifecycle-runtime-compose   = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose",   version.ref = "lifecycle" }

# Navigation
androidx-navigation-compose = { group = "androidx.navigation",    name = "navigation-compose",       version.ref = "navigation" }

# Hilt
hilt-android                = { group = "com.google.dagger",      name = "hilt-android",             version.ref = "hilt" }
hilt-android-compiler       = { group = "com.google.dagger",      name = "hilt-android-compiler",    version.ref = "hilt" }
hilt-navigation-compose     = { group = "androidx.hilt",          name = "hilt-navigation-compose",  version = "1.2.0" }

# Room
androidx-room-runtime       = { group = "androidx.room",          name = "room-runtime",             version.ref = "room" }
androidx-room-ktx           = { group = "androidx.room",          name = "room-ktx",                 version.ref = "room" }
androidx-room-compiler      = { group = "androidx.room",          name = "room-compiler",             version.ref = "room" }

# DataStore
androidx-datastore-prefs    = { group = "androidx.datastore",     name = "datastore-preferences",    version.ref = "datastore" }

# Network — OkHttp / Retrofit
okhttp                      = { group = "com.squareup.okhttp3",   name = "okhttp",                   version.ref = "okhttp" }
okhttp-logging              = { group = "com.squareup.okhttp3",   name = "logging-interceptor",      version.ref = "okhttp" }
retrofit                    = { group = "com.squareup.retrofit2",  name = "retrofit",                 version.ref = "retrofit" }
retrofit-gson               = { group = "com.squareup.retrofit2",  name = "converter-gson",           version.ref = "retrofit" }

# Firebase
firebase-bom                = { group = "com.google.firebase",    name = "firebase-bom",             version.ref = "firebase-bom" }
firebase-messaging-ktx      = { group = "com.google.firebase",    name = "firebase-messaging-ktx" }

# Stripe
stripe-android              = { group = "com.stripe",             name = "stripe-android",            version.ref = "stripe" }
stripe-financial-connections = { group = "com.stripe",            name = "financial-connections",     version.ref = "stripe" }

# MQTT
paho-mqtt-android           = { group = "org.eclipse.paho",       name = "org.eclipse.paho.android.service", version.ref = "paho-mqtt" }
paho-mqtt-client            = { group = "org.eclipse.paho",       name = "org.eclipse.paho.client.mqttv3",   version.ref = "paho-mqtt" }

# AWS
aws-sdk-kotlin-core         = { group = "aws.sdk.kotlin",         name = "aws-core",                 version.ref = "aws-sdk" }

[plugins]
android-application         = { id = "com.android.application",   version.ref = "agp" }
android-library             = { id = "com.android.library",       version.ref = "agp" }
kotlin-android              = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose              = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
hilt-android                = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
ksp                         = { id = "com.google.devtools.ksp",   version.ref = "ksp" }
google-services             = { id = "com.google.gms.google-services", version = "4.4.2" }

[bundles]
compose                     = ["androidx-compose-ui", "androidx-compose-ui-tooling", "androidx-compose-material3", "androidx-compose-animation"]
lifecycle                   = ["androidx-lifecycle-viewmodel-compose", "androidx-lifecycle-runtime-compose"]
networking                  = ["okhttp", "okhttp-logging", "retrofit", "retrofit-gson"]
```

---

## 3. Convention Plugin Matrix

| Plugin Class | `build-logic` file | Applied to |
|---|---|---|
| `AndroidApplicationConventionPlugin` | `AndroidApplicationConventionPlugin.kt` | `:app` |
| `AndroidLibraryConventionPlugin` | `AndroidLibraryConventionPlugin.kt` | `:core:*` |
| `AndroidFeatureConventionPlugin` | `AndroidFeatureConventionPlugin.kt` | `:feature:*` |
| `AndroidComposeConventionPlugin` | `AndroidComposeConventionPlugin.kt` | Any module with Compose |
| `AndroidHiltConventionPlugin` | `AndroidHiltConventionPlugin.kt` | Any module with Hilt |

Each convention plugin sets:
- `compileSdk = 35`, `minSdk = 34`, `targetSdk = 35`
- `jvmToolchain(17)`
- `buildFeatures.buildConfig = true` (app module only)
- `testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"`

---

## 4. `settings.gradle.kts` Structure

```kotlin
pluginManagement {
    includeBuild("build-logic")
    repositories { google(); mavenCentral() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
    versionCatalogs { create("libs") { from(files("gradle/libs.versions.toml")) } }
}
rootProject.name = "vigia2"
include(":app")
include(":core:model")
include(":core:network")
include(":core:sensor")
include(":feature:copilot")
```

---

## 5. Module Responsibility Contracts

### `:core:model`
- **Allowed:** Pure Kotlin data classes, sealed interfaces, enums, value classes
- **Forbidden:** Any `android.*`, `androidx.*`, or framework imports
- **Exports:** `SpatialLatentVector`, `RriScore`, `HazardAlert`, `LocationSnapshot`, `VigiaSearchContext`, `BleLinkState`, `DevicePresenceState`

### `:core:network`
- **Allowed:** OkHttp, Retrofit, Paho MQTT, Stripe SDK, AWS SDK, Firebase Messaging, `javax.net.ssl.*`
- **Forbidden:** Compose, ViewModel, UI imports
- **Exports (via interfaces):** `VigiaSearchRepository`, `MqttAlertRepository`, `StripePayRepository`

### `:core:sensor`
- **Allowed:** `android.companion.*` (CDM), `android.bluetooth.*`, `android.app.Service`, Location APIs
- **Forbidden:** Compose, Stripe, MQTT (sensor layer only manages hardware)
- **Exports (via interfaces):** `CdmPresenceRepository`, `BleRepository`

### `:feature:copilot`
- **Allowed:** Compose, ViewModel, Hilt navigation, all `:core:*` interfaces
- **Forbidden:** Direct Android system service calls — must go through `:core:sensor` interfaces
- **Exports:** Navigation route key (public); all Screen/ViewModel implementation (internal)

---

## 6. Verification Checklist

- [ ] `./gradlew help` executes without error
- [ ] `./gradlew :app:assembleDebug` exits 0
- [ ] `./gradlew :core:model:build` exits 0 (no Android SDK required)
- [ ] `./gradlew :core:network:assembleDebug` exits 0
- [ ] `./gradlew :core:sensor:assembleDebug` exits 0
- [ ] `./gradlew :feature:copilot:assembleDebug` exits 0
- [ ] No `api()` leakage of `:core:sensor` or `:core:network` internals into `:feature:copilot`
- [ ] Version catalog used for all dependency declarations — no hardcoded version strings
