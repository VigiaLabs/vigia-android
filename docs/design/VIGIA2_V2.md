# VIGIA2 (Android app) — Master Design Spec V2

**Status:** Active · supersedes everything in `docs/design/archive/` (the phase1–5 specs, `01_copilot_architecture_v2.md`, old `README.md`) and the root `GAPS.md`.
**Scope:** the Android copilot app — multi-module Kotlin/Compose, BLE pairing, keystore/crypto, wallet, voice (Sarvam + Azure), maps, auth, network.
**Audited:** 2026-07-25 against current `main`. Review + improvement spec only; nothing here is implemented yet.
**Companion specs:** [vigia-raspi V2](../../../../Documents/Github%20Repositories/vigia-raspi/.claude/design/VIGIA_RASPI_V2.md), [vigia-amazon V2](../../../../Documents/Github%20Repositories/vigia-amazon/docs/design/VIGIA_AMAZON_V2.md), [vigia-public V2](../../../../Documents/Github%20Repositories/vigia-public/docs/design/VIGIA_PUBLIC_V2.md).

---

## 0. Reading guide

IDs: `M-CRIT-n`, `M-SEC-n`, `M-BUG-n`, `M-QUAL-n`, `M-AZ-n`. Severities P0/P1/P2 as in sibling specs.

**First, the good news (verified-correct — keep, and lean on these in the pitch):**
- The BLE identity key is **P-256, hardware-backed** (`KeystoreManager`, `PURPOSE_AGREE_KEY | PURPOSE_SIGN`, StrongBox-preferred with silent TEE fallback) — the "fatal flaw" from the old handoff (PURPOSE_SIGN-only, HMAC impossible) is fixed. The private key never leaves the TEE.
- The Sarvam API key is **no longer in the APK** — `SarvamSttClientImpl`/`SarvamTtsClientImpl` call the backend proxy (`<VIGIA_API_BASE_URL>/sarvam-proxy/*`). Archived gap A.6 is resolved on the client side (server side has its own finding — see [vigia-amazon A-SEC-1](../../../../Documents/Github%20Repositories/vigia-amazon/docs/design/VIGIA_AMAZON_V2.md)).
- The wallet Ed25519 private key is **AES-256-GCM wrapped** by a TEE Keystore key (`Ed25519KeyStore`).
- The BLE handshake **pins the Pi's public key** from the QR (`BleLinkManager.performHandshake`, lines 342-345) and verifies the CHALLENGE signature.
- `VigiaForegroundService` is `exported=false`; `CdmPresenceService` is `exported=true` but correctly guarded by `BIND_COMPANION_DEVICE_SERVICE`; minSdk 34, no cleartext traffic.

The findings below are the residual gaps.

---

## Review Reconciliation (v2.1 — cross-reviewed and verified against source, 2026-07-25)

An independent second review (Codex) cross-checked this spec; every item was re-verified by reading the cited files. **Authoritative where it conflicts with the original findings.**

### REVISED

- **M-QUAL-1 → M-CRIT-1 (upgraded).** `AuthModule.kt:24` selects `DemoAuthRepository` at **runtime** whenever `AmplifyInitializer.isConfigured` is false — not merely a flavor-binding risk. DemoAuth accepts any email + any 8-char password, so an Amplify init failure in production silently disables authentication (runtime fail-open). Fix: bind by source set/flavor and fail **closed** in production — no demo fallback reachable in release.
- **M-QUAL-4 — CLOSED (confirmed safe).** `NetworkModule.kt:67,98` uses `Level.BODY` only under `BuildConfig.DEBUG`; release is `NONE`.
- **M-SEC-4 — remains a verification task** (PKCE is runtime/library behavior, not provable from checked-in config alone).

### New CONFIRMED findings

- **M-CRIT-2 — Device-wallet binding never succeeds (P0, cross-repo).** `PairingViewModel.kt:169` hardcodes `deviceSig = ""` (TODO: obtain the Pi ECDSA signature over the bind challenge via BLE). The server correctly 401s; `ClaimDeviceRepositoryImpl` maps non-409 responses to `NetworkError`; the only caller of `claimDevice` is the pairing flow (no next-launch retry despite the comment). Net effect: the 1:1 device↔wallet binding is never established and pairing silently degrades to local-only. Fix requires all three: a Pi BLE "sign binding challenge" command (raspi R-SEC-6), the Android call to obtain it + a real retry state machine, and the transactional claim (amazon A-SEC-6).
- **M-SEC-5 — allowBackup enabled (P1).** `AndroidManifest.xml:14` `allowBackup="true"` with sample rules. Wallet ciphertext, pairing state, and pinned identity may be backed up; restored wallet ciphertext won't match a new device's Keystore-bound AES key (unusable), and sensitive material leaves the device. Fix: exclude the wallet/pairing/keystore stores from backup, or disable backup.
- **M-QUAL-7 — Release accepts empty config (P1).** `AndroidApplicationConventionPlugin.kt:54` allows empty API/MQTT/MAC config values, so CI can produce a successful-but-unusable release APK. Fix: release-variant Gradle validation that fails the build on empty required config.

### Revised priority (vigia2)

1. M-CRIT-1 fail-closed auth in release.
2. M-CRIT-2 binding (coordinated with raspi + amazon).
3. M-SEC-1 BLE null-key compile-out; M-SEC-2 biometric payout signing.
4. M-SEC-5 backup exclusion; M-QUAL-7 release-config validation.
5. M-SEC-3 timestamped registration; remaining hardening.

Closed: M-QUAL-4.

---

## 1. Architecture recap (as-built)

```
feature/copilot (CopilotViewModel, voice-call overlay) ─┬─ core/sensor (BLE, keystore, TTS, VAD, voice)
feature/maps (osmdroid, route)                          ├─ core/network (OkHttp SSE search, Sarvam proxy, MQTT, Stripe)
feature/pairing (CDM + QR)                              ├─ core/auth (Amplify Cognito / Demo)
                                                        ├─ core/wallet (Ed25519 + Base58)
                                                        └─ core/data, core/model
BLE: HELLO→CHALLENGE(pinned Pi pub)→RESPONSE→CONFIRM  ·  ECDH P-256 → HKDF session key
```

---

## 2. P0 — Critical findings

### M-SEC-1 — BLE MITM is possible whenever the pinned Pi key is absent (demo/dev path)
**File:** `core/sensor/src/main/kotlin/com/vigia/core/sensor/ble/BleLinkManager.kt:342-349`
**Failure:** identity pinning only runs `if (piPublicKeyBytes != null)`. When it's null (`connect(...)` called without the QR-provisioned key — the documented "demo/dev mode"), the app **skips identity verification and still performs ECDH**, establishing an authenticated-looking session with *any* peer advertising the service UUID. An attacker with a rogue Pi impersonates the device: injects fake hazards, or man-in-the-middles telemetry. If any competition/pilot build ever reaches this path (e.g. a pairing flow that forgets to pass the key), the "cryptographically bound device" claim is false.
**V2 fix:** make the null-key path **compile-out in release builds** (or hard-throw). Production pairing must always carry the QR-pinned Pi key; a session without pinning must be impossible outside a debug flavor. Add a release build assertion + an instrumentation test that `connect()` without a pinned key throws in `release`.

---

## 3. P1 — High findings

### M-SEC-2 — Wallet signing key is usable without user authentication
**File:** `core/wallet/src/main/kotlin/com/vigia/core/wallet/Ed25519KeyStore.kt:90` (`setUserAuthenticationRequired(false)` on the AES wrapping key) + `:65-77` (`sign` decrypts the PKCS8 priv into process memory on every call).
**Failure:** the Ed25519 wallet key signs money-moving proofs (`VIGIA-PAYOUT`, registration, balance). Because the AES wrapping key requires no user auth, any code running as the app while the device is unlocked — or malware exploiting the app — can produce valid payout signatures. Additionally, `Ed25519` is **not** an AndroidKeyStore-native algorithm, so the raw private key necessarily exists in app memory at generation and on every sign (decrypted PKCS8), unlike the hardware-bound P-256 BLE key. The asymmetry (BLE key hardware-bound; wallet key software) is undocumented.
**V2 fix:** (1) for payout-authorizing signatures, gate the wrapping-key use behind `setUserAuthenticationRequired(true)` with a `BiometricPrompt` (a payout is a deliberate user action — a per-use auth is appropriate); (2) minimise the decrypted-key lifetime (zero the PKCS8 bytes after signing); (3) document the software-key exposure window; (4) after the Azure migration, prefer moving the reward/identity signing to the DPS X.509 / hardware P-256 path where possible so the money key can also be hardware-bound.

### M-SEC-3 — Registration proof is static (replayable); align with server timestamp binding
**File:** `core/wallet/src/main/kotlin/com/vigia/core/wallet/WalletRepositoryImpl.kt:36-37` — signs `VIGIA-REGISTER:<pubKey>` with no timestamp.
**Failure:** mirrors [vigia-amazon A-BUG-2](../../../../Documents/Github%20Repositories/vigia-amazon/docs/design/VIGIA_AMAZON_V2.md). The registration signature is static per device → replayable. (Balance and payout proofs *do* bind `:tsMs` correctly, lines 61-69 — so this is an inconsistency, not a systemic gap.)
**V2 fix:** sign `VIGIA-REGISTER:<pubKey>:<tsMs>` and send `X-Wallet-Timestamp`; coordinate the server change so both sides move together.

### M-SEC-4 — Confirm OAuth uses PKCE; audit redirect handling
**File:** `app/src/main/AndroidManifest.xml:37-42` (`HostedUIRedirectActivity`, `exported=true`, BROWSABLE, custom scheme `vigia://callback`).
**Failure:** custom-scheme redirects are interceptable by other installed apps; without PKCE an intercepted authorization code can be exchanged for tokens. Archived audit noted "safe only with PKCE (Amplify default; confirm)" — it must be *confirmed*, not assumed.
**V2 fix:** verify Amplify is configured with PKCE (S256) for the Hosted UI flow; prefer App Links (verified https) over a custom scheme if feasible; ensure no token/code is written to logs. Add a note to the security narrative once confirmed.

### M-QUAL-1 — Ensure the Demo auth/blackbox path cannot ship in release
**File:** `core/auth/.../DemoAuthRepository.kt` (accepts any email + any 8-char password) + `core/sensor/.../BlackboxConfig.kt` (build-flavour secret injection).
**Failure:** `DemoAuthRepository` is a real bypass of authentication. If the DI binding selects it in a release/competition build (flavour misconfig), anyone "logs in." The BLE demo-mode key-skip (M-SEC-1) is the same class of risk.
**V2 fix:** bind `DemoAuthRepository` only in a `demo`/`debug` flavour; add a release-variant unit test asserting the bound `AuthRepository` is `AmplifyAuthRepository` and that no demo/skip paths are reachable. Treat "no debug affordance in release" as a checklist item before every demo build.

---

## 4. P2 — Quality / hardening

- **M-QUAL-2** — `CopilotViewModel` is a large hotspot (fan-in 22; owns voice, search, alerts, TTS, state). Split into focused collaborators (session manager, voice controller, alert reducer) for testability and to survive a technical-review "walk me through this class."
- **M-QUAL-3** — `getPublicKeyUncompressed()` (`KeystoreManager.kt:53-54`) assumes the raw point is the last 65 bytes of the X.509 encoding; robust for P-256 but add an assertion on the SPKI header to fail fast if the provider changes encoding.
- **M-QUAL-4** — HTTP body logging is `Level.BODY` in debug (`NetworkModule.kt:69,98`); confirm it is `NONE` in release so tokens/telemetry never hit logcat.
- **M-QUAL-5** — Zeroize sensitive `ByteArray`s (shared secrets, decrypted PKCS8, session keys) after use across `EcdhHandshake`, `KeystoreManager.computeSharedSecret`, `Ed25519KeyStore.sign`.
- **M-QUAL-6** — Add instrumentation tests for the full BLE handshake against a known-good and a rogue peer (asserts M-SEC-1 fix), and a golden-vector test that the phone's ECDH/HKDF output matches the Pi's `vigia_ecdh.hpp` for the same inputs (cross-implementation regression guard).

---

## 5. Azure / IC-2027 transition (November window)

- **M-AZ-1 — Azure AI Speech second lane.** Add an Azure Speech STT/TTS lane beside Sarvam behind the existing `SarvamSttClient`/`SarvamTtsClient` interfaces (dual-stack resilience = a named Microsoft service + a failover story). The vigia-public side already has `azure-stt.ts`/`azure-tts.ts` to mirror.
- **M-AZ-2 — Voice streaming on Azure OpenAI Realtime.** The memory note lists Gemini-Live-style streaming as the next voice step — build it on **Azure OpenAI Realtime API**, not Gemini, for the IC audience.
- **M-AZ-3 — Feature-flag OFF Stripe + Solana wallet for competition builds.** `stripe-*` are empty stubs; `wallet-*`/Solana are DePIN surface the pitch should not expose. Gate them behind a build flag defaulting OFF; after the [vigia-amazon A-AZ-1](../../../../Documents/Github%20Repositories/vigia-amazon/docs/design/VIGIA_AMAZON_V2.md) migration, the reward UI reads Confidential-Ledger receipts + UPI payout, not tokens.
- **M-AZ-4 — Frame-hash in the telemetry signer.** `WalletRepositoryImpl` (lines 101-112) already conditionally signs `...:<frameSha256>` when a frame is present — make the frame hash **mandatory** for photo-bearing reports so the server can enforce [R-SEC-4 / A-AZ-3](../../../../Documents/Github%20Repositories/vigia-raspi/.claude/design/VIGIA_RASPI_V2.md).
- **M-AZ-5 — On-device anonymization awareness.** If the app ever displays or forwards dashcam frames, apply/verify the edge blur (DPDP, May 13 2027). Prefer showing latent-derived overlays over raw frames.

---

## 6. Priority-ordered work plan

| Order | ID | Item | Effort |
|---|---|---|---|
| 1 | M-SEC-1 | Compile-out BLE null-key path in release; test | ~1 d |
| 2 | M-QUAL-1 | Release-variant assertions (no Demo auth / no skip paths) | ~0.5 d |
| 3 | M-SEC-2 | Biometric-gated payout signing + key-lifetime hygiene | ~1.5 d |
| 4 | M-SEC-3 | Timestamped registration proof (with server) | ~0.5 d |
| 5 | M-SEC-4 | Confirm/enforce PKCE; redirect audit | ~0.5 d |
| 6 | M-QUAL-2..6 | Refactor + crypto hygiene + cross-impl tests | ~3 d |
| 7 | M-AZ-1..5 | Azure lanes + flag-off + frame-hash (Nov window) | see roadmap |

**Definition of done for V2:** no build can pair without pinning the Pi key; no release build can select Demo auth or a skip path; money-signing requires user auth; registration proof is replay-resistant; PKCE confirmed; and the Azure Speech lane + Realtime voice are demoable with Stripe/Solana flagged off.
