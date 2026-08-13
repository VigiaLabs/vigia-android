# Episode 3: Designing a BLE trust protocol—and auditing the gaps between protocol and code

*Why a long-lived pre-shared HMAC key conflicts with a non-exportable signing key, how an authenticated
ephemeral ECDH protocol should work, and why a sound whiteboard protocol is still insecure if QR binding,
server ownership, deadlines, replay state or key provisioning are incomplete in code.*

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

We planned this around a signing key that should remain non-exportable. That specific key cannot also be
copied out and used as a pre-shared HMAC secret. The broader statement needs precision: Android Keystore can
support some symmetric keys for local cryptographic operations; what is impossible is distributing the raw
bytes of a non-exportable device key to the Pi while preserving non-exportability.

## Why it was impossible, not just hard

The point of generating a key in the **Android Keystore** is that its key material does not enter the app
process. Depending on device and algorithm, operations may be implemented in the TEE, StrongBox or another
security level; code must inspect `KeyInfo` rather than assume StrongBox. A non-exportable signing key can
sign a transcript, but the app cannot read its bytes and provision the same bytes to the Pi.

Symmetric HMAC needs the exact opposite. For both the phone and the Pi to compute the same HMAC, they must both hold the *same key bytes*. But the phone's key bytes are, by hardware design, unreadable — there's no way to hand a copy to the Pi. **You cannot share a secret that the hardware exists specifically to keep unshareable.** The symmetric design wasn't merely awkward; it was ruled out by the property that made the key worth using in the first place.

That's the moment the design got better, because the constraint forced the right answer.

## From zero: symmetric vs asymmetric, and key agreement

Two families of cryptography, and the distinction is interview-critical:
- **Symmetric** (AES, HMAC): one shared key does both sides. Fast, but *both* parties must hold the same secret — a distribution and leakage problem.
- **Asymmetric / public-key** (ECC, RSA): each party has a *keypair* — a private key it never reveals and a public key it hands out freely. You can verify a signature or agree on a secret without ever sharing a private key.

The magic primitive here is **Diffie-Hellman key agreement**, in its elliptic-curve form **ECDH**. Two parties exchange only *public* keys, and each independently computes the *same* shared secret from (its own private key, the other's public key). The shared secret is **never transmitted** — it's derived on both ends — so an eavesdropper who saw both public keys still can't compute it. That single property is what makes the asymmetric design strictly safer than the symmetric one we started with.

## The authenticated handshake the production design requires

If the two sides can't share a secret, they shouldn't try to. So the link uses public-key crypto. The connection climbs a fixed pipeline — scan/associate the Pi, connect, negotiate a larger MTU, complete Bluetooth's own pairing — and then runs a mutual challenge-response on top:

- The Pi sends a **CHALLENGE**: a fresh random **nonce**, its **P-256** public key, and an **ECDSA** signature over them.
- The phone replies with a **RESPONSE**: its own nonce, its public key, and a signature over both nonces and its key.
- Both sides independently derive a **session key** — `HKDF-SHA256` over the ECDH shared secret, salted with both nonces — and the Pi sends a **CONFIRM** that is an HMAC under that derived key.

Two things improve over a long-lived pre-shared key: no **pre-provisioned shared secret** needs to be copied
between devices, and the per-session secret is derived rather than transmitted. A shared session secret does
exist in both endpoints' memory—that is the point of ECDH—and endpoint compromise can expose it. Nonces help
with freshness only when signatures/MACs bind the complete transcript and the implementation tracks/rejects
reuse. Forward secrecy requires fresh ephemeral ECDH private keys, erasure after use and authentication that
does not replace them with a reused static DH key.

Two supporting primitives worth naming from zero:
- **ECDSA** is the *signature* scheme — it proves "the holder of this private key signed this exact message," which is how each side authenticates.
- **HKDF** (HMAC-based Key Derivation Function) turns the raw ECDH output — which isn't uniformly random and shouldn't be used directly — into proper, context-bound key material, salted here with both nonces so the session key is unique per connection.

## Pinning the Pi so a stranger can't impersonate it

Authentication is only meaningful if the phone knows *which* public key to expect. Anyone can generate a P-256 keypair and sign a challenge; what makes the Pi *this* Pi is that the phone already holds its public key, captured from a **QR code** during the one-time pairing. So the phone verifies the CHALLENGE signature against that **pinned** key. A device signing with any other key fails the check — it can complete a Bluetooth connection but cannot become *bound*, and an unbound peer's telemetry never enters the app.

This is out-of-band key provisioning/pinning, not automatically trust-on-first-use. The QR itself must be
authentic—ideally a versioned payload signed by a manufacturer/provisioning authority—or an attacker can
replace both device and QR. Pinning turns “some peer proved possession of some key” into “the peer proved
possession of the key authorised by this trusted enrolment statement.”

The current pairing path does not yet earn that guarantee. It sends an empty `deviceSig`, CDM filters by a
`VIGIA.*` name rather than the QR's hardware identity, broad claim errors can become local success, and BLE
scan/bond have unbounded waits. Connection errors are swallowed before the foreground service can retry, and
the service start helper currently has no caller. These are not cosmetic TODOs: ownership, peer identity and
liveness form one security property.

The required state machine is explicit and durable at appropriate checkpoints:

```text
Unpaired -> QrValidated -> CandidateSelected -> PeerAuthenticated
         -> ServerClaimPending -> Claimed
errors: WrongPeer, LinkTimeout, SignatureRejected, Unauthorized,
        AlreadyClaimed, OfflineRestricted
```

Every BLE operation has a deadline and deterministic cleanup. A 401/403/409 or invalid signature never
becomes offline success. If product requires offline association, it becomes `OfflineRestricted`: no reward,
upload, ownership change or cash-out until the server claim succeeds idempotently.

## Takeaway

The useful lesson is not “we used ECDH, therefore secure.” It is that a protocol's security claim is the
conjunction of key provenance, authenticated transcript, peer/QR binding, freshness, state machine, server
ownership, timeouts, key erasure, rotation/revocation and tests. The platform constraint correctly pushed the
design away from distributing a long-lived shared key. The audit then showed that an elegant protocol
description can still outrun its implementation. Production engineering closes that evidence gap.

The code is open at [github.com/VigiaLabs/vigia-android](https://github.com/VigiaLabs/vigia-android).

*Engineering VIGIA Mobile · Episode 3 of 5 — Previous: Episode 2. Next: Episode 4, Two identities, two key hierarchies.*

---

## 🎓 CS Fundamentals — study companion

*This is a **Cryptography** episode focused on **key exchange** (Diffie-Hellman / ECDH), complementing the *signature* crypto from the backend series, plus **Computer Networks** (BLE/GATT) and the **Android Bluetooth stack** from zero. Key-exchange questions are classic security-interview territory.*

### Cryptography — key agreement & mutual authentication
- **The constraint that changed the design.** A non-exportable signing key cannot simultaneously be copied
  to the Pi as a pre-shared HMAC key. This does not make all symmetric Keystore use impossible; it rules out
  distributing that protected key's bytes.
- **Diffie-Hellman / ECDH.** Two parties exchange only public keys and each computes the *same* shared secret from (own private, other's public); it never crosses the wire, and an eavesdropper can't derive it. **The session key is derived, not transmitted** — the single most important sentence.
- **Key derivation (HKDF).** Don't use raw ECDH output as a key; run it through HKDF with a salt (both nonces) for uniform, context-bound key material.
- **Signatures + nonces (ECDSA).** Both sides sign a challenge containing fresh nonces, proving private-key possession *and* liveness (not a replay). Fresh nonces per connection = replay resistance.
- **Provisioned key pinning.** Verify the peer against a QR key/fingerprint whose own authenticity is
  established. TOFU is a different policy: accepting the first key seen without prior authority.
- **Forward secrecy.** Requires fresh ephemeral DH keys that are erased. Merely deriving a fresh labelled
  session key from a static private key does not guarantee it.

### Computer Networks & the Android BLE stack
- **BLE + GATT.** Low-power, short-range; data as **GATT characteristics** (read/write/notify) inside services; **MTU** negotiation sizes packets. The handshake is an application-layer protocol *on top* of BLE's own pairing.
- **Android specifics.** `BLUETOOTH_SCAN`/`BLUETOOTH_CONNECT` runtime permissions (scan historically tied to location); the callback-based `BluetoothGatt` API wrapped into coroutine `Flow`s; **Companion Device Manager** for scoped, OS-mediated device association.

**Interview Q&A.**
1. *Explain Diffie-Hellman / ECDH.* → Exchange public keys; each computes the same shared secret from (own private, other's public); the secret never travels; an eavesdropper can't derive it.
2. *Why a KDF instead of the raw DH output?* → Raw output isn't uniform or context-bound; HKDF (salted with nonces) produces proper key material.
3. *How do nonces prevent replay?* → Fresh random values per session make a captured handshake invalid to replay.
4. *What is key pinning and what attack does it stop?* → Binding identity to an already-authorised public
   key. Also explain how the pin is authenticated, rotated and revoked; an attacker-controlled QR pins the attacker.
5. *What is forward secrecy?* → Compromise of a long-term identity key does not recover old session keys,
   normally because ephemeral DH private values were unique and erased.
7. *Does ECDH authenticate the peer?* → No. Unauthenticated ECDH is vulnerable to MITM; signatures/MACs
   must bind identities, both ephemeral keys, nonces, protocol version/roles and context.
8. *How do you test a protocol implementation?* → Known-answer/interoperability vectors, transcript mutation,
   replay/reorder/duplication, wrong identity, timeout/cancellation, state-machine and key-rotation tests.
6. *What is GATT?* → BLE's attribute model: services containing readable/writable/subscribable characteristics.

### ⚖️ This vs That — the architecture decisions, and the roads not taken

| Decision | Alternatives | Why this choice |
|---|---|---|
| **Asymmetric ECDH + ECDSA handshake** | Symmetric shared-HMAC handshake | Symmetric needs both sides to hold the same key — *impossible* with a non-exportable hardware key, and a shared secret is a liability. Asymmetric proves identity without sharing anything. |
| **Derive the session key (never send it)** | Generate a key on one side, transmit it encrypted | Transmitting a key, even encrypted, puts it on the wire. ECDH derives the same key independently on both ends — it never crosses the air. |
| **Pin the Pi's key from the QR** | Trust any device advertising the service | Without pinning, a rogue Pi passes an "authenticated-looking" handshake (MITM). Pinning binds trust to a key captured out-of-band. |
| **Fresh transcript-bound nonces** | Static challenge | Nonces contribute freshness/replay resistance when authenticated and checked; forward secrecy comes from ephemeral DH, not nonces. |
| **Companion Device Manager + Flow wrappers** | Raw open BLE scan + callback code | CDM is scoped and privacy-respecting; wrapping `BluetoothGatt` callbacks in Flow keeps the rest of the app clean. |

**The one to defend:** *the protected signing key cannot be exported as a shared HMAC secret, so use
authenticated ephemeral key agreement—and then prove every binding.* The senior answer explicitly says
ECDH creates a shared session secret in endpoint memory, ECDH alone is unauthenticated, forward secrecy
requires ephemeral keys, and QR/server ownership are part of the protocol's trust boundary.

## Cross-repository production lens

Pairing is only complete when the Android, Pi, and cloud implementations agree on one versioned,
authenticated ownership contract. The [engineering knowledge pack](../engineering-knowledge/README.md)
and [cross-repository audit](../engineering-knowledge/vigia-cross-repo-audit.md) make the open
`deviceSig`, replay, revocation, and compatibility-test work explicit rather than treating a local
handshake as production proof.

---

> 📚 **Prepping for placements or interviews?** This series doubles as a study track. The [**VIGIA Interview Prep Companion**](https://ridingbluewaves.hashnode.dev/the-vigia-interview-prep-companion) ties every post to coding-round patterns, system-design drills, and behavioral (STAR) answers — a full Microsoft/Amazon prep guide grounded in these projects.
