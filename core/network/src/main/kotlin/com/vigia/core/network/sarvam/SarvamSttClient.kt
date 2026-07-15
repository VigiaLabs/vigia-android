package com.vigia.core.network.sarvam

import kotlinx.coroutines.flow.StateFlow

/**
 * Transcribes speech audio using Sarvam AI's Saaras v3 model.
 * Supports Indian English and Indian languages with auto-detection.
 */
interface SarvamSttClient {
    /** Language detected for the most recently completed utterance. */
    val lastDetectedLanguageCode: StateFlow<String?>

    /**
     * @param wavBytes     Raw WAV audio bytes (16kHz, 16-bit mono PCM).
     * @param languageCode BCP-47 hint or "unknown" for auto-detection.
     * @return Transcribed text. Throws on network/API error.
     */
    suspend fun transcribe(
        wavBytes: ByteArray,
        languageCode: String = "unknown",
    ): String
}
