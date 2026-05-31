package com.adsamcik.temperaturedashboard.decoder.builtins

import com.adsamcik.temperaturedashboard.decoder.api.DecodeContext
import com.adsamcik.temperaturedashboard.decoder.api.DecodedValue
import com.adsamcik.temperaturedashboard.decoder.builtins.sig.BatteryLevelDecoder
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class BatteryLevelDecoderTest {
    @Test
    fun `0x4D decodes to 77 percent`() {
        val result = BatteryLevelDecoder.decode(byteArrayOf(0x4D), DecodeContext(characteristicUuid = "2A19"))!!
        assertThat((result.fields[0].value as DecodedValue.IntValue).v).isEqualTo(77L)
        assertThat(result.fields[0].unit).isEqualTo("%")
        assertThat(result.warnings).isEmpty()
    }

    @Test
    fun `0x00 decodes to 0 percent`() {
        val result = BatteryLevelDecoder.decode(byteArrayOf(0x00), DecodeContext(characteristicUuid = "2A19"))!!
        assertThat((result.fields[0].value as DecodedValue.IntValue).v).isEqualTo(0L)
    }

    @Test
    fun `0x64 decodes to 100 percent`() {
        val result = BatteryLevelDecoder.decode(byteArrayOf(0x64), DecodeContext(characteristicUuid = "2A19"))!!
        assertThat((result.fields[0].value as DecodedValue.IntValue).v).isEqualTo(100L)
        assertThat(result.warnings).isEmpty()
    }

    @Test
    fun `0xFF is out-of-spec with warning`() {
        val result = BatteryLevelDecoder.decode(
            byteArrayOf(0xFF.toByte()),
            DecodeContext(characteristicUuid = "2A19"),
        )!!
        assertThat((result.fields[0].value as DecodedValue.IntValue).v).isEqualTo(255L)
        assertThat(result.warnings).isNotEmpty()
    }

    @Test
    fun `empty payload returns null`() {
        assertThat(BatteryLevelDecoder.decode(ByteArray(0), DecodeContext(characteristicUuid = "2A19"))).isNull()
    }

    @Test
    fun `matches characteristic UUID 2A19`() {
        assertThat(BatteryLevelDecoder.matches(null, "2A19")).isTrue()
        assertThat(BatteryLevelDecoder.matches(null, "2A6E")).isFalse()
    }
}
