# Cloud infrastructure: operate the system you designed

The AWS Well-Architected Framework names six interacting concerns: operational excellence, security, reliability, performance efficiency, cost optimization, and sustainability. Treat them as a review matrix, not a checklist to tick once. Azure Functions, AWS Lambda/CDK, Postgres/pgvector, Kusto, MQTT, and containers can all be correct choices when their failure and ownership boundaries are explicit.

## The six-pillar review

| Pillar | Questions for VIGIA |
|---|---|
| Operational excellence | Who owns the service? What is the runbook, SLO, alert, deploy gate, and rollback? |
| Security | Which identity gets which action? Where are secrets, key rotation, audit logs, and tenant boundaries? |
| Reliability | What happens during a region/provider/network/queue/database failure? How is work replayed or reconciled? |
| Performance efficiency | What is the bottleneck and the percentile budget? Which work is synchronous, cached, batched, or edge-local? |
| Cost optimization | What is the unit cost per event/query/device? What is the retention and scale-down policy? |
| Sustainability | Can compute, storage, model size, and radio usage be reduced without violating the SLO? |

## Event-driven and serverless boundaries

Serverless removes server management, not distributed-systems semantics. A function can be retried, run concurrently, time out after a provider commit, or receive an old event. Use stable event IDs, idempotent writes, bounded execution, explicit DLQs, schema/version compatibility, and a replay tool. Keep long-running/model-heavy work in a worker or service with a measured concurrency policy.

Cloud APIs should distinguish authentication, authorization, validation, and business decision. Use least-privilege IAM and short-lived credentials. Keep secrets in a managed secret/key service, rotate them, and ensure logs and traces redact tokens, payment data, precise location, and raw payloads. Network TLS is necessary but not sufficient for device ownership; bind device identity, challenge freshness, authorization, and revocation.

## Infrastructure as code and environments

Use CDK/Terraform/Bicep (or an equivalent) as the source of truth for infrastructure. Review plans/diffs, pin or constrain provider versions, separate environment state, and make destructive changes explicit. Promote the same immutable application artifact through dev, staging, and production; do not rebuild different code for each environment. Configuration should be validated at startup and fail closed when a production dependency is missing.

Backups need encryption, access control, retention, restore drills, and an RPO/RTO. A local export of DynamoDB records is not a disaster-recovery plan until the restore path and authorization have been tested.

## Observability and health

OpenTelemetry is a vendor-neutral framework for traces, metrics, logs, and baggage. Instrument the request/event ID and propagate context across HTTP, queues, MQTT, and workers. Emit structured events at boundaries and sample high-cardinality data carefully. Define SLIs from user outcomes and alert on symptoms, not only CPU.

For containerized services, startup, readiness, and liveness are different probes: startup protects slow initialization; readiness controls traffic; liveness restarts a stuck process. Health endpoints must not report “healthy” while a required dependency is known to be unusable unless the endpoint's contract explicitly says it is a degraded mode.

## Delivery and change management

Google's public SRE guidance treats release engineering as a self-service, automated, high-velocity discipline that starts early. A VIGIA release pipeline should include:

- format/lint/type/static analysis and dependency/security scans;
- unit, contract, integration, migration, replay, and load tests;
- infrastructure synthesis/plan and policy checks;
- signed, reproducible artifacts with provenance;
- staged/canary rollout, SLO/error-budget gates, feature flags, and kill switches;
- rollback plus data/schema compatibility and reconciliation runbooks.

## VIGIA cloud map

- `vigia-amazon`: TypeScript workspaces, AWS CDK, Lambda/backend/frontend/contracts, and a Rust protocol package; strong separation and tests, but no repository-level CI workflow was found in this snapshot.
- `vigia-public`: Next.js/React, LangGraph, Bedrock/AWS SDKs, Postgres/pgvector, SQLite edge fallback, and release/security scripts; it needs repeatable CI, contract tests, and production SLO/trace evidence.
- `vigia-functions-orchestrator`: Azure Functions with `routes`, `core`, `infra`, and `agents`, Key Vault/Confidential Ledger/Kusto integrations, idempotency and audit flows; it needs automated tests, CI, and operational telemetry.
- `vigia-raspi`: edge compute with a cloud/event ingress path; it needs a production-only signing/TLS posture, fleet update/recovery policy, and hardware-in-the-loop gates.

