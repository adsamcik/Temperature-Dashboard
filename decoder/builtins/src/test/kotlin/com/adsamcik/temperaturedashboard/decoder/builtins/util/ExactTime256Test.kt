package com.adsamcik.temperaturedashboard.decoder.builtins.util

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDateTime

class ExactTime256Test {
    @Test
    fun `decodes 2024-05-30T15_28_00 Friday`() {
        val payload = byteArrayOf(0xE8.toByte(), 0x07, 0x05, 0x1E, 0x0F, 0x1C, 0x00, 0x05, 0x00, 0x00)
        val result = decodeExactTime256(payload)!!
        assertThat(result.dateTime).isEqualTo(LocalDateTime.of(2024, 5, 30, 15, 28, 0))
        assertThat(result.dayOfWeek).isEqualTo(DayOfWeek.FRIDAY)
        assertThat(result.fractions256).isEqualTo(0)
        assertThat(result.adjustReason).isEqualTo(0)
    }

    @Test
    fun `returns null for payload too short`() {
        assertThat(decodeExactTime256(byteArrayOf(0xE8.toByte(), 0x07))).isNull()
    }

    @Test
    fun `returns null for invalid month`() {
        val payload = byteArrayOf(0xE8.toByte(), 0x07, 0x0D, 0x01, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00)
        assertThat(decodeExactTime256(payload)).isNull()
    }

    @Test
    fun `returns null for invalid hour`() {
        val payload = byteArrayOf(0xE8.toByte(), 0x07, 0x01, 0x01, 0x18, 0x00, 0x00, 0x01, 0x00, 0x00)
        assertThat(decodeExactTime256(payload)).isNull()
    }

    @Test
    fun `day-of-week 0 maps to null`() {
        val payload = byteArrayOf(0xE8.toByte(), 0x07, 0x01, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
        val result = decodeExactTime256(payload)!!
        assertThat(result.dayOfWeek).isNull()
    }

    @Test
    fun `fractions256 and adjustReason are captured`() {
        val payload = byteArrayOf(0xE8.toByte(), 0x07, 0x01, 0x01, 0x00, 0x00, 0x00, 0x01, 0x80.toByte(), 0x03)
        val result = decodeExactTime256(payload)!!
        assertThat(result.fractions256).isEqualTo(128)
        assertThat(result.adjustReason).isEqualTo(3)
    }
}
