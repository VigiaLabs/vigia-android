# Episode 3: Why the phone and the Pi never share a secret

*How a "share an HMAC key" pairing design turned out to be cryptographically impossible on hardware-backed keys and pushed us to a proper asymmetric handshake — with a from-zero tour of Bluetooth Low Energy, GATT, Android's Bluetooth permission model, and the key-agreement crypto underneath.*

The phone has to trust the Raspberry Pi bolted to the windshield. When that Pi says "there is a pothole at these coordinates" or streams a road-roughness reading, the app acts on it — grounds an answer in it, forwards it, rewards it. So the Bluetooth link between them can't just be "connected." It has to be *authenticated*: the phone needs to know it's talking to the real, pinned Pi and not some other device broadcasting the same service. This post is about how we got that wrong the first time, what the failure taught us — and, from zero, how BLE and the crypto actually work.

This is Episode 3 of 5 in the Engineering VIGIA Mobile series. The full system overview is in the [master post](https://ridingbluewaves.hashnode.dev/engineering-the-vigia-in-vehicle-ai-copilot).

## From zero: what BLE and GATT are

**Bluetooth Low Energy (BLE)** is the low-power, short-range radio protocol built for exactly this — a battery device (or a Pi) exchanging small bits of data with a phone. It's a different stack from "Classic" Bluetooth (which does audio/file transfer); BLE is optimized for tiny, infrequent payloads and long battery life.

The data model is **GATT (Generic Attribute Profile)**. A BLE peripheral exposes a tree of **services**, each containing **characteristics** — named attributes you can *read*, *write*, or *subscribe to* (`notify`). The Pi publishes a telemetry service with a characteristic that pushes frames; the phone subscribes and receives a notification per frame. Before any of that, the two negotiate an **MTU** (maximum transmission unit) — BLE's default packet is tiny (~20 usable bytes), so both sides agree on a larger one to fit a telemetry frame in fewer packets.

**On Android specifically**, three things bite newcomers:
- **Runtime permissions.** Since Android 12, BLE needs the `BLUETOOTH_SCAN` and `BLUETOOTH_CONNECT` runtime permissions (not just a manifest entry), and historically scanning was gated behind *location* permission because a BLE scan can reveal your location. You must request these at runtime and handle denial.
- **The callback-based API.** The framework `BluetoothGatt` API is old-school: you call `connectGatt()`, then results arrive on a `BluetoothGattCallback` (`onConnectionStateChange`, `onServicesDiscovered`, `onCharacteristicChanged`). We wrap that callback soup into coroutine `Flow`s in `:core:sensor` so the rest of the app consumes clean streams instead of nested callbacks.
- **Companion Device Manager (CDM).** Rather than a raw scan, Android offers CDM: the OS shows a system dialog to *associate* a specific companion device, which grants scoped, persistent access to it. Our pairing flow reads a QR from the Pi, associates via CDM, and stores the association — a cleaner, more privacy-respecting path than an open scan.

## The first design: a shared HMAC key

The obvious pairing scheme is symmetric. Both devices hold the same secret key; to authenticate, one sends a challenge, the other returns an HMAC of it under the shared key, and you check it matches. Simple, fast, and what most "just make it secure enough" BLE designs reach for.

We planned exactly this. Then we tried to implement it against the phone's hardware-backed key and discovered it couldn't be built at all.

## Why it was impossible, not just hard

The whole point of putting a key in the **Android Keystore** — ideally in **StrongBox**, the dedicated secure element — is that the key is **non-exportable**. A `PURPOSE_SIGN` key generated there can sign things, but its raw bytes never leave the secure hardware. Not to the app, not to the developer, not to anyone. That non-exportability is the security property you're paying for.

Symmetric HMAC needs the exact opposite. For both the phone and the Pi to compute the same HMAC, they must both hold the *same key bytes*. But the phone's key bytes are, by hardware design, unreadable — there's no way to hand a copy to the Pi. **You cannot share a secret that the hardware exists specifically to keep unshareable.** The symmetric design wasn't merely awkward; it was ruled out by the property that made the key worth using in the first place.

That's the moment the design got better, because the constraint forced the right answer.

## From zero: symmetric vs asymmetric, and key agreement

Two families of cryptography, and the distinction is interview-critical:
- **Symmetric** (AES, HMAC): one shared key does both sides. Fast, but *both* parties must hold the same secret — a distribution and leakage problem.
- **Asymmetric / public-key** (ECC, RSA): each party has a *keypair* — a private key it never reveals and a public key it hands out freely. You can verify a signature or agree on a secret without ever sharing a private key.

The magic primitive here is **Diffie-Hellman key agreement**, in its elliptic-curve form **ECDH**. Two parties exchange only *public* keys, and each independently computes the *same* shared secret from (its own private key, the other's public key). The shared secret is **never transmitted** — it's derived on both ends — so an eavesdropper who saw both public keys still can't compute it. That single property is what makes the asymmetric design strictly safer than the symmetric one we started with.

## The asymmetric handshake we ended up with

If the two sides can't share a secret, they shouldn't try to. So the link uses public-key crypto. The connection climbs a fixed pipeline — scan/associate the Pi, connect, negotiate a larger MTU, complete Bluetooth's own pairing — and then runs a mutual challenge-response on top:

- The Pi sends a **CHALLENGE**: a fresh random **nonce**, its **P-256** public key, and an **ECDSA** signature over them.
- The phone replies with a **RESPONSE**: its own nonce, its public key, and a signature over both nonces and its key.
- Both sides independently derive a **session key** — `HKDF-SHA256` over the ECDH shared secret, salted with both nonces — and the Pi sends a **CONFIRM** that is an HMAC under that derived key.

Two things fall out that the symmetric design never could: first, **no shared secret ever exists to be leaked** — each side proves possession of its private key without revealing it; second, the **session key is derived, not transmitted** — computed independently on both ends from the ECDH exchange, so it never crosses the air. The fresh nonces on every connection mean a captured handshake can't be replayed later.

Two supporting primitives worth naming from zero:
- **ECDSA** is the *signature* scheme — it proves "the holder of this private key signed this exact message," which is how each side authenticates.
- **HKDF** (HMAC-based Key Derivation Function) turns the raw ECDH output — which isn't uniformly random and shouldn't be used directly — into proper, context-bound key material, salted here with both nonces so the session key is unique per connection.

## Pinning the Pi so a stranger can't impersonate it

Authentication is only meaningful if the phone knows *which* public key to expect. Anyone can generate a P-256 keypair and sign a challenge; what makes the Pi *this* Pi is that the phone already holds its public key, captured from a **QR code** during the one-time pairing. So the phone verifies the CHALLENGE signature against that **pinned** key. A device signing with any other key fails the check — it can complete a Bluetooth connection but cannot become *bound*, and an unbound peer's telemetry never enters the app.

This is **key pinning** / trust-on-first-use, the same idea as certificate pinning in TLS: trust is bound to a *specific* known key captured out-of-band, so a rogue peer with a different key — a man-in-the-middle — fails authentication. Pinning is what turns "a device is talking to me" into "the device I trust is talking to me."

Once bound, the phone confirms the default stream mode and the Pi's telemetry characteristic starts pushing frames, each arriving on an authenticated channel.

## Takeaway

The best decision in this subsystem was one we were forced into. A symmetric shared-key handshake is the intuitive design, and it's quietly incompatible with the entire reason to use hardware-backed keys — you cannot share a secret the hardware refuses to export. Being blocked pushed us onto an asymmetric handshake that's strictly better: no shared secret to leak, a session key that's derived rather than sent, fresh nonces for replay resistance, and a pinned identity a rogue device can't forge. Sometimes the platform constraint isn't in your way; it's pointing at the correct design.

The code is open at [github.com/VigiaLabs/vigia-android](https://github.com/VigiaLabs/vigia-android).

*Engineering VIGIA Mobile · Episode 3 of 5 — Previous: Episode 2. Next: Episode 4, Two identities, two key hierarchies.*

---

## 🎓 CS Fundamentals — study companion

*This is a **Cryptography** episode focused on **key exchange** (Diffie-Hellman / ECDH), complementing the *signature* crypto from the backend series, plus **Computer Networks** (BLE/GATT) and the **Android Bluetooth stack** from zero. Key-exchange questions are classic security-interview territory.*

### Cryptography — key agreement & mutual authentication
- **The impossibility that forced the design.** You can't do a symmetric handshake (shared HMAC key) with a **non-exportable** hardware key — the hardware refuses to hand out the bytes both sides would need. A security *feature* (non-extractability) rules out symmetric crypto and forces an **asymmetric** design (safer anyway — no shared secret to leak).
- **Diffie-Hellman / ECDH.** Two parties exchange only public keys and each computes the *same* shared secret from (own private, other's public); it never crosses the wire, and an eavesdropper can't derive it. **The session key is derived, not transmitted** — the single most important sentence.
- **Key derivation (HKDF).** Don't use raw ECDH output as a key; run it through HKDF with a salt (both nonces) for uniform, context-bound key material.
- **Signatures + nonces (ECDSA).** Both sides sign a challenge containing fresh nonces, proving private-key possession *and* liveness (not a replay). Fresh nonces per connection = replay resistance.
- **Key pinning / TOFU.** Verify the Pi's signature against a public key **pinned from a QR** during pairing — the defence against a rogue peer / MITM. Same idea as TLS cert pinning.
- **Forward secrecy.** A fresh per-connection session key means compromising one session doesn't expose past/future ones.

### Computer Networks & the Android BLE stack
- **BLE + GATT.** Low-power, short-range; data as **GATT characteristics** (read/write/notify) inside services; **MTU** negotiation sizes packets. The handshake is an application-layer protocol *on top* of BLE's own pairing.
- **Android specifics.** `BLUETOOTH_SCAN`/`BLUETOOTH_CONNECT` runtime permissions (scan historically tied to location); the callback-based `BluetoothGatt` API wrapped into coroutine `Flow`s; **Companion Device Manager** for scoped, OS-mediated device association.

**Interview Q&A.**
1. *Explain Diffie-Hellman / ECDH.* → Exchange public keys; each computes the same shared secret from (own private, other's public); the secret never travels; an eavesdropper can't derive it.
2. *Why a KDF instead of the raw DH output?* → Raw output isn't uniform or context-bound; HKDF (salted with nonces) produces proper key material.
3. *How do nonces prevent replay?* → Fresh random values per session make a captured handshake invalid to replay.
4. *What is key pinning and what attack does it stop?* → Trusting a specific known public key (from QR/cert) so a rogue peer with a different key fails auth — stops MITM/impersonation.
5. *What is forward secrecy?* → Ephemeral per-session keys so a future key compromise doesn't decrypt past sessions.
6. *What is GATT?* → BLE's attribute model: services containing readable/writable/subscribable characteristics.

### ⚖️ This vs That — the architecture decisions, and the roads not taken

| Decision | Alternatives | Why this choice |
|---|---|---|
| **Asymmetric ECDH + ECDSA handshake** | Symmetric shared-HMAC handshake | Symmetric needs both sides to hold the same key — *impossible* with a non-exportable hardware key, and a shared secret is a liability. Asymmetric proves identity without sharing anything. |
| **Derive the session key (never send it)** | Generate a key on one side, transmit it encrypted | Transmitting a key, even encrypted, puts it on the wire. ECDH derives the same key independently on both ends — it never crosses the air. |
| **Pin the Pi's key from the QR** | Trust any device advertising the service | Without pinning, a rogue Pi passes an "authenticated-looking" handshake (MITM). Pinning binds trust to a key captured out-of-band. |
| **Fresh nonces per connection** | Static challenge | Static challenges are replayable; fresh nonces give replay resistance and forward secrecy. |
| **Companion Device Manager + Flow wrappers** | Raw open BLE scan + callback code | CDM is scoped and privacy-respecting; wrapping `BluetoothGatt` callbacks in Flow keeps the rest of the app clean. |

**The one to defend:** *symmetric was impossible, so asymmetric — and it's better.* The standout story: **a hardware key's non-extractability (a feature) makes symmetric HMAC impossible, forcing an ECDH/ECDSA design where no secret is ever shared and the session key is derived, not transmitted.** The platform constraint pointed straight at the more secure protocol.

---

> 📚 **Prepping for placements or interviews?** This series doubles as a study track. The [**VIGIA Interview Prep Companion**](https://ridingbluewaves.hashnode.dev/the-vigia-interview-prep-companion) ties every post to coding-round patterns, system-design drills, and behavioral (STAR) answers — a full Microsoft/Amazon prep guide grounded in these projects.
