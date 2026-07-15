package com.vigia.core.sensor.voice

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

@Singleton
class BargeInController @Inject constructor(
    @ApplicationContext context: Context,
) {

    sealed class BargeInEvent {
        object SpeechStart : BargeInEvent()
        data class UtteranceComplete(val wav: ByteArray) : BargeInEvent()
    }

    private val _events = MutableSharedFlow<BargeInEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<BargeInEvent> = _events.asSharedFlow()

    private var monitorScope: CoroutineScope? = null
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var previousAudioMode: Int? = null

    @SuppressLint("MissingPermission")
    fun startMonitoring() {
        stopMonitoring()
        previousAudioMode = audioManager.mode
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        monitorScope = scope
        scope.launch {
            try {
                runMonitor()
            } finally {
                if (monitorScope === scope) {
                    monitorScope = null
                    restoreAudioMode()
                }
            }
        }
    }

    fun stopMonitoring() {
        monitorScope?.cancel()
        monitorScope = null
        restoreAudioMode()
    }

    private fun restoreAudioMode() {
        val mode = previousAudioMode ?: return
        previousAudioMode = null
        audioManager.mode = mode
    }

    @SuppressLint("MissingPermission")
    private suspend fun CoroutineScope.runMonitor() {
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            maxOf(minBuffer, CHUNK_SAMPLES * 2),
        )
        val echoCanceler = if (AcousticEchoCanceler.isAvailable()) {
            AcousticEchoCanceler.create(recorder.audioSessionId)?.apply { enabled = true }
        } else null
        val noiseSuppressor = if (NoiseSuppressor.isAvailable()) {
            NoiseSuppressor.create(recorder.audioSessionId)?.apply { enabled = true }
        } else null
        val detector = AdaptiveSpeechDetector(
            calibrationFrames = CALIBRATION_FRAMES,
            onsetFrames = ONSET_FRAMES,
            hangoverFrames = HANGOVER_FRAMES,
            minimumSpeechRms = 0.01f,
        )
        val chunk = ShortArray(CHUNK_SAMPLES)
        val pcmBuffer = ByteArrayOutputStream()
        var recordingSpeech = false

        recorder.startRecording()
        try {
            while (isActive) {
                val read = recorder.read(chunk, 0, chunk.size)
                if (read <= 0) continue
                val rms = rms(chunk, read)

                appendPcm(pcmBuffer, chunk, read)
                if (!recordingSpeech) trimToPreRoll(pcmBuffer)

                when (detector.process(rms)) {
                    AdaptiveSpeechDetector.Result.SpeechStarted -> {
                        recordingSpeech = true
                        _events.tryEmit(BargeInEvent.SpeechStart)
                    }
                    AdaptiveSpeechDetector.Result.SpeechEnded -> {
                        _events.tryEmit(BargeInEvent.UtteranceComplete(buildWav(pcmBuffer.toByteArray())))
                        return
                    }
                    else -> Unit
                }
            }
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
            echoCanceler?.release()
            noiseSuppressor?.release()
        }
    }

    private fun rms(buffer: ShortArray, count: Int): Float {
        var sumSquares = 0.0
        for (index in 0 until count) sumSquares += (buffer[index] * buffer[index]).toDouble()
        return (sqrt(sumSquares / count) / Short.MAX_VALUE).toFloat().coerceIn(0f, 1f)
    }

    private fun appendPcm(output: ByteArrayOutputStream, buffer: ShortArray, count: Int) {
        val bytes = ByteArray(count * 2)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(buffer, 0, count)
        output.write(bytes)
    }

    private fun trimToPreRoll(output: ByteArrayOutputStream) {
        if (output.size() <= PRE_ROLL_BYTES) return
        val bytes = output.toByteArray()
        output.reset()
        output.write(bytes, bytes.size - PRE_ROLL_BYTES, PRE_ROLL_BYTES)
    }

    private fun buildWav(pcm: ByteArray): ByteArray {
        val output = ByteArrayOutputStream(44 + pcm.size)
        val data = DataOutputStream(output)
        data.writeBytes("RIFF"); data.writeIntLittleEndian(36 + pcm.size); data.writeBytes("WAVE")
        data.writeBytes("fmt "); data.writeIntLittleEndian(16)
        data.writeShortLittleEndian(1); data.writeShortLittleEndian(1)
        data.writeIntLittleEndian(SAMPLE_RATE); data.writeIntLittleEndian(SAMPLE_RATE * 2)
        data.writeShortLittleEndian(2); data.writeShortLittleEndian(16)
        data.writeBytes("data"); data.writeIntLittleEndian(pcm.size); data.write(pcm)
        return output.toByteArray()
    }

    private fun DataOutputStream.writeIntLittleEndian(value: Int) {
        write(value and 0xFF); write((value shr 8) and 0xFF)
        write((value shr 16) and 0xFF); write((value shr 24) and 0xFF)
    }

    private fun DataOutputStream.writeShortLittleEndian(value: Int) {
        write(value and 0xFF); write((value shr 8) and 0xFF)
    }

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val CHUNK_SAMPLES = SAMPLE_RATE / 20
        const val CALIBRATION_FRAMES = 10
        const val ONSET_FRAMES = 3
        const val HANGOVER_FRAMES = 16
        const val PRE_ROLL_BYTES = CHUNK_SAMPLES * 2 * ONSET_FRAMES
    }
}
