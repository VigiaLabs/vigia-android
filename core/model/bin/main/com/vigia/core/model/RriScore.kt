package com.vigia.core.model

@JvmInline
value class RriScore(val value: Float) {
    init {
        require(value in 0f..1f) { "RriScore must be in [0.0, 1.0], got $value" }
    }
}
