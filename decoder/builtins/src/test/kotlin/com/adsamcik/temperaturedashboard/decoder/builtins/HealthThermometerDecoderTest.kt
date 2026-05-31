package com.adsamcik.temperaturedashboard.decoder.builtins

import com.adsamcik.temperaturedashboard.decoder.api.DecodeContext
import com.adsamcik.temperaturedashboard.decoder.api.DecodedValue
import com.adsamcik.temperaturedashboard.decoder.builtins.sig.HealthThermometerDecoder
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import kotlin.math.abs

class HealthThermometerDecoderTest {
    private fun assertApprox(actual: Double, expected: Double, tolerance: Double = 1e-6) {
        assertThat(abs(actual - expected)).isLessThan(tolerance)
    }

    @Test
    fun `minimum payload Celsius only - 36_4 degrees`() {
        val payload = byteArrayOf(0x00, 0x6C, 0x01, 0x00, 0xFF.toByte())
        val result = HealthThermometerDecoder.decode(payload, DecodeContext(characteristicUuid = "2A1C"))!!
        assertThat(result.fields).hasSize(1)
        assertApprox((result.fields[0].value as DecodedValue.FloatValue).v, 36.4)
        assertThat(result.fields[0].unit).isEqualTo("°C")
    }

    @Test
    fun `Fahrenheit flag set`() {
        val payload = byteArrayOf(0x01, 0x6C, 0x01, 0x00, 0xFF.toByte())
        val result = HealthThermometerDecoder.decode(payload, DecodeContext(characteristicUuid = "2A1C"))!!
        assertThat(result.fields[0].unit).isEqualTo("°F")
    }

    @Test
    fun `full payload with timestamp and type`() {
        val payload = byteArrayOf(
            0x06,
            0x72,
            0x01,
            0x00,
            0xFF.toByte(),
            0xE8.toByte(),
            0x07,
            0x01,
            0x01,
            0x0C,
            0x00,
            0x00,
            0x04,
        )
        val result = HealthThermometerDecoder.decode(payload, DecodeContext(characteristicUuid = "2A1C"))!!
        assertThat(result.fields.size).isAtLeast(3)
        assertApprox((result.fields[0].value as DecodedValue.FloatValue).v, 37.0)
    }

    @Test
    fun `too short returns null`() {
        val payload = byteArrayOf(0x00, 0x01)
        val context = DecodeContext(characteristicUuid = "2A1C")
        assertThat(HealthThermometerDecoder.decode(payload, context)).isNull()
    }
}
