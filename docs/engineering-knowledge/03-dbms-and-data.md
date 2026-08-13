# DBMS and data: correctness survives retries and upgrades

The database is a consistency boundary, not just a place to put JSON. Interviewers usually probe transactions, isolation, indexes, schema change, and recovery. Production systems add idempotency, provenance, retention, and cross-store reconciliation.

## Relational fundamentals

Start with a clear model and constraints: primary keys, foreign keys, uniqueness, nullability, check constraints, and units. Normalize until duplicated facts create update anomalies; denormalize only when a measured read path justifies it and the write/rebuild policy is explicit.

ACID is a set of properties, not a guarantee that every operation is globally serializable:

- atomicity: a transaction commits all of its writes or none;
- consistency: constraints and application invariants remain true;
- isolation: concurrent transactions observe a defined model;
- durability: a committed write survives the failure model promised by the system.

Know the anomalies: dirty read, non-repeatable read, phantom, lost update, write skew, and serialization failure. Choose isolation per operation and handle serialization/deadlock errors deliberately. PostgreSQL's official documentation covers transaction isolation, locking, indexes, and `EXPLAIN`; use the query plan and production measurements instead of guessing from SQL appearance.

## Indexes and query plans

An index trades write/storage cost for read selectivity. Explain the access path: equality/range prefix, sort order, covering columns, cardinality, and hot updates. Composite index order matters. An index that is never selective or never used is operational debt. Capture representative plans in tests or migration reviews, and measure P95/P99 latency under realistic data volume.

For spatial or vector search, name the approximation and freshness policy: geohash partition, R-tree/GiST, HNSW/IVFFlat, reranker, or a keyword fallback. A fallback should expose its lower precision and data age to the caller.

## Local-first mobile storage

Android's offline-first guidance starts in the data layer: the app must read critical data without a network, local data should be available immediately, and refresh work should respect connectivity, battery, and charging constraints. Room migrations are executable contracts. Export schemas, test upgrade paths from every supported version, preserve user data, and define recovery for a corrupt or partially migrated database.

For VIGIA:

- Room is the durable source for the alert inbox and chat history on the phone;
- `StateFlow`/`SharedFlow` are process-local delivery mechanisms, not durable storage;
- SQLite edge packs are projections with a version, geography, freshness, and signature;
- payment, ownership, and device identity remain server/device-authoritative;
- `(0, 0)` or zero telemetry is not a valid data value unless the protocol says it is.

## Idempotency, inbox/outbox, and reconciliation

At-least-once delivery requires a consumer-side deduplication boundary. Use a unique event ID and version; make the inbox insert idempotent. For a local side effect and a remote publish, use a transactional outbox or an explicit resumable state machine. For provider-backed money, model intent, processing, settled, reversed, and failed states; do not equate an HTTP success or a client callback with settlement.

Reconciliation is a first-class job. Compare provider events with internal ledger/inbox state, report drift, and make repair safe to repeat. Retain enough provenance to answer “which source and version produced this value?” without replaying an unbounded history.

## Retention, backup, and privacy

Data lifecycle is part of the schema: retention, deletion, export, redaction, encryption, access logs, and restore tests. Backups are not automatically safe; they need encryption, least privilege, expiry, and a recovery point/recovery time target. Do not put tokens, precise location, or raw payment data into logs or long-lived analytics without a purpose and policy.

## Interview follow-ups

- Which anomaly can happen at your chosen isolation level?
- What is the unique key for a retried request?
- How do you roll back a migration that has already written new data?
- What happens when a replica is stale or a vector index is rebuilding?
- How do you prove a backup can restore, not merely that a snapshot exists?
- Where is the source of truth when cache, inbox, and provider disagree?

