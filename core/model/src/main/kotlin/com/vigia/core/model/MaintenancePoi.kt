package com.vigia.core.model

data class MaintenancePoi(
    val id: String,
    val location: LocationSnapshot,
    val priority: Priority,
    val category: String,
    val reportedMs: Long,
) {
    enum class Priority { HIGH, MEDIUM, LOW }
}
