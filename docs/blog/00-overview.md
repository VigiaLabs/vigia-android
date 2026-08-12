# Engineering the VIGIA copilot: what an advanced Android prototype still needs before production

*A from-zero tour of an in-vehicle Android copilot, followed by the less glamorous engineering review:
which paths work, which are only partially wired, which design claims the code does not yet earn, and
how to reason about those gaps in a production or FAANG-style interview.*

Most "AI assistant" apps assume a user sitting still, looking at a screen, on good Wi-Fi, with both hands free. A driver has none of that. They are moving, their eyes belong on the road, connectivity drops in exactly the places where hazards are worst, and the useful information — a pothole, a stray animal, a road's safety rating — is about the few hundred metres directly ahead. VIGIA Mobile is our attempt to build a copilot for that person.

This overview does two jobs. First, it's the **map of the series** — five deep dives, each on one hard decision. Second, and new in this rewrite, it's a **from-zero tour of the whole Android stack**: what every layer is, which framework we picked, what we picked it *over*, and what it takes to move an app like this from a hackathon demo to something you could actually ship. If you are studying for an Android or systems interview, read this end to end. The code is open at [github.com/VigiaLabs/vigia-android](https://github.com/VigiaLabs/vigia-android).

## What the app does

VIGIA Mobile is the Android companion to the VIGIA road-intelligence system. The prototype contains
Bluetooth pairing and telemetry code for a Raspberry Pi edge node, a voice loop, streamed VIGIASearch
answers, MQTT/FCM alert receivers, maps, authentication, and a rewards-wallet/payout surface. That list
describes **capabilities present in the repository**, not an end-to-end production guarantee. The audit
behind this rewrite found important paths that are partial, disconnected, or unsafe under failure; those
are called out below rather than being hidden behind the architecture diagram.

The shape is **three tiers meeting in one app**. Below it, a Pi edge node feeds telemetry over an authenticated Bluetooth link. Above it, a cloud backend answers queries and pushes alerts. In the middle, the app fuses the two — the driver's spoken question is enriched with the vehicle's real GPS, speed, and road-roughness reading before it ever reaches the cloud, so every answer is grounded in where the car actually is.

## Starting from zero: what an Android app *is* made of

Before the specific decisions, here is the ground floor, because "app development from zero" means knowing what each piece does and why it exists.

An Android app is Kotlin (or Java) code compiled to run on the Android Runtime (ART), packaged as an APK/AAB, built by **Gradle**. Every serious app is a stack of layers, and for each layer there's a dominant framework and a set of alternatives. Here's the whole stack VIGIA uses, and the reasoning:

**Language — Kotlin (over Java).** Kotlin is Google's recommended language for Android: null-safety in
the type system, coroutines for asynchronous work, concise data classes, and sealed types for exhaustive
state. Java remains supported. The project currently uses Kotlin `2.0.21` and KSP for Hilt/Room code
generation. Versions are an audited input, not an architectural achievement: this stack has drifted and
must be upgraded in small compatibility-tested changes.

**UI toolkit — Jetpack Compose + Material 3 (over XML Views).** The old model was XML layouts inflated into a `View` tree and mutated imperatively (`findViewById`, `textView.setText(...)`). Compose is *declarative*: you write functions that describe the UI as a function of state, and the framework recomposes when state changes. We chose it because a driving UI is highly stateful (listening → thinking → speaking) and declarative UI makes "UI = f(state)" literal. The trade-off: Compose has a learning curve around recomposition and stability, and a slightly heavier cold start (which the baseline-profile work in our hardening plan addresses). Material 3 gives us the design system; **Haze** adds the backdrop-blur "liquid glass" surfaces.

**Architecture pattern — MVVM + Unidirectional Data Flow (over MVC/MVP).** State flows **down** from a `ViewModel` to Composables as an immutable `UiState`; events flow **up** as function calls. This is Google's officially recommended pattern and the one the *Now in Android* reference app uses. We picked it over MVC/MVP because UDF makes state changes traceable and testable — there's exactly one place state mutates.

**Dependency injection — Hilt (over Koin or manual DI).** Hilt is Google's DI framework built on Dagger;
it generates most wiring at compile time, so many missing bindings become build errors. The trade-off is
generated-code/build complexity and the temptation to hide an over-large object graph behind injection.
That temptation is visible here: `CopilotViewModel` has too many collaborators. DI can construct a design;
it cannot make the design cohesive.

**Asynchrony — Kotlin Coroutines + Flow (over RxJava/callbacks/LiveData).** Every network call, BLE read, and sensor stream is async. Coroutines give us `suspend` functions that read like straight-line code, structured concurrency (child jobs cancel with their parent), and `Flow`/`StateFlow` for reactive streams. We chose it over **RxJava** (powerful but a heavy operator vocabulary) and over `LiveData` (Android-only, lifecycle-bound but less composable) because coroutines are the modern standard and `StateFlow` is the natural driver of a Compose `UiState`.

**Local persistence — Room + DataStore (over raw SQLite / SharedPreferences).** **Room** is an ORM over SQLite with compile-time-checked SQL and `Flow`-returning queries; we use it for chat history and harsh-driving events. **DataStore** replaces the old `SharedPreferences` for small key/values (it's coroutine/Flow-based and transactional). We chose Room over **SQLDelight** mostly for Jetpack cohesion and the `Flow` integration.

**Networking — OkHttp + Retrofit, plus raw OkHttp for streaming (over Ktor/Volley).** Retrofit turns a REST API into a typed Kotlin interface; OkHttp is the HTTP engine underneath. For the copilot's token-by-token answer we drop to raw OkHttp to consume a **Server-Sent Events** stream. We chose this stack over **Ktor client** (fine, but Retrofit/OkHttp is the most battle-tested on Android) and over the ancient **Volley**.

**Maps — currently OSMDroid.** Avoiding a billed maps SDK made sense for a prototype, but the production
conclusion changed: OSMDroid was archived in November 2024, and the public OpenStreetMap standard tile
server has no SLA and is not free production infrastructure. The correct decision is now an ADR and
proof-of-concept for a maintained renderer plus a contracted or responsibly operated tile source,
including attribution, privacy, cache/offline terms, quotas and failure UX.

**Messaging — Eclipse Paho MQTT v3 + Firebase Cloud Messaging.** Two ingress paths can improve
availability, but two transports do not automatically provide reliable delivery. The current MQTT path
lacks normal AWS IoT client authentication, and the FCM receiver writes only to an in-memory flow. The
production design must validate and transactionally persist a stable event ID, deduplicate MQTT/FCM
copies, post a user-visible notification, recover sequence gaps, and measure ingress-to-visible latency.

**Auth — AWS Amplify/Cognito.** Choosing the backend-aligned identity provider is reasonable. The current
production binding is not: if Amplify configuration fails, dependency injection selects the simulated
demo repository. Production must contain no reachable demo-auth fallback. Missing configuration should
produce a blocking, observable state, with exact OAuth/App Link redirect validation and tested token refresh.

**Camera + scanning — CameraX + ML Kit barcode (over Camera2 directly).** CameraX is the lifecycle-aware wrapper over the low-level Camera2 API; ML Kit's bundled barcode scanner reads the pairing QR with no Play Services dependency.

**Build system — Gradle with a version catalog + convention plugins.** All versions live in one `libs.versions.toml`; shared build config lives in **convention plugins** under `build-logic/` so ten modules don't copy-paste their setup. This is the *Now in Android* pattern (Episode 1).

## The layered architecture, the way Google draws it

Those frameworks are organized into layers, and the direction of dependencies is the whole game:

```
UI layer      :feature:copilot / :maps / :pairing
              Composable ↔ ViewModel ↔ UiState (StateFlow)      ← state down, events up
Workflow/policy (optional use cases for complex/reused logic)    ← introduced where it earns its cost
Data layer    :core:network / :sensor / :wallet / :auth / :data
              Repository (interface) → RepositoryImpl           ← single source of truth
              ├─ remote (OkHttp / MQTT / BLE / RPC)
              └─ local  (Room / DataStore / Keystore)
Model         :core:model  — pure Kotlin data classes
```

That is the target, not the current graph. Today `feature:copilot` directly depends on both
`feature:maps` and `feature:pairing`, and the broad `core:sensor` module owns device link, context,
notifications and network-adjacent work. There are ten application modules, but module count is not a
quality metric. The rule we actually want is: features do not depend on another feature's implementation;
shared behaviour gets an intentionally owned API; the app composes navigation; complex/reused policy is
extracted and tested. Android's domain layer is optional, so one-line use-case wrappers are not the goal.

A repository is also not always the authority. Room can be the source of truth for a durable local read
model, while the server remains authoritative for ownership and money and the physical device remains
authoritative for current link state. Production design starts by naming that authority and the cache's
freshness, not by repeating “single source of truth” mechanically.

## The five decisions worth reading about

Each episode is a standalone deep dive on one choice where we picked the harder path for a reason:

**Episode 1: Why a phone app has modules.** A single-module app ships faster; we built a ten-module
`core`/`feature` graph with convention plugins. The revised episode also examines the two feature-to-feature
edges that violate the intended leaf rule and explains when modularisation becomes cargo cult. *[Read →](https://ridingbluewaves.hashnode.dev/why-a-phone-app-has-nine-modules)*

**Episode 2: A conversation that survives the driver never touching the screen.** A full hands-free loop — listen, transcribe, stream, speak, reopen the mic — modelled as a finite state machine, with barge-in. *[Read →](https://ridingbluewaves.hashnode.dev/a-hands-free-copilot-that-never-needs-the-screen)*

**Episode 3: Why the phone and the Pi should not share a long-lived secret.** It explains the target
ECDH/signature protocol, then separates that protocol from the current implementation gaps: empty claim
signature, weak QR-to-peer binding, unbounded BLE operations, and a server-claim fail-open. *[Read →](https://ridingbluewaves.hashnode.dev/why-the-phone-and-the-pi-never-share-a-secret)*

**Episode 4: Two identities, two key hierarchies.** The current Ed25519 wallet key is generated in
software, encrypted by a Keystore AES key at rest, and reconstructed in app memory for signing. That is
a wrapped software key—not a non-exportable TEE key. *[Read →](https://ridingbluewaves.hashnode.dev/two-key-hierarchies-and-no-secrets-in-the-apk)*

**Episode 5: From best-effort alerts to a durable inbox.** Chat persistence is a useful start. Hazard
delivery is currently best-effort and process-local; the revised episode derives the required Room,
dedupe, notification, WorkManager gap-sync and SLO design. *[Read →](https://ridingbluewaves.hashnode.dev/never-drop-a-token-always-deliver-the-warning)*

## From demo to production: the honest part

Here is the thing most build write-ups skip: a production review is not a checklist of fashionable
libraries. The app has good Compose/UDF foundations, convention plugins, repositories and partial Room
persistence. It is still an **advanced prototype, not a release candidate**, because correctness under
failure matters more than the shape of the clean path.

The audit found these release blockers:

1. **Authentication fails open.** An Amplify configuration failure selects simulated demo auth in prod.
2. **Ownership fails open.** Pairing submits an empty device signature and treats broad claim errors as
   permission to continue locally.
3. **BLE lifecycle is not bounded.** Scan/bond can suspend forever; a swallowed connection error defeats
   the foreground-service retry; the service-start helper has no caller.
4. **Unknown context becomes fabricated data.** Missing location/sensors are seeded as zero values, so a
   query can appear to originate at latitude/longitude `0,0`.
5. **Payment success is named too early.** Receiving a Stripe client secret is intent creation, not a
   settled charge. The payment sheet/status path is not fully wired.
6. **Alerts are process-local.** FCM writes to an in-memory `SharedFlow`, with no durable inbox or immediate
   system notification; an FCM-started process can exit and lose it.
7. **Updates and backups risk user data.** Room uses destructive migration fallback, while Android backup
   is enabled with template rules that do not explicitly exclude wallet/pairing/session/database state.
8. **Cloud/platform assumptions are incomplete.** MQTT lacks normal AWS IoT client auth; OSMDroid is
   archived; standard OSM tiles have no production SLA; production lint currently fails.

The order of work therefore changes. First freeze incomplete payout and make identity/configuration fail
closed. Then repair device ownership/BLE, context truth, alert durability, database migration and backup.
CI, tests, observability, dependency verification and release signing are built in parallel so those
correctness properties remain enforced. Only after that do module extraction, broad offline caching,
performance profiles and UI polish become the highest-value work.

Some common “production” advice is deliberately conditional:

- A **domain layer is optional**. Extract pairing, search and payout workflows because they are complex,
  not because every repository call needs a one-line `UseCase`.
- A local database is a durable read model, but the **server remains authoritative** for ownership and
  money. Cached balance needs a timestamp and cannot authorise cash-out.
- **WorkManager is durable deferrable work**, not an exact scheduler and not the place to delay an
  immediate FCM notification.
- **Certificate pinning is a threat-model decision**, not a maturity badge. Without backup pins,
  overlapping rotation, expiry, telemetry and recovery it can turn certificate rotation into a lockout.
- Coverage percentage is a diagnostic. Transition, migration, protocol, idempotency and negative-path
  tests provide stronger evidence for these critical workflows.

The expanded hardening specification now has explicit stop-ship invariants, transition models, ownership,
money and alert-delivery contracts, privacy/backup rules, SLOs, release evidence and interview prompts.
Its schedule is measured in staged multi-week phases with backend/firmware/device dependencies—not an
unsupported “three to four weeks to production” promise.

## The thread running through all five

The same instinct should show up in every decision: **design for the adversarial environment, not the demo.**
Assume the network drops, hands are busy, a Bluetooth peer/QR may lie, and the phone may be lost. Then test
the actual implementation: visible module edges rather than “no coupling”; policy-controlled voice resume
rather than “the mic always returns”; authenticated ephemeral key agreement rather than “no secret exists”;
measured Keystore protection rather than “hardware everywhere”; durable alert inbox rather than “two paths.”

None of these are exotic alone. The interesting part was deciding, for software that rides in a moving car, where to spend engineering effort on resilience and trust that a stationary app would never need — and being honest about the production work still ahead.

The full app is open at [github.com/VigiaLabs/vigia-android](https://github.com/VigiaLabs/vigia-android).

---

## 🎓 CS Fundamentals — study companion

*This overview frames a mobile client that fuses **OS**, **Computer Networks**, **Security**, and **Software Architecture**, and now doubles as a from-zero map of the Android stack. Read it before mobile/systems interviews; the episodes go deep on each subsystem.*

### System Design (mobile client)
- **Three-tier fusion on one device.** The app sits between a Pi edge node (below, over Bluetooth) and a cloud backend (above, over HTTPS/MQTT), fusing both in real time. The constraint that shapes everything: **the environment is adversarial** — the network drops, hands are busy, the peer might lie, the phone could be lost. Each episode hardens one of those.
- **Client vs server discipline.** Unlike a server, a mobile client must handle intermittent connectivity, constrained battery/CPU, the OS process lifecycle (backgrounding, Doze), and on-device secrets — a distinct engineering discipline.
- **The layered architecture (and its caveat).** UI → optional workflow/domain policy → data boundaries →
  model. The interview answer is not the diagram alone: identify authority, durable state, lifecycle,
  cancellation and failure semantics. Domain is optional and repositories do not magically make cached
  money or physical link state authoritative.

### Frameworks & the alternatives (a favourite interview line of questioning)
- **Compose vs Views**: declarative UI = f(state) vs imperative view mutation.
- **Hilt vs Koin**: compile-time DI (missing binding = build error) vs runtime DI.
- **Coroutines/Flow vs RxJava/LiveData**: structured concurrency + reactive streams, Kotlin-native.
- **Room vs SQLDelight/raw SQLite**: compile-checked SQL + Flow queries.
- **Map renderer/provider choice**: licence, maintenance, SDK/API key, privacy, offline rights, attribution,
  quotas, SLA and migration—not “public tiles are free.”
- **MVVM + UDF vs MVC/MVP**: one place state mutates; traceable and testable.

**Interview Q&A.**
1. *Describe Android's recommended architecture.* → UI (Compose+ViewModel+UiState), optional Domain (UseCases), Data (Repository + local/remote sources), Model; UDF, offline-first, Flow everywhere.
2. *Why Compose over XML Views?* → Declarative state-driven UI; less boilerplate; recomposition instead of manual view mutation.
3. *Compile-time vs runtime DI — why does it matter?* → Compile-time (Hilt) turns a missing/incorrect binding into a build failure instead of a crash.
4. *What separates a demo app from a production app?* → Proven invariants under failure: fail-closed
   identity/ownership, bounded state machines, durable/idempotent work, migrations/backups, privacy,
   SLOs/runbooks, tested signed releases, staged rollout and rollback. Framework names are secondary.
5. *Why is process death different from configuration change?* → A ViewModel commonly survives only the
   latter. Anything needed after process death—pending claim, alert, payout ID, partial operation—needs a
   durable checkpoint and an idempotent resume policy.
6. *StateFlow vs SharedFlow vs a database?* → `StateFlow` holds current in-process state; `SharedFlow`
   broadcasts in-process events with configurable replay; neither is durable. Room persists across process
   death/reboot and can expose observable queries.
7. *Why can retry be dangerous?* → A timeout is ambiguous: the server may have committed. Retry only an
   idempotent operation with a stable key, deadline, exponential backoff and jitter; do not retry semantic
   rejection such as bad signature/unauthorised.
8. *How would you measure this app?* → Pairing-stage success/latency, BLE-ready duration, hazard
   ingress-to-visible P50/P95/P99 and duplicates, SSE first-token/completion, crash/ANR-free sessions,
   migration failures and payout reconciliation—not just average request latency.

### ⚖️ This vs That — the guiding principle
| Decision | Alternatives | Why this choice |
|---|---|---|
| **Design for the adversarial environment** | Design for the demo (good network, hands free, honest peer) | A driving copilot lives in the worst case: dropped signal, eyes on the road, untrusted hardware. Building for the happy path fails exactly when it matters. |
| **Modern Jetpack stack (Compose/Hilt/Room/Coroutines)** | Legacy Views/manual DI/SQLite/RxJava | The Jetpack stack is Google's recommended, testable, offline-first-ready path; legacy choices cost you tooling, safety, and hiring familiarity. |
| **Name the demo→production gap explicitly** | Claim the demo is production-ready | A hardening spec graded against Google/NIA is more credible — and more useful — than overclaiming a finished product. |

**The one to defend:** *build for the failure modes, not the demo — and be honest about the road to production.* Every subsystem is one instance of assuming the hostile case and degrading gracefully, and the hardening spec is the disciplined plan to close the rest.

---

> 📚 **Prepping for placements or interviews?** This series doubles as a study track. The [**VIGIA Interview Prep Companion**](https://ridingbluewaves.hashnode.dev/the-vigia-interview-prep-companion) ties every post to coding-round patterns, system-design drills, and behavioral (STAR) answers — a full Microsoft/Amazon prep guide grounded in these projects.
