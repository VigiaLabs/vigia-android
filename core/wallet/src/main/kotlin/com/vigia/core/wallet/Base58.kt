package com.vigia.core.wallet

internal object Base58 {

    private val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz".toCharArray()

    fun encode(input: ByteArray): String {
        if (input.isEmpty()) return ""

        var leadingZeros = 0
        for (b in input) { if (b == 0.toByte()) leadingZeros++ else break }

        val encoded = IntArray(input.size * 2)
        var outputLen = 1

        for (b in input) {
            var carry = b.toInt() and 0xFF
            for (j in 0 until outputLen) {
                carry += encoded[j] * 256
                encoded[j] = carry % 58
                carry /= 58
            }
            while (carry > 0) {
                encoded[outputLen++] = carry % 58
                carry /= 58
            }
        }

        val sb = StringBuilder()
        repeat(leadingZeros) { sb.append('1') }

        // Find the most significant non-zero digit; skip high-order zeros.
        var top = outputLen - 1
        while (top > 0 && encoded[top] == 0) top--
        // Only emit digits if the numeric value is non-zero.
        if (encoded[top] != 0) {
            for (i in top downTo 0) sb.append(ALPHABET[encoded[i]])
        }
        return sb.toString()
    }
}
