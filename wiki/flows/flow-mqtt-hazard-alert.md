---
title: "Flow: MQTT Hazard Alert → TTS"
type: flow
tags: [flow, alerts]
source: "core/network/src/main/kotlin/com/vigia/core/network/mqtt/MqttAlertRepositoryImpl.kt"
related: ["[[mqtt-alert-repository]]", "[[vigia-fcm-receiver]]", "[[copilot-viewmodel]]", "[[tts-manager]]"]
updated: 2026-06-20
---

# Flow: MQTT Hazard Alert → TTS

Real-time hazard delivery pipeline from AWS IoT Core to audible TTS on the device.

## Primary Path (MQTT)

1. Pi 5 or cloud backend publishes JSON to `vigia/alerts/{userId}` (QoS 1)
2. `MqttAlertRepositoryImpl.callback.messageArrived()` receives the message
3. `parseAlert(payload)` → `HazardAlert` (or null on parse error)
4. `_alerts.emit(alert)` on `SharedFlow`
5. `CopilotViewModel.observeAlerts()` (collectLatest) receives `HazardAlert`
6. Prepend to `pendingAlerts` list (keep latest 10)
7. `TtsManager.speak(alert.messageText, queueMode)`:
   - CRITICAL → `QUEUE_FLUSH` (interrupts any current TTS)
   - others → `QUEUE_ADD`
8. If severity >= HIGH → `OrbState.Alert`

## FCM Fallback Path (Doze mode)

1. FCM message arrives → `VigiaFcmReceiver.onMessageReceived()`
2. Parses FCM data map → `HazardAlert`
3. `mqttAlertRepository.injectAlert(alert)` → emits on same `_alerts` SharedFlow
4. `mqttAlertRepository.reconnect()` — re-establishes MQTT connection

## Links

[[mqtt-alert-repository]] [[vigia-fcm-receiver]] [[copilot-viewmodel]] [[tts-manager]]
[[hazard-alert-model]] [[eclipse-paho-mqtt]] [[firebase-fcm]] [[aws-backend]]
