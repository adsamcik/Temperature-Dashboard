package com.adsamcik.temperaturedashboard

import com.adsamcik.temperaturedashboard.networking.Tp357AdvertisementParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class Tp357AdvertisementParserTest {

    @Test
    fun parseRawAdvertisement_validHundredthsData_returnsReading() {
        val raw = createRawPacket(tempRaw = 2350, humidity = 56, batteryRaw = 2)

        val reading = Tp357AdvertisementParser.parseRawAdvertisement(raw, "AA:BB", -45)

        assertNotNull(reading)
        assertEquals(23.5, reading!!.temperature, 0.0001)
        assertEquals(56.0, reading.humidity, 0.0001)
        assertEquals(100, reading.batteryPercent)
        assertEquals("AA:BB", reading.address)
        assertEquals(-45, reading.rssi)
    }

    @Test
    fun parseRawAdvertisement_lowTempRaw_parsesViaHundredths() {
        // Note: tempRaw=235 → 235/100.0 = 2.35°C (in range [-30,70]), so hundredths path is taken.
        // The tenths fallback path is unreachable with current constants because
        // the hundredths range [-3000,7000] fully contains the tenths range [-300,700].
        val raw = createRawPacket(tempRaw = 235, humidity = 42, batteryRaw = 1)

        val reading = Tp357AdvertisementParser.parseRawAdvertisement(raw, "CC:DD", -50)

        assertNotNull(reading)
        assertEquals(2.35, reading!!.temperature, 0.0001)
        assertEquals(42.0, reading.humidity, 0.0001)
    }

    @Test
    fun parseRawAdvertisement_tooShortData_returnsNull() {
        val reading = Tp357AdvertisementParser.parseRawAdvertisement(byteArrayOf(1, 2, 3, 4), "AA:BB", -60)

        assertNull(reading)
    }

    @Test
    fun parseRawAdvertisement_temperatureOutOfRange_returnsNull() {
        val raw = createRawPacket(tempRaw = 9000, humidity = 50, batteryRaw = 1)

        val reading = Tp357AdvertisementParser.parseRawAdvertisement(raw, "AA:BB", -60)

        assertNull(reading)
    }

    @Test
    fun parseRawAdvertisement_humidityGreaterThan100_returnsNull() {
        val raw = createRawPacket(tempRaw = 2300, humidity = 101, batteryRaw = 1)

        val reading = Tp357AdvertisementParser.parseRawAdvertisement(raw, "AA:BB", -60)

        assertNull(reading)
    }

    @Test
    fun parseRawAdvertisement_validChecksum_acceptsPacket() {
        val raw = createRawPacket(tempRaw = 2150, humidity = 55, batteryRaw = 2, includeChecksum = true)

        val reading = Tp357AdvertisementParser.parseRawAdvertisement(raw, "AA:BB", -60)

        assertNotNull(reading)
        assertEquals(21.5, reading!!.temperature, 0.0001)
        assertEquals(55.0, reading.humidity, 0.0001)
        assertEquals(100, reading.batteryPercent)
    }

    @Test
    fun parseRawAdvertisement_invalidChecksum_rejectsPacket() {
        val validRaw = createRawPacket(tempRaw = 2150, humidity = 55, batteryRaw = 2, includeChecksum = true)
        val invalidRaw = validRaw.copyOf().apply { this[lastIndex] = (this[lastIndex] + 1).toByte() }

        val reading = Tp357AdvertisementParser.parseRawAdvertisement(invalidRaw, "AA:BB", -60)

        assertNull(reading)
    }

    @Test
    fun parseRawAdvertisement_batteryPercentClampsToRange() {
        val lowBattery = createRawPacket(tempRaw = 2200, humidity = 50, batteryRaw = 0)
        val highBattery = createRawPacket(tempRaw = 2200, humidity = 50, batteryRaw = 255)

        val lowReading = Tp357AdvertisementParser.parseRawAdvertisement(lowBattery, "AA:BB", -60)
        val highReading = Tp357AdvertisementParser.parseRawAdvertisement(highBattery, "AA:BB", -60)

        assertEquals(0, lowReading!!.batteryPercent)
        assertEquals(100, highReading!!.batteryPercent)
    }

    @Test
    fun toTemperatureHumidityData_validReading_convertsFields() {
        val reading = Tp357AdvertisementParser.AdvertisementReading(
            address = "AA:BB",
            temperature = 21.75,
            humidity = 48.0,
            batteryPercent = 80,
            rssi = -48,
            timestamp = 123456L
        )

        val converted = Tp357AdvertisementParser.run { reading.toTemperatureHumidityData() }

        assertEquals(21.75, converted.temperature, 0.0001)
        assertEquals(48.0, converted.humidity, 0.0001)
        assertEquals(123456L, converted.timestamp)
    }

    @Test
    fun parseRawAdvertisement_humidity100_isAccepted() {
        val raw = createRawPacket(tempRaw = 2200, humidity = 100, batteryRaw = 1)

        val reading = Tp357AdvertisementParser.parseRawAdvertisement(raw, "AA:BB", -60)

        assertNotNull(reading)
        assertEquals(100.0, reading!!.humidity, 0.0001)
    }

    private fun createRawPacket(
        tempRaw: Int,
        humidity: Int,
        batteryRaw: Int,
        includeChecksum: Boolean = false
    ): ByteArray {
        val base = byteArrayOf(
            0x01,
            (tempRaw and 0xFF).toByte(),
            ((tempRaw ushr 8) and 0xFF).toByte(),
            (humidity and 0xFF).toByte(),
            (batteryRaw and 0xFF).toByte()
        )

        if (!includeChecksum) return base

        val checksum = base.fold(0) { acc, byte -> (acc + (byte.toInt() and 0xFF)) and 0xFF }.toByte()
        return base + checksum
    }
}
