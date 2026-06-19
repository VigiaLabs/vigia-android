package com.vigia.core.sensor.keystore

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.KeyAgreement
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the device's BLE identity key inside the Android Keystore TEE.
 *
 * Key type: EC P-256 (secp256r1) with PURPOSE_AGREE_KEY | PURPOSE_SIGN.
 *   - ECDH key agreement: derives a shared secret with the Pi's P-256 key.
 *   - ECDSA-SHA256 signing: signs the RESPONSE payload during handshake.
 *   - The private key never leaves the TEE hardware boundary.
 *   - StrongBox is preferred; regular Keystore TEE is used as a silent fallback.
 *
 * Design spec: §4.2a — P-256 on both ends, hardware-backed on the phone.
 * Matches Pi-side mbedTLS ECDH implementation in ble_gatt_node.cpp (Phase 2).
 */
@Singleton
class KeystoreManager @Inject constructor() {

    private val keyStore: KeyStore = KeyStore.getInstance(PROVIDER).also { it.load(null) }

    /** Idempotent. Generates the EC P-256 identity key on first call; no-op thereafter. */
    fun provisionIfAbsent() {
        if (keyStore.containsAlias(KEY_ALIAS)) return
        generateEcKey(strongBox = true)
    }

    fun isProvisioned(): Boolean = keyStore.containsAlias(KEY_ALIAS)

    /**
     * Returns the phone's P-256 public key as an uncompressed point (65 bytes: 0x04 || X || Y).
     * Safe to send to the Pi over BLE — no private material is included.
     */
    fun getPublicKeyUncompressed(): ByteArray {
        val cert = keyStore.getCertificate(KEY_ALIAS)
            ?: error("KeystoreManager: not provisioned — call provisionIfAbsent() first")
        // X.509-encoded pub key starts with a SubjectPublicKeyInfo header (26 bytes for P-256);
        // the raw EC point (uncompressed, 65 bytes) is the last 65 bytes of the encoding.
        val encoded = cert.publicKey.encoded
        return encoded.copyOfRange(encoded.size - 65, encoded.size)
    }

    /** Exposed for ECDH [computeSharedSecret] — returns the full Java [PublicKey]. */
    fun getPublicKey(): PublicKey =
        keyStore.getCertificate(KEY_ALIAS)?.publicKey
            ?: error("KeystoreManager: not provisioned")

    /**
     * Performs ECDH key agreement with [peerPublicKeyBytes] (uncompressed P-256 point, 65 bytes).
     * The raw shared secret (32 bytes) is returned. Callers must derive a session key from it
     * using HKDF-SHA256 (see [EcdhHandshake.hkdfSha256]).
     *
     * The phone's private key never leaves the Keystore hardware boundary.
     */
    fun computeSharedSecret(peerPublicKeyBytes: ByteArray): ByteArray {
        val privateKey = keyStore.getKey(KEY_ALIAS, null) as PrivateKey
        val peerPub = decodeUncompressedP256PublicKey(peerPublicKeyBytes)
        val ka = KeyAgreement.getInstance("ECDH", PROVIDER)
        ka.init(privateKey)
        ka.doPhase(peerPub, true)
        return ka.generateSecret()
    }

    /**
     * Signs [data] with ECDSA-SHA256 using the hardware-backed private key.
     * Returns a DER-encoded signature (~70 bytes).
     * Used in RESPONSE step of the BLE handshake (design spec §4.2, step 3).
     */
    fun signEcdsa(data: ByteArray): ByteArray {
        val privateKey = keyStore.getKey(KEY_ALIAS, null) as PrivateKey
        val sig = Signature.getInstance("SHA256withECDSA", PROVIDER)
        sig.initSign(privateKey)
        sig.update(data)
        return sig.sign()
    }

    // ── Key generation ──────────────────────────────────────────────────────

    private fun generateEcKey(strongBox: Boolean) {
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_AGREE_KEY or KeyProperties.PURPOSE_SIGN,
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setUserAuthenticationRequired(false)
            .apply { if (Build.VERSION.SDK_INT >= 28) setIsStrongBoxBacked(strongBox) }
            .build()
        try {
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, PROVIDER)
                .also { it.initialize(spec) }
                .generateKeyPair()
        } catch (e: StrongBoxUnavailableException) {
            if (strongBox) generateEcKey(strongBox = false) else throw e
        }
    }

    // ── Peer public key decoding ─────────────────────────────────────────────

    companion object {
        private const val PROVIDER  = "AndroidKeyStore"
        const val KEY_ALIAS = "vigia_ble_identity_v1"

        /**
         * Decodes an uncompressed P-256 point (65 bytes: 0x04 || X || Y) into a [PublicKey].
         * Uses a manually-constructed X.509 SubjectPublicKeyInfo wrapper — no BouncyCastle needed.
         *
         *   DER structure (91 bytes total):
         *     SEQUENCE {
         *       SEQUENCE { OID ecPublicKey, OID prime256v1 }
         *       BIT STRING { 0x00, <65-byte uncompressed point> }
         *     }
         */
        fun decodeUncompressedP256PublicKey(raw65: ByteArray): PublicKey {
            require(raw65.size == 65 && raw65[0] == 0x04.toByte()) {
                "Expected uncompressed P-256 point (65 bytes starting with 0x04)"
            }
            // P-256 SubjectPublicKeyInfo header — fixed for secp256r1.
            val header = byteArrayOf(
                0x30, 0x59.toByte(),                                          // SEQUENCE, 89 bytes
                0x30, 0x13,                                                   // SEQUENCE, 19 bytes
                0x06, 0x07, 0x2a, 0x86.toByte(), 0x48, 0xce.toByte(),        // OID 1.2.840.10045.2.1
                0x3d, 0x02, 0x01,
                0x06, 0x08, 0x2a, 0x86.toByte(), 0x48, 0xce.toByte(),        // OID 1.2.840.10045.3.1.7
                0x3d, 0x03, 0x01, 0x07,
                0x03, 0x42, 0x00,                                             // BIT STRING, 66 bytes, 0 unused
            )
            return KeyFactory.getInstance("EC")
                .generatePublic(X509EncodedKeySpec(header + raw65))
        }
    }
}
