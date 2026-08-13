# System design: make the failure modes explicit

System design is the discipline of turning an ambiguous product request into a set of bounded components with measurable behaviour. “Use microservices” is not a design. A design names the authority for each fact, the consistency needed, the cost of a failure, and the evidence that the system works.

## A repeatable interview and implementation loop

Use **REDFOR**:

1. **Requirements:** functional, non-functional, abuse/privacy, and explicit out-of-scope behaviour.
2. **Estimates:** users/devices, events per second, payload size, retention, peak multiplier, latency, battery, and cost budgets.
3. **Data and APIs:** identifiers, schema, authority, versioning, idempotency, pagination, and compatibility.
4. **Flow/components:** draw the hot path first, then asynchronous work, caches, and control planes.
5. **Overload/failure:** duplicate, reorder, partition, slow dependency, process death, region loss, poison message, and backpressure.
6. **Reliability/operations:** SLI/SLO, error budget, alert, runbook, rollout, kill switch, reconciliation, and rollback.

This loop is useful in an interview because it exposes trade-offs early. It is useful in VIGIA because the phone, Pi, cloud, and web repositories are separate failure domains.

## Boundaries and authority

Use high cohesion and low coupling as a default. A boundary should own one reason to change and expose the smallest stable interface. The cached copy of a value is not automatically its authority:

- the server owns account/permission/payment state;
- the paired device owns its private key and live sensor state;
- the phone owns local UI state and restart-safe checkpoints;
- a broker owns delivery mechanics, not business truth;
- a search index owns a query projection, not the source record.

When multiple writers can change a fact, define the conflict policy: version, timestamp, Lamport-style sequence, server arbitration, or explicit human review. Never hide this policy in an incidental database ordering.

## Consistency and delivery

Name the guarantee instead of saying “reliable.” Common choices are:

- at-most-once: simple, but loss is acceptable;
- at-least-once: duplicates are expected, so consumers need a stable event ID and idempotent application;
- effectively-once: at-least-once transport plus a transactional deduplication boundary;
- ordered per key: preserve order for one hazard/device, not necessarily globally;
- convergent state: versions and reconciliation eventually produce the same result.

For VIGIA alerts, persist the event before announcing it, deduplicate by `(source, eventId, version)`, keep a gap/replay path, and collapse stale updates. MQTT/FCM are delivery paths; the inbox and server state are the durable contract.

## Timeouts, retries, queues, and backpressure

A timeout is ambiguous: the remote operation may have committed. Retry only operations that are idempotent or carry a stable idempotency key. Use a deadline, exponential backoff with jitter, a maximum attempt count, and a terminal classification for semantic errors. Do not retry invalid credentials, bad signatures, or unsupported versions.

Every queue needs a capacity policy: bounded buffer, drop/coalesce rule, dead-letter path, and a metric for age/depth. Backpressure must travel to the producer or become an explicit lossy mode. `Promise.allSettled`/supervisor-style fan-out isolates one failing evidence source; it does not make a partial result complete.

## SLOs and error budgets

Google SRE defines an SLI as a user-relevant measurement and an SLO as the target for that measurement. Pick the objective from user value, then instrument it. Useful VIGIA examples:

| User promise | SLI | First SLO candidate |
|---|---|---|
| hazard becomes visible | ingress-to-visible latency, duplicate rate | P99 latency and duplicate percentage by severity |
| pairing completes safely | stage success, time per stage, invalid-peer rate | success rate and bounded failure time |
| voice feels responsive | time to first useful audio, turn completion | P50/P95 by network class |
| data is truthful | unavailable-data rate, stale-data age | no fabricated fallback; freshness distribution |
| releases are safe | crash-free/ANR-free sessions, rollback time | staged gates and recovery objective |

An error budget makes release speed a function of observed reliability rather than optimism. A 100% target is usually less useful than a target with an explicit budget and a response policy.

## Capacity and resilience patterns

Estimate before selecting technology. A rough model is `peak QPS = active users × actions/user/second × peak factor`; include retries and fan-out. Partition by the key that localizes work (device, user, region, geohash, or tenant), but plan for hot keys and skew. Cache only data whose staleness and invalidation policy are explicit. Prefer a smaller synchronous path and move enrichment, indexing, notifications, and reconciliation to asynchronous workers.

Resilience is more than redundancy:

- health checks distinguish startup, readiness, liveness, and dependency health;
- circuit breakers prevent a failing dependency from consuming all threads;
- bulkheads isolate high-cost work;
- graceful degradation returns a truthful reduced capability;
- a reconciliation job repairs missed or duplicated side effects;
- a kill switch disables unsafe or expensive paths without a new client release.

## Interview follow-ups to rehearse

- What happens when a client retries after a lost response?
- What is the authority if local and remote state disagree?
- How do you backfill a new field without stopping writes?
- What is the hottest partition and how do you split it?
- What evidence shows the SLO is user-relevant?
- How do you roll back a schema, model, or protocol change?

The strongest answer is not the most elaborate diagram. It is a smaller design with explicit invariants, bounded failure, and a measurement plan.

