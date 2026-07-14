package com.vigia.core.network.search

import com.vigia.core.model.VigiaSearchContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Streams VIGIASearch responses from the Fargate endpoint over SSE.
 * Uses the auth-intercepted [OkHttpClient] so every search request carries
 * the Cognito ID token required by the API Gateway Cognito authorizer.
 *
 * SSE wire format at POST /v1/search (Content-Type: text/event-stream):
 *   event: step     → {"step":"...", "ts":1234}
 *   event: text     → {"delta":"..."}
 *   event: metadata → {"sources":[...], "spatialMarkers":[...], "totalLatencyMs":N, ...}
 *   event: done     → {}
 *
 * The underlying OkHttp call is cancelled immediately when the collecting coroutine is cancelled.
 * ALB idle timeout is 300s server-side; OkHttp readTimeout is set to 120s in NetworkModule.
 */
@Singleton
class OkHttpSseSearchClient @Inject constructor(
    @Named("VigiaOkHttpClient") private val okHttpClient: OkHttpClient,
    @Named("VigiaApiBaseUrl") private val baseUrl: String,
) : VigiaSearchClient {

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    override fun search(context: VigiaSearchContext): Flow<SearchEvent> = callbackFlow {
        val requestBody = buildRequestBody(context).toRequestBody(jsonMedia)
        val request = Request.Builder()
            .url("$baseUrl/v1/search")
            .post(requestBody)
            .header("Accept", "text/event-stream")
            .header("Cache-Control", "no-cache")
            .build()

        val call = okHttpClient.newCall(request)

        withContext(Dispatchers.IO) {
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("VIGIASearch HTTP ${response.code}")
                    }
                    val responseBody = response.body
                        ?: throw IOException("VIGIASearch: empty response body")

                    val reader: BufferedReader = responseBody.byteStream().bufferedReader()
                    var currentEvent = ""
                    val dataBuffer = StringBuilder()

                    var line: String? = reader.readLine()
                    while (line != null) {
                        when {
                            line.startsWith("event:") -> {
                                currentEvent = line.removePrefix("event:").trim()
                            }
                            line.startsWith("data:") -> {
                                dataBuffer.append(line.removePrefix("data:").trim())
                            }
                            line.isEmpty() && dataBuffer.isNotEmpty() -> {
                                val event = parseEvent(currentEvent, dataBuffer.toString())
                                if (event != null) {
                                    // trySendBlocking (not trySend) so a fast burst of
                                    // text deltas can never overflow the channel and
                                    // silently drop events — most importantly the final
                                    // Done event, whose loss left the UI stuck on
                                    // "Generating response". Paired with an UNLIMITED
                                    // buffer below, this never actually blocks.
                                    trySendBlocking(event)
                                    if (event is SearchEvent.Done) {
                                        close()
                                        return@withContext
                                    }
                                }
                                currentEvent = ""
                                dataBuffer.clear()
                            }
                        }
                        line = reader.readLine()
                    }
                    close()
                }
            } catch (e: Exception) {
                close(e)
            }
        }

        awaitClose { call.cancel() }
    }
        // UNLIMITED capacity fuses with callbackFlow's internal channel so the
        // server's sub-second burst of ~200+ text deltas is fully buffered and
        // no event (especially Done) is ever dropped under back-pressure.
        .buffer(Channel.UNLIMITED)

    // ── SSE parsing ───────────────────────────────────────────────────────────

    internal fun parseEvent(type: String, data: String): SearchEvent? =
        try {
            when (type) {
                "step" -> {
                    val obj = JSONObject(data)
                    SearchEvent.Step(
                        message     = obj.optString("step"),
                        timestampMs = obj.optLong("ts"),
                    )
                }
                "text" -> SearchEvent.TextDelta(delta = JSONObject(data).optString("delta"))
                "metadata" -> parseMetadata(JSONObject(data))
                "done"     -> SearchEvent.Done
                else       -> null
            }
        } catch (_: Exception) {
            null
        }

    private fun parseMetadata(obj: JSONObject): SearchEvent.Metadata =
        SearchEvent.Metadata(
            sources               = obj.optJSONArray("sources")?.let(::parseSourceArray) ?: emptyList(),
            claims                = obj.optJSONArray("claims")?.let(::parseClaimArray) ?: emptyList(),
            offline               = obj.optJSONObject("offline")?.let(::parseOfflineEvidence),
            spatialMarkers        = obj.optJSONArray("spatialMarkers")?.let(::parseMarkerArray) ?: emptyList(),
            totalLatencyMs        = obj.optLong("totalLatencyMs"),
            contradictionVerified = obj.optBoolean("contradictionVerified"),
        )

    private fun parseClaimArray(arr: JSONArray): List<SearchEvent.EvidenceClaim> =
        (0 until arr.length()).map { index ->
            val obj = arr.getJSONObject(index)
            SearchEvent.EvidenceClaim(
                category        = obj.optString("category"),
                status          = obj.optString("status"),
                subject         = obj.optString("subject"),
                predicate       = obj.optString("predicate"),
                value           = obj.opt("value")?.takeUnless { it == JSONObject.NULL }?.toString(),
                unit            = obj.optNullableString("unit"),
                role            = obj.optNullableString("role"),
                financialType   = obj.optNullableString("financialType"),
                maintenanceType = obj.optNullableString("maintenanceType"),
                dateKind        = obj.optNullableString("dateKind"),
                observedAt      = obj.optNullableString("observedAt"),
                sourceId        = obj.optString("sourceId"),
                sourceQuote     = obj.optString("sourceQuote"),
                sourceLocator   = obj.optNullableString("sourceLocator"),
                retrievedAt     = obj.optString("retrievedAt"),
            )
        }

    private fun parseOfflineEvidence(obj: JSONObject) = SearchEvent.OfflineEvidence(
        mode          = obj.optString("mode"),
        lastSyncAt    = obj.optLongOrNull("lastSyncAt"),
        cacheAgeHours = obj.optLongOrNull("cacheAgeHours"),
        packVersion   = obj.optNullableString("packVersion"),
        stale         = obj.optBoolean("stale"),
    )

    private fun JSONObject.optNullableString(key: String): String? =
        if (has(key) && !isNull(key)) optString(key) else null

    private fun JSONObject.optLongOrNull(key: String): Long? =
        if (has(key) && !isNull(key)) optLong(key) else null

    private fun parseSourceArray(arr: JSONArray): List<SearchEvent.Source> =
        (0 until arr.length())
            .map { i ->
                val o = arr.getJSONObject(i)
                SearchEvent.Source(
                    id         = o.optString("id"),
                    label      = o.optString("label"),
                    trustLevel = o.optString("trustLevel"),
                    url        = o.optString("url"),
                )
            }
            .distinctBy { it.id }

    private fun parseMarkerArray(arr: JSONArray): List<SearchEvent.SpatialMarker> =
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            SearchEvent.SpatialMarker(
                id       = o.optString("id"),
                title    = o.optString("title"),
                lat      = o.optDouble("lat"),
                lng      = o.optDouble("lng"),
                type     = o.optString("type"),
                severity = o.optString("severity"),
                summary  = o.optString("summary"),
            )
        }

    // ── Request serialisation ─────────────────────────────────────────────────

    internal fun buildRequestBody(ctx: VigiaSearchContext): String {
        val contextObj = JSONObject().apply {
            put("locationLat",    ctx.location.latitudeDeg)
            put("locationLng",    ctx.location.longitudeDeg)
            put("accuracyMeters", ctx.location.accuracyMeters)
            put("bearingDeg",     ctx.location.bearingDeg)
            put("velocityMs",     ctx.velocityMs)
            put("rriScore",       ctx.rriScore.value)
            put("timestampMs",    ctx.timestampMs)
        }
        val historyArr = JSONArray().also { arr ->
            ctx.conversationHistory.forEach { turn ->
                arr.put(JSONObject().apply {
                    put("role", turn.role.name.lowercase())
                    put("text", turn.text)
                })
            }
        }
        val hazardsArr = JSONArray().also { arr ->
            ctx.routeAheadHazards.forEach { h ->
                arr.put(JSONObject().apply {
                    put("geohash",       h.geohash)
                    put("distance_m",    h.distanceMeters)
                    put("hazard_type",   h.hazardType)
                    put("severity",      h.severity.name)
                    put("avg_rri",       h.avgRriScore)
                    put("eta_s",         h.etaSeconds)
                })
            }
        }
        return JSONObject().apply {
            put("query",             ctx.queryText)
            put("context",           contextObj)
            put("conversationHistory", historyArr)
            put("routeAheadHazards", hazardsArr)
        }.toString()
    }
}
