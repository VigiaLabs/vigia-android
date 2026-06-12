package com.vigia.core.sensor.keystore

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the 256-bit userAccountSecret inside the Android Keystore.
 *
 * Security boundaries enforced here:
 *  - Raw key bytes never leave the Keystore hardware boundary.
 *  - [sign] performs HMAC-SHA256 in-hardware; only the output tag is returned.
 *  - StrongBox TEE is preferred; a regular Keystore TEE is used as fallback.
 *  - [PURPOSE_SIGN] only — the key cannot be used for encryption or export.
 *  - User authentication is NOT required: the service must sign without user presence.
 */
@Singleton
class KeystoreManager @Inject constructor() {

    private val keyStore: KeyStore = KeyStore.getInstance(PROVIDER).also { it.load(null) }

    /** Idempotent. Generates the key on first call; subsequent calls are no-ops. */
    fun provisionIfAbsent() {
        if (isProvisioned()) return
        generateKey(strongBox = true)
    }

    fun isProvisioned(): Boolean = keyStore.containsAlias(KEY_ALIAS)

    /**
     * Computes HMAC-SHA256([data]) using the hardware-backed key.
     * Never returns the key itself.
     *
     * @throws IllegalStateException if the key has not been provisioned.
     */
    fun sign(data: ByteArray): ByteArray {
        check(isProvisioned()) { "KeystoreManager: key not provisioned — call provisionIfAbsent() first" }
        val mac = Mac.getInstance(MAC_ALGORITHM, PROVIDER)
        mac.init(keyStore.getKey(KEY_ALIAS, null))
        return mac.doFinal(data)
    }

    // ── Key generation ──────────────────────────────────────────────────────

    private fun generateKey(strongBox: Boolean) {
        val spec = buildSpec(strongBox)
        try {
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, PROVIDER)
                .also { it.init(spec) }
                .generateKey()
        } catch (e: StrongBoxUnavailableException) {
            if (strongBox) generateKey(strongBox = false)   // transparent TEE fallback
            else throw e
        }
    }

    private fun buildSpec(strongBox: Boolean) =
        KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_SIGN)
            .setKeySize(256)
            .setIsStrongBoxBacked(strongBox)
            // Device must not be unlocked by the user to use this key —
            // the Foreground Service needs it without user interaction.
            .setUserAuthenticationRequired(false)
            .build()

    companion object {
        private const val PROVIDER    = "AndroidKeyStore"
        private const val KEY_ALIAS   = "vigia_account_secret_v1"
        private const val MAC_ALGORITHM = "HmacSHA256"
    }
}
