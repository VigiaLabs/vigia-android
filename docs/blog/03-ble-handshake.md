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
