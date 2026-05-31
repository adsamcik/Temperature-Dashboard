package com.adsamcik.temperaturedashboard.decoder.builtins.util

import kotlin.math.pow

/**
 * IEEE 11073-20601 §8.7.3.2 — 16-bit SFLOAT.
 * Bits [15:12] = 4-bit signed exponent. Bits [11:0] = 12-bit signed mantissa.
 */
fun decodeSfloat(raw: Short): Double {
    val value = raw.toInt() and 0xFFFF
    return when (value) {
        0x07FF -> Double.NaN
        0x0800 -> Double.NaN
        0x07FE -> Double.POSITIVE_INFINITY
        0x0802 -> Double.NEGATIVE_INFINITY
        0x07FD -> Double.NaN
        else -> {
            var mantissa = value and 0x0FFF
            var exponent = (value ushr 12) and 0x0F
            if (mantissa >= 0x0800) mantissa -= 0x1000
            if (exponent >= 0x08) exponent -= 0x10
            mantissa * 10.0.pow(exponent.toDouble())
        }
    }
}

/**
 * IEEE 11073-20601 §8.7.3.3 — 32-bit FLOAT (little-endian).
 * Byte layout: [mantissa LSB, mantissa mid-low, mantissa mid-high, exponent].
 */
fun decodeFloat11073(b0: Byte, b1: Byte, b2: Byte, b3: Byte): Double {
    val rawMantissa = (b0.toInt() and 0xFF) or
        ((b1.toInt() and 0xFF) shl 8) or
        ((b2.toInt() and 0xFF) shl 16)
    val mantissa = if (rawMantissa and 0x800000 != 0) rawMantissa or -0x1000000 else rawMantissa
    val exponent = b3.toInt()
    return mantissa * 10.0.pow(exponent.toDouble())
}
