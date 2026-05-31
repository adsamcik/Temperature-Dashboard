package com.adsamcik.temperaturedashboard.decoder.builtins

import com.adsamcik.temperaturedashboard.decoder.api.DecodeContext
import com.adsamcik.temperaturedashboard.decoder.api.DecodedValue
import com.adsamcik.temperaturedashboard.decoder.builtins.sig.EssTemperatureDecoder
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import kotlin.math.abs

class EssTemperatureDecoderTest {
    private fun assertApprox(actual: Double, expected: Double, tolerance: Double = 1e-9) {
        assertThat(abs(actual - expected)).isLessThan(tolerance)
    }

    @Test
    fun `0x94 0x09 decodes to 24_52 celsius`() {
        val result = EssTemperatureDecoder.decode(
            byteArrayOf(0x94.toByte(), 0x09),
            DecodeContext(characteristicUuid = "2A6E"),
        )!!
        assertApprox((result.fields[0].value as DecodedValue.FloatValue).v, 24.52, 1e-6)
        assertThat(result.fields[0].unit).isEqualTo("°C")
    }

    @Test
    fun `0x00 0x80 sentinel returns unknown`() {
        val result = EssTemperatureDecoder.decode(
            byteArrayOf(0x00, 0x80.toByte()),
            DecodeContext(characteristicUuid = "2A6E"),
        )!!
        assertThat(result.fields[0].value).isInstanceOf(DecodedValue.StringValue::class.java)
        assertThat(result.warnings).isNotEmpty()
    }

    @Test
    fun `0xFE 0xFF decodes to -0_02 celsius`() {
        val result = EssTemperatureDecoder.decode(
            byteArrayOf(0xFE.toByte(), 0xFF.toByte()),
            DecodeContext(characteristicUuid = "2A6E"),
        )!!
        assertApprox((result.fields[0].value as DecodedValue.FloatValue).v, -0.02, 1e-6)
    }

    @Test
    fun `too short payload returns null`() {
        assertThat(EssTemperatureDecoder.decode(byteArrayOf(0x01), DecodeContext(characteristicUuid = "2A6E"))).isNull()
    }
}
