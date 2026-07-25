# VIGIA2 (Android app) — Master Design Spec V2 (rev 2.2)

**Status:** Active, internally reconciled. Supersedes v2.0/v2.1 and everything in `docs/design/archive/`.
**Scope:** the Android copilot — multi-module Kotlin/Compose, BLE pairing, keystore/crypto, wallet, voice (Sarvam + Azure), maps, auth, network.
**Audited against:** `design/v2-specs@9ffa73a` (no code-fix branch yet in this repo). Two cross-reviews (Codex ×2) + first-party re-verification.
**Companion specs:** [vigia-raspi V2](../../../../Documents/Github%20Repositories/vigia-raspi/.claude/design/VIGIA_RASPI_V2.md) · [vigia-amazon V2](../../../../Documents/Github%20Repositories/vigia-amazon/docs/design/VIGIA_AMAZON_V2.md) · [vigia-public V2](../../../../Documents/Github%20Repositories/vigia-public/docs/design/VIGIA_PUBLIC_V2.md).
**Cross-repo protocol findings** (pairing/key hierarchy) are defined in [vigia-raspi V2 §5.2](../../../../Documents/Github%20Repositories/vigia-raspi/.claude/design/VIGIA_RASPI_V2.md); M-CRIT-2 is this repo's slice.

---

## 0. How to read

**Finding status:** `OPEN` · `IMPLEMENTED` · `IMPLEMENTED-PARTIAL` · `RETRACTED` · `SUPERSEDED` · `CLOSED` (already-correct). One entry per finding. **Severity:** `P0/P1/P2`. OPEN findings carry file:line · failure · fix · **acceptance** · deps. The status matrix (§1) is authoritative. No code fixes have been implemented in this repo yet — everything is OPEN except the CLOSED/ SUPERSEDED items in §6.

**Verified-correct, keep (and use in the pitch):** BLE identity key is P-256, hardware-backed (`KeystoreManager`, StrongBox-preferred TEE fallback); the Sarvam key is off the APK (proxied); the wallet Ed25519 private key is AES-256-GCM wrapped; the BLE handshake pins the Pi key **when a pinned key is supplied** (`BleLinkManager:342`); `VigiaForegroundService` `exported=false`, `CdmPresenceService` guarded by `BIND_COMPANION_DEVICE_SERVICE`; minSdk 34, no cleartext.

---

## 1. Implementation status matrix

| ID | Title | Sev | Status |
|----|-------|-----|--------|
| M-CRIT-1 | DemoAuth runtime fail-open on Amplify init failure | P0 | OPEN |
| M-CRIT-2 | Device-wallet binding never succeeds (`deviceSig=""`) | P0 | OPEN (cross-repo) |
| M-SEC-1 | BLE identity bypass on null-key demo path | P0 | OPEN |
| M-SEC-2 | Wallet signing key usable without user auth | P1 | OPEN (fix corrected) |
| M-SEC-3 | Static (replayable) registration proof | P1 | OPEN |
| M-SEC-5 | `allowBackup` exposes wallet/pairing material | P1 | OPEN (fix corrected) |
| M-QUAL-7 | Release build accepts empty API/MQTT/MAC config | P1 | OPEN |
| M-SEC-4 | Confirm OAuth PKCE | P2 | OPEN (verification task) |
| M-QUAL-2 | `CopilotViewModel` God-object | P2 | OPEN |
| M-QUAL-3 | SPKI-header assertion on pubkey decode | P2 | OPEN |
| M-QUAL-5 | Zeroize sensitive byte buffers | P2 | OPEN |
| M-QUAL-6 | Cross-implementation handshake tests | P2 | OPEN |
| M-QUAL-1 | (BLE demo/flavor auth risk) | — | SUPERSEDED by M-CRIT-1 |
| M-QUAL-4 | Release HTTP body logging | — | CLOSED (release=NONE, verified) |

---

## 2. Architecture recap (as-built)

```
feature/copilot (CopilotViewModel, voice overlay) ─┬─ core/sensor (BLE, keystore, TTS, VAD, voice)
feature/maps (osmdroid) · feature/pairing (CDM+QR) ├─ core/network (OkHttp SSE, Sarvam proxy, MQTT, Stripe)
                                                    ├─ core/auth (Amplify Cognito / Demo)
                                                    ├─ core/wallet (Ed25519 + Base58)
                                                    └─ core/data, core/model
BLE: HELLO→CHALLENGE(pinned Pi pub)→RESPONSE→CONFIRM · ECDH P-256 → HKDF session key
```

---

## 3. Open findings

### M-CRIT-1 — DemoAuth runtime fail-open (P0, OPEN)
**File:** `core/auth/.../di/AuthModule.kt:24` — `if (AmplifyInitializer.isConfigured) amplify.get() else demo.get()`.
**Failure:** if Amplify fails to configure at runtime (misconfig, missing config, network at init), production silently binds `DemoAuthRepository`, which accepts any email + any 8-char password. This is runtime fail-open, not merely a flavor-binding risk.
**Fix:** bind implementations by source set/flavor; in release, there must be no path to `DemoAuthRepository` — fail **closed** (surface an auth-unavailable error) if Amplify isn't configured.
**Acceptance:** a release-variant test asserts the bound `AuthRepository` is `AmplifyAuthRepository` and that no demo/bypass path is reachable; simulated Amplify-init failure in release does not grant access.

### M-CRIT-2 — Device-wallet binding never succeeds (P0, OPEN — cross-repo)
**File:** `feature/pairing/.../PairingViewModel.kt:169` hardcodes `val deviceSig = ""`; `ClaimDeviceRepositoryImpl` maps non-409 responses to `NetworkError`; the only caller of `claimDevice` is the pairing flow (no next-launch retry despite the comment).
**Failure:** the server correctly 401s an empty device signature, the app treats it as a network error and degrades to local-only pairing, so the 1:1 device↔wallet binding is never established.
**Fix (requires all three, per [raspi §5.2](../../../../Documents/Github%20Repositories/vigia-raspi/.claude/design/VIGIA_RASPI_V2.md)):** a Pi BLE "sign binding challenge" command (raspi R-SEC-6), the Android call to obtain that ATECC signature + a real retry state machine, and the transactional claim + wallet→phone delegation (amazon A-SEC-6). Do not code any one side before the protocol in raspi §5.2 is agreed.
**Acceptance:** a fresh pairing produces a server-accepted claim with a real `deviceSig`; a transient failure retries on next launch; local-only fallback is a deliberate, surfaced state, not a silent default.

### M-SEC-1 — BLE identity bypass on the null-key path (P0, OPEN)
**File:** `core/sensor/.../BleLinkManager.kt:342-349` — pinning runs only `if (piPublicKeyBytes != null)`; the null path does ECDH with any peer.
**Failure:** any pairing flow that omits the QR-pinned Pi key establishes an authenticated-looking session with a rogue Pi (fake hazards / MITM).
**Fix:** make the null-key path **compile out** (or hard-throw) in release; production pairing must always carry the pinned key.
**Acceptance:** an instrumentation test asserts `connect()` without a pinned key throws in the release variant; a rogue-peer handshake fails.

### M-SEC-2 — Wallet signing key usable without user auth (P1, OPEN — fix corrected)
**File:** `core/wallet/.../Ed25519KeyStore.kt:90` (`setUserAuthenticationRequired(false)`), `:65-77` (decrypts PKCS8 into memory on every sign). Ed25519 isn't AndroidKeyStore-native, so the raw key exists in app memory at generation and per-sign — unlike the hardware-bound P-256 BLE key.
**Correction (Codex):** do **not** make the single wallet wrapping key biometric-only — it is also used for registration, telemetry signing, balance checks, and binding, so gating it globally would break those flows. Instead: introduce a **separate payout-authorization key** (or gate only the payout-signature operation) behind `BiometricPrompt`; zero the decrypted PKCS8 immediately after signing; document the software-key exposure window; after the Azure migration prefer moving money-signing to the hardware DPS/P-256 path.
**Acceptance:** a payout signature requires a biometric prompt; routine telemetry/balance/registration signing does not; decrypted key bytes are zeroed post-sign.

### M-SEC-3 — Static registration proof (P1, OPEN)
**File:** `core/wallet/.../WalletRepositoryImpl.kt:36-37` signs `VIGIA-REGISTER:<pubKey>` with no timestamp (balance/payout proofs correctly bind `:tsMs`). **Fix:** sign `VIGIA-REGISTER:<pubKey>:<tsMs>` + send `X-Wallet-Timestamp`; coordinate the server change ([amazon A-BUG-2 note](../../../../Documents/Github%20Repositories/vigia-amazon/docs/design/VIGIA_AMAZON_V2.md)). **Acceptance:** a replayed registration proof is rejected server-side.

### M-SEC-5 — Backup exposes wallet/pairing material (P1, OPEN — fix corrected)
**File:** `app/src/main/AndroidManifest.xml:14` `allowBackup="true"` + `dataExtractionRules`/`fullBackupContent`.
**Correction (Codex):** remediation must update **both** `app/src/main/res/xml/backup_rules.xml` and `app/src/main/res/xml/data_extraction_rules.xml`. Because the wallet ciphertext is bound to the device Keystore AES key, a restored copy is unusable on a new device **and** leaks sensitive material — so the safest policy is to **disable backup** (or explicitly exclude the wallet/pairing/keystore stores in both rule files).
**Acceptance:** wallet ciphertext, pairing state, and pinned identity are excluded from cloud/device-transfer backup in both rule files (or backup disabled).

### M-QUAL-7 — Release accepts empty required config (P1, OPEN)
**File:** `build-logic/convention/.../AndroidApplicationConventionPlugin.kt:54` allows empty API/MQTT/MAC config, so CI can produce a successful-but-unusable release APK. **Fix:** release-variant Gradle validation that fails the build on empty required config. **Acceptance:** a release build with any required config blank fails at assembly.

### M-SEC-4 — Confirm OAuth PKCE (P2, OPEN — verification task)
`HostedUIRedirectActivity` uses custom scheme `vigia://callback` (`AndroidManifest.xml:37`). Custom-scheme redirects are interceptable; PKCE (S256) mitigates but is library/runtime behavior not provable from checked-in config. **Fix:** verify Amplify uses PKCE S256; prefer verified App Links; ensure no token/code is logged. **Acceptance:** a captured authorization code cannot be exchanged without the PKCE verifier (verified in a runtime test).

### M-QUAL-2..6 (P2, OPEN)
Split `CopilotViewModel` (fan-in 22) into focused collaborators (M-QUAL-2); assert the SPKI header in `getPublicKeyUncompressed()` (M-QUAL-3); zeroize shared secrets / decrypted PKCS8 / session keys after use (M-QUAL-5); add golden-vector cross-implementation handshake tests against the Pi's `vigia_ecdh.hpp` and a rogue-peer test (M-QUAL-6).

---

## 6. Resolved / superseded / closed (not scheduled)

- **M-QUAL-1 — SUPERSEDED by M-CRIT-1.** The auth risk is a runtime fail-open, not just a flavor-binding mistake.
- **M-QUAL-4 — CLOSED (verified safe).** `NetworkModule.kt:67,98` uses `Level.BODY` only under `BuildConfig.DEBUG`; release is `NONE`.

---

## 7. Priority-ordered work plan (OPEN only)

1. **M-CRIT-1** fail-closed auth in release (+ release-variant tests, which also cover M-SEC-1 and M-QUAL-7 gating).
2. **M-SEC-1** BLE null-key compile-out.
3. **M-CRIT-2** binding — only after the [raspi §5.2](../../../../Documents/Github%20Repositories/vigia-raspi/.claude/design/VIGIA_RASPI_V2.md) protocol is agreed (cross-repo).
4. **M-SEC-2** separate/biometric-gated payout key; **M-SEC-5** backup exclusion in both rule files; **M-QUAL-7** release config validation.
5. **M-SEC-3** timestamped registration; **M-SEC-4** PKCE confirmation.
6. **M-QUAL-2..6** refactor + crypto hygiene + cross-impl tests.

Android builds (Gradle/device) could not run in the review sandbox; each item is "done" only when it builds and its variant/instrumentation test passes.

---

## 8. Azure / IC-2027 transition

- **M-AZ-1** Azure AI Speech lane beside Sarvam behind the existing client interfaces.
- **M-AZ-2** voice streaming on Azure OpenAI Realtime (not Gemini Live).
- **M-AZ-3** feature-flag OFF Stripe + Solana/wallet for competition builds; after [amazon A-AZ-1](../../../../Documents/Github%20Repositories/vigia-amazon/docs/design/VIGIA_AMAZON_V2.md) the reward UI reads Confidential-Ledger receipts + UPI.
- **M-AZ-4** make the frame hash mandatory for photo-bearing reports — but see the [raspi R-SEC-4 re-scope](../../../../Documents/Github%20Repositories/vigia-raspi/.claude/design/VIGIA_RASPI_V2.md) (needs a Pi-side signature + uploaded blurred frame, not the Pico).
- **M-AZ-5** on-device anonymization awareness if frames are ever shown/forwarded (DPDP, May 13 2027).

---

## Appendix — verification method
Re-verified by reading cited files at `design/v2-specs@9ffa73a`. Key pinning, hardware-backed BLE key, and Sarvam-proxy confirmed present; DemoAuth fail-open, `deviceSig=""`, and `allowBackup` confirmed by source. Gradle/device builds could not run here; all remediations require an on-device build + variant test to close.
