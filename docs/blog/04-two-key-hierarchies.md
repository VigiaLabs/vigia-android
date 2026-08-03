# Episode 4: Two identities, two key hierarchies, no secrets in the APK

*Why the device's identity key lives in hardware and can never be exported, why the wallet key can't, and why the app ships with no third-party credentials at all.*

An app that signs road data on behalf of a device, manages a rewards wallet, and calls paid AI services has three different secrets to worry about, and the lazy answer — put them all in one place, protect them the same way — is wrong for every one of them. This post is about how VIGIA Mobile ended up with two deliberately different key hierarchies and zero API keys in the shipped binary, and about being honest where the platform forced a compromise.

This is Episode 4 of 5 in the Engineering VIGIA Mobile series. The full system overview is in the master post.

## The device identity key: hardware, non-exportable, by design

The key that establishes *this device* — the one used in the BLE handshake from Episode 3 — is an elliptic-curve P-256 key generated directly inside the Android Keystore, with StrongBox (the dedicated secure element) preferred and a silent fallback to the Keystore's TEE when a device lacks StrongBox. It is created for key-agreement and signing, and its private bytes never leave the secure hardware.

This is the strong case, and we leaned into it: the device's identity can sign challenges and derive session keys, but there is no code path — not ours, not an attacker's with the unlocked app — that can read the raw key out. The identity is bound to the hardware, not to the app's memory.

## The wallet key: software, because the platform gives us no choice

The wallet is a different story, and the honest version is more interesting than a clean one. The wallet identity is an **Ed25519** keypair, because that is the signature scheme the reward backend and its Solana-style settlement expect — fast, compact 64-byte signatures, base58-encoded. The catch is that the Android Keystore does not support Ed25519 as a hardware key type. You cannot generate or store an Ed25519 key in the secure element the way you can a P-256 key.

So we could not give the wallet the same guarantee as the device identity, and rather than pretend otherwise we built the next-best thing and documented what it costs. The Ed25519 keypair is generated in software; its private key is then **AES-256-GCM encrypted with a wrapping key that *is* hardware-resident and non-exportable**, and only the ciphertext is persisted. At rest, the wallet key is protected by hardware — the ciphertext is useless without the Keystore AES key, which never leaves the TEE, so a stolen copy of the app's storage is inert, and the wrapping is even bound to the device, so a backup restored onto another phone can't use it.

The honest cost: to sign, the app must decrypt the private key into ordinary memory for the duration of the operation. Unlike the P-256 device key, the raw Ed25519 key does briefly exist in software at generation and on each signature. We treat that as a known, bounded exposure — protect the ciphertext in hardware, minimise and account for the window when the plaintext is live — rather than as something to paper over. When the platform can't give you a hardware guarantee, the right move is to state the boundary of what you actually have.

## What the keys are *for*: proving possession without a shared secret

Both key hierarchies exist to serve one pattern, the same one from Episode 3: the backend should be able to trust a request without the app carrying any shared secret. So every telemetry upload and every wallet operation is signed, and the server verifies the signature against the device's or wallet's public key. There is no symmetric token in the app that, if extracted, would let someone impersonate the device. Possession of the private key *is* the credential, and possession is proven by signing, never by sending the key.

## The secrets that aren't there at all

The third category of secret is the one for paid third-party services — the Sarvam voice API, the cloud AI calls. The strongest thing you can do with a credential is not ship it. So the app contains **none** of them. Every call to a paid AI service goes through the VIGIA backend, which holds those keys server-side and exposes a proxy the app talks to after authenticating with its own Cognito token. Decompile the APK and you will not find a Sarvam key or a cloud credential, because they were never there — the app is only ever trusted to talk to *our* backend, and the backend is trusted to talk to the vendors.

## Takeaway

Three secrets, three different answers. The device identity goes in hardware because the platform lets it, and gets the full non-exportable guarantee. The wallet key can't, because its algorithm isn't hardware-native, so it settles for a hardware-wrapped ciphertext and an honest note about the signing-time exposure. And the third-party API keys get the strongest protection of all — they are not in the app to begin with. The principle underneath is to match each secret to the best protection the platform actually offers it, and to be precise about the difference rather than claim one guarantee for all three.

The code is open at [github.com/VigiaLabs/vigia-android](https://github.com/VigiaLabs/vigia-android).

*Engineering VIGIA Mobile · Episode 4 of 5 — Previous: Episode 3. Next: Episode 5, Never drop a token, always deliver the warning.*
