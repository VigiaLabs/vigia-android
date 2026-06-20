package com.vigia.core.sensor.adas

import android.util.Log
import com.vigia.core.model.LocationSnapshot
import com.vigia.core.sensor.ble.BleDataStreamer
import com.vigia.core.sensor.voice.LaneDriftDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.LinkedList
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Computes a hardware-free fatigue proxy score F ∈ [0, 1] from three signals:
 *
 *   1. Reaction-lag Δt_react — gap between an RRI-drop road event and the driver's
 *      corrective GPS response (speed/bearing inflection). Proxy for neural latency.
 *   2. Velocity micro-variance σ_v — std-dev of speed over a 30s window. Fatigued
 *      drivers hold speed less steadily (unconscious drift).
 *   3. Drift frequency — from LaneDriftDetector; weighted at 0.30.
 *
 *   F = 0.45·Δt_norm + 0.25·σ_v_norm + 0.30·drift_norm
 *
 * EWMA baselines are established during the first [BASELINE_WINDOW_MS] (10 min) of the trip.
 * No alert fires before the baseline is set.
 *
 * Emits [FatigueEvent] at two thresholds, with 30s inter-event cooldown.
 */
@Singleton
class FatigueProxyScorer @Inject constructor(
    private val bleDataStreamer: BleDataStreamer,
) {
    sealed class FatigueEvent {
        data class NudgeAlert(val score: Float, val message: String) : FatigueEvent()
        data class EscalateAlert(val score: Float, val message: String) : FatigueEvent()
    }

    private val _events = MutableSharedFlow<FatigueEvent>(extraBufferCapacity = 8)
    val events: Flow<FatigueEvent> = _events.asSharedFlow()

    private val _fatigueScore = MutableStateFlow(0f)
    val fatigueScore: StateFlow<Float> = _fatigueScore.asStateFlow()

    // ── State ─────────────────────────────────────────────────────────────────

    // Reaction-lag tracking
    private data class PendingReaction(val eventTimeMs: Long, val rriAtDrop: Float)
    private var pendingReaction: PendingReaction? = null
    private val reactionLags = LinkedList<Float>()          // rolling samples (ms)

    // Velocity variance
    private val speedWindow = LinkedList<Float>()           // 30s GPS speed samples

    // Drift frequency
    private var driftCountLast30m = 0
    private var driftCountWindowStartMs = 0L

    // EWMA baselines (set after first 10 min)
    private var tripStartMs = 0L
    private var baselineSet = false
    private var baselineReactionMs = 0f
    private var baselineSigmaV = 0f
    private var baselineDriftFreq = 0f

    // Alert cooldown
    private var lastAlertMs = 0L

    // Prev RRI for spike detection
    private var prevRri = -1f

    private var scorerScope: CoroutineScope? = null
    private var driftObserveJob: Job? = null

    // ── Public API ────────────────────────────────────────────────────────────

    fun start(
        locationFlow: Flow<LocationSnapshot>,
        laneDriftDetector: LaneDriftDetector,
        scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    ) {
        stop()
        val s = scope
        scorerScope = s
        tripStartMs = System.currentTimeMillis()
        baselineSet = false
        reactionLags.clear(); speedWindow.clear()
        driftCountLast30m = 0
        driftCountWindowStartMs = tripStartMs
        prevRri = -1f

        // Observe BLE RRI drops → record road-event timestamps
        s.launch {
            bleDataStreamer.telemetryFrames.collect { frame ->
                val rri = frame.rriScore.value
                if (prevRri >= 0f) {
                    val drop = prevRri - rri
                    if (drop >= RRI_DROP_THRESHOLD && pendingReaction == null) {
                        pendingReaction = PendingReaction(
                            eventTimeMs = frame.receivedAtMs,
                            rriAtDrop   = prevRri,
                        )
                        Log.v(TAG, "Road event detected: RRI $prevRri→$rri at ${frame.receivedAtMs}")
                    }
                }
                prevRri = rri
            }
        }

        // Observe GPS — detect speed/bearing inflection after a pending road event
        s.launch {
            var prevLocation: LocationSnapshot? = null
            val now = System.currentTimeMillis()

            locationFlow.collect { loc ->
                // Speed micro-variance window
                speedWindow.addLast(loc.velocityMs)
                val windowCutoff = loc.timestampMs - SPEED_WINDOW_MS
                while (speedWindow.size > 1 && loc.timestampMs - SPEED_WINDOW_MS > 0) {
                    if (speedWindow.size > MAX_SPEED_SAMPLES) speedWindow.removeFirst() else break
                }

                // Reaction-lag measurement
                val pending = pendingReaction
                if (pending != null) {
                    val elapsed = loc.timestampMs - pending.eventTimeMs
                    if (elapsed > REACTION_CAP_MS) {
                        // No response in 2s → cap at 2s (strongest fatigue signal)
                        recordReactionLag(REACTION_CAP_MS.toFloat())
                        pendingReaction = null
                    } else if (prevLocation != null) {
                        val dSpeed   = abs(loc.velocityMs - prevLocation!!.velocityMs)
                        val dBearing = bearingDelta(prevLocation!!.bearingDeg, loc.bearingDeg)
                        if (dSpeed > RESPONSE_SPEED_THRESHOLD_MS || abs(dBearing) > RESPONSE_BEARING_DEG) {
                            recordReactionLag(elapsed.toFloat())
                            pendingReaction = null
                            Log.v(TAG, "Reaction detected in ${elapsed}ms")
                        }
                    }
                }
                prevLocation = loc

                // Score update every GPS tick (1Hz)
                updateScore(loc.timestampMs)
            }
        }

        // Observe drift events → count frequency
        driftObserveJob = s.launch {
            laneDriftDetector.events.collect {
                driftCountLast30m++
            }
        }
    }

    fun stop() {
        scorerScope?.cancel()
        scorerScope = null
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun recordReactionLag(lagMs: Float) {
        reactionLags.addLast(lagMs)
        if (reactionLags.size > MAX_REACTION_SAMPLES) reactionLags.removeFirst()
    }

    private fun updateScore(nowMs: Long) {
        val elapsedMs = nowMs - tripStartMs

        // Reset 30-min drift window
        if (nowMs - driftCountWindowStartMs > DRIFT_WINDOW_MS) {
            driftCountLast30m = 0
            driftCountWindowStartMs = nowMs
        }

        // Establish baseline after 10 min
        if (!baselineSet && elapsedMs >= BASELINE_WINDOW_MS) {
            baselineReactionMs = reactionLags.average().toFloat().takeIf { it > 0f } ?: 500f
            baselineSigmaV     = speedSigma().takeIf { it > 0f } ?: 0.5f
            baselineDriftFreq  = driftCountLast30m.toFloat().coerceAtLeast(0.1f)
            baselineSet = true
            Log.d(TAG, "Fatigue baseline set: Δt=${baselineReactionMs}ms σv=${baselineSigmaV} drifts=${baselineDriftFreq}")
            return   // don't alert on the baseline tick itself
        }
        if (!baselineSet) return

        val currentReaction = reactionLags.average().toFloat().takeIf { reactionLags.isNotEmpty() }
            ?: baselineReactionMs
        val currentSigmaV  = speedSigma()
        val currentDrift   = driftCountLast30m.toFloat()

        val dtNorm    = clamp01((currentReaction / baselineReactionMs) - 1f)
        val sigmaVNorm = clamp01((currentSigmaV    / baselineSigmaV)    - 1f)
        val driftNorm  = clamp01((currentDrift      / baselineDriftFreq) - 1f)

        val score = 0.45f * dtNorm + 0.25f * sigmaVNorm + 0.30f * driftNorm
        _fatigueScore.value = score

        val since = nowMs - lastAlertMs
        if (since < ALERT_COOLDOWN_MS) return

        when {
            score >= ESCALATE_THRESHOLD -> {
                lastAlertMs = nowMs
                _events.tryEmit(
                    FatigueEvent.EscalateAlert(
                        score   = score,
                        message = "You're showing signs of fatigue. " +
                                "Please consider pulling over at the next safe spot or rest area.",
                    )
                )
            }
            score >= NUDGE_THRESHOLD -> {
                lastAlertMs = nowMs
                _events.tryEmit(
                    FatigueEvent.NudgeAlert(
                        score   = score,
                        message = "You seem a little less sharp than earlier. How are you feeling?",
                    )
                )
            }
        }
    }

    private fun speedSigma(): Float {
        if (speedWindow.size < 2) return 0f
        val mean = speedWindow.average().toFloat()
        val variance = speedWindow.map { (it - mean) * (it - mean) }.average().toFloat()
        return sqrt(variance)
    }

    private fun clamp01(v: Float) = v.coerceIn(0f, 1f)

    private fun bearingDelta(from: Float, to: Float): Float {
        var d = to - from
        while (d > 180f) d -= 360f
        while (d < -180f) d += 360f
        return d
    }

    companion object {
        private const val TAG = "FatigueProxyScorer"

        // RRI drop considered a road impact event (proxy for ISS spike).
        private const val RRI_DROP_THRESHOLD = 0.15f

        // 2s reaction window; cap Δt_react at this if no response detected.
        private const val REACTION_CAP_MS = 2_000L
        private const val RESPONSE_SPEED_THRESHOLD_MS = 0.3f   // m/s delta → braking/lift
        private const val RESPONSE_BEARING_DEG = 1.5f           // steering nudge

        // 30s speed variance window (~30 GPS fixes at 1Hz)
        private const val SPEED_WINDOW_MS = 30_000L
        private const val MAX_SPEED_SAMPLES = 32

        private const val MAX_REACTION_SAMPLES = 20
        private const val BASELINE_WINDOW_MS = 10 * 60 * 1_000L   // 10 min
        private const val DRIFT_WINDOW_MS   = 30 * 60 * 1_000L    // 30 min

        private const val NUDGE_THRESHOLD     = 0.40f
        private const val ESCALATE_THRESHOLD  = 0.60f
        private const val ALERT_COOLDOWN_MS   = 30_000L
    }
}
