# VIGIA — Implementation Gaps & Deferred Work

Last updated: 2026-06-21

## ✅ Resolved (this session)
- **TTS speaking rate per driver profile** — `DriverProfile.ttsRate` now flows through
  `SarvamTtsClient.synthesize(pace=)` → `SarvamTtsClientImpl` → `TtsManager.speakSarvam()`.
- **BLE ECDH application-layer handshake** — already implemented in `BleLinkManager`
  (HELLO / CHALLENGE / RESPONSE / BOUND).
- **CDM presence → BLE auto-connect** — already wired in `VigiaForegroundService.observePresence()`.

## ⏳ Blocked on external credentials (revisit later)

### 1. FCM push (Doze wakeup) — needs `google-services.json`
- Firebase console → create project → add Android app (`com.vigia.copilot`)
- Download `google-services.json` → drop into `app/`
- ~5 min, unblocks push notifications immediately.

### 2. MQTT mTLS telemetry — needs AWS IoT Core X.509 cert
```bash
aws iot create-keys-and-certificate --set-as-active \
  --certificate-pem-outfile device.crt \
  --public-key-outfile device.pub \
  --private-key-outfile device.key
aws iot attach-policy --policy-name VigiaDevicePolicy --target <cert-arn>
```
- Prefer fetching cert from AWS Secrets Manager at runtime — do NOT bake into APK.

### 3. HTTPS on ALB — needs ACM cert + domain
```bash
aws acm request-certificate --domain-name api.<domain> --validation-method DNS
```
- Requires a registered domain (Route 53 ~$12/yr for .com). Attach cert to ALB :443 listener.

### 4. Stripe payout endpoint — needs Stripe secret key
- Stripe dashboard → Developers → API keys → copy secret key
```bash
aws secretsmanager create-secret --name vigia/stripe-secret-key --secret-string "sk_live_..."
```
- Backend (Lambda/ECS) reads it via GetSecretValue at runtime. Never in code/APK.

## 🔮 Out of scope (future hardware)
- OBD-II integration (M12)
- Cabin DMS / driver monitoring (M13)
