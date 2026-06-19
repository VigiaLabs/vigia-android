---
title: "Firebase Cloud Messaging (FCM)"
type: external
tags: [external, network]
source: "core/network/src/main/kotlin/com/vigia/core/network/fcm/VigiaFcmReceiver.kt"
related: ["[[vigia-fcm-receiver]]", "[[mqtt-alert-repository]]", "[[flow-mqtt-hazard-alert]]"]
updated: 2026-06-20
---

# Firebase Cloud Messaging (FCM)

Firebase BOM `33.14.0`. `firebase-messaging-ktx` provides `FirebaseMessagingService` for push notifications.

Used as a fallback hazard alert delivery path when Android Doze mode kills the MQTT connection. `VigiaFcmReceiver` injects the parsed `HazardAlert` into `MqttAlertRepository.injectAlert()` so the same downstream flow (TTS, orb state) applies regardless of delivery path.

`google-services.json` must be placed in `app/` (gitignored). The `google-services` Gradle plugin is currently commented out in `app/build.gradle.kts` pending provisioning.

## Links

[[vigia-fcm-receiver]] [[mqtt-alert-repository]] [[flow-mqtt-hazard-alert]] [[core-network]]
