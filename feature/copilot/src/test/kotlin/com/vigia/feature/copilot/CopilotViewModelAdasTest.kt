package com.vigia.feature.copilot

import app.cash.turbine.test
import com.vigia.core.data.ChatRepository
import com.vigia.core.model.BleLinkState
import com.vigia.core.model.ChatMessage
import com.vigia.core.model.ChatSession
import com.vigia.core.model.ConversationTurn
import com.vigia.core.model.DriverProfile
import com.vigia.core.model.HazardAlert
import com.vigia.core.model.LocationSnapshot
import com.vigia.core.model.RouteAheadHazard
import com.vigia.core.model.RriScore
import com.vigia.core.model.SpatialLatentVector
import com.vigia.core.model.VigiaSearchContext
import com.vigia.core.network.mqtt.MqttAlertRepository
import com.vigia.core.network.search.SearchEvent
import com.vigia.core.network.search.VigiaSearchClient
import com.vigia.core.network.stripe.StripePayRepository
import com.vigia.core.sensor.adas.FatigueProxyScorer
import com.vigia.core.sensor.adas.HarshEventLogger
import com.vigia.core.sensor.adas.SpeedCurveAdvisor
import com.vigia.core.sensor.ble.BleDataStreamer
import com.vigia.core.sensor.ble.BleRepository
import com.vigia.core.sensor.cdm.CdmPresenceRepository
import com.vigia.core.sensor.context.ContextAggregator
import com.vigia.core.sensor.profile.DriverProfileRepository
import com.vigia.core.sensor.tts.TtsManager
import com.vigia.core.sensor.voice.BargeInController
import com.vigia.core.sensor.voice.ConversationContextManager
import com.vigia.core.sensor.voice.LaneDriftDetector
import com.vigia.core.sensor.voice.LiveVadEngine
import com.vigia.core.sensor.voice.RouteAheadMonitor
import com.vigia.core.sensor.voice.VoiceAmplitudeMonitor
import com.vigia.core.network.sarvam.SarvamSttClient
import com.vigia.core.wallet.WalletRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [CopilotViewModel] covering ADAS-relevant conversational-AI scenarios.
 *
 * Strategy:
 * - All 21 constructor dependencies are MockK relaxed mocks — no Android runtime needed.
 * - [VigiaSearchClient] is the key mock: its [search] return value drives state transitions.
 * - [ContextAggregator.searchContext] emits a single baked VigiaSearchContext so the VM
 *   receives `_latestContext` before any [sendMessage] call.
 * - [StandardTestDispatcher] replaces [Dispatchers.Main]; [advanceUntilIdle] drains coroutines.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CopilotViewModelAdasTest {

    @Test
    fun `detected Hindi controls the current reply language`() {
        assertEquals("hi-IN", resolveTurnLanguageCode("NH 44 कौन संभालता है?", "hi-IN"))
    }

    @Test
    fun `typed Devanagari selects Hindi without STT metadata`() {
        assertEquals("hi-IN", resolveTurnLanguageCode("यह सड़क किसकी जिम्मेदारी है?", null))
    }

    @Test
    fun `latest English turn switches replies back to English`() {
        assertEquals("en-IN", resolveTurnLanguageCode("Who maintains this road?", null, "hi-IN"))
    }

    @Test
    fun `short ambiguous follow-up preserves prior language`() {
        assertEquals("hi-IN", resolveTurnLanguageCode("NH 44?", null, "hi-IN"))
    }

    // UnconfinedTestDispatcher runs coroutines eagerly and does NOT try to drain infinite
    // SharedFlow collectors in advanceUntilIdle() — which would hang forever.
    private val testDispatcher = UnconfinedTestDispatcher()

    // ── Mocks ────────────────────────────────────────────────────────────────

    private val searchClient          = mockk<VigiaSearchClient>(relaxed = true)
    private val mqttAlertRepository   = mockk<MqttAlertRepository>(relaxed = true)
    private val contextAggregator     = mockk<ContextAggregator>(relaxed = true)
    private val ttsManager            = mockk<TtsManager>(relaxed = true)
    private val cdmRepository         = mockk<CdmPresenceRepository>(relaxed = true)
    private val chatRepository        = mockk<ChatRepository>(relaxed = true)
    private val sarvamSttClient       = mockk<SarvamSttClient>(relaxed = true)
    private val voiceAmplitudeMonitor = mockk<VoiceAmplitudeMonitor>(relaxed = true)
    private val liveVadEngine         = mockk<LiveVadEngine>(relaxed = true)
    private val bargeInController     = mockk<BargeInController>(relaxed = true)
    private val laneDriftDetector     = mockk<LaneDriftDetector>(relaxed = true)
    private val routeAheadMonitor     = mockk<RouteAheadMonitor>(relaxed = true)
    private val conversationContextMgr= mockk<ConversationContextManager>(relaxed = true)
    private val walletRepository      = mockk<WalletRepository>(relaxed = true)
    private val stripePayRepository   = mockk<StripePayRepository>(relaxed = true)
    private val driverProfileRepository = mockk<DriverProfileRepository>(relaxed = true)
    private val harshEventLogger      = mockk<HarshEventLogger>(relaxed = true)
    private val bleRepository         = mockk<BleRepository>(relaxed = true)
    private val bleDataStreamer        = mockk<BleDataStreamer>(relaxed = true)
    private val fatigueProxyScorer    = mockk<FatigueProxyScorer>(relaxed = true)
    private val speedCurveAdvisor     = mockk<SpeedCurveAdvisor>(relaxed = true)

    // ── Default sensor context ────────────────────────────────────────────────

    private val defaultLocation = LocationSnapshot(
        latitudeDeg    = 12.9716,
        longitudeDeg   = 77.5946,
        accuracyMeters = 4f,
        bearingDeg     = 145f,
        velocityMs     = 13.9f,
        timestampMs    = 1_750_000_000_000L,
    )
    private val defaultContext = VigiaSearchContext(
        queryText           = "",
        timestampMs         = 1_750_000_000_000L,
        location            = defaultLocation,
        velocityMs          = 13.9f,
        rriScore            = RriScore(0.72f),
        spatialLatentVector = SpatialLatentVector(256, FloatArray(256), 0L),
    )

    // ── Shared flows for mocks ────────────────────────────────────────────────

    private val fcwEventFlow   = MutableSharedFlow<BleDataStreamer.FcwEvent>()
    private val fatigueFlow    = MutableSharedFlow<FatigueProxyScorer.FatigueEvent>()
    private val driftFlow      = MutableSharedFlow<LaneDriftDetector.DriftEvent>()
    private val proactiveFlow  = MutableSharedFlow<RouteAheadMonitor.ProactiveEvent>()
    private val routeHazards   = MutableStateFlow<List<RouteAheadHazard>>(emptyList())
    private val bleLinkState   = MutableStateFlow<BleLinkState>(BleLinkState.Idle)
    private val vadEvents      = MutableSharedFlow<LiveVadEngine.VadEvent>()
    private val bargeEvents    = MutableSharedFlow<BargeInController.BargeInEvent>()

    private lateinit var vm: CopilotViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        // Wire shared flows
        every { bleDataStreamer.fcwEvents }           returns fcwEventFlow
        every { fatigueProxyScorer.events }           returns fatigueFlow
        every { laneDriftDetector.events }            returns driftFlow
        every { routeAheadMonitor.proactiveEvents }   returns proactiveFlow
        every { routeAheadMonitor.routeAheadHazards } returns routeHazards
        every { bleRepository.linkState }             returns bleLinkState
        every { liveVadEngine.events }                returns vadEvents
        every { ttsManager.ttsAmplitude }             returns MutableStateFlow(0f)
        every { ttsManager.lastSpokenText }           returns MutableStateFlow(null)
        every { bargeInController.events }            returns bargeEvents
        every { mqttAlertRepository.alerts }          returns MutableSharedFlow()
        every { voiceAmplitudeMonitor.amplitude }     returns MutableStateFlow(0f)
        every { sarvamSttClient.lastDetectedLanguageCode } returns MutableStateFlow(null)
        every { walletRepository.state }              returns MutableStateFlow(com.vigia.core.wallet.WalletState())
        every { driverProfileRepository.profile }     returns flowOf(DriverProfile.NEW)
        every { conversationContextMgr.history() }    returns emptyList()
        every { contextAggregator.searchContext }     returns flowOf(defaultContext)
        every { chatRepository.allSessions() }        returns flowOf(emptyList())
        every { chatRepository.messagesForSession(any()) } returns flowOf(emptyList())
        coEvery { chatRepository.createSession(any()) }    returns Unit
        coEvery { chatRepository.insertMessage(any()) }    returns Unit
        coEvery { chatRepository.bumpSession(any()) }      returns Unit
        coEvery { bleRepository.sendTtcThreshold(any()) }  returns Unit

        vm = CopilotViewModel(
            searchClient              = searchClient,
            mqttAlertRepository       = mqttAlertRepository,
            contextAggregator         = contextAggregator,
            ttsManager                = ttsManager,
            cdmRepository             = cdmRepository,
            chatRepository            = chatRepository,
            sarvamSttClient           = sarvamSttClient,
            voiceAmplitudeMonitor     = voiceAmplitudeMonitor,
            liveVadEngine             = liveVadEngine,
            bargeInController         = bargeInController,
            laneDriftDetector         = laneDriftDetector,
            routeAheadMonitor         = routeAheadMonitor,
            conversationContextManager= conversationContextMgr,
            walletRepository          = walletRepository,
            stripePayRepository       = stripePayRepository,
            driverProfileRepository   = driverProfileRepository,
            harshEventLogger          = harshEventLogger,
            bleRepository             = bleRepository,
            bleDataStreamer            = bleDataStreamer,
            fatigueProxyScorer        = fatigueProxyScorer,
            speedCurveAdvisor         = speedCurveAdvisor,
        )
    }

    @After
    fun tearDown() {
        vm.close()
        Dispatchers.resetMain()
    }

    // viewModelScope is a public extension property — cancel its Job directly.
    // ViewModel.onCleared() is just a hook; it does NOT cancel viewModelScope.
    // ViewModel.clear() does, but is package-private. Cancelling the Job is equivalent.
    private fun CopilotViewModel.close() {
        viewModelScope.coroutineContext[Job]?.cancel()
    }

    // ── Test runner ───────────────────────────────────────────────────────────

    // runTest drains virtual time via advanceUntilIdle() after the block exits.
    // The wallet polling while(true) { delay(60_000) } would spin infinitely and OOM.
    // Cancelling viewModelScope inside the block makes delay() throw CancellationException,
    // so advanceUntilIdle() finds no pending work and exits cleanly.
    private fun runVmTest(block: suspend TestScope.() -> Unit) = runTest {
        try {
            block()
        } finally {
            vm.close()
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun givenSearchReturns(vararg events: SearchEvent) {
        every { searchClient.search(any()) } returns flowOf(*events)
    }

    private fun activeState() = vm.uiState.value as? CopilotUiState.Active

    // ── 1. State machine: Idle → Searching → streaming → Done ─────────────────

    @Test
    fun `sendMessage transitions through Searching then back to Active`() = runVmTest {
        givenSearchReturns(
            SearchEvent.Step("Checking hazards", 0L),
            SearchEvent.TextDelta("Road is clear for "),
            SearchEvent.TextDelta("the next 5 km."),
            SearchEvent.Done,
        )

        vm.sendMessage("what's ahead on this road?")
        testDispatcher.scheduler.runCurrent()

        val state = activeState()!!
        assertFalse("streaming should be finished", state.isSearchStreaming)
        assertEquals(OrbState.Active, state.orbState)
    }

    @Test
    fun `step events accumulate in completedSteps during streaming`() = runVmTest {
        val stepFlow = MutableSharedFlow<SearchEvent>(replay = 4)
        every { searchClient.search(any()) } returns stepFlow

        vm.sendMessage("is the road safe?")
        testDispatcher.scheduler.runCurrent()

        stepFlow.emit(SearchEvent.Step("Querying hazard DB", 0L))
        stepFlow.emit(SearchEvent.Step("Analysing road context", 100L))
        testDispatcher.scheduler.runCurrent()

        val state = activeState()!!
        assertTrue(state.completedSteps.contains("Querying hazard DB"))
        assertTrue(state.completedSteps.contains("Analysing road context"))
    }

    @Test
    fun `voice mode never speaks raw intent classification step`() = runVmTest {
        val stepFlow = MutableSharedFlow<SearchEvent>()
        every { searchClient.search(any()) } returns stepFlow

        vm.startAutoVoiceMode()
        vm.sendMessage("who maintains this road?")
        stepFlow.emit(SearchEvent.Step("Classifying intent", 0L))
        testDispatcher.scheduler.runCurrent()

        verify(exactly = 0) { ttsManager.speakSarvam("Classifying intent", any(), any()) }
    }

    @Test
    fun `voice mode narrates one natural retrieval acknowledgement`() = runVmTest {
        val stepFlow = MutableSharedFlow<SearchEvent>()
        every { searchClient.search(any()) } returns stepFlow

        vm.startAutoVoiceMode()
        vm.sendMessage("who maintains this road?")
        stepFlow.emit(SearchEvent.Step("Classifying intent", 0L))
        stepFlow.emit(SearchEvent.Step("Searching NHAI documents", 1L))
        stepFlow.emit(SearchEvent.Step("Verifying official sources", 2L))
        testDispatcher.scheduler.runCurrent()

        verify(exactly = 1) {
            ttsManager.speakSarvam("One moment while I check the road records.", any(), any())
        }
        verify(exactly = 1) { bargeInController.startMonitoring() }
        assertEquals(VoiceListeningState.Speaking, activeState()!!.voiceListeningState)
    }

    @Test
    fun `text deltas are concatenated into searchAnswer`() = runVmTest {
        val eventFlow = MutableSharedFlow<SearchEvent>(replay = 10)
        every { searchClient.search(any()) } returns eventFlow

        vm.sendMessage("should I slow down?")
        testDispatcher.scheduler.runCurrent()

        listOf("Slow ", "down — ", "pothole 200 metres ahead.").forEach {
            eventFlow.emit(SearchEvent.TextDelta(it))
        }
        testDispatcher.scheduler.runCurrent()

        val state = activeState()!!
        assertEquals("Slow down — pothole 200 metres ahead.", state.searchAnswer)
    }

    @Test
    fun `metadata event updates sources and spatial markers`() = runVmTest {
        val eventFlow = MutableSharedFlow<SearchEvent>(replay = 10)
        every { searchClient.search(any()) } returns eventFlow

        vm.sendMessage("what hazards are verified?")
        testDispatcher.scheduler.runCurrent()

        eventFlow.emit(SearchEvent.Metadata(
            sources               = listOf(SearchEvent.Source("hz-001", "Pothole on Bellary Rd", "HIGH", "")),
            spatialMarkers        = listOf(SearchEvent.SpatialMarker("m1","Pothole",12.97,77.59,"POTHOLE","HIGH","big pothole")),
            totalLatencyMs        = 850L,
            contradictionVerified = true,
        ))
        testDispatcher.scheduler.runCurrent()

        val state = activeState()!!
        assertEquals(1, state.searchSources.size)
        assertEquals("hz-001", state.searchSources[0].id)
        assertEquals(1, state.spatialMarkers.size)
        assertEquals(850L, state.totalLatencyMs)
    }

    // ── 2. ADAS query types: message reaches search client ────────────────────

    @Test
    fun `hazard query reaches search client with road-ahead query text`() = runVmTest {
        givenSearchReturns(SearchEvent.TextDelta("All clear."), SearchEvent.Done)

        vm.sendMessage("what's ahead on this road?")
        testDispatcher.scheduler.runCurrent()

        verify { searchClient.search(match { it.queryText == "what's ahead on this road?" }) }
    }

    @Test
    fun `speed advisory query carries current velocity in context`() = runVmTest {
        givenSearchReturns(SearchEvent.TextDelta("Ease off."), SearchEvent.Done)

        vm.sendMessage("should I slow down?")
        testDispatcher.scheduler.runCurrent()

        verify { searchClient.search(match { it.velocityMs == defaultContext.velocityMs }) }
    }

    @Test
    fun `fatigue state query carries rri score in context`() = runVmTest {
        givenSearchReturns(SearchEvent.TextDelta("You seem tired."), SearchEvent.Done)

        vm.sendMessage("am I driving okay?")
        testDispatcher.scheduler.runCurrent()

        verify { searchClient.search(match { it.rriScore == defaultContext.rriScore }) }
    }

    @Test
    fun `earnings query reaches search client`() = runVmTest {
        givenSearchReturns(SearchEvent.TextDelta("You earned ₹47 today."), SearchEvent.Done)

        vm.sendMessage("how much have I earned today?")
        testDispatcher.scheduler.runCurrent()

        verify { searchClient.search(match { it.queryText == "how much have I earned today?" }) }
    }

    @Test
    fun `route safety query carries route-ahead hazards in context`() = runVmTest {
        val pothole = RouteAheadHazard(
            geohash = "tf1uk", distanceMeters = 220f, hazardType = "POTHOLE",
            severity = HazardAlert.Severity.HIGH, avgRriScore = 0.45f,
            reportCount = 5, lastSeenMs = 0L, etaSeconds = 15f,
        )
        routeHazards.value = listOf(pothole)

        givenSearchReturns(SearchEvent.TextDelta("Pothole at 220m."), SearchEvent.Done)

        vm.sendMessage("is there a safer route?")
        testDispatcher.scheduler.runCurrent()

        verify { searchClient.search(match { it.routeAheadHazards.any { h -> h.hazardType == "POTHOLE" } }) }
    }

    // ── 3. Multi-turn conversation thread ────────────────────────────────────

    @Test
    fun `follow-up question includes prior conversation history`() = runVmTest {
        val priorHistory = listOf(
            ConversationTurn(ConversationTurn.Role.USER,      "what's the road quality like?", 0L),
            ConversationTurn(ConversationTurn.Role.ASSISTANT, "poor — RRI 0.41 ahead",          0L),
        )
        // history() is called once inside sendMessage after addUserTurn().
        // It returns the full list [prior…, currentTurn]; dropLast(1) strips the current turn,
        // leaving priorHistory (size 2) as conversationHistory.
        every { conversationContextMgr.history() } returns
            priorHistory + ConversationTurn(ConversationTurn.Role.USER, "how long does it last?", 0L)

        givenSearchReturns(SearchEvent.TextDelta("About 2 km."), SearchEvent.Done)

        vm.sendMessage("how long does it last?")
        testDispatcher.scheduler.runCurrent()

        verify { searchClient.search(match { it.conversationHistory.size >= 1 }) }
    }

    // ── 4. FCW preempts conversation with QUEUE_FLUSH ─────────────────────────

    @Test
    fun `fcw alert invokes tts with QUEUE_FLUSH and sets OrbState to Alert`() = runVmTest {
        val fcwEvent = BleDataStreamer.FcwEvent(ttcSeconds = 2.1f, classId = 3)

        vm.uiState.test {
            awaitItem() // initial Loading or Active

            fcwEventFlow.emit(fcwEvent)
            testDispatcher.scheduler.runCurrent()

            // Check TTS was called with QUEUE_FLUSH (= 2)
            verify { ttsManager.speak(
                match { it.contains("Brake", ignoreCase = true) && it.contains("2.1") },
                android.speech.tts.TextToSpeech.QUEUE_FLUSH,
            ) }

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `fcw message contains target label from classId`() = runVmTest {
        // classId 0 = "vehicle", 3 = "person" depending on BleDataStreamer mapping
        val fcwEvent = BleDataStreamer.FcwEvent(ttcSeconds = 1.8f, classId = 0)

        fcwEventFlow.emit(fcwEvent)
        testDispatcher.scheduler.runCurrent()

        // The TTS message must mention the object class and TTC value
        verify { ttsManager.speak(
            match { it.contains("1.8") },
            android.speech.tts.TextToSpeech.QUEUE_FLUSH,
        ) }
    }

    // ── 5. Driver profile: TTC threshold scales with sProfile ────────────────

    @Test
    fun `new driver profile sends 4_5s ttc threshold to ble`() = runVmTest {
        every { driverProfileRepository.profile } returns flowOf(DriverProfile.NEW)

        // Re-create VM so profile observer fires with NEW profile
        vm.close()
        vm = createVm()
        testDispatcher.scheduler.runCurrent()

        // NEW sProfile = 1.5 → threshold = 3.0 * 1.5 = 4.5s
        coVerify { bleRepository.sendTtcThreshold(match { kotlin.math.abs(it - 4.5f) < 0.01f }) }
    }

    @Test
    fun `expert profile sends smaller ttc threshold than standard`() = runVmTest {
        every { driverProfileRepository.profile } returns flowOf(DriverProfile.EXPERT)

        vm.close()
        vm = createVm()
        testDispatcher.scheduler.runCurrent()

        // EXPERT sProfile = 0.5 → threshold = 3.0 * 0.5 = 1.5s
        coVerify { bleRepository.sendTtcThreshold(match { it < 2.0f }) }
    }

    @Test
    fun `elderly profile sends larger ttc threshold than standard`() = runVmTest {
        every { driverProfileRepository.profile } returns flowOf(DriverProfile.ELDERLY)

        vm.close()
        vm = createVm()
        testDispatcher.scheduler.runCurrent()

        // ELDERLY sProfile = 3.0 → threshold = 3.0 * 3.0 = 9.0s
        coVerify { bleRepository.sendTtcThreshold(match { it > 5.0f }) }
    }

    // ── 6. Error / degraded-mode handling ────────────────────────────────────

    @Test
    fun `search exception clears streaming flag without crashing`() = runVmTest {
        every { searchClient.search(any()) } throws RuntimeException("network timeout")

        vm.sendMessage("what's ahead?")
        testDispatcher.scheduler.runCurrent()

        val state = activeState()!!
        assertFalse(state.isSearchStreaming)
        assertEquals(OrbState.Active, state.orbState)
    }

    @Test
    fun `cancel search resets streaming state immediately`() = runVmTest {
        val pausedFlow = MutableSharedFlow<SearchEvent>()
        every { searchClient.search(any()) } returns pausedFlow

        vm.sendMessage("is the road safe?")
        testDispatcher.scheduler.runCurrent()

        vm.cancelSearch()
        testDispatcher.scheduler.runCurrent()

        val state = activeState()!!
        assertFalse(state.isSearchStreaming)
    }

    @Test
    fun `blank message is silently dropped without calling search client`() = runVmTest {
        vm.sendMessage("   ")
        testDispatcher.scheduler.runCurrent()

        verify(exactly = 0) { searchClient.search(any()) }
    }

    @Test
    fun `message dropped when sensor context not ready`() = runVmTest {
        // Override context aggregator to never emit (no context available)
        every { contextAggregator.searchContext } returns MutableSharedFlow()

        vm.close()
        val vmNoCtx = createVm()
        testDispatcher.scheduler.runCurrent()

        vmNoCtx.sendMessage("what's ahead?")
        testDispatcher.scheduler.runCurrent()

        verify(exactly = 0) { searchClient.search(any()) }
        vmNoCtx.close()
    }

    // ── 7. Streaming assistant answer persisted to chat repo on Done ──────────

    @Test
    fun `completed answer is inserted into chat repository as ASSISTANT message`() = runVmTest {
        givenSearchReturns(
            SearchEvent.TextDelta("Road is clear."),
            SearchEvent.Done,
        )

        vm.sendMessage("is there anything ahead?")
        testDispatcher.scheduler.runCurrent()

        coVerify {
            chatRepository.insertMessage(match { msg ->
                msg.role == com.vigia.core.model.MessageRole.ASSISTANT &&
                msg.body.contains("Road is clear.")
            })
        }
    }

    // ── 8. MQTT alert injection from Pi edge node ────────────────────────────

    @Test
    fun `critical mqtt alert speaks with QUEUE_FLUSH and sets OrbState to Alert`() = runVmTest {
        val mqttAlerts = MutableSharedFlow<HazardAlert>()
        every { mqttAlertRepository.alerts } returns mqttAlerts
        vm.close()
        vm = createVm()

        val alert = HazardAlert(
            id              = "a1",
            severity        = HazardAlert.Severity.CRITICAL,
            messageText     = "Critical: road collapse 500m ahead",
            timestampMs     = 0L,
            locationSnapshot= null,
        )
        mqttAlerts.emit(alert)
        testDispatcher.scheduler.runCurrent()

        verify { ttsManager.speak("Critical: road collapse 500m ahead", android.speech.tts.TextToSpeech.QUEUE_FLUSH) }
        assertEquals(OrbState.Alert, activeState()!!.orbState)
    }

    @Test
    fun `high severity mqtt alert also sets OrbState to Alert`() = runVmTest {
        val mqttAlerts = MutableSharedFlow<HazardAlert>()
        every { mqttAlertRepository.alerts } returns mqttAlerts
        vm.close()
        vm = createVm()

        mqttAlerts.emit(HazardAlert("a2", HazardAlert.Severity.HIGH, "Flooded road ahead", 0L, null))
        testDispatcher.scheduler.runCurrent()

        assertEquals(OrbState.Alert, activeState()!!.orbState)
    }

    @Test
    fun `low severity mqtt alert speaks with QUEUE_ADD and does not set Alert orb`() = runVmTest {
        val mqttAlerts = MutableSharedFlow<HazardAlert>()
        every { mqttAlertRepository.alerts } returns mqttAlerts
        vm.close()
        vm = createVm()

        mqttAlerts.emit(HazardAlert("a3", HazardAlert.Severity.LOW, "Minor bump reported", 0L, null))
        testDispatcher.scheduler.runCurrent()

        verify { ttsManager.speak("Minor bump reported", android.speech.tts.TextToSpeech.QUEUE_ADD) }
        // OrbState should still be Idle (not Alert for low severity)
        val state = activeState()!!
        assertTrue(state.orbState != OrbState.Alert)
    }

    @Test
    fun `mqtt alerts are prepended to pendingAlerts list up to 10`() = runVmTest {
        val mqttAlerts = MutableSharedFlow<HazardAlert>(replay = 12)
        every { mqttAlertRepository.alerts } returns mqttAlerts
        vm.close()
        vm = createVm()

        repeat(12) { i ->
            mqttAlerts.emit(HazardAlert("a$i", HazardAlert.Severity.MEDIUM, "Alert $i", 0L, null))
        }
        testDispatcher.scheduler.runCurrent()

        // pendingAlerts is capped at 10
        assertTrue(activeState()!!.pendingAlerts.size <= 10)
    }

    // ── 9. Proactive driving assistance alerts ────────────────────────────────

    @Test
    fun `lane drift event triggers tts with QUEUE_ADD`() = runVmTest {
        driftFlow.emit(LaneDriftDetector.DriftEvent.DriftDetected(
            directionLabel = "left", oscillationDeg = 5f,
            message = "Lane drift — check your position",
        ))
        testDispatcher.scheduler.runCurrent()

        verify { ttsManager.speak("Lane drift — check your position", android.speech.tts.TextToSpeech.QUEUE_ADD) }
    }

    @Test
    fun `lane drift event sets OrbState to Alert`() = runVmTest {
        driftFlow.emit(LaneDriftDetector.DriftEvent.DriftDetected(
            directionLabel = "right", oscillationDeg = 6f, message = "Lane drift detected",
        ))
        testDispatcher.scheduler.runCurrent()

        assertEquals(OrbState.Alert, activeState()!!.orbState)
    }

    @Test
    fun `fatigue nudge alert speaks with QUEUE_ADD`() = runVmTest {
        fatigueFlow.emit(FatigueProxyScorer.FatigueEvent.NudgeAlert(score = 0.65f, message = "Take a break soon"))
        testDispatcher.scheduler.runCurrent()

        verify { ttsManager.speak("Take a break soon", android.speech.tts.TextToSpeech.QUEUE_ADD) }
    }

    @Test
    fun `fatigue escalate alert speaks with QUEUE_FLUSH`() = runVmTest {
        fatigueFlow.emit(FatigueProxyScorer.FatigueEvent.EscalateAlert(score = 0.9f, message = "Pull over — severe fatigue"))
        testDispatcher.scheduler.runCurrent()

        verify { ttsManager.speak("Pull over — severe fatigue", android.speech.tts.TextToSpeech.QUEUE_FLUSH) }
    }

    @Test
    fun `route-ahead proactive hazard event speaks via tts`() = runVmTest {
        val hazard = RouteAheadHazard(
            geohash = "tf1uk", distanceMeters = 300f, hazardType = "POTHOLE",
            severity = HazardAlert.Severity.HIGH, avgRriScore = 0.4f,
            reportCount = 3, lastSeenMs = 0L, etaSeconds = 22f,
        )
        proactiveFlow.emit(RouteAheadMonitor.ProactiveEvent.HazardAhead(
            hazard = hazard, message = "Deep pothole 300m ahead on your route",
        ))
        testDispatcher.scheduler.runCurrent()

        verify { ttsManager.speak(match { it.contains("pothole") }, android.speech.tts.TextToSpeech.QUEUE_ADD) }
    }

    @Test
    fun `route-ahead road quality warning speaks via tts`() = runVmTest {
        proactiveFlow.emit(RouteAheadMonitor.ProactiveEvent.RoadQualityWarning(
            avgRri = 0.35f, distanceMeters = 2000f, message = "Poor road quality for next 2 km",
        ))
        testDispatcher.scheduler.runCurrent()

        verify { ttsManager.speak(match { it.contains("road quality") }, android.speech.tts.TextToSpeech.QUEUE_ADD) }
    }

    // ── 10. BLE lifecycle — trip start / trip end debrief ────────────────────

    @Test
    fun `ble connect transition starts a trip in HarshEventLogger`() = runVmTest {
        // Simulate BLE going Idle → Bound
        bleLinkState.value = BleLinkState.Bound
        testDispatcher.scheduler.runCurrent()

        coVerify { harshEventLogger.startTrip(any(), any()) }
    }

    @Test
    fun `ble disconnect after connect ends trip and speaks debrief`() = runVmTest {
        coEvery { harshEventLogger.endTrip(any()) } returns "Great drive! 0 harsh events."

        bleLinkState.value = BleLinkState.Bound
        testDispatcher.scheduler.runCurrent()

        bleLinkState.value = BleLinkState.Idle
        testDispatcher.scheduler.runCurrent()

        verify { ttsManager.speak(match { it.contains("drive") }, android.speech.tts.TextToSpeech.QUEUE_FLUSH) }
    }

    @Test
    fun `ble disconnect with null debrief does not speak`() = runVmTest {
        coEvery { harshEventLogger.endTrip(any()) } returns null

        bleLinkState.value = BleLinkState.Bound
        testDispatcher.scheduler.runCurrent()
        bleLinkState.value = BleLinkState.Idle
        testDispatcher.scheduler.runCurrent()

        // null debrief → no TTS for the trip summary (FCW/fatigue TTS may still be called)
        verify(exactly = 0) { ttsManager.speak(match { it.contains("drive") || it.contains("trip") }, any()) }
    }

    // ── 11. VAD auto-voice mode ───────────────────────────────────────────────

    @Test
    fun `startAutoVoiceMode starts VAD engine and sets AutoListening state`() = runVmTest {
        vm.startAutoVoiceMode()
        testDispatcher.scheduler.runCurrent()

        verify { liveVadEngine.start() }
        val state = activeState()!!
        assertEquals(VoiceListeningState.AutoListening, state.voiceListeningState)
        assertEquals(OrbState.Listening, state.orbState)
        assertTrue(state.isAutoVadActive)
    }

    @Test
    fun `vad UtteranceComplete triggers transcription and search`() = runVmTest {
        coEvery { sarvamSttClient.transcribe(any()) } returns "is it safe to overtake?"
        givenSearchReturns(SearchEvent.TextDelta("Yes, road is clear."), SearchEvent.Done)

        vm.startAutoVoiceMode()
        testDispatcher.scheduler.runCurrent()

        vadEvents.emit(LiveVadEngine.VadEvent.UtteranceComplete(ByteArray(0)))
        testDispatcher.scheduler.runCurrent()

        verify { searchClient.search(match { it.queryText == "is it safe to overtake?" }) }
    }

    @Test
    fun `vad AmplitudeUpdate propagates to voiceAmplitude in UI state`() = runVmTest {
        vm.startAutoVoiceMode()
        testDispatcher.scheduler.runCurrent()

        vadEvents.emit(LiveVadEngine.VadEvent.AmplitudeUpdate(rms = 0.42f))
        testDispatcher.scheduler.runCurrent()

        assertEquals(0.42f, activeState()!!.voiceAmplitude, 0.001f)
    }

    @Test
    fun `vad Ready plays the listening cue`() = runVmTest {
        vm.startAutoVoiceMode()
        testDispatcher.scheduler.runCurrent()

        vadEvents.emit(LiveVadEngine.VadEvent.Ready)
        testDispatcher.scheduler.runCurrent()

        verify { ttsManager.playListeningCue() }
    }

    @Test
    fun `hands free stop transcript closes voice mode without searching`() = runVmTest {
        coEvery { sarvamSttClient.transcribe(any()) } returns "Okay, thank you."

        vm.startAutoVoiceMode()
        testDispatcher.scheduler.runCurrent()
        vadEvents.emit(LiveVadEngine.VadEvent.UtteranceComplete(ByteArray(44)))
        testDispatcher.scheduler.runCurrent()

        assertFalse(activeState()!!.isVoiceOverlayVisible)
        verify(exactly = 0) { searchClient.search(any()) }
    }

    @Test
    fun `barge in stops speech immediately and captures stop command`() = runVmTest {
        coEvery { sarvamSttClient.transcribe(any()) } returns "stop"
        givenSearchReturns(SearchEvent.TextDelta("A long spoken response"), SearchEvent.Done)

        vm.startAutoVoiceMode()
        vm.sendMessage("Tell me about this road")
        testDispatcher.scheduler.runCurrent()

        bargeEvents.emit(BargeInController.BargeInEvent.SpeechStart)
        testDispatcher.scheduler.runCurrent()

        verify { ttsManager.stop() }
        verify { ttsManager.playListeningCue() }
        assertEquals(VoiceListeningState.BargeIn, activeState()!!.voiceListeningState)

        bargeEvents.emit(BargeInController.BargeInEvent.UtteranceComplete(ByteArray(44)))
        testDispatcher.scheduler.runCurrent()

        assertFalse(activeState()!!.isVoiceOverlayVisible)
    }

    @Test
    fun `barge in playback echo is discarded instead of searched`() = runVmTest {
        every { ttsManager.lastSpokenText } returns MutableStateFlow(
            "The road authority recommends reducing speed near the damaged shoulder."
        )
        coEvery { sarvamSttClient.transcribe(any()) } returns
            "road authority recommends reducing speed near the damaged shoulder"
        givenSearchReturns(SearchEvent.TextDelta("A long spoken response"), SearchEvent.Done)

        vm.startAutoVoiceMode()
        vm.sendMessage("What should I do?")
        testDispatcher.scheduler.runCurrent()
        bargeEvents.emit(BargeInController.BargeInEvent.SpeechStart)
        bargeEvents.emit(BargeInController.BargeInEvent.UtteranceComplete(ByteArray(44)))
        testDispatcher.scheduler.runCurrent()

        verify(exactly = 1) { searchClient.search(any()) }
        assertEquals(VoiceListeningState.AutoListening, activeState()!!.voiceListeningState)
    }

    @Test
    fun `dismissVoiceOverlay stops all voice engines and hides overlay`() = runVmTest {
        vm.startAutoVoiceMode()
        testDispatcher.scheduler.runCurrent()

        vm.dismissVoiceOverlay()
        testDispatcher.scheduler.runCurrent()

        verify { liveVadEngine.stop() }
        verify { bargeInController.stopMonitoring() }
        verify { ttsManager.stop() }
        val state = activeState()!!
        assertFalse(state.isVoiceOverlayVisible)
        assertEquals(VoiceListeningState.Idle, state.voiceListeningState)
    }

    // ── Private factory ───────────────────────────────────────────────────────

    private fun createVm() = CopilotViewModel(
        searchClient               = searchClient,
        mqttAlertRepository        = mqttAlertRepository,
        contextAggregator          = contextAggregator,
        ttsManager                 = ttsManager,
        cdmRepository              = cdmRepository,
        chatRepository             = chatRepository,
        sarvamSttClient            = sarvamSttClient,
        voiceAmplitudeMonitor      = voiceAmplitudeMonitor,
        liveVadEngine              = liveVadEngine,
        bargeInController          = bargeInController,
        laneDriftDetector          = laneDriftDetector,
        routeAheadMonitor          = routeAheadMonitor,
        conversationContextManager = conversationContextMgr,
        walletRepository           = walletRepository,
        stripePayRepository        = stripePayRepository,
        driverProfileRepository    = driverProfileRepository,
        harshEventLogger           = harshEventLogger,
        bleRepository              = bleRepository,
        bleDataStreamer             = bleDataStreamer,
        fatigueProxyScorer         = fatigueProxyScorer,
        speedCurveAdvisor          = speedCurveAdvisor,
    )
}
