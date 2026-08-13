# Mobile production: lifecycle is a distributed-systems constraint

The current Android guidance recommends at least a UI layer and a data layer, with an optional domain layer. Modern Android apps commonly combine layered boundaries, unidirectional data flow, state holders, modularization, and offline-first data. The framework is a means; the release bar is truthful behaviour under lifecycle and network failure.

## Recommended shape

```text
Compose UI -> screen state holder/ViewModel -> workflow/policy (optional)
                                      -> repository -> local + remote data sources
```

State flows down and events flow up. UI entry points coordinate; they do not own durable data. Repositories combine sources and expose domain-shaped data. Keep Android framework types at the edge so policy can be tested on the JVM. Add a domain/use-case boundary when policy is cohesive or reused, not as a wrapper around every repository method.

Android modularization guidance emphasizes high cohesion, low coupling, strict visibility, testability, and ownership. Feature modules should be leaves where possible; core modules should not import UI features. Too many tiny modules create build/configuration overhead, while one giant module hides boundaries.

## Lifecycle and background work

Treat configuration change, process death, reboot, permission revocation, and Doze as different events. `remember` is not durable; a ViewModel is not a process database. Persist any checkpoint required to resume safely: pending claim, event ID/version, payment intent, partial assistant turn, or migration state.

Choose work by lifetime:

- active screen: lifecycle/ViewModel coroutine;
- short push handling: validate, persist, notify;
- deferrable durable sync: WorkManager with constraints and unique work;
- user-visible ongoing operation: correctly typed foreground service and notification;
- exact alarms: only for a justified user-visible use case and current platform policy.

Cancellation is normal. Bound BLE scans, HTTP, MQTT, audio, and TTS work with deadlines and ownership. Do not catch cancellation as a generic error.

## Offline-first and truthful degradation

Critical reads should come from local durable data immediately. Writes need an explicit queue/optimistic policy, conflict resolution, and retry semantics. Mark freshness and availability in the model. A missing location is not `(0, 0)`; unavailable telemetry is not a zero-valued sample; an offline ownership claim is not success.

For VIGIA, the durable alert inbox is the source of truth for pending notifications; MQTT/FCM/SharedFlow are delivery edges. The edge map/tile service remains a separate production workstream because a static/demo map pack is not a maintained offline data product.

## Privacy and security

Android's privacy guidance says to request the minimum permissions, minimize location, degrade gracefully when access changes, keep sensitive data out of logs, use internal storage/Keystore for secrets, and never commit API keys. OWASP MASVS provides a mobile-specific verification vocabulary covering storage, cryptography, authentication/authorization, network communication, platform interaction, code quality, and resilience against reverse engineering/tampering.

For high-risk paths, verify:

- authenticated identity and authorization are separate checks;
- transport encryption has hostname/client authentication where required;
- redirects use verified App Links and PKCE/state/nonce;
- device pairing binds the intended peer, fresh challenge, account ownership, and protocol version;
- release builds remove demo credentials and use the intended signing/attestation policy;
- backups and device transfer do not silently export secrets or authoritative state.

## Performance and release evidence

Measure release-like builds on physical devices: cold/warm startup, frame/jank, memory, battery/network, pairing duration, alert visibility, voice first-useful-audio, and P95/P99 request latency. Use Macrobenchmark/Baseline Profiles when the data justifies it. R8/minification is a separate candidate that needs smoke tests for reflection-heavy SDKs.

Release engineering is part of the product: reproducible builds, dependency update policy, static analysis, unit/integration/device tests, signed artifacts, staged rollout, crash/ANR gates, kill switches, and rollback/runbooks. “It compiles” is not a release criterion.

## VIGIA mobile status

`vigia2` already has a strong framework baseline: Kotlin, Compose, Hilt, Room, Flow/coroutines, multi-module Gradle conventions, explicit demo/prod flavours, and CI compile/test/lint gates. The next production gates are cross-repository device-signature protocol, MQTT client authentication, maintained map/tile source, workflow extraction, observability, release signing/staging, and migration/security tests.

