---
title: "build-logic (convention plugins)"
type: module
tags: [module, build]
source: "build-logic/convention/build.gradle.kts"
related: ["[[app-module]]", "[[adr-minsdk-34]]"]
updated: 2026-06-20
---

# build-logic (convention plugins)

Composite build providing 6 convention plugins via Gradle's `includeBuild`. JVM 17 target. All modules inherit `compileSdk=36`, `minSdk=34` (enforced in `KotlinAndroid.kt:8`), and test runner from a single central function.

## Plugins

| id | Class | Effect |
|---|---|---|
| `vigia.android.application` | `AndroidApplicationConventionPlugin` | AGP application; sets targetSdk=36, applicationId, product flavors demo/prod, BuildConfig secrets |
| `vigia.android.application.compose` | `AndroidApplicationComposeConventionPlugin` | Enables Compose compiler plugin on application modules |
| `vigia.android.library` | `AndroidLibraryConventionPlugin` | AGP library base |
| `vigia.android.library.compose` | `AndroidLibraryComposeConventionPlugin` | Enables Compose on library modules |
| `vigia.android.feature` | `AndroidFeatureConventionPlugin` | Library + Compose + Hilt + hilt-navigation-compose |
| `vigia.android.hilt` | `AndroidHiltConventionPlugin` | Adds hilt-android + hilt-compiler + KSP |

## Links

[[app-module]] [[adr-minsdk-34]] [[feature-copilot]] [[feature-maps]] [[feature-pairing]]
[[core-sensor]] [[core-network]] [[core-data]] [[core-auth]] [[core-wallet]]
