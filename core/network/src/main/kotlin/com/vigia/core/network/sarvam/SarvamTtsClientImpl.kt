package com.vigia.core.network.sarvam

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Calls the Sarvam AI TTS endpoint (POST https://api.sarvam.ai/text-to-speech).
 *
 * Wire format:
 *   Request:  JSON body with inputs[], target_language_code, speaker, model="bulbul:v1"
 *   Response: { "audios": ["<base64-wav>", ...] }
 *
 * The first audio in the array is decoded from base64 and returned as raw WAV bytes.
 * Callers (TtsManager) are responsible for playback — no audio session is managed here.
 *
 * Security: API key sent via the API-Subscription-Key header. Never logged.
 */
@Singleton
class SarvamTtsClientImpl @Inject constructor(
    private val okHttpClient: OkHttpClient,
    @Named("SarvamApiKey") private val apiKey: String,
) : SarvamTtsClient {

    override suspend fun synthesize(
        text: String,
        languageCode: String,
        speaker: String,
    ): ByteArray = withContext(Dispatchers.IO) {
        val bodyJson = JSONObject().apply {
            put("inputs", JSONArray().apply { put(text) })
            put("target_language_code", languageCode)
            put("speaker", speaker)
            put("pitch", 0)
            put("pace", 1.0)
            put("loudness", 1.5)
            put("speech_sample_rate", 22050)
            put("enable_preprocessing", true)
            put("model", "bulbul:v1")
        }.toString()

        val request = Request.Builder()
            .url(BASE_URL)
            .post(bodyJson.toRequestBody(JSON_MEDIA))
            .header("API-Subscription-Key", apiKey)
            .header("Content-Type", "application/json")
            .build()

        val responseBody = okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Sarvam TTS HTTP ${response.code}")
            response.body?.string() ?: throw IOException("Sarvam TTS: empty response")
        }

        val audios = JSONObject(responseBody).getJSONArray("audios")
        if (audios.length() == 0) throw IOException("Sarvam TTS: no audio in response")

        Base64.decode(audios.getString(0), Base64.DEFAULT)
    }

    private companion object {
        const val BASE_URL = "https://api.sarvam.ai/text-to-speech"
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
