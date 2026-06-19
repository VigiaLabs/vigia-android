package com.vigia.core.sensor.ble

import com.vigia.core.sensor.keystore.KeystoreManager
import java.io.ByteArrayOutputStream
import java.security.PublicKey
import java.security.Signature
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Pure-JVM utilities for the ECDH handshake (design spec §4.2).
 *
 * All operations here use only standard Java crypto — no external libraries.
 * [KeystoreManager] handles operations that touch the hardware-backed private key.
 */
internal object EcdhHandshake {

    private const val HKDF_INFO = "vigia-ble-v1"

    /**
     * HKDF-SHA256 (RFC 5869) extract-then-expand.
     *
     * @param ikm   Input key material — raw ECDH shared secret (32 bytes).
     * @param salt  HKDF salt — concatenation of both nonces (64 bytes).
     * @param info  Context string (always [HKDF_INFO] = "vigia-ble-v1").
     * @param length Output key length in bytes (default 32).
     */
    fun hkdfSha256(
        ikm: ByteArray,
        salt: ByteArray = ByteArray(32),
        info: ByteArray = HKDF_INFO.toByteArray(),
        length: Int = 32,
    ): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")

        // Extract: PRK = HMAC-SHA256(salt, IKM)
        mac.init(SecretKeySpec(salt, "HmacSHA256"))
        val prk = mac.doFinal(ikm)

        // Expand: T(i) = HMAC-SHA256(PRK, T(i-1) || info || i)
        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        val out = ByteArrayOutputStream()
        var t = byteArrayOf()
        var counter = 1
        while (out.size() < length) {
            mac.update(t)
            mac.update(info)
            mac.update(counter.toByte())
            t = mac.doFinal()
            out.write(t)
            counter++
        }
        return out.toByteArray().copyOf(length)
    }

    /**
     * Verifies an ECDSA-SHA256 signature over [data] using [publicKeyBytes] (uncompressed P-256, 65 bytes).
     * Returns false on any verification failure or crypto exception — never throws.
     */
    fun verifyEcdsaP256(
        publicKeyBytes: ByteArray,
        data: ByteArray,
        signature: ByteArray,
    ): Boolean = runCatching {
        val pubKey: PublicKey = KeystoreManager.decodeUncompressedP256PublicKey(publicKeyBytes)
        Signature.getInstance("SHA256withECDSA").run {
            initVerify(pubKey)
            update(data)
            verify(signature)
        }
    }.getOrDefault(false)

    /**
     * Computes HMAC-SHA256([data]) using [key].
     * Used to verify the Pi's CONFIRM message:
     *   HMAC(session_key, "VIGIA-CONFIRM" || nonce_pi || nonce_phone)
     */
    fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    /** Label used by the Pi when computing its CONFIRM HMAC. Must match ble_gatt_node.cpp. */
    val CONFIRM_LABEL: ByteArray = "VIGIA-CONFIRM".toByteArray()
}
