package com.vigia.core.network.sarvam

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val _lastDetectedLanguageCode = MutableStateFlow<String?>(null)
    override val lastDetectedLanguageCode: StateFlow<String?> =
        _lastDetectedLanguageCode.asStateFlow()

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
            .addFormDataPart("model", "saaras:v3")
            .addFormDataPart("mode", "transcribe")
            .addFormDataPart("language_code", languageCode.ifBlank { "unknown" })
            .addFormDataPart("with_timestamps", "false")

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

        JSONObject(responseBody).run {
            _lastDetectedLanguageCode.value = optString("language_code")
                .takeIf { it.isNotBlank() && it != "null" && it != "unknown" }
            optString("transcript", "")
        }
    }

    private companion object {
        const val TAG = "SarvamSTT"
        val WAV_MEDIA = "audio/wav".toMediaType()
    }
}
