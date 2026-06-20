package com.vigia.core.model

data class VigiaSearchContext(
    val queryText: String,
    val timestampMs: Long,
    val location: LocationSnapshot,
    val velocityMs: Float,
    val rriScore: RriScore,
    val spatialLatentVector: SpatialLatentVector,
    val conversationHistory: List<ConversationTurn> = emptyList(),
    val routeAheadHazards: List<RouteAheadHazard> = emptyList(),
)
