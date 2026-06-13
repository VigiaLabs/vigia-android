package com.vigia.core.network.sarvam

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Calls the Sarvam AI STT endpoint (POST https://api.sarvam.ai/speech-to-text).
 *
 * Expects WAV audio (16kHz, 16-bit mono) produced by [VoiceAmplitudeMonitor].
 * Wire format: multipart/form-data — `file` (WAV bytes) + `model` + `language_code`.
 * Response: { "transcript": "...", "language_code": "en-IN" }
 *
 * Security: API key sent via the API-Subscription-Key header. Never logged.
 */
@Singleton
class SarvamSttClientImpl @Inject constructor(
    private val okHttpClient: OkHttpClient,
    @Named("SarvamApiKey") private val apiKey: String,
) : SarvamSttClient {

    override suspend fun transcribe(
        wavBytes: ByteArray,
        languageCode: String,
    ): String = withContext(Dispatchers.IO) {
        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                name     = "file",
                filename = "audio.wav",
                body     = wavBytes.toRequestBody(WAV_MEDIA),
            )
            .addFormDataPart("model", "saarika:v2")
            .addFormDataPart("language_code", languageCode)
            .addFormDataPart("with_timestamps", "false")
            .build()

        val request = Request.Builder()
            .url(BASE_URL)
            .post(multipart)
            .header("API-Subscription-Key", apiKey)
            .build()

        val responseBody = okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Sarvam STT HTTP ${response.code}")
            response.body?.string() ?: throw IOException("Sarvam STT: empty response")
        }

        JSONObject(responseBody).optString("transcript", "")
    }

    private companion object {
        const val BASE_URL = "https://api.sarvam.ai/speech-to-text"
        val WAV_MEDIA = "audio/wav".toMediaType()
    }
}
