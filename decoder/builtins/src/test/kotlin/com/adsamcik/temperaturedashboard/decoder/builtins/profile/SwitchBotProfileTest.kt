package com.adsamcik.temperaturedashboard.decoder.builtins.profile

import com.adsamcik.temperaturedashboard.decoder.api.AdvertisementSnapshot
import com.adsamcik.temperaturedashboard.decoder.api.DecodedField
import com.adsamcik.temperaturedashboard.decoder.api.DecodedValue
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import kotlin.math.abs

class SwitchBotProfileTest {

    private val serviceUuid = "0000FD3D-0000-1000-8000-00805F9B34FB"

    private fun snapshot(
        name: String? = "WoSensorTH",
        serviceBytes: ByteArray? = null,
        mfrBytes: ByteArray? = null,
    ): AdvertisementSnapshot = AdvertisementSnapshot(
        name = name,
        advertisedServiceUuids = emptyList(),
        manufacturerData = mfrBytes?.let { mapOf(0x0969 to it) } ?: emptyMap(),
        serviceData = serviceBytes?.let { mapOf(serviceUuid to it) } ?: emptyMap(),
    )

    // ----- Indoor Meter family (T / i / "Meter" / "Meter Plus")

    @Test
    fun `Meter T decodes positive temperature humidity and battery from service data`() {
        // 23.4 °C, 51 % RH, 85 % battery — temp block in service_data[3..5]
        val service = byteArrayOf(0x54, 0x00, 0x55, 0x04, 0x97.toByte(), 0x33)

        val fields = SwitchBotProfile.decodeAdvertisement(snapshot(serviceBytes = service))

        assertApprox(fields.floatValue("Temperature"), 23.4)
        assertThat(fields.intValue("Humidity")).isEqualTo(51L)
        assertThat(fields.intValue("Battery")).isEqualTo(85L)
    }

    @Test
    fun `Meter Plus i decodes the same layout`() {
        val service = byteArrayOf(0x69, 0x00, 0x64, 0x07, 0x93.toByte(), 0x2A)

        val fields = SwitchBotProfile.decodeAdvertisement(snapshot(serviceBytes = service))

        assertApprox(fields.floatValue("Temperature"), 19.7)
        assertThat(fields.intValue("Humidity")).isEqualTo(42L)
        assertThat(fields.intValue("Battery")).isEqualTo(100L)
    }

    @Test
    fun `negative temperature uses sign bit 0`() {
        // -2.3 °C
        val service = byteArrayOf(0x54, 0x00, 0x32, 0x03, 0x02, 0x4E)

        val fields = SwitchBotProfile.decodeAdvertisement(snapshot(serviceBytes = service))

        assertApprox(fields.floatValue("Temperature"), -2.3)
    }

    // ----- Indoor/Outdoor Thermo-Hygrometer (W3400010, model 'w')

    /**
     * Real-world captured packet from a SwitchBot Indoor/Outdoor
     * Thermo-Hygrometer (W3400010), documented at:
     * https://community.home-assistant.io/t/switchbot-outdoor-thermometer-hygrometer/565464
     *
     * Service data (after UUID stripped): 77 00 64        → model 'w', battery 100 %
     * Manufacturer data 0x0969 (after company id stripped):
     *   f9 66 6b 95 9c ea  be 03 07 97 39 00
     *   └─── MAC ────────┘ └─┘ └─ temp/hum ─┘ └ trailer
     * Decoded: 23.7 °C, 57 %, 100 %.
     */
    @Test
    fun `Outdoor Meter w decodes from real-world captured packet`() {
        val service = byteArrayOf(0x77, 0x00, 0x64)
        val mfr = byteArrayOf(
            0xF9.toByte(), 0x66, 0x6B, 0x95.toByte(), 0x9C.toByte(), 0xEA.toByte(),
            0xBE.toByte(), 0x03,
            0x07, 0x97.toByte(), 0x39,
            0x00,
        )

        val snap = snapshot(name = "WoTHP", serviceBytes = service, mfrBytes = mfr)
        assertThat(SwitchBotProfile.matches(snap)).isTrue()

        val fields = SwitchBotProfile.decodeAdvertisement(snap)
        assertApprox(fields.floatValue("Temperature"), 23.7)
        assertThat(fields.intValue("Humidity")).isEqualTo(57L)
        assertThat(fields.intValue("Battery")).isEqualTo(100L)
    }

    @Test
    fun `Outdoor Meter with negative temperature`() {
        // Construct: -5.6 °C, 88 %, 75 % battery
        // temp_decimal = 6 → byte 8 = 0x06
        // temp_int = 5, sign = negative → byte 9 = 0x05
        // humidity = 88 → byte 10 = 0x58
        val mfr = byteArrayOf(
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // MAC
            0x00, 0x00,                          // unknown
            0x06, 0x05, 0x58,                    // temp dec / int+sign / humidity
            0x00,                                // trailer
        )
        val service = byteArrayOf(0x77, 0x00, 0x4B) // battery 75

        val fields = SwitchBotProfile.decodeAdvertisement(
            snapshot(serviceBytes = service, mfrBytes = mfr),
        )

        assertApprox(fields.floatValue("Temperature"), -5.6)
        assertThat(fields.intValue("Humidity")).isEqualTo(88L)
        assertThat(fields.intValue("Battery")).isEqualTo(75L)
    }

    @Test
    fun `manufacturer data is preferred over service data when both contain temp`() {
        // Service data says 20.0 °C / 50 %; mfr data says 30.5 °C / 60 %.
        // The Outdoor-Meter codepath wins because mfr is present and >= 11 bytes.
        val service = byteArrayOf(0x77, 0x00, 0x64, 0x00, 0x94.toByte(), 0x32)
        val mfr = byteArrayOf(
            0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(), 0xDD.toByte(), 0xEE.toByte(), 0xFF.toByte(),
            0x00, 0x00,
            0x05, 0x9E.toByte(), 0x3C,
            0x00,
        )

        val fields = SwitchBotProfile.decodeAdvertisement(
            snapshot(serviceBytes = service, mfrBytes = mfr),
        )

        assertApprox(fields.floatValue("Temperature"), 30.5)
        assertThat(fields.intValue("Humidity")).isEqualTo(60L)
    }

    // ----- Match guards

    @Test
    fun `matches returns true for every recognised model byte`() {
        // Any 3-byte service data starting with a known model byte counts as a match.
        listOf('T', 't', 'i', 'I', 'w', 'W', '4', '5').forEach { c ->
            val service = byteArrayOf(c.code.toByte(), 0x00, 0x64)
            assertThat(SwitchBotProfile.matches(snapshot(serviceBytes = service)))
                .isEqualTo(true)
        }
    }

    @Test
    fun `matches returns false for unrelated SwitchBot models`() {
        // 'd' = Contact Sensor, 'g' = Plug Mini — same service UUID, not our concern.
        listOf('d', 'D', 'H', 'g', 's', '{', 'c').forEach { c ->
            val service = byteArrayOf(c.code.toByte(), 0x00, 0x64)
            assertThat(SwitchBotProfile.matches(snapshot(serviceBytes = service)))
                .isEqualTo(false)
        }
    }

    @Test
    fun `matches returns false when service UUID is missing`() {
        assertThat(SwitchBotProfile.matches(snapshot(serviceBytes = null))).isFalse()
    }

    @Test
    fun `matches accepts short service data so long as model byte is present`() {
        // Outdoor Meter only sends 3 bytes of service data — must still match.
        val service = byteArrayOf(0x77)
        assertThat(SwitchBotProfile.matches(snapshot(serviceBytes = service))).isTrue()
    }

    @Test
    fun `decode rejects out-of-range humidity`() {
        // humidity byte 0x65 = 101 — outside 0..100
        val service = byteArrayOf(0x54, 0x00, 0x55, 0x04, 0x97.toByte(), 0x65)

        // Battery is still valid, so we still emit a Battery field; but no temp/humidity.
        val fields = SwitchBotProfile.decodeAdvertisement(snapshot(serviceBytes = service))
        assertThat(fields.any { it.name == "Temperature" }).isFalse()
        assertThat(fields.any { it.name == "Humidity" }).isFalse()
        assertThat(fields.intValue("Battery")).isEqualTo(85L)
    }

    @Test
    fun `decode rejects the all-zero no-data marker`() {
        // temp = 0, humidity = 0, battery = 0 → pySwitchbot drops these, we should too.
        val service = byteArrayOf(0x54, 0x00, 0x00, 0x00, 0x80.toByte(), 0x00)
        val fields = SwitchBotProfile.decodeAdvertisement(snapshot(serviceBytes = service))
        assertThat(fields.any { it.name == "Temperature" }).isFalse()
    }

    private fun List<DecodedField>.intValue(name: String): Long =
        ((first { it.name == name }.value) as DecodedValue.IntValue).v

    private fun List<DecodedField>.floatValue(name: String): Double =
        ((first { it.name == name }.value) as DecodedValue.FloatValue).v

    private fun assertApprox(actual: Double, expected: Double, tolerance: Double = 1e-4) {
        assertThat(abs(actual - expected)).isLessThan(tolerance)
    }
}
