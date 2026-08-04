# Episode 3: Why the phone and the Pi never share a secret

*How a "share an HMAC key" pairing design turned out to be cryptographically impossible on hardware-backed keys, and pushed us to a proper asymmetric handshake.*

The phone has to trust the Raspberry Pi bolted to the windshield. When that Pi says "there is a pothole at these coordinates" or streams a road-roughness reading, the app acts on it — grounds an answer in it, forwards it, rewards it. So the Bluetooth link between them cannot just be "connected." It has to be *authenticated*: the phone needs to know it is talking to the real, pinned Pi and not some other device broadcasting the same service. This post is about how we got that wrong the first time, and what the failure taught us.

This is Episode 3 of 5 in the Engineering VIGIA Mobile series. The full system overview is in the master post.

## The first design: a shared HMAC key

The obvious pairing scheme is symmetric. Both devices hold the same secret key; to authenticate, one sends a challenge, the other returns an HMAC of it under the shared key, and you check it matches. It is simple, fast, and it is what most "just make it secure enough" BLE designs reach for.

We planned exactly this. Then we tried to implement it against the phone's hardware-backed key and discovered it could not be built at all.

## Why it was impossible, not just hard

The whole point of putting a key in the Android Keystore — ideally in StrongBox, the dedicated secure element — is that the key is **non-exportable**. A `PURPOSE_SIGN` key generated there can sign things, but its raw bytes never leave the secure hardware. Not to the app, not to the developer, not to anyone. That non-exportability is the security property you are paying for.

Symmetric HMAC needs the exact opposite. For both the phone and the Pi to compute the same HMAC, they must both hold the same key bytes. But the phone's key bytes are, by hardware design, unreadable — there is no way to hand a copy to the Pi. You cannot share a secret that the hardware exists specifically to keep unshareable. The symmetric design was not merely awkward; it was ruled out by the property that made the key worth using in the first place.

That is the moment the design got better, because the constraint forced the right answer.

## The asymmetric handshake we ended up with

If the two sides cannot share a secret, they should not try to. So the link uses public-key cryptography, where each side keeps its own private key and only ever exchanges public ones. The connection climbs a fixed pipeline — scan for the Pi by address, connect, negotiate a larger MTU, complete Bluetooth's own secure pairing — and then runs a mutual challenge-response on top:

- The Pi sends a **CHALLENGE**: a fresh random nonce, its P-256 public key, and an ECDSA signature over them.
- The phone replies with a **RESPONSE**: its own nonce, its public key, and a signature over both nonces and its key.
- Both sides independently derive a **session key** — `HKDF-SHA256` over the ECDH shared secret, salted with both nonces — and the Pi sends a **CONFIRM** that is an HMAC under that derived key.

Two things fall out of this that the symmetric design could never have given us. First, no shared secret ever exists to be leaked; each side proves possession of its private key without revealing it. Second, the session key is *derived*, not transmitted — it is computed independently on both ends from the ECDH exchange, so it never crosses the air at all. The fresh nonces on every connection mean a captured handshake can't be replayed later.

## Pinning the Pi so a stranger can't impersonate it

Authentication is only meaningful if the phone knows *which* public key to expect. Anyone can generate a P-256 keypair and sign a challenge; what makes the Pi *this* Pi is that the phone already holds its public key, captured from a QR code during the one-time pairing. So the phone verifies the CHALLENGE signature against that pinned key. A device that signs with any other key fails the check — it can complete a Bluetooth connection, but it cannot become *bound*, and an unbound peer's telemetry never enters the app. Pinning is what turns "a device is talking to me" into "the device I trust is talking to me."

Once bound, the phone confirms the default stream mode and the Pi's telemetry characteristic starts pushing frames, each arriving on an authenticated channel.

## Takeaway

The best decision in this subsystem was one we were forced into. A symmetric shared-key handshake is the intuitive design, and it is quietly incompatible with the entire reason to use hardware-backed keys — you cannot share a secret the hardware refuses to export. Being blocked pushed us onto an asymmetric handshake that is strictly better: no shared secret to leak, a session key that is derived rather than sent, and a pinned identity that a rogue device cannot forge. Sometimes the platform constraint is not in your way; it is pointing at the correct design.

The code is open at [github.com/VigiaLabs/vigia-android](https://github.com/VigiaLabs/vigia-android).

*Engineering VIGIA Mobile · Episode 3 of 5 — Previous: Episode 2. Next: Episode 4, Two identities, two key hierarchies.*

---

## 🎓 CS Fundamentals — study companion

*This is a **Cryptography** episode focused on **key exchange** (Diffie-Hellman / ECDH), which complements the *signature* crypto from the backend series. Plus **Computer Networks** (BLE/GATT). Key-exchange questions are classic security-interview territory.*

### Cryptography — key agreement & mutual authentication

- **The impossibility that forced the design.** You cannot do a symmetric handshake (shared HMAC key) with a **non-exportable** hardware key — the hardware refuses to hand out the bytes both sides would need. This is the crux: a security *feature* (non-extractability) rules out symmetric crypto and forces an **asymmetric** design (which is safer anyway — no shared secret to leak).
- **Diffie-Hellman / ECDH key exchange.** ECDH lets two parties who have only exchanged **public** keys independently compute the *same* shared secret, which never crosses the wire. Each side computes `shared = ECDH(my_private, their_public)`; the math guarantees both get the same value, and an eavesdropper who saw both public keys still can't derive it. **The session key is derived, not transmitted** — the single most important property to state.
- **Key derivation (HKDF).** You don't use the raw ECDH output as a key; you run it through **HKDF** (HMAC-based Key Derivation Function) with a salt (both nonces) to produce a uniform, context-bound session key. KDFs turn a shared secret into good key material.
- **Mutual authentication + nonces.** Both sides sign a **challenge** containing fresh random **nonces** (ECDSA), proving possession of their private keys *and* that this is a live exchange, not a replay. Fresh nonces per connection = replay resistance.
- **Key pinning / trust-on-first-use.** The phone verifies the Pi's signature against a public key **pinned from a QR code** during pairing. Pinning turns "some device is signing" into "the device I already trust is signing" — the defence against a rogue peer / MITM. (Same idea as certificate pinning in TLS.)
- **Forward secrecy (bonus).** Deriving a fresh session key per connection from ephemeral exchange means compromising one session's key doesn't expose past/future sessions — the property called **forward secrecy**.

### Computer Networks
- **BLE GATT.** Bluetooth Low Energy exposes data as **GATT** characteristics (attributes you read/write/subscribe). The handshake runs as writes/notifications over these characteristics; MTU negotiation sizes the packets. Know: BLE = low-power, short-range, characteristic-based; the handshake is an application-layer protocol *on top* of BLE's own pairing.

**Interview Q&A.**
1. *Explain Diffie-Hellman / ECDH key exchange.* → Two parties exchange public keys and each computes the same shared secret from (own private, other's public); the secret never travels; an eavesdropper can't derive it.
2. *Why derive a session key with a KDF instead of using the DH output directly?* → Raw DH output isn't uniformly random or context-bound; HKDF produces proper key material salted with nonces.
3. *How do nonces prevent replay in a handshake?* → Fresh random values per session make a captured handshake invalid to replay.
4. *What is key pinning and what attack does it stop?* → Trusting a specific known public key (from QR/cert) so a rogue peer with a different key fails auth — stops MITM/impersonation.
5. *What is forward secrecy?* → Ephemeral per-session keys so a future key compromise doesn't decrypt past sessions.

### ⚖️ This vs That — the architecture decisions, and the roads not taken

| Decision | Alternatives | Why this choice |
|---|---|---|
| **Asymmetric ECDH + ECDSA handshake** | Symmetric shared-HMAC handshake | Symmetric needs both sides to hold the same key — *impossible* with a non-exportable hardware key, and a shared secret is a liability. Asymmetric proves identity without sharing anything. |
| **Derive the session key (never send it)** | Generate a key on one side and transmit it (encrypted) | Transmitting a key, even encrypted, puts it on the wire. ECDH derives the same key independently on both ends — it never crosses the air. |
| **Pin the Pi's key from the QR** | Trust any device advertising the service | Without pinning, a rogue Pi passes an "authenticated-looking" handshake (MITM). Pinning binds trust to a specific key captured out-of-band. |
| **Fresh nonces per connection** | Static challenge | Static challenges are replayable; fresh nonces give replay resistance and forward secrecy. |

**The one to defend:** *symmetric was impossible, so asymmetric — and it's better.* The standout story: **a hardware key's non-extractability (a feature) makes symmetric HMAC impossible, forcing an ECDH/ECDSA design where no secret is ever shared and the session key is derived, not transmitted.** The platform constraint pointed straight at the more secure protocol.
