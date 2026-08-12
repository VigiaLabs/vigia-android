# Episode 1: Why this phone app has ten modules—and where those boundaries currently leak

*The strict core/feature module graph, the Gradle convention plugins that enforce it, the version catalog that keeps ten modules aligned, and why we paid the modularity tax on a project four people had to build at once.*

The fastest way to ship an Android app is one module: everything in `:app`, one `build.gradle`, no ceremony. VIGIA Mobile is instead ten Gradle modules wired together by a set of custom convention plugins. That is a real cost — more build files, more boundaries, a steeper first day. This post is about why we paid it anyway, and it doubles as a **from-zero explanation of how Gradle, modules, KSP, and convention plugins actually fit together** — the machinery behind every large Android codebase.

This is Episode 1 of 5 in the Engineering VIGIA Mobile series. The full system overview is in the [master post](https://ridingbluewaves.hashnode.dev/engineering-the-vigia-in-vehicle-ai-copilot).

## First, what a "module" even is

An Android project is built by **Gradle**. A *module* is a unit Gradle compiles independently — its own `build.gradle.kts`, its own dependencies, its own compiled output (`.jar`/`.aar`). A single-module app has one: `:app`. A multi-module app has many, connected by declared dependencies (`implementation(project(":core:network"))`).

Three module types matter here:
- **Application module** (`:app`) — produces the installable APK/AAB. There's exactly one.
- **Android library modules** (`:core:*`, `:feature:*`) — produce an `.aar`, contain Android code (Compose, Context), can't run on their own.
- **Pure JVM/Kotlin modules** — plain Kotlin, no Android framework, the fastest to compile and unit-test. Google's guidance (and our hardening plan) pushes model and domain logic *into* these so it's testable with plain JUnit.

## The problem a single module creates

The app does four largely independent things: it drives a Bluetooth link to a Raspberry Pi, runs a voice copilot, manages a crypto wallet, and renders maps. Four people built those four things, mostly in parallel, on a hackathon clock.

In a single module that arrangement rots fast. Everything can import everything, so within a week the wallet code is reaching into a voice class for a coroutine scope, the maps screen is reading a BLE field directly, and the "modules" exist only as folders any file can ignore. Merge conflicts pile up because everyone edits the same Gradle file and dependency list. And the compiler rebuilds the world on every change, because it has no smaller unit to reason about.

We wanted the opposite: **hard walls the build system enforces**, so a mistake in one person's area can't silently entangle another's.

## The intended shape: core is shared, feature is a leaf

The dependency graph is deliberately one-directional. There are three `feature` modules — `copilot`, `maps`, `pairing` — and six shared `core` modules — `model`, `network`, `sensor`, `data`, `auth`, `wallet`. `:app` is a thin shell at the top that wires everything together with dependency injection and nothing else. The single rule:

> **Target rule: a feature does not depend on another feature's implementation; `core` never depends on a feature.**

That rule would make parallel work safer, but the repository does not fully enforce it yet.
`feature:copilot` directly depends on `feature:maps` and `feature:pairing`. The graph is still acyclic,
so Gradle can build it, but “acyclic” and “well bounded” are different properties: the copilot now knows
feature implementations and becomes a de facto super-feature. `core:sensor` is also broad—it depends on
network, wallet and data while owning BLE, presence, context and alert delivery.

This is a useful architecture lesson precisely because the diagram is imperfect. A module edge is
visible, but the build system does not decide whether it is a good edge. CI needs an explicit graph rule,
and shared navigation/UI contracts need a deliberate owner instead of pushing every reusable thing into
a miscellaneous `core` bucket.

Here's the actual graph:

```
:app ──▶ :feature:copilot ──▶ :feature:maps
  │                └───────▶ :feature:pairing
  ├────▶ :feature:maps
  └────▶ :feature:pairing

:feature:* ──▶ selected :core:* modules
:core:sensor ──▶ :core:model + :core:network + :core:wallet + :core:data
:core:network/:wallet/:data ──▶ :core:model
```

Notice it's a **DAG** — a directed graph with no cycles. That's not an aesthetic preference: an acyclic graph has a *topological order*, so Gradle can build modules in dependency order and rebuild only what changed downstream. A cycle (`A → B → A`) would force both to always recompile together and make neither reason-about-able in isolation.

## Why not just "agree to be disciplined"

The obvious objection: you don't need modules for this, you need discipline. Put the layers in packages and agree not to cross them.

It doesn't survive contact with a deadline. A **package boundary is a naming convention** — the compiler doesn't care if you cross it. Under time pressure, "just import it for now" always wins, and by the time you notice, the graph is a ball of mutual references no refactor can cheaply undo. A **module boundary is different**: crossing it requires declaring a dependency in a build file, which is a visible, reviewable act in a diff. The wall is enforced by the tool, not by everyone's good intentions at 2am. (In the hardening plan we go further and assert the graph in CI with a module-graph check, so an illegal edge fails the build.)

## The convention plugins are the point, not the modules

Ten modules would be miserable if each carried its own copy of the Android and Kotlin configuration — the `compileSdk`, the Compose setup, the Hilt wiring. The moment those drift between modules, you get the subtle, maddening build failures that come from inconsistent configuration.

So the real investment was a small set of **convention plugins** in a `build-logic` module. If you've seen the old `buildSrc` approach, this is its successor (the *Now in Android* pattern): `build-logic` is an *included build* that compiles a handful of Gradle plugins, and every module applies one instead of hand-writing its config. Ours are:

- `vigia.android.application` / `vigia.android.application.compose` — the `:app` module.
- `vigia.android.library` / `vigia.android.library.compose` — `core`/`feature` library modules.
- `vigia.android.feature` — the feature-module bundle (Compose + Hilt + lifecycle + the core deps a feature always needs).
- `vigia.android.hilt` — the Hilt/KSP setup.

A feature module's entire build file collapses to a few lines: apply the feature convention, then declare which `core` modules it needs. `compileSdk`, `minSdk`, the Compose compiler, Kotlin options, and Hilt are all defined once and inherited. Adding a module is cheap because the configuration is *applied, not copied*; changing the SDK level is one edit, not ten.

**Why included `build-logic` over `buildSrc`:** it is an explicit composite build, can be structured and
tested like other build code, and gives better control over dependencies/cache boundaries. Either approach
still sits on a critical build path; “included build” does not make careless convention-plugin changes free.

## The two more pieces of the machinery

To make sense of the build files, two more from-zero concepts:

**The version catalog (`gradle/libs.versions.toml`).** Every dependency version lives in one TOML file — Compose BOM, Hilt, Room, OkHttp, coroutines, and so on — referenced as `libs.hilt.android`. One source of truth means no two modules can drift onto different Room versions, and a bump is a one-line change. We even declare *bundles* (`libs.bundles.networking` = OkHttp + Retrofit + converters) so a module pulls a related set in one line.

**KSP (Kotlin Symbol Processing).** Hilt and Room generate code from annotations at compile time. KSP
lets processors work with Kotlin symbols without kapt's Java-stub model and generally improves Kotlin
tooling/incrementality. Actual build impact must be measured in this repository rather than quoted as a
universal multiplier.

**Dependency injection across modules — the reason this all holds together.** Hilt is what lets a `:feature:copilot` ViewModel ask for a `WalletRepository` interface (declared in `:core:wallet`) and receive the concrete `WalletRepositoryImpl` at runtime, without the feature ever importing the implementation. Interfaces live in `core`, implementations are `@Binds`-bound in a Hilt module, and features depend on the *abstraction*. That's how you get low coupling *and* a working object graph: the module graph enforces the boundary, and DI stitches across it.

## Takeaway

The modules were never about tidiness for its own sake. They were intended to let BLE, voice, wallet and
maps evolve with visible dependencies, while convention plugins prevented build configuration drift.
The audit adds the important second half: **modules make an edge visible; tests, ownership and graph rules
make it good**. This app has ten useful compilation units, two feature-to-feature leaks and one over-broad
sensor integration module. The production move is not “add four more modules.” It is to extract cohesive,
tested workflows, remove the illegal edges, then promote a package into a module only when independent
ownership, reuse, enforcement or build isolation earns the cost.

The code is open at [github.com/VigiaLabs/vigia-android](https://github.com/VigiaLabs/vigia-android).

*Engineering VIGIA Mobile · Episode 1 of 5 — Next: Episode 2, A conversation that survives the driver never touching the screen.*

---

## 🎓 CS Fundamentals — study companion

*This is the **Software Architecture** episode — modularity, coupling & cohesion, dependency graphs, build systems — with an **OS** note on compilation/linking. "How do you structure a large codebase?" is a staple system-design question, and "how does the Android build work?" is a common Android-specific one.*

### Software Architecture & Engineering
- **Coupling vs cohesion.** Good architecture seeks **low coupling** and **high cohesion**. Module names do
  not maximise either automatically; the current feature edges and broad sensor module are counterexamples.
- **The dependency graph is a DAG, but not the target DAG.** Acyclic means a topological build order; it
  does not imply low coupling. The current feature-to-feature edges are legal to Gradle and undesirable
  to the architecture.
- **Compile-time enforcement > convention.** A package boundary is a name the compiler ignores; a *module* boundary requires a declared dependency to cross. Enforced by the tool, reviewable in a diff — and assertable in CI.
- **Incremental builds.** Modules give the build a smaller unit to recompile. Change one feature → only it and its dependents rebuild. This is why big codebases modularise — build times.
- **DRY build config.** `build-logic` convention plugins define the platform (SDK, Compose, Hilt) once and apply everywhere. `build-logic` over `buildSrc` because the latter invalidates the whole build cache on any change.
- **Dependency Injection (Hilt).** Interfaces in `core`, implementations `@Binds`-bound, features depend on abstractions. Compile-time DI = a missing binding is a build error. DI stitches across module boundaries the graph enforces.

### Android build fundamentals (name-drop these)
- **Gradle module** → independent compilation unit (`.aar`/`.jar`). **KSP** → fast annotation processing (replaced kapt). **Version catalog** → single source of truth for versions. **BOM** → aligned Compose versions.

**Interview Q&A.**
1. *Coupling vs cohesion — what do you want?* → Low coupling, high cohesion.
2. *Why must a module graph be acyclic?* → A DAG has a topological build order and clearer change impact.
   But challenge the premise: Gradle module graphs are generally constrained to be acyclic already, and
   an acyclic graph can still be badly coupled. Discuss fan-in/fan-out, ownership and API stability too.
3. *Package boundary vs module boundary — which is stronger?* → Module: crossing needs a declared dependency the build enforces; a package is just a name.
4. *Why multi-module?* → Independent ownership/API enforcement/reuse and sometimes build isolation.
   For a small app, module configuration and cross-module changes may cost more than they save; measure.
5. *What does Hilt/DI solve?* → Decouples construction from use; enables testing, swapping implementations, and wiring across module boundaries.
6. *buildSrc vs build-logic convention plugins?* → build-logic is an included build that doesn't invalidate the whole cache and lets modules apply shared config as plugins.

### ⚖️ This vs That — the architecture decisions, and the roads not taken

| Decision | Alternatives | Why this choice |
|---|---|---|
| **Multi-module (`core`/`feature`)** | Single-module app with packages | One module lets any file import any other; boundaries rot under deadline and builds recompile everything. Modules enforce the wall and speed the build. |
| **Compile-enforced boundary** | "Agree to be disciplined" with package conventions | Discipline fails at 2am; a declared-dependency wall is enforced by the tool, reviewable in a diff, assertable in CI. |
| **Convention plugins in `build-logic`** | `buildSrc`; or copy config into each module | Copied config drifts; `buildSrc` nukes the whole build cache on any change. Define-once-apply-everywhere keeps 10 modules aligned. |
| **Version catalog + BOM** | Hard-coded versions per module | Independent version strings drift; one catalog is a single source of truth and a one-line bump. |
| **Target: no feature-implementation dependency** | Current `copilot → maps/pairing` edges | Feature edges do not automatically create cycles, but they couple ownership and change impact. Compose navigation at `:app`; move only genuinely shared contracts/components to an owned core API. |

**The one to defend:** *enforced module boundary plus an explicit graph policy.* The mature answer is not
that humans disappear: modules turn an import into a visible dependency declaration, while CI and review
decide whether that declaration is allowed. The current leaks are proof that visibility alone is not enforcement.

---

> 📚 **Prepping for placements or interviews?** This series doubles as a study track. The [**VIGIA Interview Prep Companion**](https://ridingbluewaves.hashnode.dev/the-vigia-interview-prep-companion) ties every post to coding-round patterns, system-design drills, and behavioral (STAR) answers — a full Microsoft/Amazon prep guide grounded in these projects.
