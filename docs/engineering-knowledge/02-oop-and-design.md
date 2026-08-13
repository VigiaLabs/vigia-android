# OOP and software design: keep policy testable

Object-oriented design is not a requirement to create a class for every noun. It is a way to control state, behaviour, and dependencies. VIGIA uses Kotlin, Python, TypeScript, and C++; the transferable ideas are cohesion, coupling, ownership, substitutability, and explicit state transitions.

## The core vocabulary

- **Encapsulation:** keep invariants beside the state they protect; expose an operation, not a mutable representation.
- **Abstraction:** expose the capability a caller needs, not a framework or transport detail.
- **Polymorphism:** substitute implementations behind a stable contract when the behaviour genuinely varies (real vs demo, local vs remote, cloud vs edge).
- **Composition:** assemble small behaviours instead of building deep inheritance trees. Kotlin's native delegation is often a clearer alternative to implementation inheritance.
- **Immutability:** make values and events immutable where possible; it reduces aliasing and makes concurrency/replay easier to reason about.

SOLID is a review lens, not a quota. Single responsibility means one cohesive reason to change, not one method per class. Dependency inversion means policy does not depend directly on an SDK; it does not mean every concrete type needs an interface. Open/closed is useful for stable extension points, but speculative abstractions add coupling.

## Kotlin-specific tools

Kotlin's sealed classes/interfaces make finite state and error domains compiler-checkable. Use them for pairing/payment/connection states when the set is intentionally closed. Interfaces are good for capability boundaries; data classes are good for immutable values; delegation is useful when a wrapper should forward a contract without copying an inheritance hierarchy. Keep exported APIs small and default implementations private to a module.

Example shape:

```kotlin
sealed interface ClaimResult {
    data class Claimed(val deviceId: String) : ClaimResult
    data object OfflineRestricted : ClaimResult
    data object InvalidPeer : ClaimResult
    data class TransientFailure(val cause: Throwable) : ClaimResult
}
```

The caller must handle all cases. That is stronger than returning `null`, throwing a generic exception, or silently treating a transient failure as success.

## State machines over boolean soup

If a workflow has lifecycle, cancellation, retries, or mutually exclusive states, model it as a state machine. A transition should validate its current state, make one observable change, and define the side effect boundary. Use a generation/turn ID for concurrent voice work; use a claim ID and idempotency key for device ownership; use versioned events for alerts.

Safety and liveness are different:

- safety: an invalid peer is never marked paired;
- liveness: a valid pairing eventually completes or reaches a recoverable terminal state.

Tests should cover both transition tables and side-effect ordering. “The happy path works” covers neither.

## Concurrency ownership

Every mutable resource needs one owner and one cancellation policy. A repository may own its IO dispatcher and close its client; a ViewModel may own a screen workflow; a service may own a long-lived connection. Do not launch an unscoped coroutine from an object whose lifetime is unclear. Cancellation is a normal control signal and should be rethrown or propagated, not converted into a user-visible failure.

Types should be safe to call from the main thread: they either perform short work or move blocking work to the right executor internally. This keeps callers from having to guess a hidden threading precondition.

## Review questions that scale

Google's public code-review guidance asks reviewers to examine design, functionality, complexity, tests, naming, comments, style, and documentation. Apply the same order to VIGIA:

1. Which invariant does this type own?
2. Can a caller observe a half-written state?
3. What happens on duplicate delivery, cancellation, process death, and malformed input?
4. Can the policy be tested without Android, a network, or a GPU?
5. Does this change reduce or increase coupling between repositories?
6. Is the abstraction earning its maintenance cost?

Prefer a small change that improves code health over a perfect rewrite. Record non-obvious reasoning in an ADR, not in a comment that merely repeats the code.

## VIGIA design targets

- Extract cohesive voice, pairing, payment, and alert workflows from the high-fan-in copilot coordinator.
- Keep transport adapters (`MQTT`, `FCM`, `SSE`, BLE) at the edge; expose domain events and typed failures inward.
- Keep demo implementations compile-time selected and impossible to reach from production failure paths.
- Keep the durable inbox and migration logic independent of the UI lifecycle.
- Keep C++ real-time/edge loops explicit about ownership, memory allocation, queue capacity, and shutdown.

