package com.adsamcik.temperaturedashboard.decoder.builtins.profile

import com.adsamcik.temperaturedashboard.decoder.api.AdvertisementSnapshot
import com.adsamcik.temperaturedashboard.decoder.api.DecodedField
import com.adsamcik.temperaturedashboard.decoder.api.DecodedValue
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import kotlin.math.abs

class ThermoProTP35xProfileTest {
    @Test
    fun `passive advertisement keeps documented low-bit battery mapping`() {
        val fields = ThermoProTP35xProfile.decodeAdvertisement(
            AdvertisementSnapshot(
                name = "TP357 (6ECA)",
                advertisedServiceUuids = emptyList(),
                // Reconstructed bytes become C2 F1 00 1D 02 2C:
                // 24.1 C, 29% RH, battery code 2 -> full.
                manufacturerData = mapOf(0xF1C2 to byteArrayOf(0x00, 0x1D, 0x02, 0x2C)),
            ),
        )

        assertApprox(fields.floatValue("Temperature"), 24.1)
        assertThat(fields.intValue("Humidity")).isEqualTo(29L)
        assertThat(fields.intValue("Battery")).isEqualTo(100L)
    }

    @Test
    fun `current GATT notification maps observed status byte 0x2C to medium battery`() {
        val packet = byteArrayOf(
            0xC2.toByte(),
            0x00,
            0x00,
            0x07,
            0x01,
            0x25,
            0x2C,
        )

        val fields = ThermoProTP35xProfile.parseActionResponse("tp35x.current", packet)

        assertApprox(fields.floatValue("Temperature"), 26.3)
        assertThat(fields.intValue("Humidity")).isEqualTo(37L)
        assertThat(fields.intValue("Battery")).isEqualTo(50L)
    }

    @Test
    fun `current GATT notification may be prefixed before C2 marker`() {
        val prefix = "SIMPLE_SERVER_SEND_N".encodeToByteArray()
        val c2Packet = byteArrayOf(
            0xC2.toByte(),
            0x00,
            0x00,
            0x07,
            0x01,
            0x25,
            0x2C,
        )

        val fields = ThermoProTP35xProfile.parseActionResponse(
            actionId = "tp35x.current",
            packet = prefix + c2Packet,
        )

        assertApprox(fields.floatValue("Temperature"), 26.3)
        assertThat(fields.intValue("Humidity")).isEqualTo(37L)
        assertThat(fields.intValue("Battery")).isEqualTo(50L)
    }

    private fun List<DecodedField>.intValue(name: String): Long =
        ((first { it.name == name }.value) as DecodedValue.IntValue).v

    private fun List<DecodedField>.floatValue(name: String): Double =
        ((first { it.name == name }.value) as DecodedValue.FloatValue).v

    private fun assertApprox(actual: Double, expected: Double, tolerance: Double = 1e-4) {
        assertThat(abs(actual - expected)).isLessThan(tolerance)
    }
}
