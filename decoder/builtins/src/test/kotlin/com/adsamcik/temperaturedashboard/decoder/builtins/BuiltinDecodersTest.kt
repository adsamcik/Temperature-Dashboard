package com.adsamcik.temperaturedashboard.decoder.builtins

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class BuiltinDecodersTest {
    private val registry = BuiltinDecoders.newRegistry()

    @Test
    fun `routes 2A19 to BatteryLevelDecoder`() {
        val decoder = registry.decoderFor(null, "2A19")
        assertThat(decoder.id).isEqualTo("sig.battery_level")
    }

    @Test
    fun `routes 2A6E to EssTemperatureDecoder`() {
        val decoder = registry.decoderFor(null, "2A6E")
        assertThat(decoder.id).isEqualTo("sig.ess_temperature")
    }

    @Test
    fun `routes 2A6F to EssHumidityDecoder`() {
        val decoder = registry.decoderFor(null, "2A6F")
        assertThat(decoder.id).isEqualTo("sig.ess_humidity")
    }

    @Test
    fun `routes 2A6D to EssPressureDecoder`() {
        val decoder = registry.decoderFor(null, "2A6D")
        assertThat(decoder.id).isEqualTo("sig.ess_pressure")
    }

    @Test
    fun `routes 2A37 to HeartRateMeasurementDecoder`() {
        val decoder = registry.decoderFor(null, "2A37")
        assertThat(decoder.id).isEqualTo("sig.heart_rate")
    }

    @Test
    fun `routes 2A2B to CurrentTimeDecoder`() {
        val decoder = registry.decoderFor(null, "2A2B")
        assertThat(decoder.id).isEqualTo("sig.current_time")
    }

    @Test
    fun `routes FCD2 service UUID to BTHomeV2Decoder`() {
        val decoder = registry.decoderFor("FCD2", null)
        assertThat(decoder.id).isEqualTo("bthome.v2")
    }

    @Test
    fun `unknown UUID falls back to GenericHexDecoder`() {
        val decoder = registry.decoderFor(null, "DEAD")
        assertThat(decoder.id).isEqualTo("generic.hex")
    }
}
