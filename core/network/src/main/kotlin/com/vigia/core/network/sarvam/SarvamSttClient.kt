package com.vigia.core.network.sarvam

/**
 * Transcribes speech audio using Sarvam AI's saarika:v2 model.
 * Supports Indian English and 10+ Indian languages with auto-detection.
 */
interface SarvamSttClient {
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
