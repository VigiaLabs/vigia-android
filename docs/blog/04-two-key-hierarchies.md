# Episode 4: Two identities, two key hierarchies, no secrets in the APK

*Why the device's identity key lives in hardware and can never be exported, why the wallet key can't, why the app ships with zero third-party credentials — and a from-zero tour of the Android Keystore, the TEE, envelope encryption, and how secrets management actually works on a phone.*

An app that signs road data on behalf of a device, manages a rewards wallet, and calls paid AI services has three different secrets to worry about, and the lazy answer — put them all in one place, protect them the same way — is wrong for every one of them. This post is about how VIGIA Mobile ended up with two deliberately different key hierarchies and zero API keys in the shipped binary, and about being honest where the platform forced a compromise. It also doubles as a **from-zero guide to on-device secret storage** — the single sharpest question in a mobile-security interview is "where do you keep secrets on a device, and why?"

This is Episode 4 of 5 in the Engineering VIGIA Mobile series. The full system overview is in the [master post](https://ridingbluewaves.hashnode.dev/engineering-the-vigia-in-vehicle-ai-copilot).

## From zero: the Android Keystore, the TEE, and StrongBox

Start with the hardware, because it's the foundation. Modern phones have a **Trusted Execution Environment (TEE)** — an isolated processor context (ARM TrustZone) that runs separately from the main OS, so even a fully compromised Android can't read into it. Some phones go further with a **StrongBox** secure element: a dedicated, tamper-resistant chip.

The **Android Keystore** is the API that lets your app generate and use keys *inside* that hardware. The defining property: a key generated in the Keystore can be **non-exportable** — you can ask it to *sign* or *decrypt*, but its raw private bytes never leave the secure hardware. Not to your app's memory, not to the developer, not to an attacker who fully owns the unlocked phone. That's the property you're buying, and it's the axis every decision in this post turns on.

Two more from-zero terms you'll need:
- **Symmetric vs asymmetric keys.** Symmetric (AES) = one secret for encrypt and decrypt. Asymmetric (EC keypairs) = a private key you keep and a public key you share; used for signatures and key agreement.
- **Key wrapping / envelope encryption.** Encrypting one key with *another* key. The inner "data key" can be any algorithm; the outer "wrapping key" stays in hardware. This is exactly how AWS KMS "data keys" work, and it's the escape hatch when the hardware can't hold your algorithm natively (below).

## The device identity key: hardware, non-exportable, by design

The key that establishes *this device* — the one used in the BLE handshake from Episode 3 — is an elliptic-curve **P-256** key generated directly inside the Android Keystore, with **StrongBox preferred** and a silent fallback to the Keystore's TEE when a device lacks StrongBox. It's created for key-agreement and signing, and its private bytes never leave the secure hardware.

This is the strong case, and we leaned into it: the device's identity can sign challenges and derive session keys, but there is *no code path* — not ours, not an attacker's with the unlocked app — that can read the raw key out. The identity is bound to the hardware, not to the app's memory. We chose P-256 here precisely *because* it's a first-class Keystore citizen — the hardware supports it natively.

## The wallet key: software, because the platform gives us no choice

The wallet is a different story, and the honest version is more interesting than a clean one. The wallet identity is an **Ed25519** keypair, because that's the signature scheme the reward backend and its Solana-style settlement expect — fast, compact 64-byte signatures, base58-encoded. The catch: the Android Keystore **does not support Ed25519 as a hardware key type.** You cannot generate or store an Ed25519 key in the secure element the way you can a P-256 key.

So we couldn't give the wallet the same guarantee as the device identity, and rather than pretend otherwise we built the next-best thing and documented what it costs. This is **envelope encryption** in practice: the Ed25519 keypair is generated in software; its private key is then **AES-256-GCM encrypted with a wrapping key that *is* hardware-resident and non-exportable**, and only the ciphertext is persisted. At rest, the wallet key is protected by hardware — the ciphertext is useless without the Keystore AES key, which never leaves the TEE, so a stolen copy of the app's storage is inert, and the wrapping is even bound to the device, so a backup restored onto another phone can't use it.

The honest cost: to sign, the app must decrypt the private key into ordinary memory for the duration of the operation. Unlike the P-256 device key, the raw Ed25519 key *does* briefly exist in software at generation and on each signature. We treat that as a **known, bounded exposure** — protect the ciphertext in hardware, minimise and account for the window when the plaintext is live — rather than something to paper over. When the platform can't give you a hardware guarantee, the right move is to state the boundary of what you actually have, not to claim the guarantee you don't.

## What the keys are *for*: proving possession without a shared secret

Both hierarchies serve one pattern, the same one from Episode 3: the backend should trust a request without the app carrying any shared secret. Every telemetry upload and every wallet operation is **signed**, and the server verifies the signature against the device's or wallet's public key. There's no symmetric token in the app that, if extracted, would let someone impersonate the device. **Possession of the private key *is* the credential**, and possession is proven by signing, never by sending the key. This is why signatures beat bearer tokens/API keys for device identity: a stolen bearer token grants impersonation; a signature proves possession without ever exposing the key.

## The secrets that aren't there at all

The third category of secret is the one for paid third-party services — the Sarvam voice API, the cloud AI calls. **The strongest thing you can do with a credential is not ship it.** So the app contains *none* of them. Every call to a paid AI service goes through the VIGIA backend, which holds those keys server-side and exposes a proxy the app talks to after authenticating with its own token. Decompile the APK and you will not find a Sarvam key or a cloud credential, because they were never there — the app is only ever trusted to talk to *our* backend, and the backend is trusted to talk to the vendors.

From zero, the rule underneath: **anything shipped in an APK is extractable.** An APK is a zip; strings, resources, and even obfuscated constants can be pulled out with `apktool`/`jadx`. Obfuscation (R8) only *delays* a determined attacker. So a client should hold *no* credential it doesn't strictly need — and the ones it does need (its own identity) should be signatures proving possession, not secrets that grant access.

## From zero: how the app authenticates itself

If there are no API keys in the app, how does it prove *who the user is* to the backend? Through **AWS Amplify (Cognito)**: Cognito User Pools manage identity, with Google federation, and the app authenticates using the modern **Credential Manager** API — the native "Sign in with Google" account picker that replaced the long-deprecated `GoogleSignInClient`. The app receives a scoped **token** from Cognito and attaches it to backend calls. So there are two distinct kinds of "identity" here, and it's worth not conflating them: the **user's** identity (Cognito token, for the account) and the **device/wallet** identity (hardware/wrapped keys, for signing road data and rewards). Different problems, different mechanisms.

Transport is protected by a per-flavor **`network_security_config.xml`** — the prod configuration disallows cleartext and constrains trust anchors; the demo flavor keeps a cleartext allowance for local testing, which must never reach production.

## The production edge: what "hardened" adds

From zero, being honest about the road to production (this app's foundation is strong; these are the deliberate next steps in our hardening plan, not claims of done):
- **Certificate/public-key pinning** on the prod API — so even a device with a rogue CA installed can't MITM the app — shipped with backup pins so a rotation can't brick clients.
- **Play Integrity API** attestation attached to minting-eligible telemetry, so the backend can reject an emulator or a tampered app before it earns rewards (paired with backend spoof-slashing).
- **CI secret scanning** (gitleaks) and dependency-vulnerability scanning, so no key is ever committed and no known-CVE library ships.
- **R8** obfuscation with reviewed keep-rules for the release build.

## Takeaway

Three secrets, three different answers. The device identity goes in hardware because the platform lets it, and gets the full non-exportable guarantee. The wallet key can't, because its algorithm isn't hardware-native, so it settles for a hardware-wrapped ciphertext (envelope encryption) and an honest note about the signing-time exposure. And the third-party API keys get the strongest protection of all — they're not in the app to begin with. The principle underneath is to **match each secret to the best protection the platform actually offers it, and be precise about the difference** rather than claim one guarantee for all three.

The code is open at [github.com/VigiaLabs/vigia-android](https://github.com/VigiaLabs/vigia-android).

*Engineering VIGIA Mobile · Episode 4 of 5 — Previous: Episode 3. Next: Episode 5, Never drop a token, always deliver the warning.*

---

## 🎓 CS Fundamentals — study companion

*This is a **Security + OS** episode on key storage: trusted execution environments, key wrapping, non-extractable keys, secrets management, and mobile auth from zero. "Where do you store secrets on a device?" is a sharp security-interview question.*

### Security / OS — key storage & trust
- **TEE / secure element.** A TEE (ARM TrustZone; a StrongBox secure element) is isolated hardware the main OS can't read. Keys generated there are **non-exportable** — they survive a full OS/app compromise. Hardware-rooted trust.
- **The honest limit: not every algorithm is hardware-native.** The wallet uses **Ed25519**, which the Keystore can't hold as a hardware key. So the design does the next-best thing and *says so*: generate Ed25519 in software, then **key-wrap** it with a TEE-resident AES-256-GCM key and store only ciphertext. At rest it's hardware-protected; the honest cost is a brief in-memory exposure at signing time. **Knowing and stating the boundary of your guarantee is the mark of real security engineering.**
- **Key wrapping / envelope encryption.** Encrypt one key with another (the wrapping key stays in hardware) — the same pattern as AWS KMS data keys. The ciphertext is useless without the hardware-held wrapping key; binding it to the device makes a stolen backup inert on another phone.
- **Proof of possession, not shared secrets.** Every upload / wallet op is *signed*; the server verifies the public key. No bearer token to steal — **possession of the private key is the credential, proven by signing.** Why signatures beat API keys for device identity.
- **Secrets management: don't ship the secret.** The strongest protection for third-party keys is that they're **not in the APK at all** — the app calls a backend proxy that holds them server-side and authenticates the app via its own token. An APK is extractable; obfuscation only delays.
- **User vs device identity.** Cognito token (via Credential Manager) = *who the user is*; hardware/wrapped keys = *device/wallet signing identity*. Different problems, different mechanisms. TLS + `network_security_config` protects transport.

**Interview Q&A.**
1. *What is a TEE / secure element and why use it for keys?* → Isolated hardware the OS can't read; keys are non-exportable, surviving a full OS/app compromise.
2. *You must use an algorithm the secure hardware doesn't support — how do you protect its key?* → Generate in software, wrap it with a hardware-resident key (envelope encryption), store only ciphertext, minimise and document the plaintext-in-memory window.
3. *What is envelope encryption / key wrapping?* → Encrypt a data key with a master key held in hardware/KMS; store the wrapped key; unwrap only when needed.
4. *Why are signatures better than API keys for device identity?* → No shared secret to steal; proof-of-possession proves identity without transmitting the credential.
5. *Where should third-party API keys live in a mobile app?* → Not in the app — behind a backend proxy; the app authenticates to your backend, which holds the vendor secrets.
6. *Why isn't obfuscation real secret protection?* → An APK is extractable; R8 only delays a determined attacker.

### ⚖️ This vs That — the architecture decisions, and the roads not taken

| Decision | Alternatives | Why this choice |
|---|---|---|
| **Hardware P-256 for device identity** | Software-stored identity key | Hardware makes the key non-exportable — it survives a compromised app. Use it wherever the platform allows. |
| **Software Ed25519, hardware-wrapped** | Force the wallet onto a hardware key; or store Ed25519 in plaintext | Keystore can't hold Ed25519 natively; plaintext is unacceptable. Wrapping with a TEE key protects it at rest — and being honest about the signing-time exposure beats pretending it's fully hardware-bound. |
| **No third-party keys in the APK (backend proxy)** | Ship the Sarvam/cloud key in the app (maybe obfuscated) | Any key in an APK is extractable; obfuscation only delays. Not shipping it is the only real protection. |
| **Sign every request (proof of possession)** | A bearer API token in the app | A stolen bearer token grants impersonation; a signature proves possession without exposing the key. |
| **Cognito token for user auth (Credential Manager)** | Roll your own auth / ship long-lived secrets | Managed identity + a scoped token; the modern native sign-in replaces the deprecated Google Sign-In. |

**The one to defend:** *match each secret to the strongest protection the platform actually offers — and be honest where it can't.* Device identity → hardware (non-exportable); wallet key → hardware-wrapped ciphertext (with a stated exposure window); third-party keys → not on the device at all. The senior signal is refusing to claim one uniform guarantee and instead precisely naming what each key really gets.

---

> 📚 **Prepping for placements or interviews?** This series doubles as a study track. The [**VIGIA Interview Prep Companion**](https://ridingbluewaves.hashnode.dev/the-vigia-interview-prep-companion) ties every post to coding-round patterns, system-design drills, and behavioral (STAR) answers — a full Microsoft/Amazon prep guide grounded in these projects.
