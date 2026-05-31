package com.adsamcik.temperaturedashboard.decoder.api

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class DecoderRegistryTest {
    private val mockBattery = object : Decoder {
        override val id = "mock.battery"
        override val displayName = "Mock Battery"

        override fun matches(serviceUuid: String?, characteristicUuid: String?) =
            characteristicUuid?.uppercase() == "2A19"

        override fun decode(payload: ByteArray, context: DecodeContext) =
            DecodeResult(
                id,
                listOf(DecodedField("Battery Level", DecodedValue.IntValue(payload[0].toLong()), "%")),
            )
    }

    private val mockTemp = object : Decoder {
        override val id = "mock.temp"
        override val displayName = "Mock Temp"

        override fun matches(serviceUuid: String?, characteristicUuid: String?) =
            characteristicUuid?.uppercase() == "2A6E"

        override fun decode(payload: ByteArray, context: DecodeContext) =
            DecodeResult(
                id,
                listOf(DecodedField("Temp", DecodedValue.FloatValue(0.0), "°C")),
            )
    }

    private val registry = DecoderRegistry(listOf(mockBattery, mockTemp))

    @Test
    fun `routes to battery decoder by UUID`() {
        val decoder = registry.decoderFor(null, "2A19")
        assertThat(decoder.id).isEqualTo("mock.battery")
    }

    @Test
    fun `routes to temperature decoder by UUID`() {
        val decoder = registry.decoderFor(null, "2A6E")
        assertThat(decoder.id).isEqualTo("mock.temp")
    }

    @Test
    fun `falls back to GenericHexDecoder for unknown UUID`() {
        val decoder = registry.decoderFor(null, "FFFF")
        assertThat(decoder.id).isEqualTo("generic.hex")
    }

    @Test
    fun `falls back to GenericHexDecoder for null UUIDs`() {
        val decoder = registry.decoderFor(null, null)
        assertThat(decoder.id).isEqualTo("generic.hex")
    }

    @Test
    fun `decode uses fallback if decoder returns null`() {
        val nullDecoder = object : Decoder {
            override val id = "null.decoder"
            override val displayName = "Null"

            override fun matches(serviceUuid: String?, characteristicUuid: String?) = true

            override fun decode(payload: ByteArray, context: DecodeContext): DecodeResult? = null
        }

        val reg = DecoderRegistry(listOf(nullDecoder))
        val result = reg.decode(byteArrayOf(0xFF.toByte()), DecodeContext())
        assertThat(result.decoderId).isEqualTo("generic.hex")
    }
}
