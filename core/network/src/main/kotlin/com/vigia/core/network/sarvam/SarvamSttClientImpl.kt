package com.vigia.core.network.sarvam

import android.util.Log
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
 * Wire format: multipart/form-data — `file` (WAV bytes) + `model`.
 * language_code is omitted so Sarvam auto-detects the language; passing an
 * invalid value like "unknown" causes a 400.
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
        val builder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                name     = "file",
                filename = "audio.wav",
                body     = wavBytes.toRequestBody(WAV_MEDIA),
            )
            .addFormDataPart("model", "saarika:v2")
            .addFormDataPart("with_timestamps", "false")

        // Only send language_code when the caller specifies a real BCP-47 code.
        // Omitting it lets Sarvam auto-detect; sending "unknown" causes HTTP 400.
        if (languageCode != "unknown" && languageCode.isNotBlank()) {
            builder.addFormDataPart("language_code", languageCode)
        }

        val request = Request.Builder()
            .url(BASE_URL)
            .post(builder.build())
            .header("API-Subscription-Key", apiKey)
            .build()

        val responseBody = okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                Log.e(TAG, "Sarvam STT HTTP ${response.code}: $errorBody")
                throw IOException("Sarvam STT HTTP ${response.code}: $errorBody")
            }
            response.body?.string() ?: throw IOException("Sarvam STT: empty response")
        }

        JSONObject(responseBody).optString("transcript", "")
    }

    private companion object {
        const val TAG     = "SarvamSTT"
        const val BASE_URL = "https://api.sarvam.ai/speech-to-text"
        val WAV_MEDIA = "audio/wav".toMediaType()
    }
}
