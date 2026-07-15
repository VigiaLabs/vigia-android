package com.vigia.core.sensor.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.ToneGenerator
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.OnInitListener
import android.util.Log
import com.vigia.core.model.DriverProfile
import com.vigia.core.network.sarvam.SarvamTtsClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * Two-tier speech output:
 *
 *  1. **Sarvam AI** (primary) — natural Indian-English voice via [SarvamTtsClient].
 *     Used for AI copilot responses. Runs on a background coroutine and plays via
 *     [AudioTrack] so no temporary files touch the filesystem.
 *     [speakSarvam] accepts an [onDone] callback invoked on the IO thread when
 *     the AudioTrack finishes — the ViewModel uses this to auto-loop the mic for
 *     the next conversational turn.
 *
 *  2. **Android TTS** (fallback / alerts) — used for hazard alerts because they
 *     must fire even when the network is unavailable (Doze, no connectivity).
 *     CRITICAL alerts use [TextToSpeech.QUEUE_FLUSH] to interrupt Sarvam playback.
 *
 * [isSpeaking] reflects whether Sarvam TTS is actively playing audio — the UI
 * reads this to keep the aurora mist visible and animated while speech plays back.
 */
@Singleton
class TtsManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sarvamTtsClient: SarvamTtsClient,
) : OnInitListener {

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _ttsAmplitude = MutableStateFlow(0f)
    val ttsAmplitude: StateFlow<Float> = _ttsAmplitude.asStateFlow()

    private val _lastSpokenText = MutableStateFlow<String?>(null)
    val lastSpokenText: StateFlow<String?> = _lastSpokenText.asStateFlow()

    private val androidTts: TextToSpeech = TextToSpeech(context, this)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Profile-derived audio parameters (§3.3 spec). Applied per-speak for Android TTS
    // and as a volume multiplier for the Sarvam AudioTrack.
    // Base Sarvam volume is 0.6 so ELDERLY (+4 dB → ×1.585) reaches 0.95 without clipping.
    @Volatile private var profileSpeechRate: Float = 1.0f
    @Volatile private var profileVolume: Float = 0.6f   // linear [0,1] for AudioTrack

    fun setProfile(profile: DriverProfile) {
        profileSpeechRate = profile.ttsRate
        // Convert dB gain to linear, anchored at 0.6 for EXPERT (0 dB).
        profileVolume = (0.6f * Math.pow(10.0, profile.ttsGainDb / 20.0).toFloat())
            .coerceIn(0f, 1f)
        if (_isReady.value) androidTts.setSpeechRate(profile.ttsRate)
    }

    @Volatile private var activeTrack: AudioTrack? = null
    private var listeningCueJob: Job? = null

    private data class SpeechItem(
        val text: String,
        val languageCode: String,
        val pace: Double,
        val generation: Long,
        val onDone: () -> Unit,
    )

    private val playbackGeneration = AtomicLong(0L)

    // Unlimited channel — items are drained sequentially by a single coroutine so
    // step narrations and the final answer always play in arrival order.
    private val speechQueue = Channel<SpeechItem>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (item in speechQueue) {
                try {
                    if (item.generation != playbackGeneration.get()) continue
                    _lastSpokenText.value = item.text
                    val wav = sarvamTtsClient.synthesize(item.text, item.languageCode, pace = item.pace)
                    if (item.generation == playbackGeneration.get()) {
                        playWavAndAwait(wav, item.generation)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Sarvam TTS failed, falling back to Android TTS: ${e.message}")
                    _isSpeaking.value = false
                    if (item.generation == playbackGeneration.get()) {
                        speak(item.text, TextToSpeech.QUEUE_ADD)
                    }
                } finally {
                    item.onDone()
                }
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            androidTts.language = Locale.US
            androidTts.setSpeechRate(profileSpeechRate)
            _isReady.value = true
        }
    }

    /**
     * Enqueues [text] for Sarvam AI voice synthesis.
     * Items are played sequentially in arrival order — callers can enqueue step
     * narrations and the final answer in one pass and they will play back-to-back
     * without overlap. [onDone] fires on the IO thread after this specific item
     * finishes playing (use it to reopen the mic on the last item).
     */
    fun speakSarvam(
        text: String,
        languageCode: String = "en-IN",
        onDone: () -> Unit = {},
    ) {
        if (text.isBlank()) { onDone(); return }
        speechQueue.trySend(
            SpeechItem(
                text = text,
                languageCode = languageCode,
                pace = profileSpeechRate.toDouble(),
                generation = playbackGeneration.get(),
                onDone = onDone,
            )
        )
    }

    /**
     * Speaks [text] using the pre-warmed Android TTS engine.
     * Use for hazard alerts — works offline and fires immediately.
     * For CRITICAL severity pass [TextToSpeech.QUEUE_FLUSH] to interrupt anything playing.
     */
    fun speak(text: String, queueMode: Int = TextToSpeech.QUEUE_ADD) {
        if (!_isReady.value) return
        _lastSpokenText.value = text
        androidTts.speak(text, queueMode, null, text.hashCode().toString())
    }

    fun playListeningCue() {
        listeningCueJob?.cancel()
        listeningCueJob = scope.launch {
            val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 55)
            try {
                tone.startTone(ToneGenerator.TONE_PROP_ACK, 110)
                delay(140)
            } finally {
                tone.release()
            }
        }
    }

    fun stop() {
        playbackGeneration.incrementAndGet()
        listeningCueJob?.cancel()
        listeningCueJob = null
        androidTts.stop()
        stopTrack()
        // Drain queued items and fire their onDone callbacks so the ViewModel
        // is never stranded waiting for a mic-reopen signal.
        while (true) {
            val item = speechQueue.tryReceive().getOrNull() ?: break
            item.onDone()
        }
    }

    fun shutdown() {
        androidTts.shutdown()
        stopTrack()
        _isReady.value = false
    }

    // ── WAV → AudioTrack playback ─────────────────────────────────────────────

    /**
     * Decodes the 44-byte WAV header, plays the PCM payload via [AudioTrack] in
     * static mode, and **suspends** until playback is complete. Sets [isSpeaking]
     * for the duration so the UI can animate the aurora mist.
     */
    private suspend fun playWavAndAwait(wav: ByteArray, generation: Long) {
        if (wav.size < 44 || generation != playbackGeneration.get()) return

        val header = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        header.position(24); val sampleRate    = header.int
        header.position(34); val bitsPerSample = header.short.toInt()

        val encoding      = if (bitsPerSample == 16) AudioFormat.ENCODING_PCM_16BIT
                            else AudioFormat.ENCODING_PCM_8BIT
        val bytesPerFrame = if (bitsPerSample == 16) 2 else 1
        val windowFrames  = sampleRate / 10   // 100 ms RMS window

        stopTrack()  // release any previous track first

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(encoding)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(wav.size - 44)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        activeTrack = track
        track.write(wav, 44, wav.size - 44)
        track.setVolume(profileVolume)

        _isSpeaking.value = true
        try {
            track.play()
            while (generation == playbackGeneration.get() &&
                   runCatching { track.playState }.getOrDefault(AudioTrack.PLAYSTATE_STOPPED)
                   == AudioTrack.PLAYSTATE_PLAYING) {
                // Compute RMS of the 100 ms PCM window around the current playhead
                // so the UI can animate the orb and aurora to the AI's voice level.
                val headFrame  = track.playbackHeadPosition
                val startByte  = ((headFrame - windowFrames / 2) * bytesPerFrame)
                    .coerceAtLeast(0)
                val endByte    = ((headFrame + windowFrames / 2) * bytesPerFrame)
                    .coerceAtMost(wav.size - 44)
                if (endByte > startByte && bytesPerFrame == 2) {
                    var sumSq = 0.0; var count = 0; var i = startByte + 44
                    while (i + 1 < endByte + 44) {
                        val lo = wav[i].toInt() and 0xFF
                        val hi = wav[i + 1].toInt()
                        val sample = ((hi shl 8) or lo).toShort().toFloat()
                        sumSq += sample * sample; count++; i += 2
                    }
                    _ttsAmplitude.value = if (count > 0)
                        (sqrt(sumSq / count).toFloat() / 32768f).coerceIn(0f, 1f) else 0f
                }
                delay(80)
            }
        } finally {
            _ttsAmplitude.value = 0f
            _isSpeaking.value = false
            if (activeTrack === track) activeTrack = null
            runCatching { track.release() }
        }
    }

    private fun stopTrack() {
        val t = activeTrack ?: return
        activeTrack = null
        _isSpeaking.value = false
        _ttsAmplitude.value = 0f
        if (t.state == AudioTrack.STATE_INITIALIZED) runCatching { t.stop() }
        runCatching { t.release() }
    }

    private companion object { const val TAG = "TtsManager" }
}
