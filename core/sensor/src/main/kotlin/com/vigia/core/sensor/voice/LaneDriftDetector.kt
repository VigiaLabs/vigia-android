package com.vigia.core.sensor.voice

import com.vigia.core.model.DriverProfile
import com.vigia.core.model.LocationSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.util.LinkedList
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Detects lane drift from GPS bearing oscillation patterns.
 *
 * Algorithm:
 *   1. Maintain a 4-second rolling window of (bearing, timestamp) samples.
 *   2. At each 1Hz GPS update (when velocity > [MIN_DRIVING_SPEED_MS]):
 *      - Compute max |bearing delta| over the window.
 *      - Check for oscillation: alternating sign changes with small amplitude.
 *      - If |delta| in [DRIFT_MIN_DEG, DRIFT_MAX_DEG] AND ≥ 3 sign changes → drift.
 *   3. Emit [DriftEvent.DriftDetected] with the measured bearing oscillation.
 *   4. 30-second cooldown between alerts to avoid repetition.
 *
 * Distinguishes drift (slow sinusoidal wobble, 2-8° amplitude, ~0.5-1Hz frequency)
 * from intentional lane changes (large rapid bearing change >15° in <2s) and
 * highway curves (smooth monotonic bearing increase).
 *
 * New driver assistance: immediately announces corrective instruction via TTS
 * ("You appear to be drifting left — gently steer right and check lane markings").
 */
@Singleton
class LaneDriftDetector @Inject constructor() {

    sealed class DriftEvent {
        data class DriftDetected(
            val directionLabel: String,
            val oscillationDeg: Float,
            val message: String,
        ) : DriftEvent()
    }

    private val _events = MutableSharedFlow<DriftEvent>(extraBufferCapacity = 8)
    val events: Flow<DriftEvent> = _events.asSharedFlow()

    private data class BearingSample(val bearingDeg: Float, val timestampMs: Long)

    private val bearingWindow = LinkedList<BearingSample>()
    private var lastAlertMs = 0L
    private var detectorScope: CoroutineScope? = null
    private var profile: DriverProfile = DriverProfile.NEW

    fun setProfile(p: DriverProfile) { profile = p }

    fun start(locationFlow: Flow<LocationSnapshot>) {
        stop()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        detectorScope = scope
        locationFlow.onEach { snap -> onLocationUpdate(snap) }.launchIn(scope)
    }

    fun stop() {
        detectorScope?.cancel()
        detectorScope = null
        bearingWindow.clear()
    }

    private fun onLocationUpdate(snap: LocationSnapshot) {
        val now = snap.timestampMs
        if (snap.velocityMs < MIN_DRIVING_SPEED_MS) {
            bearingWindow.clear()
            return
        }

        bearingWindow.addLast(BearingSample(snap.bearingDeg, now))
        // Evict samples older than the analysis window.
        while (bearingWindow.isNotEmpty() && now - bearingWindow.first.timestampMs > WINDOW_MS) {
            bearingWindow.removeFirst()
        }
        if (bearingWindow.size < MIN_SAMPLES) return

        val deltas = bearingWindow.zipWithNext { a, b -> bearingDelta(a.bearingDeg, b.bearingDeg) }
        val maxDelta = deltas.maxOf { abs(it) }

        // Oscillation: alternating sign changes detect wobble vs. smooth turn.
        var signChanges = 0
        for (i in 1 until deltas.size) {
            if (deltas[i - 1] * deltas[i] < 0) signChanges++
        }

        val scaledMin = BASE_DRIFT_MIN_DEG / profile.sProfile
        val isDrift = maxDelta in scaledMin..DRIFT_MAX_DEG && signChanges >= MIN_SIGN_CHANGES
        if (!isDrift) return
        if (now - lastAlertMs < COOLDOWN_MS) return

        lastAlertMs = now
        val netDrift = deltas.sum()
        val direction = if (netDrift < 0) "left" else "right"
        val corrective = if (netDrift < 0) "gently steer right" else "gently steer left"
        _events.tryEmit(
            DriftEvent.DriftDetected(
                directionLabel = direction,
                oscillationDeg = maxDelta,
                message = "Lane drift detected — you are drifting $direction. $corrective and check your lane markings.",
            )
        )
    }

    // Normalize bearing delta to [-180, 180].
    private fun bearingDelta(from: Float, to: Float): Float {
        var d = to - from
        while (d > 180f) d -= 360f
        while (d < -180f) d += 360f
        return d
    }

    companion object {
        private const val MIN_DRIVING_SPEED_MS = 7f           // ~25 km/h — ignore parking lots
        private const val WINDOW_MS            = 4_000L        // 4-second analysis window
        private const val MIN_SAMPLES          = 4             // need at least 4 GPS fixes
        private const val BASE_DRIFT_MIN_DEG   = 2f            // scaled by 1/sProfile at runtime
        private const val DRIFT_MAX_DEG        = 10f           // above this = intentional turn
        private const val MIN_SIGN_CHANGES     = 3             // oscillation signature
        private const val COOLDOWN_MS          = 30_000L       // 30-second inter-alert gap
    }
}
