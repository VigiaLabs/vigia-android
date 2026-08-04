# Episode 2: A conversation that survives the driver never touching the screen

*The hands-free voice loop — voice-activity detection, streaming transcription, spoken answers, and barge-in — and why the microphone reopens itself.*

The core constraint of an in-vehicle copilot is simple to state and hard to honour: the driver cannot touch the screen. Not to start talking, not to stop the AI, not to ask a follow-up. Every interaction that requires a tap is an interaction that either doesn't happen or happens with the driver's eyes off the road. So the design target was a full spoken conversation where the phone can sit in a mount, untouched, for the entire drive. This post is about the loop that makes that work.

This is Episode 2 of 5 in the Engineering VIGIA Mobile series. The full system overview is in the master post.

## The loop, end to end

A single turn runs through six stages, and the driver initiates none of them by hand:

1. **Voice-activity detection** listens continuously and decides when the driver has actually started and finished an utterance.
2. On end-of-utterance, the captured audio goes to **speech-to-text** (Sarvam's Indian-language STT).
3. The transcript, enriched with live vehicle context, is sent to the **VIGIASearch backend** as a Server-Sent Events stream.
4. As the answer streams, each reasoning step and then the final answer are spoken back with **text-to-speech**.
5. A **barge-in monitor** listens the whole time the AI is speaking, so the driver can cut in.
6. When the answer finishes, the **microphone reopens itself** and the loop returns to stage 1.

The view-model that drives this holds an explicit state — listening, processing, speaking, paused, barge-in, idle — because a voice loop with no visible state machine becomes impossible to reason about the first time two stages race.

## Why voice-activity detection instead of push-to-talk

The easy version is push-to-talk: hold a button, speak, release. It is trivial to build and it is exactly the tap we were trying to eliminate. So instead the app runs a live VAD engine that watches the audio stream and emits events — speech started, amplitude updated, utterance complete — and the pipeline reacts to *utterance complete* on its own. The driver just talks. When they stop, the engine stops the microphone, the UI orb switches to a "searching" state, and transcription begins. There is no button in the path.

This is the Gemini-Live-style hands-free mode, and the cost of it is that the VAD has to be good enough not to fire on road noise or clip the end of a sentence. That is a tuning problem, but it is the right problem to have — far better than a correct-but-untouchable button.

## Why the microphone reopens itself

The decision that took the most care was what happens *after* the AI finishes speaking. A normal assistant returns to idle and waits for the next tap. Ours cannot wait for a tap. So when text-to-speech finishes delivering the answer, the completion callback reopens the microphone automatically and the loop returns to listening — no interaction required to ask the next question.

That auto-reopen has to be defensive, because the alternative to reopening is a dead conversation. If transcription comes back empty or errors out, the loop does not stop and surface an error the driver would have to dismiss by hand; it simply reopens the mic and lets them try again. The invariant we held was: **as long as the voice overlay is open, the microphone always comes back.** A hands-free loop that can silently end is worse than useless, because the driver has no way to notice or restart it without doing the one thing they must not do.

## Barge-in: interrupting the AI before it finishes a sentence

The counterpart to a self-reopening mic is the ability to interrupt. If the copilot starts reading a long answer and the driver already has what they need — or wants to ask something else — they must be able to just talk over it. So a barge-in monitor runs the entire time the AI is speaking, and on detecting the driver's voice it stops the text-to-speech immediately, cancels the in-flight response, and reopens the microphone.

The subtle part is *when* the monitor starts. We start it before the first syllable of the answer plays, not after, so even the opening word is interruptible. And after a barge-in we insert a brief pause before listening again, so the tail of the AI's own voice bleeding out of the speaker isn't mistaken for the driver starting to talk. Getting that ordering wrong produces either an assistant you cannot interrupt or one that interrupts itself.

## Grounding every question in where the car actually is

One more thing happens before a transcript leaves the phone: it is fused with live context. The app continuously aggregates the vehicle's GPS position, speed, road-roughness reading from the Pi, and any hazards on the road ahead, and attaches that to the query. So "is this road safe?" is never answered in the abstract — the backend receives the question *and* the coordinates, the speed, and the roughness index for the stretch the car is on. The voice loop is the interface; the context fusion is what makes the answers worth listening to.

## Takeaway

A hands-free copilot is not a normal assistant with the buttons hidden. It is a loop that has to close itself — detect speech without a tap, speak the answer, reopen the microphone, and stay interruptible throughout — while treating every failure as a reason to keep listening rather than to stop and ask the driver for help. The hard parts were not the individual pieces of speech tech; they were the state machine that sequences them and the single rule that the microphone always comes back.

The code is open at [github.com/VigiaLabs/vigia-android](https://github.com/VigiaLabs/vigia-android).

*Engineering VIGIA Mobile · Episode 2 of 5 — Previous: Episode 1. Next: Episode 3, Why the phone and the Pi never share a secret.*

---

## 🎓 CS Fundamentals — study companion

*This is an **Operating Systems** (state machines, concurrency, async) + **Computer Networks** (streaming) episode, with real-time **System Design**. Finite state machines and async event loops are common interview ground.*

### Operating Systems / Concurrency

- **Finite State Machine (FSM).** The voice loop is an explicit FSM: `idle → listening → processing → speaking → (barge-in) → listening`. Modelling interactive/async logic as an FSM makes illegal transitions impossible and races reason-about-able. Any time you have "modes" that respond differently to events, reach for an FSM.
- **Event-driven / callback model.** VAD emits events (speech-start, utterance-complete); the pipeline reacts. TTS completion fires a callback that reopens the mic. This is the **event loop / observer pattern** — the same model as UI frameworks and Node.js.
- **Concurrency & cancellation.** The search runs on a background coroutine; a barge-in must **cancel** it cleanly and start over. Structured concurrency (scoped coroutines that cancel together) prevents leaked work — cancellation correctness is a genuinely hard concurrency topic.
- **The self-reopening invariant.** "As long as the overlay is open, the mic always comes back" is a **liveness** property (something good eventually happens). Its dual, **safety** (nothing bad happens — e.g., two mic sessions at once), is enforced by the FSM. Liveness + safety is the formal way to reason about concurrent systems.

### Computer Networks
- **Server-Sent Events (SSE) streaming.** The answer streams token-by-token over a long-lived HTTP connection (SSE) rather than one big response. Streaming reduces *perceived latency* (first token fast) and enables incremental TTS. Compare: SSE (server→client, one-way, over HTTP) vs WebSockets (bidirectional) vs long-polling.
- **Partial results & backpressure.** Consuming a stream means handling partial data and a producer faster than the consumer (TTS) — buffering and ordering matter.

### System Design (real-time interaction)
- **Barge-in / preemption.** Starting the barge-in monitor *before* the first TTS syllable so even the opening word is interruptible is a **preemption** design — the user's input can always preempt the system's output. The brief post-barge-in pause avoids the system's own audio being mistaken for the user (feedback/echo rejection).

**Interview Q&A.**
1. *When would you model logic as a finite state machine?* → When behaviour depends on mode and you must forbid illegal transitions and reason about async events (UI, protocols, game logic).
2. *SSE vs WebSockets vs long-polling?* → One-way server push over HTTP vs full-duplex vs repeated requests; SSE for streaming responses, WS for interactive bidirectional.
3. *How do you cancel in-flight async work correctly?* → Structured concurrency / cancellation tokens; ensure resources release and no orphaned callbacks fire on stale state.
4. *Safety vs liveness properties?* → "Nothing bad ever happens" vs "something good eventually happens"; concurrent systems need both.

### ⚖️ This vs That — the architecture decisions, and the roads not taken

| Decision | Alternatives | Why this choice |
|---|---|---|
| **Voice-activity detection (hands-free)** | Push-to-talk button | A button is the exact tap a driver can't make. VAD lets them just talk; the cost is tuning against road noise. |
| **Mic auto-reopens; failures reopen too** | Return to idle and wait for a tap after each turn | A hands-free loop that can silently end is useless — the driver can't restart it without looking. The invariant "mic always comes back" is non-negotiable. |
| **Barge-in monitor starts before first syllable** | Allow interruption only after the answer starts / not at all | An assistant you can't interrupt is worse in a car; early monitoring makes even the first word interruptible. |
| **SSE streaming + per-step TTS** | Wait for the full answer, then speak | Waiting feels broken; streaming speaks as it arrives, cutting perceived latency to near-zero. |

**The one to defend:** *the self-closing loop (FSM + auto-reopen).* The insight isn't the speech tech; it's that **a hands-free interface must be a loop that closes itself and can never silently stop** — modelled as an explicit FSM with a liveness invariant (mic always returns) and preemptible output (barge-in). Treating every failure as "keep listening" rather than "surface an error" is what makes it usable at 80 km/h.
