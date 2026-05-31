package com.adsamcik.temperaturedashboard.decoder.api

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class GenericHexDecoderTest {
    @Test
    fun `empty payload produces empty hex`() {
        val result = GenericHexDecoder.decode(ByteArray(0), DecodeContext())
        assertThat(result.fields).hasSize(1)
        assertThat((result.fields[0].value as DecodedValue.HexValue).v).isEmpty()
    }

    @Test
    fun `encodes bytes to uppercase hex`() {
        val result = GenericHexDecoder.decode(
            byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte()),
            DecodeContext(),
        )
        assertThat((result.fields[0].value as DecodedValue.HexValue).v).isEqualTo("DEADBEEF")
    }

    @Test
    fun `single zero byte`() {
        val result = GenericHexDecoder.decode(byteArrayOf(0x00), DecodeContext())
        assertThat((result.fields[0].value as DecodedValue.HexValue).v).isEqualTo("00")
    }

    @Test
    fun `always matches any UUID`() {
        assertThat(GenericHexDecoder.matches(null, null)).isTrue()
        assertThat(GenericHexDecoder.matches("2A19", "2A6E")).isTrue()
    }
}
