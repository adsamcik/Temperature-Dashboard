package com.adsamcik.temperaturedashboard.decoder.builtins.profile

import com.adsamcik.temperaturedashboard.decoder.api.AdvertisementSnapshot
import com.adsamcik.temperaturedashboard.decoder.api.DecodedField
import com.adsamcik.temperaturedashboard.decoder.api.DecodedValue
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import kotlin.math.abs

class SwitchBotMeterProfileTest {

    private val serviceUuid = "0000FD3D-0000-1000-8000-00805F9B34FB"

    private fun snapshot(name: String? = "WoSensorTH", bytes: ByteArray): AdvertisementSnapshot =
        AdvertisementSnapshot(
            name = name,
            advertisedServiceUuids = emptyList(),
            manufacturerData = emptyMap(),
            serviceData = mapOf(serviceUuid to bytes),
        )

    @Test
    fun `Meter T decodes positive temperature humidity and battery`() {
        // 23.4 °C, 51 % RH, 85 % battery
        // byte 0: 0x54 ('T', not encrypted)
        // byte 2: 0x55 = 85
        // byte 3: 0x04 (decimal .4)
        // byte 4: 0x80 | 23 = 0x97 (sign positive + integer 23)
        // byte 5: 0x33 = 51
        val bytes = byteArrayOf(0x54, 0x00, 0x55, 0x04, 0x97.toByte(), 0x33)

        val fields = SwitchBotMeterProfile.decodeAdvertisement(snapshot(bytes = bytes))

        assertApprox(fields.floatValue("Temperature"), 23.4)
        assertThat(fields.intValue("Humidity")).isEqualTo(51L)
        assertThat(fields.intValue("Battery")).isEqualTo(85L)
    }

    @Test
    fun `Meter Plus decodes the same layout`() {
        // model 'i' = 0x69; 19.7 °C, 42 %, 100 % battery
        val bytes = byteArrayOf(0x69, 0x00, 0x64, 0x07, 0x93.toByte(), 0x2A)

        val fields = SwitchBotMeterProfile.decodeAdvertisement(snapshot(name = null, bytes = bytes))

        assertApprox(fields.floatValue("Temperature"), 19.7)
        assertThat(fields.intValue("Humidity")).isEqualTo(42L)
        assertThat(fields.intValue("Battery")).isEqualTo(100L)
    }

    @Test
    fun `Hub 2 model l also matches`() {
        // model 'l' = 0x6C; 21.1 °C, 60 %, 100 %
        val bytes = byteArrayOf(0x6C, 0x00, 0x64, 0x01, 0x95.toByte(), 0x3C)

        assertThat(SwitchBotMeterProfile.matches(snapshot(bytes = bytes))).isTrue()
        val fields = SwitchBotMeterProfile.decodeAdvertisement(snapshot(bytes = bytes))
        assertApprox(fields.floatValue("Temperature"), 21.1)
        assertThat(fields.intValue("Humidity")).isEqualTo(60L)
    }

    @Test
    fun `negative temperature decodes correctly`() {
        // -2.3 °C: integer 2, decimal 3, sign bit 0 = negative
        // byte 4: 0x02 (sign=0 negative, integer 2)
        val bytes = byteArrayOf(0x54, 0x00, 0x32, 0x03, 0x02, 0x4E)

        val fields = SwitchBotMeterProfile.decodeAdvertisement(snapshot(bytes = bytes))

        assertApprox(fields.floatValue("Temperature"), -2.3)
        assertThat(fields.intValue("Humidity")).isEqualTo(78L)
    }

    @Test
    fun `matches returns false for unknown model byte`() {
        // 'w' (Outdoor Meter) — recognised SwitchBot service UUID but a model
        // this profile doesn't decode yet, so it should decline the match.
        val bytes = byteArrayOf(0x77, 0x00, 0x32, 0x03, 0x02, 0x4E)

        assertThat(SwitchBotMeterProfile.matches(snapshot(bytes = bytes))).isFalse()
    }

    @Test
    fun `matches returns false when service data is too short`() {
        val bytes = byteArrayOf(0x54, 0x00, 0x55)
        assertThat(SwitchBotMeterProfile.matches(snapshot(bytes = bytes))).isFalse()
    }

    @Test
    fun `matches returns false when service UUID is missing`() {
        val noServiceData = AdvertisementSnapshot(
            name = "WoSensorTH",
            advertisedServiceUuids = emptyList(),
            manufacturerData = emptyMap(),
            serviceData = emptyMap(),
        )
        assertThat(SwitchBotMeterProfile.matches(noServiceData)).isFalse()
    }

    @Test
    fun `decode discards out-of-range humidity`() {
        // humidity byte = 0x7F & 200 = 72, no that's in range. Let me make it actually out of range.
        // Wait: 0x7F mask gives 0..127, so values 101..127 are out of range. Use 0x65 = 101.
        val bytes = byteArrayOf(0x54, 0x00, 0x55, 0x04, 0x97.toByte(), 0x65)
        assertThat(SwitchBotMeterProfile.decodeAdvertisement(snapshot(bytes = bytes))).isEmpty()
    }

    private fun List<DecodedField>.intValue(name: String): Long =
        ((first { it.name == name }.value) as DecodedValue.IntValue).v

    private fun List<DecodedField>.floatValue(name: String): Double =
        ((first { it.name == name }.value) as DecodedValue.FloatValue).v

    private fun assertApprox(actual: Double, expected: Double, tolerance: Double = 1e-4) {
        assertThat(abs(actual - expected)).isLessThan(tolerance)
    }
}
