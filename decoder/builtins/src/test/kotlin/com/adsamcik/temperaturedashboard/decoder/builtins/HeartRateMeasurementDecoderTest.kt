package com.adsamcik.temperaturedashboard.decoder.builtins

import com.adsamcik.temperaturedashboard.decoder.api.DecodeContext
import com.adsamcik.temperaturedashboard.decoder.api.DecodedValue
import com.adsamcik.temperaturedashboard.decoder.builtins.sig.HeartRateMeasurementDecoder
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class HeartRateMeasurementDecoderTest {
    @Test
    fun `minimum payload uint8 HR = 76 BPM`() {
        val result = HeartRateMeasurementDecoder.decode(
            byteArrayOf(0x00, 0x4C),
            DecodeContext(characteristicUuid = "2A37"),
        )!!
        assertThat(result.fields).hasSize(1)
        assertThat((result.fields[0].value as DecodedValue.IntValue).v).isEqualTo(76L)
        assertThat(result.fields[0].unit).isEqualTo("BPM")
    }

    @Test
    fun `flag bit 0 set - uint16 HR = 76 BPM`() {
        val result = HeartRateMeasurementDecoder.decode(
            byteArrayOf(0x01, 0x4C, 0x00),
            DecodeContext(characteristicUuid = "2A37"),
        )!!
        assertThat((result.fields[0].value as DecodedValue.IntValue).v).isEqualTo(76L)
    }

    @Test
    fun `with energy expended flag`() {
        val result = HeartRateMeasurementDecoder.decode(
            byteArrayOf(0x08, 0x50, 0x64, 0x00),
            DecodeContext(characteristicUuid = "2A37"),
        )!!
        assertThat(result.fields.size).isEqualTo(2)
        assertThat(result.fields[0].name).isEqualTo("Heart Rate")
        assertThat(result.fields[1].name).isEqualTo("Energy Expended")
        assertThat((result.fields[1].value as DecodedValue.IntValue).v).isEqualTo(100L)
    }

    @Test
    fun `with RR intervals`() {
        val result = HeartRateMeasurementDecoder.decode(
            byteArrayOf(0x10, 0x4B, 0x00, 0x04),
            DecodeContext(characteristicUuid = "2A37"),
        )!!
        assertThat(result.fields.size).isEqualTo(2)
        assertThat(result.fields[1].name).startsWith("RR Interval")
    }
}
