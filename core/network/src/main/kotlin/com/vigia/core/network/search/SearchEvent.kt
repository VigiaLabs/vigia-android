package com.vigia.core.network.search

/**
 * Mirrors the SSE event taxonomy emitted by the VIGIASearch Fargate endpoint at /v1/search.
 *
 * Wire format (text/event-stream):
 *   event: step     → {"step":"...", "ts":1234}
 *   event: text     → {"delta":"..."}
 *   event: metadata → {"sources":[...], "spatialMarkers":[...], "totalLatencyMs":5000, ...}
 *   event: done     → {}
 */
sealed interface SearchEvent {

    /** An intermediate reasoning step emitted before the final answer. */
    data class Step(
        val message: String,
        val timestampMs: Long,
    ) : SearchEvent

    /** A token (or small chunk) of the streamed answer text. Accumulate in the ViewModel. */
    data class TextDelta(val delta: String) : SearchEvent

    /** Final metadata delivered after all TextDelta events. */
    data class Metadata(
        val sources: List<Source>,
        val claims: List<EvidenceClaim> = emptyList(),
        val offline: OfflineEvidence? = null,
        val spatialMarkers: List<SpatialMarker>,
        val totalLatencyMs: Long,
        val contradictionVerified: Boolean,
    ) : SearchEvent

    /** Terminal event — the flow completes immediately after this is emitted. */
    data object Done : SearchEvent

    // ── supporting types ──────────────────────────────────────────────────────

    data class Source(
        val id: String,
        val label: String,
        val trustLevel: String,
        val url: String,
    )

    data class EvidenceClaim(
        val category: String,
        val status: String,
        val subject: String,
        val predicate: String,
        val value: String?,
        val unit: String?,
        val role: String?,
        val financialType: String?,
        val maintenanceType: String?,
        val dateKind: String?,
        val observedAt: String?,
        val sourceId: String,
        val sourceQuote: String,
        val sourceLocator: String?,
        val retrievedAt: String,
    )

    data class OfflineEvidence(
        val mode: String,
        val lastSyncAt: Long?,
        val cacheAgeHours: Long?,
        val packVersion: String?,
        val stale: Boolean,
    )

    data class SpatialMarker(
        val id: String,
        val title: String,
        val lat: Double,
        val lng: Double,
        val type: String,
        val severity: String,
        val summary: String,
    )
}
