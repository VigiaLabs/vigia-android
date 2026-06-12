package com.vigia.core.model

data class HazardAlert(
    val id: String,
    val severity: Severity,
    val messageText: String,
    val timestampMs: Long,
    val locationSnapshot: LocationSnapshot?,
) {
    enum class Severity { LOW, MEDIUM, HIGH, CRITICAL }
}
