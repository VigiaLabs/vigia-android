# Episode 4: Two key hierarchies—and the exact protection each one really provides

*A from-zero tour of Android Keystore and envelope encryption, followed by an audit of the words that
security write-ups misuse: non-exportable, hardware-backed, StrongBox, proof of possession and “no secrets.”*

An app that signs road data, manages a rewards wallet and calls paid services has several credential
classes. Treating them uniformly is a mistake. This post separates a Keystore device key, a Keystore-wrapped
software wallet key, Cognito bearer tokens, backend-only vendor secrets and public client configuration. It
also answers the sharper mobile-security question: not merely “where is the key?”, but “who can invoke it,
what exactly does it authorise, how is it backed up/rotated/revoked, and what evidence supports the claim?”

This is Episode 4 of 5 in the Engineering VIGIA Mobile series. The full system overview is in the [master post](https://ridingbluewaves.hashnode.dev/engineering-the-vigia-in-vehicle-ai-copilot).

## From zero: the Android Keystore, the TEE, and StrongBox

Start with the hardware. Many Android phones implement Keystore operations in a **Trusted Execution
Environment (TEE)**; some support **StrongBox**, a separate secure element with a stronger isolation
contract. These reduce key-extraction risk. They do not make a compromised app harmless: malware running
with the app's authority may still ask Keystore to perform allowed operations unless the key requires user
authentication, and implementation/security level must be inspected rather than assumed.

The **Android Keystore** is the API that lets an app generate/use keys without returning their raw material
to the app process. Depending on the device and key, implementation may be StrongBox, TEE or another
security level. “Non-exportable” means the key bytes are not returned; it does **not** mean an attacker who
can drive the unlocked app cannot request a signature. Purpose, authentication requirements, validity,
attestation and server-side abuse controls remain part of the design.

Two more from-zero terms you'll need:
- **Symmetric vs asymmetric keys.** Symmetric (AES) = one secret for encrypt and decrypt. Asymmetric (EC keypairs) = a private key you keep and a public key you share; used for signatures and key agreement.
- **Key wrapping / envelope encryption.** Encrypting one key with *another* key. The inner "data key" can be any algorithm; the outer "wrapping key" stays in hardware. This is exactly how AWS KMS "data keys" work, and it's the escape hatch when the hardware can't hold your algorithm natively (below).

## The device identity key: hardware, non-exportable, by design

The current BLE identity is an elliptic-curve **P-256** key generated through Android Keystore, with
StrongBox requested and fallback when StrongBox is unavailable. Code uses the same long-lived key for
ECDSA signing and ECDH agreement and does not require user authentication. We can accurately say the app
does not receive the raw private-key encoding. We cannot yet claim every supported phone implements it in
TEE hardware because the app does not inspect/report `KeyInfo.securityLevel`, and using a static ECDH key
means the protocol does not gain forward secrecy merely by deriving a new session key.

This is stronger against raw-key extraction than a software PKCS#8 blob. It is not absolute device
identity by itself: an attacker who can invoke the app's key may still sign, backup/restore and reinstall
need defined semantics, server registration/rotation/revocation must bind the public key, and attestation is
a separate decision. Production code records the measured security level and designs authorization around
what the key is permitted to do, not just where its bytes live.

## The wallet key: software, because the platform gives us no choice

The wallet is a different story. The implementation generates an **Ed25519** keypair using the software
JCA provider because its current Android Keystore/device support target does not generate/use it as a
non-exportable Keystore signing key. Algorithm support evolves, so production must test the supported API/OEM
matrix rather than preserve this statement forever.

The code uses **envelope encryption**: generate Ed25519 in software, AES-GCM-encrypt its PKCS#8
private encoding with an Android Keystore wrapping key, and persist ciphertext. This protects the blob
at rest from an attacker who cannot invoke the originating Keystore key. Calling the AES key
“hardware-resident” requires measured `KeyInfo` evidence. Android backup is currently enabled with
template rules, so the encrypted private blob may be copied; it must be explicitly excluded even if a
different device normally cannot unwrap it.

The honest cost: to sign, the app must decrypt the private key into ordinary memory for the duration of the operation. Unlike the P-256 device key, the raw Ed25519 key *does* briefly exist in software at generation and on each signature. We treat that as a **known, bounded exposure** — protect the ciphertext in hardware, minimise and account for the window when the plaintext is live — rather than something to paper over. When the platform can't give you a hardware guarantee, the right move is to state the boundary of what you actually have, not to claim the guarantee you don't.

## What the keys are *for*: proving possession without a shared secret

The intended pattern is proof of possession: sign a canonical, versioned request containing method,
resource, body hash, timestamp/nonce and account/device context, and verify it server-side. A signature
alone is replayable if it is not bound to freshness and exact intent. The app also uses Cognito bearer
tokens for user identity, so “there is no bearer token” would be false; user authorization and device
possession solve different problems and may both be required. The current device-claim signature is empty,
which is why the production spec treats ownership as stop-ship.

## The secrets that aren't there at all

The third category is paid-vendor credentials. **The strongest thing you can do with a vendor secret is
not ship it.** Paid-service secrets should remain behind the backend. Client identifiers and Stripe
publishable keys may legitimately be public configuration; calling every build value a secret creates the
wrong protection model. Production validation must still fail when required public configuration is empty
or points at an unsafe endpoint.

From zero, the rule underneath: **anything shipped in an APK is extractable.** An APK is a zip; strings, resources, and even obfuscated constants can be pulled out with `apktool`/`jadx`. Obfuscation (R8) only *delays* a determined attacker. So a client should hold *no* credential it doesn't strictly need — and the ones it does need (its own identity) should be signatures proving possession, not secrets that grant access.

## From zero: how the app authenticates itself

The app uses **AWS Amplify/Cognito** for user identity and receives a bearer token for backend calls.
The crucial audit finding is that a production Amplify configuration failure currently causes Hilt to bind
simulated demo auth. That is a fail-open identity path, not resilience. Demo auth must live only in demo
source sets; prod initialisation failure must block safely and observably. User identity (Cognito token)
and device/key proof are distinct and may be combined by the server's authorization policy.

Transport is protected by a per-flavor **`network_security_config.xml`** — the prod configuration disallows cleartext and constrains trust anchors; the demo flavor keeps a cleartext allowance for local testing, which must never reach production.

## The production edge: what "hardened" adds

From zero, being honest about the road to production (this app's foundation is strong; these are the deliberate next steps in our hardening plan, not claims of done):
- **A threat-model decision on certificate/public-key pinning.** If adopted: backup pins, overlap,
  rotation, expiry, telemetry and recovery. Platform TLS trust is operationally safer than a naive pin.
- **Play Integrity API** attestation attached to minting-eligible telemetry, so the backend can reject an emulator or a tampered app before it earns rewards (paired with backend spoof-slashing).
- **CI secret scanning** (gitleaks) and dependency-vulnerability scanning, so no key is ever committed and no known-CVE library ships.
- **R8** obfuscation with reviewed keep-rules for the release build.

## Takeaway

Different credentials need different threat models. The BLE identity is generated through Keystore and is
non-exportable to the app, but its hardware security level and invocation-abuse posture need evidence. The
wallet is a software Ed25519 private key encrypted at rest by a Keystore AES key and exposed in process while
signing. Vendor secrets belong on the backend; public client configuration is not a secret. Cognito bearer
tokens authorise the user, while signed request proofs bind a device/key and require freshness. Precision is
the security feature: never promote “encrypted at rest” into “never leaves the TEE.”

The code is open at [github.com/VigiaLabs/vigia-android](https://github.com/VigiaLabs/vigia-android).

*Engineering VIGIA Mobile · Episode 4 of 5 — Previous: Episode 3. Next: Episode 5, From best-effort alerts to a durable warning inbox.*

---

## 🎓 CS Fundamentals — study companion

*This is a **Security + OS** episode on key storage: trusted execution environments, key wrapping, non-extractable keys, secrets management, and mobile auth from zero. "Where do you store secrets on a device?" is a sharp security-interview question.*

### Security / OS — key storage & trust
- **TEE / StrongBox / Keystore are related, not synonyms.** Keystore is the API; KeyMint may implement a
  key in StrongBox, TEE or another security level. Non-exportability resists extraction, while a compromised
  authorised app may still invoke the key. Inspect `KeyInfo` and decide whether user authentication/attestation
  is required.
- **The honest limit: not every algorithm is hardware-native.** The wallet uses **Ed25519**, which the Keystore can't hold as a hardware key. So the design does the next-best thing and *says so*: generate Ed25519 in software, then **key-wrap** it with a TEE-resident AES-256-GCM key and store only ciphertext. At rest it's hardware-protected; the honest cost is a brief in-memory exposure at signing time. **Knowing and stating the boundary of your guarantee is the mark of real security engineering.**
- **Key wrapping / envelope encryption.** Encrypt one key with another. It protects stored key material but
  necessarily exposes a software signing key during generation/unwrap/use, and backup rules still matter.
- **Proof of possession, not shared secrets.** Every upload / wallet op is *signed*; the server verifies the public key. No bearer token to steal — **possession of the private key is the credential, proven by signing.** Why signatures beat API keys for device identity.
- **Secrets management: don't ship the secret.** The strongest protection for third-party keys is that they're **not in the APK at all** — the app calls a backend proxy that holds them server-side and authenticates the app via its own token. An APK is extractable; obfuscation only delays.
- **User vs device identity.** Cognito token (via Credential Manager) = *who the user is*; hardware/wrapped keys = *device/wallet signing identity*. Different problems, different mechanisms. TLS + `network_security_config` protects transport.

**Interview Q&A.**
1. *What is a TEE / StrongBox and why use it?* → Isolate key material/operations from the app/OS to reduce
   extraction risk. Then state the limit: authorised invocation may remain possible and hardware level is measured.
2. *You must use an algorithm unavailable as a non-exportable Keystore key—what now?* → Re-evaluate the
   algorithm/device matrix; if retained, generate in software, wrap with a Keystore key, exclude backup,
   minimise plaintext lifetime and state clearly that signing occurs in app memory.
3. *What is envelope encryption / key wrapping?* → Encrypt a data key with a master key held in hardware/KMS; store the wrapped key; unwrap only when needed.
4. *Why are signatures better than API keys for device identity?* → No shared secret to steal; proof-of-possession proves identity without transmitting the credential.
5. *Where should third-party API keys live in a mobile app?* → Not in the app — behind a backend proxy; the app authenticates to your backend, which holds the vendor secrets.
6. *Why isn't obfuscation real secret protection?* → An APK is extractable; R8 only delays a determined attacker.
7. *Non-exportable vs non-usable by an attacker?* → Different properties. A key may be impossible to export
   while malware can still ask the authorised app/Keystore to sign; user authentication and server policy matter.
8. *How do backup and device transfer change the threat model?* → Wrapped blobs, tokens and pairing state may
   leave the device unless explicitly excluded; destination inability to unwrap is defence-in-depth, not a backup policy.

### ⚖️ This vs That — the architecture decisions, and the roads not taken

| Decision | Alternatives | Why this choice |
|---|---|---|
| **Keystore P-256 for device identity** | Software-stored identity key | Non-exportability reduces extraction risk; measure security level and control authorised use, rotation and revocation. |
| **Software Ed25519, Keystore-wrapped** | Supported non-exportable algorithm; plaintext | Current compatibility choice protects the stored blob, not signing-time memory; re-evaluate support and exclude backup. |
| **No third-party keys in the APK (backend proxy)** | Ship the Sarvam/cloud key in the app (maybe obfuscated) | Any key in an APK is extractable; obfuscation only delays. Not shipping it is the only real protection. |
| **Sign every request (proof of possession)** | A bearer API token in the app | A stolen bearer token grants impersonation; a signature proves possession without exposing the key. |
| **Cognito token for user auth (Credential Manager)** | Roll your own auth / ship long-lived secrets | Managed identity + a scoped token; the modern native sign-in replaces the deprecated Google Sign-In. |

**The one to defend:** *name key origin, storage, operation boundary, measured security level, authentication
requirement, backup, rotation/revocation and server policy separately.* “Android Keystore” is not a complete
threat model, and a wrapped software key is not a hardware signing key.

## Cross-repository production lens

Key protection is one layer of identity. The [engineering knowledge pack](../engineering-knowledge/README.md)
and [cross-repository audit](../engineering-knowledge/vigia-cross-repo-audit.md) separate storage,
proof-of-possession, authorization, transport authentication, rotation, and recovery across Android,
Pi, and cloud. That is why the device-signature contract remains a release gate.

---

> 📚 **Prepping for placements or interviews?** This series doubles as a study track. The [**VIGIA Interview Prep Companion**](https://ridingbluewaves.hashnode.dev/the-vigia-interview-prep-companion) ties every post to coding-round patterns, system-design drills, and behavioral (STAR) answers — a full Microsoft/Amazon prep guide grounded in these projects.
