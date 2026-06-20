package com.vigia.core.sensor.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * Detects user voice onset while the AI is speaking (TTS playback).
 *
 * Runs a lightweight RMS monitor on the mic in parallel with AudioTrack playback.
 * When [BARGE_IN_FRAMES] consecutive chunks exceed [BARGE_IN_THRESHOLD], it emits
 * a [BargeInEvent.Detected] signal.
 *
 * The ViewModel responds by stopping TTS immediately and reopening the mic in
 * auto-VAD mode — giving the user the ability to interrupt the AI mid-sentence,
 * exactly as Gemini Live handles barge-in.
 *
 * Note: Android's AudioRecord can run simultaneously with AudioTrack (they use
 * independent hardware paths). No conflicts with [TtsManager]'s AudioTrack.
 */
@Singleton
class BargeInController @Inject constructor() {

    sealed class BargeInEvent {
        object Detected : BargeInEvent()
    }

    private val _events = MutableSharedFlow<BargeInEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<BargeInEvent> = _events.asSharedFlow()

    private var monitorScope: CoroutineScope? = null

    @SuppressLint("MissingPermission")
    fun startMonitoring() {
        stopMonitoring()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        monitorScope = scope
        scope.launch { runMonitor() }
    }

    fun stopMonitoring() {
        monitorScope?.cancel()
        monitorScope = null
    }

    @SuppressLint("MissingPermission")
    private suspend fun CoroutineScope.runMonitor() {
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            maxOf(minBuf, CHUNK_SAMPLES * 2),
        )
        recorder.startRecording()
        val chunk = ShortArray(CHUNK_SAMPLES)
        var consecutiveVoiceFrames = 0

        try {
            while (isActive) {
                val read = recorder.read(chunk, 0, chunk.size)
                if (read <= 0) continue
                var sumSq = 0.0
                for (i in 0 until read) sumSq += (chunk[i] * chunk[i]).toDouble()
                val rms = (sqrt(sumSq / read) / Short.MAX_VALUE).toFloat()

                if (rms > BARGE_IN_THRESHOLD) {
                    consecutiveVoiceFrames++
                    if (consecutiveVoiceFrames >= BARGE_IN_FRAMES) {
                        _events.tryEmit(BargeInEvent.Detected)
                        // Emit once then reset so we don't spam.
                        consecutiveVoiceFrames = 0
                    }
                } else {
                    consecutiveVoiceFrames = 0
                }
            }
        } finally {
            recorder.stop()
            recorder.release()
        }
    }

    companion object {
        private const val SAMPLE_RATE    = 16_000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT   = AudioFormat.ENCODING_PCM_16BIT
        private const val CHUNK_SAMPLES  = SAMPLE_RATE / 20    // 50ms windows (faster response)
        // Must clear 0.04 RMS for 150ms (3 frames) → avoids triggering on breathing/ambient noise.
        private const val BARGE_IN_THRESHOLD = 0.04f
        private const val BARGE_IN_FRAMES    = 3
    }
}
