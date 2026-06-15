package com.vigia.core.wallet

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.core.content.edit
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.spec.PKCS8EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import android.util.Base64

/**
 * Manages an Ed25519 keypair whose private key is AES-GCM-encrypted inside Android Keystore.
 *
 * The private key (PKCS8, 48 bytes) is encrypted once at generation time and stored in
 * SharedPreferences as base64. The AES-256-GCM wrapping key never leaves the TEE.
 *
 * Thread-safety: all calls are idempotent once the key is provisioned; provision() is
 * safe to call from multiple coroutines (SharedPreferences write is atomic).
 */
class Ed25519KeyStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val ks = KeyStore.getInstance(KEYSTORE_PROVIDER).also { it.load(null) }

    val isProvisioned: Boolean get() = prefs.contains(KEY_PUB_B58)

    /** Returns base58 public key — callers must call [provision] first. */
    val publicKeyBase58: String
        get() = prefs.getString(KEY_PUB_B58, null)
            ?: error("Ed25519KeyStore: not provisioned")

    /**
     * Generates Ed25519 keypair, encrypts the private key with Keystore AES-GCM,
     * and persists both keys. No-op if already provisioned.
     */
    fun provision() {
        if (isProvisioned) return
        ensureAesKey()

        val kpg = KeyPairGenerator.getInstance("Ed25519")
        val keyPair = kpg.generateKeyPair()

        // SubjectPublicKeyInfo encoding: 12-byte header + 32-byte raw key
        val pubBytes = keyPair.public.encoded.drop(12).toByteArray()
        val pubB58 = Base58.encode(pubBytes)

        val encryptedPriv = encryptPrivKey(keyPair.private.encoded)

        prefs.edit {
            putString(KEY_PUB_B58, pubB58)
            putString(KEY_ENC_PRIV, Base64.encodeToString(encryptedPriv.ciphertext, Base64.NO_WRAP))
            putString(KEY_IV, Base64.encodeToString(encryptedPriv.iv, Base64.NO_WRAP))
        }
    }

    /**
     * Signs [message] with the Ed25519 private key.
     * Returns base58-encoded signature (64 bytes).
     */
    fun sign(message: ByteArray): String {
        val encPriv = Base64.decode(prefs.getString(KEY_ENC_PRIV, null)!!, Base64.NO_WRAP)
        val iv = Base64.decode(prefs.getString(KEY_IV, null)!!, Base64.NO_WRAP)
        val pkcs8Bytes = decryptPrivKey(encPriv, iv)

        val privateKey = KeyFactory.getInstance("Ed25519")
            .generatePrivate(PKCS8EncodedKeySpec(pkcs8Bytes))

        val signer = java.security.Signature.getInstance("Ed25519")
        signer.initSign(privateKey)
        signer.update(message)
        return Base58.encode(signer.sign())
    }

    // ── AES-GCM key management ────────────────────────────────────────────────

    private fun ensureAesKey() {
        if (ks.containsAlias(AES_KEY_ALIAS)) return
        val spec = KeyGenParameterSpec.Builder(
            AES_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false)
            .build()
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
            .also { it.init(spec) }
            .generateKey()
    }

    private data class EncryptedBlob(val ciphertext: ByteArray, val iv: ByteArray)

    private fun encryptPrivKey(plaintext: ByteArray): EncryptedBlob {
        val key = ks.getKey(AES_KEY_ALIAS, null)
        val cipher = Cipher.getInstance(AES_TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return EncryptedBlob(cipher.doFinal(plaintext), cipher.iv)
    }

    private fun decryptPrivKey(ciphertext: ByteArray, iv: ByteArray): ByteArray {
        val key = ks.getKey(AES_KEY_ALIAS, null)
        val cipher = Cipher.getInstance(AES_TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val PREFS_NAME        = "vigia_wallet_v1"
        private const val AES_KEY_ALIAS     = "vigia_wallet_aes_v1"
        private const val AES_TRANSFORM     = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS      = 128
        private const val KEY_PUB_B58       = "pub_b58"
        private const val KEY_ENC_PRIV      = "enc_priv"
        private const val KEY_IV            = "iv"
    }
}
