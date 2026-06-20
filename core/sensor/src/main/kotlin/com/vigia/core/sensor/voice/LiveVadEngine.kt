package com.vigia.core.sensor.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * Real-time Voice Activity Detection engine.
 *
 * Replaces the manual tap-to-stop pattern in [VoiceAmplitudeMonitor] with
 * automatic utterance boundary detection based on RMS energy thresholding.
 *
 * State machine:
 *   SILENCE → (onset: [ONSET_FRAMES] frames > [SPEECH_RMS_THRESHOLD]) → SPEECH
 *   SPEECH  → (hangover: [HANGOVER_FRAMES] frames < [SILENCE_RMS_THRESHOLD]) → emit utterance
 *
 * Each [CHUNK_SAMPLES] window (~100ms at 16kHz) is evaluated. The engine emits:
 *   - [VadEvent.SpeechStart] when the onset condition first fires
 *   - [VadEvent.AmplitudeUpdate] on every chunk so the aurora mist stays alive
 *   - [VadEvent.UtteranceComplete] with the full PCM+WAV when silence hangover expires
 *
 * Gemini Live equivalent: this is the client-side endpointing that lets the user
 * speak naturally without pressing a button — identical to how Gemini Live's
 * automatic turn detection works in the browser.
 */
@Singleton
class LiveVadEngine @Inject constructor() {

    sealed class VadEvent {
        object SpeechStart : VadEvent()
        data class AmplitudeUpdate(val rms: Float) : VadEvent()
        data class UtteranceComplete(val wav: ByteArray) : VadEvent()
    }

    private val _events = MutableSharedFlow<VadEvent>(extraBufferCapacity = 64)
    val events: Flow<VadEvent> = _events.asSharedFlow()

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    private var engineScope: CoroutineScope? = null

    @SuppressLint("MissingPermission")
    fun start() {
        stop()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        engineScope = scope
        scope.launch { runVadLoop() }
    }

    fun stop() {
        engineScope?.cancel()
        engineScope = null
        _amplitude.value = 0f
    }

    // ── VAD loop ──────────────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    private suspend fun CoroutineScope.runVadLoop() {
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        val bufSize = maxOf(minBuf, CHUNK_SAMPLES * 2)

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufSize,
        )
        recorder.startRecording()

        val chunk = ShortArray(CHUNK_SAMPLES)
        val pcmBuffer = ByteArrayOutputStream()
        var state = VadState.SILENCE
        var onsetCount = 0
        var hangoverCount = 0

        try {
            while (isActive) {
                val read = recorder.read(chunk, 0, chunk.size)
                if (read <= 0) continue

                val rms = rms(chunk, read)
                _amplitude.value = rms
                _events.tryEmit(VadEvent.AmplitudeUpdate(rms))

                when (state) {
                    VadState.SILENCE -> {
                        if (rms > SPEECH_RMS_THRESHOLD) {
                            onsetCount++
                            if (onsetCount >= ONSET_FRAMES) {
                                state = VadState.SPEECH
                                hangoverCount = 0
                                pcmBuffer.reset()
                                _events.tryEmit(VadEvent.SpeechStart)
                            }
                        } else {
                            onsetCount = 0
                        }
                        // Buffer pre-roll so we don't lose the onset frames
                        appendPcm(pcmBuffer, chunk, read)
                        if (pcmBuffer.size() > PRE_ROLL_BYTES) {
                            val excess = pcmBuffer.toByteArray()
                            pcmBuffer.reset()
                            pcmBuffer.write(excess, excess.size - PRE_ROLL_BYTES, PRE_ROLL_BYTES)
                        }
                    }
                    VadState.SPEECH -> {
                        appendPcm(pcmBuffer, chunk, read)
                        if (rms < SILENCE_RMS_THRESHOLD) {
                            hangoverCount++
                            if (hangoverCount >= HANGOVER_FRAMES) {
                                state = VadState.SILENCE
                                onsetCount = 0
                                hangoverCount = 0
                                val pcm = pcmBuffer.toByteArray()
                                pcmBuffer.reset()
                                _amplitude.value = 0f
                                _events.tryEmit(VadEvent.UtteranceComplete(buildWav(pcm)))
                            }
                        } else {
                            hangoverCount = 0
                        }
                    }
                }
            }
        } finally {
            recorder.stop()
            recorder.release()
            _amplitude.value = 0f
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun rms(buf: ShortArray, count: Int): Float {
        var sumSq = 0.0
        for (i in 0 until count) sumSq += (buf[i] * buf[i]).toDouble()
        return (sqrt(sumSq / count) / Short.MAX_VALUE.toDouble()).toFloat().coerceIn(0f, 1f)
    }

    private fun appendPcm(out: ByteArrayOutputStream, buf: ShortArray, count: Int) {
        val bytes = ByteArray(count * 2)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(buf, 0, count)
        out.write(bytes)
    }

    private fun buildWav(pcm: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(44 + pcm.size)
        val dos = DataOutputStream(out)
        val byteRate = SAMPLE_RATE * 2
        dos.writeBytes("RIFF"); dos.writeIntLE(36 + pcm.size); dos.writeBytes("WAVE")
        dos.writeBytes("fmt "); dos.writeIntLE(16)
        dos.writeShortLE(1); dos.writeShortLE(1)
        dos.writeIntLE(SAMPLE_RATE); dos.writeIntLE(byteRate)
        dos.writeShortLE(2); dos.writeShortLE(16)
        dos.writeBytes("data"); dos.writeIntLE(pcm.size)
        dos.write(pcm)
        return out.toByteArray()
    }

    private fun DataOutputStream.writeIntLE(v: Int) {
        write(v and 0xFF); write((v shr 8) and 0xFF)
        write((v shr 16) and 0xFF); write((v shr 24) and 0xFF)
    }
    private fun DataOutputStream.writeShortLE(v: Int) {
        write(v and 0xFF); write((v shr 8) and 0xFF)
    }

    private enum class VadState { SILENCE, SPEECH }

    companion object {
        private const val SAMPLE_RATE     = 16_000
        private const val CHANNEL_CONFIG  = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT    = AudioFormat.ENCODING_PCM_16BIT
        // 100ms chunks — fine enough for fast onset, not so small it thrashes CPU.
        private const val CHUNK_SAMPLES   = SAMPLE_RATE / 10

        // Onset: 3 consecutive chunks (300ms) above threshold triggers SPEECH.
        private const val SPEECH_RMS_THRESHOLD = 0.015f
        private const val ONSET_FRAMES         = 3

        // Hangover: 8 consecutive chunks (800ms) below threshold = end of utterance.
        private const val SILENCE_RMS_THRESHOLD = 0.010f
        private const val HANGOVER_FRAMES        = 8

        // Pre-roll: 300ms of audio buffered in SILENCE so onset frames aren't lost.
        private const val PRE_ROLL_BYTES = CHUNK_SAMPLES * 2 * ONSET_FRAMES
    }
}
