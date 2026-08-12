# Episode 2: A conversation that survives the driver never touching the screen

*The hands-free voice loop — voice-activity detection, streaming transcription, spoken answers, and barge-in — built on Compose, MVVM, and Kotlin coroutines, with a from-zero look at the reactive-UI and async machinery that makes it hold together.*

The core constraint of an in-vehicle copilot is simple to state and hard to honour: the driver cannot touch the screen. Not to start talking, not to stop the AI, not to ask a follow-up. Every interaction that requires a tap either doesn't happen or happens with the driver's eyes off the road. So the design target was a full spoken conversation where the phone sits in a mount, untouched, for the entire drive. This post is about the loop that makes that work — and, from zero, about the **reactive UI + async stack** that a stateful, real-time Android feature is built on.

This is Episode 2 of 5 in the Engineering VIGIA Mobile series. The full system overview is in the [master post](https://ridingbluewaves.hashnode.dev/engineering-the-vigia-in-vehicle-ai-copilot).

## The loop, end to end

A single turn runs through six stages, and the driver initiates none of them by hand:

1. **Voice-activity detection (VAD)** listens continuously and decides when the driver has actually started and finished an utterance.
2. On end-of-utterance, the captured audio goes to **speech-to-text** (Sarvam's Indian-language STT).
3. The transcript, enriched with live vehicle context, is sent to the **VIGIASearch backend** as a Server-Sent Events stream.
4. As the answer streams, each reasoning step and then the final answer are spoken back with **text-to-speech**.
5. A **barge-in monitor** listens the whole time the AI is speaking, so the driver can cut in.
6. When the answer finishes, the **microphone reopens itself** and the loop returns to stage 1.

## From zero: how a Compose feature is actually wired

Before the voice-specific parts, here's the machinery underneath, because it's the same for any screen you'll ever build.

**The MVVM + Unidirectional Data Flow trio.** The feature is three pieces:
- a **`UiState`** — an immutable, exhaustive description of everything on screen, modelled as a sealed interface (`CopilotUiState`: `Idle`, `Active`, and so on, each with the fields it needs);
- a **`ViewModel`** (`CopilotViewModel`) — the state holder that owns a `StateFlow<CopilotUiState>` and mutates it in response to events;
- a **Composable screen** — a function that reads the state and renders it.

State flows **down** (ViewModel → Composable); events flow **up** (Composable calls `viewModel.onXxx()`). That's Unidirectional Data Flow, and it's the whole reason a six-stage voice loop stays debuggable: there is exactly one place the state changes.

**Why `StateFlow` and `collectAsStateWithLifecycle`.** `StateFlow` is a coroutine primitive: an always-has-a-value observable stream. The Composable subscribes with `collectAsStateWithLifecycle()`, which — crucially — *stops collecting when the screen isn't visible*, so a backgrounded copilot doesn't keep doing UI work. This is why we use it over the older `LiveData` (Android-only, less composable) or a raw callback (no lifecycle-awareness). When the ViewModel sets `_uiState.value = Speaking(...)`, Compose recomposes only the affected UI.

**Why the whole thing is coroutines.** Every stage — recording, an STT network call, the SSE stream, TTS playback — is asynchronous and must not block the main thread (block it for >5s and Android kills you with an ANR). Coroutines let each stage be written as straight-line `suspend` code while running off the main thread, and they give us the one property this feature lives or dies on: **structured concurrency**.

## The state machine, made explicit

The view-model holds an explicit state — listening, processing, speaking, paused, barge-in, idle — because a voice loop with no visible state machine becomes impossible to reason about the first time two stages race.

Modelling it as a **finite state machine (FSM)** is the key move. An FSM is a set of states plus the legal transitions between them; encoding it makes *illegal* transitions (two microphone sessions at once, speaking while still recording) unrepresentable. Any time a feature has "modes" that respond differently to the same event, an FSM is the right tool — this is as true in an interview answer as it is in the code.

## Why voice-activity detection instead of push-to-talk

The easy version is push-to-talk: hold a button, speak, release. Trivial to build, and exactly the tap we were trying to eliminate. So instead the app runs a live VAD engine that watches the audio stream and emits events — speech started, amplitude updated, utterance complete — and the pipeline reacts to *utterance complete* on its own. The driver just talks. When they stop, the engine stops the microphone, the UI orb switches to "searching," and transcription begins. No button in the path.

This is the Gemini-Live-style hands-free mode, and its cost is that the VAD has to be good enough not to fire on road noise or clip the end of a sentence. That's a tuning problem — but the right problem to have, versus a correct-but-untouchable button.

## Why the microphone reopens itself

The decision that took the most care is what happens *after* the AI finishes speaking. A normal assistant returns to idle and waits for the next tap. Ours can't wait for a tap. So when text-to-speech finishes, the completion callback reopens the microphone automatically and the loop returns to listening — no interaction to ask the next question.

That auto-reopen has to be defensive, because the alternative to reopening is a dead conversation. If transcription comes back empty or errors, the loop doesn't stop and surface an error the driver would have to dismiss by hand; it simply reopens the mic and lets them try again. The invariant: **as long as the voice overlay is open, the microphone always comes back.** A hands-free loop that can silently end is worse than useless — the driver has no way to notice or restart it without doing the one thing they must not do.

## Barge-in: interrupting the AI before it finishes a sentence

The counterpart to a self-reopening mic is the ability to interrupt. If the copilot starts reading a long answer and the driver already has what they need — or wants to ask something else — they must be able to just talk over it. So a barge-in monitor runs the entire time the AI is speaking; on detecting the driver's voice it stops TTS immediately, cancels the in-flight response, and reopens the microphone.

This is where **structured concurrency** earns its place. The search runs on a coroutine launched in the ViewModel's scope; barge-in must **cancel** it cleanly — stop the network read, release the TTS engine, and start over — with no orphaned callback firing later against stale state. Coroutine cancellation is cooperative and scoped: cancel the parent job and every child stops. Getting cancellation right is a genuinely hard concurrency problem, and it's the reason we didn't hand-roll this with raw threads and callbacks, where a late callback mutating dead state is the classic bug.

The subtle part is *when* the monitor starts. We start it before the first syllable of the answer plays, so even the opening word is interruptible. And after a barge-in we insert a brief pause before listening again, so the tail of the AI's own voice bleeding out of the speaker isn't mistaken for the driver starting to talk.

## Why the answer streams: SSE over a blocking request

The answer streams back token-by-token over **Server-Sent Events (SSE)** — a long-lived HTTP response the server writes to incrementally — consumed with raw **OkHttp** (we drop below Retrofit here because we need the raw response body as a stream, not a parsed object). The alternative, a single blocking request that returns the whole answer at the end, would feel broken: several seconds of silence, then a wall of speech. Streaming lets TTS start on the first sentence while the rest is still being generated, cutting *perceived* latency to near-zero.

Know the neighbours for interviews: **SSE** is one-way server→client over plain HTTP (ideal here); **WebSockets** are full-duplex (overkill for a one-way answer); **long-polling** is repeated requests (legacy). SSE also means handling *partial* data and a producer (the network) that can outrun the consumer (TTS) — buffering and ordering matter.

## Grounding every question in where the car actually is

One more thing happens before a transcript leaves the phone: it's fused with live context. The app continuously aggregates GPS position, speed, the road-roughness reading from the Pi, and any hazards ahead, and attaches that to the query. So "is this road safe?" is never answered in the abstract — the backend receives the question *and* the coordinates, speed, and roughness for the stretch the car is on. The voice loop is the interface; context fusion is what makes the answers worth listening to.

## The production edge: this must survive backgrounding

A from-zero note on the part a demo skips. A voice loop that keeps running while the app isn't foregrounded needs a **foreground service** with an ongoing notification — that's the OS contract for "I'm doing user-visible work in the background," and without it Android will suspend your microphone and coroutines to save battery. Getting the audio-focus handshake right (ducking navigation prompts, releasing on barge-in) and honouring the lifecycle is the difference between a demo that works while you stare at it and a copilot that survives a real drive. In the hardening plan this feature's business logic (the loop orchestration) also moves out of the ViewModel into a testable UseCase, so the FSM can be unit-tested with fake STT/TTS.

## Takeaway

A hands-free copilot isn't a normal assistant with the buttons hidden. It's a loop that has to close itself — detect speech without a tap, speak the answer, reopen the mic, stay interruptible throughout — while treating every failure as a reason to keep listening rather than to stop and ask for help. The hard parts weren't the speech tech; they were the **finite state machine** that sequences the stages, the **structured concurrency** that makes barge-in cancel cleanly, and the single rule that the microphone always comes back.

The code is open at [github.com/VigiaLabs/vigia-android](https://github.com/VigiaLabs/vigia-android).

*Engineering VIGIA Mobile · Episode 2 of 5 — Previous: Episode 1. Next: Episode 3, Why the phone and the Pi never share a secret.*

---

## 🎓 CS Fundamentals — study companion

*This is an **Operating Systems** (state machines, concurrency, async, process lifecycle) + **Computer Networks** (streaming) episode, with real-time **System Design** and the Android reactive-UI stack. FSMs, async event loops, and cancellation are common interview ground.*

### Operating Systems / Concurrency
- **Finite State Machine.** The voice loop is an explicit FSM: `idle → listening → processing → speaking → (barge-in) → listening`. Modelling async logic as an FSM makes illegal transitions unrepresentable. Reach for it whenever behaviour depends on "mode."
- **Event-driven / observer model.** VAD emits events; the pipeline reacts; TTS completion fires a callback that reopens the mic. This is the event loop / observer pattern — the same model as UI frameworks and Node.js.
- **Structured concurrency & cancellation.** The search runs on a scoped coroutine; barge-in must cancel it cleanly and start over. Scoped coroutines cancel together (cancel the parent → children stop), preventing leaked work and stale-state callbacks. Cancellation correctness is a genuinely hard topic.
- **Safety + liveness.** "As long as the overlay is open, the mic always comes back" is a **liveness** property (something good eventually happens); its dual **safety** (never two mic sessions at once) is enforced by the FSM. Concurrent systems need both.
- **ANR / main thread.** Blocking the main thread >5s triggers an ANR; coroutines run work off-main so the UI stays responsive.
- **Process lifecycle.** A background voice loop needs a foreground service; the OS suspends background work (Doze) otherwise.

### Android reactive UI (name these)
- **MVVM + UDF**: immutable `UiState` down, events up. **`StateFlow` + `collectAsStateWithLifecycle`**: lifecycle-aware collection that stops when off-screen. **Recomposition**: Compose re-renders only what depends on changed state.

### Computer Networks
- **SSE streaming.** Token-by-token over a long-lived HTTP response (SSE), consumed via raw OkHttp. Reduces perceived latency and enables incremental TTS. **SSE (one-way) vs WebSockets (full-duplex) vs long-polling.**
- **Partial results & backpressure.** A producer (network) faster than the consumer (TTS) means buffering and ordering matter.

**Interview Q&A.**
1. *When model logic as an FSM?* → When behaviour depends on mode and illegal transitions must be forbidden (UI, protocols, game logic).
2. *SSE vs WebSockets vs long-polling?* → One-way server push over HTTP · full-duplex · repeated requests. SSE for streaming responses.
3. *How do you cancel in-flight async work correctly?* → Structured concurrency / cancellation; ensure resources release and no orphaned callback fires on stale state.
4. *Safety vs liveness?* → "Nothing bad ever happens" vs "something good eventually happens."
5. *What is `StateFlow` and why collect it lifecycle-aware?* → An always-valued observable stream; lifecycle-aware collection stops work when the UI is not visible.

### ⚖️ This vs That — the architecture decisions, and the roads not taken

| Decision | Alternatives | Why this choice |
|---|---|---|
| **Voice-activity detection (hands-free)** | Push-to-talk button | A button is the exact tap a driver can't make; VAD lets them just talk (cost: tuning against road noise). |
| **Mic auto-reopens; failures reopen too** | Return to idle after each turn | A hands-free loop that can silently end is useless; "mic always comes back" is non-negotiable. |
| **Barge-in starts before first syllable** | Interrupt only after the answer starts / not at all | An uninterruptible assistant is worse in a car; early monitoring makes even the first word interruptible. |
| **Coroutines + structured concurrency** | Raw threads + callbacks | Cancellation is the hard part; scoped coroutines cancel cleanly and avoid stale-state callbacks. |
| **SSE streaming + per-step TTS** | Wait for the full answer, then speak | Waiting feels broken; streaming speaks as it arrives, cutting perceived latency to near-zero. |
| **StateFlow + UDF** | Mutable shared state / LiveData | One place state mutates; lifecycle-aware, testable, Compose-native. |

**The one to defend:** *the self-closing loop (FSM + auto-reopen + clean cancellation).* The insight isn't the speech tech; it's that **a hands-free interface must be a loop that closes itself and can never silently stop** — an explicit FSM with a liveness invariant (mic always returns), preemptible output (barge-in), and structured concurrency so interruption cancels cleanly. Treating every failure as "keep listening" is what makes it usable at 80 km/h.

---

> 📚 **Prepping for placements or interviews?** This series doubles as a study track. The [**VIGIA Interview Prep Companion**](https://ridingbluewaves.hashnode.dev/the-vigia-interview-prep-companion) ties every post to coding-round patterns, system-design drills, and behavioral (STAR) answers — a full Microsoft/Amazon prep guide grounded in these projects.
