package com.adsamcik.temperaturedashboard.decoder.builtins.util

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import kotlin.math.abs

class Ieee11073Test {
    private fun assertApprox(actual: Double, expected: Double, tolerance: Double = 1e-9) {
        assertThat(abs(actual - expected)).isLessThan(tolerance)
    }

    @Test
    fun `sfloat 120_0 = mantissa 1200 exp -1 = 0xF4B0`() {
        assertApprox(decodeSfloat(0xF4B0.toShort()), 120.0, 1e-6)
    }

    @Test
    fun `sfloat 36_4 = mantissa 364 exp -1 = 0xF16C`() {
        assertApprox(decodeSfloat(0xF16C.toShort()), 36.4, 1e-6)
    }

    @Test
    fun `sfloat 0_0`() {
        assertApprox(decodeSfloat(0x0000.toShort()), 0.0)
    }

    @Test
    fun `sfloat positive exp`() {
        assertApprox(decodeSfloat(0x100A.toShort()), 100.0, 1e-6)
    }

    @Test
    fun `sfloat negative mantissa`() {
        assertApprox(decodeSfloat(0x0FF6.toShort()), -10.0, 1e-6)
    }

    @Test
    fun `sfloat NaN sentinel 0x07FF`() {
        assertThat(decodeSfloat(0x07FF.toShort()).isNaN()).isTrue()
    }

    @Test
    fun `sfloat NRes sentinel 0x0800`() {
        assertThat(decodeSfloat(0x0800.toShort()).isNaN()).isTrue()
    }

    @Test
    fun `sfloat +Infinity sentinel 0x07FE`() {
        assertThat(decodeSfloat(0x07FE.toShort())).isEqualTo(Double.POSITIVE_INFINITY)
    }

    @Test
    fun `sfloat -Infinity sentinel 0x0802`() {
        assertThat(decodeSfloat(0x0802.toShort())).isEqualTo(Double.NEGATIVE_INFINITY)
    }

    @Test
    fun `sfloat reserved sentinel 0x07FD`() {
        assertThat(decodeSfloat(0x07FD.toShort()).isNaN()).isTrue()
    }

    @Test
    fun `sfloat negative exponent largest magnitude`() {
        assertApprox(decodeSfloat(0x87FC.toShort()), 2044 * 1e-8, 1e-12)
    }

    @Test
    fun `sfloat max positive exponent`() {
        assertApprox(decodeSfloat(0x7001.toShort()), 1e7, 1.0)
    }

    @Test
    fun `float11073 36_4 celsius`() {
        val result = decodeFloat11073(0x6C, 0x01, 0x00, 0xFF.toByte())
        assertApprox(result, 36.4, 1e-6)
    }

    @Test
    fun `float11073 zero`() {
        val result = decodeFloat11073(0x00, 0x00, 0x00, 0x00)
        assertApprox(result, 0.0)
    }
}
