package com.adsamcik.temperaturedashboard.decoder.builtins.profile

import com.adsamcik.temperaturedashboard.decoder.api.AdvertisementSnapshot
import com.adsamcik.temperaturedashboard.decoder.api.DecodedField
import com.adsamcik.temperaturedashboard.decoder.api.DecodedValue
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import kotlin.math.abs

class GoveeProfileTest {

    private fun snapshot(
        name: String? = "GVH5104_ABCD",
        manufacturerId: Int = 0xEC88,
        payload: ByteArray = ByteArray(0),
        extraEntries: Map<Int, ByteArray> = emptyMap(),
    ): AdvertisementSnapshot = AdvertisementSnapshot(
        name = name,
        advertisedServiceUuids = emptyList(),
        manufacturerData = mapOf(manufacturerId to payload) + extraEntries,
        serviceData = emptyMap(),
    )

    // ---------------------------------------------------------------- decode

    /**
     * 23.4 °C / 51.2 % / 85 %. Derivation:
     *
     *   value = temp*10000 + humidity*10 = 234000 + 512 = 234512 = 0x03_94_10
     *   no sign bit set; battery 0x55 = 85, error bit clear.
     */
    @Test
    fun `H5104 decodes positive temp humidity and battery`() {
        val payload = byteArrayOf(0x00, 0x00, 0x03, 0x94.toByte(), 0x10, 0x55)

        val fields = GoveeProfile.decodeAdvertisement(snapshot(payload = payload))

        assertApprox(fields.floatValue("Temperature"), 23.4)
        assertApprox(fields.floatValue("Humidity"), 51.2)
        assertThat(fields.intValue("Battery")).isEqualTo(85L)
    }

    /**
     * -5.6 °C / 30.0 % / 25 %. Derivation:
     *
     *   value = 56300 = 0xDB_EC; with negative sign bit → 0x80_DB_EC.
     *   battery 0x19 = 25.
     */
    @Test
    fun `H5104 decodes negative temperature via sign bit`() {
        val payload = byteArrayOf(0x00, 0x00, 0x80.toByte(), 0xDB.toByte(), 0xEC.toByte(), 0x19)

        val fields = GoveeProfile.decodeAdvertisement(snapshot(payload = payload))

        assertApprox(fields.floatValue("Temperature"), -5.6)
        assertApprox(fields.floatValue("Humidity"), 30.0)
        assertThat(fields.intValue("Battery")).isEqualTo(25L)
    }

    @Test
    fun `H5110 with 8-byte payload uses the same head 6 bytes`() {
        // 20.0 °C / 45.5 % / 90 %; value = 200000 + 455 = 200455 = 0x03_0F_07.
        // Trailing 2 bytes are variant-specific noise we ignore.
        val payload = byteArrayOf(
            0x00, 0x00,
            0x03, 0x0F, 0x07, 0x5A,
            0x11, 0x22,
        )

        val fields = GoveeProfile.decodeAdvertisement(
            snapshot(name = "GVH5110_1234", payload = payload),
        )

        assertApprox(fields.floatValue("Temperature"), 20.0)
        assertApprox(fields.floatValue("Humidity"), 45.5)
        assertThat(fields.intValue("Battery")).isEqualTo(90L)
    }

    @Test
    fun `error bit drops temperature and humidity but keeps battery`() {
        // Same 23.4 °C / 51.2 % packet but error bit set → battery still 25, no temp/hum.
        val payload = byteArrayOf(0x00, 0x00, 0x03, 0x94.toByte(), 0x10, 0x99.toByte())

        val fields = GoveeProfile.decodeAdvertisement(snapshot(payload = payload))

        assertThat(fields.any { it.name == "Temperature" }).isFalse()
        assertThat(fields.any { it.name == "Humidity" }).isFalse()
        assertThat(fields.intValue("Battery")).isEqualTo(25L)
    }

    @Test
    fun `out-of-range temperature drops temp and humidity`() {
        // 200.0 °C: value = 2_000_000 = 0x1E_84_80; clearly bogus.
        val payload = byteArrayOf(0x00, 0x00, 0x1E, 0x84.toByte(), 0x80.toByte(), 0x55)

        val fields = GoveeProfile.decodeAdvertisement(snapshot(payload = payload))

        assertThat(fields.any { it.name == "Temperature" }).isFalse()
        assertThat(fields.any { it.name == "Humidity" }).isFalse()
        assertThat(fields.intValue("Battery")).isEqualTo(85L)
    }

    // ---------------------------------------------------------------- match

    @Test
    fun `matches every supported model name`() {
        listOf(
            "GVH5100_1111", "GVH5101_2222", "GVH5102_3333", "GVH5103_4444",
            "GVH5104_AAAA", "GVH5105_BBBB", "GVH5108_CCCC",
            "GVH5110_DDDD", "GVH5174_EEEE", "GVH5177_FFFF", "GV5179_aaaa",
            "H5104 Living Room", // friendly name pattern
        ).forEach { name ->
            assertThat(GoveeProfile.matches(snapshot(name = name))).isTrue()
        }
    }

    @Test
    fun `does not match unrelated devices`() {
        listOf("ihoment", "TP357 ABCD", "WoSensorTH", "Some random name", null).forEach { name ->
            assertThat(GoveeProfile.matches(snapshot(name = name))).isFalse()
        }
    }

    @Test
    fun `decode skips Apple manufacturer ID 76 and uses Govee payload`() {
        val govee = byteArrayOf(0x00, 0x00, 0x03, 0x94.toByte(), 0x10, 0x55)
        val apple = byteArrayOf(0x02, 0x15, 0x00, 0x00) // iBeacon header noise

        val snap = AdvertisementSnapshot(
            name = "GVH5104_X1",
            advertisedServiceUuids = emptyList(),
            manufacturerData = mapOf(0x004C to apple, 0xEC88 to govee),
            serviceData = emptyMap(),
        )
        val fields = GoveeProfile.decodeAdvertisement(snap)

        assertApprox(fields.floatValue("Temperature"), 23.4)
    }

    @Test
    fun `decode rejects payload of unexpected length`() {
        // Govee branch requires 6 or 8 bytes only.
        val tooShort = byteArrayOf(0x00, 0x00, 0x03)
        val odd = byteArrayOf(0x00, 0x00, 0x03, 0x94.toByte(), 0x10) // 5 bytes
        val tooLong = ByteArray(16) { 0x00 }

        assertThat(GoveeProfile.decodeAdvertisement(snapshot(payload = tooShort))).isEmpty()
        assertThat(GoveeProfile.decodeAdvertisement(snapshot(payload = odd))).isEmpty()
        assertThat(GoveeProfile.decodeAdvertisement(snapshot(payload = tooLong))).isEmpty()
    }

    private fun List<DecodedField>.intValue(name: String): Long =
        ((first { it.name == name }.value) as DecodedValue.IntValue).v

    private fun List<DecodedField>.floatValue(name: String): Double =
        ((first { it.name == name }.value) as DecodedValue.FloatValue).v

    private fun assertApprox(actual: Double, expected: Double, tolerance: Double = 1e-4) {
        assertThat(abs(actual - expected)).isLessThan(tolerance)
    }
}
