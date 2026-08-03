# Engineering VIGIA Mobile — blog series (source drafts)

Markdown source for the "Engineering VIGIA Mobile" series, written to match the voice of
the existing "Engineering SAGE" series on [ridingbluewaves.hashnode.dev](https://ridingbluewaves.hashnode.dev).
Edit here; these are the source of truth for the writing.

| File | Post |
|---|---|
| [00-overview.md](00-overview.md) | Overview — an in-vehicle AI, hands-free, offline-tolerant, paired to a road sensor |
| [01-multi-module-architecture.md](01-multi-module-architecture.md) | Ep 1 — Why a phone app has nine modules |
| [02-hands-free-voice-loop.md](02-hands-free-voice-loop.md) | Ep 2 — A conversation that survives the driver never touching the screen |
| [03-ble-handshake.md](03-ble-handshake.md) | Ep 3 — Why the phone and the Pi never share a secret |
| [04-two-key-hierarchies.md](04-two-key-hierarchies.md) | Ep 4 — Two identities, two key hierarchies, no secrets in the APK |
| [05-resilience-and-alerts.md](05-resilience-and-alerts.md) | Ep 5 — Never drop a token, always deliver the warning |

**Note on scope:** these posts describe the architecture and the *design intent* of the
security model (hardware-backed device identity, hardware-wrapped wallet key, no
third-party secrets in the APK). They deliberately do not claim end-to-end security
guarantees, and they do not surface the open items tracked in
[`docs/design/VIGIA2_V2.md`](../design/VIGIA2_V2.md) — resolve those before making any
security-guarantee claims publicly.
