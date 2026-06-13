package com.vigia.core.network.auth

/**
 * Synchronous bridge between the auth layer and OkHttp interceptors.
 *
 * OkHttp interceptors run on background IO threads, so blocking here is safe.
 * In demo mode the implementation returns null and no Authorization header is attached.
 * In production the implementation fetches the cached Cognito ID token via Amplify.
 */
interface ApiTokenProvider {
    fun getIdToken(): String?
}
