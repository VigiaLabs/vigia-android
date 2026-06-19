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
 * Calls the Sarvam AI STT endpoint via the VIGIA backend proxy
 * (POST <VIGIA_API_BASE_URL>/sarvam-proxy/stt).
 *
 * The proxy holds the Sarvam API key in AWS Secrets Manager — it is never
 * included in the APK. Requests are authenticated with the Cognito JWT via
 * the [com.vigia.core.network.auth.VigiaAuthInterceptor].
 *
 * Wire format: multipart/form-data — `file` (WAV bytes) + `model`.
 * Response: { "transcript": "...", "language_code": "en-IN" }
 */
@Singleton
class SarvamSttClientImpl @Inject constructor(
    @Named("VigiaOkHttpClient") private val okHttpClient: OkHttpClient,
    @Named("VigiaApiBaseUrl")   private val baseUrl: String,
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
            .addFormDataPart("model", "saarika:v2.5")
            .addFormDataPart("with_timestamps", "false")

        // Only send language_code when the caller specifies a real BCP-47 code.
        if (languageCode != "unknown" && languageCode.isNotBlank()) {
            builder.addFormDataPart("language_code", languageCode)
        }

        val proxyUrl = baseUrl.trimEnd('/') + "/sarvam-proxy/stt"
        val request = Request.Builder()
            .url(proxyUrl)
            .post(builder.build())
            .build()

        val responseBody = okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                Log.e(TAG, "Sarvam STT proxy HTTP ${response.code}: $errorBody")
                throw IOException("Sarvam STT HTTP ${response.code}")
            }
            response.body?.string() ?: throw IOException("Sarvam STT: empty response")
        }

        JSONObject(responseBody).optString("transcript", "")
    }

    private companion object {
        const val TAG = "SarvamSTT"
        val WAV_MEDIA = "audio/wav".toMediaType()
    }
}
