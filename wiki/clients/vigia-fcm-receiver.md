---
title: "VigiaFcmReceiver"
type: client
tags: [client, network]
source: "core/network/src/main/kotlin/com/vigia/core/network/fcm/VigiaFcmReceiver.kt"
related: ["[[mqtt-alert-repository]]", "[[firebase-fcm]]", "[[core-network]]"]
updated: 2026-06-20
---

# VigiaFcmReceiver

`FirebaseMessagingService` subclass. Receives FCM push messages and injects them into `MqttAlertRepository` when MQTT is disconnected (e.g., during Android Doze mode).

## onMessageReceived

Parses FCM `RemoteMessage.data` map → `HazardAlert`, then calls `mqttAlertRepository.injectAlert(alert)`. Also calls `mqttAlertRepository.reconnect()` to re-establish the MQTT connection.

## Links

[[mqtt-alert-repository]] [[firebase-fcm]] [[hazard-alert-model]] [[di-network-module]]
[[flow-mqtt-hazard-alert]]
