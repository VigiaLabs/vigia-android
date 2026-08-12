# Episode 5: Never drop a token, always deliver the warning

*Why a streamed answer is persisted token-by-token, why a critical hazard alert interrupts the copilot mid-sentence, how the app treats a lost connection as a normal event — and a from-zero tour of Room, DataStore, WorkManager, MQTT QoS, FCM, and Android's background-execution limits.*

A phone in a moving car has a network connection that comes and goes, and it tends to go in exactly the places that matter — underpasses, rural stretches, the unlit road where the pothole is. An app that assumes connectivity fails there constantly. So VIGIA Mobile is built on the opposite assumption: the connection *will* drop, the app should degrade instead of error, and the one message that must never be lost — a safety alert — should be able to pre-empt everything else. This post is about the two halves of that: never losing an answer, and never delaying a warning — and, from zero, the persistence and messaging machinery that makes resilience possible.

This is Episode 5 of 5 in the Engineering VIGIA Mobile series. The full system overview is in the [master post](https://ridingbluewaves.hashnode.dev/engineering-the-vigia-in-vehicle-ai-copilot).

## From zero: the local-persistence toolkit

Three Android storage tools, and knowing which is which is interview-standard:
- **Room** — an ORM over SQLite. You declare `@Entity` data classes and a `@Dao` with SQL queries; Room checks the SQL at *compile time* and can return results as a `Flow` that re-emits whenever the underlying table changes. This is the backbone of offline-first: the UI observes the database, and the database is the source of truth. We chose it over **SQLDelight** (also excellent) for Jetpack cohesion and the Flow integration, and over raw SQLite because hand-written cursor code is error-prone and untyped.
- **DataStore** — the modern replacement for `SharedPreferences`, for small key/values (last-sync time, device-association id). It's coroutine/Flow-based and transactional, unlike the old synchronous, main-thread-unsafe `SharedPreferences`.
- **WorkManager** — the framework for *deferrable, guaranteed* background work. It survives process death and reboots, respects battery constraints, and is the correct tool for periodic sync (e.g., a 15-minute wallet refresh). It replaced the tangle of `AlarmManager` + `JobScheduler` + broadcast receivers you used to hand-assemble.

## A streamed answer is written down as it arrives

When the copilot answers, the response streams back token by token over Server-Sent Events. The naive way to handle a stream is to accumulate the whole thing in memory and save it once, at the end. On a stationary device that's fine. In a car it means the most common real-world event — the network dropping halfway through a long answer — throws away everything received so far, and the driver is left with nothing and no record it ever happened.

So every session and message lives in a **Room** database, and the stream **writes through** to it as it goes. If the connection drops or the response is cancelled mid-sentence, the partial answer is persisted with an explicit `Partial` status rather than discarded. The driver keeps what arrived, the history is intact, and the app knows the turn was incomplete. A dropped connection is recorded as a *fact about a message*, not surfaced as an error the driver has to dismiss.

From zero, this is **durability under failure** at the client, and it's the same idea as a database's write-ahead log: commit incrementally so a crash mid-operation loses nothing. The trade-off versus buffer-then-save is a few more small writes for the guarantee that no partial answer is ever lost.

That's the whole philosophy in miniature: the failure mode of a moving vehicle isn't exceptional, so it's handled as ordinary data flow, not as an exception.

## Offline-first, honestly: what's shipped and what's next

The same instinct runs through the rest of the state — but here's the honest, from-zero picture rather than a marketing one, because "offline-first" is a spectrum and it's worth being precise.

**Shipped today:** conversation history is local-first — sessions and messages live in Room, and harsh-driving events are persisted locally too. Open the app in a dead zone and your past conversations are there, because they never depended on the network. This is the part a driver most often wants when there's no signal: what the copilot said about this road last time.

**The offline-first roadmap (our hardening plan, WS-6):** wallet balance, hazard history, and the reward ledger are still *network-only* today — lose the connection and those panes are empty. Making them truly offline-first means the same pattern we already use for chat: a Room-backed single source of truth the UI reads from a `Flow`, refreshed in the background by a **WorkManager** worker (cached-then-network). We've specced it; it's the deliberate next step, not a shipped claim. Naming that gap precisely is the point — an offline-first *architecture* is a discipline you apply data type by data type, and we're honest about which types have it yet.

The general pattern, from zero: **the local database is the source of truth; the network is a sync layer.** The UI reads the DB and never blocks on connectivity; a background job reconciles with the server when a connection returns. The client becomes an **eventually-consistent replica** — reads are always available locally, writes queue and reconcile.

## A critical alert outranks the conversation

The second half is about priority. Hazard alerts arrive over a persistent **MQTT** connection — a long-lived, low-overhead pub/sub channel, subscribed to the driver's own alert topic, configured to keep its session across brief disconnects so a reconnect doesn't miss a queued warning. When an alert comes in, its severity is evaluated, and here we made a deliberate choice that feels wrong until you remember the setting.

A **critical** alert pre-empts the copilot mid-sentence. If the AI is speaking an answer and a critical hazard fires, the alert flushes the speech queue and speaks *now* — the answer is interrupted so the warning gets out. Lower-severity alerts queue politely behind whatever is playing and shift the UI's status orb instead. This is an intentional **priority scheduling** decision — in a car, a collision warning outranks a sentence about a road's maintenance history, every time. An assistant that finished its paragraph before mentioning the obstacle ahead would be worse than no assistant.

The same pre-emption logic carries the proactive advisories the app generates locally from sensor fusion — lane-drift nudges, a fatigue proxy, a forward-collision warning off the Pi's detection, speed advice into a curve. Routine advisories are appended to the speech queue; urgent ones flush it. Priority is encoded in *how* each message is spoken, not just whether it is.

## From zero: MQTT QoS, and why two delivery paths

**MQTT** is a publish/subscribe messaging protocol built for exactly this — low-bandwidth, unreliable networks, many devices. Its three **QoS (Quality of Service)** levels are worth memorizing:
- **QoS 0 — at most once:** fire-and-forget, may be lost.
- **QoS 1 — at least once:** guaranteed delivery, may duplicate, so the receiver must be **idempotent**.
- **QoS 2 — exactly once:** guaranteed and de-duplicated, but the heaviest handshake.

Safety alerts use at-least-once with a persistent session, so a queued warning survives a brief drop.

But a persistent socket has a gap: Android aggressively suspends background work to save battery (**Doze** and the background-execution limits), and a suspended MQTT client can miss a message. So a push path via **Firebase Cloud Messaging (FCM)** sits behind the MQTT channel as a fallback wake-up. FCM high-priority messages can rouse a Dozing app when the socket can't. The persistent connection is the primary, low-latency path; the push is the safety net. Two paths, because a hazard alert that arrives only when the phone happens to be awake is not a hazard alert you can rely on.

## Takeaway

Resilience here wasn't a feature bolted on at the end; it was the default assumption the data path was built around. Streamed answers are written down as they arrive so a mid-sentence drop loses nothing. History is local so the app works in the dead zones where it's needed most — and we're honest that extending that to wallet and hazards is the next step, not a shipped claim. And the message hierarchy is explicit — a critical warning pre-empts the conversation, with a push-notification fallback for when the phone is asleep — because the one thing an in-vehicle copilot cannot do is be late with the alert that matters.

The code is open at [github.com/VigiaLabs/vigia-android](https://github.com/VigiaLabs/vigia-android).

*Engineering VIGIA Mobile · Episode 5 of 5 — Previous: Episode 4. Back to the [series overview](https://ridingbluewaves.hashnode.dev/engineering-the-vigia-in-vehicle-ai-copilot).*

---

## 🎓 CS Fundamentals — study companion

*This finale spans **Computer Networks** (streaming, MQTT QoS, push), **DBMS** (local persistence, partial writes, sync), **OS** (background execution, preemption, Doze), and **System Design** (offline-first, message priority), plus the Android storage stack from zero. Rich, and very interview-relevant for mobile/distributed roles.*

### Computer Networks
- **MQTT & QoS levels.** Persistent pub/sub. **QoS 0** at-most-once, **QoS 1** at-least-once (may duplicate → needs idempotent handling), **QoS 2** exactly-once (heavier). Safety alerts use at-least-once with a persistent session.
- **Persistent connection vs push.** A long-lived MQTT socket is low-latency but the OS may suspend it; **FCM** push can *wake* the app. Two paths because either alone has a gap.
- **Streaming + partial consumption.** The answer streams over SSE and is written down as it arrives — networks-meets-DBMS.

### DBMS / Data (local — the Android storage stack)
- **Offline-first & local persistence.** Sessions/messages live in a local **Room** (SQLite) database, so the app works with no network — local is the source of truth, the cloud is a sync layer. The offline-first pattern.
- **Write-through / partial writes.** The stream persists tokens *as they arrive*, marking an interrupted message `Partial` — durability under failure, the client analogue of a write-ahead log.
- **Eventual consistency / sync.** Local writes reconcile with the cloud on reconnect — the client is an eventually-consistent replica.
- **The toolkit.** **Room** (compile-checked SQL, Flow queries) vs **SQLDelight**/raw SQLite; **DataStore** over `SharedPreferences` for small values; **WorkManager** over `AlarmManager`/`JobScheduler` for guaranteed background sync.

### Operating Systems
- **Background execution & Doze.** Android suspends background work to save battery; a suspended MQTT client can miss messages, so a high-priority FCM push provides an OS-sanctioned wake path.
- **Preemption / priority.** A **critical** alert *flushes* the TTS queue and speaks immediately, preempting the current answer; lower-severity alerts queue. Priority scheduling encoded in *how* each message is enqueued.

### System Design
- **Graceful degradation & message hierarchy.** Persist partial answers, work offline, rank messages so a critical warning always gets through (with a push fallback). Resilience is the *default assumption* of the data path.

**Interview Q&A.**
1. *Explain MQTT QoS levels.* → 0 at-most-once, 1 at-least-once (dedup/idempotent), 2 exactly-once (costly); pick per delivery guarantee vs overhead.
2. *What is offline-first and how do you build it?* → Local DB as source of truth + background sync; UI reads local, writes queue for reconciliation; handle conflicts on reconnect.
3. *How do you not lose a streamed response if the network drops mid-way?* → Persist incrementally (write-through) and mark partial; never buffer-only-then-save.
4. *Why both a persistent socket and push notifications?* → The socket is low-latency but the OS can suspend it; push can wake the app so critical messages still arrive.
5. *How do you make a safety alert preempt other output?* → Priority scheduling: flush/interrupt lower-priority output (TTS queue) for the critical message; queue the rest.
6. *Room vs SharedPreferences vs DataStore vs WorkManager?* → Structured relational storage; legacy small-values; modern async small-values; guaranteed deferrable background work.

### ⚖️ This vs That — the architecture decisions, and the roads not taken

| Decision | Alternatives | Why this choice |
|---|---|---|
| **Persist streamed tokens as they arrive** | Accumulate in memory, save once at the end | In a car the network drops mid-answer constantly; save-at-end loses everything received. Write-through + `Partial` status loses nothing. |
| **Offline-first (local DB source of truth)** | Cloud-first, require connectivity | The app is needed most in dead zones; a cloud-first app shows a blank screen there. (Shipped for chat; roadmap for wallet/hazards via WorkManager.) |
| **Critical alert preempts TTS** | Finish the current sentence, then announce | A collision warning after a paragraph about road history is worse than useless; safety outranks conversation, encoded as queue-flush vs queue-add. |
| **MQTT (persistent) + FCM (push) dual path** | MQTT only | A suspended socket misses messages under Doze; FCM wakes the app. Redundant paths because a late safety alert is a failed one. |
| **Room / WorkManager** | Raw SQLite; `AlarmManager` + `JobScheduler` | Compile-checked SQL with Flow queries; guaranteed, battery-aware background sync that survives reboots. |

**The one to defend:** *treat failure as the normal case.* The senior framing: **in a moving vehicle, a dropped connection isn't an exception — it's the expected event**, so the data path is built to degrade (persist partials, work offline) and to prioritise (critical alerts preempt, with a push fallback). Resilience is the default, not a feature bolted on at the end — and where it isn't complete yet (wallet/hazards), we name the gap instead of hiding it.

---

> 📚 **Prepping for placements or interviews?** This series doubles as a study track. The [**VIGIA Interview Prep Companion**](https://ridingbluewaves.hashnode.dev/the-vigia-interview-prep-companion) ties every post to coding-round patterns, system-design drills, and behavioral (STAR) answers — a full Microsoft/Amazon prep guide grounded in these projects.
