# Episode 5: Never drop a token, always deliver the warning

*Why a streamed answer is persisted token-by-token, why a critical hazard alert interrupts the copilot mid-sentence, and how the app treats a lost connection as a normal event.*

A phone in a moving car has a network connection that comes and goes, and it tends to go in exactly the places that matter — underpasses, rural stretches, the unlit road where the pothole is. An app that assumes connectivity fails there constantly. So VIGIA Mobile is built on the opposite assumption: the connection *will* drop, the app should degrade instead of error, and the one message that must never be lost — a safety alert — should be able to pre-empt everything else. This post is about the two halves of that: never losing an answer, and never delaying a warning.

This is Episode 5 of 5 in the Engineering VIGIA Mobile series. The full system overview is in the master post.

## A streamed answer is written down as it arrives

When the copilot answers, the response streams back token by token over Server-Sent Events. The naive way to handle a stream is to accumulate the whole thing in memory and save it once, at the end. On a stationary device that is fine. In a car it means the most common real-world event — the network dropping halfway through a long answer — throws away everything received so far, and the driver is left with nothing and no record it ever happened.

So every session and message lives in a Room database, and the stream writes through to it as it goes. If the connection drops or the response is cancelled mid-sentence, the partial answer is persisted with an explicit `Partial` status rather than discarded. The driver keeps what arrived, the history is intact, and the app knows the turn was incomplete. A dropped connection is recorded as a fact about a message, not surfaced as an error the driver has to dismiss.

That is the whole philosophy in miniature: the failure mode of a moving vehicle is not exceptional, so it is handled as ordinary data flow, not as an exception.

## Offline-first, because the field is where it's used

The same instinct runs through the rest of the state. Conversation history is local first — it is in Room, so it is there whether or not the network is. The app is built to open, show past sessions, and remain useful in a dead zone, because the dead zone is precisely where a driver might most want to pull up what the copilot said about this road last time. Connectivity is treated as an enhancement to a working local app, not a precondition for the app working at all.

## A critical alert outranks the conversation

The second half is about priority. Hazard alerts arrive over a persistent MQTT connection — a long-lived, low-overhead channel with at-least-once delivery, subscribed to the driver's own alert topic, and configured to keep its session across brief disconnects so a reconnect doesn't miss a queued warning. When an alert comes in, its severity is evaluated, and here we made a deliberate choice that feels wrong until you remember the setting.

A **critical** alert pre-empts the copilot mid-sentence. If the AI is in the middle of speaking an answer and a critical hazard fires, the alert flushes the speech queue and speaks *now* — the answer is interrupted so the warning gets out. Lower-severity alerts queue politely behind whatever is playing and shift the UI's status orb instead. This is an intentional priority inversion: in a car, a collision warning outranks a sentence about a road's maintenance history, every time. An assistant that finished its paragraph before mentioning the obstacle ahead would be worse than no assistant.

The same pre-emption logic carries the proactive advisories the app generates locally from sensor fusion — lane-drift nudges, a fatigue proxy, a forward-collision warning off the Pi's own detection, speed advice into a curve. Routine advisories are appended to the speech queue; the urgent ones flush it. Priority is encoded in *how* each message is spoken, not just whether it is.

## Delivery even when the phone is asleep

There is one more gap to close: Android aggressively suspends background work to save battery ("Doze"), and a suspended MQTT client can miss a message. So a push-notification path via Firebase Cloud Messaging sits behind the MQTT channel as a fallback wake-up. The persistent connection is the primary, low-latency path; the push is the safety net that can rouse the app when the OS has put it to sleep. Two paths, because a hazard alert that arrives only when the phone happens to be awake is not a hazard alert you can rely on.

## Takeaway

Resilience here was not a feature bolted on at the end; it was the default assumption the data path was built around. Streamed answers are written down as they arrive so a mid-sentence drop loses nothing. History is local so the app works in the dead zones where it is needed most. And the message hierarchy is explicit — a critical warning pre-empts the conversation, with a push-notification fallback for when the phone is asleep — because the one thing an in-vehicle copilot cannot do is be late with the alert that matters.

The code is open at [github.com/VigiaLabs/vigia-android](https://github.com/VigiaLabs/vigia-android).

*Engineering VIGIA Mobile · Episode 5 of 5 — Previous: Episode 4. Back to the series overview.*

---

## 🎓 CS Fundamentals — study companion

*This finale spans **Computer Networks** (streaming, MQTT QoS, push), **DBMS** (local persistence, partial writes), **OS** (background execution, preemption), and **System Design** (offline-first, message priority). Rich, and very interview-relevant for mobile/distributed roles.*

### Computer Networks

- **MQTT & QoS levels.** Hazard alerts arrive over a persistent **MQTT** connection (pub/sub). MQTT defines **QoS 0** (at-most-once, fire-and-forget), **QoS 1** (at-least-once, may duplicate → needs idempotent handling), **QoS 2** (exactly-once, heavier). Safety alerts use at-least-once and keep the session across brief drops so a queued warning isn't missed.
- **Persistent connection vs push.** A long-lived MQTT socket gives low-latency delivery but the OS may suspend it (below). **FCM push** is the fallback that can *wake* the app. Two paths because either alone has a gap.
- **Streaming + partial consumption.** The answer streams over SSE and is written down as it arrives (below) — networks-meets-DBMS.

### DBMS / Data (local)
- **Offline-first & local persistence.** Sessions/messages live in a local **Room** (SQLite) database, so the app works with no network — the source of truth is local, the cloud is a sync layer. This is the **offline-first** pattern (local DB + background sync), the opposite of assuming connectivity.
- **Write-through / partial writes.** The stream persists tokens *as they arrive*, marking an interrupted message `Partial`. A mid-stream network drop loses nothing — the DB records exactly what arrived. This is **durability under failure** at the client: treat a dropped connection as ordinary data, not an exception.
- **Eventual consistency / sync.** Local writes reconcile with the cloud when connectivity returns — the client is an eventually-consistent replica.

### Operating Systems
- **Background execution & Doze.** Android suspends background work to save battery (**Doze**); a suspended MQTT client can miss messages. FCM (a high-priority push) can wake the app — an OS-scheduling constraint solved with a second delivery path. General theme: the OS reclaims resources from background apps, so critical delivery needs an OS-sanctioned wake path.
- **Preemption / priority.** A **critical** alert *flushes* the TTS queue and speaks immediately, preempting the current answer; lower-severity alerts queue (`QUEUE_ADD`). This is a **priority scheduling** decision encoded in *how* each message is enqueued — a deliberate priority inversion where safety outranks conversation.

### System Design
- **Graceful degradation & message hierarchy.** Persist partial answers, work offline, and rank messages so a critical warning always gets through (with a push fallback). Resilience is the *default assumption* of the data path, not an add-on.

**Interview Q&A.**
1. *Explain MQTT QoS levels.* → 0 at-most-once, 1 at-least-once (dedup/idempotent), 2 exactly-once (costly); pick per delivery guarantee vs overhead.
2. *What is offline-first and how do you build it?* → Local DB as source of truth + background sync; UI reads local, writes queue for reconciliation; handle conflicts on reconnect.
3. *How do you not lose a streamed response if the network drops mid-way?* → Persist incrementally (write-through) and mark partial; never buffer-only-then-save.
4. *Why do you need both a persistent socket and push notifications?* → The socket is low-latency but the OS can suspend it; push can wake the app so critical messages still arrive.
5. *How do you make a safety alert preempt other output?* → Priority scheduling: flush/interrupt lower-priority output (TTS queue) for the critical message; queue the rest.

### ⚖️ This vs That — the architecture decisions, and the roads not taken

| Decision | Alternatives | Why this choice |
|---|---|---|
| **Persist streamed tokens as they arrive** | Accumulate in memory, save once at the end | In a car the network drops mid-answer constantly; save-at-end loses everything received. Write-through + `Partial` status loses nothing and records the truth. |
| **Offline-first (local DB source of truth)** | Cloud-first, require connectivity to function | The app is needed most in dead zones; a cloud-first app shows a blank screen there. Local-first works offline and syncs later. |
| **Critical alert preempts TTS** | Finish the current sentence, then announce | A collision warning after a paragraph about road history is worse than useless; safety must outrank conversation, encoded as queue-flush vs queue-add. |
| **MQTT (persistent) + FCM (push) dual path** | MQTT only | A suspended socket misses messages under Doze; FCM wakes the app. Redundant paths because a late safety alert is a failed one. |

**The one to defend:** *treat failure as the normal case.* The senior framing: **in a moving vehicle, a dropped connection isn't an exception — it's the expected event**, so the data path is built to degrade (persist partials, work offline) and to prioritise (critical alerts preempt, with a push fallback). Resilience is the default, not a feature bolted on at the end.
