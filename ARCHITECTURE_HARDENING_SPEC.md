# VIGIA Android — Architecture Hardening Master Spec

**Status:** Corrected implementation specification · **Owner:** VigiaLabs · **Target repo:** `VigiaLabs/vigia-android`
**Audit date:** 2026-08-12 · **Default branch:** `master`
**Baseline:** 10 application modules plus the included `build-logic` build; approximately
15.6K lines of production Kotlin and 1.5K lines of test Kotlin.
**Current classification:** advanced prototype, **not a production release candidate**.
**Goal:** make the app safe to release, observable in operation, recoverable under failure,
and explainable in an engineering interview. The reference points are Google's official
[App Architecture guidance](https://developer.android.com/topic/architecture), the
[Now in Android](https://github.com/android/nowinandroid) sample, Android platform security
and background-execution rules, AWS IoT guidance, and reliability practices such as
user-centred service-level objectives. A reference application is evidence, not a template
that must be copied module-for-module.

---

## 0. How to read this spec

Each workstream states **intent, evidence, target behaviour, verification, rollout, and
rollback**. The order is deliberate: eliminate unsafe behaviour before reorganising the
code. A green build does not make an unsafe fallback acceptable, and a new architecture
layer does not repair an incorrect state machine.

The words **MUST**, **SHOULD**, and **MAY** are used in their normal requirements sense:

- **MUST:** a release gate; shipping without it requires an explicit, time-bounded risk acceptance.
- **SHOULD:** the default engineering choice; deviation requires an ADR with evidence.
- **MAY:** useful when the measured problem justifies its cost.

Production readiness is evaluated across four properties:

1. **Correctness:** the app never converts an authentication, ownership, payment, or integrity
   failure into success.
2. **Durability:** important state survives process death, reboot, update, and temporary loss of
   network or hardware.
3. **Operability:** failures can be detected, diagnosed, mitigated, and rolled back without a new APK.
4. **Evidence:** critical claims are backed by tests, release artefacts, dashboards, or a documented
   threat-model decision.

**Non-negotiable principles**
- Kotlin + Jetpack Compose + Material 3 only.
- MVVM + Unidirectional Data Flow (state down, events up).
- Hilt for dependency injection where dependency injection is useful; do not create interfaces
  solely to satisfy a pattern.
- A single source of truth for durable product state. The database is not automatically the source
  of truth for security authority, live device state, or money—the server/device remains authoritative
  and cached data carries freshness metadata.
- Complex or reused business policy leaves ViewModels. A separate domain layer is **optional**, as
  Android's own guidance states; use cases are introduced for cohesive workflows, not one wrapper per
  repository function.
- Cancellation is rethrown, timeouts are finite, retries are bounded, and at-least-once delivery is
  paired with deduplication/idempotency.
- Demo behaviour is selected at compile time and cannot be reached from a production runtime failure.
- Every production-impacting change is gated by CI and every high-risk path has an observable rollback.

### 0.1 Implementation checkpoint — 2026-08-13

The first production-hardening tranche is implemented on `master` (`8852d49`). It covers:

- fail-closed production authentication and startup configuration validation;
- explicit payout disablement in production and intent-vs-settlement payment states;
- typed claim failures with no offline/local-success fallback, plus exact QR-MAC CDM filtering;
- bounded BLE scan/bond/MTU operations with cancellation propagation and service-visible failures;
- Room migrations/schema export and a durable MQTT/FCM alert inbox;
- explicit location/telemetry availability flags instead of treating `(0, 0)`/zero telemetry as real;
- backup/device-transfer restrictions, redacted network logs, finite HTTP call bounds, and CI compile/test/lint gates.

This is an implementation checkpoint, not a release declaration. The device-wallet claim remains
blocked until the Android/Pi/server binding-challenge protocol supplies a real `deviceSig`; MQTT
client authentication, maintained map/tile service, observability, workflow extraction, release
signing, and the remaining migration/security tests remain planned work.

---

## 1. Current-state assessment

| Layer / concern | Status | Evidence in repo |
|---|---|---|
| Kotlin + Compose + Material 3 | ✅ Standard | all `:feature:*`, `VigiaTheme` |
| Modularization (`core:`/`feature:`) | 🟡 Partial | 10 application modules; `feature:copilot` depends directly on `feature:maps` and `feature:pairing` |
| Hilt DI (`@Binds` interfaces) | ✅ Standard | `NetworkModule`, `WalletModule`, … |
| MVVM + StateFlow + sealed `UiState` | ✅ Standard | `CopilotUiState`, `CopilotViewModel` |
| Version catalog + convention plugins | ✅ Advanced | `gradle/libs.versions.toml`, `build-logic/` |
| **Cohesive business workflows** | ❌ Missing | `CopilotViewModel` is ~1,100 lines and coordinates search, speech, wallet, alerts, sensors, and payout |
| **Typed error model** | ❌ Missing | only `ClaimDeviceRepository` uses a sealed `Result` |
| **Offline-first data layer** | 🟡 Partial | Room holds chat + harsh events only; wallet/hazards/rewards are network-only |
| **Automated testing** | ❌ Weak | about five meaningful JVM test files plus template tests; no auth, pairing, BLE lifecycle, migration, FCM/MQTT, payment, or Keystore security tests |
| **Static analysis (detekt/ktlint)** | ❌ Missing | no config, no gradle plugin |
| **CI/CD** | ❌ Missing | no GitHub Actions workflow or protected release gate |
| **Performance tooling** | ❌ Missing | no baseline profile module, no macrobenchmark |
| **Observability (crash/log/analytics)** | ❌ Missing | raw `android.util.Log` only |
| **Type-safe navigation** | 🟡 Partial | pager/route strings, no typed routes |
| **Release engineering** | ❌ Missing | release minification exists, but no CI signing/publishing pipeline and production lint is red |
| **Authentication safety** | 🔴 Stop-ship | Amplify configuration failure binds `DemoAuthRepository` in production |
| **Device ownership** | 🔴 Stop-ship | empty device signature; several claim failures continue as a local success |
| **BLE lifecycle** | 🔴 Stop-ship | scans/bonding can wait forever; swallowed errors defeat service retry; FGS start path is unused |
| **Location/context correctness** | 🔴 Stop-ship | missing data is represented as zero coordinates/zero telemetry; permission is checked once |
| **Payment correctness** | 🔴 Stop-ship | a client secret is reported as payment success; payout UI is not wired end-to-end |
| **Alert delivery** | 🔴 Stop-ship | FCM writes to an in-memory flow rather than durable storage/system notification |
| **Database migration** | 🔴 Stop-ship | `fallbackToDestructiveMigration()` can delete user data during upgrade |
| **Backup/privacy** | 🔴 Stop-ship | backup enabled with template rules; sensitive preferences/database content are not excluded |
| **MQTT authentication** | 🔴 Stop-ship | TLS server validation exists, but normal AWS IoT client authentication is absent |
| **Map platform** | 🔴 Stop-ship | OSMDroid is archived; public OSM standard tiles have no production SLA |

**Verdict:** the Compose/UDF foundation, version catalogue, convention plugins, repository
seams, and partial persistence are worth keeping. The application is nevertheless unsafe to
ship because several failure paths become success paths. Correct those first. Module creation,
formatters, dashboards, and performance work are important but cannot outrank identity,
ownership, durable alerting, payment correctness, or data preservation.

### 1.1 Stop-ship release invariants

No production build may be promoted while any invariant below is false:

- A production authentication configuration error produces a blocking configuration state,
  never demo authentication.
- A device is account-bound only after a verified, signed, idempotent server claim. Offline
  association, if retained, is visibly restricted and cannot earn, upload, or cash out.
- BLE scan, connect, discover, bond, handshake, and disconnect transitions have deadlines,
  explicit error states, and deterministic cleanup.
- Unknown location/sensor data stays unknown. Staleness, accuracy, provenance, and permission
  are part of the model and server request.
- Payment states distinguish intent creation, user confirmation, processing, settlement,
  failure, reversal, and reconciliation. Only server/webhook-confirmed settlement is success.
- A critical warning is persisted before acknowledgement and is user-visible when policy permits,
  even if no activity or ViewModel exists.
- Database upgrades preserve data or intentionally block startup with a recoverable support path;
  release builds never use destructive fallback.
- Backups exclude credentials, keys, pairing identifiers, tokens, notification state, and any
  database not explicitly approved by the privacy threat model.
- Required production configuration is present, valid, HTTPS where applicable, and checked by
  build/startup validation.
- The exact minified, signed release candidate passes tests on the supported device/API matrix.

---

## 2. Target architecture

### 2.1 Responsibility model

```
┌───────────────────────────────────────────────────────────┐
│  UI LAYER            :feature:copilot / :maps / :pairing    │
│  Composable ↔ ViewModel ↔ UiState (StateFlow)              │  ← state down, events up
├───────────────────────────────────────────────────────────┤
│  WORKFLOW/POLICY     cohesive use cases where complexity     │
│  justifies them: PairDevice, StreamCopilot, SettlePayout     │  ← optional layer, pure Kotlin where possible
├───────────────────────────────────────────────────────────┤
│  DATA LAYER          :core:network / :sensor / :wallet /    │
│                      :data / :auth                          │
│  Repository (interface) → RepositoryImpl                    │  ← single source of truth
│    ├── remote source (OkHttp / MQTT / BLE / Solana RPC)     │
│    └── local source  (Room / DataStore / Keystore)          │
├───────────────────────────────────────────────────────────┤
│  MODEL / SHARED      :core:model; a small shared module only │
│  if multiple modules genuinely need the same abstractions   │
└───────────────────────────────────────────────────────────┘
```

### 2.2 Module dependency rules (enforced)

- A feature does not depend on another feature's implementation. Shared UI or behaviour moves to
  a deliberately owned core/component API; navigation is composed by `:app`.
- Pure Kotlin models and policies do not depend on Android. Android-dependent orchestration may
  stay in an Android library rather than forcing awkward wrappers.
- Repository interfaces live with the consumer/domain contract when doing so breaks dependency
  cycles; implementations live with their data source.
- `:core:sensor` is split by responsibility only when tests/refactors show stable seams. The target
  bounded contexts are device link, context collection, and alert delivery—not arbitrary layer count.
- Dependency direction is enforced in CI after existing feature-to-feature edges are removed.
- Rule enforced in CI via a module-graph assertion (Gradle task or
  [module-graph-assert](https://github.com/jraska/modules-graph-assert)).

### 2.3 Module policy: earn every module

| Module | Type | Contents |
|---|---|---|
| `:core:testing` | Android/JVM test fixtures | Shared fakes, dispatcher rule, test data builders; add when duplication appears |
| `:benchmark` | Android benchmark | Macrobenchmark and Baseline Profile generation; add when the release journey is stable |
| `:core:common` | Pure JVM, optional | Only truly cross-cutting error/dispatcher/clock abstractions; reject a miscellaneous dumping ground |
| `:core:domain` | Pure JVM/Android library, optional | Only complex or reused policy/workflows; initially pairing, copilot streaming, and payout state machines |

The current repository has ten application modules, not twelve. Adding four modules at once
would increase build and ownership cost before it proved a boundary. Start by extracting classes
inside their owning module. Promote a package to a module only when it needs independent ownership,
enforcement, reuse, or build isolation.

---

## 3. Workstream specifications

### WS-0A — Authentication and production configuration fail closed

**Why first.** `AmplifyInitializer` catches every configuration failure and marks Amplify as
unconfigured. `AuthModule` then binds `DemoAuthRepository`, whose password and confirmation paths
are simulations. That is appropriate for a demo build and unacceptable as production recovery.

**Required design.**

- Move demo bindings and implementation to `src/demo`; put the Amplify implementation in
  `src/prod` or bind it using a compile-time flavour value that cannot change after startup.
- Model initialisation as `Loading | Ready | Misconfigured(correlationId)`. Production UI blocks
  at `Misconfigured` with a support-safe message and no local authenticated state.
- Make the prod build fail when Cognito identifiers, redirect URLs, backend URL, MQTT endpoint,
  Stripe publishable key, or required Firebase configuration are empty or placeholders.
- Replace the broad custom-scheme redirect with an exact redirect contract. Prefer verified HTTPS
  App Links when supported; otherwise use a reverse-domain private-use scheme with exact host/path.
- Cache tokens with expiry awareness and synchronised refresh. Do not call `runBlocking` from every
  OkHttp interceptor invocation.
- Redact `Authorization`, cookies, tokens, emails, precise location, conversation bodies, and
  payment identifiers from all logs.

**Verification.** A production test with missing/malformed Amplify configuration must reach only
the blocking state. Build-logic tests reject each missing required value. OAuth tests cover success,
cancel, expired state/nonce, wrong redirect host, and process recreation. No demo auth class is in
the prod runtime classpath.

**Rollout/rollback.** Ship behind no flag: fail-closed identity is an invariant. Roll back to the
previous known-good production configuration, never to demo authentication.

---

### WS-0B — Device ownership, BLE lifecycle, and cryptographic protocol

**Current failures.** Pairing submits `deviceSig = ""`; the claim repository collapses several HTTP
responses into `NetworkError`; the ViewModel treats that error as permission to pair locally. The CDM
filter matches any device named `VIGIA.*`, not the QR identity. BLE connect swallows its structured
exception, resets `Error` to `Idle`, and prevents the foreground-service retry loop from seeing a
failure. Scan and bond have no deadline, and the foreground-service start helper has no caller.

**Required state machines.** Pairing and connection are separate, durable workflows:

```text
Pairing:
Unpaired -> QrValidated -> CandidateSelected -> LinkAuthenticated
         -> ServerClaimPending -> Claimed
terminal/recoverable errors: InvalidQr, WrongDevice, LinkTimeout,
SignatureRejected, AlreadyClaimed, Unauthorized, OfflineRestricted

Connection:
Idle -> WaitingForPresence -> Scanning -> Connecting -> Discovering
     -> Bonding -> Authenticating -> Ready
every state -> Disconnecting -> Idle
every bounded operation -> Error(reason, retryAt?)
```

**Protocol requirements.**

- The QR payload contains a version, stable device identifier, public-key fingerprint, and signed
  manufacturer/provisioning statement. Validate schema and signature before scanning.
- Bind the selected Bluetooth identity to the QR identity and confirm the peer proves possession of
  the pinned private key. A name regex is discovery assistance, never identity.
- Use ephemeral ECDH for session-key agreement, transcript-bound HKDF, explicit key confirmation,
  monotonically increasing counters/nonces, and authenticated encryption. The protocol needs a
  version and downgrade rule.
- Server claim is signed by both account/session authority and device possession, carries a nonce,
  and uses an idempotency key. HTTP 401/403/409/signature rejection are semantic failures, never
  offline success.
- If offline association is a product requirement, persist `OfflineRestricted` and disable upload,
  reward, ownership-sensitive settings, and payout until server claim succeeds.
- Give scan, connect, service discovery, bond, handshake, and disconnect finite time budgets.
  Preserve `Error` until a new command/retry; rethrow cancellation; close GATT exactly once.
- Start/stop the connected-device foreground service only through a tested presence coordinator and
  comply with Android background-start and Companion Device Manager restrictions.
- Persist only restart-safe pairing checkpoints. Never persist ephemeral session keys or half a
  cryptographic transcript.

**Verification.** Unit-test transition tables and invalid transitions. Instrument with two real
devices plus a controllable fake GATT server. Cover wrong nearby device, process death at each state,
Bluetooth off/on, bond rejection, replay, wrong key, timeout, duplicate claim, 401/403/409, reboot,
service kill, and OEM/API 34–36 behaviour. Record pairing-success rate and per-stage latency/error.

---

### WS-0C — Sensor and location truth model

Zero values are measurements, not absence. Replace seeded `0.0/0.0` and zero sensor vectors with a
model that carries availability and provenance:

```kotlin
sealed interface Sample<out T> {
    data class Available<T>(
        val value: T,
        val capturedAt: Instant,
        val accuracy: Accuracy?,
        val source: Source,
    ) : Sample<T>
    data class Unavailable(val reason: UnavailableReason) : Sample<Nothing>
}
```

- Observe permission changes instead of checking once. Ask permissions in context, explain why,
  degrade gracefully, and resume collection after grant without recreating the process.
- Define freshness budgets per use: conversational context may tolerate older/coarser location than
  a safety alert or reward proof. The server validates timestamps, accuracy, impossible movement,
  and coordinate bounds.
- Do not send unavailable fields. Include capture time and source; distinguish phone, edge device,
  cached, and inferred samples.
- Apply backpressure/sampling to high-rate signals. Do not let a slow network consumer retain an
  unbounded sensor history.
- Establish explicit retention and deletion for precise location and harsh-event history.

**Verification.** Test permission denied/granted/revoked, GPS disabled, stale cache, mock location,
clock skew, 0/0 as a genuine coordinate, impossible accuracy, and process recreation. A query with
no location must remain valid and must never contain invented coordinates.

---

### WS-0D — Payments, rewards, and wallet truth

Money is a server-authoritative state machine, not a UI callback. The current implementation calls a
client secret `PaymentSucceeded`, stores mutable proof data on a singleton repository, casts an
interface to its implementation from the ViewModel, and does not wire the payment sheet/status to a
complete user flow.

**Required design.**

- Disable payout/cash-out in prod until the following contract is complete.
- Define `Requested -> IntentCreated -> AwaitingUserAction -> Processing -> Settled`, with terminal
  `Declined`, `Cancelled`, `Failed`, `Expired`, and `Reversed` states. A Stripe client secret is
  `IntentCreated`, never a charge/settlement identifier.
- The backend calculates balance, fees, exchange rate, eligibility, and amount. The client displays
  a quote with expiry and submits an opaque quote ID.
- Every request has a stable idempotency key persisted before transmission. The backend enforces a
  unique constraint and transactionally records ledger mutation plus outbox event.
- Stripe webhook verification and reconciliation make settlement authoritative. The client polls or
  receives state and can recover after process death without repeating the payout.
- Replace mutable singleton proof fields with immutable request objects. Keep concrete repository
  types behind their interface.
- Use a double-submit guard in UI, but do not confuse it with server idempotency.

**Wallet correction.** The Ed25519 wallet key is currently generated in software, encrypted by an
AES-GCM Android Keystore wrapping key, stored as a PKCS#8 blob, and decrypted into app memory for
signing. Documentation MUST call this a **wrapped software signing key**, not a TEE-non-exportable
Ed25519 key. Either retain it with an honest threat model and optional user authentication, or choose
a signing algorithm/device matrix that Android Keystore can generate and use without exposing key
material. Inspect `KeyInfo.securityLevel`; StrongBox is a capability, not an assumption.

**Verification.** Contract tests cover duplicate taps, retries after timeout, webhook duplication and
reordering, process death, expired quote, insufficient balance, reversal, concurrent devices, and
reconciliation. Property tests assert ledger conservation. No client-only state can mint or settle.

---

### WS-0E — Durable hazard delivery and background execution

MQTT QoS and FCM priority do not equal end-to-end delivery. QoS 1 is at-least-once between client and
broker; duplicates are normal. FCM callbacks have a short execution window, and high-priority messages
are expected to produce user-visible work. An in-memory `SharedFlow` is neither a queue nor durable.

**Target pipeline.**

```text
MQTT/FCM receiver -> validate + dedupe -> Room transaction -> notification policy
                  -> system notification/TTS when allowed
Room observable  -> active UI
WorkManager      -> token registration, gap/full sync, deferred acknowledgement
```

- Give every hazard a globally stable event ID, sequence/version, creation/expiry time, severity,
  geospatial scope, and signature/provenance. Enforce a unique database constraint for deduplication.
- Persist before acknowledging/announcing. Replay only unexpired, unacknowledged events; a new
  collector must not speak the previous alert merely because a flow has replay=1.
- Use a stable MQTT client identity if durable sessions are required. Configure reconnect/backoff,
  subscription state, last sequence, gap recovery, and observable connection status.
- Authenticate to AWS IoT using the selected supported mode—per-device X.509 or Cognito/SigV4 over
  WebSockets—and implement certificate/key rotation and revocation. Server-only TLS is insufficient.
- Post a notification immediately in the FCM callback. Schedule longer registration/sync work with
  WorkManager. Handle `onDeletedMessages` with a bounded full sync.
- Request notification permission in context and define the safe degradation when denied. TTS must
  obey driving/user policy, audio focus, expiry, dedupe, and severity.

**Verification/SLO.** Test duplicate/reordered MQTT and FCM copies, process death, notification denial,
offline gap, expired event, reconnect, broker session loss, token rotation, and reboot. Measure valid
hazards persisted, user-visible delivery latency at P50/P95/P99, duplicate-alert rate, and gap-sync
success. Never claim exactly-once transport; provide effectively-once user experience through durable
deduplication.

---

### WS-0F — Data preservation, privacy, and production platform dependencies

**Room and backup.**

- Remove `fallbackToDestructiveMigration()` from release builds. Enable schema export, commit schema
  history, write explicit auto/manual migrations, and run migration tests from every supported version.
- Decide per data class whether Android cloud backup and device-to-device transfer are allowed.
  Prefer an allow-list. Exclude Keystore-wrapped private blobs, tokens/session preferences, pairing
  identity, FCM/MQTT state, payment state, and sensitive databases unless a documented recovery design
  makes them safe and useful on a different device.
- Publish a data inventory: purpose, lawful/product basis, source, retention, encryption, sharing,
  export/delete behaviour, and log/analytics policy. Keep precise location and conversations out of
  crash metadata by default.

**Maps and dependencies.**

- Migrate from archived OSMDroid to a maintained map renderer/provider selected through an ADR and
  proof-of-concept on supported devices.
- Do not depend on `tile.openstreetmap.org` as production infrastructure. Use a contracted provider or
  responsibly operated/self-hosted tiles with attribution, caching, rate limits, offline terms, privacy,
  and an availability plan.
- Re-evaluate the Eclipse Paho client against the chosen AWS IoT authentication/lifecycle requirements.
- Remove tracked stale `bin/main` Kotlin duplicates and ignore generated `bin/` output. Regenerate the
  knowledge graph so architecture analysis is not polluted by duplicate symbols.
- Adopt automated, grouped dependency updates. Upgrade AGP/Kotlin/Compose/network/AWS/Stripe in small,
  tested compatibility steps; do not merge one blind "update everything" change.

**Verification.** Upgrade from each retained database schema with representative data; restore/transfer
tests prove excluded secrets do not move. Map load/offline/cache/attribution tests pass with rate and
failure injection. Dependency verification and secret scanning run in CI.

---

### WS-1 — CI/CD pipeline

**Intent.** Nothing merges without an automated build + test + lint gate. This is a
high-leverage parallel foundation, but it does not outrank immediate capability freezes and
fail-closed corrections.

**Current.** No workflow. All verification is manual on a developer machine.

**Target.** GitHub Actions on every PR and push to `master`:
1. compile demo and production debug variants and every library module,
2. run all module JVM tests—not only the app aggregate task,
3. lint demo and production; the current prod lint `MissingClass` for
   `HostedUIRedirectActivity` is fixed rather than baselined,
4. run formatting, detekt/Compose rules, dependency verification, secret scanning, and
   architecture assertions,
5. on protected/release jobs, build and smoke-test the minified production release candidate,
6. use the Gradle build/configuration cache only after cache correctness is verified,
7. upload test, lint, coverage, benchmark, dependency, and R8 artefacts,
8. require reviewed PRs, CODEOWNERS for identity/payment/device/security paths, and green checks.

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
      - run: ./gradlew --no-daemon spotlessCheck detekt test lintDemoDebug lintProdDebug assembleDemoDebug assembleProdDebug
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
**Acceptance.** A deliberately failing module test, prod lint error, secret fixture, formatting error,
or forbidden module edge each blocks merge. A release job tests the exact signed/minified candidate.
**Effort.** ~3 h. **Risk.** None (additive).

---

### WS-2 — Static analysis & formatting

**Intent.** Catch correctness/style drift automatically and enforce one code style.

**Current.** No detekt, no ktlint/spotless, no `lint.xml` baseline.

**Target.**
- **detekt** with Compose ruleset ([detekt-compose](https://github.com/mrmans0n/compose-rules))
  for recomposition/stability smells.
- **spotless + ktlint** for formatting (`./gradlew spotlessApply`).
- **Android Lint:** fix errors immediately. If a temporary warning baseline is unavoidable, each
  entry has an owner/issue/expiry; do not baseline missing classes, security, permission, backup,
  data-loss, or release correctness findings.
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

**Intent.** Replace silent `try/catch` + `Log.w` with typed failure semantics at boundaries.
Critical for a **hardware- and network-dependent** app where "why did it fail" is UX.

**Current.** BLE handshake failure, `/telemetry` 401, RPC timeout — all logged and dropped.
Only `ClaimDeviceRepository` models errors as a sealed type.

**Target.** A small error taxonomy where callers must make a policy decision. Do not mechanically
wrap every Kotlin function. Throw programming errors, rethrow cancellation, use domain results for
expected business outcomes, and expose durable streams as state models containing freshness/error
metadata. Preserve HTTP semantics such as 401, 403, 409, 429, and 5xx instead of converting them all
to "network error".

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
**Acceptance.** No security/payment/ownership failure is logged-and-swallowed; error mapping is
exhaustive and tested; cancellation passes through; UI distinguishes retryable, user-action,
configuration, policy, and terminal failures without exposing raw exception text.
**Effort.** ~1–1.5 days. **Risk.** Medium (touches every repo) — do behind WS-5 tests.

---

### WS-4 — Cohesive workflows and ViewModel decomposition

**Intent.** Give business logic a single, testable home; shrink `CopilotViewModel`.

**Current.** Logic lives inline in ViewModels/repositories. `CopilotViewModel` is approximately
1,100 lines; `startSearch` alone performs context, persistence, SSE, citations, voice, wallet, and
error orchestration.

**Target.** Extract tested state machines/workflows first. They may initially live in the owning
feature/core module. Create `:core:domain` only if more than one feature consumes the policies or
module enforcement materially improves ownership/build isolation. A one-line repository wrapper is
not a use case.

**Candidate UseCases (initial set)**
| UseCase | Wraps / owns |
|---|---|
| `SubmitHazardDetectionUseCase` | detection threshold + `signTelemetry` + POST `/telemetry` |
| `EvaluateHazardFromTelemetryUseCase` | RRI threshold → is-this-a-hazard decision |
| `ObserveWalletUseCase` | server-authoritative balance observation + freshness/reconciliation |
| `EnsureWalletProvisionedUseCase` | explicit wrapped-software/Keystore key posture + idempotent registration |
| `ObserveSensorContextUseCase` | fuse GPS + BLE frame into `VigiaSearchContext` |
| `StreamCopilotAnswerUseCase` | SSE search orchestration |
| `PairAndClaimDeviceUseCase` | QR validation → peer proof → CDM association → durable server claim |
| `RequestPayoutUseCase` | quote → idempotent intent → user action → webhook-confirmed settlement |

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
**Acceptance.** Search, voice turn-taking, pairing, payout, and alert policy have explicit transition
tests; ViewModels translate UI events and expose immutable UI state; `CopilotViewModel` has one reason
to change per collaborator and no concrete-repository casts. Module count is not an acceptance metric.
**Effort.** ~2 days (after WS-5). **Risk.** Medium — behavior-preserving refactor; needs tests first.

---

### WS-5 — Testing strategy

**Intent.** A real safety net, prioritized at the code that moves money and data.

**Current.** About five meaningful unit-test files plus Android Studio template tests. No meaningful
coverage of production auth, pairing claim, BLE/service lifecycle, Room migration, MQTT/FCM delivery,
payment settlement, backup, map provider, or wallet key posture.

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
- **Release contract tests:** production configuration, minified build, Cognito redirect, backend
  auth/error mapping, AWS IoT authentication, Stripe webhook/reconciliation, migration/backup rules.
- **Hardware/device tests:** BLE protocol/lifecycle on a supported OEM matrix; process-death and reboot.
- **Coverage:** use Kover to find untested branches. A percentage is diagnostic, not proof. Gate critical
  workflow branch/transition coverage and mutation-test selected pure policies before choosing a number.

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
**Acceptance.** Each stop-ship invariant has at least one failing-before/passing-after automated test;
CI runs every module; release smoke tests use the minified candidate; flaky tests are quarantined only
with owner, issue, telemetry, and expiry.
**Effort.** ~2–3 days. **Risk.** Low (additive) — but unlocks WS-3/WS-4 safely.

---

### WS-6 — Offline-first data layer

**Intent.** A driving app lives in tunnels and dead zones — cached data, not blank screens.

**Current.** Room only stores chat + harsh events. Wallet balance, hazards, rewards,
maps data are network-only; connection loss = empty UI.

**Target.** Room-backed durable read models for chat, hazard inbox/history, pending claim/outbox,
and cached wallet/reward views. Each cached value carries source, server version, observed time,
expiry/freshness, and last-refresh error. The backend remains authoritative for ownership and money.
Repositories expose database flows and explicit refresh commands. WorkManager handles deferrable,
persistent work; it is not an exact timer and does not replace immediate alert notification.

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
**Acceptance.** Airplane mode shows labelled last-known state rather than fabricated freshness;
pending operations survive process death and retry idempotently; expired hazards do not alert; users
cannot cash out against cached balance; repository tests cover cache hit/miss/stale, refresh failure,
conflict, duplicate work, clock skew, and recovery.
**Effort.** ~2–3 days. **Risk.** Medium — sits on WS-3 (offline == an error/loading state).

---

### WS-7 — Performance engineering

**Intent.** Fast cold start and jank-free scrolling, measured not guessed.

**Current.** No baseline profile module, no macrobenchmark, no Compose-stability audit,
R8 on for release but unverified. (We already fixed pager jank + map re-inflation ad hoc.)

**Target.**
- **`:benchmark` module** with Macrobenchmark: startup (cold/warm) + a scroll journey.
- **Baseline Profiles** generated from representative journeys and shipped only after a measured
  before/after comparison on physical devices. Do not promise a fixed percentage for this app.
- **Compose stability:** measure recomposition first. Mark types `@Immutable`/`@Stable` only when
  their contract is actually true; incorrect annotations hide bugs.
- **R8:** verify current AGP defaults and rules rather than relying on legacy flags. Exercise Amplify,
  Stripe, MQTT, Room, Hilt and serialisation paths in the minified release candidate.
- **StrictMode** in debug (disk/network on main thread → crash in dev).
- Bound SSE event, line, answer, and stream-duration sizes; replace `Channel.UNLIMITED`; batch UI text
  updates to avoid O(n²) string allocation and excessive recomposition.

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
- A structured logging abstraction (Timber is optional), debug sink only for detailed local logs.
- **Crashlytics** (Firebase — you already pull FCM) *or* Sentry; wire a `CrashReporter`
  interface in `:core:common` so the vendor is swappable and absent in tests.
- **Analytics abstraction:** allow-list schemas, data classification, consent/region controls, and
  no-op test/demo implementation. Use coarse location or irreversible aggregation when possible.
- **Reliability telemetry:** crash-free/ANR-free sessions; app startup; auth readiness; pairing stage
  success/latency; BLE ready duration; hazard ingress-to-visible latency; SSE first-token/completion;
  payout state/reconciliation; Room migration and WorkManager outcomes.
- **SLOs:** define user-centred indicators and error budgets before alert thresholds. Dashboards link
  client version, device/API, backend correlation ID and feature flag without collecting raw PII.
- **Runbooks:** owner, impact, detection, immediate mitigation/kill switch, diagnosis, rollback,
  communication, and post-incident action for identity, pairing, alert delivery, search, and payout.
- **Coroutine/Compose tracing** in debug for perf sessions.

**Files.** `:core:common` (`CrashReporter`, `AnalyticsHelper`), Timber init in
`VigiaApplication`, DI bindings.
**Acceptance.** A forced non-PII crash and synthetic critical-path failures appear in the correct
dashboard/alert; redaction tests cover tokens, email, coordinates, conversation and payment IDs;
each production alert has an owner and runbook; telemetry can be remotely reduced/disabled.
**Effort.** ~1 day. **Risk.** Low.

---

### WS-9 — Security hardening

**Intent.** Protect keys, transport, and the reward economy against tampering.

**Current.** The Ed25519 wallet key is a wrapped software key and enters app memory during signing.
BLE identity/session-key behaviour and production key provisioning require threat-model validation.
Networking has flavour configuration, but required values may be empty and debug body logging can
include bearer tokens and sensitive payloads. The repository documentation overstates protection.

**Target additions.**
- **TLS everywhere** for prod. Decide certificate/public-key pinning from the threat model. Pinning is
  not automatic maturity: if adopted, use backup pins, overlapping rotation, expiry, telemetry and a
  remotely recoverable plan; otherwise rely on platform trust plus correct endpoint validation.
- **Play Integrity API** attestation before minting-eligible telemetry is accepted
  (anti-emulator/anti-tamper) — pairs with the backend spoof-slash.
- **Dependency vulnerability scanning:** OWASP dependency-check or GitHub Dependabot
  alerts in CI.
- **Secret scanning:** gitleaks in CI; confirm no `secrets.properties`/keys are committed.
- **R8 obfuscation** for release; keep-rules reviewed (WS-7).
- **Rooted/tamper posture:** document what Play Integrity can and cannot prove; the server remains the
  enforcement point. Do not claim the current Ed25519 key is non-exportable or StrongBox-backed.
- **Threat model:** assets, actors, trust boundaries, abuse cases, privacy harms, mitigations, residual
  risk, incident/revocation paths. Cover stolen phone, malicious app, rooted device, BLE impersonation,
  replay, compromised edge device, account takeover, API abuse, broker credentials, and payout fraud.

**Files.** `network_security_config.xml` (TLS and the documented pinning decision), Play Integrity client in
`:core:auth` or `:core:wallet`, `.github/workflows/security.yml`, `THREAT_MODEL.md`.
**Acceptance.** Prod rejects invalid/non-TLS endpoints and missing config; secret/log redaction tests
pass; critical dependency findings have policy/owner/SLA; key security level is measured; revocation
and rotation drills work. If pinning is selected, rotation is tested before enforcement.
**Effort.** Estimate after the threat model and backend attestation contract. **Risk.** Medium–High:
security controls can create lockout/availability failures and must have rotation/recovery tests.

---

### WS-10 — Type-safe navigation

**Intent.** Compile-checked routes, no stringly-typed nav.

**Current.** Auth/pairing gate + landing pager use route strings/booleans; the OAuth manifest accepts
the broad `vigia:` scheme without exact host/path ownership.

**Target.** Navigation Compose with **type-safe routes** (`@Serializable` route objects,
`navController.navigate(WalletRoute)`), or migrate to **Navigation 3** if adopted. Model
the top-level graph: `Auth → Pairing → Copilot(landing tabs)`.

**Files.** `:feature:copilot` `AppRoot`/nav host, route definitions per feature.
**Acceptance.** Internal destinations are compile checked; external links are allow-listed, verified,
and tested against interception/malformed input; back stacks survive process recreation.
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
- Driving-mode contract: safety review defines what is disabled while moving, eyes-free actions,
  interruption priority, audio focus, glanceability, and fallback when speech fails. Do not invent a
  universal numeric glance threshold without product/human-factors validation.

**Files.** semantics on composables, a11y assertions in UI tests.
**Acceptance.** a11y checks pass in CI; no unlabeled interactive nodes on core screens.
**Effort.** ~1 day. **Risk.** Low.

---

### WS-12 — Dependency & supply-chain management

**Intent.** Stay current and reproducible without manual bumps.

**Current.** Version catalog present. No automated updates, dependency verification, lockfiles, SBOM,
or explicit response to the archived OSMDroid dependency.

**Target.**
- **Renovate** (or Dependabot) PRs for catalog + gradle-wrapper updates, grouped weekly.
- **Gradle dependency verification** (`verification-metadata.xml`) for checksums.
- **Dependency lockfiles** for reproducible CI.
- Pin the AGP/Kotlin/KSP triple explicitly (already in catalog — keep aligned).
- Produce an SBOM for release candidates and retain provenance for signing/build artefacts.
- Migration PRs include upstream release notes, compatibility risks, minified smoke tests, and rollback.

**Files.** `renovate.json`, `gradle/verification-metadata.xml`, lockfiles.
**Acceptance.** Update PRs open automatically and pass CI; builds verify checksums.
**Effort.** ~0.5 day. **Risk.** Low.

---

### WS-13 — Documentation & ADRs

**Intent.** Decisions are discoverable; the `wiki/` stays accurate.

**Current.** README, `GAPS.md`, design specifications, and blog drafts exist, but several statements
describe intended architecture as shipped behaviour. Stale tracked `bin/main` Kotlin duplicates also
pollute code/knowledge-graph discovery.

**Target.**
- **ADRs** under `docs/adr/` for: auth fail-closed source sets, device claim/protocol, wallet key
  posture, payment state/idempotency, offline cache authority/freshness, MQTT/AWS IoT auth, map engine
  and tile provider, certificate pinning decision, retention/backup, module boundary changes.
  Use the existing `codebase-memory-mcp` `manage_adr` tool to keep them indexed.
- **Module READMEs** (one-paragraph responsibility + public API) per module.
- Maintain an implementation status vocabulary: `Implemented`, `Partially implemented`, `Planned`,
  `Blocked`, `Verified in release`. README/blog claims link to evidence or use that vocabulary.
- Remove generated duplicates and re-index codebase-memory after structural changes.

**Files.** `docs/adr/*.md`, `*/README.md`.
**Acceptance.** High-impact decisions have ADRs; README/blog/security claims match audited code;
module docs name responsibilities and public APIs; documentation checks flag broken links and stale
status markers.
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
- **Environment promotion:** dev/demo → internal → closed/beta → staged production. Promote the same
  immutable artefact; server configuration and feature flags are versioned/audited.
- **Rollout controls:** percentage staged rollout, compatibility gates, remote feature flags and kill
  switches for payout, MQTT, TTS/voice, telemetry upload, and new pairing protocol.
- **Release evidence:** test matrix, lint/static reports, SBOM, dependency scan, R8 mapping, signing
  identity, config fingerprint, privacy/data-safety review, known risks and rollback owner.

**Files.** `app/build.gradle.kts` signing/publishing, `.github/workflows/release.yml`,
CI secrets (keystore, service account json).
**Acceptance.** A protected tag produces a signed AAB on the internal track; testers exercise auth,
pairing, BLE service, search, notification, migration, map and disabled/enabled payout in the minified
artefact; mapping/SBOM are retained; staged rollout can be halted without a client update.
**Effort.** ~1.5 days. **Risk.** Medium (keystore handling — document recovery).

---

## 4. Module changes summary

| Module | Change |
|---|---|
| `:app` | Composition root, typed navigation, exact external-link routing, foreground-service/notification integration, production configuration gate |
| `:core:auth` | Compile-time demo/prod separation; fail-closed init; token lifecycle; OAuth/App Link contract |
| `:core:sensor` | First decompose internally into device-link, context, and alert-delivery packages with tested state machines; split modules only if boundaries prove stable |
| `:core:network` | Bounded SSE; structured DTOs/versioning; typed HTTP semantics; redacted logging; AWS IoT/FCM/Stripe contracts |
| `:core:wallet` | Honest wrapped-software-key posture or supported non-exportable key migration; immutable signing/payment requests; concurrency tests |
| `:core:data` | Exported Room schemas, explicit migrations, durable hazard/pending-operation/read-model tables, backup policy |
| `:feature:copilot` | Remove dependencies on other feature implementations; split search/voice/alert/wallet collaborators; no concrete repository casts |
| `:feature:pairing` | Durable pairing/claim state machine, QR/peer identity binding, restricted offline state, process-death recovery |
| `:feature:maps` | Maintained renderer/provider abstraction; tile policy, attribution, cache/privacy/failure handling |
| `:core:common` | **Optional** — only shared clock/dispatcher/error/telemetry contracts that have multiple real consumers |
| `:core:domain` | **Optional** — complex/reused workflows; not a mandatory wrapper layer |
| `:core:testing` | **Add when shared fixtures justify it** — fakes, dispatcher/clock, protocol servers, data builders |
| `:benchmark` | **Add after journeys stabilise** — Macrobenchmark and Baseline Profile generation |
| `build-logic` | new `AndroidQualityConventionPlugin` (detekt/spotless/lint/kover) applied everywhere |
| `gradle/libs.versions.toml` | controlled additions/upgrades selected by ADR/compatibility tests; no speculative bulk version block |

---

## 5. Dependency-selection policy

This specification intentionally does not pin future library versions. Versions are time-sensitive
and must be selected when each implementation PR is opened.

For every dependency addition or major upgrade, record:

1. the capability/problem it solves and why platform/current code is insufficient;
2. upstream support/maintenance and release cadence;
3. licence, privacy/network behaviour, transitive dependencies and binary size;
4. API/minSdk/targetSdk/AGP/Kotlin/R8 compatibility;
5. security advisories and dependency-verification checksum;
6. migration and rollback plan;
7. unit/integration/minified-release/device evidence.

Particular decisions requiring ADRs are the map renderer/tile service, MQTT/AWS IoT client/auth mode,
crash/analytics vendor, screenshot-test framework, Play publishing mechanism, and any cryptographic
provider. Prefer Android/Jetpack and service-vendor-supported SDKs when they satisfy the requirement,
but keep vendor calls behind small boundaries where replacement risk is material.

---

## 6. Phased rollout plan

Ordered by **risk reduction and dependency**, not architectural purity. Early phases produce
internal candidates; only phase 6 can establish a production candidate.

| Phase | Workstreams | Exit outcome | Indicative duration | Release posture |
|---|---|---|---|---|
| **0 — Freeze unsafe capabilities** | Disable prod payout; fail required config; document demo-only paths; create P0 owners/issues | No new production claim; risky capabilities cannot be mistaken for complete | 1–2 days | No public release |
| **1 — Identity and ownership** | WS-0A, WS-0B plus focused tests/telemetry | Auth fails closed; device claim and BLE lifecycle meet transition/integrity tests | 2–4 weeks | Internal hardware alpha |
| **2 — Truth and durability** | WS-0C, WS-0E, WS-0F; Room migrations/backup | No invented context; alerts/operations survive death/reboot/update; AWS IoT auth works | 2–4 weeks | Closed alpha |
| **3 — Money and privacy** | WS-0D, privacy inventory, threat model, server contracts | Webhook-confirmed idempotent payout; key claims accurate; delete/export/retention defined | 2–4 weeks plus backend/compliance | Closed beta |
| **4 — Engineering system** | WS-1, WS-2, WS-3, WS-5, WS-8, WS-12, WS-13 continuously alongside 1–3 | CI/review/observability/runbooks make correctness enforceable | parallel, 2–4 weeks initial | Required before beta |
| **5 — Structure and product quality** | WS-4, WS-6, WS-10, WS-11; map migration | Maintainable boundaries, accessible/safe driving UX, honest offline behaviour | 2–4 weeks | Beta/staged |
| **6 — Release and scale** | WS-7, WS-9, WS-14; SLO/error-budget review | Signed reproducible candidate, measured performance, staged rollout and rollback | 2–3 weeks | Production candidate |

These durations are planning ranges, not commitments. Several streams require firmware/backend/cloud,
real hardware, policy, and store configuration. Estimate after protocol/API contracts are agreed.
Make changes small and independently reviewable; do not combine auth, BLE, database, dependency and
architecture rewrites into one migration branch.

### 6.1 First ten implementation changes

1. Add a build-time required-production-config validator and compile-time demo auth separation.
2. Disable payout/cash-out in prod and rename client-secret status correctly.
3. Remove destructive release migration; export Room schemas and add v1→v2 migration tests.
4. Replace backup templates with an explicit allow-list/exclusions and test restore/device transfer.
5. Add BLE deadlines/error preservation and call the foreground service through a tested coordinator.
6. Stop local-success fallback for claim policy/auth failures; introduce `OfflineRestricted` if needed.
7. Replace zero context defaults with explicit unavailable/stale samples and permission observation.
8. Persist/dedupe FCM alerts and immediately create a notification; add gap-sync WorkManager job.
9. Fix production lint/Hosted UI registration and establish CI across every module/flavour.
10. Choose AWS IoT auth and map-provider migrations through ADR/proof-of-concept before implementation.

---

## 7. Definition of Done (acceptance matrix)

### Identity, device and cryptography

- [ ] Demo authentication is absent from the prod runtime graph; missing Amplify config blocks safely.
- [ ] Required prod values are non-empty, valid and HTTPS where applicable; lint is green.
- [ ] OAuth redirect is exact/verified and state, nonce, cancel, interception, expiry and process death are tested.
- [ ] QR identity, selected peer, cryptographic peer proof and server ownership claim bind the same device.
- [ ] 401/403/409/signature rejection never creates a local success; offline state is visibly restricted.
- [ ] BLE operations have deadlines, explicit transitions, cancellation and exactly-once cleanup.
- [ ] Protocol versioning, replay/downgrade resistance, provisioning, rotation and revocation are documented/tested.
- [ ] Wallet documentation and UI accurately describe hardware/non-exportability; measured security level is recorded.

### Data, delivery and money

- [ ] Unknown/stale context remains unknown/stale and carries timestamp, accuracy and source.
- [ ] Critical hazards are validated, transactionally persisted, uniquely deduplicated and user-visible when allowed.
- [ ] AWS IoT client authentication, reconnect/session semantics, token/certificate rotation and gap recovery work.
- [ ] Room schemas are exported; all supported migrations pass; release has no destructive fallback.
- [ ] Backup/device-transfer tests prove keys/tokens/pairing/payment/sensitive state do not leave their approved boundary.
- [ ] Cached wallet values show freshness and cannot authorise payout.
- [ ] Payout uses persisted idempotency, server quotes, verified webhooks and reconciliation; duplicate/reordered events pass.
- [ ] Retention, export/delete, privacy policy and store Data Safety declarations match actual collection/SDK behaviour.

### Engineering and release

- [ ] CI green-gates every PR across all modules and demo/prod; security-sensitive paths have CODEOWNERS.
- [ ] Formatting/static/lint/dependency/secret/architecture checks pass; suppressions have owner and expiry.
- [ ] Each stop-ship invariant has automated negative and recovery tests; exact minified release candidate is exercised.
- [ ] Search/voice/pairing/payout/alert workflows are explicit and tested; no concrete repository casts in ViewModels.
- [ ] No unbounded stream/buffer/retry; cancellation and backpressure tests pass.
- [ ] Crash/ANR/performance and critical-path SLIs are redacted, dashboarded and linked to owned runbooks.
- [ ] Map renderer/provider is maintained and production tile use has SLA/terms/attribution/privacy/offline compliance.
- [ ] Accessibility, dynamic type, notification permission, audio focus and moving-vehicle UX gates pass.
- [ ] Dependency verification, SBOM, signed AAB, R8 mapping and release evidence are retained.
- [ ] Internal/closed/staged promotion and kill-switch/rollback drills succeed.
- [ ] README, design specs and Hashnode articles distinguish implemented, partial, planned and verified behaviour.

---

## 8. Risks & rollback

| Risk | Mitigation |
|---|---|
| Fail-closed auth temporarily locks out prod | Validate config in CI/internal track; keep last-known-good config and an owned incident rollback—never enable demo auth |
| Pairing protocol change strands installed hardware | Version negotiation, compatibility matrix, staged firmware/app rollout, dual-read/single-write migration window, remote disable |
| Alert persistence produces duplicate speech/notifications | Unique event IDs, transactional dedupe, expiry/ack policy, shadow metrics before enabling announcement |
| Payment migration risks money | Keep prod capability disabled; sandbox/contract/reconciliation tests; backend idempotency; small invite-only rollout |
| Database migration loses data | Schema export, migration tests from every retained version, encrypted test backup, staged rollout and restore drill |
| R8 passes compile but breaks reflection/runtime | Minified candidate smoke tests for every SDK; retain mapping/config; internal track before promotion |
| Certificate pinning bricks clients | Prefer documented threat-based choice; if adopted, overlapping backup pins, expiry, telemetry, rotation drill and recovery channel |
| Large architecture refactor changes behaviour | Extract one tested workflow at a time; no module-count goal; preserve API until callers migrate |
| Observability leaks sensitive data | Allow-list schemas, redaction/property tests, coarse aggregation, access/retention controls, remote kill switch |
| Public map provider throttles/blocks app | Contracted/self-hosted provider, caching and attribution compliance, quotas/alerts, offline/error UX |
| Dependency upgrade stack becomes unreviewable | Group compatible small updates, one ecosystem at a time, release notes and rollback per PR |
| Schedule pressure relabels planned behaviour as shipped | Status vocabulary plus release-evidence links; stop-ship checklist owned outside feature team |

---

## 9. Appendix — reference alignment

- Google, [Guide to app architecture](https://developer.android.com/topic/architecture) ·
  [UI layer](https://developer.android.com/topic/architecture/ui-layer) ·
  [Domain layer](https://developer.android.com/topic/architecture/domain-layer) ·
  [Architecture recommendations](https://developer.android.com/topic/architecture/recommendations)
- [Now in Android](https://github.com/android/nowinandroid) — modularization, offline-first,
  fakes-over-mocks, convention plugins, tests and baseline profiles. Its architecture learning
  journey explicitly notes that it is not identical to every "Clean Architecture" formulation.
- Android, [Room migrations](https://developer.android.com/training/data-storage/room/migrating-db-versions) ·
  [Auto Backup](https://developer.android.com/identity/data/autobackup) ·
  [runtime permissions](https://developer.android.com/training/permissions/requesting) ·
  [persistent work](https://developer.android.com/develop/background-work/background-tasks/persistent) ·
  [foreground-service restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start) ·
  [Android Keystore](https://developer.android.com/privacy-and-security/keystore) ·
  [App Links](https://developer.android.com/training/app-links/about) ·
  [Network Security Configuration](https://developer.android.com/privacy-and-security/security-config).
- Firebase, [receive messages on Android](https://firebase.google.com/docs/cloud-messaging/android/receive-messages) ·
  [message priority](https://firebase.google.com/docs/cloud-messaging/android-message-priority).
- AWS IoT, [device communication protocols](https://docs.aws.amazon.com/iot/latest/developerguide/protocols.html) ·
  [client authentication](https://docs.aws.amazon.com/iot/latest/developerguide/client-authentication.html) ·
  [X.509 certificates](https://docs.aws.amazon.com/iot/latest/developerguide/x509-client-certs.html).
- OWASP, [Mobile Application Security Verification Standard](https://mas.owasp.org/MASVS/).
- OpenStreetMap Foundation, [standard tile usage policy](https://operations.osmfoundation.org/policies/tiles/) ·
  archived [OSMDroid repository](https://github.com/osmdroid/osmdroid).
- Google, [engineering code-review practices](https://google.github.io/eng-practices/review/) ·
  [SRE service-level objectives](https://sre.google/sre-book/service-level-objectives/).
- Meta, [MobileLab performance regression infrastructure](https://engineering.fb.com/2018/10/19/android/mobilelab/) ·
  [large-scale mobile testing](https://engineering.fb.com/2017/05/24/android/managing-resources-for-large-scale-testing/).
- Existing repo conventions retained where accurate: `libs.versions.toml`, `build-logic`, design
  specifications, `GAPS.md`, and codebase-memory graph/ADR tooling.

---

## 10. Interview and design-review prompts

The implementation is complete only when its owner can defend it under adversarial questioning.
These prompts double as design-review checklists and FAANG-style interview preparation.

### Android, Kotlin and operating systems

1. Why can a ViewModel survive configuration change but not guarantee survival after process death?
2. When should durable work use WorkManager, a foreground service, FCM immediate work, or an in-process coroutine?
3. How do structured concurrency and cancellation change the design of BLE connection/retry cleanup?
4. What are the memory/backpressure consequences of `Channel.UNLIMITED` and repeated immutable-string concatenation?
5. How do cold Flow, `StateFlow`, `SharedFlow`, Room observable queries and replay differ?
6. How can two check-then-act operations race during wallet provisioning, device claim, or payout?
7. Why are `@Stable`/`@Immutable` promises rather than performance decorations?
8. What survives activity recreation, process death, force stop, reboot and app update in this design?

### Networks and distributed systems

1. Compare SSE, WebSocket, MQTT and FCM for directionality, connection ownership, ordering, backpressure,
   power, intermediary compatibility and delivery semantics.
2. Explain why MQTT QoS 1 is at-least-once and how a stable event ID plus transactional uniqueness creates
   an effectively-once user experience.
3. Why do retries need deadlines, exponential backoff, jitter, caps and idempotency? Which failures must not retry?
4. What does `cleanSession=false` mean if every connection uses a random client ID?
5. Design gap detection when MQTT messages are missed and FCM messages are deleted or reordered.
6. Which system is authoritative for device ownership, sensor presence, cached balance and payment settlement—and why?
7. Where do linearizability, eventual consistency, the outbox pattern and reconciliation apply here?
8. Define SLIs/SLOs for pairing, hazard delivery and Copilot streaming, including P95/P99 rather than averages.

### Security and cryptography

1. Distinguish a Keystore-generated non-exportable signing key from a software private key encrypted by a
   Keystore wrapping key. What attacks does each stop?
2. How do ECDH, signatures, HKDF, transcript binding, key confirmation, nonces and AEAD combine in pairing?
3. How do you prevent MITM, replay, downgrade, malicious QR replacement and a compromised edge node?
4. Compare bearer tokens, proof-of-possession, mTLS X.509 and Cognito/SigV4 for a mobile IoT client.
5. What does certificate pinning defend against, and how can it create an availability incident?
6. What can Play Integrity signal, and why must the backend still enforce every economic rule?
7. How do backup, logs, analytics, crash reports and screenshots expand the threat/privacy boundary?

### Data structures, databases and system design

1. Model BLE/pairing/payment as transition tables. How do you prove invalid transitions are impossible?
2. Choose data structures for bounded SSE assembly, hazard dedupe/LRU, retry scheduling and a spatial alert index.
3. Explain Room WAL/transactions, schema migrations, unique constraints and why destructive migration is unacceptable.
4. Design a road-hazard ingestion/fan-out system for millions of devices using geospatial partitioning, dedupe,
   expiry, backpressure and regional failure recovery.
5. Design an idempotent payout ledger under duplicated webhooks and concurrent devices. State the transaction boundary.
6. Estimate client/server QPS, storage, notification fan-out and battery/network cost. Identify the true bottleneck.
7. Describe a staged rollout, kill switch and rollback when firmware, Android app and backend protocols change together.

The strongest answer names assumptions, invariants, failure modes, trade-offs, metrics, test evidence, and rollback.
It does not answer "we use MVVM/Clean Architecture" as though a framework name proves correctness.
