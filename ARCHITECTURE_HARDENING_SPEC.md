# VIGIA Android — Architecture Hardening Master Spec

**Status:** Draft · **Owner:** VigiaLabs · **Target repo:** `VigiaLabs/vigia-android`
**Baseline:** 12 modules, ~17.5K Kotlin LOC · **Grade today:** B (solid modern foundation)
**Goal:** Reach and hold "industry-standard, production-grade" as defined by Google's
official [App Architecture guidance](https://developer.android.com/topic/architecture)
and the [Now in Android](https://github.com/android/nowinandroid) reference app.

---

## 0. How to read this spec

Each workstream (WS-n) is self-contained and states: **Intent · Current state ·
Target · Reference pattern · Files touched · Acceptance criteria · Effort · Risk.**
Workstreams are independent enough to land in separate PRs. §6 gives the recommended
ordering; §7 is the machine-checkable Definition of Done.

**Non-negotiable principles carried from CLAUDE.md and Google guidance**
- Kotlin + Jetpack Compose + Material 3 only.
- MVVM + Unidirectional Data Flow (state down, events up).
- Hilt for DI; interfaces bound via `@Binds`.
- Single source of truth per data type; offline-tolerant by default.
- No business logic in ViewModels or Composables — it lives in the domain layer.
- Every change is gated by CI (build + test + lint + detekt) before merge.

---

## 1. Current-state assessment

| Layer / concern | Status | Evidence in repo |
|---|---|---|
| Kotlin + Compose + Material 3 | ✅ Standard | all `:feature:*`, `VigiaTheme` |
| Modularization (`core:`/`feature:`) | ✅ Standard | 12 modules in `settings.gradle.kts` |
| Hilt DI (`@Binds` interfaces) | ✅ Standard | `NetworkModule`, `WalletModule`, … |
| MVVM + StateFlow + sealed `UiState` | ✅ Standard | `CopilotUiState`, `CopilotViewModel` |
| Version catalog + convention plugins | ✅ Advanced | `gradle/libs.versions.toml`, `build-logic/` |
| **Domain / UseCase layer** | ❌ Missing | zero `*UseCase` classes |
| **Typed error model** | ❌ Missing | only `ClaimDeviceRepository` uses a sealed `Result` |
| **Offline-first data layer** | 🟡 Partial | Room holds chat + harsh events only; wallet/hazards/rewards are network-only |
| **Automated testing** | ❌ Weak | 6 test files / 17.5K LOC (~0.03%) |
| **Static analysis (detekt/ktlint)** | ❌ Missing | no config, no gradle plugin |
| **CI/CD** | ❌ Missing | `.github/` has only `modernize/` tooling |
| **Performance tooling** | ❌ Missing | no baseline profile module, no macrobenchmark |
| **Observability (crash/log/analytics)** | ❌ Missing | raw `android.util.Log` only |
| **Type-safe navigation** | 🟡 Partial | pager/route strings, no typed routes |
| **Release engineering** | ❌ Missing | no signing config in VCS, no publish pipeline |

**Verdict:** the *presentation layer and module topology are genuinely good*. Every gap
is in the layers below/around the UI — the parts that surface only under team scale,
network adversity, and release cadence.

---

## 2. Target architecture

### 2.1 Layered reference model

```
┌───────────────────────────────────────────────────────────┐
│  UI LAYER            :feature:copilot / :maps / :pairing    │
│  Composable ↔ ViewModel ↔ UiState (StateFlow)              │  ← state down, events up
├───────────────────────────────────────────────────────────┤
│  DOMAIN LAYER        :core:domain            (NEW, pure JVM) │
│  UseCases (invoke operator) · domain errors · policy logic  │  ← the only home for business rules
├───────────────────────────────────────────────────────────┤
│  DATA LAYER          :core:network / :sensor / :wallet /    │
│                      :data / :auth                          │
│  Repository (interface) → RepositoryImpl                    │  ← single source of truth
│    ├── remote source (OkHttp / MQTT / BLE / Solana RPC)     │
│    └── local source  (Room / DataStore / Keystore)          │
├───────────────────────────────────────────────────────────┤
│  MODEL / COMMON      :core:model · :core:common (NEW)       │
│  data classes, VigiaResult<T>, dispatchers, Result mappers  │
└───────────────────────────────────────────────────────────┘
```

### 2.2 Module dependency rules (enforced)

- `:feature:*` → `:core:domain`, `:core:model`, `:core:common` (never another `:feature:*`).
- `:core:domain` → `:core:model`, `:core:common`, and **data-layer interfaces only**
  (depends on repository interfaces, never `*Impl`).
- `:core:*` data modules → `:core:model`, `:core:common`.
- `:core:model`, `:core:common` → no Android framework (pure `kotlin("jvm")`).
- Rule enforced in CI via a module-graph assertion (Gradle task or
  [module-graph-assert](https://github.com/jraska/modules-graph-assert)).

### 2.3 Two new modules

| Module | Type | Contents |
|---|---|---|
| `:core:common` | `kotlin("jvm")` | `VigiaResult<T>`, error taxonomy, `@Dispatcher` qualifiers, `DispatcherProvider`, common extensions |
| `:core:domain` | `kotlin("jvm")` (+ hilt if needed) | all `*UseCase` classes, domain policies (reward calc, detection thresholds) |

> Keep them JVM-only where possible so domain/business logic is unit-testable with plain
> JUnit — no Robolectric, no device.

---

## 3. Workstream specifications

### WS-1 — CI/CD pipeline

**Intent.** Nothing merges without an automated build + test + lint gate. This is the
single highest-leverage, lowest-risk change and a hard prerequisite for everything else.

**Current.** No workflow. All verification is manual on a developer machine.

**Target.** GitHub Actions on every PR and push to `master`:
1. `assembleDemoDebug` (compile both flavors on `master`),
2. `testDemoDebugUnitTest` (+ all module unit tests),
3. `lintDemoDebug`,
4. `detekt`,
5. Gradle build cache + configuration cache + dependency caching,
6. Upload test/lint reports as artifacts,
7. Branch protection: PR cannot merge unless the workflow is green.

**Reference pattern** — `.github/workflows/ci.yml`:
```yaml
name: CI
on:
  pull_request:
  push: { branches: [ master ] }
concurrency:
  group: ci-${{ github.ref }}
  cancel-in-progress: true
jobs:
  build:
    runs-on: ubuntu-latest
    timeout-minutes: 30
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '17' }
      - uses: gradle/actions/setup-gradle@v4
        with: { cache-read-only: ${{ github.ref != 'refs/heads/master' }} }
      - run: ./gradlew --no-daemon detekt lintDemoDebug testDemoDebugUnitTest assembleDemoDebug
      - uses: actions/upload-artifact@v4
        if: always()
        with:
          name: reports
          path: |
            **/build/reports/tests/**
            **/build/reports/detekt/**
            **/build/reports/lint-results-*.html
```

**Files.** `.github/workflows/ci.yml` (new); repo settings → branch protection on `master`.
**Acceptance.** A red build blocks merge; reports downloadable from the run.
**Effort.** ~3 h. **Risk.** None (additive).

---

### WS-2 — Static analysis & formatting

**Intent.** Catch correctness/style drift automatically and enforce one code style.

**Current.** No detekt, no ktlint/spotless, no `lint.xml` baseline.

**Target.**
- **detekt** with Compose ruleset ([detekt-compose](https://github.com/mrmans0n/compose-rules))
  for recomposition/stability smells.
- **spotless + ktlint** for formatting (`./gradlew spotlessApply`).
- **Android Lint** baseline committed (`lint-baseline.xml`) so new lint warnings fail CI
  while legacy ones are grandfathered.
- Wire all three into the convention plugins so every module inherits them.

**Reference pattern** — add to `build-logic` a `AndroidQualityConventionPlugin` applied by
`androidLibrary`/`androidApplication`:
```kotlin
// detekt.yml top-level; per-module inherits
detekt {
    config.setFrom(rootProject.files("config/detekt/detekt.yml"))
    baseline = file("detekt-baseline.xml")
    parallel = true
}
dependencies {
    "detektPlugins"(libs.detekt.compose)   // io.nlopez.compose.rules:detekt
}
```
```kotlin
android {
    lint {
        baseline = file("lint-baseline.xml")
        warningsAsErrors = true
        abortOnError = true
        checkDependencies = true
    }
}
```

**Files.** `build-logic/.../AndroidQualityConventionPlugin.kt`, `config/detekt/detekt.yml`,
per-module `*-baseline.xml`, catalog entries for detekt/spotless.
**Acceptance.** `./gradlew detekt spotlessCheck lintDemoDebug` passes; new violations fail CI.
**Effort.** ~1 day (mostly generating/curating baselines). **Risk.** Low.

---

### WS-3 — Typed error model (`VigiaResult`)

**Intent.** Replace silent `try/catch` + `Log.w` with a typed result the UI can render.
Critical for a **hardware- and network-dependent** app where "why did it fail" is UX.

**Current.** BLE handshake failure, `/telemetry` 401, RPC timeout — all logged and dropped.
Only `ClaimDeviceRepository` models errors as a sealed type.

**Target.** A single `VigiaResult<T>` in `:core:common` and a domain error taxonomy;
every repository suspend function returns `VigiaResult<T>` (or `Flow<VigiaResult<T>>`),
ViewModels map errors to `UiState` error variants.

**Reference pattern** — `:core:common`:
```kotlin
sealed interface VigiaResult<out T> {
    data class Success<T>(val data: T) : VigiaResult<T>
    data class Failure(val error: VigiaError) : VigiaResult<Nothing>
}

sealed interface VigiaError {
    val cause: Throwable?
    // transport
    data class Network(override val cause: Throwable?) : VigiaError
    data class Timeout(override val cause: Throwable?) : VigiaError
    data class Http(val code: Int, override val cause: Throwable? = null) : VigiaError
    // auth
    data object Unauthorized : VigiaError { override val cause: Throwable? = null }
    // ble / sensor
    data class Ble(val reason: BleLinkError, override val cause: Throwable? = null) : VigiaError
    // wallet / chain
    data class Wallet(val reason: String, override val cause: Throwable? = null) : VigiaError
    data class Unknown(override val cause: Throwable?) : VigiaError
}

inline fun <T> vigiaCatching(block: () -> T): VigiaResult<T> =
    try { VigiaResult.Success(block()) }
    catch (c: CancellationException) { throw c }          // never swallow cancellation
    catch (e: IOException) { VigiaResult.Failure(VigiaError.Network(e)) }
    catch (e: Throwable) { VigiaResult.Failure(VigiaError.Unknown(e)) }
```
Repository example (`WalletRepositoryImpl.refreshBalance`):
```kotlin
override suspend fun refreshBalance(): VigiaResult<WalletState> = withContext(io) {
    vigiaCatching {
        val resp = httpClient.newCall(request).await()   // OkHttp await ext
        if (resp.code == 401) return@withContext VigiaResult.Failure(VigiaError.Unauthorized)
        parseBalance(resp)
    }
}
```

**Files.** New `:core:common`; refactor `WalletRepositoryImpl`, `MqttAlertRepositoryImpl`,
`OkHttpSseSearchClient`, `BleRepositoryImpl`, `MapsRepositoryImpl`, `AmplifyAuthRepository`;
add error variants to each `*UiState`.
**Acceptance.** No repository method returns a bare value or logs-and-swallows; each
`UiState` has a typed error path rendered in UI; unit tests assert error mapping.
**Effort.** ~1–1.5 days. **Risk.** Medium (touches every repo) — do behind WS-5 tests.

---

### WS-4 — Domain layer / UseCases

**Intent.** Give business logic a single, testable home; shrink `CopilotViewModel`.

**Current.** No `:core:domain`. Logic lives inline in ViewModels/repositories
(reward math, detection thresholds, telemetry-signing orchestration, streak bonuses).

**Target.** `:core:domain` module of single-responsibility UseCases, callable via
`operator fun invoke`, named `<Verb><Noun>UseCase`. UseCases depend on repository
**interfaces** only and inject a `DispatcherProvider`.

**Candidate UseCases (initial set)**
| UseCase | Wraps / owns |
|---|---|
| `SubmitHazardDetectionUseCase` | detection threshold + `signTelemetry` + POST `/telemetry` |
| `EvaluateHazardFromTelemetryUseCase` | RRI threshold → is-this-a-hazard decision |
| `RefreshWalletBalanceUseCase` | balance poll + cache write |
| `EnsureWalletProvisionedUseCase` | Ed25519 gen + `POST /register-device` (idempotent) |
| `ObserveSensorContextUseCase` | fuse GPS + BLE frame into `VigiaSearchContext` |
| `StreamCopilotAnswerUseCase` | SSE search orchestration |
| `ClaimDeviceUseCase` | QR → CDM association |

**Reference pattern**:
```kotlin
class RefreshWalletBalanceUseCase @Inject constructor(
    private val wallet: WalletRepository,
    private val dispatchers: DispatcherProvider,
) {
    suspend operator fun invoke(): VigiaResult<WalletState> =
        withContext(dispatchers.io) { wallet.refreshBalance() }
}
```
ViewModel becomes a thin orchestrator:
```kotlin
@HiltViewModel
class CopilotViewModel @Inject constructor(
    private val refreshBalance: RefreshWalletBalanceUseCase,
    private val streamAnswer: StreamCopilotAnswerUseCase,
    // …
) : ViewModel()
```

**Files.** New `:core:domain`; extract logic from `CopilotViewModel`,
`WalletRepositoryImpl`, `VigiaForegroundService`.
**Acceptance.** ViewModels contain no `if`-heavy business rules; each UseCase has a unit
test; `CopilotViewModel` LOC drops materially.
**Effort.** ~2 days (after WS-5). **Risk.** Medium — behavior-preserving refactor; needs tests first.

---

### WS-5 — Testing strategy

**Intent.** A real safety net, prioritized at the code that moves money and data.

**Current.** 6 tests. No ViewModel/Repository/UseCase coverage of core flows.

**Target (test pyramid, NIA conventions).**
- **Unit (JVM, majority):** UseCases, ViewModels, mappers, `Ed25519KeyStore`/`Base58`,
  reward math, SSE parsing. Use **fakes over mocks** (hand-written `FakeWalletRepository`
  implementing the interface) and **[Turbine](https://github.com/cashapp/turbine)** for
  `Flow`/`StateFlow` assertions. `kotlinx-coroutines-test` + a `TestDispatcherRule`.
- **Repository tests:** with fake remote/local sources + `MockWebServer` for HTTP.
- **Instrumented / Compose UI:** `createAndroidComposeRule`, semantics-based assertions
  for the wallet pane, alerts, pairing.
- **Screenshot tests:** [Roborazzi](https://github.com/takahirom/roborazzi) (JVM, runs in
  CI) for key composables in light/dark — locks visual regressions.
- **Coverage:** track with Kover; **gate ≥ 60 %** on `:core:domain` + `:core:wallet`
  (the money paths), advisory elsewhere. Not a blanket 80 % mandate.

**Reference pattern** — fake + Turbine:
```kotlin
class FakeWalletRepository : WalletRepository {
    val state = MutableStateFlow(WalletState())
    var refreshResult: VigiaResult<WalletState> = VigiaResult.Success(WalletState())
    override val state: StateFlow<WalletState> get() = this.state
    override suspend fun refreshBalance() = refreshResult.also {
        if (it is VigiaResult.Success) state.value = it.data
    }
    /* … */
}

@Test fun balance_error_surfaces_as_ui_error() = runTest {
    val repo = FakeWalletRepository().apply {
        refreshResult = VigiaResult.Failure(VigiaError.Unauthorized)
    }
    val vm = CopilotViewModel(RefreshWalletBalanceUseCase(repo, testDispatchers), /*…*/)
    vm.uiState.test {
        assertThat(awaitItem()).isInstanceOf<CopilotUiState.Active>()
        vm.refreshWallet()
        assertThat(expectMostRecentItem().walletError).isEqualTo(WalletError.SignedOut)
    }
}
```

**Files.** `src/test` across `:core:domain`, `:core:wallet`, `:feature:copilot`,
`:feature:pairing`; shared `:core:testing` module for fakes + rules; Kover in convention plugin.
**Acceptance.** CI runs all unit + Roborazzi tests; Kover gate met on money paths.
**Effort.** ~2–3 days. **Risk.** Low (additive) — but unlocks WS-3/WS-4 safely.

---

### WS-6 — Offline-first data layer

**Intent.** A driving app lives in tunnels and dead zones — cached data, not blank screens.

**Current.** Room only stores chat + harsh events. Wallet balance, hazards, rewards,
maps data are network-only; connection loss = empty UI.

**Target.** Room-backed single-source-of-truth for wallet balance, hazard history, and
reward ledger. Repositories expose `Flow` from the DB and refresh in the background
(cached-then-network). Background sync via **WorkManager** (already planned for 15-min
balance sync). DataStore for small key/values (device association id, last sync time).

**Reference pattern** (NIA offline-first):
```kotlin
override fun observeBalance(): Flow<WalletState> =
    walletDao.observe().map { it.toDomain() }          // UI reads DB, always

override suspend fun refreshBalance(): VigiaResult<Unit> = vigiaCatching {
    val remote = api.getBalance(pubKey)                // network
    walletDao.upsert(remote.toEntity())                // write-through cache
}
// WorkManager: periodic 15-min RefreshWalletWorker calls refreshBalance()
```

**Files.** New DAOs/entities in `:core:data` (or per-domain), `WalletRepositoryImpl`
rewrite to DB-backed, `:core:data` WorkManager workers + Hilt worker factory.
**Acceptance.** Airplane-mode shows last-known balance/hazards; a `RefreshWorker` runs on
schedule; repository tests cover cache-hit / cache-miss / refresh-failure.
**Effort.** ~2–3 days. **Risk.** Medium — sits on WS-3 (offline == an error/loading state).

---

### WS-7 — Performance engineering

**Intent.** Fast cold start and jank-free scrolling, measured not guessed.

**Current.** No baseline profile module, no macrobenchmark, no Compose-stability audit,
R8 on for release but unverified. (We already fixed pager jank + map re-inflation ad hoc.)

**Target.**
- **`:benchmark` module** with Macrobenchmark: startup (cold/warm) + a scroll journey.
- **Baseline Profiles** generated by the benchmark and shipped (`baseline-prof.txt`),
  cutting cold start ~15–30 %.
- **Compose stability:** annotate domain models `@Immutable`/`@Stable`; enable strong
  skipping; add a `compose_compiler_config.conf` stability list; enable compiler metrics
  in CI to catch unstable params.
- **R8 full mode** (`android.enableR8.fullMode=true`) with verified `proguard-rules.pro`;
  keep rules for Solana/OkHttp/Amplify reflection.
- **StrictMode** in debug (disk/network on main thread → crash in dev).

**Files.** `:benchmark` (new), `app/baseline-prof.txt`, `compose_compiler_config.conf`,
`gradle.properties` flags, `proguard-rules.pro` review.
**Acceptance.** Macrobenchmark reports in CI; baseline profile packaged; no unstable
`@Composable` params on hot screens; release APK verified functional under full-mode R8.
**Effort.** ~2 days. **Risk.** Medium (R8 rules can hide runtime breakage — test release build).

---

### WS-8 — Observability

**Intent.** Know when/why production breaks; measure funnels.

**Current.** `android.util.Log` only. No crash reporting, no analytics abstraction.

**Target.**
- **Timber** for structured, tagged logging (debug tree only in debug builds).
- **Crashlytics** (Firebase — you already pull FCM) *or* Sentry; wire a `CrashReporter`
  interface in `:core:common` so the vendor is swappable and absent in tests.
- **Analytics abstraction:** `AnalyticsHelper` interface + no-op impl for tests/demo;
  events for detection-submitted, reward-received, wallet-provisioned, pairing-completed.
- **Coroutine/Compose tracing** in debug for perf sessions.

**Files.** `:core:common` (`CrashReporter`, `AnalyticsHelper`), Timber init in
`VigiaApplication`, DI bindings.
**Acceptance.** Uncaught exceptions reach the dashboard; core funnel events fire; no PII in
logs/events (address hashing where needed).
**Effort.** ~1 day. **Risk.** Low.

---

### WS-9 — Security hardening

**Intent.** Protect keys, transport, and the reward economy against tampering.

**Current (good bones).** Ed25519 wallet key AES-GCM-wrapped in Keystore; HMAC sensor
key in Keystore; `network_security_config.xml` per flavor; secrets kept out of the APK
(see `GAPS.md`).

**Target additions.**
- **TLS everywhere + certificate/public-key pinning** on the prod API once the ALB has an
  ACM cert + domain (remove the cleartext demo allowance for prod).
- **Play Integrity API** attestation before minting-eligible telemetry is accepted
  (anti-emulator/anti-tamper) — pairs with the backend spoof-slash.
- **Dependency vulnerability scanning:** OWASP dependency-check or GitHub Dependabot
  alerts in CI.
- **Secret scanning:** gitleaks in CI; confirm no `secrets.properties`/keys are committed.
- **R8 obfuscation** for release; keep-rules reviewed (WS-7).
- **Rooted/tamper posture:** document threat model; treat wallet key as non-exportable
  (already `PURPOSE_SIGN` only — keep it).

**Files.** `network_security_config.xml` (prod pinning), Play Integrity client in
`:core:auth` or `:core:wallet`, `.github/workflows/security.yml`, `THREAT_MODEL.md`.
**Acceptance.** Prod build rejects non-TLS + fails closed on pin mismatch; CI blocks known
CVEs and committed secrets; integrity token attached to telemetry.
**Effort.** ~1.5 days (excludes domain/ACM cost). **Risk.** Medium (pinning can brick
clients if rotated wrong — ship backup pins).

---

### WS-10 — Type-safe navigation

**Intent.** Compile-checked routes, no stringly-typed nav.

**Current.** Auth/pairing gate + landing pager use route strings / booleans.

**Target.** Navigation Compose with **type-safe routes** (`@Serializable` route objects,
`navController.navigate(WalletRoute)`), or migrate to **Navigation 3** if adopted. Model
the top-level graph: `Auth → Pairing → Copilot(landing tabs)`.

**Files.** `:feature:copilot` `AppRoot`/nav host, route definitions per feature.
**Acceptance.** No string routes; back-stack + deep links (reward push → wallet) typed.
**Effort.** ~1 day. **Risk.** Low–Medium.

---

### WS-11 — Accessibility & UX quality gates

**Intent.** Satisfy the `Ui ux pro max` gates automatically, not by memory — and meet the
driving-safety bar (glanceable, TalkBack-navigable).

**Current.** Good visual system (VigiaColors contrast documented). No automated a11y checks.

**Target.**
- Content descriptions on all actionable/icon-only controls (wallet chips, map toggles).
- Minimum 48dp touch targets (audit `pressScale` pills).
- Roborazzi screenshot tests double as a11y-node snapshots; add
  [accessibility-test-framework](https://github.com/google/Accessibility-Test-Framework) checks in instrumented tests.
- Dynamic type / font-scale sanity on hero balance + alerts.
- Driving-mode contract (from the UX research): eyes-free, ≤2-second glance.

**Files.** semantics on composables, a11y assertions in UI tests.
**Acceptance.** a11y checks pass in CI; no unlabeled interactive nodes on core screens.
**Effort.** ~1 day. **Risk.** Low.

---

### WS-12 — Dependency & supply-chain management

**Intent.** Stay current and reproducible without manual bumps.

**Current.** Version catalog present (good). No automated updates or lockfiles.

**Target.**
- **Renovate** (or Dependabot) PRs for catalog + gradle-wrapper updates, grouped weekly.
- **Gradle dependency verification** (`verification-metadata.xml`) for checksums.
- **Dependency lockfiles** for reproducible CI.
- Pin the AGP/Kotlin/KSP triple explicitly (already in catalog — keep aligned).

**Files.** `renovate.json`, `gradle/verification-metadata.xml`, lockfiles.
**Acceptance.** Update PRs open automatically and pass CI; builds verify checksums.
**Effort.** ~0.5 day. **Risk.** Low.

---

### WS-13 — Documentation & ADRs

**Intent.** Decisions are discoverable; the `wiki/` stays accurate.

**Current.** Rich Obsidian `wiki/` + `GAPS.md`. No formal ADRs; no module READMEs.

**Target.**
- **ADRs** under `docs/adr/` (MADR format) for: choosing NAT-instance over gateway,
  Ed25519 vs HMAC for wallet, offline-first cache strategy, domain-layer introduction.
  Use the existing `codebase-memory-mcp` `manage_adr` tool to keep them indexed.
- **Module READMEs** (one-paragraph responsibility + public API) per module.
- Keep `wiki/` regenerated on merge (the commit hook already re-indexes the graph).

**Files.** `docs/adr/*.md`, `*/README.md`.
**Acceptance.** Every architectural decision in this spec has an ADR; modules self-describe.
**Effort.** ~1 day (ongoing). **Risk.** None.

---

### WS-14 — Release engineering

**Intent.** Repeatable, signed, auditable releases.

**Current.** `demo`/`prod` flavors + minify on release. No signing config in VCS, no
versioning scheme, no publish pipeline.

**Target.**
- **Signing** via Gradle with keystore creds from CI secrets (never in repo);
  `release` build type wired to the upload key.
- **Semantic versioning** driven by tags; `versionCode` from CI run number or git height.
- **Play publishing** via [Gradle Play Publisher](https://github.com/Triple-T/gradle-play-publisher)
  or Fastlane → internal testing track on tag.
- **R8 mapping + native symbols** uploaded to Crashlytics per release.
- **Release CI job** separate from PR CI: `bundleProdRelease` → sign → upload.

**Files.** `app/build.gradle.kts` signing/publishing, `.github/workflows/release.yml`,
CI secrets (keystore, service account json).
**Acceptance.** A tag produces a signed AAB on the internal track with mapping uploaded.
**Effort.** ~1.5 days. **Risk.** Medium (keystore handling — document recovery).

---

## 4. Module changes summary

| Module | Change |
|---|---|
| `:core:common` | **NEW** — `VigiaResult`, `VigiaError`, `DispatcherProvider`, `CrashReporter`, `AnalyticsHelper` |
| `:core:domain` | **NEW** — all `*UseCase`, domain policies |
| `:core:testing` | **NEW** — fakes, `TestDispatcherRule`, Turbine helpers, Roborazzi setup |
| `:benchmark` | **NEW** — Macrobenchmark + baseline profile generation |
| `:core:network/sensor/wallet/auth/data` | return `VigiaResult`; DB-backed sources (WS-6) |
| `:feature:*` | consume UseCases; typed nav; a11y semantics; error UI states |
| `build-logic` | new `AndroidQualityConventionPlugin` (detekt/spotless/lint/kover) applied everywhere |
| `gradle/libs.versions.toml` | add detekt, spotless, turbine, kover, roborazzi, timber, crashlytics, work, benchmark, play-integrity |

---

## 5. Version catalog additions (illustrative)

```toml
[versions]
detekt = "1.23.7"
spotless = "6.25.0"
turbine = "1.1.0"
kover = "0.8.3"
roborazzi = "1.26.0"
timber = "5.0.1"
workManager = "2.9.1"
macrobenchmark = "1.3.3"

[libraries]
detekt-compose = { group = "io.nlopez.compose.rules", name = "detekt", version = "0.4.16" }
turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }
timber = { group = "com.jakewharton.timber", name = "timber", version.ref = "timber" }
androidx-work-runtime = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "workManager" }
androidx-work-testing = { group = "androidx.work", name = "work-testing", version.ref = "workManager" }
roborazzi = { group = "io.github.takahirom.roborazzi", name = "roborazzi", version.ref = "roborazzi" }

[plugins]
detekt = { id = "io.gitlab.arturbosch.detekt", version.ref = "detekt" }
spotless = { id = "com.diffplug.spotless", version.ref = "spotless" }
kover = { id = "org.jetbrains.kotlinx.kover", version.ref = "kover" }
```

---

## 6. Phased rollout plan

Ordered by **risk-reduction per hour**, not by architectural purity. Each phase is
independently shippable and leaves the app releasable.

| Phase | Workstreams | Outcome | Effort | Regression risk |
|---|---|---|---|---|
| **P0 — Guardrails** | WS-1 CI, WS-2 static analysis, WS-12 deps, WS-13 ADRs | Every future change is gated; zero product code touched | ~2 days | **None** |
| **P1 — Safety net** | WS-5 testing (money paths first), `:core:testing` | Tests exist before we refactor | ~3 days | Low (additive) |
| **P2 — Correctness** | WS-3 typed errors, WS-8 observability | Failures become visible + typed | ~2 days | Medium |
| **P3 — Structure** | WS-4 domain layer, WS-10 nav | Business logic has a home; typed routes | ~3 days | Medium (behavior-preserving, guarded by P1) |
| **P4 — Resilience** | WS-6 offline-first, WS-11 a11y | Works offline; TalkBack-clean | ~4 days | Medium |
| **P5 — Ship & scale** | WS-7 perf, WS-9 security, WS-14 release | Fast, hardened, publishable | ~5 days | Medium |

**Total ≈ 3–4 focused weeks.** P0 is safe to do anytime (including now). **P2–P5 touch
live code paths and should not be rushed against the July 16 hackathon** — do P0 (and
optionally P1) before, the rest after.

---

## 7. Definition of Done (acceptance matrix)

- [ ] CI green-gates every PR (build + unit + lint + detekt); branch protection on.
- [ ] `./gradlew detekt spotlessCheck lintDemoDebug` clean; baselines committed.
- [ ] No repository swallows errors; every `UiState` renders a typed error.
- [ ] `:core:domain` exists; ViewModels hold no business rules; each UseCase unit-tested.
- [ ] Kover ≥ 60 % on `:core:domain` + `:core:wallet`; Roborazzi snapshots for core screens.
- [ ] Wallet balance + hazards survive airplane mode (Room-backed); WorkManager sync runs.
- [ ] Baseline profile shipped; Macrobenchmark in CI; release build verified under R8 full mode.
- [ ] Crashlytics + analytics wired; no PII in logs/events.
- [ ] Prod TLS + pinning; Play Integrity on telemetry; gitleaks + dep-scan in CI.
- [ ] Type-safe navigation; reward-push deep link typed.
- [ ] a11y checks pass; ADRs for every decision here; module READMEs present.
- [ ] Tagged release → signed AAB on internal track with mapping uploaded.

---

## 8. Risks & rollback

| Risk | Mitigation |
|---|---|
| Error-model refactor destabilizes repos | Land WS-5 tests first; one repo per PR; feature-flag if needed |
| R8 full mode hides runtime breakage | Smoke-test release build in CI; keep-rules reviewed per lib |
| Certificate pinning bricks clients on rotation | Ship 2 backup pins; monitor pin-failure metric before enforcing |
| Domain extraction changes behavior | Behavior-preserving PRs guarded by P1 tests; diff UseCase vs old inline logic |
| Scope creep before hackathon | Hard rule: only P0 (+P1) before July 16; freeze P2–P5 until after |

---

## 9. Appendix — reference alignment

- Google, [Guide to app architecture](https://developer.android.com/topic/architecture) ·
  [UI layer](https://developer.android.com/topic/architecture/ui-layer) ·
  [Domain layer](https://developer.android.com/topic/architecture/domain-layer) ·
  [Architecture recommendations](https://developer.android.com/topic/architecture/recommendations)
- [Now in Android](https://github.com/android/nowinandroid) — modularization, offline-first,
  fakes-over-mocks, Roborazzi, convention plugins, baseline profiles (the reference this
  spec mirrors).
- Existing repo conventions honored: `libs.versions.toml`, `build-logic` convention
  plugins, `wiki/` MoC, `GAPS.md`, `codebase-memory-mcp` ADR tooling.
