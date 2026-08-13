# Episode 5: From best-effort alerts to a durable, deduplicated warning inbox

*What chat persistence already gets right, why MQTT QoS plus FCM still loses alerts in the current app,
and how Room transactions, stable event IDs, idempotency, WorkManager gap sync and delivery SLOs turn two
best-effort transports into a reliable user experience.*

A phone in a moving car loses connectivity exactly where resilience matters. The prototype already preserves
chat sessions and partial answers, but the audit found that its safety-alert path does **not** yet provide the
guarantee implied by the old title. FCM injects into an in-memory `SharedFlow`; there is no durable hazard
inbox or immediate system notification, and a process started only for FCM can exit with no UI collector.
MQTT uses QoS 1 but no durable application dedupe, normal AWS IoT client authentication is absent, and
`cleanSession=false` is paired with a random client ID that defeats durable broker-session identity.

This is Episode 5 of 5 in the Engineering VIGIA Mobile series. The full system overview is in the [master post](https://ridingbluewaves.hashnode.dev/engineering-the-vigia-in-vehicle-ai-copilot).

## From zero: the local-persistence toolkit

Three Android storage tools, and knowing which is which is interview-standard:
- **Room** — an ORM over SQLite. You declare `@Entity` data classes and a `@Dao` with SQL queries; Room checks the SQL at *compile time* and can return results as a `Flow` that re-emits whenever the underlying table changes. This is the backbone of offline-first: the UI observes the database, and the database is the source of truth. We chose it over **SQLDelight** (also excellent) for Jetpack cohesion and the Flow integration, and over raw SQLite because hand-written cursor code is error-prone and untyped.
- **DataStore** — the modern replacement for `SharedPreferences`, for small key/values (last-sync time, device-association id). It's coroutine/Flow-based and transactional, unlike the old synchronous, main-thread-unsafe `SharedPreferences`.
- **WorkManager** — the framework for persistent, **deferrable** work with constraints and retry. Scheduled
  work can survive process death/reboot, but timing is inexact and execution can be delayed. It is right for
  token registration, gap/full sync and queued idempotent upload—not for postponing an immediate FCM
  notification or promising an exact 15-minute financial refresh.

## A streamed answer is written down as it arrives

When the copilot answers, the response streams back token by token over Server-Sent Events. The naive way to handle a stream is to accumulate the whole thing in memory and save it once, at the end. On a stationary device that's fine. In a car it means the most common real-world event — the network dropping halfway through a long answer — throws away everything received so far, and the driver is left with nothing and no record it ever happened.

So every session and message lives in a **Room** database, and the stream **writes through** to it as it goes. If the connection drops or the response is cancelled mid-sentence, the partial answer is persisted with an explicit `Partial` status rather than discarded. The driver keeps what arrived, the history is intact, and the app knows the turn was incomplete. A dropped connection is recorded as a *fact about a message*, not surfaced as an error the driver has to dismiss.

From zero, this is **durability under failure** at the client. It resembles write-through checkpointing,
not SQLite's write-ahead log itself. Persisting incrementally narrows the loss window; claiming “nothing can
ever be lost” would require transaction/flush/process/device-failure evidence. Batch updates to avoid a disk
write per token, store completion/partial state transactionally, and bound the in-memory SSE stream.

That's the whole philosophy in miniature: the failure mode of a moving vehicle isn't exceptional, so it's handled as ordinary data flow, not as an exception.

## Offline-first, honestly: what's shipped and what's next

The same instinct runs through the rest of the state — but here's the honest, from-zero picture rather than a marketing one, because "offline-first" is a spectrum and it's worth being precise.

**Shipped today:** conversation history is local-first — sessions and messages live in Room, and harsh-driving events are persisted locally too. Open the app in a dead zone and your past conversations are there, because they never depended on the network. This is the part a driver most often wants when there's no signal: what the copilot said about this road last time.

**The offline-first roadmap (our hardening plan, WS-6):** wallet balance, hazard history, and the reward ledger are still *network-only* today — lose the connection and those panes are empty. Making them truly offline-first means the same pattern we already use for chat: a Room-backed single source of truth the UI reads from a `Flow`, refreshed in the background by a **WorkManager** worker (cached-then-network). We've specced it; it's the deliberate next step, not a shipped claim. Naming that gap precisely is the point — an offline-first *architecture* is a discipline you apply data type by data type, and we're honest about which types have it yet.

The general pattern needs an authority qualifier. Room can be the source of truth for the **local read model**;
the backend remains authoritative for ownership, rewards and settled money. Each cached row carries server
version, source, observed time, expiry/freshness and last refresh error. Reads can remain available, but stale
balance cannot authorise payout and expired hazards cannot alert.

## A critical alert outranks the conversation

The intended primary path is a persistent **MQTT** subscription. The current configuration requests a
persistent session but creates a random client ID on reconnect, so the broker sees a new session. Before
speech priority matters, the application needs a stable authenticated identity, visible connection state,
sequence/gap recovery, expiry and durable dedupe.

A **critical** alert pre-empts the copilot mid-sentence. If the AI is speaking an answer and a critical hazard fires, the alert flushes the speech queue and speaks *now* — the answer is interrupted so the warning gets out. Lower-severity alerts queue politely behind whatever is playing and shift the UI's status orb instead. This is an intentional **priority scheduling** decision — in a car, a collision warning outranks a sentence about a road's maintenance history, every time. An assistant that finished its paragraph before mentioning the obstacle ahead would be worse than no assistant.

The same pre-emption logic carries the proactive advisories the app generates locally from sensor fusion — lane-drift nudges, a fatigue proxy, a forward-collision warning off the Pi's detection, speed advice into a curve. Routine advisories are appended to the speech queue; urgent ones flush it. Priority is encoded in *how* each message is spoken, not just whether it is.

## From zero: MQTT QoS, and why two delivery paths

**MQTT** is a publish/subscribe protocol designed for constrained clients/networks. Its QoS levels describe
delivery of an MQTT message across a particular client/broker interaction—not permanent, end-to-end user
visibility:

- **QoS 0 — at most once:** no protocol retry; loss is possible.
- **QoS 1 — at least once:** retry until acknowledged; duplicates are normal.
- **QoS 2 — exactly once at the MQTT protocol exchange:** additional handshake removes duplicate protocol
  delivery for that session, but it does not make downstream database, notification or speech side effects
  globally exactly once.

Safety alerts should use at-least-once ingress plus application idempotency. A globally stable hazard ID and
unique Room constraint make repeated MQTT/FCM copies converge on one inbox row; an acknowledgement/expiry
policy prevents repeat speech.

But a persistent socket has a gap: Android may suspend/kill background work. **FCM** can provide another
ingress path, but “high priority” is not a durable queue or unlimited execution grant. The callback has a
short execution window; Firebase expects high-priority delivery to result in user-visible notification work
and can deprioritise misuse. The receiver should validate/dedupe/persist and post the notification immediately,
then use WorkManager for token registration or work lasting more than a few seconds. `onDeletedMessages`
triggers a bounded full/gap sync.

## The production pipeline: persist before announcing

```text
MQTT or FCM
    -> authenticate/validate schema, signature, scope and expiry
    -> Room transaction: INSERT eventId UNIQUE (or update higher version)
    -> notification policy: permission, severity, acknowledgement, driving/audio focus
    -> system notification and, when allowed, deduplicated TTS
Room Flow -> active UI
WorkManager -> token registration, gap/full sync, deferred acknowledgement
```

Each event needs `eventId`, version/sequence, `createdAt`, `expiresAt`, severity, spatial scope and
provenance/signature. Persist before acknowledgement/announcement. The database—not `SharedFlow.replay`—is
the durable inbox. A new collector observes unexpired unacknowledged rows and must not re-speak a previously
handled warning. This is an **effectively-once user experience built over at-least-once ingress**.

The cloud connection also needs actual AWS IoT client authentication: per-device X.509 on supported MQTT
connections or Cognito/SigV4 over WebSockets, with rotation and revocation. A server-authenticated TLS socket
alone does not authorise the mobile client to an ordinary AWS IoT Core endpoint.

Reliability becomes measurable with SLIs: valid hazards persisted / valid hazards sent; ingress-to-system-
notification latency at P50/P95/P99; duplicate notification/speech rate; expired-alert suppression; MQTT
connected duration; gap-sync success. “Always deliver” is not a design. It is a target with a scope, SLO,
error budget, telemetry and incident runbook.

## Takeaway

The prototype has a good resilience instinct: chat persists partial work and critical speech can outrank
conversation. The audit makes the limit precise. Today hazards can disappear with the process, MQTT sessions
are not durably identified, and two delivery paths can duplicate without application idempotency. Production
resilience is the durable pipeline above plus migration/backup safety, authenticated cloud connectivity,
notification permission UX and measured delivery. The mature story is not “we never drop an alert”; it is
“we found why that claim was false, defined the invariant and built the evidence needed to earn it.”

The code is open at [github.com/VigiaLabs/vigia-android](https://github.com/VigiaLabs/vigia-android).

*Engineering VIGIA Mobile · Episode 5 of 5 — Previous: Episode 4. Back to the [series overview](https://ridingbluewaves.hashnode.dev/engineering-the-vigia-in-vehicle-ai-copilot).*

---

## 🎓 CS Fundamentals — study companion

*This finale spans **Computer Networks** (streaming, MQTT QoS, push), **DBMS** (local persistence, partial writes, sync), **OS** (background execution, preemption, Doze), and **System Design** (offline-first, message priority), plus the Android storage stack from zero. Rich, and very interview-relevant for mobile/distributed roles.*

### Computer Networks
- **MQTT & QoS levels.** QoS is scoped to protocol delivery, not end-to-end side effects. QoS 1 duplicates;
  QoS 2 does not make notification/TTS/database globally exactly once. Stable IDs + transactions + effect
  acknowledgement provide application idempotency.
- **Persistent connection vs push.** MQTT can reduce latency while active; FCM provides a separate OS-managed
  path with a short callback window. Both can miss/duplicate/reorder; durable sync closes gaps.
- **Streaming + partial consumption.** The answer streams over SSE and is written down as it arrives — networks-meets-DBMS.

### DBMS / Data (local — the Android storage stack)
- **Offline-first & local persistence.** Sessions/messages live in a local **Room** (SQLite) database, so the app works with no network — local is the source of truth, the cloud is a sync layer. The offline-first pattern.
- **Write-through / partial writes.** The stream persists tokens *as they arrive*, marking an interrupted message `Partial` — durability under failure, the client analogue of a write-ahead log.
- **Eventual consistency / sync.** Local writes reconcile with the cloud on reconnect — the client is an eventually-consistent replica.
- **The toolkit.** **Room** for relational durable state; **DataStore** for small asynchronous settings;
  **WorkManager** for persistent deferrable work with inexact timing. None replaces an immediate notification.
- **Migrations and backups.** A production database exports schemas and tests every supported migration;
  destructive fallback can erase data. Backup is an allow-list decision, especially for key/pairing/payment data.

### Operating Systems
- **Background execution & Doze.** High-priority FCM supplies a short, policy-governed execution window,
  not arbitrary background runtime. Post immediate user-visible work; defer longer work.
- **Preemption / priority.** A **critical** alert *flushes* the TTS queue and speaks immediately, preempting the current answer; lower-severity alerts queue. Priority scheduling encoded in *how* each message is enqueued.

### System Design
- **Graceful degradation & message hierarchy.** Persist partial answers, model freshness, rank output, and
  close transport gaps with durable sync. Never turn “two paths” into an unmeasured delivery guarantee.

**Interview Q&A.**
1. *Explain MQTT QoS levels.* → State the protocol scope. QoS 1 duplicates; QoS 2 adds handshake within
   MQTT but downstream side effects still need idempotency.
2. *What is offline-first and how do you build it?* → Local durable read model + freshness/provenance,
   queued idempotent writes and conflict policy. Name the server/device authority per data type.
3. *How do you not lose a streamed response if the network drops mid-way?* → Persist incrementally (write-through) and mark partial; never buffer-only-then-save.
4. *Why both a persistent socket and push notifications?* → Independent failure modes can improve
   availability, but require a common event ID, dedupe and gap sync; neither guarantees visibility alone.
5. *How do you make a safety alert preempt other output?* → Priority scheduling: flush/interrupt lower-priority output (TTS queue) for the critical message; queue the rest.
6. *Room vs SharedPreferences vs DataStore vs WorkManager?* → Structured relational storage; legacy
   small-values; modern async small-values; persistent deferrable scheduling. WorkManager is not storage.
7. *How do you make at-least-once effectively once?* → Persist a stable idempotency/event key under a
   unique constraint in the same transaction as state; make external side effects resumable/acknowledged.
8. *How do you detect a missed alert?* → Monotonic sequence/version per scope, last-applied checkpoint,
   gap API/full sync, expiry and reconciliation metrics.

### ⚖️ This vs That — the architecture decisions, and the roads not taken

| Decision | Alternatives | Why this choice |
|---|---|---|
| **Persist streamed tokens as they arrive** | Accumulate in memory, save once at the end | In a car the network drops mid-answer constantly; save-at-end loses everything received. Write-through + `Partial` status loses nothing. |
| **Offline-first (local DB source of truth)** | Cloud-first, require connectivity | The app is needed most in dead zones; a cloud-first app shows a blank screen there. (Shipped for chat; roadmap for wallet/hazards via WorkManager.) |
| **Critical alert preempts TTS** | Finish the current sentence, then announce | A collision warning after a paragraph about road history is worse than useless; safety outranks conversation, encoded as queue-flush vs queue-add. |
| **MQTT + FCM into one durable inbox** | Either alone; two in-memory callbacks | Independent ingress improves availability only when stable IDs, transactions, dedupe, expiry and gap sync converge them. |
| **Room + WorkManager, different jobs** | In-memory flow; exact-alarm assumptions | Room persists truth/read models; WorkManager resumes deferrable reconciliation. Immediate notification stays in the receiver window. |

**The one to defend:** *transport delivery is not user-visible delivery.* The senior framing follows one
event across broker/push, process, transaction, notification permission, TTS policy, acknowledgement and
gap recovery; names the semantics at each boundary; then defines an SLO and tests duplicates, reorder,
process death, reboot and denial. That is much stronger than saying “MQTT QoS 1 plus FCM.”

## Cross-repository production lens

The durable inbox is a client boundary in a larger event system. The [engineering knowledge pack](../engineering-knowledge/README.md)
and [cross-repository audit](../engineering-knowledge/vigia-cross-repo-audit.md) extend the same
reasoning to cloud queues, Pi ingress, SQLite edge projections, migrations, reconciliation, telemetry,
and rollback. Delivery semantics are only production-ready when every participant shares the event
contract and recovery evidence.

---

> 📚 **Prepping for placements or interviews?** This series doubles as a study track. The [**VIGIA Interview Prep Companion**](https://ridingbluewaves.hashnode.dev/the-vigia-interview-prep-companion) ties every post to coding-round patterns, system-design drills, and behavioral (STAR) answers — a full Microsoft/Amazon prep guide grounded in these projects.
