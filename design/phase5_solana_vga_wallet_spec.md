# PHASE 5: Solana $VGA Token Wallet — Burn-and-Mint Reward Economy
**Status:** Design / Pre-implementation  
**Prerequisite:** Phase 2 (BLE hardware link) + Phase 3 (MQTT/FCM alert pipeline) verified  
**Primary Modules:** `:core:wallet` (new) · `:feature:copilot` (WalletPane replacement)  
**Treasury Public Key (Testnet):** `7PTUbMJMWRwAixmkez2yBpsjovyAECtcXQHVYzAi8jf1`  
**Network:** Solana Devnet → Testnet → Mainnet-Beta (phased rollout)

---

## 0. Executive Summary

Every Vigia user gets a non-custodial Solana wallet provisioned silently on first launch. When the hardware sensor detects a road hazard that the cloud pipeline cryptographically verifies, the backend mints a $VGA token reward directly to the user's wallet. $VGA operates on a **burn-and-mint** model: new tokens are minted only for verified real-world detections; tokens are burned on premium feature redemption, staking into governance, or optional cash-out via a DEX swap bridge. This design replaces the Stripe `PayoutStatus` model with a sovereign, programmable, censorship-resistant reward rail.

---

## 1. Token Economics — $VGA Design

### 1.1 Token Specification

| Property | Value |
|---|---|
| Token Standard | SPL Token (Solana Program Library) |
| Decimals | 6 (matches USDC convention, human-readable sub-cent units) |
| Initial Supply | 0 (mint-on-demand only — no pre-mine, no VC allocation) |
| Mint Authority | `7PTUbMJMWRwAixmkez2yBpsjovyAECtcXQHVYzAi8jf1` (treasury multisig target) |
| Freeze Authority | None (tokens are freely tradeable once minted) |
| Max Supply | 1,000,000,000 VGA (1B hard cap enforced in on-chain program) |
| Network | Solana Devnet (dev) → Testnet (staging) → Mainnet-Beta (prod) |

### 1.2 Burn-and-Mint Flow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         BURN-AND-MINT ECONOMY                               │
│                                                                             │
│  MINT EVENTS (supply increases)          BURN EVENTS (supply decreases)     │
│  ─────────────────────────────           ──────────────────────────────     │
│  • Verified hazard detection             • Premium AI co-pilot session       │
│  • Detection streak bonus (×1.5)         • Governance vote stake            │
│  • First-detection-in-area bonus         • NFT data badge mint              │
│  • Hardware uptime bonus (weekly)        • Cash-out via DEX swap bridge      │
│  • Referral (new device activated)       • Speed boost on leaderboard        │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 1.3 Reward Rate Schedule

| Detection Severity | Base Reward | Confidence Multiplier | Notes |
|---|---|---|---|
| LOW | 0.5 VGA | ×1.0 | Edge condition, minor road debris |
| MEDIUM | 1.0 VGA | ×1.0 – ×1.3 | Pothole, standing water |
| HIGH | 2.0 VGA | ×1.0 – ×1.5 | Multi-sensor confirmed |
| CRITICAL | 5.0 VGA | ×1.0 – ×2.0 | Cross-validated by ≥3 users |
| First detection in geo-cell | +3.0 VGA bonus | flat | H3 resolution-9 cell |
| Streak (5 consecutive days) | +50% on next detection | ×1.5 | Resets on miss |
| Hardware uptime (≥22h/week) | +2.0 VGA/week | flat | Paid Sunday 00:00 UTC |

### 1.4 Anti-Gaming Controls

- **Rate limiting**: Max 20 mint events per wallet per 24h (enforced on-chain via custom program)
- **Geo-deduplication**: Detections within 15m of a prior event within 30min are rejected
- **Cloud attestation**: Every mint requires a signed JWT from the Vigia backend — no backend sig, no mint
- **Replay protection**: Nonce stored per detection ID in the on-chain program's account map
- **Confidence floor**: Cloud ML score must be ≥ 0.72 for any mint to proceed
- **Velocity anomaly detection**: Backend ML flags wallets with >3σ deviation from cohort mint rate

---

## 2. Android Wallet Architecture

### 2.1 Module Structure

```
:core:wallet  (NEW)
├── src/main/kotlin/com/vigia/core/wallet/
│   ├── model/
│   │   ├── VgaWallet.kt              — domain model (publicKey, balance, history)
│   │   ├── VgaTransaction.kt         — mint/burn/transfer events
│   │   ├── WalletState.kt            — sealed interface for UI states
│   │   └── RewardEvent.kt            — detection → reward mapping
│   ├── keystore/
│   │   ├── SolanaKeystoreManager.kt  — Android Keystore keypair lifecycle
│   │   └── MnemonicEncryptionUtil.kt — AES-GCM encrypted mnemonic backup
│   ├── rpc/
│   │   ├── SolanaRpcClient.kt        — Retrofit/OkHttp JSON-RPC client
│   │   ├── SolanaRpcModels.kt        — request/response DTOs
│   │   └── SolanaRpcService.kt       — Retrofit interface
│   ├── repository/
│   │   ├── WalletRepository.kt       — interface
│   │   └── WalletRepositoryImpl.kt   — implementation
│   ├── sync/
│   │   └── WalletSyncWorker.kt       — WorkManager periodic balance sync
│   └── di/
│       └── WalletModule.kt           — Hilt bindings
```

**Dependency chain:** `WalletRepositoryImpl` → `SolanaRpcClient` + `SolanaKeystoreManager`  
**No dependency on** `:feature:copilot` — wallet is a pure data module.

### 2.2 Key Generation & Storage — Security Model

#### Critical Design Decision: Non-Custodial with Encrypted Cloud Backup

The private key never leaves the device in plaintext. Generation and signing happen entirely within Android Keystore hardware-backed enclaves.

```
┌──────────────────────────────────────────────────────────┐
│                 WALLET PROVISIONING FLOW                 │
│                                                          │
│  1. First launch detected (no wallet in DataStore)       │
│                    ↓                                     │
│  2. Generate Ed25519 keypair                             │
│     • Use Android Keystore (StrongBox if available)      │
│     • Alias: "vigia_solana_wallet_v1"                    │
│                    ↓                                     │
│  3. Derive BIP39 mnemonic from entropy (24-word)         │
│     • Encrypted with AES-256-GCM                        │
│     • Key derived from user's biometric-protected key    │
│     • Stored in EncryptedSharedPreferences               │
│                    ↓                                     │
│  4. Register public key with backend                     │
│     POST /v1/wallet/register { publicKey, userId, sig }  │
│                    ↓                                     │
│  5. Backend creates Associated Token Account (ATA)       │
│     for the user's wallet + pays rent (lamports)         │
│                    ↓                                     │
│  6. PublicKey persisted to DataStore                     │
│     WalletState transitions: Creating → Active           │
└──────────────────────────────────────────────────────────┘
```

#### Why Android Keystore for Solana (Ed25519)?

Android Keystore natively supports EC keypairs but **not** Ed25519 (as of API 34). The implementation uses a hybrid approach:
- **Option A (recommended for launch):** Generate the Ed25519 keypair using BouncyCastle inside a TEE-backed computation, then wrap the private key bytes with an AES-256-GCM key stored in Android Keystore (hardware-backed). Signing happens in-process but private key bytes are always encrypted at rest.
- **Option B (future, API 35+):** StrongBox-backed Ed25519 when Android adds native support.

```kotlin
// SolanaKeystoreManager.kt — simplified signing contract
interface SolanaKeystoreManager {
    suspend fun provisionIfAbsent(): PublicKey           // idempotent
    fun publicKey(): PublicKey
    suspend fun sign(payload: ByteArray): ByteArray      // Ed25519 signature
    suspend fun exportEncryptedMnemonic(): String        // biometric-gated
    fun isProvisioned(): Boolean
}
```

### 2.3 Solana RPC Client

Target endpoint (configurable via BuildConfig):

| Environment | RPC URL |
|---|---|
| Debug (devnet) | `https://api.devnet.solana.com` |
| Staging (testnet) | `https://api.testnet.solana.com` |
| Release (mainnet) | `https://vigia-rpc.helius.xyz` (private Helius node — rate-limit free) |

Key RPC calls used:

```kotlin
interface SolanaRpcService {
    // getBalance — SOL balance for gas fee awareness
    @POST(".") suspend fun getBalance(@Body req: RpcRequest): RpcResponse<BalanceResult>
    
    // getTokenAccountsByOwner — fetch VGA ATA balance
    @POST(".") suspend fun getTokenAccounts(@Body req: RpcRequest): RpcResponse<TokenAccountsResult>
    
    // getSignaturesForAddress — transaction history (paginated)
    @POST(".") suspend fun getSignatures(@Body req: RpcRequest): RpcResponse<List<SignatureResult>>
    
    // getTransaction — full transaction details for history display
    @POST(".") suspend fun getTransaction(@Body req: RpcRequest): RpcResponse<TransactionResult>
    
    // sendTransaction — user-initiated burns (premium feature redemption)
    @POST(".") suspend fun sendTransaction(@Body req: RpcRequest): RpcResponse<String>
}
```

**No server-signing dependency for reads.** All balance queries and history reads hit public RPC. Only mint transactions require the backend's mint authority signature.

### 2.4 WalletRepository Interface

```kotlin
interface WalletRepository {
    // State
    val walletState: StateFlow<WalletState>
    val balance: StateFlow<BigDecimal>          // $VGA balance, 6-decimal precision
    val pendingRewards: StateFlow<List<RewardEvent>>
    val transactionHistory: StateFlow<List<VgaTransaction>>
    
    // Commands
    suspend fun provision()                     // idempotent first-launch setup
    suspend fun refreshBalance()                // pull from RPC
    suspend fun claimPendingReward(event: RewardEvent)  // sign & submit claim tx
    suspend fun burnForFeature(featureId: String, amount: BigDecimal): Result<String>
    fun exportEncryptedMnemonic(): Flow<String> // biometric gated
}

sealed interface WalletState {
    data object Unprovisioned : WalletState
    data object Creating : WalletState
    data class Active(
        val publicKey: String,
        val solBalance: Double,          // for gas fee warnings
        val hasAssociatedTokenAccount: Boolean,
    ) : WalletState
    data class Error(val cause: Throwable) : WalletState
}
```

---

## 3. Detection → Reward Pipeline

### 3.1 End-to-End Sequence

```
Android Device                    Vigia Backend                  Solana Network
─────────────                     ─────────────                  ──────────────
BLE sensor frame received
    │
    ▼
ContextAggregator.toSnapshot()
    │  (lat, lng, timestamp, rriScore, severity)
    │
    ▼
MqttAlertRepository publishes
    │  POST /v1/detection/submit
    │  Body: {
    │    detectionId: UUID,
    │    sensorSerial: String,   ← hardware attestation
    │    keystoreSig: Base64,    ← device signs detection w/ provisioned key
    │    severity: Severity,
    │    lat, lng, accuracy,
    │    rriScore: Float,
    │    walletPublicKey: String
    │  }
    │                                 ▼
    │                         Backend validation:
    │                         1. Verify keystoreSig against registered pubkey
    │                         2. Run ML confidence scoring (≥0.72 threshold)
    │                         3. Geo-dedup check (PostGIS / Redis spatial index)
    │                         4. Rate-limit check (Redis, 20/wallet/24h)
    │                         5. Replay-nonce check
    │                                 │
    │                         If approved:
    │                         6. Build mint instruction:
    │                            MintTo {
    │                              mint: VGA_MINT_PUBKEY,
    │                              to: user_ATA,
    │                              authority: treasury_keypair,
    │                              amount: reward_lamports
    │                            }
    │                         7. Sign and submit to Solana RPC
    │                                 │                    ▼
    │                                 │          Transaction confirmed
    │                                 │          (1-2 Solana slots ≈ 800ms)
    │                                 │
    │                         8. FCM push to device:
    │                            {
    │                              type: "reward",
    │                              amount: "2.0",
    │                              txSignature: "...",
    │                              detectionId: "..."
    │                            }
    │◄────────────────────────────────┘
    │
    ▼
VigiaFcmReceiver.onMessageReceived()
    │  type == "reward" → WalletRepository.refreshBalance()
    │  Post local notification: "You earned 2.0 $VGA!"
    │
    ▼
WalletPane recomposes with updated balance
```

### 3.2 Detection Signing (Android Keystore Integration)

The existing `KeystoreManager` in `:core:sensor` signs BLE telemetry for handshake auth. Wallet provisioning introduces a **separate** signing key scoped to reward claims. This prevents the sensor key from being coupled to financial operations.

```kotlin
// In SolanaKeystoreManager — the detection claim signature
suspend fun signDetectionClaim(detectionId: String, walletPublicKey: String): String {
    val payload = "$detectionId|$walletPublicKey|${System.currentTimeMillis() / 1000}"
    val sigBytes = sign(payload.toByteArray())
    return Base64.encodeToString(sigBytes, Base64.NO_WRAP)
}
```

The backend verifies this signature against the registered wallet public key — tying every reward claim cryptographically to both the device and the user wallet.

### 3.3 Pending Reward State (Optimistic UI)

To avoid latency between detection and user feedback, the app maintains a local `pendingRewards` list:

1. Detection submitted → add `RewardEvent(status=Pending)` immediately
2. FCM `reward` push received → update to `RewardEvent(status=Confirmed, txSignature=...)`
3. FCM not received within 30s → poll `GET /v1/detection/{id}/status`
4. After 2 min → mark `RewardEvent(status=Failed)` with retry option

---

## 4. WalletPane UI Redesign

### 4.1 Design System Alignment

Extends the existing cyber-minimalist dark theme from Phase 4:

| Token | Value | Usage |
|---|---|---|
| `vgaGold` | `#F59E0B` (Amber-500) | $VGA balance number, token icon |
| `vgaMint` | `#10B981` (Emerald-500) | Confirmed reward events |
| `vgaBurn` | `#EF4444` (Red-500) | Burn events, spend indicators |
| `vgaPending` | `#6366F1` (Indigo-500) | Pending/processing states |
| `solanaGradient` | `#9945FF → #14F195` | Powered-by Solana indicator |

### 4.2 Screen Layout — Three Zones

```
┌─────────────────────────────────────────────────┐
│  ZONE A — BALANCE HERO (fixed height 180dp)     │
│  ┌─────────────────────────────────────────────┐│
│  │   [VGA icon]  7,241.500000                  ││
│  │               $VGA                          ││
│  │   ≈ $0.00 USD  ·  Wallet ready              ││
│  │   0x7PTU…8jf1  [copy] [QR]                 ││
│  └─────────────────────────────────────────────┘│
│                                                  │
│  ZONE B — ACTION ROW (48dp)                     │
│  [  Receive  ] [  Burn / Spend  ] [  Export  ]  │
│                                                  │
│  ZONE C — ACTIVITY FEED (scrollable)            │
│  PENDING REWARDS (if any)                        │
│  ┌──────────────────────────────────────────┐   │
│  │ 🟡 ·  +2.0 VGA  Pending confirmation     │   │
│  │       MEDIUM hazard · 2s ago             │   │
│  └──────────────────────────────────────────┘   │
│  RECENT ACTIVITY                                 │
│  ┌──────────────────────────────────────────┐   │
│  │ ✅   +5.0 VGA  Critical detection        │   │
│  │       First in area bonus included       │   │
│  │       tx: 4xHr…9Jk  ·  2h ago           │   │
│  └──────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────┐   │
│  │ 🔥  –1.0 VGA  AI Co-pilot session       │   │
│  │       Burned for premium feature         │   │
│  │       tx: 9mKp…3Wz  ·  1d ago           │   │
│  └──────────────────────────────────────────┘   │
│                                                  │
│  ── Powered by Solana ──────────────────────── │
└─────────────────────────────────────────────────┘
```

### 4.3 WalletUiState

```kotlin
data class WalletUiState(
    val walletState: WalletState = WalletState.Unprovisioned,
    val vgaBalance: BigDecimal = BigDecimal.ZERO,
    val usdEquivalent: BigDecimal? = null,          // null until price feed connected
    val publicKeyDisplay: String = "",               // truncated for display
    val pendingRewards: List<RewardEventUi> = emptyList(),
    val recentActivity: List<VgaTransactionUi> = emptyList(),
    val isSyncing: Boolean = false,
    val hasLowSolBalance: Boolean = false,           // warn user — gas fees
)

data class RewardEventUi(
    val detectionId: String,
    val amountVga: BigDecimal,
    val severity: HazardAlert.Severity,
    val status: RewardStatus,
    val timestampMs: Long,
    val txSignature: String?,
) {
    enum class RewardStatus { PENDING, CONFIRMED, FAILED }
}

data class VgaTransactionUi(
    val signature: String,
    val type: TransactionType,
    val amountVga: BigDecimal,
    val label: String,
    val timestampMs: Long,
) {
    enum class TransactionType { MINT, BURN, TRANSFER_IN, TRANSFER_OUT }
}
```

### 4.4 Composable Structure

```kotlin
// Replaces current WalletPane(payoutStatus: PayoutStatus)
@Composable
fun WalletPane(
    uiState: WalletUiState,
    onCopyAddress: () -> Unit,
    onShowQr: () -> Unit,
    onBurnForFeature: (featureId: String) -> Unit,
    onExportMnemonic: () -> Unit,
    onRetryReward: (detectionId: String) -> Unit,
    modifier: Modifier = Modifier,
)

// Internal composables:
@Composable private fun BalanceHeroCard(balance: BigDecimal, publicKey: String, ...)
@Composable private fun WalletActionRow(onReceive: () -> Unit, onBurn: () -> Unit, ...)
@Composable private fun PendingRewardCard(reward: RewardEventUi, onRetry: () -> Unit)
@Composable private fun ActivityFeedItem(tx: VgaTransactionUi)
@Composable private fun SolanaAttributionFooter()
@Composable private fun WalletProvisioningPlaceholder()  // shown during Creating state
```

### 4.5 Balance Number Animation

Use `animateFloatAsState` with a spring spec for the balance counter on reward confirmation — the number animates up, triggering a brief scale pulse on the VGA icon. This is a key delight moment.

```kotlin
val animatedBalance by animateFloatAsState(
    targetValue = uiState.vgaBalance.toFloat(),
    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
    label = "vgaBalance"
)
```

---

## 5. Backend Integration Contracts

### 5.1 New API Endpoints

```
POST /v1/wallet/register
  Request:  { userId, walletPublicKey, attestationSig, deviceId }
  Response: { success, ataAddress, rentLamports }

POST /v1/detection/submit  (extend existing detection submit)
  Add fields: { walletPublicKey, keystoreSig }

GET  /v1/detection/{id}/status
  Response: { status, txSignature?, rewardAmount?, failureReason? }

GET  /v1/wallet/{pubkey}/history?limit=50&before={sig}
  Response: { transactions: VgaTransaction[], cursor }
  Note: wraps Solana RPC getSignaturesForAddress with enriched labels

GET  /v1/wallet/price
  Response: { vgaUsdRate: BigDecimal, updatedAt: epoch }
  Source: Pyth Network oracle on-chain, cached 60s
```

### 5.2 Mint Authority Security

**Critical:** The mint authority private key (`7PTUbMJMWRwAixmkez2yBpsjovyAECtcXQHVYzAi8jf1`) must **never** reside in application code or a single server process.

Production security model:
- Convert to **3-of-5 multisig** using Squads v4 before mainnet launch
- Backend holds 2 of 5 keys on separate cloud regions (AWS KMS + GCP KMS)
- Remaining 3 keys held in cold storage hardware wallets (Ledger) by different team members
- Each mint requires 2 automated backend signatures + manual approval for batches > 1000 VGA/tx

For testnet: single key in AWS Secrets Manager is acceptable.

### 5.3 Associated Token Account Creation

New users lack an ATA for $VGA. The backend pays the rent exemption (~0.002 SOL) on registration:

```
Backend flow on POST /v1/wallet/register:
1. Check if ATA exists (getAccountInfo)
2. If not: build createAssociatedTokenAccount instruction
3. Backend pays from treasury SOL reserve wallet
4. Submit and confirm
5. Return ATA address to app
```

This eliminates any SOL funding requirement on the user — a critical UX improvement over raw Solana wallet onboarding.

---

## 6. WorkManager Sync Strategy

```kotlin
@HiltWorker
class WalletSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val walletRepository: WalletRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return runCatching { walletRepository.refreshBalance() }
            .fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
    }

    companion object {
        fun enqueue(workManager: WorkManager) {
            workManager.enqueueUniquePeriodicWork(
                "wallet_balance_sync",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<WalletSyncWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .build()
            )
        }
    }
}
```

FCM-triggered refresh on `type == "reward"` bypasses the 15-minute window for immediate feedback.

---

## 7. Gradle / Dependency Setup

### 7.1 New `libs.versions.toml` entries

```toml
[versions]
solana-kotlin = "0.2.5"          # metaplex-foundation/solana-kotlin (community SDK)
bouncycastle = "1.78.1"
androidx-biometric = "1.2.0-alpha05"

[libraries]
solana-kotlin = { group = "foundation.metaplex", name = "solana-kotlin", version.ref = "solana-kotlin" }
bouncycastle = { group = "org.bouncycastle", name = "bcprov-jdk18on", version.ref = "bouncycastle" }
androidx-biometric = { group = "androidx.biometric", name = "biometric-ktx", version.ref = "androidx-biometric" }
```

> **Note on Solana Android SDK landscape:** As of 2025, the Solana Mobile Foundation maintains `solana-mobile-wallet-adapter` for MWA protocol (connecting to Phantom-style wallets). For **embedded non-custodial wallets**, `metaplex-foundation/solana-kotlin` or a hand-rolled JSON-RPC client is more appropriate. Evaluate `solana-kotlin` vs `web3.kt` (SolanaLabs) against maintenance activity at implementation time.

### 7.2 `:core:wallet` build.gradle.kts

```kotlin
plugins {
    alias(libs.plugins.vigia.android.library)
    alias(libs.plugins.vigia.android.hilt)
}

android {
    namespace = "com.vigia.core.wallet"
    buildFeatures { buildConfig = true }
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.network)           // for Retrofit/OkHttp singleton
    implementation(libs.solana.kotlin)
    implementation(libs.bouncycastle)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.security.crypto)   // EncryptedSharedPreferences
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
}
```

---

## 8. Security Threat Model

| Threat | Mitigation |
|---|---|
| Private key extraction from device | Keys wrapped with Android Keystore AES-256-GCM hardware-backed key; plaintext never in JVM heap longer than signing operation |
| Replay attack on detection claims | Nonce per detectionId stored on-chain; backend rejects duplicate IDs |
| Sybil farming (many fake wallets) | Wallet registration requires authenticated session (Cognito JWT); one wallet per verified user account |
| GPS spoofing for first-detection bonus | Cross-validate with sensor telemetry (speed, accelerometer) — static GPS with moving sensor data is flagged |
| Backend compromise → unauthorized mints | Multisig mint authority; off-chain rate cap; on-chain max-supply program guard |
| Man-in-the-middle on RPC calls | TLS pinned to Helius/Solana Labs certificate; certificate pinning in OkHttp |
| Token dump / price manipulation | No liquidity on mainnet until governance vote; initial DEX listing gated |
| User loses phone | Encrypted mnemonic backup in EncryptedSharedPreferences; optional cloud backup via user-authenticated encrypted export |

---

## 9. Migration from Stripe PayoutStatus

The current `WalletPane` consumes `PayoutStatus` from `CopilotUiState`. Migration plan:

### Phase 5A (parallel run)
- Add `WalletUiState` alongside `PayoutStatus` in `CopilotUiState`
- `WalletPane` renders `WalletUiState` when wallet is Active, falls back to Stripe pane for Stripe-enrolled users

### Phase 5B (full cutover)
- Remove `PayoutStatus`, `StripePaySheet`, Stripe dependency from build
- `WalletPane` is the sole payout surface

### CopilotUiState change

```kotlin
// Before (Phase 4):
data class CopilotUiState(
    val payoutStatus: PayoutStatus = PayoutStatus.Idle,
    ...
)

// After (Phase 5B):
data class CopilotUiState(
    val walletUiState: WalletUiState = WalletUiState(),
    ...
)
```

---

## 10. Future / Post-MVP Innovations

### 10.1 Compressed NFT Badges (Bubblegum protocol)
Each unique hazard type first detected by a user mints them a compressed NFT badge (< $0.001 cost via state compression). Displayed on profile, tradeable on Tensor/Magic Eden.

### 10.2 Geo-staking (DePIN primitive)
Users stake $VGA into a geographic grid cell to signal commitment to coverage. Staked cells earn higher base rewards. Unstaking starts a 7-day cooldown. This creates a self-organizing coverage map without Vigia central coordination.

### 10.3 DAO Governance
Once supply > 10M VGA in circulation, token holders vote on:
- Reward rate schedule adjustments
- New burn-event types
- Treasury allocation for infrastructure

### 10.4 Privacy-Preserving Location Proofs
Replace raw GPS in detection payloads with a ZK-proof of "detection occurred within hex cell H-X" using Light Protocol (Solana ZK compression). Users prove they were in an area without revealing precise GPS.

### 10.5 Cross-Chain Bridge
Deploy a lock-and-mint bridge to Base (Ethereum L2) to enable $VGA to participate in broader DeFi liquidity — yield farming, collateral — while keeping the core reward loop on Solana for cost and speed.

---

## 11. Exit Criteria

| Criterion | Metric |
|---|---|
| Wallet provisioned on first launch | < 3s p99 (excluding network) |
| Balance visible after reward FCM | < 2s from FCM receive to UI update |
| Zero plaintext private key in logs | Security audit pass |
| Mint round-trip (detection → confirmed) | < 5s p95 on devnet |
| WalletPane recomposition on balance tick | 0 unnecessary recompositions (Layout Inspector) |
| Anti-gaming: duplicate detection rejected | 100% of geo-dedup cases |
| Balance sync on background refresh | WorkManager success rate > 99% |
| Mnemonic export requires biometric | No export path without biometric gate |

---

## 12. Implementation Sequence

| Sprint | Deliverable |
|---|---|
| S1 | `:core:wallet` module scaffold + `SolanaKeystoreManager` (Ed25519 key gen, Keystore wrap) |
| S1 | `SolanaRpcClient` (getBalance, getTokenAccountsByOwner) + devnet integration test |
| S2 | `WalletRepositoryImpl` + `WalletSyncWorker` + Hilt wiring |
| S2 | Backend: `POST /v1/wallet/register` + ATA creation service |
| S3 | Backend: extend detection submit with wallet sig; mint pipeline on approval |
| S3 | FCM `reward` message type handling in `VigiaFcmReceiver` → `WalletRepository.refreshBalance()` |
| S4 | `WalletPane` redesign (balance hero, activity feed, action row) |
| S4 | `WalletViewModel` + `WalletUiState` flows |
| S5 | Burn-for-feature flow (AI co-pilot session gate) |
| S5 | Mnemonic export UI (biometric gate + share sheet) |
| S6 | Testnet end-to-end validation + security audit |
| S7 | Multisig mint authority setup (Squads v4) before mainnet |

---

*Document version: 1.0.0 — 2026-06-13*  
*Author: Design session with Claude Sonnet 4.6*  
*Treasury public key: `7PTUbMJMWRwAixmkez2yBpsjovyAECtcXQHVYzAi8jf1` (testnet — rotate before mainnet)*
