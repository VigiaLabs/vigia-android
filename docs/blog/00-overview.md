# Engineering the VIGIA copilot: an in-vehicle AI that runs hands-free, offline-tolerant, and paired to a road sensor

*How we built an Android app that talks to a driver, a Raspberry Pi, and a cloud brain at once, and the five design decisions behind it.*

Most "AI assistant" apps assume a user who is sitting still, looking at a screen, with a good network connection and both hands free. A driver has none of that. They are moving, their eyes belong on the road, connectivity drops in exactly the places where hazards are worst, and the useful information — a pothole, a stray animal, a road's safety rating — is often about the few hundred metres directly ahead. The VIGIA mobile app is our attempt to build a copilot for that person.

This is the overview. It links out to five deep dives, each on one decision that shaped the app. The code is open at [github.com/VigiaLabs/vigia-android](https://github.com/VigiaLabs/vigia-android).

## What the app does

VIGIA Mobile is the Android companion to the VIGIA road-intelligence system. It pairs over Bluetooth with a Raspberry Pi "black box" running computer vision at the kerb, streams that live road context into a voice copilot, answers a driver's spoken questions about the road ahead through the VIGIASearch backend, and speaks the answer back — all without the driver touching the screen. It receives real-time hazard alerts over MQTT and reads the critical ones aloud, and it manages an on-device rewards wallet for contributing road data.

The shape is three tiers meeting in one app. Below it, a Pi edge node feeds telemetry over an authenticated Bluetooth link. Above it, a cloud backend answers queries and pushes alerts. In the middle, the app fuses the two — the driver's spoken question is enriched with the vehicle's actual GPS, speed, and road-roughness reading before it ever reaches the cloud, so every answer is grounded in where the car really is. Internally it is ten Gradle modules: a shell, six `core` libraries, and three `feature` modules.

## The five decisions worth reading about

Rather than one long article, we pulled out the five choices that were genuinely non-obvious, the ones where we picked the harder path for a reason. Each is a standalone post.

**Episode 1: Why a phone app has nine modules.** A single-module app would have shipped faster. We split into a strict `core`/`feature` graph enforced by convention plugins, so that four people could build BLE, voice, wallet, and maps in parallel without stepping on each other, and so no feature could quietly reach into another. *[Read Episode 1 →](https://ridingbluewaves.hashnode.dev/why-a-phone-app-has-nine-modules)*

**Episode 2: A conversation that survives the driver never touching the screen.** The copilot runs a full hands-free loop — listen, transcribe, stream the answer, speak it, and reopen the mic — with barge-in so the driver can interrupt the AI mid-sentence. The hard parts were the state machine and the rule that the microphone reopens itself. *[Read Episode 2 →](https://ridingbluewaves.hashnode.dev/a-hands-free-copilot-that-never-needs-the-screen)*

**Episode 3: Why the phone and the Pi never share a secret.** Our first pairing design used a shared HMAC key. It was cryptographically impossible on hardware-backed keys, and finding out why pushed us to a proper asymmetric handshake: ECDH over P-256, mutual ECDSA challenge-response, and a session key neither side can exfiltrate. *[Read Episode 3 →](https://ridingbluewaves.hashnode.dev/why-the-phone-and-the-pi-never-share-a-secret)*

**Episode 4: Two identities, two key hierarchies, no secrets in the APK.** The device's identity key lives in hardware and can never be exported; the wallet key can't, because its algorithm isn't hardware-native, so it is software-wrapped instead — and we were honest about what that costs. Meanwhile no third-party API key ships in the app at all. *[Read Episode 4 →](https://ridingbluewaves.hashnode.dev/two-key-hierarchies-and-no-secrets-in-the-apk)*

**Episode 5: Never drop a token, always deliver the warning.** A moving vehicle loses signal constantly, so the app is built to degrade instead of fail: streamed answers are persisted token-by-token so a mid-sentence network drop loses nothing, and a critical hazard alert will pre-empt whatever the copilot is currently saying to get the warning out. *[Read Episode 5 →](https://ridingbluewaves.hashnode.dev/never-drop-a-token-always-deliver-the-warning)*

## The thread running through all five

Looking back, the same instinct shows up in every one of these decisions. Design for the adversarial version of the environment, not the demo version. Assume the network will drop, the driver's hands are busy, the peer on the other end of the Bluetooth link might be lying, and the phone itself could be lost — then make the architecture degrade gracefully under each of those instead of assuming none of them happen. Modularity so the team can move fast without coupling; a voice loop that closes itself so the driver never reaches for the screen; a handshake that trusts no shared secret; keys placed in hardware wherever the platform allows and honestly accounted for where it doesn't; and a data path that treats a lost connection as a normal event, not an error.

None of these are exotic on their own. The interesting part was deciding, for software that rides in a moving car, where to spend engineering effort on resilience and trust that a stationary app would never need.

The full app is open at [github.com/VigiaLabs/vigia-android](https://github.com/VigiaLabs/vigia-android). This is part of an ongoing build series.

---

## 🎓 CS Fundamentals — study companion

*This overview frames a mobile client that fuses **OS**, **Computer Networks**, **Security**, and **Software Architecture**. The episodes go deep; read this before mobile/systems interviews.*

### System Design (mobile client)
- **Three-tier fusion on one device.** The app sits between a Pi edge node (below, over Bluetooth) and a cloud backend (above, over HTTPS/MQTT), fusing both in real time. The design constraint that shapes everything: **the environment is adversarial** — the network drops, hands are busy, the Bluetooth peer might lie, the phone could be lost. Each episode hardens one of those.
- **Client-side responsibilities.** Unlike a server, a mobile client must handle intermittent connectivity, constrained battery/CPU, OS lifecycle (backgrounding), and on-device secrets — a distinct discipline from backend engineering.

### ⚖️ This vs That — the guiding principle
| Decision | Alternatives | Why this choice |
|---|---|---|
| **Design for the adversarial environment** | Design for the demo (good network, hands free, honest peer) | A driving copilot lives in the worst case: dropped signal, eyes on the road, untrusted hardware. Building for the happy path means failing exactly when it matters. |

**The one to defend:** *build for the failure modes, not the demo.* Every subsystem — modules for parallel dev, a self-closing voice loop, a no-shared-secret handshake, hardware-backed keys, resilient streaming — is one instance of assuming the hostile case and degrading gracefully.
