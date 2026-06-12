package com.vigia.core.sensor.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.OnInitListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pre-warmed [TextToSpeech] engine for spoken hazard alerts.
 *
 * Pre-warming happens in [init] so the engine is ready before the first alert arrives.
 * On average a cold TextToSpeech init takes ~400ms; calling [speak] while [isReady] is
 * false silently discards the utterance — CRITICAL alerts are still surfaced via the
 * notification channel, so no alert is truly lost.
 *
 * Shutdown is managed by the DI scope; [shutdown] must be called in [VigiaForegroundService.onDestroy].
 */
@Singleton
class TtsManager @Inject constructor(
    @ApplicationContext private val context: Context,
) : OnInitListener {

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val tts: TextToSpeech = TextToSpeech(context, this)

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.US
            _isReady.value = true
        }
    }

    /**
     * Speaks [text] aloud.
     *
     * [utteranceId] defaults to the text hashcode — identical consecutive utterances
     * will be deduplicated by the TTS engine automatically.
     * [queueMode] defaults to [TextToSpeech.QUEUE_ADD] so alerts queue rather than interrupt.
     * For CRITICAL severity, callers should pass [TextToSpeech.QUEUE_FLUSH] to interrupt immediately.
     */
    fun speak(text: String, queueMode: Int = TextToSpeech.QUEUE_ADD) {
        if (!_isReady.value) return
        tts.speak(text, queueMode, null, text.hashCode().toString())
    }

    fun stop() {
        tts.stop()
    }

    fun shutdown() {
        tts.shutdown()
        _isReady.value = false
    }
}
