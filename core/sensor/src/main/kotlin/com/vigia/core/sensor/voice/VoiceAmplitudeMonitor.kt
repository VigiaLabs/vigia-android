package com.vigia.core.sensor.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
 * Records microphone audio and exposes:
 *  - [amplitude]  — normalised RMS amplitude (0..1) sampled every ~50ms.
 *    Feed directly into [AuroraMist] and [ReactiveVoiceOrb] so the aurora breathes
 *    with the user's real voice level instead of simulated oscillators.
 *  - [stopAndGetWav] — stops recording and returns all captured audio as a complete
 *    WAV file (PCM 16-bit, 16kHz, mono) ready for the Sarvam STT API.
 *
 * RECORD_AUDIO permission must be granted before [startRecording] is called.
 * The caller (CopilotViewModel) checks the permission via a runtime-permission flow.
 *
 * Audio parameters match Sarvam saarika:v2's preferred input format (16kHz, 16-bit mono).
 */
@Singleton
class VoiceAmplitudeMonitor @Inject constructor() {

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    private var recordScope: CoroutineScope? = null
    private var recordJob: Job? = null

    // Protected by the single recording coroutine — no concurrent writes.
    private val pcmBuffer = ByteArrayOutputStream()

    @SuppressLint("MissingPermission")
    fun startRecording() {
        stopRecordingInternal()
        pcmBuffer.reset()

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

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        recordScope = scope

        recordJob = scope.launch {
            val buf = ShortArray(CHUNK_SAMPLES)
            while (isActive) {
                val read = recorder.read(buf, 0, buf.size)
                if (read <= 0) continue

                // RMS amplitude normalised to [0, 1].
                var sumSq = 0.0
                for (i in 0 until read) sumSq += (buf[i] * buf[i]).toDouble()
                val rms = sqrt(sumSq / read) / Short.MAX_VALUE.toDouble()
                _amplitude.value = rms.toFloat().coerceIn(0f, 1f)

                // Accumulate raw PCM for WAV encoding on stop.
                val bytes = ByteArray(read * 2)
                ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(buf, 0, read)
                pcmBuffer.write(bytes)
            }
            recorder.stop()
            recorder.release()
            _amplitude.value = 0f
        }
    }

    /**
     * Stops recording and returns the complete WAV file bytes.
     * Safe to call even if recording was never started — returns an empty WAV header.
     */
    fun stopAndGetWav(): ByteArray {
        stopRecordingInternal()
        val pcm = pcmBuffer.toByteArray()
        pcmBuffer.reset()
        return buildWav(pcm)
    }

    fun stopSilently() {
        stopRecordingInternal()
        _amplitude.value = 0f
        pcmBuffer.reset()
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun stopRecordingInternal() {
        recordJob?.cancel()
        recordScope?.cancel()
        recordJob  = null
        recordScope = null
    }

    /**
     * Wraps raw 16-bit LE PCM in a RIFF/WAVE header so Sarvam STT accepts it.
     * Header layout: standard 44-byte PCM WAV.
     */
    private fun buildWav(pcm: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(44 + pcm.size)
        val dos = DataOutputStream(out)
        val numChannels   = 1
        val bitsPerSample = 16
        val byteRate      = SAMPLE_RATE * numChannels * bitsPerSample / 8
        val blockAlign    = numChannels * bitsPerSample / 8

        // RIFF chunk
        dos.writeBytes("RIFF")
        dos.writeIntLE(36 + pcm.size)
        dos.writeBytes("WAVE")
        // fmt sub-chunk
        dos.writeBytes("fmt ")
        dos.writeIntLE(16)
        dos.writeShortLE(1)                    // PCM
        dos.writeShortLE(numChannels)
        dos.writeIntLE(SAMPLE_RATE)
        dos.writeIntLE(byteRate)
        dos.writeShortLE(blockAlign)
        dos.writeShortLE(bitsPerSample)
        // data sub-chunk
        dos.writeBytes("data")
        dos.writeIntLE(pcm.size)
        dos.write(pcm)
        return out.toByteArray()
    }

    private fun DataOutputStream.writeIntLE(v: Int) {
        write(v and 0xFF)
        write((v shr 8) and 0xFF)
        write((v shr 16) and 0xFF)
        write((v shr 24) and 0xFF)
    }

    private fun DataOutputStream.writeShortLE(v: Int) {
        write(v and 0xFF)
        write((v shr 8) and 0xFF)
    }

    private companion object {
        const val SAMPLE_RATE    = 16_000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT   = AudioFormat.ENCODING_PCM_16BIT
        // ~50ms window per RMS calculation.
        const val CHUNK_SAMPLES  = SAMPLE_RATE / 20
    }
}
