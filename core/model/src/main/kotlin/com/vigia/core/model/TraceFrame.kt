package com.vigia.core.model

data class TraceFrame(
    val location: LocationSnapshot,
    val rriScore: Float,
    val timestampMs: Long,
)
