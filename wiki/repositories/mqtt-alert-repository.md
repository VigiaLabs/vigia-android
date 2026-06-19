---
title: "MqttAlertRepository"
type: repository
tags: [repository, network]
source: "core/network/src/main/kotlin/com/vigia/core/network/mqtt/MqttAlertRepository.kt"
related: ["[[core-network]]", "[[copilot-viewmodel]]", "[[vigia-fcm-receiver]]", "[[hazard-alert-model]]"]
updated: 2026-06-20
---

# MqttAlertRepository

Interface + `MqttAlertRepositoryImpl` (`@Singleton`). Persistent Eclipse Paho MQTT v3 client subscribed to `vigia/alerts/{userId}`, QoS 1.

## Interface

```kotlin
val alerts: SharedFlow<HazardAlert>
fun connect(userId: String)
fun reconnect()
fun disconnect()
suspend fun injectAlert(alert: HazardAlert)  // FCM fallback path
```

## Wire Format

JSON: `{"id":"...","severity":"HIGH","message":"...","ts":1234567890,"lat":28.6,"lng":77.2}`

## Connection Options

`cleanSession=false`, `isAutomaticReconnect=true`, `keepAliveInterval=30`, `maxInflight=10`. Uses `MqttDefaultFilePersistence` (file-based) for QoS 1 in-flight durability. TLS: `SSLContext("TLSv1.2")` with Android system trust store for `ssl://` URIs.

## FCM Fallback

`VigiaFcmReceiver` calls `injectAlert()` when an FCM message arrives during Doze mode and MQTT is disconnected.

## Links

[[core-network]] [[copilot-viewmodel]] [[vigia-fcm-receiver]] [[hazard-alert-model]]
[[flow-mqtt-hazard-alert]] [[di-network-module]] [[eclipse-paho-mqtt]]
