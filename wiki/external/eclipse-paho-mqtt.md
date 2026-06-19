---
title: "Eclipse Paho MQTT v3"
type: external
tags: [external, network]
source: "core/network/src/main/kotlin/com/vigia/core/network/mqtt/MqttAlertRepositoryImpl.kt"
related: ["[[mqtt-alert-repository]]", "[[flow-mqtt-hazard-alert]]"]
updated: 2026-06-20
---

# Eclipse Paho MQTT v3

`org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5`. Battle-tested Android MQTT client available on Maven Central.

Used for: persistent TLS connection to AWS IoT Core, QoS 1 subscribe to `vigia/alerts/{userId}`, file-based persistence for in-flight QoS 1 messages. `cleanSession=false` preserves subscriptions across reconnects.

## Links

[[mqtt-alert-repository]] [[flow-mqtt-hazard-alert]] [[aws-backend]] [[core-network]]
