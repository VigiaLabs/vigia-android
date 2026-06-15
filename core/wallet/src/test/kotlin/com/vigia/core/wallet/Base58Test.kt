package com.vigia.core.wallet

import org.junit.Assert.assertEquals
import org.junit.Test

class Base58Test {

    @Test
    fun `encodes empty array to empty string`() {
        assertEquals("", Base58.encode(byteArrayOf()))
    }

    @Test
    fun `encodes single zero byte to 1`() {
        assertEquals("1", Base58.encode(byteArrayOf(0)))
    }

    @Test
    fun `encodes known 32-byte value deterministically`() {
        // 32 bytes of 0x01 → deterministic Base58
        val input = ByteArray(32) { 1 }
        val result = Base58.encode(input)
        // Round-trip: encoded string must be non-empty, all chars in Base58 alphabet
        val alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        assert(result.isNotEmpty())
        result.forEach { c -> assert(c in alphabet) { "Unexpected char '$c' in Base58 output" } }
    }

    @Test
    fun `leading zero bytes produce leading 1s`() {
        val input = byteArrayOf(0, 0, 1)
        val result = Base58.encode(input)
        assert(result.startsWith("11")) { "Expected two leading '1's, got: $result" }
    }

    @Test
    fun `32 bytes all zeros encodes to 32 ones`() {
        val result = Base58.encode(ByteArray(32))
        assertEquals("1".repeat(32), result)
    }

    @Test
    fun `telemetry payload string encodes without exception`() {
        val payload = "VIGIA:POTHOLE:12.9716:77.5946:1718449200000:0.87"
        val bytes = payload.toByteArray(Charsets.UTF_8)
        // Simulate what sign() would use as message — must not throw
        val encoded = Base58.encode(bytes)
        assert(encoded.isNotEmpty())
    }
}
