package com.vigia.core.network.search

import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.vigia.core.model.ConversationTurn
import com.vigia.core.model.HazardAlert
import com.vigia.core.model.LocationSnapshot
import com.vigia.core.model.RouteAheadHazard
import com.vigia.core.model.RriScore
import com.vigia.core.model.SpatialLatentVector
import com.vigia.core.model.VigiaSearchContext

/**
 * Tests that the VIGIASearch SSE client correctly:
 * 1. Parses every SSE event type the Bedrock Agent backend emits
 * 2. Assembles VigiaSearchContext with all ADAS-critical fields
 * 3. Handles edge cases: empty hazard lists, degraded (RRI-only) frames,
 *    multi-turn conversation history, high-fatigue context
 *
 * Both `buildRequestBody` and `parseEvent` are `internal`, so tests in the
 * same Gradle module (`core:network`) can reach them on the JVM without reflection.
 */
class SseParsingTest {

    // Real OkHttpClient — buildRequestBody/parseEvent never make HTTP calls.
    // A mock would require the inline mock-maker agent for final-class mocking on JVM.
    private val client = OkHttpSseSearchClient(
        okHttpClient = OkHttpClient(),
        baseUrl      = "https://test.vigia.local",
    )

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun makeContext(
        query: String,
        lat: Double = 12.9716,
        lon: Double = 77.5946,
        speedMs: Float = 13.9f,
        rri: Float = 0.72f,
        hazards: List<RouteAheadHazard> = emptyList(),
        history: List<ConversationTurn> = emptyList(),
    ) = VigiaSearchContext(
        queryText           = query,
        timestampMs         = 1_750_000_000_000L,
        location            = LocationSnapshot(
            latitudeDeg    = lat,
            longitudeDeg   = lon,
            accuracyMeters = 4.2f,
            bearingDeg     = 145f,
            velocityMs     = speedMs,
            timestampMs    = 1_750_000_000_000L,
        ),
        velocityMs          = speedMs,
        rriScore            = RriScore(rri),
        spatialLatentVector = SpatialLatentVector(256, FloatArray(256), 1_750_000_000_000L),
        conversationHistory = history,
        routeAheadHazards   = hazards,
    )

    private fun hazard(
        type: String,
        distM: Float,
        severity: HazardAlert.Severity = HazardAlert.Severity.MEDIUM,
    ) = RouteAheadHazard(
        geohash        = "tf1uk",
        distanceMeters = distM,
        hazardType     = type,
        severity       = severity,
        avgRriScore    = 0.55f,
        reportCount    = 3,
        lastSeenMs     = 1_750_000_000_000L,
        etaSeconds     = distM / 13.9f,
    )

    // ── 1. Context request-body assembly ─────────────────────────────────────

    @Test
    fun `context includes location lat lon velocity and rri`() {
        val body = JSONObject(client.buildRequestBody(makeContext("is the road ahead safe?", speedMs = 16.7f, rri = 0.68f)))
        val ctx  = body.getJSONObject("context")
        assertEquals(12.9716, ctx.getDouble("locationLat"), 0.00001)
        assertEquals(77.5946, ctx.getDouble("locationLng"), 0.00001)
        assertEquals(16.7,    ctx.getDouble("velocityMs"),  0.01)
        assertEquals(0.68,    ctx.getDouble("rriScore"),    0.001)
    }

    @Test
    fun `context encodes hazard array with distance severity and type`() {
        val ctx = makeContext(
            "should I slow down?",
            hazards = listOf(
                hazard("POTHOLE", 180f, HazardAlert.Severity.HIGH),
                hazard("DEBRIS",  420f, HazardAlert.Severity.MEDIUM),
            ),
        )
        val arr = JSONObject(client.buildRequestBody(ctx)).getJSONArray("routeAheadHazards")

        assertEquals(2, arr.length())
        assertEquals("POTHOLE", arr.getJSONObject(0).getString("hazard_type"))
        assertEquals(180.0,     arr.getJSONObject(0).getDouble("distance_m"), 0.1)
        assertEquals("HIGH",    arr.getJSONObject(0).getString("severity"))
        assertEquals(420.0,     arr.getJSONObject(1).getDouble("distance_m"), 0.1)
    }

    @Test
    fun `context serialises conversation history with role and text`() {
        val history = listOf(
            ConversationTurn(ConversationTurn.Role.USER,      "what's ahead?",         1_750_000_000_000L),
            ConversationTurn(ConversationTurn.Role.ASSISTANT, "pothole in 200 metres", 1_750_000_001_000L),
        )
        val arr = JSONObject(client.buildRequestBody(makeContext("how severe is it?", history = history)))
            .getJSONArray("conversationHistory")

        assertEquals(2,           arr.length())
        assertEquals("user",      arr.getJSONObject(0).getString("role"))
        assertEquals("what's ahead?", arr.getJSONObject(0).getString("text"))
        assertEquals("assistant", arr.getJSONObject(1).getString("role"))
    }

    @Test
    fun `context with zero hazards sends empty array not null`() {
        val arr = JSONObject(client.buildRequestBody(makeContext("how are you?")))
            .getJSONArray("routeAheadHazards")
        assertEquals(0, arr.length())
    }

    @Test
    fun `high rri drop surfaces road event in context`() {
        val body = JSONObject(client.buildRequestBody(makeContext("why did the car shake?", rri = 0.31f)))
        assertEquals(0.31, body.getJSONObject("context").getDouble("rriScore"), 0.001)
    }

    @Test
    fun `query text is preserved verbatim in request body`() {
        val body = JSONObject(client.buildRequestBody(makeContext("should I detour then?")))
        assertEquals("should I detour then?", body.getString("query"))
    }

    @Test
    fun `latest turn response language is sent to search service`() {
        val body = JSONObject(client.buildRequestBody(makeContext("इस सड़क की जिम्मेदारी किसकी है?").copy(
            responseLanguage = "hi-IN",
        )))
        assertEquals("hi-IN", body.getString("response_language"))
    }

    // ── 2. SSE event parsing ──────────────────────────────────────────────────

    @Test
    fun `step event parsed with message and timestamp`() {
        val event = client.parseEvent("step", """{"step":"Querying hazard database","ts":1750001234}""")
        assertTrue(event is SearchEvent.Step)
        val step = event as SearchEvent.Step
        assertEquals("Querying hazard database", step.message)
        assertEquals(1750001234L, step.timestampMs)
    }

    @Test
    fun `text delta parsed correctly`() {
        val event = client.parseEvent("text", """{"delta":"Slow "}""")
        assertTrue(event is SearchEvent.TextDelta)
        assertEquals("Slow ", (event as SearchEvent.TextDelta).delta)
    }

    @Test
    fun `empty delta string is a valid text event`() {
        val event = client.parseEvent("text", """{"delta":""}""")
        assertEquals("", (event as SearchEvent.TextDelta).delta)
    }

    @Test
    fun `done event recognised`() {
        assertEquals(SearchEvent.Done, client.parseEvent("done", "{}"))
    }

    @Test
    fun `metadata parsed with sources and spatial markers`() {
        val json = """
        {
          "sources": [
            {"id":"hz-001","label":"Verified hazard","trustLevel":"HIGH","url":""}
          ],
          "claims": [
            {
              "category":"financial","status":"verified","subject":"P161305",
              "predicate":"project-financing","value":750000000,"unit":"USD",
              "financialType":"project-financing","sourceId":"worldbank-P161305",
              "sourceQuote":"750,000,000","sourceLocator":"projects[P161305].totalamt",
              "retrievedAt":"2026-07-14T19:53:22.234Z"
            }
          ],
          "offline": {"mode":"offline","lastSyncAt":1750000000000,"cacheAgeHours":2,"packVersion":"2026.07.15","stale":false},
          "spatialMarkers": [
            {"id":"m1","title":"Pothole","lat":12.97,"lng":77.59,"type":"POTHOLE","severity":"HIGH","summary":"Large pothole"}
          ],
          "totalLatencyMs": 840,
          "contradictionVerified": true
        }"""
        val event = client.parseEvent("metadata", json) as SearchEvent.Metadata

        assertEquals(1,       event.sources.size)
        assertEquals(1,       event.claims.size)
        assertEquals("project-financing", event.claims[0].financialType)
        assertEquals("offline", event.offline?.mode)
        assertEquals("2026.07.15", event.offline?.packVersion)
        assertEquals("hz-001",event.sources[0].id)
        assertEquals("HIGH",  event.sources[0].trustLevel)
        assertEquals(1,       event.spatialMarkers.size)
        assertEquals(12.97,   event.spatialMarkers[0].lat, 0.001)
        assertEquals(840L,    event.totalLatencyMs)
        assertTrue(event.contradictionVerified)
    }

    @Test
    fun `unknown event type returns null without throwing`() {
        assertEquals(null, client.parseEvent("heartbeat", "{}"))
    }

    @Test
    fun `malformed json returns null without throwing`() {
        assertEquals(null, client.parseEvent("text", "NOT_JSON{{{"))
    }

    @Test
    fun `duplicate source ids are deduplicated in metadata`() {
        val json = """
        {
          "sources": [
            {"id":"hz-001","label":"A","trustLevel":"HIGH","url":""},
            {"id":"hz-001","label":"B","trustLevel":"LOW","url":""}
          ],
          "spatialMarkers": [],
          "totalLatencyMs": 100,
          "contradictionVerified": false
        }"""
        val event = client.parseEvent("metadata", json) as SearchEvent.Metadata
        assertEquals(1, event.sources.size)
    }

    // ── 3. Full streaming reconstruction ─────────────────────────────────────

    @Test
    fun `full adas response stream assembles coherent answer`() = runTest {
        val mockClient = mockk<VigiaSearchClient>()
        every { mockClient.search(any()) } returns flowOf(
            SearchEvent.Step("Querying route-ahead hazards", 0L),
            SearchEvent.Step("Reasoning about road context", 100L),
            SearchEvent.TextDelta("There is a verified "),
            SearchEvent.TextDelta("pothole 180 metres "),
            SearchEvent.TextDelta("ahead on Bellary Road. "),
            SearchEvent.TextDelta("Ease off to around 30 km/h before the dip."),
            SearchEvent.Metadata(
                sources               = listOf(SearchEvent.Source("hz-001", "Verified pothole", "HIGH", "")),
                spatialMarkers        = emptyList(),
                totalLatencyMs        = 720,
                contradictionVerified = true,
            ),
            SearchEvent.Done,
        )

        val assembled = StringBuilder()
        var stepCount = 0
        var done = false

        mockClient.search(makeContext("is it safe ahead?")).test {
            repeat(8) { _ ->
                when (val event = awaitItem()) {
                    is SearchEvent.Step      -> stepCount++
                    is SearchEvent.TextDelta -> assembled.append(event.delta)
                    is SearchEvent.Done      -> done = true
                    else                     -> {}
                }
            }
            awaitComplete()
        }

        assertEquals(2, stepCount)
        assertTrue(assembled.toString().contains("pothole", ignoreCase = true))
        assertTrue(assembled.toString().contains("180"))
        assertTrue(done)
    }

    // ── 4. ADAS query coverage: context field correctness per query type ──────

    @Test
    fun `fcw query context carries high velocity and critical close hazard`() {
        val ctx = makeContext(
            "brake now",
            speedMs = 22.2f,
            hazards = listOf(hazard("VEHICLE_STOPPING", 45f, HazardAlert.Severity.CRITICAL)),
        )
        val body = JSONObject(client.buildRequestBody(ctx))

        assertEquals("brake now", body.getString("query"))
        assertEquals(22.2, body.getJSONObject("context").getDouble("velocityMs"), 0.1)
        val h0 = body.getJSONArray("routeAheadHazards").getJSONObject(0)
        assertEquals("CRITICAL", h0.getString("severity"))
        assertEquals(45.0,       h0.getDouble("distance_m"), 0.5)
    }

    @Test
    fun `fatigue query context carries low rri and prior fatigue nudge in history`() {
        val history = listOf(
            ConversationTurn(ConversationTurn.Role.ASSISTANT,
                "You seem a little less sharp than earlier. How are you feeling?",
                1_749_990_000_000L),
            ConversationTurn(ConversationTurn.Role.USER, "I'm fine", 1_749_991_000_000L),
        )
        val body = JSONObject(client.buildRequestBody(makeContext("where's the nearest rest stop?", rri = 0.28f, history = history)))

        assertEquals(0.28, body.getJSONObject("context").getDouble("rriScore"), 0.001)
        assertEquals(2, body.getJSONArray("conversationHistory").length())
        assertTrue(body.getJSONArray("conversationHistory")
            .getJSONObject(0).getString("text").contains("sharp", ignoreCase = true))
    }

    @Test
    fun `route query context carries multiple hazard types including flooding and roadworks`() {
        val ctx = makeContext(
            "is there a safer route?",
            hazards = listOf(
                hazard("POTHOLE",   220f, HazardAlert.Severity.HIGH),
                hazard("FLOODING",  450f, HazardAlert.Severity.CRITICAL),
                hazard("ROADWORKS", 900f, HazardAlert.Severity.LOW),
            ),
        )
        val arr = JSONObject(client.buildRequestBody(ctx)).getJSONArray("routeAheadHazards")

        assertEquals(3, arr.length())
        val types = (0 until arr.length()).map { arr.getJSONObject(it).getString("hazard_type") }
        assertTrue(types.contains("FLOODING"))
        assertTrue(types.contains("ROADWORKS"))
    }

    @Test
    fun `curve advisory query context carries current velocity`() {
        val body = JSONObject(client.buildRequestBody(makeContext("how fast should I take this curve?", speedMs = 19.4f)))
        assertEquals(19.4, body.getJSONObject("context").getDouble("velocityMs"), 0.1)
    }

    @Test
    fun `earnings query context carries rri as depin contribution quality proxy`() {
        val body = JSONObject(client.buildRequestBody(makeContext("how much did I earn today?", rri = 0.81f)))
        assertEquals(0.81, body.getJSONObject("context").getDouble("rriScore"), 0.001)
    }

    @Test
    fun `follow-up question preserves full four-turn conversation thread`() {
        val history = listOf(
            ConversationTurn(ConversationTurn.Role.USER,      "what's the road quality like?",  0L),
            ConversationTurn(ConversationTurn.Role.ASSISTANT, "road quality is poor — RRI 0.41", 0L),
            ConversationTurn(ConversationTurn.Role.USER,      "how long does it last?",          0L),
            ConversationTurn(ConversationTurn.Role.ASSISTANT, "about 2 km based on the data",    0L),
        )
        val body = JSONObject(client.buildRequestBody(makeContext("should I detour then?", history = history)))

        assertEquals(4, body.getJSONArray("conversationHistory").length())
        assertEquals("should I detour then?", body.getString("query"))
    }

    @Test
    fun `degraded rri-only frame still produces well-formed context`() {
        val ctx = VigiaSearchContext(
            queryText           = "what's around me?",
            timestampMs         = 1_750_000_000_000L,
            location            = LocationSnapshot(12.97, 77.59, 5f, 0f, 0f, 0L),
            velocityMs          = 0f,
            rriScore            = RriScore(0.60f),
            spatialLatentVector = SpatialLatentVector(256, FloatArray(256), 0L),
            routeAheadHazards   = emptyList(),
        )
        val body = JSONObject(client.buildRequestBody(ctx))

        assertFalse(body.getJSONObject("context").isNull("rriScore"))
        assertEquals("what's around me?", body.getString("query"))
        assertEquals(0, body.getJSONArray("routeAheadHazards").length())
    }

    @Test
    fun `speed query at highway velocity encodes bearing for directional context`() {
        val ctx = makeContext("what's the speed limit ahead?", speedMs = 27.8f)  // 100 km/h
        val ctxObj = JSONObject(client.buildRequestBody(ctx)).getJSONObject("context")
        assertEquals(145.0, ctxObj.getDouble("bearingDeg"), 0.1)
        assertEquals(27.8,  ctxObj.getDouble("velocityMs"), 0.1)
    }

    @Test
    fun `hazard eta seconds is serialised alongside distance`() {
        val ctx = makeContext("is there anything ahead?", hazards = listOf(hazard("POTHOLE", 139f)))
        val h0 = JSONObject(client.buildRequestBody(ctx))
            .getJSONArray("routeAheadHazards").getJSONObject(0)

        assertEquals(139.0, h0.getDouble("distance_m"), 0.5)
        // eta = 139 / 13.9 ≈ 10 s
        assertEquals(10.0,  h0.getDouble("eta_s"), 0.5)
    }
}
