# Episode 1: Why a phone app has nine modules

*The strict core/feature module graph, the convention plugins that enforce it, and why we paid the modularity tax on a project four people had to build at once.*

The fastest way to ship an Android app is one module: everything in `:app`, one `build.gradle`, no ceremony. VIGIA Mobile is instead ten modules wired together by a set of custom Gradle convention plugins. That is a real cost — more build files, more boundaries, a steeper first day for anyone new. This post is about why we paid it anyway.

This is Episode 1 of 5 in the Engineering VIGIA Mobile series. The full system overview is in the master post.

## The problem a single module creates

The app does four largely independent things: it drives a Bluetooth link to a Raspberry Pi, it runs a voice copilot, it manages a crypto wallet, and it renders maps. Four people built those four things, mostly in parallel, on a hackathon clock.

In a single module, that arrangement rots fast. Everything can import everything, so within a week the wallet code is reaching into a voice class for a coroutine scope, the maps screen is reading a BLE field directly, and the "modules" exist only as folders that any file can ignore. Merge conflicts pile up because everyone edits the same Gradle file and the same dependency list. And the compiler rebuilds the world on every change, because it has no smaller unit to reason about.

We wanted the opposite: hard walls that the build system enforces, so that a mistake in one person's area cannot silently entangle another's.

## The shape: core is shared, feature is a leaf

The graph is deliberately one-directional. There are three `feature` modules — `copilot`, `maps`, `pairing` — and six shared `core` modules — `model`, `network`, `sensor`, `data`, `auth`, `wallet`. The single rule is: **feature modules depend only on `core`, never on each other, and `core` never depends on a feature.** `:app` is a thin shell at the top that wires everything together with dependency injection and nothing else.

That rule is what makes parallel work safe. The person building the maps feature cannot accidentally couple it to the copilot, because the maps module physically cannot see the copilot module — the dependency isn't declared, so the symbol doesn't exist. Shared concerns live in `core` (the BLE manager, the network clients, the wallet), where they are built once and consumed through typed interfaces. A feature is a leaf: it composes core capabilities into a screen and a view-model, and that is all.

## Why not just agree to be disciplined

The obvious objection is that you don't need modules for this — you need discipline. Put the layers in packages and agree not to cross them.

We tried thinking that through and it does not survive contact with a deadline. A package boundary is a naming convention; the compiler does not care if you cross it. Under time pressure, "just import it for now" always wins, and by the time you notice, the graph is a ball of mutual references that no refactor can cheaply undo. A module boundary is different: crossing it requires declaring a dependency in a build file, which is a visible, reviewable act. The wall is enforced by the tool, not by everyone's good intentions at 2am.

## The convention plugins are the point, not the modules

Ten modules would be miserable if each carried its own copy of the Android and Kotlin configuration. The moment the SDK version or the Compose compiler or the Hilt setup drifted between modules, you would get the subtle, maddening build failures that come from inconsistent configuration.

So the real investment was a small set of **convention plugins** in a `build-logic` module — one each for an application module, a library module, a Compose module, a feature module, and Hilt. Every module applies a convention (`AndroidFeatureConventionPlugin`, `AndroidLibraryComposeConventionPlugin`, and so on) instead of hand-writing its configuration. `compileSdk`, `minSdk`, the Compose setup, the Kotlin options, the Hilt wiring — all of it is defined once and inherited everywhere. A feature module's build file becomes three lines: apply the feature convention, and declare which core modules it needs.

That is what makes the module count sustainable rather than a tax that compounds. Adding a module is cheap because the configuration is not copied, it is applied. Changing the SDK level is one edit, not ten.

## Takeaway

The modules were never about tidiness for its own sake. They were about letting four people build BLE, voice, wallet, and maps at the same time without their code silently growing into each other, and about having the build system — not a code-review norm — be the thing that enforces the boundary. The convention plugins are what kept that structure from becoming its own maintenance burden: define the platform once, apply it everywhere, and let each feature stay a thin leaf over a shared core.

The code is open at [github.com/VigiaLabs/vigia-android](https://github.com/VigiaLabs/vigia-android).

*Engineering VIGIA Mobile · Episode 1 of 5 — Next: Episode 2, A conversation that survives the driver never touching the screen.*
