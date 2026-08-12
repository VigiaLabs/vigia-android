# Engineering VIGIA Mobile — blog series (source drafts)

Markdown source for the "Engineering VIGIA Mobile" series, written to match the voice of
the existing "Engineering SAGE" series on [ridingbluewaves.hashnode.dev](https://ridingbluewaves.hashnode.dev).
Edit here; these are the source of truth for the writing.

| File | Post |
|---|---|
| [00-overview.md](00-overview.md) | Overview — what an advanced Android prototype still needs before production |
| [01-multi-module-architecture.md](01-multi-module-architecture.md) | Ep 1 — ten modules, their benefits, and the boundaries that currently leak |
| [02-hands-free-voice-loop.md](02-hands-free-voice-loop.md) | Ep 2 — A conversation that survives the driver never touching the screen |
| [03-ble-handshake.md](03-ble-handshake.md) | Ep 3 — BLE trust protocol and the gaps between protocol and code |
| [04-two-key-hierarchies.md](04-two-key-hierarchies.md) | Ep 4 — the exact protection provided by each key hierarchy |
| [05-resilience-and-alerts.md](05-resilience-and-alerts.md) | Ep 5 — from best-effort transports to a durable warning inbox |
| [06-interview-prep-companion.md](06-interview-prep-companion.md) | Interview companion — CS fundamentals, coding patterns, design drills, and project stories |

**Status vocabulary:** posts use **implemented**, **partial**, **planned**, and **verified in release**
deliberately. Architecture intent is not described as shipped security/reliability. Open implementation
findings are summarised in [`ARCHITECTURE_HARDENING_SPEC.md`](../../ARCHITECTURE_HARDENING_SPEC.md) and
[`docs/design/VIGIA2_V2.md`](../design/VIGIA2_V2.md). Re-audit both code and public claims before publishing
future changes.
