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
 * Calls the Sarvam AI TTS endpoint via the VIGIA backend proxy
 * (POST <VIGIA_API_BASE_URL>/sarvam-proxy/tts).
 *
 * The proxy holds the Sarvam API key in AWS Secrets Manager — it is never
 * included in the APK. Requests are authenticated with the Cognito JWT via
 * the [com.vigia.core.network.auth.VigiaAuthInterceptor].
 *
 * Wire format:
 *   Request:  JSON body with text, target_language_code, speaker, pitch, pace
 *   Response: { "audios": ["<base64-wav>", ...] }
 *
 * The first audio in the array is decoded from base64 and returned as raw WAV bytes.
 */
@Singleton
class SarvamTtsClientImpl @Inject constructor(
    @Named("VigiaOkHttpClient") private val okHttpClient: OkHttpClient,
    @Named("VigiaApiBaseUrl")   private val baseUrl: String,
) : SarvamTtsClient {

    override suspend fun synthesize(
        text: String,
        languageCode: String,
        speaker: String,
    ): ByteArray = withContext(Dispatchers.IO) {
        val bodyJson = JSONObject().apply {
            put("text", text)
            put("target_language_code", languageCode)
            put("speaker", speaker)
            put("pitch", 0)
            put("pace", 1.0)
        }.toString()

        val proxyUrl = baseUrl.trimEnd('/') + "/sarvam-proxy/tts"
        val request = Request.Builder()
            .url(proxyUrl)
            .post(bodyJson.toRequestBody(JSON_MEDIA))
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
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
