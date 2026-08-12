# Engineering the VIGIA copilot: a from-zero tour of a production-shaped Android app

*An in-vehicle AI that runs hands-free, tolerates dead zones, and pairs to a road sensor — and a complete walk through the Android stack that makes it work, every framework we chose, and every one we rejected.*

Most "AI assistant" apps assume a user sitting still, looking at a screen, on good Wi-Fi, with both hands free. A driver has none of that. They are moving, their eyes belong on the road, connectivity drops in exactly the places where hazards are worst, and the useful information — a pothole, a stray animal, a road's safety rating — is about the few hundred metres directly ahead. VIGIA Mobile is our attempt to build a copilot for that person.

This overview does two jobs. First, it's the **map of the series** — five deep dives, each on one hard decision. Second, and new in this rewrite, it's a **from-zero tour of the whole Android stack**: what every layer is, which framework we picked, what we picked it *over*, and what it takes to move an app like this from a hackathon demo to something you could actually ship. If you are studying for an Android or systems interview, read this end to end. The code is open at [github.com/VigiaLabs/vigia-android](https://github.com/VigiaLabs/vigia-android).

## What the app does

VIGIA Mobile is the Android companion to the VIGIA road-intelligence system. It pairs over Bluetooth with a Raspberry Pi "black box" running computer vision at the kerb, streams that live road context into a voice copilot, answers a driver's spoken questions about the road ahead through the VIGIASearch backend, and speaks the answer back — without the driver touching the screen. It receives real-time hazard alerts over MQTT and reads the critical ones aloud, and it manages an on-device rewards wallet for contributing road data.

The shape is **three tiers meeting in one app**. Below it, a Pi edge node feeds telemetry over an authenticated Bluetooth link. Above it, a cloud backend answers queries and pushes alerts. In the middle, the app fuses the two — the driver's spoken question is enriched with the vehicle's real GPS, speed, and road-roughness reading before it ever reaches the cloud, so every answer is grounded in where the car actually is.

## Starting from zero: what an Android app *is* made of

Before the specific decisions, here is the ground floor, because "app development from zero" means knowing what each piece does and why it exists.

An Android app is Kotlin (or Java) code compiled to run on the Android Runtime (ART), packaged as an APK/AAB, built by **Gradle**. Every serious app is a stack of layers, and for each layer there's a dominant framework and a set of alternatives. Here's the whole stack VIGIA uses, and the reasoning:

**Language — Kotlin (over Java).** Kotlin is Google's default for Android since 2019: null-safety in the type system, coroutines for async, concise data classes, and sealed types for exhaustive state. Java still works, but every modern Jetpack API is Kotlin-first. We're on Kotlin `2.0.21` with KSP (Kotlin Symbol Processing) for annotation processing — KSP replaced the older `kapt` because it's roughly 2× faster at generating Hilt/Room code.

**UI toolkit — Jetpack Compose + Material 3 (over XML Views).** The old model was XML layouts inflated into a `View` tree and mutated imperatively (`findViewById`, `textView.setText(...)`). Compose is *declarative*: you write functions that describe the UI as a function of state, and the framework recomposes when state changes. We chose it because a driving UI is highly stateful (listening → thinking → speaking) and declarative UI makes "UI = f(state)" literal. The trade-off: Compose has a learning curve around recomposition and stability, and a slightly heavier cold start (which the baseline-profile work in our hardening plan addresses). Material 3 gives us the design system; **Haze** adds the backdrop-blur "liquid glass" surfaces.

**Architecture pattern — MVVM + Unidirectional Data Flow (over MVC/MVP).** State flows **down** from a `ViewModel` to Composables as an immutable `UiState`; events flow **up** as function calls. This is Google's officially recommended pattern and the one the *Now in Android* reference app uses. We picked it over MVC/MVP because UDF makes state changes traceable and testable — there's exactly one place state mutates.

**Dependency injection — Hilt (over Koin or manual DI).** Hilt is Google's DI framework built on Dagger; it generates the wiring at compile time. We chose it over **Koin** (runtime, reflection-ish, fails at runtime if a binding is missing) because compile-time DI catches a missing dependency as a *build error*, and over hand-rolled factories because DI is what lets nine modules snap together without a giant manual object graph.

**Asynchrony — Kotlin Coroutines + Flow (over RxJava/callbacks/LiveData).** Every network call, BLE read, and sensor stream is async. Coroutines give us `suspend` functions that read like straight-line code, structured concurrency (child jobs cancel with their parent), and `Flow`/`StateFlow` for reactive streams. We chose it over **RxJava** (powerful but a heavy operator vocabulary) and over `LiveData` (Android-only, lifecycle-bound but less composable) because coroutines are the modern standard and `StateFlow` is the natural driver of a Compose `UiState`.

**Local persistence — Room + DataStore (over raw SQLite / SharedPreferences).** **Room** is an ORM over SQLite with compile-time-checked SQL and `Flow`-returning queries; we use it for chat history and harsh-driving events. **DataStore** replaces the old `SharedPreferences` for small key/values (it's coroutine/Flow-based and transactional). We chose Room over **SQLDelight** mostly for Jetpack cohesion and the `Flow` integration.

**Networking — OkHttp + Retrofit, plus raw OkHttp for streaming (over Ktor/Volley).** Retrofit turns a REST API into a typed Kotlin interface; OkHttp is the HTTP engine underneath. For the copilot's token-by-token answer we drop to raw OkHttp to consume a **Server-Sent Events** stream. We chose this stack over **Ktor client** (fine, but Retrofit/OkHttp is the most battle-tested on Android) and over the ancient **Volley**.

**Maps — OSMDroid (over the Google Maps SDK).** This one is a deliberate cost decision: the Google Maps SDK needs an API key and billing; **OSMDroid** renders OpenStreetMap tiles with *no key and no per-call cost*. For a civic road-safety app that should run anywhere without a billing account, zero-cost mapping won.

**Messaging — Eclipse Paho MQTT v3 + Firebase Cloud Messaging (two paths on purpose).** Hazard alerts arrive over a persistent **MQTT** pub/sub connection (Paho is the battle-tested Android client); **FCM** is the fallback push that can wake the app when the OS has suspended the socket. Two paths because either one alone has a gap (Episode 5).

**Auth — AWS Amplify (Cognito) + Credential Manager (over Firebase Auth / the old Google Sign-In).** Cognito User Pools handle identity with Google federation; **Credential Manager** is the modern native "Sign in with Google" picker that replaced the deprecated `GoogleSignInClient`. Cognito because the backend already lives in AWS.

**Camera + scanning — CameraX + ML Kit barcode (over Camera2 directly).** CameraX is the lifecycle-aware wrapper over the low-level Camera2 API; ML Kit's bundled barcode scanner reads the pairing QR with no Play Services dependency.

**Build system — Gradle with a version catalog + convention plugins.** All versions live in one `libs.versions.toml`; shared build config lives in **convention plugins** under `build-logic/` so ten modules don't copy-paste their setup. This is the *Now in Android* pattern (Episode 1).

## The layered architecture, the way Google draws it

Those frameworks are organized into layers, and the direction of dependencies is the whole game:

```
UI layer      :feature:copilot / :maps / :pairing
              Composable ↔ ViewModel ↔ UiState (StateFlow)      ← state down, events up
Domain layer  (business rules — UseCases)                       ← the single home for logic
Data layer    :core:network / :sensor / :wallet / :auth / :data
              Repository (interface) → RepositoryImpl           ← single source of truth
              ├─ remote (OkHttp / MQTT / BLE / RPC)
              └─ local  (Room / DataStore / Keystore)
Model         :core:model  — pure Kotlin data classes
```

The rule: **feature depends on core, never on another feature; the UI never touches the network directly — it goes through a repository.** A repository is the *single source of truth* for a data type, and (in the ideal Google/NIA form) it reads from the local database and syncs the network in the background, so the UI keeps working offline.

## The five decisions worth reading about

Each episode is a standalone deep dive on one choice where we picked the harder path for a reason:

**Episode 1: Why a phone app has nine modules.** A single-module app ships faster; we split into a strict `core`/`feature` graph enforced by convention plugins so four people could build BLE, voice, wallet, and maps in parallel without coupling. *[Read →](https://ridingbluewaves.hashnode.dev/why-a-phone-app-has-nine-modules)*

**Episode 2: A conversation that survives the driver never touching the screen.** A full hands-free loop — listen, transcribe, stream, speak, reopen the mic — modelled as a finite state machine, with barge-in. *[Read →](https://ridingbluewaves.hashnode.dev/a-hands-free-copilot-that-never-needs-the-screen)*

**Episode 3: Why the phone and the Pi never share a secret.** A shared-HMAC pairing turned out to be cryptographically impossible on hardware-backed keys, which pushed us to a proper asymmetric ECDH/ECDSA handshake. *[Read →](https://ridingbluewaves.hashnode.dev/why-the-phone-and-the-pi-never-share-a-secret)*

**Episode 4: Two identities, two key hierarchies, no secrets in the APK.** The device key lives in hardware and can't be exported; the wallet key can't (its algorithm isn't hardware-native) so it's software-wrapped — and we're honest about the cost. No third-party API key ships in the app at all. *[Read →](https://ridingbluewaves.hashnode.dev/two-key-hierarchies-and-no-secrets-in-the-apk)*

**Episode 5: Never drop a token, always deliver the warning.** A moving vehicle loses signal constantly, so the app degrades instead of failing: streamed answers persist token-by-token, and a critical hazard pre-empts whatever the copilot is saying. *[Read →](https://ridingbluewaves.hashnode.dev/never-drop-a-token-always-deliver-the-warning)*

## From demo to production: the honest part

Here is the thing most build write-ups skip. VIGIA Mobile has a genuinely strong **presentation layer and module topology** — Compose, Material 3, MVVM+UDF, Hilt, a clean `core`/`feature` graph with convention plugins. That's the visible 40%. The other 60% — the substrate that only matters under team scale, network adversity, and a release cadence — is where a demo and a production app diverge, and it's worth naming exactly what that substrate is, because it's the same checklist for *any* app you ship:

- **A domain layer.** Business rules (reward math, detection thresholds) belong in testable `UseCase` classes, not inside ViewModels. Google added the domain layer to the guidance for exactly this.
- **A typed error model.** A hardware- and network-dependent app must turn "why did it fail" into UX — a sealed `Result<T>` the UI can render, not a `try/catch` that logs and drops.
- **Offline-first everywhere.** Chat history is Room-backed today; wallet balance, hazards, and rewards are still network-only. Real production means the local DB is the source of truth for all of them, with **WorkManager** doing background sync.
- **Automated testing.** The test pyramid — JVM unit tests on UseCases/ViewModels with fakes over mocks, **Turbine** for Flow assertions, **Roborazzi** screenshot tests — gated in CI. This is the difference between "works on my phone" and "works."
- **CI/CD + static analysis.** GitHub Actions running build + test + lint + **detekt** on every PR, with branch protection. Nothing merges red.
- **Performance engineering.** Baseline Profiles (15–30% faster cold start), Macrobenchmark, Compose-stability audits, R8 in full mode — measured, not guessed.
- **Observability.** Structured logging (Timber) + crash reporting (Crashlytics/Sentry) + an analytics abstraction, so you know when and why production breaks.
- **Security hardening for release.** TLS with certificate pinning, Play Integrity attestation on minting-eligible telemetry, dependency + secret scanning in CI.
- **Release engineering.** Gradle signing with keystore creds from CI secrets, semantic versioning, and a Play publishing pipeline that ships a signed AAB on tag.

We wrote all of this down as a **14-workstream hardening spec** graded against Google's architecture guide and *Now in Android*, sequenced by risk-reduction-per-hour (guardrails → safety net → correctness → structure → resilience → ship). The honest status: the foundation is a solid **B**, and that spec is the ~3–4 week road to production-grade. Naming that gap precisely — rather than claiming the demo is done — is itself part of engineering maturity, and each episode below flags where its subsystem sits on that road.

## The thread running through all five

The same instinct shows up in every decision: **design for the adversarial version of the environment, not the demo version.** Assume the network will drop, the driver's hands are busy, the Bluetooth peer might be lying, and the phone could be lost — then make the architecture degrade gracefully under each. Modularity so the team moves fast without coupling; a voice loop that closes itself so the driver never reaches for the screen; a handshake that trusts no shared secret; keys in hardware wherever the platform allows and honestly accounted for where it doesn't; a data path that treats a lost connection as a normal event.

None of these are exotic alone. The interesting part was deciding, for software that rides in a moving car, where to spend engineering effort on resilience and trust that a stationary app would never need — and being honest about the production work still ahead.

The full app is open at [github.com/VigiaLabs/vigia-android](https://github.com/VigiaLabs/vigia-android).

---

## 🎓 CS Fundamentals — study companion

*This overview frames a mobile client that fuses **OS**, **Computer Networks**, **Security**, and **Software Architecture**, and now doubles as a from-zero map of the Android stack. Read it before mobile/systems interviews; the episodes go deep on each subsystem.*

### System Design (mobile client)
- **Three-tier fusion on one device.** The app sits between a Pi edge node (below, over Bluetooth) and a cloud backend (above, over HTTPS/MQTT), fusing both in real time. The constraint that shapes everything: **the environment is adversarial** — the network drops, hands are busy, the peer might lie, the phone could be lost. Each episode hardens one of those.
- **Client vs server discipline.** Unlike a server, a mobile client must handle intermittent connectivity, constrained battery/CPU, the OS process lifecycle (backgrounding, Doze), and on-device secrets — a distinct engineering discipline.
- **The layered architecture (know this cold).** UI → Domain → Data → Model, with dependencies pointing downward and a repository as the single source of truth. "How would you structure an Android app?" is answered by naming these layers and the rule that the UI never touches the network directly.

### Frameworks & the alternatives (a favourite interview line of questioning)
- **Compose vs Views**: declarative UI = f(state) vs imperative view mutation.
- **Hilt vs Koin**: compile-time DI (missing binding = build error) vs runtime DI.
- **Coroutines/Flow vs RxJava/LiveData**: structured concurrency + reactive streams, Kotlin-native.
- **Room vs SQLDelight/raw SQLite**: compile-checked SQL + Flow queries.
- **OSMDroid vs Google Maps SDK**: zero-cost, no-API-key mapping — a deliberate cost trade-off.
- **MVVM + UDF vs MVC/MVP**: one place state mutates; traceable and testable.

**Interview Q&A.**
1. *Describe Android's recommended architecture.* → UI (Compose+ViewModel+UiState), optional Domain (UseCases), Data (Repository + local/remote sources), Model; UDF, offline-first, Flow everywhere.
2. *Why Compose over XML Views?* → Declarative state-driven UI; less boilerplate; recomposition instead of manual view mutation.
3. *Compile-time vs runtime DI — why does it matter?* → Compile-time (Hilt) turns a missing/incorrect binding into a build failure instead of a crash.
4. *What separates a demo app from a production app?* → The substrate: domain layer, typed errors, offline-first, automated tests, CI/CD, observability, security hardening, release pipeline.

### ⚖️ This vs That — the guiding principle
| Decision | Alternatives | Why this choice |
|---|---|---|
| **Design for the adversarial environment** | Design for the demo (good network, hands free, honest peer) | A driving copilot lives in the worst case: dropped signal, eyes on the road, untrusted hardware. Building for the happy path fails exactly when it matters. |
| **Modern Jetpack stack (Compose/Hilt/Room/Coroutines)** | Legacy Views/manual DI/SQLite/RxJava | The Jetpack stack is Google's recommended, testable, offline-first-ready path; legacy choices cost you tooling, safety, and hiring familiarity. |
| **Name the demo→production gap explicitly** | Claim the demo is production-ready | A hardening spec graded against Google/NIA is more credible — and more useful — than overclaiming a finished product. |

**The one to defend:** *build for the failure modes, not the demo — and be honest about the road to production.* Every subsystem is one instance of assuming the hostile case and degrading gracefully, and the hardening spec is the disciplined plan to close the rest.

---

> 📚 **Prepping for placements or interviews?** This series doubles as a study track. The [**VIGIA Interview Prep Companion**](https://ridingbluewaves.hashnode.dev/the-vigia-interview-prep-companion) ties every post to coding-round patterns, system-design drills, and behavioral (STAR) answers — a full Microsoft/Amazon prep guide grounded in these projects.
