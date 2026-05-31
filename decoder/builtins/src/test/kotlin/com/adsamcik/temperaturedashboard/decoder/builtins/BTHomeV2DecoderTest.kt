package com.adsamcik.temperaturedashboard.decoder.builtins

import com.adsamcik.temperaturedashboard.decoder.api.DecodeContext
import com.adsamcik.temperaturedashboard.decoder.api.DecodedValue
import com.adsamcik.temperaturedashboard.decoder.builtins.bthome.BTHomeV2Decoder
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import kotlin.math.abs

class BTHomeV2DecoderTest {
    private fun assertApprox(actual: Double, expected: Double, tolerance: Double = 1e-4) {
        assertThat(abs(actual - expected)).isLessThan(tolerance)
    }

    @Test
    fun `bthome spec example - temperature and humidity`() {
        val serviceData = byteArrayOf(0x40, 0x02, 0xC4.toByte(), 0x09, 0x03, 0xBF.toByte(), 0x13)
        val result = BTHomeV2Decoder.decode(serviceData, DecodeContext(serviceUuid = "FCD2"))!!
        assertThat(result.warnings).isEmpty()
        val temperatureField = result.fields.first { it.name == "Temperature" }
        val humidityField = result.fields.first { it.name == "Humidity" }
        assertApprox((temperatureField.value as DecodedValue.FloatValue).v, 25.0)
        assertApprox((humidityField.value as DecodedValue.FloatValue).v, 50.55)
    }

    @Test
    fun `encrypted flag emits warning`() {
        val serviceData = byteArrayOf(0x41, 0x01, 0x64)
        val result = BTHomeV2Decoder.decode(serviceData, DecodeContext(serviceUuid = "FCD2"))!!
        assertThat(result.warnings.any { it.contains("encrypted", ignoreCase = true) }).isTrue()
    }

    @Test
    fun `unknown object id emits warning but keeps prior fields`() {
        val serviceData = byteArrayOf(0x40, 0x01, 0x64, 0xFF.toByte())
        val result = BTHomeV2Decoder.decode(serviceData, DecodeContext(serviceUuid = "FCD2"))!!
        assertThat(result.fields.any { it.name == "Battery" }).isTrue()
        assertThat(result.warnings.any { it.contains("Unknown", ignoreCase = true) }).isTrue()
    }

    @Test
    fun `matches bthome service UUID`() {
        assertThat(BTHomeV2Decoder.matches("FCD2", null)).isTrue()
        assertThat(BTHomeV2Decoder.matches("0000FCD2-0000-1000-8000-00805F9B34FB", null)).isTrue()
        assertThat(BTHomeV2Decoder.matches("2A19", null)).isFalse()
    }

    @Test
    fun `battery object ID 0x01`() {
        val serviceData = byteArrayOf(0x40, 0x01, 0x4D)
        val result = BTHomeV2Decoder.decode(serviceData, DecodeContext(serviceUuid = "FCD2"))!!
        val batteryField = result.fields.first { it.name == "Battery" }
        assertThat((batteryField.value as DecodedValue.IntValue).v).isEqualTo(77L)
    }
}
