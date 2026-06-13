package com.vigia.core.network.sarvam

/**
 * Synthesizes text to speech using Sarvam AI's bulbul:v1 model.
 * Returns raw WAV bytes ready for AudioTrack or MediaPlayer playback.
 */
interface SarvamTtsClient {
    /**
     * @param text         The text to synthesize.
     * @param languageCode BCP-47 code, e.g. "en-IN", "hi-IN". Defaults to Indian English.
     * @param speaker      Sarvam voice name. Defaults to "meera" (female, en-IN).
     * @return Raw WAV bytes. Throws on network/API error.
     */
    suspend fun synthesize(
        text: String,
        languageCode: String = "en-IN",
        speaker: String = "meera",
    ): ByteArray
}
