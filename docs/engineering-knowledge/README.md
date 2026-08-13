# VIGIA engineering knowledge base

**Snapshot:** 2026-08-13  
**Purpose:** a versioned study and design rubric for moving VIGIA from demo behaviour to an operable product and for defending the decisions in systems interviews.

This is a repository memory, not a claim that a language model has been permanently retrained. The codebase-memory graph indexes the implementation; these documents preserve the reasoning, source links, and audit decisions that should survive a future session.

## How to use this pack

1. Read the six subject guides before changing a cross-cutting subsystem.
2. Read [`vigia-cross-repo-audit.md`](vigia-cross-repo-audit.md) before describing a repository as production-ready.
3. For every important decision, record the requirement, authority, failure mode, measurement, rollout, and rollback.
4. Keep the words **implemented**, **partial**, and **planned** literal. A design diagram is not evidence of a shipped guarantee.

## The six subject guides

| Guide | What it teaches | VIGIA anchor |
|---|---|---|
| [System design](01-system-design.md) | boundaries, consistency, failure, capacity, SLOs, and interview structure | hazard/alert delivery, pairing, voice, and cross-repository contracts |
| [OOP and design](02-oop-and-design.md) | cohesion, coupling, composition, state machines, immutability, and testable policy | Kotlin repositories, workflow extraction, Python services, and C++ agents |
| [DBMS and data](03-dbms-and-data.md) | transactions, isolation, indexes, migrations, durability, and idempotency | Room inbox, SQLite edge packs, Postgres/pgvector, Kusto, DynamoDB |
| [Computer architecture](04-computer-architecture.md) | latency, locality, memory/concurrency, real-time edge work, and hardware trust | Pi 5 inference pipeline, BLE, Android lifecycle, and Keystore |
| [Mobile production](05-mobile-production.md) | Android architecture, offline-first, lifecycle, privacy, security, testing, and release | `vigia2` Compose/Hilt/Room/Flow app |
| [Cloud infrastructure](06-cloud-infrastructure.md) | Well-Architected trade-offs, event systems, IAM, IaC, observability, and operations | AWS, Azure, edge sync, and staged releases |

## Current bar

“FAANG-style” is not a single framework or secret checklist. The public engineering practices that scale are observable in the sources below: clear ownership, small cohesive boundaries, reviewable changes, explicit SLOs, safe releases, least privilege, durable state, and evidence from tests and telemetry. A smaller system should adopt the principles without copying the complexity of a large company.

For VIGIA, the release bar is:

- correctness before elegance (authentication, ownership, payment, and integrity failures cannot become success);
- durability for every user-visible promise (process death, reboot, duplicate delivery, migration, and network loss are normal cases);
- truthful degradation (missing GPS, telemetry, or remote evidence is represented as unavailable, never invented);
- observable operations (SLIs, SLOs, structured logs, traces/metrics, alerts, runbooks, and rollback);
- one versioned contract across phone, edge, cloud, and web, with compatibility and replay tests;
- a signed, reproducible artefact promoted through staged environments.

## Primary references

These are the authoritative or first-party references used for this snapshot:

- [Android app architecture](https://developer.android.com/topic/architecture) and [Android modularization](https://developer.android.com/topic/modularization)
- [Android offline-first data layer](https://developer.android.com/topic/architecture/data-layer/offline-first) and [Android privacy checklist](https://developer.android.com/privacy-and-security/about)
- [OWASP MASVS](https://mas.owasp.org/MASVS/02-Frontispiece/)
- [AWS Well-Architected Framework](https://docs.aws.amazon.com/wellarchitected/2025-02-25/framework/the-pillars-of-the-framework.html)
- [Google SRE service-level objectives](https://sre.google/sre-book/service-level-objectives/) and [release engineering](https://sre.google/sre-book/release-engineering/)
- [OpenTelemetry concepts and signals](https://opentelemetry.io/docs/concepts/observability-primer/)
- [Kubernetes probes](https://kubernetes.io/docs/concepts/workloads/pods/probes/)
- [PostgreSQL SQL, isolation, indexes, and EXPLAIN documentation](https://www.postgresql.org/docs/current/sql.html)
- [Kotlin classes and inheritance](https://kotlinlang.org/docs/classes.html), [interfaces](https://kotlinlang.org/docs/interfaces.html), [sealed types](https://kotlinlang.org/docs/sealed-classes.html), and [delegation](https://kotlinlang.org/docs/delegation.html)
- [RISC-V ratified ISA specifications](https://docs.riscv.org/reference/isa/unpriv/unpriv-index.html)
- [Google engineering code-review practices](https://google.github.io/eng-practices/review/)

