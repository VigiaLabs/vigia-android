# VIGIA cross-repository architecture audit

**Audit date:** 2026-08-13  
**Method:** codebase-memory graph architecture/search/trace queries, repository manifests/configuration, test/CI inventory, and a comparison with the first-party guidance in [`README.md`](README.md).  
**Important:** this is an evidence snapshot. It is not a security certification, penetration test, capacity test, or claim that every unversioned directory is deployable.

## Scope

The canonical active repositories found under `Documents/Github Repositories` plus the Android workspace were reviewed:

| Codebase | Framework/runtime | Architectural read | Production posture |
|---|---|---|---|
| `vigia2` | Kotlin, Compose, Hilt, Room, coroutines/Flow, Gradle convention plugins | layered multi-module client with repository boundaries, local inbox, BLE/MQTT/FCM/HTTP adapters | **Strong baseline / partial** — hardening tranche is implemented; cross-repo identity, observability, client auth, maintained map data, release signing/staging, and remaining security/migration tests are open |
| `vigia-amazon` | TypeScript workspaces, Next/React, AWS CDK/Lambda, shared contracts, Rust/Solana protocol | cloud/frontend/backend/contracts/infrastructure separation, event and agent paths, meaningful test suite | **Partial** — good modular seams and tests; no repo-level GitHub Actions workflow found; needs contract/replay/observability/release gates |
| `vigia-public` | Next.js/React, LangGraph, Bedrock/AWS SDKs, Express server, Postgres/pgvector, SQLite edge fallback | API/chat/search/agent/data/edge layers with explicit security and release scripts | **Partial** — useful fallback and release tooling; no CI workflow or discoverable test suite in the snapshot; needs SLOs, traces, contract tests, and deployment gates |
| `vigia-functions-orchestrator` | Azure Functions, Python, Kusto, Key Vault, Confidential Ledger, Azure AI | `routes` → `core`/`infra`/`agents` with deterministic idempotency/audit concepts | **Partial** — good policy/infrastructure separation; no tests or CI workflow found; needs telemetry, dependency fault tests, and deployment/replay gates |
| `vigia-raspi` | C++17, Raspberry Pi 5, OpenCV/OpenVINO, ROS-like workspace, CMake, Postgres edge service | sensor/inference/fusion/event-promoter/event-store pipeline with bounded queues and many tests | **Partial / edge prototype** — strongest embedded test inventory; production build must forbid dev signing fallbacks, replace dev credentials, add fleet update/recovery and hardware-in-loop gates |
| `VigiaWeb/vigia-demo` | Next.js/React, Supabase, MapLibre/Leaflet, ONNX Runtime Web | dashboard/demo and analytics surfaces with API/auth routes | **Demo** — useful product surface; node_modules is tracked, no tests/CI workflow found, and the demo boundary needs an explicit deploy/security contract |
| `vigia-web` | Vite/React marketing site, React Router, Three.js | presentation-focused component/page tree | **Marketing site** — not a backend or operational control plane; add basic build/accessibility/security checks if it becomes a public production surface |
| `VigiaHMAS` (unversioned) | Python edge router + Azure function | small edge-routing prototype and dummy-data sender | **Prototype / no repository history** — needs ownership, source control, tests, auth, rate limiting, and deployment provenance before it is treated as a product component |
| `vigia-aws-backup` (unversioned) | DynamoDB JSON exports | data snapshot only, no application code | **Recovery material, not an app** — protect with encryption/access control/retention and validate a restore procedure; do not treat the folder as an architecture source |

`Documents/VigiaAndroidApp` contained no meaningful source at audit time and is not counted as a second Android implementation. The memory graph indexed 11 project snapshots; its cross-repository route/channel pass found no machine-verifiable cross edges, so phone/edge/cloud contracts still need explicit compatibility tests rather than inferred coupling.

## What already matches the production bar

- **Android boundaries:** the client uses the platform-recommended UI/data layering, multi-module Gradle conventions, Hilt, Room, and Flow. The first hardening tranche added fail-closed production auth, typed claim failures, bounded BLE work, durable alert persistence, explicit data availability, migration/schema export, backup restrictions, redacted logs, finite HTTP bounds, and CI compile/test/lint gates.
- **Durability intent:** the Android alert inbox, Amazon contracts/tests, Azure audit/idempotency flows, and Pi event signer/promoter show the right direction: stable IDs, append-only/auditable state, and replay-aware processing.
- **Edge discipline:** the Pi repository has explicit CMake targets, C++17, bounded queues, event signing code, sensor health, performance/visual/security tests, and hardware-specific performance notes.
- **Cloud composition:** AWS CDK/infrastructure, Azure identity/key services, Postgres/pgvector, local SQLite fallback, and managed messaging are reasonable choices when the missing operational contracts below are completed.
- **Honest public writing:** the Android series already distinguishes implemented, partial, and planned work; the companion guides now use the same vocabulary across repositories.

## Highest-risk gaps

### P0 — close before a production release

1. **One cross-repository identity/ownership contract.** Specify protocol version, challenge, `deviceSig`, key fingerprint, account authorization, freshness/replay, revocation, reset/recovery, and compatibility tests across Android ↔ Pi ↔ cloud. The Android wallet/device claim must remain disabled until the server verifies a real device proof.
2. **Production-only transport authentication.** MQTT needs client authentication/authorization in addition to TLS; edge HTTP/event ingress needs the same device/user binding and replay/rate controls. Do not rely on a topic containing a user ID.
3. **Release provenance.** Add protected CI workflows to every deployable repository, dependency/security scans, signed/reproducible artifacts, staged rollout, rollback, and environment configuration validation. `vigia2` has a baseline workflow; the other repositories did not expose a repository-level workflow in this snapshot.
4. **Operational evidence.** Define SLIs/SLOs for pairing, alert visibility/duplicates, voice responsiveness, search correctness/latency, edge ingest, and crash/ANR. Propagate correlation IDs and collect structured logs, metrics, and traces with secrets/location redaction.
5. **Data recovery and schema safety.** Test Android Room upgrades, Postgres/SQLite pack versions, Kusto/DynamoDB retention, backups/restores, outbox/inbox replay, and provider reconciliation. A JSON export is not evidence of recoverability until restore is exercised.

### P1 — complete for a credible launch candidate

- Extract cohesive voice, pairing, alert, map, and payment workflows from high-fan-in coordinators; keep transport adapters at the boundary.
- Add cross-repo contract tests and a versioned event/schema registry; test duplicate, reorder, missing, stale, and unknown-version messages.
- Maintain a signed, freshness-stamped map/tile service with a documented geographic source, update cadence, cache invalidation, and offline fallback policy.
- For Pi, make production builds fail when OpenSSL/transport signing is unavailable, remove development credentials, add device provisioning/rotation/recovery, and add hardware-in-loop/thermal/long-run tests.
- For `vigia-public`, `vigia-amazon`, and Azure functions, add integration tests for provider timeouts, partial fan-out, rate limits, DLQs, retries, and reconciliation; add deployment smoke tests and canary gates.
- Remove tracked `node_modules` from `VigiaWeb`, lock dependency policy, and define a secure demo-to-production boundary for Supabase/auth/ONNX assets.

### P2 — improve scale and maintainability after correctness

- Baseline profiles/macrobenchmarks and battery/network budgets for Android; CPU/cache/thermal profiles for Pi; cost-per-query/event/device dashboards in cloud.
- Add ownership metadata/ADRs and dependency direction checks to each repository.
- Consolidate generated diagrams and public claims from the same contract/source-of-truth files.
- Evaluate semantic cache invalidation, regional partitioning, and model/edge rollout only after SLO and freshness evidence exists.

## Standard-framework comparison

The projects are using recognizable, current frameworks: Jetpack Compose/Hilt/Room/Flow on Android; Next.js/React/TypeScript and AWS CDK in web/cloud; Azure Functions/Python; CMake/C++/OpenVINO on edge. That is a sound baseline. “Latest” should not mean upgrading every dependency immediately. The durable standard is:

1. platform-recommended architecture and lifecycle handling;
2. small, cohesive modules with explicit public APIs;
3. typed contracts and state machines at boundaries;
4. durable/idempotent state under at-least-once delivery;
5. least privilege, privacy minimization, and secure defaults;
6. telemetry tied to user SLOs;
7. automated, signed, reversible delivery;
8. evidence from tests, benchmarks, restore drills, and incident learning.

VIGIA is strongest on framework selection, embedded algorithmic work, and the Android hardening tranche. It is not yet at the “operated product” bar because cross-repo contracts, observability, release controls, and recovery evidence are still uneven.

## Blog alignment rules

The public articles should:

- call the Android app an advanced prototype with an implemented hardening tranche, not a production release;
- present the Pi/AWS/Azure/web systems as a distributed portfolio with explicit contract work still open;
- teach the six subject areas through the real VIGIA decisions (authority, idempotency, migrations, cache/CPU locality, lifecycle, IAM/SLOs);
- distinguish platform guidance from VIGIA evidence and label measurements as measured, target, or illustrative;
- link readers to this audit and the knowledge pack so interview claims can be checked against code.

