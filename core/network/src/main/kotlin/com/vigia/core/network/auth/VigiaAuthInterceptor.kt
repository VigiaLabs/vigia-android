package com.vigia.core.network.auth

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Attaches the Cognito ID token as a Bearer Authorization header to every
 * request made by the Vigia-API OkHttpClient.
 *
 * If [ApiTokenProvider.getIdToken] returns null (demo mode or signed-out),
 * the request proceeds without an Authorization header — unauthenticated routes
 * still work and the 403 from protected routes surfaces as a clear HTTP error.
 *
 * Sarvam API calls use the plain OkHttpClient (no auth header) so this
 * interceptor never touches STT/TTS traffic.
 */
@Singleton
class VigiaAuthInterceptor @Inject constructor(
    private val tokenProvider: ApiTokenProvider,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenProvider.getIdToken()
        val request = if (token != null) {
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }
        return chain.proceed(request)
    }
}
