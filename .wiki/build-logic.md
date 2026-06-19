# build-logic

**Layer:** Infrastructure  
**Path:** `build-logic/convention/`

Composite Gradle build with shared convention plugins. Applied to every module via `plugins { }` blocks — enforces uniform Android config, compile SDK, Kotlin version, and Hilt setup without repetition.

## Convention Plugins

| Plugin class | Applied to | Sets |
|---|---|---|
| `AndroidApplicationConventionPlugin` | `:app` | `compileSdk`, `targetSdk`, `minSdk`, signing, proguard, `secrets-gradle-plugin` |
| `AndroidLibraryConventionPlugin` | All `core:*` and `feature:*` | Library defaults, `namespace`, resource prefix |
| `AndroidHiltConventionPlugin` | Any Hilt-enabled module | `kapt`/`ksp` for Hilt + Dagger codegen |
| `KotlinAndroidConventionPlugin` | All modules | JVM target, Kotlin options, coroutines |
| `ComposeConventionPlugin` | UI modules | Compose compiler, BOM, metrics flags |

## Version Catalog
`gradle/libs.versions.toml` — single source of truth for all dependency versions referenced as `libs.*` throughout the build.

## Secrets Plugin
`com.google.android.libraries.mapsplatform.secrets-gradle-plugin` — reads `secrets.properties` at build time and injects each key into `BuildConfig` as a `String` field. CI uses env vars as the fallback source.

## Dependents
All modules — `build-logic` is a `includeBuild` composite, not a runtime dependency.
